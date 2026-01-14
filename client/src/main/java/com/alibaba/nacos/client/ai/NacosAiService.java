/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.client.ai;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentCardEvent;
import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.ai.cache.NacosAgentCardCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosMcpServerCacheHolder;
import com.alibaba.nacos.client.ai.event.AgentCardListenerInvoker;
import com.alibaba.nacos.client.ai.event.AiChangeNotifier;
import com.alibaba.nacos.client.ai.event.McpServerChangedEvent;
import com.alibaba.nacos.client.ai.event.McpServerListenerInvoker;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ClientBasicParamUtil;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Nacos AI client service implementation.
 *
 * @author xiweng.yy
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class NacosAiService implements AiService {
    
    private static final Logger LOGGER = LogUtils.logger(NacosAiService.class);
    
    private final String namespaceId;
    
    private final AiGrpcClient grpcClient;
    
    private final NacosMcpServerCacheHolder mcpServerCacheHolder;
    
    private final NacosAgentCardCacheHolder agentCardCacheHolder;
    
    private final AiChangeNotifier aiChangeNotifier;
    
    public NacosAiService(Properties properties) throws NacosException {
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        LOGGER.info(ClientBasicParamUtil.getInputParameters(clientProperties.asProperties()));
        this.namespaceId = initNamespace(clientProperties);
        this.grpcClient = new AiGrpcClient(namespaceId, clientProperties);
        this.mcpServerCacheHolder = new NacosMcpServerCacheHolder(grpcClient, clientProperties);
        this.agentCardCacheHolder = new NacosAgentCardCacheHolder(grpcClient, clientProperties);
        this.aiChangeNotifier = new AiChangeNotifier();
        start();
    }
    
    private String initNamespace(NacosClientProperties properties) {
        String tempNamespace = properties.getProperty(PropertyKeyConst.NAMESPACE);
        if (StringUtils.isBlank(tempNamespace)) {
            return Constants.DEFAULT_NAMESPACE_ID;
        }
        return tempNamespace;
    }
    
    private void start() throws NacosException {
        this.grpcClient.start(this.mcpServerCacheHolder, this.agentCardCacheHolder);
        NotifyCenter.registerToPublisher(McpServerChangedEvent.class, 16384);
        NotifyCenter.registerSubscriber(this.aiChangeNotifier);
    }
    
    @Override
    public McpServerDetailInfo getMcpServer(String mcpName, String version) throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `mcpName` not present");
        }
        return grpcClient.queryMcpServer(mcpName, version);
    }
    
    @Override
    public String releaseMcpServer(McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
            McpEndpointSpec endpointSpecification) throws NacosException {
        if (null == serverSpecification) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `serverSpecification` not present");
        }
        if (StringUtils.isBlank(serverSpecification.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `serverSpecification.name` not present");
        }
        if (null == serverSpecification.getVersionDetail() || StringUtils.isBlank(
                serverSpecification.getVersionDetail().getVersion())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `serverSpecification.versionDetail.version` not present");
        }
        return grpcClient.releaseMcpServer(serverSpecification, toolSpecification, endpointSpecification);
    }
    
    @Override
    public void registerMcpServerEndpoint(String mcpName, String address, int port, String version)
            throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
        }
        Instance instance = new Instance();
        instance.setIp(address);
        instance.setPort(port);
        instance.validate();
        grpcClient.registerMcpServerEndpoint(mcpName, address, port, version);
    }
    
    @Override
    public void deregisterMcpServerEndpoint(String mcpName, String address, int port) throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
        }
        Instance instance = new Instance();
        instance.setIp(address);
        instance.setPort(port);
        instance.validate();
        grpcClient.deregisterMcpServerEndpoint(mcpName, address, port);
    }
    
    @Override
    public McpServerDetailInfo subscribeMcpServer(String mcpName, String version,
            AbstractNacosMcpServerListener mcpServerListener) throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
        }
        if (null == mcpServerListener) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpServerListener` can't be empty or null");
        }
        McpServerListenerInvoker listenerInvoker = new McpServerListenerInvoker(mcpServerListener);
        aiChangeNotifier.registerListener(mcpName, version, listenerInvoker);
        McpServerDetailInfo result = grpcClient.subscribeMcpServer(mcpName, version);
        if (null != result && !listenerInvoker.isInvoked()) {
            listenerInvoker.invoke(new NacosMcpServerEvent(result));
        }
        return result;
    }
    
    @Override
    public void unsubscribeMcpServer(String mcpName, String version, AbstractNacosMcpServerListener mcpServerListener)
            throws NacosException {
        if (StringUtils.isBlank(mcpName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
        }
        if (null == mcpServerListener) {
            return;
        }
        McpServerListenerInvoker listenerInvoker = new McpServerListenerInvoker(mcpServerListener);
        aiChangeNotifier.deregisterListener(mcpName, version, listenerInvoker);
        if (!aiChangeNotifier.isMcpServerSubscribed(mcpName, version)) {
            grpcClient.unsubscribeMcpServer(mcpName, version);
        }
    }
    
    @Override
    public AgentCardDetailInfo getAgentCard(String agentName, String version, String registrationType)
            throws NacosException {
        if (StringUtils.isBlank(agentName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
        }
        return grpcClient.getAgentCard(agentName, version, registrationType);
    }
    
    @Override
    public void releaseAgentCard(AgentCard agentCard, String registrationType, boolean setAsLatest)
            throws NacosException {
        if (null == agentCard) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentCard` can't be null");
        }
        validateAgentCardField("name", agentCard.getName());
        validateAgentCardField("version", agentCard.getVersion());
        validateAgentCardField("protocolVersion", agentCard.getProtocolVersion());
        if (StringUtils.isBlank(registrationType)) {
            registrationType = AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE;
        }
        grpcClient.releaseAgentCard(agentCard, registrationType, setAsLatest);
    }
    
    @Override
    public void registerAgentEndpoint(String agentName, AgentEndpoint endpoint) throws NacosException {
        if (StringUtils.isBlank(agentName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
        }
        validateAgentEndpoint(endpoint);
        grpcClient.registerAgentEndpoint(agentName, endpoint);
    }
    
    @Override
    public void registerAgentEndpoint(String agentName, Collection<AgentEndpoint> endpoints) throws NacosException {
        if (StringUtils.isBlank(agentName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
        }
        validateAgentEndpoint(endpoints);
        grpcClient.registerAgentEndpoints(agentName, endpoints);
    }
    
    @Override
    public void deregisterAgentEndpoint(String agentName, AgentEndpoint endpoint) throws NacosException {
        if (StringUtils.isBlank(agentName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
        }
        validateAgentEndpoint(endpoint);
        grpcClient.deregisterAgentEndpoint(agentName, endpoint);
    }
    
    @Override
    public AgentCardDetailInfo subscribeAgentCard(String agentName, String version,
            AbstractNacosAgentCardListener agentCardListener) throws NacosException {
        if (StringUtils.isBlank(agentName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
        }
        if (null == agentCardListener) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentCardListener` can't be empty or null");
        }
        AgentCardListenerInvoker listenerInvoker = new AgentCardListenerInvoker(agentCardListener);
        aiChangeNotifier.registerListener(agentName, version, listenerInvoker);
        AgentCardDetailInfo result = grpcClient.subscribeAgentCard(agentName, version);
        if (null != result && !listenerInvoker.isInvoked()) {
            listenerInvoker.invoke(new NacosAgentCardEvent(result));
        }
        return result;
    }
    
    @Override
    public void unsubscribeAgentCard(String agentName, String version, AbstractNacosAgentCardListener agentCardListener)
            throws NacosException {
        if (StringUtils.isBlank(agentName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
        }
        if (null == agentCardListener) {
            return;
        }
        AgentCardListenerInvoker listenerInvoker = new AgentCardListenerInvoker(agentCardListener);
        aiChangeNotifier.deregisterListener(agentName, version, listenerInvoker);
        if (!aiChangeNotifier.isAgentCardSubscribed(agentName, version)) {
            grpcClient.unsubscribeAgentCard(agentName, version);
        }
    }
    
    private void validateAgentEndpoint(Collection<AgentEndpoint> endpoints) throws NacosApiException {
        if (null == endpoints || endpoints.isEmpty()) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `endpoints` can't be empty or null, if want to deregister endpoints, please use deregister API.");
        }
        Set<String> versions = new HashSet<>();
        for (AgentEndpoint endpoint : endpoints) {
            validateAgentEndpoint(endpoint);
            versions.add(endpoint.getVersion());
        }
        if (versions.size() > 1) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    String.format("Required parameter `endpoint.version` can't be different, current includes: %s.",
                            String.join(",", versions)));
        }
    }
    
    private void validateAgentEndpoint(AgentEndpoint endpoint) throws NacosApiException {
        if (null == endpoint) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "parameters `endpoint` can't be null");
        }
        if (StringUtils.isBlank(endpoint.getVersion())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `endpoint.version` can't be empty or null");
        }
        Instance instance = new Instance();
        instance.setIp(endpoint.getAddress());
        instance.setPort(endpoint.getPort());
        instance.validate();
    }
    
    private static void validateAgentCardField(String fieldName, String fieldValue) throws NacosApiException {
        if (StringUtils.isEmpty(fieldValue)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `agentCard." + fieldName + "` not present");
        }
    }
    
    @Override
    public void shutdown() throws NacosException {
        this.grpcClient.shutdown();
        this.mcpServerCacheHolder.shutdown();
    }
}
