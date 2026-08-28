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

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDraftRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionCommand;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionSummary;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.StringUtils;

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
    @Since("3.0.0")
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
    @Since("3.0.0")
    default Page<McpServerBasicInfo> listMcpServer(int pageNo, int pageSize) throws NacosException {
        return listMcpServer(StringUtils.EMPTY, pageNo, pageSize);
    }
    
    /**
     * List Mcp Servers in Nacos with page.
     *
     * @param mcpName  mcpName pattern, if empty string or null, will list all Mcp Servers.
     * @param pageNo   the page number of mcp Servers
     * @param pageSize the size of each page
     * @return paged mcp Server list
     * @throws NacosException if fail to list mcp server
     */
    @Since("3.0.0")
    default Page<McpServerBasicInfo> listMcpServer(String mcpName, int pageNo, int pageSize)
        throws NacosException {
        return listMcpServer(Constants.DEFAULT_NAMESPACE_ID, mcpName, pageNo, pageSize);
    }
    
    /**
     * List Mcp Servers in Nacos with page.
     *
     * @param namespaceId namespaceId
     * @param mcpName  mcpName pattern, if empty string or null, will list all Mcp Servers.
     * @param pageNo   the page number of mcp Servers
     * @param pageSize the size of each page
     * @return paged mcp Server list
     * @throws NacosException if fail to list mcp server
     */
    @Since("3.0.1")
    Page<McpServerBasicInfo> listMcpServer(String namespaceId, String mcpName, int pageNo,
        int pageSize) throws NacosException;
    
    /**
     * Blur search first 100 Mcp Servers in Nacos with mcp name pattern.
     *
     * @param mcpName mcpName pattern, if empty string or null, will list all Mcp Servers.
     * @return First 100 mcp server list matched input mcpName pattern.
     * @throws NacosException if fail to search mcp server
     */
    @Since("3.0.0")
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
    @Since("3.0.0")
    default Page<McpServerBasicInfo> searchMcpServer(String mcpName, int pageNo, int pageSize)
        throws NacosException {
        return searchMcpServer(Constants.DEFAULT_NAMESPACE_ID, mcpName, pageNo, pageSize);
    }
    
    /**
     * Blur search first 100 Mcp Servers in Nacos with mcp name pattern.
     * 
     * @param namespaceId namespaceId
     * @param mcpName  mcpName pattern, if empty string or null, will list all Mcp Servers.
     * @param pageNo   the page number of mcp Servers
     * @param pageSize the size of each page
     * @return paged mcp Server list matched input mcpName pattern.
     * @throws NacosException if fail to search mcp server
     */
    @Since("3.0.1")
    Page<McpServerBasicInfo> searchMcpServer(String namespaceId, String mcpName, int pageNo,
        int pageSize) throws NacosException;
    
    /**
     * Get mcp server detail information from Nacos.
     *
     * @param mcpName the mcp server name
     * @return detail information for this mcp server
     * @throws NacosException if fail to get mcp server
     * @deprecated Since 3.3.0, use {@link #listMcpServerVersions(String, String, int, int)}
     *     to select an exact Version and then {@link #getMcpServerVersion(String, String)}.
     *     Planned for removal in Nacos 4.0.0.
     */
    @Since("3.0.0")
    @Deprecated
    default McpServerDetailInfo getMcpServerDetail(String mcpName) throws NacosException {
        return getMcpServerDetail(mcpName, null);
    }
    
    /**
     * Get mcp server detail information from Nacos.
     *
     * @param mcpName the mcp server name
     * @param version the mcp server version
     * @return detail information for this mcp server
     * @throws NacosException if fail to get mcp server
     * @deprecated Since 3.3.0, use {@link #getMcpServerVersion(String, String)}. Planned for
     *     removal in Nacos 4.0.0.
     */
    @Since("3.0.1")
    @Deprecated
    default McpServerDetailInfo getMcpServerDetail(String mcpName, String version)
        throws NacosException {
        return getMcpServerDetail(Constants.DEFAULT_NAMESPACE_ID, mcpName, null, version);
    }
    
    /**
     * Gets mcp server detail.
     *
     * @param namespaceId the namespace id
     * @param mcpName     the mcp name
     * @param version     the version
     * @return the mcp server detail
     * @throws NacosException the nacos exception
     * @deprecated Since 3.3.0, use {@link #getMcpServerVersion(String, String, String)}. Planned
     *     for removal in Nacos 4.0.0.
     */
    @Since("3.0.1")
    @Deprecated
    default McpServerDetailInfo getMcpServerDetail(String namespaceId, String mcpName,
        String version) throws NacosException {
        return getMcpServerDetail(namespaceId, mcpName, null, version);
    }
    
    /**
     * Get mcp server detail information from Nacos.
     *
     * @param namespaceId namespaceId
     * @param mcpName the mcp server name
     * @param mcpId the mcp server id
     * @param version the mcp server version
     * @return detail information for this mcp server
     * @throws NacosException if fail to get mcp server
     * @deprecated Since 3.3.0, use {@link #getMcpServerVersion(String, String, String)} with the
     *     canonical MCP name. Planned for removal in Nacos 4.0.0.
     */
    @Since("3.0.2")
    @Deprecated
    McpServerDetailInfo getMcpServerDetail(String namespaceId, String mcpName, String mcpId,
        String version)
        throws NacosException;
    
    /**
     * Create new local mcp server to Nacos.
     *
     * @param mcpName mcp server name of the new mcp server
     * @param version version of the new mcp server
     * @return mcp server id of the new mcp server
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use {@link #createMcpServer(McpServerDraftRequest)} and
     *     {@link #submitMcpServerVersion(McpServerVersionCommand)}. If review is enabled,
     *     publish the reviewed Version with
     *     {@link #publishMcpServerVersion(McpServerVersionCommand)}. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Since("3.0.0")
    @Deprecated
    default String createLocalMcpServer(String mcpName, String version) throws NacosException {
        return createLocalMcpServer(mcpName, version, null);
    }
    
    /**
     * Create new local mcp server to Nacos.
     *
     * @param mcpName     mcp server name of the new mcp server
     * @param version     version of the new mcp server
     * @param description description of the new mcp server
     * @return mcp server id of the new mcp server
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use {@link #createMcpServer(McpServerDraftRequest)} and
     *     {@link #submitMcpServerVersion(McpServerVersionCommand)}. If review is enabled,
     *     publish the reviewed Version with
     *     {@link #publishMcpServerVersion(McpServerVersionCommand)}. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Since("3.0.0")
    @Deprecated
    default String createLocalMcpServer(String mcpName, String version, String description)
        throws NacosException {
        return createLocalMcpServer(mcpName, version, description, null);
    }
    
    /**
     * Create new local mcp server to Nacos.
     *
     * @param mcpName     mcp server name of the new mcp server
     * @param version     version of the new mcp server
     * @param description description of the new mcp server
     * @param toolSpec    mcp server tools specification, see {@link McpToolSpecification}, nullable.
     * @return mcp server id of the new mcp server
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use {@link #createMcpServer(McpServerDraftRequest)} and
     *     {@link #submitMcpServerVersion(McpServerVersionCommand)}. If review is enabled,
     *     publish the reviewed Version with
     *     {@link #publishMcpServerVersion(McpServerVersionCommand)}. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Since("3.0.0")
    @Deprecated
    default String createLocalMcpServer(String mcpName, String version, String description,
        McpToolSpecification toolSpec) throws NacosException {
        return createLocalMcpServer(mcpName, version, description, null, toolSpec);
    }
    
    /**
     * Create new local mcp server to Nacos.
     *
     * @param mcpName           mcp server name of the new mcp server
     * @param version           version of the new mcp server
     * @param description       description of the new mcp server
     * @param localServerConfig custom config of the new mcp server
     * @param toolSpec          mcp server tools specification, see {@link McpToolSpecification}, nullable.
     * @return mcp server id of the new mcp server
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use {@link #createMcpServer(McpServerDraftRequest)} and
     *     {@link #submitMcpServerVersion(McpServerVersionCommand)}. If review is enabled,
     *     publish the reviewed Version with
     *     {@link #publishMcpServerVersion(McpServerVersionCommand)}. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Since("3.0.0")
    @Deprecated
    default String createLocalMcpServer(String mcpName, String version, String description,
        Map<String, Object> localServerConfig, McpToolSpecification toolSpec)
        throws NacosException {
        McpServerBasicInfo serverSpec = new McpServerBasicInfo();
        serverSpec.setName(mcpName);
        serverSpec.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion(version);
        serverSpec.setVersionDetail(versionDetail);
        serverSpec.setDescription(description);
        serverSpec.setLocalServerConfig(localServerConfig);
        return createLocalMcpServer(mcpName, serverSpec, toolSpec);
    }
    
    /**
     * Create new local mcp server to Nacos.
     *
     * @param mcpName    mcp server name of the new mcp server
     * @param serverSpec mcp server specification, see {@link McpServerBasicInfo} which `type` is
     *                   {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}.
     * @param toolSpec   mcp server tools specification, see {@link McpToolSpecification}, nullable.
     * @return mcp server id of the new mcp server
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use {@link #createMcpServer(McpServerDraftRequest)} and
     *     {@link #submitMcpServerVersion(McpServerVersionCommand)}. If review is enabled,
     *     publish the reviewed Version with
     *     {@link #publishMcpServerVersion(McpServerVersionCommand)}. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Since("3.0.0")
    @Deprecated
    default String createLocalMcpServer(String mcpName, McpServerBasicInfo serverSpec,
        McpToolSpecification toolSpec)
        throws NacosException {
        if (Objects.isNull(serverSpec)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "Mcp server specification cannot be null.");
        }
        if (!AiConstants.Mcp.MCP_PROTOCOL_STDIO.equalsIgnoreCase(serverSpec.getProtocol())) {
            throw new NacosException(NacosException.INVALID_PARAM,
                String.format("Mcp server type must be `local`, input is `%s`",
                    serverSpec.getProtocol()));
        }
        return createMcpServer(mcpName, serverSpec, toolSpec, null);
    }
    
    /**
     * Create new remote mcp server to Nacos.
     *
     * @param mcpName      mcp server name of the new mcp server
     * @param version      version of the new mcp server
     * @param protocol     mcp protocol type not {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}.
     * @param endpointSpec mcp server endpoint specification, see {@link McpEndpointSpec}, can't be null.
     * @return mcp server id of the new mcp server
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use {@link #createMcpServer(McpServerDraftRequest)} and
     *     {@link #submitMcpServerVersion(McpServerVersionCommand)}. If review is enabled,
     *     publish the reviewed Version with
     *     {@link #publishMcpServerVersion(McpServerVersionCommand)}. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Since("3.0.0")
    @Deprecated
    default String createRemoteMcpServer(String mcpName, String version, String protocol,
        McpEndpointSpec endpointSpec) throws NacosException {
        return createRemoteMcpServer(mcpName, version, protocol, new McpServerRemoteServiceConfig(),
            endpointSpec);
    }
    
    /**
     * Create new remote mcp server to Nacos.
     *
     * @param mcpName             mcp server name of the new mcp server
     * @param version             version of the new mcp server
     * @param protocol            mcp protocol type not {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}.
     * @param remoteServiceConfig remote service configuration, see {@link McpServerRemoteServiceConfig}.
     * @param endpointSpec        mcp server endpoint specification, see {@link McpEndpointSpec}, can't be null.
     * @return mcp server id of the new mcp server
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use {@link #createMcpServer(McpServerDraftRequest)} and
     *     {@link #submitMcpServerVersion(McpServerVersionCommand)}. If review is enabled,
     *     publish the reviewed Version with
     *     {@link #publishMcpServerVersion(McpServerVersionCommand)}. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Since("3.0.0")
    @Deprecated
    default String createRemoteMcpServer(String mcpName, String version, String protocol,
        McpServerRemoteServiceConfig remoteServiceConfig, McpEndpointSpec endpointSpec)
        throws NacosException {
        return createRemoteMcpServer(mcpName, version, null, protocol, remoteServiceConfig,
            endpointSpec);
    }
    
    /**
     * Create new remote mcp server to Nacos.
     *
     * @param mcpName             mcp server name of the new mcp server
     * @param version             version of the new mcp server
     * @param description         description of the new mcp server
     * @param protocol            mcp protocol type not {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}.
     * @param remoteServiceConfig remote service configuration, see {@link McpServerRemoteServiceConfig}.
     * @param endpointSpec        mcp server endpoint specification, see {@link McpEndpointSpec}, can't be null.
     * @return mcp server id of the new mcp server
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use {@link #createMcpServer(McpServerDraftRequest)} and
     *     {@link #submitMcpServerVersion(McpServerVersionCommand)}. If review is enabled,
     *     publish the reviewed Version with
     *     {@link #publishMcpServerVersion(McpServerVersionCommand)}. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Since("3.0.0")
    @Deprecated
    default String createRemoteMcpServer(String mcpName, String version, String description,
        String protocol,
        McpServerRemoteServiceConfig remoteServiceConfig, McpEndpointSpec endpointSpec)
        throws NacosException {
        return createRemoteMcpServer(mcpName, version, description, protocol, remoteServiceConfig,
            endpointSpec, null);
    }
    
    /**
     * Create new remote mcp server to Nacos.
     *
     * @param mcpName             mcp server name of the new mcp server
     * @param version             version of the new mcp server
     * @param description         description of the new mcp server
     * @param protocol            mcp protocol type not {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}.
     * @param remoteServiceConfig remote service configuration, see {@link McpServerRemoteServiceConfig}.
     * @param endpointSpec        mcp server endpoint specification, see {@link McpEndpointSpec}, can't be null.
     * @param toolSpec            mcp server tools specification, see {@link McpToolSpecification}, nullable.
     * @return mcp server id of the new mcp server
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use {@link #createMcpServer(McpServerDraftRequest)} and
     *     {@link #submitMcpServerVersion(McpServerVersionCommand)}. If review is enabled,
     *     publish the reviewed Version with
     *     {@link #publishMcpServerVersion(McpServerVersionCommand)}. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Since("3.0.0")
    @Deprecated
    default String createRemoteMcpServer(String mcpName, String version, String description,
        String protocol,
        McpServerRemoteServiceConfig remoteServiceConfig, McpEndpointSpec endpointSpec,
        McpToolSpecification toolSpec)
        throws NacosException {
        McpServerBasicInfo serverSpec = new McpServerBasicInfo();
        serverSpec.setName(mcpName);
        serverSpec.setProtocol(protocol);
        ServerVersionDetail detail = new ServerVersionDetail();
        detail.setVersion(version);
        serverSpec.setVersionDetail(detail);
        serverSpec.setDescription(description);
        serverSpec.setRemoteServerConfig(remoteServiceConfig);
        return createRemoteMcpServer(mcpName, serverSpec, toolSpec, endpointSpec);
    }
    
    /**
     * Create new remote mcp server to Nacos.
     *
     * @param mcpName      mcp server name of the new mcp server
     * @param serverSpec   mcp server specification, see {@link McpServerBasicInfo} which `type` is not
     *                     {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}.
     * @param endpointSpec mcp server endpoint specification, see {@link McpEndpointSpec}, can't be null.
     * @return mcp server id of the new mcp server
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use {@link #createMcpServer(McpServerDraftRequest)} and
     *     {@link #submitMcpServerVersion(McpServerVersionCommand)}. If review is enabled,
     *     publish the reviewed Version with
     *     {@link #publishMcpServerVersion(McpServerVersionCommand)}. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Since("3.0.0")
    @Deprecated
    default String createRemoteMcpServer(String mcpName, McpServerBasicInfo serverSpec,
        McpEndpointSpec endpointSpec)
        throws NacosException {
        return createRemoteMcpServer(mcpName, serverSpec, null, endpointSpec);
    }
    
    /**
     * Create new remote mcp server to Nacos.
     *
     * @param mcpName      mcp server name of the new mcp server
     * @param serverSpec   mcp server specification, see {@link McpServerBasicInfo} which `type` is not
     *                     {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}.
     * @param toolSpec     mcp server tools specification, see {@link McpToolSpecification}, nullable.
     * @param endpointSpec mcp server endpoint specification, see {@link McpEndpointSpec}, nullable.
     * @return mcp server id of the new mcp server
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use {@link #createMcpServer(McpServerDraftRequest)} and
     *     {@link #submitMcpServerVersion(McpServerVersionCommand)}. If review is enabled,
     *     publish the reviewed Version with
     *     {@link #publishMcpServerVersion(McpServerVersionCommand)}. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Since("3.0.0")
    @Deprecated
    default String createRemoteMcpServer(String mcpName, McpServerBasicInfo serverSpec,
        McpToolSpecification toolSpec,
        McpEndpointSpec endpointSpec) throws NacosException {
        if (Objects.isNull(serverSpec)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "Mcp server specification cannot be null.");
        }
        if (AiConstants.Mcp.MCP_PROTOCOL_STDIO.equalsIgnoreCase(serverSpec.getProtocol())) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "Mcp server type cannot be `local` or empty.");
        }
        if (Objects.isNull(endpointSpec)) {
            throw new NacosException(NacosException.INVALID_PARAM,
                "Mcp server endpoint specification cannot be null.");
        }
        return createMcpServer(mcpName, serverSpec, toolSpec, endpointSpec);
    }
    
    /**
     * Create new mcp server to Nacos.
     *
     * @param mcpName      mcp server name of the new mcp server
     * @param serverSpec   mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpec     mcp server tools specification, see {@link McpToolSpecification}, nullable.
     * @param endpointSpec mcp server endpoint specification, see {@link McpEndpointSpec}, nullable if `type` is
     *                     {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}.
     * @return mcp server id of the new mcp server
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use {@link #createMcpServer(McpServerDraftRequest)} and
     *     {@link #submitMcpServerVersion(McpServerVersionCommand)}. If review is enabled,
     *     publish the reviewed Version with
     *     {@link #publishMcpServerVersion(McpServerVersionCommand)}. Planned for removal in
     *     Nacos 4.0.0.
     */
    @Since("3.0.0")
    @Deprecated
    default String createMcpServer(String mcpName, McpServerBasicInfo serverSpec,
        McpToolSpecification toolSpec,
        McpEndpointSpec endpointSpec) throws NacosException {
        return createMcpServer(Constants.DEFAULT_NAMESPACE_ID, mcpName, serverSpec, toolSpec,
            endpointSpec);
    }
    
    /**
     * Create new mcp server to Nacos.
     *
     * @param namespaceId namespaceId
     * @param mcpName      mcp server name of the new mcp server
     * @param serverSpec   mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpec     mcp server tools specification, see {@link McpToolSpecification}, nullable.
     * @param endpointSpec mcp server endpoint specification, see {@link McpEndpointSpec}, nullable if `type` is
     *                     {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}.
     * @return mcp server id of the new mcp server
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use
     *     {@link #createMcpServer(String, McpServerDraftRequest)} and
     *     {@link #submitMcpServerVersion(String, McpServerVersionCommand)}. If review is
     *     enabled, publish the reviewed Version with
     *     {@link #publishMcpServerVersion(String, McpServerVersionCommand)}. Planned for
     *     removal in Nacos 4.0.0.
     */
    @Since("3.0.1")
    @Deprecated
    String createMcpServer(String namespaceId, String mcpName, McpServerBasicInfo serverSpec,
        McpToolSpecification toolSpec,
        McpEndpointSpec endpointSpec) throws NacosException;
    
    /**
     * Create one new MCP draft Version.
     *
     * @param namespaceId namespace identifier
     * @param request complete draft content
     * @return persisted draft detail
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    McpServerVersionDetail createMcpServer(String namespaceId,
        McpServerDraftRequest request) throws NacosException;
    
    /**
     * Create one new MCP draft Version in the default namespace.
     *
     * @param request complete draft content
     * @return persisted draft detail
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default McpServerVersionDetail createMcpServer(McpServerDraftRequest request)
        throws NacosException {
        return createMcpServer(Constants.DEFAULT_NAMESPACE_ID, request);
    }
    
    /**
     * Update existed mcp server to Nacos Default namespace.
     * <p>
     * Please Query Full information by {@link #getMcpServerDetail(String)} and input Full information to this method.
     * This method will full cover update the old information.
     * </p>
     *
     * @param mcpName      mcp server name of the new mcp server
     * @param serverSpec   mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpec     mcp server tools specification, see {@link McpToolSpecification}, nullable.
     * @param endpointSpec mcp server endpoint specification, see {@link McpEndpointSpec}, nullable if `type` is
     *                     {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}.
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use {@link #createMcpServer(McpServerDraftRequest)} for a
     *     new Version or {@link #updateMcpServer(McpServerDraftRequest)} for an existing
     *     draft, then use {@link #submitMcpServerVersion(McpServerVersionCommand)} and, when
     *     review is enabled, {@link #publishMcpServerVersion(McpServerVersionCommand)}.
     *     Planned for removal in Nacos 4.0.0.
     */
    @Since("3.0.0")
    @Deprecated
    default boolean updateMcpServer(String mcpName, McpServerBasicInfo serverSpec,
        McpToolSpecification toolSpec,
        McpEndpointSpec endpointSpec) throws NacosException {
        return updateMcpServer(mcpName, true, serverSpec, toolSpec, endpointSpec);
    }
    
    /**
     * Update existed mcp server to Nacos Default namespace.
     * <p>
     * Please Query Full information by {@link #getMcpServerDetail(String)} and input Full information to this method.
     * This method will full cover update the old information.
     * </p>
     *
     * @param mcpName      mcp server name of the new mcp server
     * @param isLatest     publish current version to latest
     * @param serverSpec   mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpec     mcp server tools specification, see {@link McpToolSpecification}, nullable.
     * @param endpointSpec mcp server endpoint specification, see {@link McpEndpointSpec}, nullable if `type` is
     *                     {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}.
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use {@link #createMcpServer(McpServerDraftRequest)} for a
     *     new Version or {@link #updateMcpServer(McpServerDraftRequest)} for an existing
     *     draft, then use {@link #submitMcpServerVersion(McpServerVersionCommand)} and, when
     *     review is enabled, {@link #publishMcpServerVersion(McpServerVersionCommand)}.
     *     Planned for removal in Nacos 4.0.0.
     */
    @Since("3.0.1")
    @Deprecated
    default boolean updateMcpServer(String mcpName, boolean isLatest, McpServerBasicInfo serverSpec,
        McpToolSpecification toolSpec,
        McpEndpointSpec endpointSpec) throws NacosException {
        return updateMcpServer(Constants.DEFAULT_NAMESPACE_ID, mcpName, isLatest, serverSpec,
            toolSpec, endpointSpec);
    }
    
    /**
     * Update existed mcp server to Nacos.
     * <p>
     * Please Query Full information by {@link #getMcpServerDetail(String)} and input Full information to this method.
     * This method will full cover update the old information.
     * </p>
     *
     * @param namespaceId  namespaceId
     * @param mcpName      mcp server name of the new mcp server
     * @param isLatest     publish current version to latest
     * @param serverSpec   mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpec     mcp server tools specification, see {@link McpToolSpecification}, nullable.
     * @param endpointSpec mcp server endpoint specification, see {@link McpEndpointSpec}, nullable if `type` is
     *                     {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}.
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use
     *     {@link #createMcpServer(String, McpServerDraftRequest)} for a new Version or
     *     {@link #updateMcpServer(String, McpServerDraftRequest)} for an existing draft,
     *     then use {@link #submitMcpServerVersion(String, McpServerVersionCommand)} and, when
     *     review is enabled,
     *     {@link #publishMcpServerVersion(String, McpServerVersionCommand)}. Planned for
     *     removal in Nacos 4.0.0.
     */
    @Since("3.0.1")
    @Deprecated
    default boolean updateMcpServer(String namespaceId, String mcpName, boolean isLatest,
        McpServerBasicInfo serverSpec,
        McpToolSpecification toolSpec, McpEndpointSpec endpointSpec) throws NacosException {
        return updateMcpServer(namespaceId, mcpName, isLatest, serverSpec, toolSpec, endpointSpec,
            false);
    }
    
    /**
     * Update existed mcp server to Nacos.
     * <p>
     * Please Query Full information by {@link #getMcpServerDetail(String)} and input Full information to this method.
     * This method will full cover update the old information.
     * </p>
     *
     * @param namespaceId  namespaceId
     * @param mcpName      mcp server name of the new mcp server
     * @param isLatest     publish current version to latest
     * @param serverSpec   mcp server specification, see {@link McpServerBasicInfo}
     * @param toolSpec     mcp server tools specification, see {@link McpToolSpecification}, nullable.
     * @param endpointSpec mcp server endpoint specification, see {@link McpEndpointSpec}, nullable if `type` is
     *                     {@link AiConstants.Mcp#MCP_PROTOCOL_STDIO}.
     * @param overrideExisting  if replace all the instances when update the mcp server
     * @return {@code true} if create success, {@code false} otherwise
     * @throws NacosException if fail to create mcp server.
     * @deprecated Since 3.3.0, use
     *     {@link #createMcpServer(String, McpServerDraftRequest)} for a new Version or
     *     {@link #updateMcpServer(String, McpServerDraftRequest)} for an existing draft,
     *     then use {@link #submitMcpServerVersion(String, McpServerVersionCommand)} and, when
     *     review is enabled,
     *     {@link #publishMcpServerVersion(String, McpServerVersionCommand)}. Planned for
     *     removal in Nacos 4.0.0.
     */
    @Since("3.1.1")
    @Deprecated
    boolean updateMcpServer(String namespaceId, String mcpName, boolean isLatest,
        McpServerBasicInfo serverSpec, McpToolSpecification toolSpec,
        McpEndpointSpec endpointSpec, boolean overrideExisting) throws NacosException;
    
    /**
     * Replace one exact current MCP draft.
     *
     * @param namespaceId namespace identifier
     * @param request complete replacement content
     * @return updated draft detail
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    McpServerVersionDetail updateMcpServer(String namespaceId,
        McpServerDraftRequest request) throws NacosException;
    
    /**
     * Replace one exact current MCP draft in the default namespace.
     *
     * @param request complete replacement content
     * @return updated draft detail
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default McpServerVersionDetail updateMcpServer(McpServerDraftRequest request)
        throws NacosException {
        return updateMcpServer(Constants.DEFAULT_NAMESPACE_ID, request);
    }
    
    /**
     * Delete existed mcp server from Nacos.
     *
     * @param mcpName mcp server name of the new mcp server
     * @return {@code true} if delete success, {@code false} otherwise
     * @throws NacosException if fail to delete mcp server.
     */
    @Since("3.0.0")
    default boolean deleteMcpServer(String mcpName) throws NacosException {
        return deleteMcpServer(Constants.DEFAULT_NAMESPACE_ID, mcpName, null, null);
    }
    
    /**
     * Delete existed mcp server from Nacos.
     *
     * @param namespaceId namespaceId
     * @param mcpName mcp server name of the new mcp server
     * @param mcpId mcp server id of the new mcp server
     * @param version mcp version of the new mcp server
     * @return {@code true} if delete success, {@code false} otherwise
     * @throws NacosException if fail to delete mcp server.
     */
    @Since("3.0.2")
    boolean deleteMcpServer(String namespaceId, String mcpName, String mcpId, String version)
        throws NacosException;
    
    /**
     * List management summaries for one MCP Server's Versions.
     *
     * @param namespaceId namespace identifier
     * @param mcpName canonical MCP name
     * @param status optional Version status
     * @param pageNo page number
     * @param pageSize page size
     * @return MCP Server Version page
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    Page<McpServerVersionSummary> listMcpServerVersions(String namespaceId, String mcpName,
        String status, int pageNo, int pageSize) throws NacosException;
    
    /**
     * List MCP Server Version summaries in the default namespace.
     *
     * @param mcpName canonical MCP name
     * @param status optional Version status
     * @param pageNo page number
     * @param pageSize page size
     * @return MCP Server Version page
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default Page<McpServerVersionSummary> listMcpServerVersions(String mcpName, String status,
        int pageNo, int pageSize) throws NacosException {
        return listMcpServerVersions(Constants.DEFAULT_NAMESPACE_ID, mcpName, status, pageNo,
            pageSize);
    }
    
    /**
     * Get one exact MCP Server Version.
     *
     * @param namespaceId namespace identifier
     * @param mcpName canonical MCP name
     * @param version exact Version
     * @return MCP Server Version detail
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    McpServerVersionDetail getMcpServerVersion(String namespaceId, String mcpName,
        String version) throws NacosException;
    
    /**
     * Get one exact MCP Server Version in the default namespace.
     *
     * @param mcpName canonical MCP name
     * @param version exact Version
     * @return MCP Server Version detail
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default McpServerVersionDetail getMcpServerVersion(String mcpName, String version)
        throws NacosException {
        return getMcpServerVersion(Constants.DEFAULT_NAMESPACE_ID, mcpName, version);
    }
    
    /**
     * Delete one exact current MCP draft.
     *
     * @param namespaceId namespace identifier
     * @param command exact Version command
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    void deleteMcpServerDraft(String namespaceId, McpServerVersionCommand command)
        throws NacosException;
    
    /**
     * Delete one exact current MCP draft in the default namespace.
     *
     * @param command exact Version command
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default void deleteMcpServerDraft(McpServerVersionCommand command) throws NacosException {
        deleteMcpServerDraft(Constants.DEFAULT_NAMESPACE_ID, command);
    }
    
    /**
     * Submit one exact MCP working Version.
     *
     * @param namespaceId namespace identifier
     * @param command exact Version command
     * @return resulting Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    McpServerVersionSummary submitMcpServerVersion(String namespaceId,
        McpServerVersionCommand command) throws NacosException;
    
    /**
     * Submit one exact MCP working Version in the default namespace.
     *
     * @param command exact Version command
     * @return resulting Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default McpServerVersionSummary submitMcpServerVersion(
        McpServerVersionCommand command) throws NacosException {
        return submitMcpServerVersion(Constants.DEFAULT_NAMESPACE_ID, command);
    }
    
    /**
     * Publish one exact reviewed MCP Version.
     *
     * @param namespaceId namespace identifier
     * @param command exact Version command
     * @return online Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    McpServerVersionSummary publishMcpServerVersion(String namespaceId,
        McpServerVersionCommand command) throws NacosException;
    
    /**
     * Publish one exact reviewed MCP Version in the default namespace.
     *
     * @param command exact Version command
     * @return online Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default McpServerVersionSummary publishMcpServerVersion(
        McpServerVersionCommand command) throws NacosException {
        return publishMcpServerVersion(Constants.DEFAULT_NAMESPACE_ID, command);
    }
    
    /**
     * Force-publish one exact MCP working Version.
     *
     * @param namespaceId namespace identifier
     * @param command exact Version command
     * @return online Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    McpServerVersionSummary forcePublishMcpServerVersion(String namespaceId,
        McpServerVersionCommand command) throws NacosException;
    
    /**
     * Force-publish one exact MCP working Version in the default namespace.
     *
     * @param command exact Version command
     * @return online Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default McpServerVersionSummary forcePublishMcpServerVersion(
        McpServerVersionCommand command) throws NacosException {
        return forcePublishMcpServerVersion(Constants.DEFAULT_NAMESPACE_ID, command);
    }
    
    /**
     * Move one exact reviewed MCP Version back to draft.
     *
     * @param namespaceId namespace identifier
     * @param command exact Version command
     * @return draft Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    McpServerVersionSummary redraftMcpServerVersion(String namespaceId,
        McpServerVersionCommand command) throws NacosException;
    
    /**
     * Move one exact reviewed MCP Version back to draft in the default namespace.
     *
     * @param command exact Version command
     * @return draft Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default McpServerVersionSummary redraftMcpServerVersion(
        McpServerVersionCommand command) throws NacosException {
        return redraftMcpServerVersion(Constants.DEFAULT_NAMESPACE_ID, command);
    }
    
    /**
     * Bring one exact offline MCP Version online.
     *
     * @param namespaceId namespace identifier
     * @param command exact Version command
     * @return online Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    McpServerVersionSummary onlineMcpServerVersion(String namespaceId,
        McpServerVersionCommand command) throws NacosException;
    
    /**
     * Bring one exact offline MCP Version online in the default namespace.
     *
     * @param command exact Version command
     * @return online Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default McpServerVersionSummary onlineMcpServerVersion(
        McpServerVersionCommand command) throws NacosException {
        return onlineMcpServerVersion(Constants.DEFAULT_NAMESPACE_ID, command);
    }
    
    /**
     * Take one exact online MCP Version offline.
     *
     * @param namespaceId namespace identifier
     * @param command exact Version command
     * @return offline Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    McpServerVersionSummary offlineMcpServerVersion(String namespaceId,
        McpServerVersionCommand command) throws NacosException;
    
    /**
     * Take one exact online MCP Version offline in the default namespace.
     *
     * @param command exact Version command
     * @return offline Version summary
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default McpServerVersionSummary offlineMcpServerVersion(
        McpServerVersionCommand command) throws NacosException {
        return offlineMcpServerVersion(Constants.DEFAULT_NAMESPACE_ID, command);
    }
    
    /**
     * Replace custom MCP labels.
     *
     * @param namespaceId namespace identifier
     * @param request labels update request
     * @return complete label map after replacement
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    Map<String, String> updateMcpServerLabels(String namespaceId,
        McpServerLabelsUpdateRequest request) throws NacosException;
    
    /**
     * Replace custom MCP labels in the default namespace.
     *
     * @param request labels update request
     * @return complete label map after replacement
     * @throws NacosException when the request fails
     */
    @Since("3.3.0")
    default Map<String, String> updateMcpServerLabels(McpServerLabelsUpdateRequest request)
        throws NacosException {
        return updateMcpServerLabels(Constants.DEFAULT_NAMESPACE_ID, request);
    }
}
