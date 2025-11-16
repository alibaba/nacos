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
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerList;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerResponse;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.OfficialMeta;
import com.alibaba.nacos.api.ai.model.mcp.registry.Remote;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.mcpregistry.form.ListServerForm;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
    
    private static final int DEFAULT_HTTP_PORT = 80;
    
    private static final int DEFAULT_HTTPS_PORT = 443;

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
     * List mcp server from mcpServerOperationService and convert the result to
     * {@link McpRegistryServerList}.
     *
     * @param listServerForm listServerParams
     * @return {@link McpRegistryServerList}
     */
    public McpRegistryServerList listMcpServers(ListServerForm listServerForm) {
        int limit = listServerForm.getLimit();
        int offset = listServerForm.getOffset();
        String namespaceId = listServerForm.getNamespaceId();
        String serverName = listServerForm.getServerName();
        Collection<String> namespaceIdList = StringUtils.isNotEmpty(namespaceId)
                ? Collections.singletonList(namespaceId)
                : fetchOrderedNamespaceList();

        List<McpServerBasicInfo> servers = listMcpServerByNamespaceList(namespaceIdList, serverName, offset, limit);

        // Build detail list by fetching per-item detail via getServer for consistency
        List<ServerResponse> finalServers = servers.stream().map((item) -> {
            try {
                return getServer(item.getName(), item.getNamespaceId(), null);
            } catch (Exception ignore) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
        McpRegistryServerList serverList = new McpRegistryServerList();
        serverList.setServers(finalServers);
        return serverList;
    }
    
    private List<String> fetchOrderedNamespaceList() {
        return namespaceOperationService.getNamespaceList().stream()
                .sorted(Comparator.comparing(Namespace::getNamespace)).map(Namespace::getNamespace).toList();
    }
    
    private List<McpServerBasicInfo> listMcpServerByNamespaceList(Collection<String> namespaceIdList, String serverName,
            int offset, int limit) {
        List<McpServerBasicInfo> result = new ArrayList<>();
        
        // 如果 limit <= 0，直接返回空
        if (limit <= 0) {
            return result;
        }
        
        int remainOffset = offset;
        
        for (String namespaceId : namespaceIdList) {
            if (result.size() >= limit) {
                break;
            }
            Page<McpServerBasicInfo> countPage = mcpServerOperationService.listMcpServerWithPage(namespaceId, serverName,
                    MCP_LIST_SEARCH_BLUR, 1, 1);
            int totalCount = countPage.getTotalCount();
            if (totalCount == 0) {
                continue;
            }
            if (remainOffset >= totalCount) {
                remainOffset -= totalCount;
                continue;
            }
            int remaining = limit - result.size();
            int pageSize = limit;
            int pageNum = remainOffset / pageSize + 1;
            int pageOffset = remainOffset % pageSize;
            while (remaining > 0) {
                Page<McpServerBasicInfo> dataPage = mcpServerOperationService.listMcpServerWithPage(namespaceId, serverName, 
                        MCP_LIST_SEARCH_BLUR, pageNum, pageSize);
                if (CollectionUtils.isEmpty(dataPage.getPageItems())) {
                    break;
                }
                int startIdx = (pageNum == remainOffset / pageSize + 1) ? pageOffset : 0;
                int endIdx = Math.min(startIdx + remaining, dataPage.getPageItems().size());
                if (startIdx < dataPage.getPageItems().size()) {
                    result.addAll(dataPage.getPageItems().subList(startIdx, endIdx));
                    remaining -= (endIdx - startIdx);
                }
                if (endIdx < dataPage.getPageItems().size()) {
                    break;
                }
                pageNum++;
            }
            remainOffset = 0;
        }
        return result;
    }
    
    /**
     * Get mcp server detail for the specified name and version.
     * if namespaceId is null, search in all namespaces and return the first mcp server found.
     * @param name mcp server name
     * @param namespaceId namespace id
     * @param version mcp server version
     * @return mcp server detail
     * @throws NacosException if nacos operation fails
     */
    public ServerResponse getServer(String name, String namespaceId, String version) throws NacosException {
        try {
            McpServerDetailInfo mcpServerDetail = mcpServerOperationService.getMcpServerDetail(namespaceId, null, name,
                    version);
            return buildServerResponse(mcpServerDetail);
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND) {
                return null;
            }
            throw e;
        }
    }

    /**
     * Get all versions of the specified MCP server.
     * @param namespaceId the namespaceId of mcp server, if not specified, search in all namespaces.
     * @param serverName the server name of mcp server.
     * @return all versions of the found MCP server as {@link McpRegistryServerList}
     */
    public McpRegistryServerList getServerVersions(String namespaceId, String serverName) throws NacosException {
        try {
            McpServerDetailInfo mcpServerDetail = mcpServerOperationService.getMcpServerDetail(namespaceId, null, serverName, null);
            List<ServerVersionDetail> allVersions = mcpServerDetail.getAllVersions();
            allVersions.sort(Comparator.comparing(ServerVersionDetail::getVersion));
            List<ServerResponse> serverResponses = allVersions.stream().map((server) -> {
                try {
                    return mcpServerOperationService.getMcpServerDetail(namespaceId, null, serverName, server.getVersion());
                } catch (NacosException e) {
                    throw new RuntimeException(e);
                }
            }).map(this::buildServerResponse).collect(Collectors.toList());
            McpRegistryServerList registryServerList = new McpRegistryServerList();
            registryServerList.setServers(serverResponses);
            McpRegistryServerList.Metadata metadata = new McpRegistryServerList.Metadata();
            metadata.setCount(registryServerList.getServers().size());
            registryServerList.setMetadata(metadata);
            return registryServerList;
        } catch (NacosException e) {
            if (e.getErrCode() == NacosException.NOT_FOUND) {
                return null;
            }
            throw e;
        }
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

    /**
     * Prefer frontend endpoints, fallback to backend.
     */
    private List<McpEndpointInfo> pickEndpoints(List<McpEndpointInfo> frontend, List<McpEndpointInfo> backend) {
        if (CollectionUtils.isNotEmpty(frontend)) {
            return frontend;
        }
        return backend;
    }
    
    /**
     * Map endpoints to remotes with default headers.
     */
    private List<Remote> toRemotes(List<McpEndpointInfo> endpoints, String type) {
        if (CollectionUtils.isEmpty(endpoints)) {
            return null;
        }
        return endpoints.stream().map((item) -> {
            Remote remote = new Remote();
            remote.setType(type);
            String url = buildUrl(item);
            remote.setUrl(url);
            remote.setHeaders(item.getHeaders());
            return remote;
        }).collect(Collectors.toList());
    }

    /**
     * Build URL for endpoint, omitting default ports.
     * Default ports: 80 for http, 443 for https
     */
    private String buildUrl(McpEndpointInfo endpoint) {
        String protocol = endpoint.getProtocol();
        int port = endpoint.getPort();
        boolean isDefaultHttpPort = Constants.PROTOCOL_TYPE_HTTP.equalsIgnoreCase(protocol) && port == DEFAULT_HTTP_PORT;
        boolean isDefaultHttpsPort = Constants.PROTOCOL_TYPE_HTTPS.equalsIgnoreCase(protocol) && port == DEFAULT_HTTPS_PORT;
        
        if (isDefaultHttpPort || isDefaultHttpsPort) {
            return String.format("%s://%s%s", protocol, endpoint.getAddress(), endpoint.getPath());
        }
        return String.format("%s://%s:%d%s", protocol, endpoint.getAddress(), port, endpoint.getPath());
    }

    private List<Remote> buildRemotes(McpServerDetailInfo mcpServerDetail) {
        List<McpEndpointInfo> endpoints = pickEndpoints(mcpServerDetail.getFrontendEndpoints(),
                mcpServerDetail.getBackendEndpoints());
        if (CollectionUtils.isEmpty(endpoints)) {
            return null;
        }
        if (mcpServerDetail.getFrontProtocol().equals(AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE)) {
            return toRemotes(endpoints, AiConstants.Mcp.OFFICIAL_TRANSPORT_STREAMABLE);
        } else if (mcpServerDetail.getFrontProtocol().equals(AiConstants.Mcp.MCP_PROTOCOL_SSE)) {
            return toRemotes(endpoints, AiConstants.Mcp.OFFICIAL_TRANSPORT_SSE);
        }
        return null;
    }
    
    /**
     * Build registry detail from detailInfo and enrich including endpoints -> remotes.
     */
    private ServerResponse buildServerResponse(McpServerDetailInfo mcpServerDetail) {
        if (mcpServerDetail == null) {
            return null;
        }
        ServerResponse result = new ServerResponse();
        result.setServer(buildMcpServer(mcpServerDetail));
        result.setMeta(buildMeta(mcpServerDetail));
        return result;
    }

    private McpRegistryServerDetail buildMcpServer(McpServerDetailInfo mcpServerDetail) {
        McpRegistryServerDetail server = new McpRegistryServerDetail();
        server.setName(mcpServerDetail.getName());
        server.setDescription(mcpServerDetail.getDescription());
        server.setRepository(mcpServerDetail.getRepository());
        server.setPackages(mcpServerDetail.getPackages());
        server.setIcons(mcpServerDetail.getIcons());
        server.setWebsiteUrl(mcpServerDetail.getWebsiteUrl());
        if (mcpServerDetail.getVersionDetail() != null) {
            server.setVersion(mcpServerDetail.getVersionDetail().getVersion());
        }
        server.setRemotes(buildRemotes(mcpServerDetail));
        return server;
    }

    private ServerResponse.Meta buildMeta(McpServerDetailInfo mcpServerDetail) {
        ServerResponse.Meta meta = new ServerResponse.Meta();
        OfficialMeta official = new OfficialMeta();
        ServerVersionDetail versionDetail = mcpServerDetail.getVersionDetail();
        List<ServerVersionDetail> allVersions = mcpServerDetail.getAllVersions();
        if (CollectionUtils.isNotEmpty(allVersions)) {
            ServerVersionDetail firstDetail = allVersions.get(0);
            official.setPublishedAt(firstDetail.getRelease_date());
            ServerVersionDetail latestDetail = allVersions.get(allVersions.size() - 1);
            official.setUpdatedAt(latestDetail.getRelease_date());
            official.setIsLatest(Objects.equals(versionDetail.getVersion(), latestDetail.getVersion()));
        }
        meta.setOfficial(official);
        return meta;
    }
}
