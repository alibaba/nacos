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

package com.alibaba.nacos.ai.service.mcp;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.mcp.McpResourceExt;
import com.alibaba.nacos.ai.model.mcp.McpServerStorageInfo;
import com.alibaba.nacos.ai.model.mcp.McpVersionStorageDescriptor;
import com.alibaba.nacos.ai.service.McpEndpointOperationService;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.mcp.storage.McpResourceExtSerializer;
import com.alibaba.nacos.ai.service.mcp.storage.McpServingManifestStorage;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageContents;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageKeyComposer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageService;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.service.search.AiResourceIndexMaintenanceService;
import com.alibaba.nacos.ai.service.trace.AiResourceTraceService;
import com.alibaba.nacos.ai.utils.McpConfigUtils;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpCapability;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionSummary;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServiceRef;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Canonical MCP compatibility implementation backed by AI Resource lifecycle rows.
 *
 * <p>AI Resource and Version rows are the durable desired state. Version content continues to use
 * the historical Config coordinates through {@link McpVersionStorageService}; the serving
 * Manifest remains a compatibility projection and is always converged last for online changes.
 * Failed convergence deliberately leaves lifecycle rows and descriptors available for retry.</p>
 *
 * @author Nacos
 */
@Service
public class McpLifecycleOperationService implements McpOperationService {
    
    private static final String RESOURCE_TYPE = AiResourceConstants.RESOURCE_TYPE_MCP;
    
    private static final String RESOURCE_SOURCE_LOCAL = "local";
    
    private static final String DEFAULT_OWNER = "nacos";
    
    private static final int VERSION_PAGE_SIZE = 100;
    
    private static final DateTimeFormatter RELEASE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern(Constants.RELEASE_DATE_FORMAT);
    
    private final McpResourceLocator resourceLocator;
    
    private final AiResourceManager resourceManager;
    
    private final AiResourcePersistService resourcePersistService;
    
    private final AiResourceVersionPersistService versionPersistService;
    
    private final McpVersionStorageService versionStorageService;
    
    private final McpServingManifestStorage manifestStorage;
    
    private final McpEndpointOperationService endpointOperationService;
    
    private final McpCanonicalAuthorizationService canonicalAuthorizationService;
    
    private AiResourceIndexMaintenanceService indexMaintenanceService =
        AiResourceIndexMaintenanceService.NOOP;
    
    public McpLifecycleOperationService(McpResourceLocator resourceLocator,
        AiResourceManager resourceManager, AiResourcePersistService resourcePersistService,
        AiResourceVersionPersistService versionPersistService,
        McpVersionStorageService versionStorageService,
        McpServingManifestStorage manifestStorage,
        McpEndpointOperationService endpointOperationService,
        McpCanonicalAuthorizationService canonicalAuthorizationService) {
        this.resourceLocator = resourceLocator;
        this.resourceManager = resourceManager;
        this.resourcePersistService = resourcePersistService;
        this.versionPersistService = versionPersistService;
        this.versionStorageService = versionStorageService;
        this.manifestStorage = manifestStorage;
        this.endpointOperationService = endpointOperationService;
        this.canonicalAuthorizationService = canonicalAuthorizationService;
    }
    
    @Autowired(required = false)
    public void setIndexMaintenanceService(
        AiResourceIndexMaintenanceService indexMaintenanceService) {
        if (indexMaintenanceService != null) {
            this.indexMaintenanceService = indexMaintenanceService;
        }
    }
    
    /**
     * Page lifecycle metadata for the Versions of one canonical MCP resource.
     *
     * @param namespaceId namespace identifier
     * @param mcpName canonical MCP name
     * @param status optional lifecycle status filter
     * @param pageNo page number, starting at one
     * @param pageSize bounded page size
     * @return lifecycle Version summaries
     * @throws NacosException when the Resource is absent, unreadable, or inconsistent
     */
    public Page<McpLifecycleVersionSummary> listLifecycleVersions(String namespaceId,
        String mcpName, String status, int pageNo, int pageSize) throws NacosException {
        LifecycleResource lifecycle = requireReadableLifecycleResource(namespaceId, mcpName);
        Page<AiResourceVersion> source = resourceManager.listVersions(
            lifecycle.resource.getNamespaceId(), lifecycle.resource.getName(), RESOURCE_TYPE,
            status, pageNo, pageSize);
        List<McpLifecycleVersionSummary> items = new ArrayList<>();
        if (source != null && source.getPageItems() != null) {
            for (AiResourceVersion row : source.getPageItems()) {
                validateVersionRow(lifecycle.resource, row);
                items.add(toLifecycleSummary(lifecycle.resource, row));
            }
        }
        return AiResourceManager.buildPageResult(items, source, pageNo);
    }
    
    /**
     * Read one exact MCP Version without exposing its internal compatibility id.
     *
     * @param namespaceId namespace identifier
     * @param mcpName canonical MCP name
     * @param version exact Version
     * @return exact lifecycle content and metadata
     * @throws NacosException when the Resource, Version, or content is unavailable
     */
    public McpLifecycleVersionDetail getLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        LifecycleResource lifecycle = requireReadableLifecycleResource(namespaceId, mcpName);
        return toLifecycleDetail(lifecycle, requireVersion(lifecycle, version));
    }
    
    /**
     * Create a new MCP draft Version, initializing the Resource directory when necessary.
     *
     * @param namespaceId namespace identifier
     * @param serverSpecification complete Server content with canonical name and Version
     * @param toolSpecification optional Tools content
     * @param resourceSpecification optional Resources content
     * @param endpointSpecification endpoint facts for non-stdio Servers
     * @return persisted draft detail
     * @throws NacosException when the draft slot is occupied or persistence fails
     */
    public McpLifecycleVersionDetail createLifecycleDraft(String namespaceId,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        String normalizedNamespace = normalizeNamespace(namespaceId);
        VersionIdentity identity = validateSpecification(serverSpecification);
        AiResource resource = resourceManager.findMeta(normalizedNamespace, identity.name,
            RESOURCE_TYPE);
        final String mcpId;
        if (resource == null) {
            mcpId = resolveCreateMcpId(normalizedNamespace, null);
            serverSpecification.setId(mcpId);
            resource = buildInitialDraftResource(normalizedNamespace, serverSpecification);
            insertInitialResource(resource);
            resource = resourceManager.requireMeta(normalizedNamespace, identity.name,
                RESOURCE_TYPE);
        } else {
            VisibilityHelper.checkWritableResource(resource);
            LifecycleResource existing = requireLifecycleResource(resource);
            mcpId = existing.mcpId;
            serverSpecification.setId(mcpId);
        }
        ResourceVersionInfo versionInfo = AiResourceManager.requireVersionInfo(resource);
        AiResourceManager.ensureNoWorkingVersion(versionInfo, "create MCP draft");
        if (resourceManager.findVersion(normalizedNamespace, identity.name, RESOURCE_TYPE,
            identity.version) != null) {
            throw conflict("MCP Version already exists: " + identity.name + '@'
                + identity.version, null);
        }
        PreparedVersion prepared = prepareVersion(normalizedNamespace, mcpId,
            serverSpecification, toolSpecification, resourceSpecification, endpointSpecification,
            false);
        versionStorageService.save(prepared.descriptor, prepared.contents);
        insertDraftVersion(normalizedNamespace, identity, prepared);
        resourceManager.markEditingVersionCas(normalizedNamespace, resource, versionInfo,
            identity.version, "create MCP draft");
        scheduleIndex(normalizedNamespace, identity.name);
        traceSuccess(identity.name, identity.version, AiResourceTraceService.OP_CREATE_DRAFT);
        LifecycleResource refreshed = requireLifecycleResource(resourceManager.requireMeta(
            normalizedNamespace, identity.name, RESOURCE_TYPE));
        return toLifecycleDetail(refreshed, requireVersion(refreshed, identity.version));
    }
    
    /**
     * Replace the full content of one exact current MCP draft.
     *
     * @param namespaceId namespace identifier
     * @param serverSpecification replacement Server content
     * @param toolSpecification replacement optional Tools content
     * @param resourceSpecification replacement optional Resources content
     * @param endpointSpecification replacement endpoint facts for non-stdio Servers
     * @return updated draft detail
     * @throws NacosException when the Version is absent, immutable, or not the current draft
     */
    public McpLifecycleVersionDetail updateLifecycleDraft(String namespaceId,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        String normalizedNamespace = normalizeNamespace(namespaceId);
        VersionIdentity identity = validateSpecification(serverSpecification);
        LifecycleResource lifecycle = requireWritableLifecycleResource(normalizedNamespace,
            identity.name);
        AiResourceVersion row = resourceManager.requireDraftVersion(normalizedNamespace,
            identity.name, RESOURCE_TYPE, identity.version);
        requireWorkingPointer(lifecycle.resource, identity.version,
            AiResourceConstants.VERSION_STATUS_DRAFT);
        serverSpecification.setId(lifecycle.mcpId);
        PreparedVersion prepared = prepareVersion(normalizedNamespace, lifecycle.mcpId,
            serverSpecification, toolSpecification, resourceSpecification, endpointSpecification,
            true);
        McpVersionStorageDescriptor previous = descriptor(row);
        versionStorageService.save(prepared.descriptor, prepared.contents);
        resourceManager.updateVersionStorageAndDesc(normalizedNamespace, identity.name,
            RESOURCE_TYPE, identity.version,
            McpVersionStorageDescriptorSerializer.serialize(prepared.descriptor),
            prepared.server.getDescription());
        versionStorageService.deleteObsolete(previous, prepared.descriptor);
        scheduleIndex(normalizedNamespace, identity.name);
        traceSuccess(identity.name, identity.version, AiResourceTraceService.OP_UPDATE_DRAFT);
        LifecycleResource refreshed = requireLifecycleResource(resourceManager.requireMeta(
            normalizedNamespace, identity.name, RESOURCE_TYPE));
        return toLifecycleDetail(refreshed, requireVersion(refreshed, identity.version));
    }
    
    /**
     * Delete one exact current MCP draft through the common lifecycle deletion flow.
     *
     * @param namespaceId namespace identifier
     * @param mcpName canonical MCP name
     * @param version exact draft Version
     * @throws NacosException when the Version is not the current draft or cleanup fails
     */
    public void deleteLifecycleDraft(String namespaceId, String mcpName, String version)
        throws NacosException {
        LifecycleResource lifecycle = requireWritableLifecycleResource(namespaceId, mcpName);
        AiResourceVersion row = resourceManager.findVersion(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), RESOURCE_TYPE, version);
        if (row != null
            && !AiResourceConstants.VERSION_STATUS_DRAFT.equalsIgnoreCase(row.getStatus())) {
            throw invalidParameter("MCP Version is not a draft: " + mcpName + '@' + version,
                null);
        }
        resourceManager.doDeleteDraft(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), RESOURCE_TYPE, version, draftRow -> {
                endpointOperationService.deleteMcpServerEndpointService(
                    lifecycle.resource.getNamespaceId(), lifecycle.resource.getName() + "::"
                        + draftRow.getVersion());
                versionStorageService.delete(descriptor(draftRow));
            });
        scheduleIndex(lifecycle.resource.getNamespaceId(), lifecycle.resource.getName());
    }
    
    /**
     * Submit one MCP working Version. Until MCP Pipeline governance is enabled, submission uses
     * the common no-Pipeline behavior and publishes the Version directly.
     *
     * @param namespaceId namespace identifier
     * @param mcpName canonical MCP name
     * @param version exact working Version
     * @return online Version summary
     * @throws NacosException when the state transition or serving convergence fails
     */
    public McpLifecycleVersionSummary submitLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        LifecycleResource lifecycle = requireWritableLifecycleResource(namespaceId, mcpName);
        AiResourceVersion row = resourceManager.prepareSubmitVersion(
            lifecycle.resource.getNamespaceId(), lifecycle.resource.getName(), RESOURCE_TYPE,
            version);
        requireWorkingPointer(lifecycle.resource, version, row.getStatus());
        LoadedVersion loaded = ensureReleaseMetadata(lifecycle, row);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(lifecycle.resource);
        resourceManager.directPublishVersion(lifecycle.resource.getNamespaceId(),
            lifecycle.resource, info, version, true);
        AiResource refreshed = refreshResource(lifecycle.resource, version, loaded.server,
            loaded.server.isEnabled());
        convergeServing(refreshed);
        scheduleIndex(refreshed.getNamespaceId(), refreshed.getName());
        traceSuccess(refreshed.getName(), version, AiResourceTraceService.OP_SUBMIT_REVIEW);
        return requireLifecycleSummary(refreshed.getNamespaceId(), refreshed.getName(), version);
    }
    
    /**
     * Publish one exact reviewed MCP Version.
     */
    public McpLifecycleVersionSummary publishLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        LifecycleResource lifecycle = requireWritableLifecycleResource(namespaceId, mcpName);
        AiResourceVersion row = requireVersionStatuses(lifecycle, version, "publish",
            AiResourceConstants.VERSION_STATUS_REVIEWED,
            AiResourceConstants.VERSION_STATUS_ONLINE);
        LoadedVersion loaded = ensureReleaseMetadata(lifecycle, row);
        resourceManager.doPublish(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), RESOURCE_TYPE, version, true);
        AiResource refreshed = refreshResource(lifecycle.resource, version, loaded.server,
            loaded.server.isEnabled());
        convergeServing(refreshed);
        scheduleIndex(refreshed.getNamespaceId(), refreshed.getName());
        return requireLifecycleSummary(refreshed.getNamespaceId(), refreshed.getName(), version);
    }
    
    /**
     * Force-publish one exact MCP draft, reviewing, or reviewed Version.
     */
    public McpLifecycleVersionSummary forcePublishLifecycleVersion(String namespaceId,
        String mcpName, String version) throws NacosException {
        LifecycleResource lifecycle = requireWritableLifecycleResource(namespaceId, mcpName);
        AiResourceVersion row = requireVersionStatuses(lifecycle, version, "force-publish",
            AiResourceConstants.VERSION_STATUS_DRAFT,
            AiResourceConstants.VERSION_STATUS_REVIEWING,
            AiResourceConstants.VERSION_STATUS_REVIEWED);
        LoadedVersion loaded = ensureReleaseMetadata(lifecycle, row);
        resourceManager.doForcePublish(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), RESOURCE_TYPE, version, true);
        AiResource refreshed = refreshResource(lifecycle.resource, version, loaded.server,
            loaded.server.isEnabled());
        convergeServing(refreshed);
        scheduleIndex(refreshed.getNamespaceId(), refreshed.getName());
        return requireLifecycleSummary(refreshed.getNamespaceId(), refreshed.getName(), version);
    }
    
    /**
     * Move one exact reviewed MCP Version back to draft.
     */
    public McpLifecycleVersionSummary redraftLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        LifecycleResource lifecycle = requireWritableLifecycleResource(namespaceId, mcpName);
        resourceManager.doRedraft(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), RESOURCE_TYPE, version);
        scheduleIndex(lifecycle.resource.getNamespaceId(), lifecycle.resource.getName());
        return requireLifecycleSummary(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), version);
    }
    
    /**
     * Bring one exact offline MCP Version online and make it latest.
     */
    public McpLifecycleVersionSummary onlineLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        LifecycleResource lifecycle = requireWritableLifecycleResource(namespaceId, mcpName);
        requireVersionStatuses(lifecycle, version, "online",
            AiResourceConstants.VERSION_STATUS_OFFLINE,
            AiResourceConstants.VERSION_STATUS_ONLINE);
        onlineMcpServerVersion(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), version, true);
        return requireLifecycleSummary(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), version);
    }
    
    /**
     * Take one exact online MCP Version offline and repair latest when necessary.
     */
    public McpLifecycleVersionSummary offlineLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        LifecycleResource lifecycle = requireWritableLifecycleResource(namespaceId, mcpName);
        requireVersionStatuses(lifecycle, version, "offline",
            AiResourceConstants.VERSION_STATUS_ONLINE,
            AiResourceConstants.VERSION_STATUS_OFFLINE);
        offlineMcpServerVersion(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), version);
        return requireLifecycleSummary(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), version);
    }
    
    /**
     * Replace custom labels while preserving the server-managed latest label.
     */
    public Map<String, String> updateLifecycleLabels(String namespaceId, String mcpName,
        Map<String, String> labels) throws NacosException {
        LifecycleResource lifecycle = requireWritableLifecycleResource(namespaceId, mcpName);
        Map<String, String> result = resourceManager.validateAndUpdateLabels(
            lifecycle.resource.getNamespaceId(), lifecycle.resource.getName(), RESOURCE_TYPE,
            labels);
        scheduleIndex(lifecycle.resource.getNamespaceId(), lifecycle.resource.getName());
        return result;
    }
    
    @Override
    public Page<McpServerBasicInfo> listMcpServerWithPage(String namespaceId, String mcpName,
        String search, int pageNo, int pageSize) throws NacosException {
        String normalizedNamespace = normalizeNamespace(namespaceId);
        String nameLike = buildNameLike(mcpName, search);
        QueryCondition condition = resourceManager.buildQueryCondition(normalizedNamespace,
            RESOURCE_TYPE, nameLike, null, VisibilityConstants.ACTION_READ);
        if (condition.isAlwaysEmpty()) {
            return AiResourceManager.buildEmptyPage(pageNo);
        }
        Page<AiResource> source = resourceManager.listMeta(condition, pageNo, pageSize);
        List<McpServerBasicInfo> items = new ArrayList<>();
        if (source != null && source.getPageItems() != null) {
            for (AiResource resource : source.getPageItems()) {
                items.add(toBasicInfo(requireLifecycleResource(resource)));
            }
        }
        return AiResourceManager.buildPageResult(items, source, pageNo);
    }
    
    @Override
    public McpServerDetailInfo getMcpServerDetail(String namespaceId, String mcpServerId,
        String mcpServerName, String version) throws NacosException {
        AiResource resource = resourceLocator.locate(namespaceId, mcpServerName, mcpServerId);
        canonicalAuthorizationService.authorizeIdOnly(resource.getNamespaceId(),
            resource.getName(), mcpServerName, mcpServerId, ActionTypes.READ);
        resourceManager.ensureReadableOrNotFound(resource,
            "MCP server not found: " + resource.getName());
        LifecycleResource lifecycle = requireLifecycleResource(resource);
        AiResourceVersion selected = resolveVersion(lifecycle, version);
        LoadedVersion loaded = loadVersion(lifecycle, selected);
        McpServerDetailInfo result = new McpServerDetailInfo();
        BeanUtils.copyProperties(loaded.server, result);
        result.setId(lifecycle.mcpId);
        result.setNamespaceId(resource.getNamespaceId());
        result.setEnabled(isResourceEnabled(resource));
        List<ServerVersionDetail> versions = buildVersionDetails(lifecycle);
        result.setAllVersions(versions);
        result.setVersion(selected.getVersion());
        result.setVersionDetail(findVersionDetail(versions, selected.getVersion()));
        injectOptionalContent(loaded, result, resource.getName());
        if (!AiConstants.Mcp.MCP_PROTOCOL_STDIO.equalsIgnoreCase(loaded.server.getProtocol())) {
            endpointOperationService.injectEndpoint(result);
        }
        return result;
    }
    
    @Override
    public String createMcpServer(String namespaceId, McpServerBasicInfo serverSpecification,
        McpToolSpecification toolSpecification, McpResourceSpecification resourceSpecification,
        McpEndpointSpec endpointSpecification) throws NacosException {
        String normalizedNamespace = normalizeNamespace(namespaceId);
        VersionIdentity identity = validateCreate(serverSpecification);
        AiResource existing = resourceManager.findMeta(normalizedNamespace, identity.name,
            RESOURCE_TYPE);
        if (existing != null) {
            return recoverIncompleteCreate(normalizedNamespace, existing, serverSpecification,
                toolSpecification, resourceSpecification, endpointSpecification);
        }
        String mcpId = resolveCreateMcpId(normalizedNamespace, serverSpecification.getId());
        serverSpecification.setId(mcpId);
        ServerVersionDetail versionDetail = requireVersionDetail(serverSpecification);
        versionDetail.setRelease_date(now());
        AiResource resource = buildInitialResource(normalizedNamespace, serverSpecification);
        insertInitialResource(resource);
        PreparedVersion prepared = prepareVersion(normalizedNamespace, mcpId,
            serverSpecification, toolSpecification, resourceSpecification, endpointSpecification,
            false);
        versionStorageService.save(prepared.descriptor, prepared.contents);
        claimVersion(normalizedNamespace, identity.name, identity.version,
            AiResourceConstants.VERSION_STATUS_ONLINE, prepared);
        AiResource persisted = resourceManager.requireMeta(normalizedNamespace, identity.name,
            RESOURCE_TYPE);
        AiResource refreshed = refreshResource(persisted, identity.version, prepared.server,
            prepared.server.isEnabled());
        convergeServing(refreshed);
        clearCompatibilityCreateMarker(refreshed, identity.version);
        scheduleIndex(normalizedNamespace, identity.name);
        traceSuccess(identity.name, identity.version, AiResourceTraceService.OP_CREATE_DRAFT);
        return mcpId;
    }
    
    @Override
    public void updateMcpServer(String namespaceId, boolean isPublish,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification,
        boolean overrideExisting) throws NacosException {
        String normalizedNamespace = normalizeNamespace(namespaceId);
        if (serverSpecification == null) {
            throw invalidParameter("serverSpecification must not be null", null);
        }
        String requestedName = serverSpecification.getName();
        String requestedId = serverSpecification.getId();
        AiResource resource = resourceLocator.locate(normalizedNamespace, requestedName,
            requestedId);
        canonicalAuthorizationService.authorizeIdOnly(resource.getNamespaceId(),
            resource.getName(), requestedName, requestedId, ActionTypes.WRITE);
        serverSpecification.setName(resource.getName());
        VersionIdentity identity = validateUpdate(serverSpecification);
        VisibilityHelper.checkWritableResource(resource);
        LifecycleResource lifecycle = requireLifecycleResource(resource);
        serverSpecification.setId(lifecycle.mcpId);
        McpServerVersionInfo retainedServingPresentation = isPublish ? null
            : manifestStorage.get(normalizedNamespace, lifecycle.mcpId);
        AiResourceVersion existingVersion = resourceManager.findVersion(normalizedNamespace,
            identity.name, RESOURCE_TYPE, identity.version);
        if (!isPublish && existingVersion != null
            && AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(
                existingVersion.getStatus())
            && retainedServingPresentation == null) {
            throw conflict("Cannot retain the published MCP presentation because its serving "
                + "Manifest is missing: " + identity.name, null);
        }
        if (isPublish) {
            requireVersionDetail(serverSpecification).setRelease_date(now());
        } else if (existingVersion != null
            && StringUtils.isBlank(requireVersionDetail(serverSpecification).getRelease_date())) {
            LoadedVersion existing = loadVersion(lifecycle, existingVersion);
            if (existing.server.getVersionDetail() != null) {
                requireVersionDetail(serverSpecification).setRelease_date(
                    existing.server.getVersionDetail().getRelease_date());
            }
        }
        PreparedVersion prepared = prepareVersion(normalizedNamespace, lifecycle.mcpId,
            serverSpecification, toolSpecification, resourceSpecification, endpointSpecification,
            overrideExisting);
        if (existingVersion == null) {
            versionStorageService.save(prepared.descriptor, prepared.contents);
            claimVersion(normalizedNamespace, identity.name, identity.version,
                AiResourceConstants.VERSION_STATUS_ONLINE, prepared);
        } else {
            McpVersionStorageDescriptor previousDescriptor = descriptor(existingVersion);
            versionStorageService.save(prepared.descriptor, prepared.contents);
            versionStorageService.deleteObsolete(previousDescriptor, prepared.descriptor);
            String retainedStatus = isPublish ? AiResourceConstants.VERSION_STATUS_ONLINE
                : existingVersion.getStatus();
            claimVersion(normalizedNamespace, identity.name, identity.version, retainedStatus,
                prepared);
        }
        AiResource refreshed = refreshResource(resource,
            isPublish ? identity.version : null,
            isPublish ? prepared.server : null,
            isPublish ? prepared.server.isEnabled() : null);
        convergeServing(refreshed, retainedServingPresentation);
        scheduleIndex(normalizedNamespace, identity.name);
        traceSuccess(identity.name, identity.version,
            isPublish ? AiResourceTraceService.OP_PUBLISH
                : AiResourceTraceService.OP_UPDATE_DRAFT);
    }
    
    @Override
    public void deleteMcpServer(String namespaceId, String mcpName, String mcpServerId,
        String version) throws NacosException {
        String normalizedNamespace = normalizeNamespace(namespaceId);
        AiResource resource = resourceLocator.locate(normalizedNamespace, mcpName, mcpServerId);
        canonicalAuthorizationService.authorizeIdOnly(resource.getNamespaceId(),
            resource.getName(), mcpName, mcpServerId, ActionTypes.WRITE);
        VisibilityHelper.checkWritableResource(resource);
        LifecycleResource lifecycle = requireLifecycleResource(resource);
        if (StringUtils.isBlank(version)) {
            deleteResource(lifecycle);
        } else {
            deleteVersion(lifecycle, version);
            traceSuccess(resource.getName(), version, AiResourceTraceService.OP_DELETE_VERSION);
        }
        scheduleIndex(normalizedNamespace, resource.getName());
    }
    
    /**
     * Bring one exact MCP Version online and converge the serving Manifest last.
     *
     * @param namespaceId namespace identifier
     * @param mcpName canonical MCP name
     * @param version exact Version
     * @param setAsLatest whether the online Version becomes latest
     * @throws NacosException when lifecycle or serving convergence fails
     */
    public void onlineMcpServerVersion(String namespaceId, String mcpName, String version,
        boolean setAsLatest) throws NacosException {
        AiResource resource = resourceLocator.locate(namespaceId, mcpName, null);
        VisibilityHelper.checkWritableResource(resource);
        LifecycleResource lifecycle = requireLifecycleResource(resource);
        AiResourceVersion row = requireVersion(lifecycle, version);
        loadVersion(lifecycle, row);
        versionPersistService.updateStatus(resource.getNamespaceId(), resource.getName(),
            RESOURCE_TYPE, version, AiResourceConstants.VERSION_STATUS_ONLINE);
        AiResource refreshed = refreshResource(resource, setAsLatest ? version : null, null, null);
        convergeServing(refreshed);
        scheduleIndex(resource.getNamespaceId(), resource.getName());
        traceSuccess(resource.getName(), version, AiResourceTraceService.OP_ONLINE_VERSION);
    }
    
    /**
     * Take one exact MCP Version offline before removing it from the serving Manifest.
     *
     * @param namespaceId namespace identifier
     * @param mcpName canonical MCP name
     * @param version exact Version
     * @throws NacosException when lifecycle or serving convergence fails
     */
    public void offlineMcpServerVersion(String namespaceId, String mcpName, String version)
        throws NacosException {
        AiResource resource = resourceLocator.locate(namespaceId, mcpName, null);
        VisibilityHelper.checkWritableResource(resource);
        LifecycleResource lifecycle = requireLifecycleResource(resource);
        requireVersion(lifecycle, version);
        versionPersistService.updateStatus(resource.getNamespaceId(), resource.getName(),
            RESOURCE_TYPE, version, AiResourceConstants.VERSION_STATUS_OFFLINE);
        AiResource refreshed = refreshResource(resource, null, null, null);
        convergeServing(refreshed);
        scheduleIndex(resource.getNamespaceId(), resource.getName());
        traceSuccess(resource.getName(), version, AiResourceTraceService.OP_OFFLINE_VERSION);
    }
    
    private String recoverIncompleteCreate(String namespaceId, AiResource existing,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification)
        throws NacosException {
        VisibilityHelper.checkWritableResource(existing);
        String version = requireVersionDetail(serverSpecification).getVersion();
        requireCompatibilityCreateMarker(existing, version);
        LifecycleResource lifecycle = requireLifecycleResource(existing);
        String requestedId = serverSpecification.getId();
        if (StringUtils.isNotBlank(requestedId) && !lifecycle.mcpId.equals(requestedId)) {
            throw duplicateServer(existing.getName(), null);
        }
        AiResourceVersion row = resourceManager.findVersion(namespaceId, existing.getName(),
            RESOURCE_TYPE, version);
        serverSpecification.setId(lifecycle.mcpId);
        McpVersionStorageContents current = null;
        if (row != null) {
            current = versionStorageService.loadIfPresent(descriptor(row));
            if (current != null
                && StringUtils
                    .isBlank(requireVersionDetail(serverSpecification).getRelease_date())) {
                McpServerStorageInfo retainedServer = decode(current.getServerContent(),
                    McpServerStorageInfo.class, "Server", existing.getName(), version);
                if (retainedServer.getVersionDetail() != null) {
                    requireVersionDetail(serverSpecification).setRelease_date(
                        retainedServer.getVersionDetail().getRelease_date());
                }
            }
        }
        if (StringUtils.isBlank(requireVersionDetail(serverSpecification).getRelease_date())) {
            requireVersionDetail(serverSpecification).setRelease_date(now());
        }
        PreparedVersion prepared = prepareVersion(namespaceId, lifecycle.mcpId,
            serverSpecification, toolSpecification, resourceSpecification, endpointSpecification,
            false);
        if (row != null) {
            requireEquivalentDescriptor(row, prepared.descriptor);
        }
        if (current == null) {
            versionStorageService.save(prepared.descriptor, prepared.contents);
            claimVersion(namespaceId, existing.getName(), version,
                AiResourceConstants.VERSION_STATUS_ONLINE, prepared);
        } else if (!sameContents(current, prepared.contents)) {
            throw conflict("MCP server retry content differs from the retained lifecycle intent: "
                + existing.getName() + '@' + version, null);
        } else {
            claimVersion(namespaceId, existing.getName(), version,
                AiResourceConstants.VERSION_STATUS_ONLINE, prepared);
        }
        AiResource refreshed = refreshResource(existing, version, prepared.server,
            prepared.server.isEnabled());
        convergeServing(refreshed);
        clearCompatibilityCreateMarker(refreshed, version);
        scheduleIndex(namespaceId, existing.getName());
        return lifecycle.mcpId;
    }
    
    private void deleteVersion(LifecycleResource lifecycle, String version)
        throws NacosException {
        AiResourceVersion row = resourceManager.findVersion(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), RESOURCE_TYPE, version);
        if (row == null) {
            if (lifecycle.versions.isEmpty()) {
                resourceManager.deleteMeta(lifecycle.resource.getNamespaceId(),
                    lifecycle.resource.getName(), RESOURCE_TYPE);
            }
            return;
        }
        McpVersionStorageDescriptor descriptor = descriptor(row);
        versionPersistService.updateStatus(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), RESOURCE_TYPE, version,
            AiResourceConstants.VERSION_STATUS_OFFLINE);
        AiResource refreshed = refreshResource(lifecycle.resource, null, null, null);
        convergeServing(refreshed);
        endpointOperationService.deleteMcpServerEndpointService(
            lifecycle.resource.getNamespaceId(), lifecycle.resource.getName() + "::" + version);
        versionStorageService.delete(descriptor);
        resourceManager.deleteVersion(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), RESOURCE_TYPE, version);
        List<AiResourceVersion> remaining = listVersionRows(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), null);
        if (remaining.isEmpty()) {
            resourceManager.deleteMeta(lifecycle.resource.getNamespaceId(),
                lifecycle.resource.getName(), RESOURCE_TYPE);
        } else {
            AiResource latest = resourceManager.requireMeta(lifecycle.resource.getNamespaceId(),
                lifecycle.resource.getName(), RESOURCE_TYPE);
            refreshResource(latest, null, null, null);
        }
    }
    
    private void deleteResource(LifecycleResource lifecycle) throws NacosException {
        List<AiResourceVersion> versions = listVersionRows(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), null);
        for (AiResourceVersion version : versions) {
            descriptor(version);
        }
        for (AiResourceVersion version : versions) {
            if (AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(version.getStatus())) {
                versionPersistService.updateStatus(lifecycle.resource.getNamespaceId(),
                    lifecycle.resource.getName(), RESOURCE_TYPE, version.getVersion(),
                    AiResourceConstants.VERSION_STATUS_OFFLINE);
            }
        }
        AiResource disabled = refreshResource(lifecycle.resource, null, null, Boolean.FALSE);
        convergeServing(disabled);
        resourceManager.deleteResourceWithVersions(lifecycle.resource.getNamespaceId(),
            lifecycle.resource.getName(), RESOURCE_TYPE,
            version -> {
                endpointOperationService.deleteMcpServerEndpointService(
                    lifecycle.resource.getNamespaceId(),
                    lifecycle.resource.getName() + "::" + version.getVersion());
                versionStorageService.delete(descriptor(version));
            });
    }
    
    private PreparedVersion prepareVersion(String namespaceId, String mcpId,
        McpServerBasicInfo serverSpecification, McpToolSpecification toolSpecification,
        McpResourceSpecification resourceSpecification, McpEndpointSpec endpointSpecification,
        boolean overrideExisting) throws NacosException {
        McpServerStorageInfo server = new McpServerStorageInfo();
        BeanUtils.copyProperties(serverSpecification, server);
        server.setId(mcpId);
        ServerVersionDetail versionDetail = copyVersionDetail(
            requireVersionDetail(serverSpecification));
        server.setVersionDetail(versionDetail);
        server.setVersion(versionDetail.getVersion());
        List<McpCapability> capabilities = new LinkedList<>();
        boolean hasTools = hasToolSpecification(toolSpecification);
        boolean hasResources = hasResourceSpecification(resourceSpecification);
        if (hasTools) {
            capabilities.add(McpCapability.TOOL);
            server.setToolsDescriptionRef(
                McpConfigUtils.formatServerToolSpecDataId(mcpId, versionDetail.getVersion()));
        }
        if (hasResources) {
            capabilities.add(McpCapability.RESOURCE);
            server.setResourceDescriptionRef(
                McpConfigUtils.formatServerResourceSpecDataId(mcpId,
                    versionDetail.getVersion()));
        }
        server.setCapabilities(capabilities);
        if (endpointSpecification != null) {
            com.alibaba.nacos.naming.core.v2.pojo.Service service =
                endpointOperationService.createMcpServerEndpointServiceIfNecessary(namespaceId,
                    server.getName(), versionDetail.getVersion(), endpointSpecification,
                    overrideExisting);
            McpServiceRef serviceRef = new McpServiceRef();
            serviceRef.setNamespaceId(service.getNamespace());
            serviceRef.setGroupName(service.getGroup());
            serviceRef.setServiceName(service.getName());
            serviceRef.setTransportProtocol(endpointSpecification.getData()
                .get(Constants.MCP_BACKEND_INSTANCE_PROTOCOL_KEY));
            McpServerRemoteServiceConfig remote = server.getRemoteServerConfig();
            if (remote == null) {
                remote = new McpServerRemoteServiceConfig();
                server.setRemoteServerConfig(remote);
            }
            remote.setServiceRef(serviceRef);
        }
        McpVersionStorageDescriptor descriptor = McpVersionStorageKeyComposer.compose(namespaceId,
            mcpId, versionDetail.getVersion(), hasTools, hasResources);
        byte[] serverContent = jsonBytes(server);
        byte[] toolContent = hasTools ? jsonBytes(toolSpecification) : null;
        byte[] resourceContent = hasResources ? jsonBytes(resourceSpecification) : null;
        return new PreparedVersion(server, descriptor,
            new McpVersionStorageContents(serverContent, toolContent, resourceContent));
    }
    
    private void claimVersion(String namespaceId, String name, String version, String status,
        PreparedVersion prepared) {
        resourceManager.insertVersionRow(namespaceId, name, RESOURCE_TYPE, currentOwner(),
            status, version, prepared.server.getDescription(),
            McpVersionStorageDescriptorSerializer.serialize(prepared.descriptor));
    }
    
    private void insertDraftVersion(String namespaceId, VersionIdentity identity,
        PreparedVersion prepared) throws NacosException {
        AiResourceVersion row = new AiResourceVersion();
        row.setNamespaceId(namespaceId);
        row.setName(identity.name);
        row.setType(RESOURCE_TYPE);
        row.setAuthor(currentOwner());
        row.setStatus(AiResourceConstants.VERSION_STATUS_DRAFT);
        row.setVersion(identity.version);
        row.setDesc(prepared.server.getDescription());
        row.setStorage(McpVersionStorageDescriptorSerializer.serialize(prepared.descriptor));
        try {
            long id = versionPersistService.insert(row);
            if (id <= 0) {
                throw new IllegalStateException("MCP draft Version insert returned an invalid id");
            }
        } catch (DuplicateKeyException e) {
            throw conflict("MCP Version already exists: " + identity.name + '@'
                + identity.version, e);
        } catch (RuntimeException e) {
            throw serverError("Failed to insert MCP draft Version: " + identity.name + '@'
                + identity.version, e);
        }
    }
    
    private AiResource buildInitialResource(String namespaceId, McpServerBasicInfo server) {
        ResourceVersionInfo versionInfo = new ResourceVersionInfo();
        versionInfo.setEditingVersion(server.getVersionDetail().getVersion());
        versionInfo.setOnlineCnt(0);
        versionInfo.setLabels(new LinkedHashMap<>());
        McpResourceExt resourceExt = new McpResourceExt();
        resourceExt.setSchemaVersion(McpResourceExt.SCHEMA_VERSION);
        resourceExt.setMcpId(server.getId());
        AiResource result = new AiResource();
        result.setNamespaceId(namespaceId);
        result.setName(server.getName());
        result.setType(RESOURCE_TYPE);
        result.setStatus(server.isEnabled() ? AiResourceConstants.META_STATUS_ENABLE
            : AiResourceConstants.META_STATUS_DISABLE);
        result.setDesc(server.getDescription());
        result.setOwner(currentOwner());
        result.setScope(VisibilityHelper.resolveDefaultScopeForCreate(RESOURCE_TYPE));
        result.setFrom(RESOURCE_SOURCE_LOCAL);
        result.setExt(McpResourceExtSerializer.serialize(resourceExt));
        result.setVersionInfo(JacksonUtils.toJson(versionInfo));
        result.setMetaVersion(1L);
        return result;
    }
    
    private AiResource buildInitialDraftResource(String namespaceId,
        McpServerBasicInfo server) {
        AiResource result = buildInitialResource(namespaceId, server);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(result);
        info.setEditingVersion(null);
        result.setVersionInfo(JacksonUtils.toJson(info));
        return result;
    }
    
    private void insertInitialResource(AiResource resource) throws NacosException {
        try {
            long id = resourcePersistService.insert(resource);
            if (id <= 0) {
                throw new IllegalStateException("MCP Resource insert returned an invalid id");
            }
        } catch (RuntimeException e) {
            AiResource recovered = resourceManager.findMeta(resource.getNamespaceId(),
                resource.getName(), RESOURCE_TYPE);
            if (sameResourceIdentity(recovered, resource)) {
                return;
            }
            if (recovered != null || e instanceof DuplicateKeyException) {
                throw duplicateServer(resource.getName(), e);
            }
            throw serverError("Failed to insert MCP Resource: " + resource.getName(), e);
        }
    }
    
    private boolean sameResourceIdentity(AiResource actual, AiResource expected) {
        if (actual == null || !Objects.equals(actual.getNamespaceId(), expected.getNamespaceId())
            || !Objects.equals(actual.getName(), expected.getName())
            || !RESOURCE_TYPE.equals(actual.getType())) {
            return false;
        }
        try {
            return Objects.equals(
                McpResourceExtSerializer.deserialize(actual.getExt()).getMcpId(),
                McpResourceExtSerializer.deserialize(expected.getExt()).getMcpId());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    private void requireCompatibilityCreateMarker(AiResource resource, String version)
        throws NacosException {
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(resource);
        if (!version.equals(info.getEditingVersion())) {
            throw duplicateServer(resource.getName(), null);
        }
    }
    
    private void clearCompatibilityCreateMarker(AiResource resource, String version)
        throws NacosException {
        for (int i = 0; i < AiResourceConstants.MAX_WORKING_VERSION_RETRY; i++) {
            AiResource latest = resourceManager.requireMeta(resource.getNamespaceId(),
                resource.getName(), RESOURCE_TYPE);
            if (latest.getMetaVersion() == null) {
                throw serverError("MCP Resource meta version is missing: " + latest.getName(),
                    null);
            }
            ResourceVersionInfo info = AiResourceManager.requireVersionInfo(latest);
            if (StringUtils.isBlank(info.getEditingVersion())) {
                return;
            }
            if (!version.equals(info.getEditingVersion())) {
                throw conflict("MCP Resource has another working Version: "
                    + info.getEditingVersion(), null);
            }
            info.setEditingVersion(null);
            AiResource update = copyMutableMeta(latest, info);
            if (resourcePersistService.updateMetaCas(latest.getNamespaceId(), latest.getName(),
                RESOURCE_TYPE, latest.getMetaVersion(), update)) {
                return;
            }
        }
        throw conflict("MCP Resource changed concurrently: " + resource.getName(), null);
    }
    
    private AiResource refreshResource(AiResource resource, String preferredLatest,
        McpServerStorageInfo publishedSpecification, Boolean enableOverride)
        throws NacosException {
        for (int i = 0; i < AiResourceConstants.MAX_WORKING_VERSION_RETRY; i++) {
            AiResource latest = resourceManager.requireMeta(resource.getNamespaceId(),
                resource.getName(), RESOURCE_TYPE);
            if (latest.getMetaVersion() == null) {
                throw serverError("MCP Resource meta version is missing: " + latest.getName(),
                    null);
            }
            List<AiResourceVersion> onlineRows = listVersionRows(latest.getNamespaceId(),
                latest.getName(), AiResourceConstants.VERSION_STATUS_ONLINE);
            Set<String> onlineVersions = new HashSet<>();
            for (AiResourceVersion row : onlineRows) {
                onlineVersions.add(row.getVersion());
            }
            ResourceVersionInfo info = AiResourceManager.requireVersionInfo(latest);
            Map<String, String> labels = info.getLabels() == null ? new LinkedHashMap<>()
                : new LinkedHashMap<>(info.getLabels());
            String currentLatest = labels.get(AiResourceConstants.LABEL_LATEST);
            String nextLatest = chooseLatest(onlineVersions, preferredLatest, currentLatest);
            if (StringUtils.isBlank(nextLatest)) {
                labels.remove(AiResourceConstants.LABEL_LATEST);
            } else {
                labels.put(AiResourceConstants.LABEL_LATEST, nextLatest);
            }
            info.setLabels(labels);
            info.setOnlineCnt(onlineVersions.size());
            AiResource update = copyMutableMeta(latest, info);
            update.setStatus(enableOverride == null ? latest.getStatus()
                : enableOverride ? AiResourceConstants.META_STATUS_ENABLE
                    : AiResourceConstants.META_STATUS_DISABLE);
            update.setDesc(publishedSpecification == null ? latest.getDesc()
                : publishedSpecification.getDescription());
            if (resourcePersistService.updateMetaCas(latest.getNamespaceId(), latest.getName(),
                RESOURCE_TYPE, latest.getMetaVersion(), update)) {
                return resourceManager.requireMeta(latest.getNamespaceId(), latest.getName(),
                    RESOURCE_TYPE);
            }
        }
        throw conflict("MCP Resource changed concurrently: " + resource.getName(), null);
    }
    
    private AiResource copyMutableMeta(AiResource resource, ResourceVersionInfo info) {
        AiResource result = new AiResource();
        result.setStatus(resource.getStatus());
        result.setDesc(resource.getDesc());
        result.setBizTags(resource.getBizTags());
        result.setExt(resource.getExt());
        result.setVersionInfo(JacksonUtils.toJson(info));
        return result;
    }
    
    private void convergeServing(AiResource resource) throws NacosException {
        convergeServing(resource, null);
    }
    
    private void convergeServing(AiResource resource,
        McpServerVersionInfo retainedServingPresentation) throws NacosException {
        LifecycleResource lifecycle = requireLifecycleResource(resource);
        List<AiResourceVersion> onlineRows = listVersionRows(resource.getNamespaceId(),
            resource.getName(), AiResourceConstants.VERSION_STATUS_ONLINE);
        if (onlineRows.isEmpty()) {
            manifestStorage.delete(resource.getNamespaceId(), lifecycle.mcpId);
            if (manifestStorage.get(resource.getNamespaceId(), lifecycle.mcpId) != null) {
                throw serverError("MCP serving Manifest still exists after deletion: "
                    + resource.getName(), null);
            }
            return;
        }
        String latest = lifecycle.latestVersion;
        if (!containsVersion(onlineRows, latest)) {
            AiResource refreshed = refreshResource(resource, null, null, null);
            lifecycle = requireLifecycleResource(refreshed);
            latest = lifecycle.latestVersion;
        }
        AiResourceVersion latestRow = findVersion(onlineRows, latest);
        if (latestRow == null) {
            throw integrityFailure("MCP Resource has no latest online Version: "
                + resource.getName(), null);
        }
        LoadedVersion latestVersion = loadVersion(lifecycle, latestRow);
        McpServerVersionInfo manifest = new McpServerVersionInfo();
        if (retainedServingPresentation == null) {
            BeanUtils.copyProperties(latestVersion.server, manifest);
        } else {
            if (!lifecycle.mcpId.equals(retainedServingPresentation.getId())
                || !resource.getName().equals(retainedServingPresentation.getName())) {
                throw integrityFailure(
                    "MCP serving Manifest identity does not match lifecycle Resource: "
                        + resource.getName(),
                    null);
            }
            BeanUtils.copyProperties(retainedServingPresentation, manifest);
        }
        manifest.setId(lifecycle.mcpId);
        manifest.setName(resource.getName());
        manifest.setNamespaceId(resource.getNamespaceId());
        manifest.setDescription(resource.getDesc());
        manifest.setEnabled(isResourceEnabled(resource));
        manifest.setLatestPublishedVersion(latest);
        List<ServerVersionDetail> details = new ArrayList<>(onlineRows.size());
        for (AiResourceVersion row : onlineRows) {
            LoadedVersion loaded = loadVersion(lifecycle, row);
            ServerVersionDetail detail = copyVersionDetail(loaded.server.getVersionDetail());
            detail.setVersion(row.getVersion());
            detail.setIs_latest(row.getVersion().equals(latest));
            details.add(detail);
        }
        manifest.setVersions(details);
        manifest.setVersion(latest);
        manifest.setVersionDetail(findVersionDetail(details, latest));
        manifestStorage.publish(resource.getNamespaceId(), manifest);
        verifyManifest(manifest,
            manifestStorage.get(resource.getNamespaceId(), lifecycle.mcpId));
    }
    
    private void verifyManifest(McpServerVersionInfo expected, McpServerVersionInfo actual)
        throws NacosException {
        boolean valid = actual != null
            && Objects.equals(JacksonUtils.toJson(expected), JacksonUtils.toJson(actual));
        if (!valid) {
            throw serverError("MCP serving Manifest does not match lifecycle desired state: "
                + expected.getName(), null);
        }
    }
    
    private McpServerBasicInfo toBasicInfo(LifecycleResource lifecycle) throws NacosException {
        AiResourceVersion selected = resolveVersion(lifecycle, null);
        LoadedVersion loaded = loadVersion(lifecycle, selected);
        McpServerVersionInfo result = new McpServerVersionInfo();
        BeanUtils.copyProperties(loaded.server, result);
        result.setId(lifecycle.mcpId);
        result.setNamespaceId(lifecycle.resource.getNamespaceId());
        result.setEnabled(isResourceEnabled(lifecycle.resource)
            && StringUtils.isNotBlank(lifecycle.latestVersion));
        result.setDescription(lifecycle.resource.getDesc());
        result.setLatestPublishedVersion(lifecycle.latestVersion);
        List<ServerVersionDetail> versions = buildVersionDetails(lifecycle);
        result.setVersions(versions);
        result.setVersion(selected.getVersion());
        result.setVersionDetail(findVersionDetail(versions, selected.getVersion()));
        return result;
    }
    
    private McpLifecycleVersionSummary toLifecycleSummary(AiResource resource,
        AiResourceVersion row) {
        McpLifecycleVersionSummary result = new McpLifecycleVersionSummary();
        result.setVersion(row.getVersion());
        result.setStatus(row.getStatus());
        result.setAuthor(row.getAuthor());
        result.setDescription(row.getDesc());
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(resource);
        String latest = info.getLabels().get(AiResourceConstants.LABEL_LATEST);
        result.setLatest(row.getVersion().equals(latest));
        result.setCreateTime(row.getGmtCreate() == null ? null : row.getGmtCreate().getTime());
        result.setUpdateTime(row.getGmtModified() == null ? null : row.getGmtModified().getTime());
        return result;
    }
    
    private McpLifecycleVersionDetail toLifecycleDetail(LifecycleResource lifecycle,
        AiResourceVersion row) throws NacosException {
        LoadedVersion loaded = loadVersion(lifecycle, row);
        McpLifecycleVersionDetail result = new McpLifecycleVersionDetail();
        McpLifecycleVersionSummary summary = toLifecycleSummary(lifecycle.resource, row);
        result.setVersion(summary.getVersion());
        result.setStatus(summary.getStatus());
        result.setAuthor(summary.getAuthor());
        result.setDescription(summary.getDescription());
        result.setLatest(summary.getLatest());
        result.setCreateTime(summary.getCreateTime());
        result.setUpdateTime(summary.getUpdateTime());
        result.setNamespaceId(lifecycle.resource.getNamespaceId());
        result.setMcpName(lifecycle.resource.getName());
        McpServerBasicInfo server = new McpServerBasicInfo();
        BeanUtils.copyProperties(loaded.server, server);
        server.setId(null);
        result.setServerSpecification(server);
        McpServerDetailInfo optionalContent = new McpServerDetailInfo();
        injectOptionalContent(loaded, optionalContent, lifecycle.resource.getName());
        result.setToolSpecification(optionalContent.getToolSpec());
        result.setResourceSpecification(optionalContent.getResourceSpec());
        return result;
    }
    
    private McpLifecycleVersionSummary requireLifecycleSummary(String namespaceId, String name,
        String version) throws NacosException {
        LifecycleResource lifecycle = requireLifecycleResource(
            resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE));
        return toLifecycleSummary(lifecycle.resource, requireVersion(lifecycle, version));
    }
    
    private LifecycleResource requireReadableLifecycleResource(String namespaceId, String name)
        throws NacosException {
        String normalizedNamespace = normalizeNamespace(namespaceId);
        AiResource resource = resourceLocator.locate(normalizedNamespace, name, null);
        resourceManager.ensureReadableOrNotFound(resource, "MCP server not found: " + name);
        return requireLifecycleResource(resource);
    }
    
    private LifecycleResource requireWritableLifecycleResource(String namespaceId, String name)
        throws NacosException {
        String normalizedNamespace = normalizeNamespace(namespaceId);
        AiResource resource = resourceLocator.locate(normalizedNamespace, name, null);
        VisibilityHelper.checkWritableResource(resource);
        return requireLifecycleResource(resource);
    }
    
    private AiResourceVersion requireVersionStatuses(LifecycleResource lifecycle, String version,
        String action, String... expectedStatuses) throws NacosException {
        AiResourceVersion row = requireVersion(lifecycle, version);
        for (String expectedStatus : expectedStatuses) {
            if (expectedStatus.equalsIgnoreCase(row.getStatus())) {
                return row;
            }
        }
        throw invalidParameter("MCP " + action + " does not accept Version status "
            + row.getStatus() + ": " + lifecycle.resource.getName() + '@' + version, null);
    }
    
    private void requireWorkingPointer(AiResource resource, String version, String status)
        throws NacosException {
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(resource);
        String pointer = AiResourceConstants.VERSION_STATUS_DRAFT.equalsIgnoreCase(status)
            ? info.getEditingVersion() : info.getReviewingVersion();
        if (!version.equals(pointer)) {
            throw conflict("MCP working Version changed: " + resource.getName() + '@' + version,
                null);
        }
    }
    
    private LoadedVersion ensureReleaseMetadata(LifecycleResource lifecycle,
        AiResourceVersion row) throws NacosException {
        LoadedVersion loaded = loadVersion(lifecycle, row);
        ServerVersionDetail versionDetail = loaded.server.getVersionDetail();
        if (StringUtils.isNotBlank(versionDetail.getRelease_date())) {
            return loaded;
        }
        versionDetail.setRelease_date(now());
        McpVersionStorageContents updated = new McpVersionStorageContents(
            jsonBytes(loaded.server), loaded.contents.getToolContent(),
            loaded.contents.getResourceContent());
        versionStorageService.save(descriptor(row), updated);
        return new LoadedVersion(row, updated, loaded.server);
    }
    
    private List<ServerVersionDetail> buildVersionDetails(LifecycleResource lifecycle)
        throws NacosException {
        List<ServerVersionDetail> result = new ArrayList<>(lifecycle.versions.size());
        for (AiResourceVersion row : lifecycle.versions) {
            LoadedVersion loaded = loadVersion(lifecycle, row);
            ServerVersionDetail detail = copyVersionDetail(loaded.server.getVersionDetail());
            detail.setVersion(row.getVersion());
            detail.setIs_latest(row.getVersion().equals(lifecycle.latestVersion));
            result.add(detail);
        }
        return result;
    }
    
    private LifecycleResource requireLifecycleResource(AiResource resource)
        throws NacosException {
        if (resource == null || StringUtils.isBlank(resource.getNamespaceId())
            || StringUtils.isBlank(resource.getName())
            || !RESOURCE_TYPE.equals(resource.getType())) {
            throw integrityFailure("MCP Resource query returned an inconsistent row", null);
        }
        final String mcpId;
        try {
            mcpId = McpResourceExtSerializer.deserialize(resource.getExt()).getMcpId();
        } catch (IllegalArgumentException e) {
            throw integrityFailure("MCP Resource has invalid compatibility identity: "
                + resource.getName(), e);
        }
        List<AiResourceVersion> versions = listVersionRows(resource.getNamespaceId(),
            resource.getName(), null);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(resource);
        String latest = info.getLabels() == null ? null
            : info.getLabels().get(AiResourceConstants.LABEL_LATEST);
        if (StringUtils.isBlank(latest) || findVersion(versions, latest) == null) {
            latest = selectLatestOnline(versions);
        }
        return new LifecycleResource(resource, mcpId, latest, versions);
    }
    
    private AiResourceVersion resolveVersion(LifecycleResource lifecycle, String version)
        throws NacosException {
        String selected = StringUtils.isBlank(version) ? lifecycle.latestVersion : version;
        if (StringUtils.isBlank(selected)) {
            List<String> available = new ArrayList<>();
            for (AiResourceVersion row : lifecycle.versions) {
                available.add(row.getVersion());
            }
            selected = maxVersion(available);
        }
        AiResourceVersion result = findVersion(lifecycle.versions, selected);
        if (result == null) {
            throw versionNotFound(lifecycle.mcpId, selected);
        }
        return result;
    }
    
    private AiResourceVersion requireVersion(LifecycleResource lifecycle, String version)
        throws NacosException {
        AiResourceVersion result = findVersion(lifecycle.versions, version);
        if (result == null) {
            throw versionNotFound(lifecycle.mcpId, version);
        }
        return result;
    }
    
    private LoadedVersion loadVersion(LifecycleResource lifecycle, AiResourceVersion row)
        throws NacosException {
        validateVersionRow(lifecycle.resource, row);
        McpVersionStorageDescriptor descriptor = descriptor(row);
        McpVersionStorageContents contents = versionStorageService.load(descriptor);
        McpServerStorageInfo server = decode(contents.getServerContent(),
            McpServerStorageInfo.class, "Server", row.getName(), row.getVersion());
        String contentVersion = server.getVersionDetail() == null ? server.getVersion()
            : server.getVersionDetail().getVersion();
        boolean valid = lifecycle.mcpId.equals(server.getId())
            && lifecycle.resource.getName().equals(server.getName())
            && row.getVersion().equals(contentVersion)
            && (StringUtils.isBlank(server.getNamespaceId())
                || lifecycle.resource.getNamespaceId().equals(server.getNamespaceId()));
        if (!valid || server.getVersionDetail() == null) {
            throw integrityFailure("MCP Server content identity does not match lifecycle Version "
                + row.getName() + ':' + row.getVersion(), null);
        }
        return new LoadedVersion(row, contents, server);
    }
    
    private void injectOptionalContent(LoadedVersion loaded, McpServerDetailInfo result,
        String resourceName) throws NacosException {
        boolean expectsTools = loaded.server.getToolsDescriptionRef() != null;
        boolean hasTools = loaded.contents.getToolContent() != null;
        boolean expectsResources = loaded.server.getResourceDescriptionRef() != null;
        boolean hasResources = loaded.contents.getResourceContent() != null;
        if (expectsTools != hasTools || expectsResources != hasResources) {
            throw integrityFailure("MCP Version descriptor does not match Server references for "
                + resourceName + ':' + loaded.row.getVersion(), null);
        }
        if (hasTools) {
            result.setToolSpec(decode(loaded.contents.getToolContent(),
                McpToolSpecification.class, "Tools", resourceName, loaded.row.getVersion()));
        }
        if (hasResources) {
            result.setResourceSpec(decode(loaded.contents.getResourceContent(),
                McpResourceSpecification.class, "Resources", resourceName,
                loaded.row.getVersion()));
        }
    }
    
    private McpVersionStorageDescriptor descriptor(AiResourceVersion row)
        throws NacosException {
        try {
            return McpVersionStorageDescriptorSerializer.deserialize(row.getStorage());
        } catch (IllegalArgumentException e) {
            throw integrityFailure("MCP Version has an invalid storage descriptor for "
                + row.getName() + ':' + row.getVersion(), e);
        }
    }
    
    private List<AiResourceVersion> listVersionRows(String namespaceId, String name,
        String status) throws NacosException {
        List<AiResourceVersion> result = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            Page<AiResourceVersion> page = versionPersistService.list(namespaceId, name,
                RESOURCE_TYPE, status, pageNo, VERSION_PAGE_SIZE);
            if (page == null || page.getPageItems() == null) {
                throw serverError("Unable to page MCP Versions for " + name, null);
            }
            if (page.getPageItems().isEmpty()) {
                break;
            }
            for (AiResourceVersion row : page.getPageItems()) {
                boolean valid = row != null && namespaceId.equals(row.getNamespaceId())
                    && name.equals(row.getName()) && RESOURCE_TYPE.equals(row.getType())
                    && StringUtils.isNotBlank(row.getVersion());
                if (!valid) {
                    throw integrityFailure(
                        "MCP Version query returned an inconsistent row for " + name, null);
                }
                result.add(row);
            }
            if (page.getPageItems().size() < VERSION_PAGE_SIZE) {
                break;
            }
            pageNo++;
        }
        result.sort(Comparator.comparing(AiResourceVersion::getGmtCreate,
            Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(
                AiResourceVersion::getVersion, Comparator.nullsLast(String::compareTo)));
        return result;
    }
    
    private String selectLatestOnline(List<AiResourceVersion> versions) {
        List<String> online = new ArrayList<>();
        for (AiResourceVersion version : versions) {
            if (AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(
                version.getStatus())) {
                online.add(version.getVersion());
            }
        }
        return maxVersion(online);
    }
    
    private String chooseLatest(Set<String> onlineVersions, String preferred,
        String currentLatest) {
        if (StringUtils.isNotBlank(preferred) && onlineVersions.contains(preferred)) {
            return preferred;
        }
        if (StringUtils.isNotBlank(currentLatest) && onlineVersions.contains(currentLatest)) {
            return currentLatest;
        }
        return maxVersion(new ArrayList<>(onlineVersions));
    }
    
    private String maxVersion(List<String> versions) {
        if (versions.isEmpty()) {
            return null;
        }
        String semver = VersionUtils.maxSemver(versions);
        if (StringUtils.isNotBlank(semver)) {
            return semver;
        }
        String vNumber = VersionUtils.maxVNumberVersion(versions);
        if (StringUtils.isNotBlank(vNumber)) {
            return vNumber;
        }
        return Collections.max(versions);
    }
    
    private boolean containsVersion(List<AiResourceVersion> versions, String version) {
        return findVersion(versions, version) != null;
    }
    
    private AiResourceVersion findVersion(List<AiResourceVersion> versions, String version) {
        if (StringUtils.isBlank(version)) {
            return null;
        }
        for (AiResourceVersion row : versions) {
            if (row != null && version.equals(row.getVersion())) {
                return row;
            }
        }
        return null;
    }
    
    private ServerVersionDetail findVersionDetail(List<ServerVersionDetail> details,
        String version) {
        for (ServerVersionDetail detail : details) {
            if (detail != null && Objects.equals(version, detail.getVersion())) {
                return detail;
            }
        }
        return null;
    }
    
    private void validateVersionRow(AiResource resource, AiResourceVersion row)
        throws NacosException {
        boolean valid = row != null && resource.getNamespaceId().equals(row.getNamespaceId())
            && resource.getName().equals(row.getName()) && RESOURCE_TYPE.equals(row.getType())
            && StringUtils.isNotBlank(row.getVersion());
        if (!valid) {
            throw integrityFailure("MCP Version query returned an inconsistent row for "
                + resource.getName(), null);
        }
    }
    
    private VersionIdentity validateCreate(McpServerBasicInfo serverSpecification)
        throws NacosException {
        VersionIdentity identity = validateSpecification(serverSpecification);
        if (StringUtils.isNotBlank(serverSpecification.getId())) {
            try {
                McpResourceExtSerializer.validateMcpId(serverSpecification.getId());
            } catch (IllegalArgumentException e) {
                throw invalidParameter(
                    "parameter `serverSpecification.id` is not match uuid pattern,  must obey uuid pattern",
                    e);
            }
        }
        return identity;
    }
    
    private VersionIdentity validateUpdate(McpServerBasicInfo serverSpecification)
        throws NacosException {
        return validateSpecification(serverSpecification);
    }
    
    private VersionIdentity validateSpecification(McpServerBasicInfo serverSpecification)
        throws NacosException {
        if (serverSpecification == null) {
            throw invalidParameter("serverSpecification must not be null", null);
        }
        if (StringUtils.isBlank(serverSpecification.getName())) {
            throw invalidParameter("MCP server name must not be blank", null);
        }
        ServerVersionDetail versionDetail = requireVersionDetail(serverSpecification);
        return new VersionIdentity(serverSpecification.getName(), versionDetail.getVersion());
    }
    
    private ServerVersionDetail requireVersionDetail(McpServerBasicInfo serverSpecification)
        throws NacosException {
        ServerVersionDetail result = serverSpecification.getVersionDetail();
        if (result == null && StringUtils.isNotBlank(serverSpecification.getVersion())) {
            result = new ServerVersionDetail();
            result.setVersion(serverSpecification.getVersion());
            serverSpecification.setVersionDetail(result);
        }
        if (result == null || StringUtils.isBlank(result.getVersion())) {
            throw invalidParameter(
                "Version must be specified in parameter serverSpecification", null);
        }
        return result;
    }
    
    private String resolveCreateMcpId(String namespaceId, String requestedId)
        throws NacosException {
        String result = StringUtils.isBlank(requestedId) ? UUID.randomUUID().toString()
            : requestedId;
        try {
            resourceLocator.locate(namespaceId, null, result);
            throw conflict("MCP compatibility id already exists: " + result, null);
        } catch (NacosApiException e) {
            if (e.getDetailErrCode() != ErrorCode.MCP_SERVER_NOT_FOUND.getCode()) {
                throw e;
            }
        }
        return result;
    }
    
    private String normalizeNamespace(String namespaceId) throws NacosException {
        String result = StringUtils.isBlank(namespaceId) ? AiConstants.Mcp.MCP_DEFAULT_NAMESPACE
            : namespaceId;
        try {
            AgentValidationUtils.validateNamespaceId(result);
            return result;
        } catch (IllegalArgumentException e) {
            throw invalidParameter("Invalid MCP namespaceId: " + namespaceId, e);
        }
    }
    
    private String buildNameLike(String mcpName, String search) {
        if (StringUtils.isBlank(mcpName)) {
            return null;
        }
        if (Constants.MCP_LIST_SEARCH_ACCURATE.equalsIgnoreCase(search)) {
            return mcpName;
        }
        return resourceManager.generateLikeArgument(
            Constants.ALL_PATTERN + mcpName + Constants.ALL_PATTERN);
    }
    
    private boolean hasToolSpecification(McpToolSpecification specification) {
        return specification != null && (specification.getTools() != null
            || specification.getSecuritySchemes() != null
            || specification.getEncryptData() != null);
    }
    
    private boolean hasResourceSpecification(McpResourceSpecification specification) {
        return specification != null && (CollectionUtils.isNotEmpty(specification.getResources())
            || CollectionUtils.isNotEmpty(specification.getResourceTemplates())
            || specification.getEncryptData() != null);
    }
    
    private byte[] jsonBytes(Object value) {
        return JacksonUtils.toJson(value).getBytes(StandardCharsets.UTF_8);
    }
    
    private <T> T decode(byte[] content, Class<T> type, String contentName, String resourceName,
        String version) throws NacosException {
        try {
            T result = JacksonUtils.toObj(new String(content, StandardCharsets.UTF_8), type);
            if (result == null) {
                throw new IllegalArgumentException(contentName + " content is null");
            }
            return result;
        } catch (NacosDeserializationException | IllegalArgumentException e) {
            throw integrityFailure("MCP " + contentName + " content is invalid for "
                + resourceName + ':' + version, e);
        }
    }
    
    private void requireEquivalentDescriptor(AiResourceVersion row,
        McpVersionStorageDescriptor expected) throws NacosException {
        String actual = McpVersionStorageDescriptorSerializer.serialize(descriptor(row));
        String expectedJson = McpVersionStorageDescriptorSerializer.serialize(expected);
        if (!actual.equals(expectedJson)) {
            throw conflict("MCP retained Version points to different storage: " + row.getName()
                + '@' + row.getVersion(), null);
        }
    }
    
    private boolean sameContents(McpVersionStorageContents first,
        McpVersionStorageContents second) {
        return Arrays.equals(first.getServerContent(), second.getServerContent())
            && Arrays.equals(first.getToolContent(), second.getToolContent())
            && Arrays.equals(first.getResourceContent(), second.getResourceContent());
    }
    
    private ServerVersionDetail copyVersionDetail(ServerVersionDetail source) {
        ServerVersionDetail result = new ServerVersionDetail();
        if (source != null) {
            result.setVersion(source.getVersion());
            result.setRelease_date(source.getRelease_date());
            result.setIs_latest(source.getIs_latest());
        }
        return result;
    }
    
    private String now() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(RELEASE_TIME_FORMATTER);
    }
    
    private String currentOwner() {
        String identity = VisibilityHelper.resolveCurrentIdentity();
        return StringUtils.isBlank(identity) ? DEFAULT_OWNER : identity;
    }
    
    private boolean isResourceEnabled(AiResource resource) {
        return AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(resource.getStatus());
    }
    
    private void scheduleIndex(String namespaceId, String name) {
        indexMaintenanceService.schedule(namespaceId, RESOURCE_TYPE, name);
    }
    
    private void traceSuccess(String name, String version, String operation) {
        AiResourceTraceService.logSuccess(RESOURCE_TYPE, name, version, operation,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
    }
    
    private NacosApiException versionNotFound(String mcpId, String version) {
        return new NacosApiException(NacosException.NOT_FOUND,
            ErrorCode.MCP_SEVER_VERSION_NOT_FOUND,
            String.format("mcp server `%s` for version `%s` not found", mcpId, version));
    }
    
    private NacosApiException invalidParameter(String message, Throwable cause) {
        return cause == null
            ? new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, message)
            : new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, cause, message);
    }
    
    private NacosApiException conflict(String message, Throwable cause) {
        return cause == null
            ? new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, message)
            : new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, cause,
                message);
    }
    
    private NacosApiException duplicateServer(String name, Throwable cause) {
        return conflict("mcp server `" + name
            + "` has existed, please update it rather than create.", cause);
    }
    
    private NacosApiException integrityFailure(String message, Throwable cause) {
        return conflict(message, cause);
    }
    
    private NacosApiException serverError(String message, Throwable cause) {
        return cause == null
            ? new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, message)
            : new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, cause,
                message);
    }
    
    private static final class VersionIdentity {
        
        private final String name;
        
        private final String version;
        
        private VersionIdentity(String name, String version) {
            this.name = name;
            this.version = version;
        }
    }
    
    private static final class PreparedVersion {
        
        private final McpServerStorageInfo server;
        
        private final McpVersionStorageDescriptor descriptor;
        
        private final McpVersionStorageContents contents;
        
        private PreparedVersion(McpServerStorageInfo server,
            McpVersionStorageDescriptor descriptor, McpVersionStorageContents contents) {
            this.server = server;
            this.descriptor = descriptor;
            this.contents = contents;
        }
    }
    
    private static final class LifecycleResource {
        
        private final AiResource resource;
        
        private final String mcpId;
        
        private final String latestVersion;
        
        private final List<AiResourceVersion> versions;
        
        private LifecycleResource(AiResource resource, String mcpId, String latestVersion,
            List<AiResourceVersion> versions) {
            this.resource = resource;
            this.mcpId = mcpId;
            this.latestVersion = latestVersion;
            this.versions = versions;
        }
    }
    
    private static final class LoadedVersion {
        
        private final AiResourceVersion row;
        
        private final McpVersionStorageContents contents;
        
        private final McpServerStorageInfo server;
        
        private LoadedVersion(AiResourceVersion row, McpVersionStorageContents contents,
            McpServerStorageInfo server) {
            this.row = row;
            this.contents = contents;
            this.server = server;
        }
    }
}
