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

package com.alibaba.nacos.mcpregistry.service;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.mcp.regsitryapi.GetServerForm;
import com.alibaba.nacos.ai.form.mcp.regsitryapi.ListServerForm;
import com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServer;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerList;
import com.alibaba.nacos.api.ai.model.mcp.registry.NacosMcpRegistryServerDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.Remote;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.alibaba.nacos.ai.constant.Constants.MCP_LIST_SEARCH_BLUR;

/**
 * a service for mcp registry api implementation.
 * @author xinluo 
 */
@Service
public class NacosMcpRegistryService {
    
    private final McpServerOperationService mcpServerOperationService;

    public NacosMcpRegistryService(McpServerOperationService mcpServerOperationService) {
        this.mcpServerOperationService = mcpServerOperationService;
    }

    /**
     * List mcp server from mcpServerOperationService and convert the result to {@link McpRegistryServerList}.
     * @param listServerForm listServerParams
     * @return {@link McpRegistryServerList}
     */
    public McpRegistryServerList listMcpServers(ListServerForm listServerForm) {
        int limit = listServerForm.getLimit();
        int offset = listServerForm.getOffset();
        String namespaceId = listServerForm.getNamespaceId();
        String serverName = listServerForm.getServerName();
        Page<McpServerBasicInfo> servers = mcpServerOperationService.listMcpServerWithOffset(namespaceId, serverName, 
                MCP_LIST_SEARCH_BLUR, offset, limit);
        
        List<McpRegistryServer> finalServers = servers.getPageItems().stream().map((item) -> {
            McpRegistryServer server = new McpRegistryServer();
            server.setId(item.getId());
            server.setName(item.getName());
            server.setDescription(item.getDescription());
            server.setRepository(item.getRepository());
            server.setVersion_detail(item.getVersionDetail());
            return server;
        }).collect(Collectors.toList());

        McpRegistryServerList serverList = new McpRegistryServerList();
        serverList.setTotal_count(servers.getTotalCount());
        serverList.setServers(finalServers);
        return serverList;
    }

    /**
     * Get mcp server details.
     * @param id mcp server id
     * @param getServerForm additional params
     *                      version mcp server version
     * @return {@link McpRegistryServer}
     * @throws NacosException if request parameter is invalid or handle error
     */
    public McpRegistryServerDetail getServer(String id, GetServerForm getServerForm)
            throws NacosException {
        McpServerDetailInfo mcpServerDetail = mcpServerOperationService.getMcpServerDetail(null, id, 
                null, getServerForm.getVersion());
        if (Objects.isNull(mcpServerDetail)) {
            return null;
        }
        McpRegistryServerDetail result = new McpRegistryServerDetail();
        result.setId(mcpServerDetail.getId());
        result.setName(mcpServerDetail.getName());
        result.setDescription(mcpServerDetail.getDescription());
        result.setRepository(mcpServerDetail.getRepository());
        result.setVersion_detail(mcpServerDetail.getVersionDetail());
        
        List<McpEndpointInfo> backendEndpoints = mcpServerDetail.getBackendEndpoints();
        String frontProtocol = mcpServerDetail.getFrontProtocol();
        if (CollectionUtils.isNotEmpty(backendEndpoints)) {
            List<Remote> remotes = backendEndpoints.stream().map((item) -> {
                Remote remote = new Remote();
                remote.setTransport_type(frontProtocol.replace("mcp-", ""));
                remote.setUrl(String.format("%s://%s:%d%s", Constants.PROTOCOL_TYPE_HTTP,
                        item.getAddress(), item.getPort(), item.getPath()));
                return remote;
            }).collect(Collectors.toList());
            result.setRemotes(remotes);
        }
        
        return result;
    }

    /**
     * Create a new mcp server. this will generate a uuid for the mcp server.
     * @param server mcp server detail info.
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void createMcpServer(NacosMcpRegistryServerDetail server) throws NacosException {
        mcpServerOperationService.createMcpServer(server.getNacosNamespaceId(), buildMcpServerSpecification(server), 
                server.getMcpToolSpecification(), server.getNacosMcpEndpointSpec());
    }

    /**
     * Update an exist mcp server.
     * @param serverDetail mcp server detail info. id must include in serverDetail.
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void updateMcpServer(NacosMcpRegistryServerDetail serverDetail) throws NacosException {
        mcpServerOperationService.updateMcpServer(serverDetail.getNacosNamespaceId(),  true,
                buildMcpServerSpecification(serverDetail), serverDetail.getMcpToolSpecification(), serverDetail.getNacosMcpEndpointSpec());
    }

    /**
     * A convertor convert {@link NacosMcpRegistryServerDetail} to {@link McpServerBasicInfo}.
     * @param server mcp server detail.
     * @return mcp server basic info.
     */
    private McpServerBasicInfo buildMcpServerSpecification(NacosMcpRegistryServerDetail server) {
        McpServerBasicInfo info = new McpServerStorageInfo();
        info.setName(server.getName());
        info.setId(server.getId());
        info.setDescription(server.getDescription());
        info.setVersionDetail(server.getVersion_detail());
        info.setRepository(server.getRepository());
        McpServerRemoteServiceConfig remoteServiceConfig = new McpServerRemoteServiceConfig();
        McpEndpointSpec mcpEndpointSpec = server.getNacosMcpEndpointSpec();
        String exportPath = mcpEndpointSpec.getData().get(Constants.SERVER_EXPORT_PATH_KEY);
        remoteServiceConfig.setExportPath(exportPath);
        info.setRemoteServerConfig(remoteServiceConfig);
        return info;
    }

    /**
     * Get tools info about the given version of the mcp server.
     * @param serverId mcp server id.
     * @param version the version of the mcp server.
     * @return tools info about the mcp server.
     * @throws NacosException if request parameter is invalid or handle error
     */
    public McpToolSpecification getTools(String serverId, String version) throws NacosException {
        McpServerDetailInfo mcpServerDetail = mcpServerOperationService.getMcpServerDetail(null, 
                serverId, null, version);
        if (Objects.isNull(mcpServerDetail)) {
            return null;
        }
        return mcpServerDetail.getToolSpec();
    }
}
