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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.remote.AiRemoteConstants;
import com.alibaba.nacos.api.ai.remote.request.AbstractMcpRequest;
import com.alibaba.nacos.api.ai.remote.request.IndexMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.request.McpServerEndpointRequest;
import com.alibaba.nacos.api.ai.remote.request.QueryMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseMcpServerRequest;
import com.alibaba.nacos.api.ai.remote.response.IndexMcpServerResponse;
import com.alibaba.nacos.api.ai.remote.response.McpServerEndpointResponse;
import com.alibaba.nacos.api.ai.remote.response.QueryMcpServerResponse;
import com.alibaba.nacos.api.ai.remote.response.ReleaseMcpServerResponse;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.client.address.AbstractServerListManager;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nacos AI GRPC protocol client.
 *
 * @author xiweng.yy
 */
public class AiGrpcClient implements Closeable {
    
    private final String namespaceId;
    
    private final String uuid;
    
    private final Long requestTimeout;
    
    private final RpcClient rpcClient;
    
    private final AbstractServerListManager serverListManager;
    
    private SecurityProxy securityProxy;
    
    public AiGrpcClient(NacosClientProperties properties) {
        this.namespaceId = initNamespace(properties);
        this.uuid = UUID.randomUUID().toString();
        this.requestTimeout = Long.parseLong(properties.getProperty(AiConstants.AI_REQUEST_TIMEOUT, "-1"));
        this.rpcClient = buildRpcClient(properties);
        this.serverListManager = new NamingServerListManager(properties, namespaceId);
    }
    
    private String initNamespace(NacosClientProperties properties) {
        String tempNamespace = properties.getProperty(PropertyKeyConst.NAMESPACE);
        if (StringUtils.isBlank(tempNamespace)) {
            return Constants.DEFAULT_NAMESPACE_ID;
        }
        return tempNamespace;
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
    public void start() throws NacosException {
        this.serverListManager.start();
        rpcClient.serverListFactory(this.serverListManager);
        rpcClient.start();
        this.securityProxy = new SecurityProxy(this.serverListManager,
                NamingHttpClientManager.getInstance().getNacosRestTemplate());
    }
    
    /**
     * Do query mcp server by mcpId and version.
     *
     * @param mcpId   id of mcp server
     * @param version version of mcp server, if input empty or null, return the latest version
     * @return mcp server detail info
     * @throws NacosException if request parameter is invalid or handle error
     */
    public McpServerDetailInfo queryMcpServer(String mcpId, String version) throws NacosException {
        QueryMcpServerRequest request = new QueryMcpServerRequest();
        request.setNamespaceId(namespaceId);
        request.setMcpId(mcpId);
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
        ReleaseMcpServerRequest request = new ReleaseMcpServerRequest();
        request.setNamespaceId(namespaceId);
        request.setServerSpecification(serverSpecification);
        request.setToolSpecification(toolSpecification);
        ReleaseMcpServerResponse response = requestToServer(request, ReleaseMcpServerResponse.class);
        return response.getMcpId();
    }
    
    /**
     * Register endpoint to target mcp server.
     *
     * @param mcpId     id of mcp server
     * @param address   address of mcp endpoint
     * @param port      port of mcp endpoint
     * @param version   version of mcp endpoint, if empty, the endpoint will return for all mcp version
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void registerMcpServerEndpoint(String mcpId, String address, int port, String version)
            throws NacosException {
        McpServerEndpointRequest request = new McpServerEndpointRequest();
        request.setNamespaceId(namespaceId);
        request.setMcpId(mcpId);
        request.setAddress(address);
        request.setPort(port);
        request.setVersion(version);
        request.setType(AiRemoteConstants.REGISTER_ENDPOINT);
        // TODO redo
        requestToServer(request, McpServerEndpointResponse.class);
    }
    
    /**
     * Index from mcpName to mcpId.
     *
     * @param mcpName mcp name
     * @return mcp id of target namespaceId and mcpName
     * @throws NacosException if request parameter is invalid or handle error
     */
    public String indexMcpNameToMcpId(String mcpName) throws NacosException {
        IndexMcpServerRequest request = new IndexMcpServerRequest();
        request.setNamespaceId(namespaceId);
        request.setMcpName(mcpName);
        IndexMcpServerResponse response = requestToServer(request, IndexMcpServerResponse.class);
        return response.getMcpId();
    }
    
    private <T extends Response> T requestToServer(Request request, Class<T> responseClass) throws NacosException {
        Response response = null;
        try {
            if (request instanceof AbstractMcpRequest) {
                request.putAllHeader(getSecurityHeaders(((AbstractMcpRequest) request).getNamespaceId(),
                        ((AbstractMcpRequest) request).getMcpId()));
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
    
    private Map<String, String> getSecurityHeaders(String namespace, String mcpId) {
        return null;
    }
    
    @Override
    public void shutdown() throws NacosException {
        rpcClient.shutdown();
        serverListManager.shutdown();
        if (null != securityProxy) {
            serverListManager.shutdown();
        }
    }
}
