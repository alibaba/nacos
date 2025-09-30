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
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServer;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerDetail;
import com.alibaba.nacos.api.ai.model.mcp.registry.McpRegistryServerList;
import com.alibaba.nacos.api.ai.model.mcp.registry.Meta;
import com.alibaba.nacos.api.ai.model.mcp.registry.OfficialMeta;
import com.alibaba.nacos.api.ai.model.mcp.registry.Remote;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.response.Namespace;
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
import com.alibaba.nacos.api.ai.model.mcp.registry.KeyValueInput;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import static com.alibaba.nacos.ai.constant.Constants.MCP_LIST_SEARCH_BLUR;

/**
 * a service for mcp registry api implementation.
 *
 * @author xinluo
 */
@Service
public class NacosMcpRegistryService {

    private static final String UTC_Z = "Z";

    private static final String TIME_SEPARATOR = "T";

    private static final int MILLIS_LENGTH = 13;

    private static final int SECONDS_LENGTH = 10;

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

        Page<McpServerBasicInfo> servers = listMcpServerByNamespaceList(namespaceIdList, serverName, offset, limit);

        // Build detail list by fetching per-item detail via getServer for consistency
        List<McpRegistryServerDetail> finalServers = servers.getPageItems().stream().map((item) -> {
            try {
                GetServerForm form = new GetServerForm();
                return getServer(item.getId(), form);
            } catch (Exception ignore) {
                return null;
            }
        }).collect(Collectors.toList());
        McpRegistryServerList serverList = new McpRegistryServerList();
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
        return buildRegistryDetail(mcpServerDetail, id);
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
    
    private String toRfc3339(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            if (raw.endsWith(UTC_Z) || raw.contains(TIME_SEPARATOR)) {
                return raw;
            }
            long epoch;
            if (raw.length() == MILLIS_LENGTH && raw.chars().allMatch(Character::isDigit)) {
                epoch = Long.parseLong(raw);
            } else if (raw.length() == SECONDS_LENGTH && raw.chars().allMatch(Character::isDigit)) {
                epoch = Long.parseLong(raw) * 1000L;
            } else {
                return raw;
            }
            return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(epoch));
        } catch (Exception e) {
            return raw;
        }
    }
    
    /**
     * Apply basic meta/schema and official block to detail.
     */
    private void applyBasicMetaAndSchema(McpRegistryServerDetail detail, String id) {
        if (detail == null) {
            return;
        }
        // Align with enum-based status in registry model
        detail.setStatus("active");
        detail.setSchema("https://static.modelcontextprotocol.io/schemas/2025-07-09/server.schema.json");
        Meta meta = detail.getMeta();
        if (meta == null) {
            meta = new Meta();
        }
        OfficialMeta official = meta.getOfficial();
        if (official == null) {
            official = new OfficialMeta();
        }
        official.setServerId(id);
        // published/updated timestamps should be carried in official meta rather than top-level
        // Keep existing values if already set
        if (official.getPublishedAt() == null && detail.getMeta() != null && detail.getMeta().getOfficial() != null) {
            official.setPublishedAt(detail.getMeta().getOfficial().getPublishedAt());
        }
        if (official.getUpdatedAt() == null && detail.getMeta() != null && detail.getMeta().getOfficial() != null) {
            official.setUpdatedAt(detail.getMeta().getOfficial().getUpdatedAt());
        }
        // If still null, do not synthesize values here
        if (official.getIsLatest() == null && detail.getMeta() != null && detail.getMeta().getOfficial() != null) {
            official.setIsLatest(detail.getMeta().getOfficial().getIsLatest());
        }
        meta.setOfficial(official);
        detail.setMeta(meta);
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
     * Resolve transport string from frontProtocol like "mcp-http" -> "http".
     */
    private String resolveTransport(String frontProtocol) {
        if (AiConstants.Mcp.MCP_PROTOCOL_SSE.equals(frontProtocol)) {
            return AiConstants.Mcp.OFFICIAL_TRANSPORT_SSE;
        } else if (AiConstants.Mcp.MCP_PROTOCOL_STREAMABLE.equals(frontProtocol)) {
            return AiConstants.Mcp.OFFICIAL_TRANSPORT_STREAMABLE;
        }
        return null;
    }
    
    /**
     * Map endpoints to remotes with default headers.
     */
    private List<Remote> toRemotes(List<McpEndpointInfo> endpoints, String transport) {
        if (CollectionUtils.isEmpty(endpoints)) {
            return null;
        }
        return endpoints.stream().map((item) -> {
            Remote remote = new Remote();
            remote.setType(transport);
            remote.setUrl(String.format("%s://%s:%d%s", Constants.PROTOCOL_TYPE_HTTP, item.getAddress(),
                    item.getPort(), item.getPath()));
            KeyValueInput headerAuth = new KeyValueInput();
            headerAuth.setName("Authorization");
            KeyValueInput headerPath = new KeyValueInput();
            headerPath.setName("X-Server-Path");
            remote.setHeaders(List.of(headerAuth, headerPath));
            return remote;
        }).collect(Collectors.toList());
    }
    
    /**
     * Enrich with meta/schema and also build remotes from endpoints.
     */
    private void enrich(McpRegistryServerDetail detail, String id, String frontProtocol,
            List<McpEndpointInfo> frontend, List<McpEndpointInfo> backend) {
        if (detail == null) {
            return;
        }
        applyBasicMetaAndSchema(detail, id);
        List<McpEndpointInfo> endpoints = pickEndpoints(frontend, backend);
        if (CollectionUtils.isEmpty(endpoints)) {
            return;
        }
        String transport = resolveTransport(frontProtocol);
        List<Remote> remotes = toRemotes(endpoints, transport);
        if (CollectionUtils.isNotEmpty(remotes)) {
            detail.setRemotes(remotes);
        }
    }
    
    /**
     * Build registry detail from detailInfo and enrich including endpoints -> remotes.
     */
    private McpRegistryServerDetail buildRegistryDetail(McpServerDetailInfo mcpServerDetail, String id) {
        McpRegistryServerDetail result = new McpRegistryServerDetail();
        result.setName(mcpServerDetail.getName());
        result.setDescription(mcpServerDetail.getDescription());
        result.setRepository(mcpServerDetail.getRepository());
        result.setPackages(mcpServerDetail.getPackages());
        if (mcpServerDetail.getVersionDetail() != null) {
            result.setVersion(mcpServerDetail.getVersionDetail().getVersion());
            String iso = toRfc3339(mcpServerDetail.getVersionDetail().getRelease_date());
            Meta meta = result.getMeta();
            if (meta == null) {
                meta = new Meta();
            }
            OfficialMeta official = meta.getOfficial();
            if (official == null) {
                official = new OfficialMeta();
            }
            official.setPublishedAt(iso);
            official.setUpdatedAt(iso);
            // mark latest when release equals update in our simple synthesis
            official.setIsLatest(Boolean.TRUE);
            meta.setOfficial(official);
            result.setMeta(meta);
        }
        enrich(result, id, mcpServerDetail.getFrontProtocol(),
                mcpServerDetail.getFrontendEndpoints(), mcpServerDetail.getBackendEndpoints());
        return result;
    }
}
