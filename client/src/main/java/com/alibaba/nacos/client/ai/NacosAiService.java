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
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentSpecListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosPromptListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosSkillListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentCardEvent;
import com.alibaba.nacos.api.ai.listener.NacosAgentSpecEvent;
import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.api.ai.listener.NacosPromptEvent;
import com.alibaba.nacos.api.ai.listener.NacosSkillEvent;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.ai.cache.NacosAgentCardCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosAgentSpecCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosMcpServerCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosPromptCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosSkillCacheHolder;
import com.alibaba.nacos.client.ai.event.AgentCardListenerInvoker;
import com.alibaba.nacos.client.ai.event.AgentSpecChangedEvent;
import com.alibaba.nacos.client.ai.event.AgentSpecListenerInvoker;
import com.alibaba.nacos.client.ai.event.AiChangeNotifier;
import com.alibaba.nacos.client.ai.event.McpServerChangedEvent;
import com.alibaba.nacos.client.ai.event.McpServerListenerInvoker;
import com.alibaba.nacos.client.ai.event.PromptChangedEvent;
import com.alibaba.nacos.client.ai.event.PromptListenerInvoker;
import com.alibaba.nacos.client.ai.event.SkillChangedEvent;
import com.alibaba.nacos.client.ai.event.SkillListenerInvoker;
import com.alibaba.nacos.client.ai.remote.AiClientProxy;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.client.ai.remote.AiHttpClientProxy;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ClientBasicParamUtil;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Nacos AI client service implementation.
 *
 * @author xiweng.yy
 */
public class NacosAiService implements AiService {
    
    private static final Logger LOGGER = LogUtils.logger(NacosAiService.class);
    
    private static final String AGENT_CARD_FORMAT_ERROR =
        "Required parameter `agentCard.supportedInterfaces` not present, and old protocol fields "
            + "(`agentCard.protocolVersion`, `agentCard.preferredTransport`, `agentCard.url`) are incomplete. "
            + "Please prefer `agentCard.supportedInterfaces` for A2A 1.0.0.";
    
    private final String namespaceId;
    
    private final AiGrpcClient grpcClient;
    
    private final AiHttpClientProxy httpProxy;
    
    private final AiClientProxy aiClientProxy;
    
    private final NacosMcpServerCacheHolder mcpServerCacheHolder;
    
    private final NacosAgentCardCacheHolder agentCardCacheHolder;
    
    private final NacosPromptCacheHolder promptCacheHolder;
    
    private final NacosAgentSpecCacheHolder agentSpecCacheHolder;
    
    private final NacosSkillCacheHolder skillCacheHolder;
    
    private final AiChangeNotifier aiChangeNotifier;
    
    public NacosAiService(Properties properties) throws NacosException {
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        LOGGER.info(ClientBasicParamUtil.getInputParameters(clientProperties.asProperties()));
        this.namespaceId = initNamespace(clientProperties);
        this.grpcClient = new AiGrpcClient(namespaceId, clientProperties);
        this.httpProxy = new AiHttpClientProxy(namespaceId, clientProperties);
        String transportMode = clientProperties.getProperty(AiConstants.AI_TRANSPORT_MODE,
            AiConstants.AI_TRANSPORT_MODE_GRPC);
        if (AiConstants.AI_TRANSPORT_MODE_HTTP.equalsIgnoreCase(transportMode)) {
            LOGGER.info("AI transport mode is HTTP, using AiHttpClientProxy as primary proxy.");
            this.aiClientProxy = this.httpProxy;
        } else {
            this.aiClientProxy = this.grpcClient;
        }
        this.mcpServerCacheHolder = new NacosMcpServerCacheHolder(grpcClient, clientProperties);
        this.agentCardCacheHolder = new NacosAgentCardCacheHolder(grpcClient, clientProperties);
        this.promptCacheHolder = new NacosPromptCacheHolder(this.aiClientProxy, clientProperties);
        this.agentSpecCacheHolder =
            new NacosAgentSpecCacheHolder(this.aiClientProxy, clientProperties);
        this.skillCacheHolder = new NacosSkillCacheHolder(this.aiClientProxy, clientProperties);
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
        NotifyCenter.registerToPublisher(PromptChangedEvent.class, 16384);
        NotifyCenter.registerToPublisher(AgentSpecChangedEvent.class, 16384);
        NotifyCenter.registerToPublisher(SkillChangedEvent.class, 16384);
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
    public String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        return releaseMcpServer(serverSpecification, toolSpecification, null,
            endpointSpecification);
    }
    
    @Override
    public String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification)
        throws NacosException {
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
        return grpcClient.releaseMcpServer(serverSpecification, toolSpecification,
            resourceSpecification,
            endpointSpecification);
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
    public void deregisterMcpServerEndpoint(String mcpName, String address, int port)
        throws NacosException {
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
    public void unsubscribeMcpServer(String mcpName, String version,
        AbstractNacosMcpServerListener mcpServerListener)
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
    public AgentCardDetailInfo getAgentCard(String agentName, String version,
        String registrationType)
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
        validateAgentCard(agentCard);
        if (StringUtils.isBlank(registrationType)) {
            registrationType = AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE;
        }
        grpcClient.releaseAgentCard(agentCard, registrationType, setAsLatest);
    }
    
    @Override
    public void registerAgentEndpoint(String agentName, AgentEndpoint endpoint)
        throws NacosException {
        if (StringUtils.isBlank(agentName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `agentName` can't be empty or null");
        }
        validateAgentEndpoint(endpoint);
        grpcClient.registerAgentEndpoint(agentName, endpoint);
    }
    
    @Override
    public void registerAgentEndpoint(String agentName, Collection<AgentEndpoint> endpoints)
        throws NacosException {
        if (StringUtils.isBlank(agentName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `agentName` can't be empty or null");
        }
        validateAgentEndpoint(endpoints);
        grpcClient.registerAgentEndpoints(agentName, endpoints);
    }
    
    @Override
    public void deregisterAgentEndpoint(String agentName, AgentEndpoint endpoint)
        throws NacosException {
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
    public void unsubscribeAgentCard(String agentName, String version,
        AbstractNacosAgentCardListener agentCardListener)
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
    
    private void validateAgentEndpoint(Collection<AgentEndpoint> endpoints)
        throws NacosApiException {
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
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                String.format(
                    "Required parameter `endpoint.version` can't be different, current includes: %s.",
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
    
    private static void validateAgentCardField(String fieldName, String fieldValue)
        throws NacosApiException {
        if (StringUtils.isEmpty(fieldValue)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Required parameter `agentCard." + fieldName + "` not present");
        }
    }
    
    private static void validateAgentCard(AgentCard agentCard) throws NacosApiException {
        boolean hasLegacyRequiredFields = !StringUtils.isEmpty(agentCard.getProtocolVersion())
            && !StringUtils.isEmpty(
                agentCard.getPreferredTransport())
            && !StringUtils.isEmpty(agentCard.getUrl());
        boolean hasV1RequiredFields = hasValidV1Interfaces(agentCard.getSupportedInterfaces());
        if (!hasLegacyRequiredFields && !hasV1RequiredFields) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                AGENT_CARD_FORMAT_ERROR);
        }
    }
    
    private static boolean hasValidV1Interfaces(List<AgentInterface> interfaces) {
        if (null == interfaces || interfaces.isEmpty()) {
            return false;
        }
        for (AgentInterface each : interfaces) {
            if (null == each || StringUtils.isEmpty(each.getUrl())
                || StringUtils.isEmpty(each.getProtocolBinding())
                || StringUtils.isEmpty(each.getProtocolVersion())) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public byte[] downloadSkillZip(String skillName) throws NacosException {
        if (StringUtils.isBlank(skillName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Required parameter `skillName` not present");
        }
        return httpProxy.downloadSkillZip(skillName, null, null);
    }
    
    @Override
    public byte[] downloadSkillZipByVersion(String skillName, String version)
        throws NacosException {
        if (StringUtils.isBlank(skillName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Required parameter `skillName` not present");
        }
        return httpProxy.downloadSkillZip(skillName, version, null);
    }
    
    @Override
    public byte[] downloadSkillZipByLabel(String skillName, String label) throws NacosException {
        if (StringUtils.isBlank(skillName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Required parameter `skillName` not present");
        }
        return httpProxy.downloadSkillZip(skillName, null, label);
    }
    
    @Override
    public byte[] subscribeSkill(String skillName, String version, String label,
        AbstractNacosSkillListener skillListener) throws NacosException {
        if (StringUtils.isBlank(skillName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `skillName` can't be empty or null");
        }
        if (null == skillListener) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `skillListener` can't be null");
        }
        
        SkillListenerInvoker listenerInvoker = new SkillListenerInvoker(skillListener);
        aiChangeNotifier.registerListener(skillName, version, label, listenerInvoker);
        byte[] zipBytes = skillCacheHolder.subscribeSkill(skillName, version, label);
        if (null != zipBytes && !listenerInvoker.isInvoked()) {
            listenerInvoker.invoke(new NacosSkillEvent(skillName, zipBytes, null, null));
        }
        return zipBytes;
    }
    
    @Override
    public void unsubscribeSkill(String skillName, String version, String label,
        AbstractNacosSkillListener skillListener) throws NacosException {
        if (StringUtils.isBlank(skillName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `skillName` can't be empty or null");
        }
        if (null == skillListener) {
            return;
        }
        SkillListenerInvoker listenerInvoker = new SkillListenerInvoker(skillListener);
        aiChangeNotifier.deregisterListener(skillName, version, label, listenerInvoker);
        if (!aiChangeNotifier.isSkillSubscribed(skillName, version, label)) {
            skillCacheHolder.unsubscribeSkill(skillName, version, label);
        }
    }
    
    // ==================== AgentSpec Methods ====================
    
    @Override
    public AgentSpec loadAgentSpec(String agentSpecName) throws NacosException {
        if (StringUtils.isBlank(agentSpecName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Required parameter `agentSpecName` not present");
        }
        return agentSpecCacheHolder.queryAgentSpec(agentSpecName);
    }
    
    @Override
    public AgentSpec subscribeAgentSpec(String agentSpecName,
        AbstractNacosAgentSpecListener agentSpecListener)
        throws NacosException {
        if (StringUtils.isBlank(agentSpecName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `agentSpecName` can't be empty or null");
        }
        if (null == agentSpecListener) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `agentSpecListener` can't be empty or null");
        }
        
        AgentSpecListenerInvoker listenerInvoker = new AgentSpecListenerInvoker(agentSpecListener);
        aiChangeNotifier.registerListener(agentSpecName, listenerInvoker);
        AgentSpec result = agentSpecCacheHolder.subscribeAgentSpec(agentSpecName);
        if (null != result && !listenerInvoker.isInvoked()) {
            listenerInvoker.invoke(new NacosAgentSpecEvent(agentSpecName, result));
        }
        return result;
    }
    
    @Override
    public void unsubscribeAgentSpec(String agentSpecName,
        AbstractNacosAgentSpecListener agentSpecListener)
        throws NacosException {
        if (StringUtils.isBlank(agentSpecName)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `agentSpecName` can't be empty or null");
        }
        if (null == agentSpecListener) {
            return;
        }
        AgentSpecListenerInvoker listenerInvoker = new AgentSpecListenerInvoker(agentSpecListener);
        aiChangeNotifier.deregisterListener(agentSpecName, listenerInvoker);
        if (!aiChangeNotifier.isAgentSpecSubscribed(agentSpecName)) {
            agentSpecCacheHolder.unsubscribeAgentSpec(agentSpecName);
        }
    }
    
    // ==================== Prompt Methods ====================
    
    @Override
    public Prompt getPrompt(String promptKey) throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `promptKey` can't be empty or null");
        }
        return getPromptByVersion(promptKey, null);
    }
    
    @Override
    public Prompt getPromptByVersion(String promptKey, String version) throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `promptKey` can't be empty or null");
        }
        if (StringUtils.isBlank(version)) {
            return aiClientProxy.queryPrompt(promptKey, null, null, null);
        }
        return aiClientProxy.queryPrompt(promptKey, version, null, null);
    }
    
    @Override
    public Prompt getPromptByLabel(String promptKey, String label) throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `promptKey` can't be empty or null");
        }
        if (StringUtils.isBlank(label)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `label` can't be empty or null");
        }
        return aiClientProxy.queryPrompt(promptKey, null, label, null);
    }
    
    @Override
    public Prompt subscribePrompt(String promptKey, String version, String label,
        AbstractNacosPromptListener promptListener) throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `promptKey` can't be empty or null");
        }
        if (null == promptListener) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `promptListener` can't be null");
        }
        
        PromptListenerInvoker listenerInvoker = new PromptListenerInvoker(promptListener);
        aiChangeNotifier.registerListener(promptKey, version, label, listenerInvoker);
        Prompt result = promptCacheHolder.subscribePrompt(promptKey, version, label);
        if (null != result && !listenerInvoker.isInvoked()) {
            listenerInvoker.invoke(new NacosPromptEvent(promptKey, result));
        }
        return result;
    }
    
    @Override
    public void unsubscribePrompt(String promptKey, String version, String label,
        AbstractNacosPromptListener promptListener) throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "parameters `promptKey` can't be empty or null");
        }
        if (null == promptListener) {
            return;
        }
        PromptListenerInvoker listenerInvoker = new PromptListenerInvoker(promptListener);
        aiChangeNotifier.deregisterListener(promptKey, version, label, listenerInvoker);
        if (!aiChangeNotifier.isPromptSubscribed(promptKey, version, label)) {
            promptCacheHolder.unsubscribePrompt(promptKey, version, label);
        }
    }
    
    @Override
    public void shutdown() throws NacosException {
        this.grpcClient.shutdown();
        this.httpProxy.shutdown();
        this.mcpServerCacheHolder.shutdown();
        this.promptCacheHolder.shutdown();
        this.agentSpecCacheHolder.shutdown();
        this.skillCacheHolder.shutdown();
    }
}
