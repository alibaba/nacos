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
import com.alibaba.nacos.api.ai.AgentTransportMode;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.McpService;
import com.alibaba.nacos.api.ai.SkillService;
import com.alibaba.nacos.api.ai.AgentSpecService;
import com.alibaba.nacos.api.ai.PromptService;
import com.alibaba.nacos.api.ai.AgentService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
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
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.ai.model.rad.AgentCatalogEntry;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryFilter;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointDeregistrationBatch;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointDeregistration;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistration;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentSearchQuery;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.ai.cache.NacosAgentDiscoveryCacheHolder;
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
import com.alibaba.nacos.client.ai.remote.AgentGrpcTransport;
import com.alibaba.nacos.client.ai.remote.AgentHttpTransport;
import com.alibaba.nacos.client.ai.remote.AgentTransportRouter;
import com.alibaba.nacos.client.ai.remote.McpGrpcTransport;
import com.alibaba.nacos.client.ai.remote.McpHttpTransport;
import com.alibaba.nacos.client.ai.remote.McpTransportRouter;
import com.alibaba.nacos.client.ai.remote.PromptTransportRouter;
import com.alibaba.nacos.client.ai.utils.AgentModelUtils;
import com.alibaba.nacos.client.ai.watch.AgentWatchTransportRouter;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Nacos AI feature facade.
 *
 * <p>The facade owns public validation and feature state. Protocol-neutral Agent calls flow
 * through {@link AgentTransportRouter}; the router owns no cache and delegates connection
 * lifecycle to the concrete Agent transports. Agent Watch and Endpoint publication intent
 * remain in their dedicated managers and depend only on that Agent transport surface.
 * Legacy AI resource holders retain their existing proxy contract.</p>
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
    
    private final AgentGrpcTransport grpcTransport;
    
    private final AgentHttpTransport httpTransport;
    
    private final AgentTransportRouter agentTransportRouter;
    
    private final McpTransportRouter mcpTransportRouter;
    
    private final NacosMcpServerCacheHolder mcpServerCacheHolder;
    
    private final NacosAgentCardCacheHolder agentCardCacheHolder;
    
    private final NacosPromptCacheHolder promptCacheHolder;
    
    private final NacosAgentSpecCacheHolder agentSpecCacheHolder;
    
    private final NacosSkillCacheHolder skillCacheHolder;
    
    private final NacosAgentDiscoveryCacheHolder agentDiscoveryCacheHolder;
    
    private final AgentEndpointPublicationManager agentEndpointPublicationManager;
    
    private final McpEndpointPublicationManager mcpEndpointPublicationManager;
    
    private final AiHttpPublicationCoordinator httpPublicationCoordinator;
    
    private final AiChangeNotifier aiChangeNotifier;
    
    private final AtomicBoolean shutdown = new AtomicBoolean();
    
    private final McpService mcpService = new McpServiceDelegate();
    
    private final SkillService skillService = new SkillServiceDelegate();
    
    private final AgentSpecService agentSpecService = new AgentSpecServiceDelegate();
    
    private final PromptService promptService = new PromptServiceDelegate();
    
    private final AgentService agentService = new AgentServiceDelegate();
    
    public NacosAiService(Properties properties) throws NacosException {
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        LOGGER.info(ClientBasicParamUtil.getInputParameters(clientProperties.asProperties()));
        this.namespaceId = initNamespace(clientProperties);
        AgentTransportMode transportMode = resolveAgentTransportMode(clientProperties);
        AgentTransportMode agentMode = resolveResourceTransportMode(clientProperties,
            AiConstants.AI_AGENT_TRANSPORT_MODE, transportMode);
        AgentTransportMode mcpMode = resolveResourceTransportMode(clientProperties,
            AiConstants.AI_MCP_TRANSPORT_MODE, transportMode);
        AgentTransportMode promptMode = resolveResourceTransportMode(clientProperties,
            AiConstants.AI_PROMPT_TRANSPORT_MODE, transportMode);
        // Validate requested values even while these resources only support HTTP.
        resolveResourceTransportMode(clientProperties, AiConstants.AI_SKILL_TRANSPORT_MODE,
            transportMode);
        resolveResourceTransportMode(clientProperties, AiConstants.AI_AGENT_SPEC_TRANSPORT_MODE,
            transportMode);
        this.grpcClient = new AiGrpcClient(namespaceId, clientProperties);
        this.httpProxy = new AiHttpClientProxy(namespaceId, clientProperties);
        this.mcpServerCacheHolder = new NacosMcpServerCacheHolder(clientProperties);
        this.agentCardCacheHolder = new NacosAgentCardCacheHolder(grpcClient, clientProperties);
        this.grpcTransport = new AgentGrpcTransport(agentMode, mcpMode, promptMode, grpcClient,
            mcpServerCacheHolder, agentCardCacheHolder);
        this.httpTransport = new AgentHttpTransport(httpProxy);
        McpGrpcTransport mcpGrpcTransport = new McpGrpcTransport(grpcTransport);
        McpHttpTransport mcpHttpTransport = new McpHttpTransport(httpProxy);
        this.mcpTransportRouter = new McpTransportRouter(mcpMode, grpcTransport, mcpGrpcTransport,
            mcpHttpTransport);
        this.mcpServerCacheHolder.setTransportRouter(mcpTransportRouter);
        this.aiClientProxy = new PromptTransportRouter(promptMode, grpcTransport, httpProxy);
        this.promptCacheHolder = new NacosPromptCacheHolder(this.aiClientProxy, clientProperties);
        this.agentSpecCacheHolder =
            new NacosAgentSpecCacheHolder(this.httpProxy, clientProperties);
        this.skillCacheHolder = new NacosSkillCacheHolder(this.httpProxy, clientProperties);
        this.agentTransportRouter =
            new AgentTransportRouter(agentMode, grpcTransport, httpTransport);
        this.agentDiscoveryCacheHolder =
            new NacosAgentDiscoveryCacheHolder(namespaceId, this.agentTransportRouter,
                resolvePositiveCapacity(clientProperties,
                    AiConstants.AI_AGENT_DISCOVERY_MAX_SUBSCRIPTIONS,
                    AiConstants.DEFAULT_AI_AGENT_DISCOVERY_MAX_SUBSCRIPTIONS),
                new AgentWatchTransportRouter(agentMode, grpcClient, httpProxy,
                    AiConstants.DEFAULT_AI_CACHE_UPDATE_INTERVAL));
        this.httpPublicationCoordinator = new AiHttpPublicationCoordinator();
        this.agentEndpointPublicationManager =
            new AgentEndpointPublicationManager(this.agentTransportRouter,
                this.httpPublicationCoordinator,
                resolvePositiveCapacity(clientProperties,
                    AiConstants.AI_AGENT_ENDPOINT_MAX_PUBLICATIONS,
                    AiConstants.DEFAULT_AI_AGENT_ENDPOINT_MAX_PUBLICATIONS));
        this.mcpEndpointPublicationManager = new McpEndpointPublicationManager(
            this.mcpTransportRouter, this.httpPublicationCoordinator);
        this.grpcClient.setAgentEndpointPublicationCapacityRejectedHandler(
            new Consumer<AgentEndpointRegistrationBatch>() {
                
                @Override
                public void accept(AgentEndpointRegistrationBatch batch) {
                    agentEndpointPublicationManager.discardAfterRemoteCapacityRejection(batch);
                }
            });
        this.aiChangeNotifier = new AiChangeNotifier();
        start();
    }
    
    static AgentTransportMode resolveAgentTransportMode(NacosClientProperties properties)
        throws NacosApiException {
        return resolveResourceTransportMode(properties, AiConstants.AI_TRANSPORT_MODE,
            AgentTransportMode.GRPC);
    }
    
    static AgentTransportMode resolveResourceTransportMode(NacosClientProperties properties,
        String key, AgentTransportMode defaultMode) throws NacosApiException {
        String value = properties.getProperty(key, defaultMode.getValue());
        try {
            return AgentTransportMode.fromValue(value);
        } catch (IllegalArgumentException e) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, e,
                "Client property `" + key
                    + "` must be one of `grpc`, `http`, or `auto`.");
        }
    }
    
    static int resolvePositiveCapacity(NacosClientProperties properties, String key,
        int defaultValue) throws NacosApiException {
        try {
            int result = properties.getInteger(key, defaultValue);
            if (result < 1) {
                throw new IllegalArgumentException("must be greater than 0");
            }
            return result;
        } catch (RuntimeException e) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, e,
                "Client property `" + key + "` must be a positive integer.");
        }
    }
    
    private String initNamespace(NacosClientProperties properties) {
        String tempNamespace = properties.getProperty(PropertyKeyConst.NAMESPACE);
        if (StringUtils.isBlank(tempNamespace)) {
            return Constants.DEFAULT_NAMESPACE_ID;
        }
        return tempNamespace;
    }
    
    private void start() throws NacosException {
        this.grpcTransport.startConfiguredTransport();
        NotifyCenter.registerToPublisher(McpServerChangedEvent.class, 16384);
        NotifyCenter.registerToPublisher(PromptChangedEvent.class, 16384);
        NotifyCenter.registerToPublisher(AgentSpecChangedEvent.class, 16384);
        NotifyCenter.registerToPublisher(SkillChangedEvent.class, 16384);
        NotifyCenter.registerSubscriber(this.aiChangeNotifier);
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
    
    // ==================== AgentSpec Methods ====================
    
    // ==================== Prompt Methods ====================
    
    @Override
    public void shutdown() throws NacosException {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        this.agentDiscoveryCacheHolder.shutdown();
        this.agentEndpointPublicationManager.shutdown();
        this.mcpEndpointPublicationManager.shutdown();
        this.httpPublicationCoordinator.shutdown();
        this.mcpServerCacheHolder.shutdown();
        this.agentCardCacheHolder.shutdown();
        this.promptCacheHolder.shutdown();
        this.agentSpecCacheHolder.shutdown();
        this.skillCacheHolder.shutdown();
        this.grpcClient.shutdown();
        this.httpProxy.shutdown();
    }
    
    @Override
    public McpService mcp() {
        return mcpService;
    }
    
    @Override
    public SkillService skill() {
        return skillService;
    }
    
    @Override
    public AgentSpecService agentSpec() {
        return agentSpecService;
    }
    
    @Override
    public PromptService prompt() {
        return promptService;
    }
    
    @Override
    public AgentService agent() {
        return agentService;
    }
    
    @Override
    public String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification,
        boolean createDraft) throws NacosException {
        return mcp().releaseMcpServer(serverSpecification, toolSpecification, resourceSpecification,
            endpointSpecification, createDraft);
    }
    
    private final class McpServiceDelegate implements McpService {
        
        @Override
        public McpServerDetailInfo getMcpServer(String mcpName, String version)
            throws NacosException {
            if (StringUtils.isBlank(mcpName)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "Required parameter `mcpName` not present");
            }
            return mcpTransportRouter.queryMcpServer(mcpName, version);
        }
        
        @Override
        public String releaseMcpServer(McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification,
            McpEndpointSpec endpointSpecification) throws NacosException {
            return releaseMcpServer(serverSpecification, toolSpecification, null,
                endpointSpecification, false);
        }
        
        @Override
        public String releaseMcpServer(McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification,
            McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification)
            throws NacosException {
            return releaseMcpServer(serverSpecification, toolSpecification, resourceSpecification,
                endpointSpecification, false);
        }
        
        @Override
        public String releaseMcpServer(McpServerBasicInfo serverSpecification,
            McpToolSpecification toolSpecification,
            McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification,
            boolean createDraft) throws NacosException {
            if (null == serverSpecification) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "Required parameter `serverSpecification` not present");
            }
            if (StringUtils.isBlank(serverSpecification.getName())) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "Required parameter `serverSpecification.name` not present");
            }
            if (null == serverSpecification.getVersionDetail() || StringUtils.isBlank(
                serverSpecification.getVersionDetail().getVersion())) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "Required parameter `serverSpecification.versionDetail.version` not present");
            }
            return mcpTransportRouter.releaseMcpServer(serverSpecification, toolSpecification,
                resourceSpecification, endpointSpecification, createDraft);
        }
        
        @Override
        public void registerMcpServerEndpoint(String mcpName, String address, int port,
            String version)
            throws NacosException {
            if (StringUtils.isBlank(mcpName)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
            }
            Instance instance = new Instance();
            instance.setIp(address);
            instance.setPort(port);
            instance.validate();
            mcpEndpointPublicationManager.register(mcpName, address, port, version);
        }
        
        @Override
        public void deregisterMcpServerEndpoint(String mcpName, String address, int port)
            throws NacosException {
            if (StringUtils.isBlank(mcpName)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
            }
            Instance instance = new Instance();
            instance.setIp(address);
            instance.setPort(port);
            instance.validate();
            mcpEndpointPublicationManager.deregister(mcpName, address, port);
        }
        
        @Override
        public McpServerDetailInfo subscribeMcpServer(String mcpName, String version,
            AbstractNacosMcpServerListener mcpServerListener) throws NacosException {
            if (StringUtils.isBlank(mcpName)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
            }
            if (null == mcpServerListener) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpServerListener` can't be empty or null");
            }
            McpServerListenerInvoker listenerInvoker =
                new McpServerListenerInvoker(mcpServerListener);
            aiChangeNotifier.registerListener(mcpName, version, listenerInvoker);
            McpServerDetailInfo result = mcpServerCacheHolder.getMcpServer(mcpName, version);
            if (result == null) {
                try {
                    result = mcpTransportRouter.queryMcpServer(mcpName, version);
                    mcpServerCacheHolder.processMcpServerDetailInfo(result);
                } catch (NacosException e) {
                    if (NacosException.NOT_FOUND != e.getErrCode()) {
                        throw e;
                    }
                }
                mcpServerCacheHolder.addMcpServerUpdateTask(mcpName, version);
            }
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
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `mcpName` can't be empty or null");
            }
            if (null == mcpServerListener) {
                return;
            }
            McpServerListenerInvoker listenerInvoker =
                new McpServerListenerInvoker(mcpServerListener);
            aiChangeNotifier.deregisterListener(mcpName, version, listenerInvoker);
            if (!aiChangeNotifier.isMcpServerSubscribed(mcpName, version)) {
                mcpServerCacheHolder.removeMcpServerUpdateTask(mcpName, version);
            }
        }
    }
    
    private final class SkillServiceDelegate implements SkillService {
        
        @Override
        public byte[] downloadSkillZip(String skillName) throws NacosException {
            if (StringUtils.isBlank(skillName)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "Required parameter `skillName` not present");
            }
            return httpProxy.downloadSkillZip(skillName, null, null);
        }
        
        @Override
        public byte[] downloadSkillZipByVersion(String skillName, String version)
            throws NacosException {
            if (StringUtils.isBlank(skillName)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "Required parameter `skillName` not present");
            }
            return httpProxy.downloadSkillZip(skillName, version, null);
        }
        
        @Override
        public byte[] downloadSkillZipByLabel(String skillName, String label)
            throws NacosException {
            if (StringUtils.isBlank(skillName)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "Required parameter `skillName` not present");
            }
            return httpProxy.downloadSkillZip(skillName, null, label);
        }
        
        @Override
        public byte[] subscribeSkill(String skillName, String version, String label,
            AbstractNacosSkillListener skillListener) throws NacosException {
            if (StringUtils.isBlank(skillName)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `skillName` can't be empty or null");
            }
            if (null == skillListener) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
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
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
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
    }
    
    private final class AgentSpecServiceDelegate implements AgentSpecService {
        
        @Override
        public AgentSpec loadAgentSpec(String agentSpecName) throws NacosException {
            if (StringUtils.isBlank(agentSpecName)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "Required parameter `agentSpecName` not present");
            }
            return agentSpecCacheHolder.queryAgentSpec(agentSpecName);
        }
        
        @Override
        public AgentSpec subscribeAgentSpec(String agentSpecName,
            AbstractNacosAgentSpecListener agentSpecListener)
            throws NacosException {
            if (StringUtils.isBlank(agentSpecName)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `agentSpecName` can't be empty or null");
            }
            if (null == agentSpecListener) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `agentSpecListener` can't be empty or null");
            }
            
            AgentSpecListenerInvoker listenerInvoker =
                new AgentSpecListenerInvoker(agentSpecListener);
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
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `agentSpecName` can't be empty or null");
            }
            if (null == agentSpecListener) {
                return;
            }
            AgentSpecListenerInvoker listenerInvoker =
                new AgentSpecListenerInvoker(agentSpecListener);
            aiChangeNotifier.deregisterListener(agentSpecName, listenerInvoker);
            if (!aiChangeNotifier.isAgentSpecSubscribed(agentSpecName)) {
                agentSpecCacheHolder.unsubscribeAgentSpec(agentSpecName);
            }
        }
    }
    
    private final class PromptServiceDelegate implements PromptService {
        
        @Override
        public Prompt getPrompt(String promptKey) throws NacosException {
            if (StringUtils.isBlank(promptKey)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `promptKey` can't be empty or null");
            }
            return getPromptByVersion(promptKey, null);
        }
        
        @Override
        public Prompt getPromptByVersion(String promptKey, String version) throws NacosException {
            if (StringUtils.isBlank(promptKey)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
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
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `promptKey` can't be empty or null");
            }
            if (StringUtils.isBlank(label)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `label` can't be empty or null");
            }
            return aiClientProxy.queryPrompt(promptKey, null, label, null);
        }
        
        @Override
        public Prompt subscribePrompt(String promptKey, String version, String label,
            AbstractNacosPromptListener promptListener) throws NacosException {
            if (StringUtils.isBlank(promptKey)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `promptKey` can't be empty or null");
            }
            if (null == promptListener) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
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
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
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
    }
    
    private final class AgentServiceDelegate implements AgentService {
        
        @Override
        public AgentVersionDetail publishAgent(AgentPublishRequest request) throws NacosException {
            return agentTransportRouter.publishAgent(AgentModelUtils.copyPublishRequest(request));
        }
        
        @Override
        public AgentCardDetailInfo getAgentCard(String agentName, String version,
            String registrationType)
            throws NacosException {
            if (StringUtils.isBlank(agentName)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
            }
            return grpcTransport.requireGrpcClient()
                .getAgentCard(agentName, version, registrationType);
        }
        
        @Override
        public void releaseAgentCard(AgentCard agentCard, String registrationType,
            boolean setAsLatest)
            throws NacosException {
            if (null == agentCard) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `agentCard` can't be null");
            }
            validateAgentCardField("name", agentCard.getName());
            validateAgentCardField("version", agentCard.getVersion());
            validateAgentCard(agentCard);
            if (StringUtils.isBlank(registrationType)) {
                registrationType = AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE;
            }
            grpcTransport.requireGrpcClient()
                .releaseAgentCard(agentCard, registrationType, setAsLatest);
        }
        
        @Override
        public void registerAgentEndpoint(String agentName, AgentEndpoint endpoint)
            throws NacosException {
            if (StringUtils.isBlank(agentName)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
            }
            validateAgentEndpoint(endpoint);
            grpcTransport.requireGrpcClient().registerAgentEndpoint(agentName, endpoint);
        }
        
        @Override
        public void registerAgentEndpoint(String agentName, Collection<AgentEndpoint> endpoints)
            throws NacosException {
            if (StringUtils.isBlank(agentName)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
            }
            validateAgentEndpoint(endpoints);
            grpcTransport.requireGrpcClient().registerAgentEndpoints(agentName, endpoints);
        }
        
        @Override
        public void deregisterAgentEndpoint(String agentName, AgentEndpoint endpoint)
            throws NacosException {
            if (StringUtils.isBlank(agentName)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
            }
            validateAgentEndpoint(endpoint);
            grpcTransport.requireGrpcClient().deregisterAgentEndpoint(agentName, endpoint);
        }
        
        @Override
        public AgentCardDetailInfo subscribeAgentCard(String agentName, String version,
            AbstractNacosAgentCardListener agentCardListener) throws NacosException {
            if (StringUtils.isBlank(agentName)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
            }
            if (null == agentCardListener) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `agentCardListener` can't be empty or null");
            }
            AgentCardListenerInvoker listenerInvoker =
                new AgentCardListenerInvoker(agentCardListener);
            aiChangeNotifier.registerListener(agentName, version, listenerInvoker);
            AgentCardDetailInfo result =
                grpcTransport.requireGrpcClient().subscribeAgentCard(agentName, version);
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
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "parameters `agentName` can't be empty or null");
            }
            if (null == agentCardListener) {
                return;
            }
            AgentCardListenerInvoker listenerInvoker =
                new AgentCardListenerInvoker(agentCardListener);
            aiChangeNotifier.deregisterListener(agentName, version, listenerInvoker);
            if (!aiChangeNotifier.isAgentCardSubscribed(agentName, version)) {
                grpcTransport.requireGrpcClient().unsubscribeAgentCard(agentName, version);
            }
        }
        
        @Override
        public Page<AgentCatalogEntry> searchAgents(AgentSearchQuery request)
            throws NacosException {
            AgentSearchRequest boundRequest =
                AgentModelUtils.copySearchRequest(request, namespaceId);
            return agentTransportRouter.searchAgents(boundRequest);
        }
        
        @Override
        public AgentDiscoveryResult discoverAgent(AgentReference reference,
            AgentDiscoveryFilter filter) throws NacosException {
            AgentDiscoveryRequest request =
                AgentModelUtils.copyDiscoveryRequest(reference, filter, namespaceId);
            return agentTransportRouter.discoverAgent(request);
        }
        
        @Override
        public AgentDiscoveryResult subscribeAgent(AgentReference reference,
            AgentDiscoveryFilter filter, AbstractNacosAgentDiscoveryListener listener)
            throws NacosException {
            return agentDiscoveryCacheHolder.subscribe(reference, filter, listener);
        }
        
        @Override
        public void unsubscribeAgent(AgentReference reference, AgentDiscoveryFilter filter,
            AbstractNacosAgentDiscoveryListener listener) throws NacosException {
            agentDiscoveryCacheHolder.unsubscribe(reference, filter, listener);
        }
        
        @Override
        public void registerAgentEndpoints(AgentEndpointRegistration batch)
            throws NacosException {
            AgentEndpointRegistrationBatch boundBatch =
                AgentModelUtils.copyRegistrationBatch(batch, namespaceId);
            agentEndpointPublicationManager.register(boundBatch);
        }
        
        @Override
        public void deregisterAgentEndpoints(AgentEndpointDeregistration batch)
            throws NacosException {
            AgentEndpointDeregistrationBatch boundBatch =
                AgentModelUtils.copyDeregistrationBatch(batch, namespaceId);
            agentEndpointPublicationManager.deregister(boundBatch);
        }
    }
}
