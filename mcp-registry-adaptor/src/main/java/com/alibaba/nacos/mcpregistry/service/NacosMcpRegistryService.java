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
import com.alibaba.nacos.ai.index.McpServerIndex;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
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
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.mcpregistry.form.GetServerForm;
import com.alibaba.nacos.mcpregistry.form.ListServerForm;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.alibaba.nacos.ai.constant.Constants.MCP_LIST_SEARCH_BLUR;

/**
 * a service for mcp registry api implementation.
 *
 * @author xinluo
 */
@Service
public class NacosMcpRegistryService {
    
    private final McpServerOperationService mcpServerOperationService;
    
    private final NamespaceOperationService namespaceOperationService;
    
    private final McpServerIndex mcpServerIndex;
    
    public NacosMcpRegistryService(McpServerOperationService mcpServerOperationService,
            NamespaceOperationService namespaceOperationService, McpServerIndex mcpServerIndex) {
        this.mcpServerOperationService = mcpServerOperationService;
        this.namespaceOperationService = namespaceOperationService;
        this.mcpServerIndex = mcpServerIndex;
    }
    
    /**
     * List mcp server from mcpServerOperationService and convert the result to {@link McpRegistryServerList}.
     *
     * @param listServerForm listServerParams
     * @return {@link McpRegistryServerList}
     */
    public McpRegistryServerList listMcpServers(ListServerForm listServerForm) {
        int limit = listServerForm.getLimit();
        int offset = listServerForm.getOffset();
        String namespaceId = listServerForm.getNamespaceId();
        String serverName = listServerForm.getServerName();
        Collection<String> namespaceIdList =
                StringUtils.isNotEmpty(namespaceId) ? Collections.singletonList(namespaceId)
                        : fetchOrderedNamespaceList();
        
        Page<McpServerBasicInfo> servers = listMcpServerByNamespaceList(namespaceIdList, serverName, offset, limit);
        
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
    
    private List<String> fetchOrderedNamespaceList() {
        return namespaceOperationService.getNamespaceList().stream()
                .sorted(Comparator.comparing(Namespace::getNamespace)).map(Namespace::getNamespace).toList();
    }
    
    private Page<McpServerBasicInfo> listMcpServerByNamespaceList(Collection<String> namespaceIdList, String serverName,
            int offset, int limit) {
        Page<McpServerBasicInfo> result = new Page<>();
        int totalCount = 0;
        int remindOffset = offset;
        for (String each : namespaceIdList) {
            Page<McpServerBasicInfo> namespaceResult;
            if (result.getPageItems().size() >= limit) {
                namespaceResult = listMcpServerByNamespace(each, serverName, 0, 1);
            } else {
                int remindLimit = limit - result.getPageItems().size();
                namespaceResult = listMcpServerByNamespace(each, serverName, remindOffset, remindLimit);
                if (namespaceResult.getPageItems().isEmpty()) {
                    remindOffset -= namespaceResult.getTotalCount();
                } else {
                    result.getPageItems().addAll(namespaceResult.getPageItems());
                    remindOffset = 0;
                }
            }
            totalCount += namespaceResult.getTotalCount();
        }
        result.setTotalCount(totalCount);
        result.setPagesAvailable(0 == limit ? 0 : (int) Math.ceil((double) totalCount / (double) limit));
        result.setPageNumber(0 == limit ? 1 : (offset / limit + 1));
        return result;
    }
    
    private Page<McpServerBasicInfo> listMcpServerByNamespace(String namespaceId, String serverName, int offset,
            int limit) {
        return mcpServerOperationService.listMcpServerWithOffset(namespaceId, serverName, MCP_LIST_SEARCH_BLUR, offset,
                limit);
    }
    
    /**
     * Get mcp server details.
     *
     * @param id            mcp server id
     * @param getServerForm additional params version mcp server version
     * @return {@link McpRegistryServer}
     * @throws NacosException if request parameter is invalid or handle error
     */
    public McpRegistryServerDetail getServer(String id, GetServerForm getServerForm) throws NacosException {
        McpServerIndexData indexData = mcpServerIndex.getMcpServerById(id);
        if (Objects.isNull(indexData)) {
            return null;
        }
        McpServerDetailInfo mcpServerDetail = mcpServerOperationService.getMcpServerDetail(indexData.getNamespaceId(),
                id, null, getServerForm.getVersion());
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
                remote.setUrl(
                        String.format("%s://%s:%d%s", Constants.PROTOCOL_TYPE_HTTP, item.getAddress(), item.getPort(),
                                item.getPath()));
                return remote;
            }).collect(Collectors.toList());
            result.setRemotes(remotes);
        }
        
        return result;
    }
    
    /**
     * Create a new mcp server. this will generate a uuid for the mcp server.
     *
     * @param server mcp server detail info.
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void createMcpServer(NacosMcpRegistryServerDetail server) throws NacosException {
        mcpServerOperationService.createMcpServer(server.getNacosNamespaceId(), buildMcpServerSpecification(server),
                server.getMcpToolSpecification(), server.getNacosMcpEndpointSpec());
    }
    
    /**
     * Update an exist mcp server.
     *
     * @param serverDetail mcp server detail info. id must include in serverDetail.
     * @throws NacosException if request parameter is invalid or handle error
     */
    public void updateMcpServer(NacosMcpRegistryServerDetail serverDetail) throws NacosException {
        McpServerIndexData indexData = mcpServerIndex.getMcpServerById(serverDetail.getId());
        if (Objects.isNull(indexData)) {
            throw new NacosApiException(NacosApiException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("mcp server `%s` not found", serverDetail.getId()));
        }
        mcpServerOperationService.updateMcpServer(indexData.getNamespaceId(), true,
                buildMcpServerSpecification(serverDetail), serverDetail.getMcpToolSpecification(),
                serverDetail.getNacosMcpEndpointSpec());
    }
    
    /**
     * A convertor convert {@link NacosMcpRegistryServerDetail} to {@link McpServerBasicInfo}.
     *
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
     *
     * @param serverId mcp server id.
     * @param version  the version of the mcp server.
     * @return tools info about the mcp server.
     * @throws NacosException if request parameter is invalid or handle error
     */
    public McpToolSpecification getTools(String serverId, String version) throws NacosException {
        McpServerIndexData indexData = mcpServerIndex.getMcpServerById(serverId);
        if (Objects.isNull(indexData)) {
            return null;
        }
        McpServerDetailInfo mcpServerDetail = mcpServerOperationService.getMcpServerDetail(indexData.getNamespaceId(),
                serverId, null, version);
        return mcpServerDetail.getToolSpec();
    }
}
