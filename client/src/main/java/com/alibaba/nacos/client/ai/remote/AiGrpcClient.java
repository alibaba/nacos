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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.ai.remote.request.AbstractAgentRequest;
import com.alibaba.nacos.api.ai.remote.request.AbstractMcpRequest;
import com.alibaba.nacos.api.ai.remote.request.AgentEndpointRequest;
import com.alibaba.nacos.api.ai.remote.request.BatchAgentEndpointRequest;
import com.alibaba.nacos.api.ai.remote.request.McpServerEndpointRequest;
import com.alibaba.nacos.api.ai.remote.request.QueryAgentCardRequest;
import com.alibaba.nacos.api.ai.remote.request.QueryMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseAgentCardRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.response.AgentEndpointResponse;
import com.alibaba.nacos.api.ai.remote.response.McpServerEndpointResponse;
import com.alibaba.nacos.api.ai.remote.response.QueryAgentCardResponse;
import com.alibaba.nacos.api.ai.remote.response.QueryMcpServerResponse;
import com.alibaba.nacos.api.ai.remote.response.ReleaseAgentCardResponse;
import com.alibaba.nacos.api.ai.remote.response.ReleaseMcpServerResponse;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.client.address.AbstractServerListManager;
import com.alibaba.nacos.client.ai.cache.NacosAgentCardCacheHolder;
import com.alibaba.nacos.client.ai.cache.NacosMcpServerCacheHolder;
import com.alibaba.nacos.client.ai.remote.redo.AgentEndpointWrapper;
import com.alibaba.nacos.client.ai.remote.redo.AiGrpcRedoService;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.core.NamingServerListManager;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.AppNameUtils;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientConfigFactory;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.grpc.GrpcClientConfig;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.client.constant.Constants.Security.SECURITY_INFO_REFRESH_INTERVAL_MILLS;

/**
 * Nacos AI GRPC protocol client.
 *
 * @author xiweng.yy
 */
public class AiGrpcClient implements Closeable {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AiGrpcClient.class);
    
    private final String namespaceId;
    
    private final String uuid;
    
    private final Long requestTimeout;
    
    private final RpcClient rpcClient;
    
    private final AbstractServerListManager serverListManager;
    
    private final AiGrpcRedoService redoService;

    private final NacosClientProperties properties;

    private SecurityProxy securityProxy;
    
    private NacosMcpServerCacheHolder mcpServerCacheHolder;
    
    private NacosAgentCardCacheHolder agentCardCacheHolder;

    private ScheduledThreadPoolExecutor executorService;

    public AiGrpcClient(String namespaceId, NacosClientProperties properties) {
        this.namespaceId = namespaceId;
        this.uuid = UUID.randomUUID().toString();
        this.requestTimeout = Long.parseLong(properties.getProperty(AiConstants.AI_REQUEST_TIMEOUT, "-1"));
        this.rpcClient = buildRpcClient(properties);
        this.serverListManager = new NamingServerListManager(properties, namespaceId);
        this.redoService = new AiGrpcRedoService(properties, this);
        this.properties = properties;
    }
    
    private RpcClient buildRpcClient(NacosClientProperties properties) {
        Map<String, String> labels = new HashMap<>(3);
        labels.put(RemoteConstants.LABEL_SOURCE, RemoteConstants.LABEL_SOURCE_SDK);
        labels.put(RemoteConstants.LABEL_MODULE, RemoteConstants.LABEL_MODULE_AI);
        labels.put(Constants.APPNAME, AppNameUtils.getAppName());
        GrpcClientConfig grpcClientConfig = RpcClientConfigFactory.getInstance()
                .createGrpcClientConfig(properties.asProperties(), labels);
        return RpcClientFactory.createClient(uuid, ConnectionType.GRPC, grpcClientConfig);
    }
    
    /**
     * Start the grpc client.
     *
     * @throws NacosException nacos exception
     */
    public void start(NacosMcpServerCacheHolder mcpServerCacheHolder, NacosAgentCardCacheHolder agentCardCacheHolder)
            throws NacosException {
        this.mcpServerCacheHolder = mcpServerCacheHolder;
        this.agentCardCacheHolder = agentCardCacheHolder;
        this.serverListManager.start();
        this.rpcClient.registerConnectionListener(this.redoService);
        this.rpcClient.serverListFactory(this.serverListManager);
        this.rpcClient.start();
        this.securityProxy = new SecurityProxy(this.serverListManager,
                NamingHttpClientManager.getInstance().getNacosRestTemplate());
        initSecurityProxy(properties);
    }

    private void initSecurityProxy(NacosClientProperties properties) {
        this.executorService = new ScheduledThreadPoolExecutor(1,
                new NameThreadFactory("com.alibaba.nacos.client.ai.security"));
        final Properties nacosClientPropertiesView = properties.asProperties();
        this.securityProxy.login(nacosClientPropertiesView);
        this.executorService.scheduleWithFixedDelay(() -> securityProxy.login(nacosClientPropertiesView), 0,
                SECURITY_INFO_REFRESH_INTERVAL_MILLS, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Do query mcp server by mcpId and version.
     *
     * @param mcpName   name of mcp server
     * @param version   version of mcp server, if input empty or null, return the latest version
     * @return mcp server detail info
     * @throws NacosException if request parameter is invalid or handle error
     */
    public McpServerDetailInfo queryMcpServer(String mcpName, String version) throws NacosException {
        if (!isAbilitySupportedByServer(AbilityKey.SERVER_MCP_REGISTRY)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support mcp registry feature.");
        }
        QueryMcpServerRequest request = new QueryMcpServerRequest();
        request.setNamespaceId(namespaceId);
        request.setMcpName(mcpName);
        request.setVersion(version);
        QueryMcpServerResponse response = requestToServer(request, QueryMcpServerResponse.class);
        return response.getMcpServerDetailInfo();
    }
    
    /**
     * Do release mcp server.
     *
     * @param serverSpecification mcp server specification
     * @param toolSpecification   mcp server tool specification, optional
     * @return mcp id
     * @throws NacosException if request parameter is invalid or handle error
     */
    public String releaseMcpServer(McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
            McpEndpointSpec endpointSpecification) throws NacosException {
        LOGGER.info("[{}] RELEASE Mcp server {}, version {}", uuid, serverSpecification.getName(),
                serverSpecification.getVersionDetail().getVersion());
        if (!isAbilitySupportedByServer(AbilityKey.SERVER_MCP_REGISTRY)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support mcp registry feature.");
        }
        ReleaseMcpServerRequest request = new ReleaseMcpServerRequest();
        request.setNamespaceId(namespaceId);
        request.setMcpName(serverSpecification.getName());
        request.setServerSpecification(serverSpecification);
        request.setToolSpecification(toolSpecification);
        request.setEndpointSpecification(endpointSpecification);
        ReleaseMcpServerResponse response = requestToServer(request, ReleaseMcpServerResponse.class);
        return response.getMcpId();
    }
    
    /**
     * Register endpoint to target mcp server and cached to redo service.
     *
     * @param mcpName   name of mcp server
     * @param address   address of mcp endpoint
     * @param port      port of mcp endpoint
     * @param version   version of mcp endpoint, if empty, the endpoint will return for all mcp version
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void registerMcpServerEndpoint(String mcpName, String address, int port, String version)
            throws NacosException {
        LOGGER.info("[{}] REGISTER Mcp server endpoint {}:{}, version {} into mcp server {}", uuid, address, port,
                version, mcpName);
        if (!isAbilitySupportedByServer(AbilityKey.SERVER_MCP_REGISTRY)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support mcp registry feature.");
        }
        redoService.cachedMcpServerEndpointForRedo(mcpName, address, port, version);
        doRegisterMcpServerEndpoint(mcpName, address, port, version);
    }
    
    /**
     * Actual do Register endpoint to target mcp server.
     *
     * @param mcpName   name of mcp server
     * @param address   address of mcp endpoint
     * @param port      port of mcp endpoint
     * @param version   version of mcp endpoint, if empty, the endpoint will return for all mcp version
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void doRegisterMcpServerEndpoint(String mcpName, String address, int port, String version)
            throws NacosException {
        McpServerEndpointRequest request = new McpServerEndpointRequest();
        request.setNamespaceId(namespaceId);
        request.setMcpName(mcpName);
        request.setAddress(address);
        request.setPort(port);
        request.setVersion(version);
        request.setType(AiRemoteConstants.REGISTER_ENDPOINT);
        requestToServer(request, McpServerEndpointResponse.class);
        redoService.mcpServerEndpointRegistered(mcpName);
    }
    
    /**
     * Deregister endpoint from target mcp server and cached to redo service.
     *
     * @param mcpName   name of mcp server
     * @param address   address of mcp endpoint
     * @param port      port of mcp endpoint
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void deregisterMcpServerEndpoint(String mcpName, String address, int port) throws NacosException {
        LOGGER.info("[{}] DE-REGISTER Mcp server endpoint {}:{} from mcp server {}", uuid, address, port, mcpName);
        if (!isAbilitySupportedByServer(AbilityKey.SERVER_MCP_REGISTRY)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support mcp registry feature.");
        }
        redoService.mcpServerEndpointDeregister(mcpName);
        doDeregisterMcpServerEndpoint(mcpName, address, port);
    }
    
    /**
     * Actual do deregister endpoint from target mcp server.
     *
     * @param mcpName   name of mcp server
     * @param address   address of mcp endpoint
     * @param port      port of mcp endpoint
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void doDeregisterMcpServerEndpoint(String mcpName, String address, int port) throws NacosException {
        McpServerEndpointRequest request = new McpServerEndpointRequest();
        request.setNamespaceId(namespaceId);
        request.setMcpName(mcpName);
        request.setAddress(address);
        request.setPort(port);
        request.setType(AiRemoteConstants.DE_REGISTER_ENDPOINT);
        requestToServer(request, McpServerEndpointResponse.class);
        redoService.mcpServerEndpointDeregistered(mcpName);
    }
    
    /**
     * Subscribe mcp server latest version.
     *
     * @param mcpName   name of mcp server
     * @param version   version of mcp server
     * @return latest version mcp server
     * @throws NacosException if request parameter is invalid or handle error
     */
    public McpServerDetailInfo subscribeMcpServer(String mcpName, String version) throws NacosException {
        if (!isAbilitySupportedByServer(AbilityKey.SERVER_MCP_REGISTRY)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support mcp registry feature.");
        }
        McpServerDetailInfo cachedServer = mcpServerCacheHolder.getMcpServer(mcpName, version);
        if (null == cachedServer) {
            try {
                cachedServer = queryMcpServer(mcpName, version);
                mcpServerCacheHolder.processMcpServerDetailInfo(cachedServer);
            } catch (NacosException e) {
                if (NacosException.NOT_FOUND != e.getErrCode()) {
                    throw e;
                }
            }
            mcpServerCacheHolder.addMcpServerUpdateTask(mcpName, version);
        }
        return cachedServer;
    }
    
    /**
     * Un-subscribe mcp server.
     *
     * @param mcpName   name of mcp server
     * @param version   version of mcp server
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void unsubscribeMcpServer(String mcpName, String version) throws NacosException {
        if (!isAbilitySupportedByServer(AbilityKey.SERVER_MCP_REGISTRY)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support mcp registry feature.");
        }
        mcpServerCacheHolder.removeMcpServerUpdateTask(mcpName, version);
    }
    
    /**
     * Get agent card with nacos extension detail with target version.
     *
     * @param agentName        name of agent card
     * @param version          target version, if null or empty, get latest version
     * @param registrationType registration type
     * @return agent card with nacos extension detail
     * @throws NacosException if request parameter is invalid or agent card not found or handle error
     */
    public AgentCardDetailInfo getAgentCard(String agentName, String version, String registrationType)
            throws NacosException {
        if (!isAbilitySupportedByServer(AbilityKey.SERVER_AGENT_REGISTRY)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support agent registry feature.");
        }
        QueryAgentCardRequest request = new QueryAgentCardRequest();
        request.setNamespaceId(this.namespaceId);
        request.setAgentName(agentName);
        request.setVersion(version);
        request.setRegistrationType(registrationType);
        QueryAgentCardResponse response = requestToServer(request, QueryAgentCardResponse.class);
        return response.getAgentCardDetailInfo();
    }
    
    /**
     * Release new agent card or new version.
     *
     * <p>
     * If current agent card and version exist, This API will do nothing. If current agent card exist but version not
     * exist, This API will release new version. If current t agent card not exist, This API will release new agent
     * card.
     * </p>
     *
     * @param agentCard        agent card need to release
     * @param registrationType {@link AiConstants.A2a#A2A_ENDPOINT_TYPE_URL} or
     *                         {@link AiConstants.A2a#A2A_ENDPOINT_TYPE_SERVICE}
     * @param setAsLatest      whether set new version as latest, default is false. This parameter is only effect when new version is released.
     *                         If current agent card not exist, whatever this parameter is, it will be set as latest.
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void releaseAgentCard(AgentCard agentCard, String registrationType, boolean setAsLatest)
            throws NacosException {
        LOGGER.info("[{}] Release Agent Card {}, version {}.", uuid, agentCard.getName(), agentCard.getVersion());
        if (!isAbilitySupportedByServer(AbilityKey.SERVER_AGENT_REGISTRY)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support agent registry feature.");
        }
        ReleaseAgentCardRequest request = new ReleaseAgentCardRequest();
        request.setNamespaceId(this.namespaceId);
        request.setAgentName(agentCard.getName());
        request.setRegistrationType(registrationType);
        request.setAgentCard(agentCard);
        request.setSetAsLatest(setAsLatest);
        requestToServer(request, ReleaseAgentCardResponse.class);
    }
    
    /**
     * Register agent endpoint into agent.
     *
     * @param agentName agent name
     * @param endpoint  agent endpoint
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void registerAgentEndpoint(String agentName, AgentEndpoint endpoint) throws NacosException {
        LOGGER.info("[{}] REGISTER Agent endpoint {} into agent {}", uuid, endpoint.toString(), agentName);
        if (!isAbilitySupportedByServer(AbilityKey.SERVER_AGENT_REGISTRY)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support agent registry feature.");
        }
        redoService.cachedAgentEndpointForRedo(agentName, AgentEndpointWrapper.wrap(endpoint));
        doRegisterAgentEndpoint(agentName, endpoint);
    }
    
    /**
     * Batch Register agent endpoint into agent.
     *
     * @param agentName agent name
     * @param endpoints agent endpoints
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void registerAgentEndpoints(String agentName, Collection<AgentEndpoint> endpoints) throws NacosException {
        LOGGER.info("[{}] BATCH REGISTER Agent endpoint size: {} into agent {}", uuid, endpoints.size(), agentName);
        if (!isAbilitySupportedByServer(AbilityKey.SERVER_AGENT_REGISTRY)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support agent registry feature.");
        }
        redoService.cachedAgentEndpointForRedo(agentName, AgentEndpointWrapper.wrap(endpoints));
        doRegisterAgentEndpoint(agentName, endpoints);
    }
    
    /**
     * Actual do register agent endpoint into agent.
     *
     * @param agentName agent name
     * @param endpoint  agent endpoint
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void doRegisterAgentEndpoint(String agentName, AgentEndpoint endpoint) throws NacosException {
        AgentEndpointRequest request = new AgentEndpointRequest();
        request.setNamespaceId(this.namespaceId);
        request.setAgentName(agentName);
        request.setType(AiRemoteConstants.REGISTER_ENDPOINT);
        request.setEndpoint(endpoint);
        requestToServer(request, AgentEndpointResponse.class);
        redoService.agentEndpointRegistered(agentName);
    }
    
    /**
     * Actual do batch register agent endpoint into agent.
     *
     * @param agentName agent name
     * @param endpoints agent endpoints
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void doRegisterAgentEndpoint(String agentName, Collection<AgentEndpoint> endpoints) throws NacosException {
        BatchAgentEndpointRequest request = new BatchAgentEndpointRequest();
        request.setNamespaceId(this.namespaceId);
        request.setAgentName(agentName);
        request.setEndpoints(endpoints);
        requestToServer(request, AgentEndpointResponse.class);
        redoService.agentEndpointRegistered(agentName);
    }
    
    /**
     * Deregister agent endpoint from agent.
     *
     * @param agentName agent name
     * @param endpoint  agent endpoint
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void deregisterAgentEndpoint(String agentName, AgentEndpoint endpoint) throws NacosException {
        LOGGER.info("[{}] DE-REGISTER agent endpoint {} from agent {}", uuid, endpoint.toString(), agentName);
        if (!isAbilitySupportedByServer(AbilityKey.SERVER_AGENT_REGISTRY)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support agent registry feature.");
        }
        redoService.agentEndpointDeregister(agentName);
        doDeregisterAgentEndpoint(agentName, endpoint);
    }
    
    /**
     * Actual do deregister agent endpoint from agent.
     *
     * @param agentName agent name
     * @param endpoint  agent endpoint
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void doDeregisterAgentEndpoint(String agentName, AgentEndpoint endpoint) throws NacosException {
        AgentEndpointRequest request = new AgentEndpointRequest();
        request.setNamespaceId(this.namespaceId);
        request.setAgentName(agentName);
        request.setType(AiRemoteConstants.DE_REGISTER_ENDPOINT);
        request.setEndpoint(endpoint);
        requestToServer(request, AgentEndpointResponse.class);
        redoService.agentEndpointDeregistered(agentName);
    }
    
    /**
     * Subscribe agent card.
     *
     * @param agentName name of agent card
     * @param version   version of agent card
     * @return current agent card
     * @throws NacosException if request parameter is invalid or handle error
     */
    public AgentCardDetailInfo subscribeAgentCard(String agentName, String version) throws NacosException {
        if (!isAbilitySupportedByServer(AbilityKey.SERVER_AGENT_REGISTRY)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support agent registry feature.");
        }
        AgentCardDetailInfo cachedAgentCard = agentCardCacheHolder.getAgentCard(agentName, version);
        if (null == cachedAgentCard) {
            try {
                cachedAgentCard = getAgentCard(agentName, version, StringUtils.EMPTY);
                agentCardCacheHolder.processAgentCardDetailInfo(cachedAgentCard);
            } catch (NacosException e) {
                if (NacosException.NOT_FOUND != e.getErrCode()) {
                    throw e;
                }
            }
            agentCardCacheHolder.addAgentCardUpdateTask(agentName, version);
        }
        return cachedAgentCard;
    }
    
    /**
     * Un-subscribe agent card.
     *
     * @param agentName name of agent card
     * @param version   version of agent card
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void unsubscribeAgentCard(String agentName, String version) throws NacosException {
        if (!isAbilitySupportedByServer(AbilityKey.SERVER_AGENT_REGISTRY)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support agent registry feature.");
        }
        agentCardCacheHolder.removeAgentCardUpdateTask(agentName, version);
    }
    
    public boolean isEnable() {
        return rpcClient.isRunning();
    }
    
    /**
     * Determine whether nacos-server supports the capability.
     *
     * @param abilityKey ability key
     * @return true if supported, otherwise false
     */
    public boolean isAbilitySupportedByServer(AbilityKey abilityKey) {
        return rpcClient.getConnectionAbility(abilityKey) == AbilityStatus.SUPPORTED;
    }
    
    private <T extends Response> T requestToServer(Request request, Class<T> responseClass) throws NacosException {
        Response response = null;
        try {
            if (request instanceof AbstractMcpRequest) {
                AbstractMcpRequest mcpRequest = (AbstractMcpRequest) request;
                request.putAllHeader(getSecurityHeaders(mcpRequest.getNamespaceId(), mcpRequest.getMcpName()));
            } else if (request instanceof AbstractAgentRequest) {
                AbstractAgentRequest agentRequest = (AbstractAgentRequest) request;
                request.putAllHeader(getSecurityHeaders(agentRequest.getNamespaceId(), agentRequest.getAgentName()));
            } else {
                throw new NacosException(400,
                        String.format("Unknown AI request type: %s", request.getClass().getSimpleName()));
            }
            
            response = requestTimeout < 0 ? rpcClient.request(request) : rpcClient.request(request, requestTimeout);
            if (ResponseCode.SUCCESS.getCode() != response.getResultCode()) {
                // If the 403 login operation is triggered, refresh the accessToken of the client
                if (NacosException.NO_RIGHT == response.getErrorCode()) {
                    securityProxy.reLogin();
                }
                throw new NacosException(response.getErrorCode(), response.getMessage());
            }
            if (responseClass.isAssignableFrom(response.getClass())) {
                return (T) response;
            }
            throw new NacosException(NacosException.SERVER_ERROR,
                    String.format("Server return invalid response: %s", response.getClass().getSimpleName()));
        } catch (NacosException e) {
            LOGGER.warn("AI request {} execute failed, {}", request.getClass().getSimpleName(), e.getMessage());
            throw e;
        } catch (Exception e) {
            LOGGER.warn("AI request {} execute failed. ", request.getClass().getSimpleName(), e);
            throw new NacosException(NacosException.SERVER_ERROR, "Request nacos server failed: ", e);
        }
    }
    
    private Map<String, String> getSecurityHeaders(String namespace, String mcpName) {
        RequestResource resource = buildRequestResource(namespace, mcpName);
        return securityProxy.getIdentityContext(resource);
    }
    
    private RequestResource buildRequestResource(String namespaceId, String mcpName) {
        RequestResource.Builder builder = RequestResource.aiBuilder();
        builder.setNamespace(namespaceId);
        builder.setGroup(com.alibaba.nacos.api.common.Constants.DEFAULT_GROUP);
        builder.setResource(null == mcpName ? StringUtils.EMPTY : mcpName);
        return builder.build();
    }
    
    @Override
    public void shutdown() throws NacosException {
        rpcClient.shutdown();
        serverListManager.shutdown();
        if (null != securityProxy) {
            securityProxy.shutdown();
        }
        if (null != executorService) {
            ThreadUtils.shutdownThreadPool(executorService, LOGGER);
        }
    }
}
