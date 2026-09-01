/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.api.ai.AgentTransportMode;
import com.alibaba.nacos.api.ai.model.agent.ClientLivenessInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;

/**
 * Routes protocol-neutral MCP Client operations and selects sticky Endpoint owners.
 *
 * @author Nacos
 */
public class McpTransportRouter {
    
    private final AgentGrpcTransport sharedGrpcTransport;
    
    private final McpGrpcTransport grpcTransport;
    
    private final McpHttpTransport httpTransport;
    
    public McpTransportRouter(AgentGrpcTransport sharedGrpcTransport,
        McpGrpcTransport grpcTransport, McpHttpTransport httpTransport) {
        this.sharedGrpcTransport = sharedGrpcTransport;
        this.grpcTransport = grpcTransport;
        this.httpTransport = httpTransport;
    }
    
    /**
     * Query one exact or latest serving MCP Version using the configured transport policy.
     *
     * @param mcpName MCP name
     * @param version optional exact Version
     * @return MCP detail
     * @throws NacosException when no transport can complete the query
     */
    public McpServerDetailInfo queryMcpServer(String mcpName, String version)
        throws NacosException {
        McpTransport transport = select();
        try {
            McpServerDetailInfo result = transport.queryMcpServer(mcpName, version);
            recordHttpSuccess(transport);
            return result;
        } catch (NacosException e) {
            if (transport.getType() != AgentTransportType.GRPC || !canFallbackRead(e)) {
                throw e;
            }
            McpServerDetailInfo result = httpTransport.queryMcpServer(mcpName, version);
            recordHttpSuccess(httpTransport);
            return result;
        }
    }
    
    /**
     * Release one MCP Version using the selected transport.
     *
     * @param serverSpecification MCP Server specification
     * @param toolSpecification optional Tool specification
     * @param resourceSpecification optional Resource specification
     * @param endpointSpecification optional Endpoint specification
     * @param createDraft whether to create a lifecycle Draft instead of direct-online release
     * @return stable internal MCP id
     * @throws NacosException when release fails
     */
    public String releaseMcpServer(McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification,
        boolean createDraft) throws NacosException {
        McpTransport transport = select();
        String result = transport.releaseMcpServer(serverSpecification, toolSpecification,
            resourceSpecification, endpointSpecification, createDraft);
        recordHttpSuccess(transport);
        return result;
    }
    
    /**
     * Select the sticky owner transport for a new Runtime Endpoint publication.
     *
     * @return selected transport type
     */
    public AgentTransportType selectPublicationTransport() {
        return select().getType();
    }
    
    /**
     * Register an MCP Runtime Endpoint through its sticky owner transport.
     *
     * @param mcpName MCP name
     * @param address Endpoint address
     * @param port Endpoint port
     * @param version optional serving Version
     * @param ownerTransport sticky owner transport
     * @return HTTP Client liveness metadata, or {@code null} for gRPC
     * @throws NacosException when registration fails
     */
    public ClientLivenessInfo registerMcpServerEndpoint(String mcpName, String address, int port,
        String version, AgentTransportType ownerTransport) throws NacosException {
        McpTransport transport = getTransport(ownerTransport);
        ClientLivenessInfo result = transport.registerMcpServerEndpoint(mcpName, address, port,
            version);
        recordHttpSuccess(transport);
        return result;
    }
    
    /**
     * Deregister an MCP Runtime Endpoint through its sticky owner transport.
     *
     * @param mcpName MCP name
     * @param address Endpoint address
     * @param port Endpoint port
     * @param ownerTransport sticky owner transport
     * @throws NacosException when deregistration fails
     */
    public void deregisterMcpServerEndpoint(String mcpName, String address, int port,
        AgentTransportType ownerTransport) throws NacosException {
        McpTransport transport = getTransport(ownerTransport);
        transport.deregisterMcpServerEndpoint(mcpName, address, port);
        recordHttpSuccess(transport);
    }
    
    /**
     * Renew the shared HTTP Client through the MCP heartbeat endpoint.
     *
     * @return Client liveness metadata
     * @throws NacosException when heartbeat fails
     */
    public ClientLivenessInfo heartbeatMcpServerEndpoints() throws NacosException {
        ClientLivenessInfo result = httpTransport.heartbeatMcpServerEndpoints();
        recordHttpSuccess(httpTransport);
        return result;
    }
    
    private McpTransport select() {
        AgentTransportMode mode = sharedGrpcTransport.getMode();
        if (mode == AgentTransportMode.HTTP) {
            return httpTransport;
        }
        if (mode == AgentTransportMode.GRPC || sharedGrpcTransport.isMcpAvailable()) {
            return grpcTransport;
        }
        return httpTransport;
    }
    
    private McpTransport getTransport(AgentTransportType type) {
        return type == AgentTransportType.GRPC ? grpcTransport : httpTransport;
    }
    
    private void recordHttpSuccess(McpTransport transport) {
        if (transport.getType() == AgentTransportType.HTTP) {
            sharedGrpcTransport.recordHttpSuccess();
        }
    }
    
    private boolean canFallbackRead(NacosException exception) {
        if (sharedGrpcTransport.getMode() != AgentTransportMode.AUTO) {
            return false;
        }
        int code = exception.getErrCode();
        return !sharedGrpcTransport.isConnected()
            || code == NacosException.CLIENT_DISCONNECT || code == NacosException.UN_REGISTER
            || isGrpcUnavailable(exception);
    }
    
    private boolean isGrpcUnavailable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof StatusRuntimeException) {
                return ((StatusRuntimeException) current).getStatus()
                    .getCode() == Status.Code.UNAVAILABLE;
            }
            if (current instanceof StatusException) {
                return ((StatusException) current).getStatus().getCode() == Status.Code.UNAVAILABLE;
            }
            current = current.getCause();
        }
        return false;
    }
}
