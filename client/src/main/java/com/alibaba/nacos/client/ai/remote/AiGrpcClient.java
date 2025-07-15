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
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.ai.remote.request.AbstractMcpRequest;
import com.alibaba.nacos.api.ai.remote.request.McpServerEndpointRequest;
import com.alibaba.nacos.api.ai.remote.request.QueryMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.response.McpServerEndpointResponse;
import com.alibaba.nacos.api.ai.remote.response.QueryMcpServerResponse;
import com.alibaba.nacos.api.ai.remote.response.ReleaseMcpServerResponse;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.client.address.AbstractServerListManager;
import com.alibaba.nacos.client.ai.cache.NacosMcpServerCacheHolder;
import com.alibaba.nacos.client.ai.remote.redo.AiGrpcRedoService;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.core.NamingServerListManager;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.AppNameUtils;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientConfigFactory;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.grpc.GrpcClientConfig;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
    
    private SecurityProxy securityProxy;
    
    private NacosMcpServerCacheHolder mcpServerCacheHolder;
    
    public AiGrpcClient(String namespaceId, NacosClientProperties properties) {
        this.namespaceId = namespaceId;
        this.uuid = UUID.randomUUID().toString();
        this.requestTimeout = Long.parseLong(properties.getProperty(AiConstants.AI_REQUEST_TIMEOUT, "-1"));
        this.rpcClient = buildRpcClient(properties);
        this.serverListManager = new NamingServerListManager(properties, namespaceId);
        this.redoService = new AiGrpcRedoService(properties, this);
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
    public void start(NacosMcpServerCacheHolder mcpServerCacheHolder) throws NacosException {
        this.mcpServerCacheHolder = mcpServerCacheHolder;
        this.serverListManager.start();
        this.rpcClient.registerConnectionListener(this.redoService);
        this.rpcClient.serverListFactory(this.serverListManager);
        this.rpcClient.start();
        this.securityProxy = new SecurityProxy(this.serverListManager,
                NamingHttpClientManager.getInstance().getNacosRestTemplate());
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
    public String releaseMcpServer(McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification)
            throws NacosException {
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
     * @return latest version mcp server
     * @throws NacosException if request parameter is invalid or handle error
     */
    public McpServerDetailInfo subscribeMcpServer(String mcpName) throws NacosException {
        if (!isAbilitySupportedByServer(AbilityKey.SERVER_MCP_REGISTRY)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support mcp registry feature.");
        }
        McpServerDetailInfo cachedServer = mcpServerCacheHolder.getMcpServer(mcpName, null);
        if (null == cachedServer) {
            cachedServer = queryMcpServer(mcpName, null);
            mcpServerCacheHolder.processMcpServerDetailInfo(cachedServer);
            mcpServerCacheHolder.addMcpServerUpdateTask(mcpName);
        }
        return cachedServer;
    }
    
    /**
     * Un-subscribe mcp server.
     *
     * @param mcpName   name of mcp server
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void unsubscribeMcpServer(String mcpName) throws NacosException {
        if (!isAbilitySupportedByServer(AbilityKey.SERVER_MCP_REGISTRY)) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support mcp registry feature.");
        }
        mcpServerCacheHolder.removeMcpServerUpdateTask(mcpName);
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
                request.putAllHeader(getSecurityHeaders(((AbstractMcpRequest) request).getNamespaceId(),
                        ((AbstractMcpRequest) request).getMcpName()));
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
            throw e;
        } catch (Exception e) {
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
    }
}
