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

package com.alibaba.nacos.api.ai;

import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;

/**
 * Nacos AI client service interface.
 *
 * @author xiweng.yy
 */
public interface AiService extends A2aService {
    
    /**
     * Get mcp server detail info for latest version.
     *
     * @param mcpName name of mcp server
     * @return detail information of MCP server
     * @throws NacosException if request parameter is invalid or mcp server not found or handle error
     */
    default McpServerDetailInfo getMcpServer(String mcpName) throws NacosException {
        return getMcpServer(mcpName, null);
    }
    
    /**
     * Get mcp server detail info.
     *
     * @param mcpName name of MCP name
     * @param version version of MCP, if null, will get the latest version
     * @return detail information of MCP server
     * @throws NacosException if request parameter is invalid or mcp server not found or handle error
     */
    McpServerDetailInfo getMcpServer(String mcpName, String version) throws NacosException;
    
    /**
     * Release new mcp server or release new version of exist mcp server request.
     *
     * <p>
     *     If mcp server is not exist, will create an new mcp server with parameter specification.
     *     If mcp server is exist, but version in specification is new one, request will create a new version of mcp server.
     *     If mcp server is exist, and version in specification is exist, request will do nothing.
     * </p>
     *
     * @param serverSpecification mcp server specification
     * @param toolSpecification   mcp server tool specification
     * @return mcp id
     * @throws NacosException if request parameter is invalid or handle error
     */
    default String releaseMcpServer(McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification)
            throws NacosException {
        return releaseMcpServer(serverSpecification, toolSpecification, null);
    }
    
    /**
     * Release new mcp server or release new version of exist mcp server request.
     *
     * <p>
     *     If mcp server is not exist, will create an new mcp server with parameter specification.
     *     If mcp server is exist, but version in specification is new one, request will create a new version of mcp server.
     *     If mcp server is exist, and version in specification is exist, request will do nothing.
     * </p>
     *
     * @param serverSpecification mcp server specification
     * @param toolSpecification   mcp server tool specification
     * @param endpointSpecification mcp server endpoint specification, optional, if null, will create ref service auto.
     * @return mcp id
     * @throws NacosException if request parameter is invalid or handle error
     */
    String releaseMcpServer(McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
            McpEndpointSpec endpointSpecification) throws NacosException;
    
    /**
     * Register an endpoint into target mcp server for all version.
     *
     * @param mcpName   name of mcp server
     * @param address   address of endpoint
     * @param port      port of endpoint
     * @throws NacosException if request parameter is invalid or handle error
     */
    default void registerMcpServerEndpoint(String mcpName, String address, int port) throws NacosException {
        registerMcpServerEndpoint(mcpName, address, port, null);
    }
    
    /**
     * Register an endpoint into target mcp server for target version.
     *
     * @param mcpName   name of mcp server
     * @param address   address of endpoint
     * @param port      port of endpoint
     * @param version   version of mcp server
     * @throws NacosException if request parameter is invalid or handle error
     */
    void registerMcpServerEndpoint(String mcpName, String address, int port, String version) throws NacosException;
    
    /**
     * Deregister an endpoint from target mcp server for any version.
     *
     * <p>
     *     The registered endpoint must be registered by this client service.
     *     If the registered endpoint is registered by other client service, the endpoint will fail to deregister.
     * </p>
     *
     * @param mcpName   name of mcp server
     * @param address   address of endpoint
     * @param port      port of endpoint
     * @throws NacosException if request parameter is invalid or handle error
     */
    void deregisterMcpServerEndpoint(String mcpName, String address, int port) throws NacosException;
    
    /**
     * Subscribe mcp server.
     *
     * @param mcpName           name of mcp server
     * @param mcpServerListener listener of mcp server, callback when mcp server is changed
     * @return The detail info of mcp server at current time
     * @throws NacosException if request parameter is invalid or handle error
     */
    default McpServerDetailInfo subscribeMcpServer(String mcpName, AbstractNacosMcpServerListener mcpServerListener)
            throws NacosException {
        return subscribeMcpServer(mcpName, null, mcpServerListener);
    }
    
    /**
     * Subscribe mcp server.
     *
     * @param mcpName           name of mcp server
     * @param version           version of mcp server
     * @param mcpServerListener listener of mcp server, callback when mcp server is changed
     * @return The detail info of mcp server at current time, nullable if agent card not found
     * @throws NacosException if request parameter is invalid or handle error
     */
    McpServerDetailInfo subscribeMcpServer(String mcpName, String version,
            AbstractNacosMcpServerListener mcpServerListener) throws NacosException;
    
    /**
     * Un-subscribe mcp server.
     *
     * @param mcpName           name of mcp server
     * @param mcpServerListener listener of mcp server
     * @throws NacosException if request parameter is invalid or handle error
     */
    default void unsubscribeMcpServer(String mcpName, AbstractNacosMcpServerListener mcpServerListener)
            throws NacosException {
        unsubscribeMcpServer(mcpName, null, mcpServerListener);
    }
    
    /**
     * Un-subscribe mcp server.
     *
     * @param mcpName           name of mcp server
     * @param version           version of mcp server
     * @param mcpServerListener listener of mcp server
     * @throws NacosException if request parameter is invalid or handle error
     */
    void unsubscribeMcpServer(String mcpName, String version, AbstractNacosMcpServerListener mcpServerListener)
            throws NacosException;
    
    /**
     * Shutdown the AI service and close resources.
     *
     * @throws NacosException exception.
     */
    void shutdown() throws NacosException;
    
}
