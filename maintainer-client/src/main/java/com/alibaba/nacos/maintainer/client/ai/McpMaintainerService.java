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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

import java.util.Map;
import java.util.Objects;

/**
 * Nacos AI module MCP relative maintainer service.
 *
 * @author xiweng.yy
 */
public interface McpMaintainerService {
    
    /**
     * List first 100 Mcp Servers in Nacos.
     *
     * @return Fist 100 mcp server list.
     * @throws NacosException if fail to list mcp server
     */
    default Page<McpServerBasicInfo> listMcpServer() throws NacosException {
        return listMcpServer(1, 100);
    }
    
    /**
     * List Mcp Servers in Nacos with page.
     *
     * @param pageNo   the page number of mcp Servers
     * @param pageSize the size of each page
     * @return paged mcp Server list
     * @throws NacosException if fail to list mcp server
     */
    Page<McpServerBasicInfo> listMcpServer(int pageNo, int pageSize) throws NacosException;
    
    /**
     * Blur search first 100 Mcp Servers in Nacos with mcp name pattern.
     *
     * @param mcpName mcpName pattern, if empty string or null, will list all Mcp Servers.
     * @return First 100 mcp server list matched input mcpName pattern.
     * @throws NacosException if fail to search mcp server
     */
    default Page<McpServerBasicInfo> searchMcpServer(String mcpName) throws NacosException {
        return searchMcpServer(mcpName, 1, 100);
    }
    
    /**
     * Blur search first 100 Mcp Servers in Nacos with mcp name pattern.
     *
     * @param mcpName  mcpName pattern, if empty string or null, will list all Mcp Servers.
     * @param pageNo   the page number of mcp Servers
     * @param pageSize the size of each page
     * @return paged mcp Server list matched input mcpName pattern.
     * @throws NacosException if fail to search mcp server
     */
    Page<McpServerBasicInfo> searchMcpServer(String mcpName, int pageNo, int pageSize) throws NacosException;
    
    /**
     * Get mcp server detail information from Nacos.
     *
     * @param mcpName the mcp server name
     * @return detail information for this mcp server
     * @throws NacosException if fail to get mcp server
     */
    McpServerDetailInfo getMcpServerDetail(String mcpName) throws NacosException;
    
    /**
     * Create new local mcp server to Nacos.
     *
     * @param mcpName mcp server name of the new mcp server
     * @param version version of the new mcp server
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     */
    default boolean createLocalMcpServer(String mcpName, String version) throws NacosException {
        return createLocalMcpServer(mcpName, version, null);
    }
    
    /**
     * Create new local mcp server to Nacos.
     *
     * @param mcpName     mcp server name of the new mcp server
     * @param version     version of the new mcp server
     * @param description description of the new mcp server
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     */
    default boolean createLocalMcpServer(String mcpName, String version, String description) throws NacosException {
        return createLocalMcpServer(mcpName, version, description, null);
    }
    
    /**
     * Create new local mcp server to Nacos.
     *
     * @param mcpName     mcp server name of the new mcp server
     * @param version     version of the new mcp server
     * @param description description of the new mcp server
     * @param toolSpec    mcp server tools specification, see {@link McpTool}, nullable.
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     */
    default boolean createLocalMcpServer(String mcpName, String version, String description, McpTool toolSpec)
            throws NacosException {
        return createLocalMcpServer(mcpName, version, description, null, toolSpec);
    }
    
    /**
     * Create new local mcp server to Nacos.
     *
     * @param mcpName           mcp server name of the new mcp server
     * @param version           version of the new mcp server
     * @param description       description of the new mcp server
     * @param localServerConfig custom config of the new mcp server
     * @param toolSpec          mcp server tools specification, see {@link McpTool}, nullable.
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     */
    default boolean createLocalMcpServer(String mcpName, String version, String description,
            Map<String, Object> localServerConfig, McpTool toolSpec) throws NacosException {
        McpServerBasicInfo serverSpec = new McpServerBasicInfo();
        serverSpec.setName(mcpName);
        serverSpec.setType(AiConstants.Mcp.MCP_TYPE_LOCAL);
        serverSpec.setVersion(version);
        serverSpec.setDescription(description);
        serverSpec.setLocalServerConfig(localServerConfig);
        return createLocalMcpServer(mcpName, serverSpec, toolSpec);
    }
    
    /**
     * Create new local mcp server to Nacos.
     *
     * @param mcpName    mcp server name of the new mcp server
     * @param serverSpec mcp server specification, see {@link McpServerBasicInfo} which `type` is
     *                   {@link AiConstants.Mcp#MCP_TYPE_LOCAL}.
     * @param toolSpec   mcp server tools specification, see {@link McpTool}, nullable.
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     */
    default boolean createLocalMcpServer(String mcpName, McpServerBasicInfo serverSpec, McpTool toolSpec)
            throws NacosException {
        if (Objects.isNull(serverSpec)) {
            throw new NacosException(NacosException.INVALID_PARAM, "Mcp server specification cannot be null.");
        }
        if (!AiConstants.Mcp.MCP_TYPE_LOCAL.equalsIgnoreCase(serverSpec.getType())) {
            throw new NacosException(NacosException.INVALID_PARAM,
                    String.format("Mcp server type must be `local`, input is `%s`", serverSpec.getType()));
        }
        return createMcpServer(mcpName, serverSpec, toolSpec, null);
    }
    
    /**
     * Create new remote mcp server to Nacos.
     *
     * @param mcpName      mcp server name of the new mcp server
     * @param version      version of the new mcp server
     * @param isStream     whether is streamable remote mcp server.
     * @param endpointSpec mcp server endpoint specification, see {@link McpEndpointSpec}, can't be null.
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     */
    default boolean createRemoteMcpServer(String mcpName, String version, boolean isStream,
            McpEndpointSpec endpointSpec) throws NacosException {
        return createRemoteMcpServer(mcpName, version, isStream, null, endpointSpec);
    }
    
    /**
     * Create new remote mcp server to Nacos.
     *
     * @param mcpName             mcp server name of the new mcp server
     * @param version             version of the new mcp server
     * @param isStream            whether is streamable remote mcp server.
     * @param remoteServiceConfig remote service configuration, see {@link McpServerRemoteServiceConfig}.
     * @param endpointSpec        mcp server endpoint specification, see {@link McpEndpointSpec}, can't be null.
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     */
    default boolean createRemoteMcpServer(String mcpName, String version, boolean isStream,
            McpServerRemoteServiceConfig remoteServiceConfig, McpEndpointSpec endpointSpec) throws NacosException {
        return createRemoteMcpServer(mcpName, version, null, isStream, remoteServiceConfig, endpointSpec);
    }
    
    /**
     * Create new remote mcp server to Nacos.
     *
     * @param mcpName             mcp server name of the new mcp server
     * @param version             version of the new mcp server
     * @param description         description of the new mcp server
     * @param isStream            whether is streamable remote mcp server.
     * @param remoteServiceConfig remote service configuration, see {@link McpServerRemoteServiceConfig}.
     * @param endpointSpec        mcp server endpoint specification, see {@link McpEndpointSpec}, can't be null.
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     */
    default boolean createRemoteMcpServer(String mcpName, String version, String description, boolean isStream,
            McpServerRemoteServiceConfig remoteServiceConfig, McpEndpointSpec endpointSpec) throws NacosException {
        return createRemoteMcpServer(mcpName, version, description, isStream, remoteServiceConfig, endpointSpec, null);
    }
    
    /**
     * Create new remote mcp server to Nacos.
     *
     * @param mcpName             mcp server name of the new mcp server
     * @param version             version of the new mcp server
     * @param description         description of the new mcp server
     * @param isStream            whether is streamable remote mcp server.
     * @param remoteServiceConfig remote service configuration, see {@link McpServerRemoteServiceConfig}.
     * @param endpointSpec        mcp server endpoint specification, see {@link McpEndpointSpec}, can't be null.
     * @param toolSpec            mcp server tools specification, see {@link McpTool}, nullable.
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     */
    default boolean createRemoteMcpServer(String mcpName, String version, String description, boolean isStream,
            McpServerRemoteServiceConfig remoteServiceConfig, McpEndpointSpec endpointSpec, McpTool toolSpec)
            throws NacosException {
        McpServerBasicInfo serverSpec = new McpServerBasicInfo();
        serverSpec.setName(mcpName);
        serverSpec.setType(isStream ? AiConstants.Mcp.MCP_TYPE_STREAM_REMOTE : AiConstants.Mcp.MCP_TYPE_SSE_REMOTE);
        serverSpec.setVersion(version);
        serverSpec.setDescription(description);
        serverSpec.setRemoteServerConfig(remoteServiceConfig);
        return createRemoteMcpServer(mcpName, serverSpec, endpointSpec);
    }
    
    /**
     * Create new remote mcp server to Nacos.
     *
     * @param mcpName      mcp server name of the new mcp server
     * @param serverSpec   mcp server specification, see {@link McpServerBasicInfo} which `type` is
     *                     {@link AiConstants.Mcp#MCP_TYPE_SSE_REMOTE} or
     *                     {@link AiConstants.Mcp#MCP_TYPE_STREAM_REMOTE}.
     * @param endpointSpec mcp server endpoint specification, see {@link McpEndpointSpec}, can't be null.
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     */
    default boolean createRemoteMcpServer(String mcpName, McpServerBasicInfo serverSpec, McpEndpointSpec endpointSpec)
            throws NacosException {
        return createRemoteMcpServer(mcpName, serverSpec, null, endpointSpec);
    }
    
    /**
     * Create new remote mcp server to Nacos.
     *
     * @param mcpName      mcp server name of the new mcp server
     * @param serverSpec   mcp server specification, see {@link McpServerBasicInfo} which `type` is
     *                     {@link AiConstants.Mcp#MCP_TYPE_SSE_REMOTE} or
     *                     {@link AiConstants.Mcp#MCP_TYPE_STREAM_REMOTE}.
     * @param toolSpec     mcp server tools specification, see {@link McpTool}, nullable.
     * @param endpointSpec mcp server endpoint specification, see {@link McpEndpointSpec}, nullable.
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     */
    default boolean createRemoteMcpServer(String mcpName, McpServerBasicInfo serverSpec, McpTool toolSpec,
            McpEndpointSpec endpointSpec) throws NacosException {
        if (Objects.isNull(serverSpec)) {
            throw new NacosException(NacosException.INVALID_PARAM, "Mcp server specification cannot be null.");
        }
        if (AiConstants.Mcp.MCP_TYPE_LOCAL.equalsIgnoreCase(serverSpec.getType())) {
            throw new NacosException(NacosException.INVALID_PARAM, "Mcp server type cannot be `local` or empty.");
        }
        if (Objects.isNull(endpointSpec)) {
            throw new NacosException(NacosException.INVALID_PARAM, "Mcp server endpoint specification cannot be null.");
        }
        return createMcpServer(mcpName, serverSpec, toolSpec, endpointSpec);
    }
    
    /**
     * Create new mcp server to Nacos.
     *
     * @param mcpName      mcp server name of the new mcp server
     * @param serverSpec   mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpec     mcp server tools specification, see {@link McpTool}, nullable.
     * @param endpointSpec mcp server endpoint specification, see {@link McpEndpointSpec}, nullable if `type` is
     *                     {@link AiConstants.Mcp#MCP_TYPE_LOCAL}.
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     */
    boolean createMcpServer(String mcpName, McpServerBasicInfo serverSpec, McpTool toolSpec,
            McpEndpointSpec endpointSpec) throws NacosException;
    
    /**
     * Update existed mcp server to Nacos.
     * <p>
     *  Please Query Full information by {@link #getMcpServerDetail(String)} and input Full information to this method.
     *  This method will full cover update the old information.
     * </p>
     *
     * @param mcpName      mcp server name of the new mcp server
     * @param serverSpec   mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpec     mcp server tools specification, see {@link McpTool}, nullable.
     * @param endpointSpec mcp server endpoint specification, see {@link McpEndpointSpec}, nullable if `type` is
     *                     {@link AiConstants.Mcp#MCP_TYPE_LOCAL}.
     * @return {@code true} if update success, {@code false} otherwise
     * @throws NacosException if fail to update mcp server.
     */
    boolean updateMcpServer(String mcpName, McpServerBasicInfo serverSpec, McpTool toolSpec,
            McpEndpointSpec endpointSpec) throws NacosException;
    
    /**
     * Delete existed mcp server from Nacos.
     *
     * @param mcpName mcp server name of the new mcp server
     * @return {@code true} if delete success, {@code false} otherwise
     * @throws NacosException if fail to delete mcp server.
     */
    boolean deleteMcpServer(String mcpName) throws NacosException;
}
