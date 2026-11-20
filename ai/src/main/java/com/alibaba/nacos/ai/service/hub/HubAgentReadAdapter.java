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

import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.agent.AgentPersistenceService;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.AgentSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentOverview;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionInfo;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionSummary;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.api.ai.model.agent.AgentSearchRequest;
import com.alibaba.nacos.api.ai.utils.RadModelValidator;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Shared Agent definitions and guarded reuse of the ordinary RAD discovery projection. */
@Service
public class HubAgentReadAdapter {
    
    private static final HubResourceType TYPE = HubResourceType.AGENT;
    private final HubResourcePublicationService publications;
    private final HubResourceDirectoryService directory;
    private final HubResourceDiscoveryProjector projector;
    private final HubResourceContentService contents;
    private final AgentPersistenceService agents;
    private final AiResourceManager manager;
    
    public HubAgentReadAdapter(HubResourcePublicationService publications,
        HubResourceDirectoryService directory, HubResourceDiscoveryProjector projector,
        HubResourceContentService contents, AgentPersistenceService agents,
        AiResourceManager manager) {
        this.publications = publications;
        this.directory = directory;
        this.projector = projector;
        this.contents = contents;
        this.agents = agents;
        this.manager = manager;
    }
    
    /** Return only the fixed shared catalog and public presentation fields. */
    public AgentSummary getAgent(String id) throws NacosException {
        return publications.inspectPinned(TYPE, HubResourceNameUtils.parseEntryId(id), null,
            (hub, source, reference) -> metadata(
                new HubResourceDirectoryService.Entry(hub, source, reference)));
    }
    
    /** Keep overview and its bounded version page within one publication revision. */
    public AgentOverview overview(String id) throws NacosException {
        return publications.inspectPinned(TYPE, HubResourceNameUtils.parseEntryId(id), null,
            (hub, source, reference) -> {
                HubResourceDirectoryService.Entry entry =
                    new HubResourceDirectoryService.Entry(hub, source, reference);
                AgentOverview result = new AgentOverview();
                result.setAgent(metadata(entry));
                result.setVersionPage(singleVersion(entry, null, 1));
                return result;
            });
    }
    
    /** Existing management filters apply before independent Hub paging and totals. */
    public Page<AgentSummary> list(String name, String tag, String scope, String owner,
        String orderBy, int pageNo, int pageSize) throws NacosException {
        List<String> tags =
            StringUtils.isBlank(tag) ? Collections.emptyList() : Collections.singletonList(tag);
        Page<HubResourceDirectoryService.Entry> page = directory.list(TYPE, null, tags, owner,
            orderBy, pageNo, pageSize,
            entry -> (StringUtils.isBlank(scope) || "PUBLIC".equalsIgnoreCase(scope))
                && (StringUtils.isBlank(name) || entry.reference().getSourceName().contains(name)));
        List<AgentSummary> items = new ArrayList<>();
        for (HubResourceDirectoryService.Entry entry : page.getPageItems()) {
            AgentSummary item = readEntry(entry);
            AgentSummary summary = new AgentSummary();
            summary.setNamespaceId(item.getNamespaceId());
            summary.setAgentName(item.getAgentName());
            summary.setDisplayName(item.getDisplayName());
            summary.setDescription(item.getDescription());
            summary.setIconUrl(item.getIconUrl());
            summary.setProvider(item.getProvider());
            summary.setTags(item.getTags());
            summary.setStatus(item.getStatus());
            summary.setOwner(item.getOwner());
            summary.setScope(item.getScope());
            summary.setVersionInfo(item.getVersionInfo());
            
            summary.setCreateTime(item.getCreateTime());
            summary.setUpdateTime(item.getUpdateTime());
            summary.setHub(item.getHub());
            items.add(summary);
        }
        return AiResourceManager.buildPageResult(items, page, pageNo);
    }
    
    /** Metadata-only version list exposes no unpublished or historical source versions. */
    public Page<AgentVersionSummary> versions(String id, String status, int pageNo, int pageSize)
        throws NacosException {
        if (pageNo < 1 || pageSize < 1 || pageSize > 500) {
            throw new NacosApiException(400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Invalid shared Agent version pagination");
        }
        return publications.inspectPinned(TYPE, HubResourceNameUtils.parseEntryId(id), null,
            (hub, source, reference) -> singleVersion(
                new HubResourceDirectoryService.Entry(hub, source, reference), status, pageNo));
    }
    
    /** Exact definition content keeps protocol validation, descriptor size and digest checks. */
    public AgentVersionDetail version(String id, String requestedVersion) throws NacosException {
        long entryId = HubResourceNameUtils.parseEntryId(id);
        return publications.inspectPinned(TYPE, entryId, requestedVersion,
            (hub, source, reference) -> {
                AiResourceVersion row =
                    requireVersion(new HubResourceDirectoryService.Entry(hub, source, reference));
                AgentVersionContent content =
                    contents.readAgent(entryId, reference.getSourceVersion());
                AgentVersionDetail result = new AgentVersionDetail();
                result.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
                result.setAgentName(id);
                result.setVersion(reference.getSourceVersion());
                result.setStatus("online");
                result.setCallInterfaces(content.getCallInterfaces());
                result.setContentDigest(AgentVersionStorageDescriptorSerializer
                    .deserialize(row.getStorage()).getContentDigest());
                result.setAuthor(row.getAuthor());
                result.setChangeDescription(row.getDesc());
                result.setPublishPipelineInfo(row.getPublishPipelineInfo());
                result.setCreateTime(
                    row.getGmtCreate() == null ? null : row.getGmtCreate().getTime());
                result.setUpdateTime(
                    row.getGmtModified() == null ? null : row.getGmtModified().getTime());
                result.setHub(projector.project(TYPE,
                    new HubResourceDirectoryService.Entry(hub, source, reference)));
                return result;
            });
    }
    
    /** Search tests protocols on this fixed version, never on another online source version. */
    public Page<AgentSummary> search(AgentSearchRequest request) throws NacosException {
        RadModelValidator.validate(AiConstants.Hub.NAMESPACE_ID, request);
        int pageNo = request.getPageNo() == null ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null ? 20 : request.getPageSize();
        Page<HubResourceDirectoryService.Entry> page =
            directory.list(TYPE, null, request.getTagsAll(), null,
                "entry_id_ascii", pageNo, pageSize, entry -> {
                    if (request.getAgentNameContains() != null
                        && !entry.reference().getSourceName()
                            .contains(request.getAgentNameContains())) {
                        return false;
                    }
                    AgentSummary shared = readEntry(entry);
                    List<String> protocols =
                        shared.getVersionInfo().getOnlineVersions().get(0).getProtocols();
                    return request.getProtocolsAny() == null
                        || request.getProtocolsAny().stream().anyMatch(protocols::contains);
                });
        List<AgentSummary> items = new ArrayList<>();
        for (HubResourceDirectoryService.Entry entry : page.getPageItems()) {
            AgentSummary shared = readEntry(entry);
            AgentSummary item = new AgentSummary();
            item.setAgentName(shared.getAgentName());
            item.setDisplayName(shared.getDisplayName());
            item.setDescription(shared.getDescription());
            item.setIconUrl(shared.getIconUrl());
            item.setProvider(shared.getProvider());
            item.setTags(shared.getTags() == null || shared.getTags().isEmpty() ? null
                : shared.getTags());
            AgentVersionInfo catalog = new AgentVersionInfo();
            catalog.setLabels(
                Collections.singletonMap("latest", entry.reference().getSourceVersion()));
            AgentVersionSummary version = new AgentVersionSummary();
            version.setVersion(entry.reference().getSourceVersion());
            version.setProtocols(shared.getVersionInfo().getOnlineVersions().get(0).getProtocols());
            catalog.setOnlineVersions(Collections.singletonList(version));
            item.setVersionInfo(catalog);
            item.setHub(shared.getHub());
            items.add(item);
        }
        Page<AgentSummary> result = AiResourceManager.buildPageResult(items, page, pageNo);
        RadModelValidator.validateCatalogPage(result);
        return result;
    }
    
    /** Resolve ordinary Endpoint facts for the exact source version without changing Publisher ownership. */
    public AgentDiscoveryResult discover(AgentDiscoveryRequest request, DiscoveryReader reader)
        throws NacosException {
        RadModelValidator.validate(request);
        AgentReference selection = request.getReference();
        if (selection.getLabel() != null && !"latest".equals(selection.getLabel())) {
            throw unavailable();
        }
        long id = HubResourceNameUtils.parseEntryId(selection.getAgentName());
        return publications.inspectPinned(TYPE, id, selection.getVersion(),
            (hub, source, reference) -> {
                AgentDiscoveryRequest pinned = new AgentDiscoveryRequest();
                pinned.setNamespaceId(reference.getSourceNamespaceId());
                AgentReference selected = new AgentReference();
                selected.setAgentName(reference.getSourceName());
                selected.setVersion(reference.getSourceVersion());
                pinned.setReference(selected);
                pinned.setFilter(request.getFilter());
                AgentDiscoveryResult result = reader.read(pinned);
                if (!reference.getSourceVersion().equals(result.getVersion())) {
                    throw unavailable();
                }
                result.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
                result.setAgentName(selection.getAgentName());
                RadModelValidator.validate(result);
                return result;
            });
    }
    
    private AgentSummary readEntry(HubResourceDirectoryService.Entry expected)
        throws NacosException {
        return publications.inspectPinned(TYPE, expected.hub().getId(),
            expected.reference().getSourceVersion(), (hub, source, reference) -> {
                if (!Objects.equals(hub.getMetaVersion(), expected.hub().getMetaVersion())
                    || !Objects.equals(reference.getSourceVersionId(),
                        expected.reference().getSourceVersionId())
                    || !Objects.equals(reference.getSourceResourceId(),
                        expected.reference().getSourceResourceId())) {
                    throw new NacosApiException(409, ErrorCode.RESOURCE_CONFLICT,
                        "Shared Agent changed during directory projection");
                }
                return metadata(new HubResourceDirectoryService.Entry(hub, source, reference));
            });
    }
    
    private AgentSummary metadata(HubResourceDirectoryService.Entry entry) throws NacosException {
        AgentSummary source = agents.getAgent(entry.reference().getSourceNamespaceId(),
            entry.reference().getSourceName());
        AgentSummary result = new AgentSummary();
        result.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        result.setAgentName(entry.hub().getId().toString());
        result.setDisplayName(source.getDisplayName());
        result.setDescription(source.getDescription());
        result.setIconUrl(source.getIconUrl());
        result.setProvider(source.getProvider());
        result.setTags(source.getTags());
        result.setExtensions(source.getExtensions());
        result.setOwner(source.getOwner());
        result.setScope("PUBLIC");
        result.setStatus("enable");
        AgentVersionInfo info = new AgentVersionInfo();
        info.setLabels(HubResourceReadMetadata.labels(
            source.getVersionInfo() == null ? null : source.getVersionInfo().getLabels(),
            entry.reference().getSourceVersion()));
        result.setVersionInfo(info);
        AgentVersionSummary fixed = new AgentVersionSummary();
        fixed.setVersion(entry.reference().getSourceVersion());
        if (source.getVersionInfo() == null
            || source.getVersionInfo().getOnlineVersions() == null) {
            throw unavailable();
        }
        AgentVersionSummary sourceVersion =
            source.getVersionInfo().getOnlineVersions().stream()
                .filter(version -> fixed.getVersion().equals(version.getVersion())).findFirst()
                .orElseThrow(this::unavailable);
        fixed.setProtocols(sourceVersion.getProtocols());
        info.setOnlineVersions(Collections.singletonList(fixed));
        result.setCreateTime(
            source.getCreateTime());
        result.setUpdateTime(
            source.getUpdateTime());
        result.setHub(projector.project(TYPE, entry));
        return result;
    }
    
    private Page<AgentVersionSummary> singleVersion(HubResourceDirectoryService.Entry entry,
        String status, int pageNo)
        throws NacosException {
        AiResourceVersion row = requireVersion(entry);
        AgentVersionSummary summary = new AgentVersionSummary();
        summary.setVersion(entry.reference().getSourceVersion());
        summary.setStatus("online");
        summary.setAuthor(row.getAuthor());
        summary.setChangeDescription(row.getDesc());
        summary.setContentDigest(AgentVersionStorageDescriptorSerializer
            .deserialize(row.getStorage()).getContentDigest());
        summary.setCreateTime(
            row.getGmtCreate() == null ? null : row.getGmtCreate().getTime());
        summary.setUpdateTime(
            row.getGmtModified() == null ? null : row.getGmtModified().getTime());
        Page<AgentVersionSummary> page = new Page<>();
        boolean matches = StringUtils.isBlank(status) || "online".equals(status);
        page.setPageNumber(pageNo);
        page.setPagesAvailable(matches ? 1 : 0);
        page.setTotalCount(matches ? 1 : 0);
        page.setPageItems(
            matches && pageNo == 1 ? Collections.singletonList(summary) : Collections.emptyList());
        return page;
    }
    
    private AiResourceVersion requireVersion(HubResourceDirectoryService.Entry entry)
        throws NacosException {
        AiResourceVersion row = manager.findVersion(entry.reference().getSourceNamespaceId(),
            entry.reference().getSourceName(), TYPE.resourceType(),
            entry.reference().getSourceVersion());
        if (row == null || !Objects.equals(row.getId(), entry.reference().getSourceVersionId())
            || !"online".equals(row.getStatus())) {
            throw unavailable();
        }
        return row;
    }
    
    private NacosApiException unavailable() {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            "Shared Agent not found or unavailable");
    }
    
    /** Internal callback invoked only inside the fixed-publication admission and validity fences. */
    @FunctionalInterface
    public interface DiscoveryReader {
        
        /** Project Endpoint facts using the ordinary exact-version discovery implementation. */
        AgentDiscoveryResult read(AgentDiscoveryRequest pinned) throws NacosException;
    }
}
