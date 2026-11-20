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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.McpEndpointOperationService;
import com.alibaba.nacos.ai.service.mcp.McpCompatibilityModeResolver;
import com.alibaba.nacos.ai.service.mcp.McpLifecycleOperationService;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/** Fixed-version MCP reads using canonical storage and the ordinary read-only Endpoint projection. */
@Service
public class HubMcpReadAdapter {
    
    private static final HubResourceType TYPE = HubResourceType.MCP;
    
    private final HubResourcePublicationService publications;
    private final HubResourceDirectoryService directory;
    private final HubResourceDiscoveryProjector projector;
    private final McpLifecycleOperationService lifecycle;
    private final McpEndpointOperationService endpoints;
    private final McpCompatibilityModeResolver modeResolver;
    
    public HubMcpReadAdapter(HubResourcePublicationService publications,
        HubResourceDirectoryService directory, HubResourceDiscoveryProjector projector,
        McpLifecycleOperationService lifecycle, McpEndpointOperationService endpoints,
        McpCompatibilityModeResolver modeResolver) {
        this.publications = publications;
        this.directory = directory;
        this.projector = projector;
        this.lifecycle = lifecycle;
        this.endpoints = endpoints;
        this.modeResolver = modeResolver;
    }
    
    /** The historical ID alias may only select the same opaque Hub entry as the name. */
    public String resolveId(String id, String name) throws NacosException {
        String selected = StringUtils.isNotBlank(name) ? name : id;
        HubResourceNameUtils.parseEntryId(selected);
        if (StringUtils.isNotBlank(id) && !id.equals(selected)) {
            throw new NacosApiException(400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "MCP Hub name and id must select the same shared entry");
        }
        return selected;
    }
    
    /** Canonical management detail contains neither other versions nor source governance state. */
    public McpServerVersionDetail version(String id, String requestedVersion)
        throws NacosException {
        requireLocalCapability();
        return publications.inspectPinned(TYPE, HubResourceNameUtils.parseEntryId(id),
            requestedVersion,
            (hub, source, reference) -> {
                McpServerVersionDetail result = lifecycle.projectSharedVersion(source, reference);
                result.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
                result.setMcpName(id);
                result.setEditingVersion(null);
                result.setReviewingVersion(null);
                result.setLabels(
                    HubResourceReadMetadata.labels(source, reference.getSourceVersion()));
                result.setOnlineCount(1);
                result.setWritable(false);
                result.setScope("PUBLIC");
                result.setResourceStatus("enable");
                result.setHub(projector.project(TYPE,
                    new HubResourceDirectoryService.Entry(hub, source, reference)));
                McpServerBasicInfo server = result.getServerSpecification();
                server.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
                server.setName(id);
                server.setId(null);
                server.setEnabled(true);
                server.setHub(result.getHub());
                server.setVersion(reference.getSourceVersion());
                server.getVersionDetail().setIs_latest(true);
                return result;
            });
    }
    
    /** Keep Endpoint resolution inside the publication fences without creating Naming state. */
    public McpServerDetailInfo serving(String id, String requestedVersion) throws NacosException {
        requireLocalCapability();
        return publications.inspectPinned(TYPE, HubResourceNameUtils.parseEntryId(id),
            requestedVersion,
            (hub, source, reference) -> {
                McpServerVersionDetail content = lifecycle.projectSharedVersion(source, reference);
                McpServerDetailInfo result = new McpServerDetailInfo();
                BeanUtils.copyProperties(content.getServerSpecification(), result);
                result.setToolSpec(content.getToolSpecification());
                result.setResourceSpec(content.getResourceSpecification());
                if (!AiConstants.Mcp.MCP_PROTOCOL_STDIO.equals(result.getProtocol())) {
                    endpoints.injectEndpoint(result);
                }
                result.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
                result.setName(id);
                result.setId(id);
                result.setEnabled(true);
                result.setVersion(reference.getSourceVersion());
                result.getVersionDetail().setIs_latest(true);
                result.setAllVersions(Collections.singletonList(result.getVersionDetail()));
                result.setHub(projector.project(TYPE,
                    new HubResourceDirectoryService.Entry(hub, source, reference)));
                return result;
            });
    }
    
    /** Management name matching is applied before independent Hub paging. */
    public Page<McpServerBasicInfo> list(String name, String search, int pageNo, int pageSize)
        throws NacosException {
        Pattern pattern = StringUtils.isBlank(name) ? null
            : Pattern.compile(".*" + Pattern.quote(name).replace("*", "\\E.*\\Q")
                .replace("%", "\\E.*\\Q") + ".*", Pattern.DOTALL);
        return summaries(directory.list(TYPE, null, Collections.emptyList(), null, "gmt_modified",
            pageNo, pageSize, entry -> pattern == null || ("blur".equalsIgnoreCase(search)
                ? pattern.matcher(entry.reference().getSourceName()).matches()
                : name.equals(entry.reference().getSourceName()))),
            pageNo);
    }
    
    /** Protocol and capability filters inspect the selected version before totals and paging. */
    public Page<McpServerBasicInfo> search(String query, List<String> tagsAll,
        List<String> protocolsAny, List<String> capabilitiesAny, int pageNo, int pageSize)
        throws NacosException {
        return summaries(directory.list(TYPE, query, tagsAll, null, "download_count", pageNo,
            pageSize, entry -> {
                McpServerBasicInfo server = version(entry.hub().getId().toString(),
                    entry.reference().getSourceVersion()).getServerSpecification();
                return (protocolsAny == null || protocolsAny.isEmpty()
                    || protocolsAny.contains(server.getProtocol()))
                    && (capabilitiesAny == null || capabilitiesAny.isEmpty()
                        || (server.getCapabilities() != null && server.getCapabilities().stream()
                            .anyMatch(capability -> capabilitiesAny.contains(capability.name()))));
            }), pageNo);
    }
    
    /** A shared entry has exactly one online version, independently paginated from source history. */
    public Page<McpServerVersionSummary> versions(String id, String status, int pageNo,
        int pageSize)
        throws NacosException {
        if (pageNo < 1 || pageSize < 1 || pageSize > 500) {
            throw new NacosApiException(400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Invalid shared MCP version pagination");
        }
        McpServerVersionDetail detail = version(id, null);
        McpServerVersionSummary summary = new McpServerVersionSummary();
        BeanUtils.copyProperties(detail, summary);
        boolean matches = StringUtils.isBlank(status) || "online".equals(status);
        Page<McpServerVersionSummary> page = new Page<>();
        page.setPageNumber(pageNo);
        page.setTotalCount(matches ? 1 : 0);
        page.setPagesAvailable(matches ? 1 : 0);
        page.setPageItems(matches && pageNo == 1 ? Collections.singletonList(summary)
            : Collections.emptyList());
        return page;
    }
    
    private Page<McpServerBasicInfo> summaries(Page<HubResourceDirectoryService.Entry> page,
        int pageNo) throws NacosException {
        List<McpServerBasicInfo> items = new ArrayList<>();
        for (HubResourceDirectoryService.Entry entry : page.getPageItems()) {
            McpServerBasicInfo server = version(entry.hub().getId().toString(),
                entry.reference().getSourceVersion()).getServerSpecification();
            McpServerBasicInfo item = new McpServerBasicInfo();
            item.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
            item.setId(entry.hub().getId().toString());
            item.setName(item.getId());
            item.setDescription(server.getDescription());
            item.setProtocol(server.getProtocol());
            item.setFrontProtocol(server.getFrontProtocol());
            item.setCapabilities(server.getCapabilities());
            item.setVersionDetail(server.getVersionDetail());
            item.setVersion(server.getVersion());
            item.setIcons(server.getIcons());
            item.setRepository(server.getRepository());
            item.setWebsiteUrl(server.getWebsiteUrl());
            item.setStatus(server.getStatus());
            item.setHub(server.getHub());
            items.add(item);
        }
        return AiResourceManager.buildPageResult(items, page, pageNo);
    }
    
    private void requireLocalCapability() throws NacosApiException {
        if (!modeResolver.localMemberSupportsManagedLifecycle()) {
            throw new NacosApiException(500, ErrorCode.SERVER_ERROR,
                "This Nacos member cannot process shared MCP lifecycle content");
        }
    }
}
