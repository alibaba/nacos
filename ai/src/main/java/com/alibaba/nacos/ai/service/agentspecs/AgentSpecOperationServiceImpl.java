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

package com.alibaba.nacos.ai.service.agentspecs;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.service.trace.AiResourceTraceService;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.ai.utils.AgentSpecContentDigestUtils;
import com.alibaba.nacos.ai.utils.AgentSpecSeedArchiveReader;
import com.alibaba.nacos.ai.utils.AgentSpecZipParser;
import com.alibaba.nacos.ai.utils.ExecutorUtils;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecBasicInfo;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.plugin.ai.pipeline.model.AgentSpecPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFileContent;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * AgentSpec operation service implementation. Mirrors {@code SkillOperationServiceImpl} with AgentSpec types.
 *
 * <p>Manages the full lifecycle of AgentSpecs: upload (single or batch), bootstrap built-in specs,
 * draft/review/publish workflow, querying, and deletion. Each AgentSpec consists of a main config
 * (manifest.json) and optional typed resource files (e.g., AGENTS.md). All content is persisted
 * through {@link AiResourceStorageRouter}, with metadata tracked via {@link AiResourcePersistService}
 * (meta row) and {@link AiResourceVersionPersistService} (version rows).</p>
 *
 * <p>Unlike Skill, AgentSpec uses simple vN versioning and does not maintain a separate index manifest.</p>
 *
 * <p>Version lifecycle: Draft -> (Submit) -> Reviewing -> (Pipeline / direct) -> Published/Online.</p>
 *
 * @author nacos
 */
@Service
public class AgentSpecOperationServiceImpl implements AgentSpecOperationService {
    
    private static final Logger LOGGER =
        LoggerFactory.getLogger(AgentSpecOperationServiceImpl.class);
    
    private static final String STORAGE_PROVIDER_NACOS_CONFIG = "nacos_config";
    
    private static final String RESOURCE_TYPE_AGENTSPEC = "agentspec";
    
    private static final String DEFAULT_AUTHOR = "nacos";
    
    private static final String DEFAULT_INITIAL_VERSION = "0.0.1";
    
    private static final String SCOPE_AGENTSPEC = "agentspec";
    
    private final AiResourceStorageRouter storageRouter;
    
    private final AiResourcePersistService aiResourcePersistService;
    
    private final AiResourceVersionPersistService aiResourceVersionPersistService;
    
    private final PublishPipelineExecutor publishPipelineExecutor;
    
    private final AiResourceManager resourceManager;
    
    public AgentSpecOperationServiceImpl(AiResourcePersistService aiResourcePersistService,
        AiResourceVersionPersistService aiResourceVersionPersistService,
        PublishPipelineExecutor publishPipelineExecutor,
        AiResourceManager resourceManager) {
        this.storageRouter = AiResourceStorageRouter.getInstance();
        this.aiResourcePersistService = aiResourcePersistService;
        this.aiResourceVersionPersistService = aiResourceVersionPersistService;
        this.publishPipelineExecutor = publishPipelineExecutor;
        this.resourceManager = resourceManager;
    }
    
    /**
     * Core draft creation logic shared by upload, overwrite, and createDraft flows.
     * Steps: 1) write main config + resource files to storage, 2) insert a draft version row,
     * 3) create or update the meta row with editingVersion pointer.
     */
    private void createDraftWithAgentSpec(String namespaceId, AgentSpec agentSpec, String version,
        AiResource existedMeta, boolean isNew) throws NacosException {
        String agentSpecName = agentSpec.getName();
        long uniformId = System.currentTimeMillis();
        String currentUser = VisibilityHelper.resolveCurrentIdentity();
        
        // 1) write storage for draft version (main config + resources persisted concurrently)
        saveAgentSpecFilesConcurrently(namespaceId, agentSpec, version, uniformId);
        
        // 2) insert draft version row
        resourceManager.insertVersionRow(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC,
            StringUtils.isBlank(currentUser) ? DEFAULT_AUTHOR : currentUser,
            AiResourceConstants.VERSION_STATUS_DRAFT, version, agentSpec.getDescription(),
            buildStorageJson(namespaceId, agentSpecName, version));
        
        // 3) create or update meta for editingVersion
        resourceManager.initOrUpdateMetaForDraft(namespaceId, agentSpecName,
            RESOURCE_TYPE_AGENTSPEC,
            agentSpec.getDescription(), agentSpec.getBizTags(), version, existedMeta, isNew);
    }
    
    @Override
    public AgentSpecMeta getAgentSpecDetail(String namespaceId, String agentSpecName,
        String version)
        throws NacosException {
        return getAgentSpecDetail(namespaceId, agentSpecName);
    }
    
    /**
     * Get AgentSpec detail metadata including all version summaries, labels, and online count.
     */
    @Override
    public AgentSpecMeta getAgentSpecDetail(String namespaceId, String agentSpecName)
        throws NacosException {
        // Step 1: Find meta and verify read permission
        AiResource meta =
            resourceManager.findMeta(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec not found: " + agentSpecName);
        }
        resourceManager.ensureReadableOrNotFound(meta, "AgentSpec not found: " + agentSpecName);
        ResourceVersionInfo versionInfo = AiResourceManager.requireVersionInfo(meta);
        // Step 2: Load all version rows and assemble version summary list
        Page<AiResourceVersion> versionPage =
            resourceManager.listVersions(namespaceId, agentSpecName,
                RESOURCE_TYPE_AGENTSPEC, null, 1, 200);
        List<AgentSpecMeta.AgentSpecVersionSummary> versionSummaries = new ArrayList<>();
        if (versionPage != null && versionPage.getPageItems() != null) {
            for (AiResourceVersion v : versionPage.getPageItems()) {
                if (v == null) {
                    continue;
                }
                AgentSpecMeta.AgentSpecVersionSummary summary =
                    new AgentSpecMeta.AgentSpecVersionSummary();
                summary.setVersion(v.getVersion());
                summary.setStatus(v.getStatus());
                summary.setAuthor(v.getAuthor());
                summary.setDescription(v.getDesc());
                summary.setCreateTime(v.getGmtCreate() == null ? null : v.getGmtCreate().getTime());
                summary.setUpdateTime(
                    v.getGmtModified() == null ? null : v.getGmtModified().getTime());
                summary.setPublishPipelineInfo(v.getPublishPipelineInfo());
                summary.setDownloadCount(v.getDownloadCount());
                versionSummaries.add(summary);
            }
        }
        
        // Step 3: Merge meta info and version list into AgentSpecMeta detail response
        AgentSpecMeta detail = new AgentSpecMeta();
        detail.setNamespaceId(meta.getNamespaceId());
        detail.setName(meta.getName());
        detail.setDescription(meta.getDesc());
        detail.setBizTags(meta.getBizTags());
        detail.setFrom(meta.getFrom());
        detail.setEnable(AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus()));
        detail.setScope(AiResourceManager.resolveScope(meta));
        detail.setEditingVersion(versionInfo.getEditingVersion());
        detail.setReviewingVersion(versionInfo.getReviewingVersion());
        detail.setLabels(versionInfo.getLabels());
        detail.setOnlineCnt(versionInfo.getOnlineCnt());
        detail
            .setUpdateTime(meta.getGmtModified() == null ? null : meta.getGmtModified().getTime());
        detail.setDownloadCount(meta.getDownloadCount());
        detail.setVersions(versionSummaries);
        return detail;
    }
    
    /**
     * Get the full content of a specific AgentSpec version by reading from storage.
     */
    @Override
    public AgentSpec getAgentSpecVersionDetail(String namespaceId, String agentSpecName,
        String version)
        throws NacosException {
        AiResource meta =
            resourceManager.findMeta(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec not found: " + agentSpecName);
        }
        resourceManager.ensureReadableOrNotFound(meta, "AgentSpec not found: " + agentSpecName);
        if (StringUtils.isBlank(version)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Version is required for agentspec version detail");
        }
        AiResourceVersion versionRow = resourceManager.findVersion(namespaceId, agentSpecName,
            RESOURCE_TYPE_AGENTSPEC, version);
        if (versionRow == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec version not found: " + agentSpecName + "@" + version);
        }
        return loadAgentSpecFromStorage(namespaceId, agentSpecName, version);
    }
    
    /**
     * Get agentspec version metadata without resource content. Only reads the main config file (manifest.json) and
     * builds resource list from the reference entries, skipping all resource file IO.
     */
    @Override
    public AgentSpec getAgentSpecVersionMeta(String namespaceId, String agentSpecName,
        String version)
        throws NacosException {
        AiResource meta =
            resourceManager.findMeta(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec not found: " + agentSpecName);
        }
        resourceManager.ensureReadableOrNotFound(meta, "AgentSpec not found: " + agentSpecName);
        if (StringUtils.isBlank(version)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Version is required for agentspec version meta");
        }
        AiResourceVersion versionRow = resourceManager.findVersion(namespaceId, agentSpecName,
            RESOURCE_TYPE_AGENTSPEC, version);
        if (versionRow == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec version not found: " + agentSpecName + "@" + version);
        }
        return loadAgentSpecMetaFromStorage(namespaceId, agentSpecName, version);
    }
    
    /**
     * Delete an AgentSpec entirely. Removes all version storage files, version rows, and the meta row.
     */
    @Override
    public void deleteAgentSpec(String namespaceId, String agentSpecName) throws NacosException {
        AiResource meta =
            resourceManager.findMeta(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            return;
        }
        VisibilityHelper.checkWritableResource(meta);
        
        resourceManager.deleteResourceWithVersions(namespaceId, agentSpecName,
            RESOURCE_TYPE_AGENTSPEC,
            v -> deleteAgentSpecStorageForVersion(namespaceId, agentSpecName, v.getVersion()));
    }
    
    @Override
    public Page<AgentSpecSummary> listAgentSpecs(String namespaceId, String agentSpecName,
        String search,
        int pageNo, int pageSize) throws NacosException {
        return listAgentSpecs(namespaceId, agentSpecName, search, null, null, null, pageNo,
            pageSize);
    }
    
    /**
     * List AgentSpecs with optional name filter, ordering, owner/scope filter, and pagination.
     * Supports both accurate and fuzzy name matching.
     */
    @Override
    public Page<AgentSpecSummary> listAgentSpecs(String namespaceId, String agentSpecName,
        String search,
        String orderBy, String owner, String scope, int pageNo, int pageSize)
        throws NacosException {
        String nameLike = null;
        if (StringUtils.isNotBlank(agentSpecName)) {
            if (Constants.AgentSpecs.SEARCH_ACCURATE.equalsIgnoreCase(search)) {
                nameLike = agentSpecName;
            } else {
                nameLike = resourceManager.generateLikeArgument(
                    Constants.ALL_PATTERN + agentSpecName + Constants.ALL_PATTERN);
            }
        }
        
        QueryCondition queryCondition = resourceManager.buildQueryCondition(namespaceId,
            RESOURCE_TYPE_AGENTSPEC, nameLike, null,
            VisibilityConstants.ACTION_READ);
        queryCondition.setOrderBy(orderBy);
        if (StringUtils.isNotBlank(owner)) {
            queryCondition.setOwner(owner);
        }
        if (StringUtils.isNotBlank(scope)) {
            queryCondition.setScope(scope);
        }
        if (queryCondition.isAlwaysEmpty()) {
            return AiResourceManager.buildEmptyPage(pageNo);
        }
        Page<AiResource> metaPage = resourceManager.listMeta(queryCondition, pageNo, pageSize);
        List<AgentSpecSummary> items = new ArrayList<>();
        if (metaPage != null && metaPage.getPageItems() != null) {
            for (AiResource meta : metaPage.getPageItems()) {
                if (meta == null) {
                    continue;
                }
                ResourceVersionInfo versionInfo =
                    AiResourceManager.parseVersionInfo(meta.getVersionInfo());
                AgentSpecSummary item = new AgentSpecSummary();
                item.setNamespaceId(namespaceId);
                item.setName(meta.getName());
                item.setDescription(meta.getDesc());
                item.setEnable(
                    AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus()));
                item.setBizTags(meta.getBizTags());
                item.setFrom(meta.getFrom());
                item.setScope(AiResourceManager.resolveScope(meta));
                item.setUpdateTime(
                    meta.getGmtModified() == null ? null : meta.getGmtModified().getTime());
                item.setDownloadCount(meta.getDownloadCount());
                if (versionInfo != null) {
                    item.setLabels(versionInfo.getLabels());
                    item.setEditingVersion(versionInfo.getEditingVersion());
                    item.setReviewingVersion(versionInfo.getReviewingVersion());
                    item.setOnlineCnt(versionInfo.getOnlineCnt());
                }
                items.add(item);
            }
        }
        
        return AiResourceManager.buildPageResult(items, metaPage, pageNo);
    }
    
    /**
     * Upload AgentSpec(s) from a ZIP archive. Supports both single-spec ZIPs and multi-spec seed archives
     * (containing multiple inner ZIPs). Each spec is processed via {@link #uploadSingleAgentSpecFromZip}.
     */
    @Override
    public String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes, boolean overwrite)
        throws NacosException {
        // Try to parse ZIP as a multi-spec seed archive (containing multiple inner ZIPs)
        List<AgentSpecSeedArchiveReader.AgentSpecPackage> packages = readUploadPackages(zipBytes);
        if (!packages.isEmpty()) {
            // Multi-spec archive: import each one and return summary
            if (packages.size() == 1) {
                return uploadSingleAgentSpecFromZip(namespaceId, packages.get(0).getZipBytes(),
                    overwrite);
            }
            List<String> importedNames = new ArrayList<>(packages.size());
            for (AgentSpecSeedArchiveReader.AgentSpecPackage each : packages) {
                importedNames
                    .add(uploadSingleAgentSpecFromZip(namespaceId, each.getZipBytes(), overwrite));
            }
            return String.format("Imported %d agentspecs: %s", importedNames.size(),
                String.join(", ", importedNames));
        }
        // Not a seed archive: treat as a single AgentSpec ZIP
        return uploadSingleAgentSpecFromZip(namespaceId, zipBytes, overwrite);
    }
    
    /**
     * Try to read the ZIP as a multi-spec seed archive. Returns empty list if it's a regular single-spec ZIP.
     */
    private List<AgentSpecSeedArchiveReader.AgentSpecPackage> readUploadPackages(byte[] zipBytes)
        throws NacosException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipBytes)) {
            return AgentSpecSeedArchiveReader.read(inputStream);
        } catch (IOException e) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR, e,
                "Failed to read agentspec zip archive");
        }
    }
    
    /**
     * Upload a single AgentSpec from ZIP bytes.
     * If overwrite=true, replaces existing draft or creates new. Otherwise fails on working version conflict.
     */
    private String uploadSingleAgentSpecFromZip(String namespaceId, byte[] zipBytes,
        boolean overwrite)
        throws NacosException {
        // Step 1: Parse ZIP and validate agentspec name
        AgentSpec agentSpec = AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, namespaceId);
        if (agentSpec == null || StringUtils.isBlank(agentSpec.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "AgentSpec name is required");
        }
        String name = agentSpec.getName();
        // Step 2: Check if an agentspec with the same name already exists
        AiResource meta = resourceManager.findMeta(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        if (overwrite) {
            return overwriteUploadedAgentSpec(namespaceId, agentSpec, meta);
        }
        if (meta == null) {
            // Brand-new agentspec: create initial draft
            String version = DEFAULT_INITIAL_VERSION;
            createDraftWithAgentSpec(namespaceId, agentSpec, version, null, true);
            AiResourceTraceService.logSuccess(RESOURCE_TYPE_AGENTSPEC, name, version,
                AiResourceTraceService.OP_UPLOAD,
                VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
            return name;
        }
        
        // Non-overwrite upload for existing agentspec: ensure no editing/reviewing version exists
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        AiResourceManager.ensureNoWorkingVersion(info, "upload");
        
        // Step 3: Assign new version number and create draft
        String newVersion = nextVersion(namespaceId, name);
        createDraftWithAgentSpec(namespaceId, agentSpec, newVersion, meta, false);
        resourceManager.syncImportedMeta(namespaceId, meta, agentSpec.getDescription(),
            agentSpec.getBizTags());
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_AGENTSPEC, name, newVersion,
            AiResourceTraceService.OP_UPLOAD,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
        return name;
    }
    
    /**
     * Bootstrap a built-in AgentSpec from a ZIP archive (delegates to the overload with null source).
     */
    @Override
    public void bootstrapAgentSpecFromZip(String namespaceId, byte[] zipBytes)
        throws NacosException {
        bootstrapAgentSpecFromZip(namespaceId, zipBytes, null);
    }
    
    /**
     * Bootstrap a built-in AgentSpec from a ZIP archive. Skips if the spec already exists
     * (unless it detects the existing content is broken and needs repair).
     * Directly writes storage and creates a published meta + version in one step (no draft workflow).
     */
    @Override
    public void bootstrapAgentSpecFromZip(String namespaceId, byte[] zipBytes, String from)
        throws NacosException {
        // Step 1: Parse ZIP and validate
        AgentSpec agentSpec = AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, namespaceId);
        if (agentSpec == null || StringUtils.isBlank(agentSpec.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "AgentSpec name is required");
        }
        String name = agentSpec.getName();
        // Step 2: If already exists, try to repair broken built-in data; otherwise skip
        AiResource existingMeta =
            resourceManager.findMeta(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        if (existingMeta != null) {
            if (repairBuiltInAgentSpecIfBroken(namespaceId, existingMeta, agentSpec)) {
                LOGGER.info("Repaired built-in agentspec bootstrap content for {}", name);
                return;
            }
            LOGGER.info("Skip built-in agentspec bootstrap because agentspec already exists: {}",
                name);
            return;
        }
        
        // Step 3: Brand-new bootstrap: write to storage and directly create published meta + version (skip draft workflow)
        String version = DEFAULT_INITIAL_VERSION;
        long uniformId = System.currentTimeMillis();
        writeAgentSpecToStorage(namespaceId, agentSpec, version, uniformId);
        
        resourceManager.insertBootstrapMeta(namespaceId, name, RESOURCE_TYPE_AGENTSPEC,
            agentSpec.getDescription(), agentSpec.getBizTags(), DEFAULT_AUTHOR, from, version,
            buildStorageJson(namespaceId, name, version));
    }
    
    /**
     * Attempt to repair a broken built-in AgentSpec by re-writing storage content.
     * Only repairs if: owned by default author, no working versions, latest version is online,
     * and critical content (e.g., AGENTS.md) is missing from the current storage.
     *
     * @return true if repair was performed
     */
    private boolean repairBuiltInAgentSpecIfBroken(String namespaceId, AiResource meta,
        AgentSpec bundledAgentSpec)
        throws NacosException {
        if (meta == null || bundledAgentSpec == null
            || StringUtils.isBlank(bundledAgentSpec.getName())) {
            return false;
        }
        // Precondition: only repair built-in agentspecs created by the default author
        if (!StringUtils.equals(DEFAULT_AUTHOR, meta.getOwner())) {
            return false;
        }
        // Precondition: no editing/reviewing version in progress (avoid interfering with user operations)
        ResourceVersionInfo versionInfo = AiResourceManager.requireVersionInfo(meta);
        if (StringUtils.isNotBlank(versionInfo.getEditingVersion())
            || StringUtils.isNotBlank(versionInfo.getReviewingVersion())) {
            return false;
        }
        // Precondition: must have a latest label pointing to an online version created by the default author
        String latestVersion = versionInfo.getLabels() == null ? null
            : versionInfo.getLabels().get(AiResourceConstants.LABEL_LATEST);
        if (StringUtils.isBlank(latestVersion)) {
            return false;
        }
        AiResourceVersion versionRow =
            resourceManager.findVersion(namespaceId, bundledAgentSpec.getName(),
                RESOURCE_TYPE_AGENTSPEC, latestVersion);
        if (versionRow == null
            || !AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(versionRow.getStatus())
            || !StringUtils.equals(DEFAULT_AUTHOR, versionRow.getAuthor())) {
            return false;
        }
        
        // Read current storage content and compare with bundled content to detect missing critical data
        AgentSpec currentAgentSpec;
        try {
            currentAgentSpec =
                loadAgentSpecFromStorage(namespaceId, bundledAgentSpec.getName(), latestVersion);
        } catch (NacosException e) {
            currentAgentSpec = null;
        }
        if (!isBuiltInContentMissing(currentAgentSpec, bundledAgentSpec)) {
            return false;
        }
        
        // Content is missing: overwrite storage with bundled data and update version/meta records
        long uniformId = System.currentTimeMillis();
        writeAgentSpecToStorage(namespaceId, bundledAgentSpec, latestVersion, uniformId);
        resourceManager.updateVersionStorageAndDesc(namespaceId, bundledAgentSpec.getName(),
            RESOURCE_TYPE_AGENTSPEC, latestVersion,
            buildStorageJson(namespaceId, bundledAgentSpec.getName(),
                latestVersion),
            bundledAgentSpec.getDescription());
        resourceManager.syncImportedMeta(namespaceId, meta, bundledAgentSpec.getDescription(),
            bundledAgentSpec.getBizTags());
        return true;
    }
    
    /**
     * Handle overwrite upload: if an editing draft exists, overwrite it in-place;
     * otherwise create a new draft with a bumped version.
     */
    private String overwriteUploadedAgentSpec(String namespaceId, AgentSpec agentSpec,
        AiResource meta)
        throws NacosException {
        String name = agentSpec.getName();
        // No meta record = brand-new agentspec, create initial draft directly
        if (meta == null) {
            createDraftWithAgentSpec(namespaceId, agentSpec, DEFAULT_INITIAL_VERSION, null, true);
            return name;
        }
        
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        // Existing editing draft -> overwrite draft content in-place
        if (StringUtils.isNotBlank(editing)) {
            overwriteEditingDraft(namespaceId, agentSpec, meta, editing);
            return name;
        }
        
        // No editing draft -> assign new version number and create new draft
        String newVersion = nextVersion(namespaceId, name);
        createDraftWithAgentSpec(namespaceId, agentSpec, newVersion, meta, false);
        resourceManager.syncImportedMeta(namespaceId, meta, agentSpec.getDescription(),
            agentSpec.getBizTags());
        return name;
    }
    
    /**
     * Overwrite an existing editing draft's storage content, version description, and meta info.
     */
    private void overwriteEditingDraft(String namespaceId, AgentSpec agentSpec, AiResource meta,
        String editing)
        throws NacosException {
        resourceManager.requireDraftVersion(namespaceId, agentSpec.getName(),
            RESOURCE_TYPE_AGENTSPEC, editing);
        long uniformId = System.currentTimeMillis();
        writeAgentSpecToStorage(namespaceId, agentSpec, editing, uniformId);
        resourceManager.updateVersionStorageAndDesc(namespaceId, agentSpec.getName(),
            RESOURCE_TYPE_AGENTSPEC, editing,
            buildStorageJson(namespaceId, agentSpec.getName(), editing),
            agentSpec.getDescription());
        resourceManager.syncImportedMeta(namespaceId, meta, agentSpec.getDescription(),
            agentSpec.getBizTags());
    }
    
    /**
     * Search AgentSpecs by keyword (fuzzy name match). Only returns enabled specs with at least one online version.
     */
    @Override
    public Page<AgentSpecBasicInfo> searchAgentSpecs(String namespaceId, String keyword, int pageNo,
        int pageSize)
        throws NacosException {
        // Build fuzzy query condition
        String nameLike = StringUtils.isBlank(keyword) ? null
            : resourceManager
                .generateLikeArgument(Constants.ALL_PATTERN + keyword + Constants.ALL_PATTERN);
        QueryCondition queryCondition = resourceManager.buildQueryCondition(namespaceId,
            RESOURCE_TYPE_AGENTSPEC, nameLike, null,
            VisibilityConstants.ACTION_READ);
        if (queryCondition.isAlwaysEmpty()) {
            return AiResourceManager.buildEmptyPage(pageNo);
        }
        Page<AiResource> metaPage = resourceManager.listMeta(queryCondition, pageNo, pageSize);
        List<AgentSpecBasicInfo> items = new ArrayList<>();
        if (metaPage != null && metaPage.getPageItems() != null) {
            for (AiResource meta : metaPage.getPageItems()) {
                if (meta == null) {
                    continue;
                }
                // Only return enabled agentspecs with at least one online version (for client-side search)
                if (!AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus())) {
                    continue;
                }
                ResourceVersionInfo info =
                    AiResourceManager.parseVersionInfo(meta.getVersionInfo());
                if (info == null || info.getOnlineCnt() == null || info.getOnlineCnt() <= 0) {
                    continue;
                }
                AgentSpecBasicInfo basicInfo = new AgentSpecBasicInfo();
                basicInfo.setName(meta.getName());
                basicInfo.setDescription(meta.getDesc());
                items.add(basicInfo);
            }
        }
        return AiResourceManager.buildPageResult(items, metaPage, pageNo);
    }
    
    /**
     * Query an AgentSpec for client consumption. Resolves the target version via explicit version or label,
     * checks the spec is enabled and the version is online, then loads content from storage.
     */
    @Override
    public AgentSpec queryAgentSpec(String namespaceId, String name, String version, String label)
        throws NacosException {
        // Step 1: Verify meta exists, is readable, and is enabled
        AiResource meta = resourceManager.findMeta(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec not found: " + name);
        }
        resourceManager.ensureReadableOrNotFound(meta, "AgentSpec not found: " + name);
        if (!AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec disabled: " + name);
        }
        // Step 2: Resolve target version from version/label params (labels like "latest" are looked up in meta versionInfo)
        String resolved = AiResourceManager.resolveVersion(meta, version, label);
        if (StringUtils.isBlank(resolved)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec version not found: " + name);
        }
        // Step 3: Confirm version is online, then load full content from storage
        AiResourceVersion versionRow = resourceManager.findVersion(namespaceId, name,
            RESOURCE_TYPE_AGENTSPEC, resolved);
        if (versionRow == null || !AiResourceConstants.VERSION_STATUS_ONLINE
            .equalsIgnoreCase(versionRow.getStatus())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec version not online: " + name);
        }
        return loadAgentSpecFromStorage(namespaceId, name, resolved);
    }
    
    /**
     * Query an AgentSpec for the client listener path with MD5-based not-modified semantics.
     * When {@code clientMd5} matches the stored content MD5, returns a not-modified result so the
     * controller can return HTTP 304 without loading content.
     */
    @Override
    public AgentSpecQueryResult queryAgentSpecForClient(String namespaceId, String name,
        String version, String label, String clientMd5) throws NacosException {
        // Step 1: Resolve version via existing logic (validates meta, status, etc.)
        AiResource meta = resourceManager.findMeta(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec not found: " + name);
        }
        resourceManager.ensureReadableOrNotFound(meta, "AgentSpec not found: " + name);
        if (!AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec disabled: " + name);
        }
        String resolved = AiResourceManager.resolveVersion(meta, version, label);
        if (StringUtils.isBlank(resolved)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec version not found: " + name);
        }
        
        // Step 2: Read stored contentMd5 from version row's storage JSON
        String storedMd5 = readStoredContentMd5(namespaceId, name, resolved);
        
        // Step 3: Fast path — client cache is fresh
        if (StringUtils.isNotBlank(storedMd5) && StringUtils.isNotBlank(clientMd5)
            && storedMd5.equals(clientMd5)) {
            return AgentSpecQueryResult.notModified(storedMd5, resolved);
        }
        
        // Step 4: Load full AgentSpec from storage
        AiResourceVersion versionRow = resourceManager.findVersion(namespaceId, name,
            RESOURCE_TYPE_AGENTSPEC, resolved);
        if (versionRow == null || !AiResourceConstants.VERSION_STATUS_ONLINE
            .equalsIgnoreCase(versionRow.getStatus())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec version not online: " + name);
        }
        AgentSpec agentSpec = loadAgentSpecFromStorage(namespaceId, name, resolved);
        
        // Step 5: Determine effective MD5; back-fill if missing (legacy data)
        String effectiveMd5 = storedMd5;
        if (StringUtils.isBlank(effectiveMd5)) {
            effectiveMd5 = backfillContentMd5(namespaceId, name, resolved, agentSpec);
        }
        return new AgentSpecQueryResult(agentSpec, effectiveMd5, resolved);
    }
    
    /**
     * Read the persisted contentMd5 from the version row's storage JSON column.
     */
    private String readStoredContentMd5(String namespaceId, String name,
        String resolvedVersion) {
        if (StringUtils.isBlank(resolvedVersion)) {
            return null;
        }
        AiResourceVersion versionRow = aiResourceVersionPersistService.find(namespaceId, name,
            RESOURCE_TYPE_AGENTSPEC, resolvedVersion);
        if (versionRow == null || StringUtils.isBlank(versionRow.getStorage())) {
            return null;
        }
        try {
            Map<String, Object> map = JacksonUtils.toObj(versionRow.getStorage(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
            Object md5 = map == null ? null
                : map.get(Constants.Skills.STORAGE_KEY_CONTENT_MD5);
            return md5 instanceof String ? (String) md5 : null;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse storage JSON for agentspec {}@{}, ignored",
                name, resolvedVersion, e);
            return null;
        }
    }
    
    /**
     * Back-fill: compute the content MD5 from the loaded AgentSpec and persist it into the
     * version row's storage column. Persistence failure is swallowed and logged.
     */
    private String backfillContentMd5(String namespaceId, String name,
        String resolvedVersion, AgentSpec agentSpec) {
        String computed;
        try {
            computed = AgentSpecContentDigestUtils.computeContentMd5(agentSpec);
        } catch (Exception e) {
            LOGGER.warn("Failed to compute content MD5 for agentspec {}@{}",
                name, resolvedVersion, e);
            return null;
        }
        if (StringUtils.isBlank(resolvedVersion)) {
            return computed;
        }
        try {
            aiResourceVersionPersistService.updateStorageMd5(namespaceId, name,
                RESOURCE_TYPE_AGENTSPEC, resolvedVersion, computed);
        } catch (Exception e) {
            LOGGER.warn(
                "Failed to back-fill content MD5 for agentspec {}@{}, response is still 200",
                name, resolvedVersion, e);
        }
        return computed;
    }
    
    /**
     * Create a new draft version for an existing or brand-new AgentSpec.
     * For existing specs with a base version, copies storage content from that version.
     * For brand-new specs, creates an empty AgentSpec draft.
     *
     * @return the newly created draft version string (e.g., "0.0.1", "0.0.2")
     */
    @Override
    public String createDraft(String namespaceId, String name, String basedOnVersion,
        String targetVersion)
        throws NacosException {
        AiResource meta = resourceManager.findMeta(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        // ---- Case A: Brand-new agentspec ----
        if (meta == null) {
            if (StringUtils.isNotBlank(basedOnVersion)) {
                throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec not found: " + name
                        + ", cannot use basedOnVersion for a brand-new agentspec");
            }
            // Create empty draft
            String version =
                resolveSpecifiedDraftVersion(namespaceId, name, targetVersion, null, null);
            AgentSpec emptyAgentSpec = new AgentSpec();
            emptyAgentSpec.setName(name);
            emptyAgentSpec.setNamespaceId(namespaceId);
            createDraftWithAgentSpec(namespaceId, emptyAgentSpec, version, null, true);
            AiResourceTraceService.logSuccess(RESOURCE_TYPE_AGENTSPEC, name, version,
                AiResourceTraceService.OP_CREATE_DRAFT, VisibilityHelper.resolveCurrentIdentity(),
                VisibilityHelper.resolveClientIp());
            return version;
        }
        
        // ---- Case B: Existing agentspec, fork from existing version ----
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        AiResourceManager.ensureNoWorkingVersion(info, "create draft");
        
        String base =
            resourceManager.resolveBaseVersion(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, meta,
                basedOnVersion);
        String newVersion =
            resolveSpecifiedDraftVersion(namespaceId, name, targetVersion, basedOnVersion, base);
        if (StringUtils.isBlank(base)) {
            // No existing version to fork from -> create empty draft
            AgentSpec emptyAgentSpec = new AgentSpec();
            emptyAgentSpec.setName(name);
            emptyAgentSpec.setNamespaceId(namespaceId);
            createDraftWithAgentSpec(namespaceId, emptyAgentSpec, newVersion, meta, false);
            AiResourceTraceService.logSuccess(RESOURCE_TYPE_AGENTSPEC, name, newVersion,
                AiResourceTraceService.OP_CREATE_DRAFT, VisibilityHelper.resolveCurrentIdentity(),
                VisibilityHelper.resolveClientIp());
            return newVersion;
        }
        
        // Step 1: Copy storage content from base version to new version path
        AgentSpec baseAgentSpec = loadAgentSpecFromStorage(namespaceId, name, base);
        long uniformId = System.currentTimeMillis();
        writeAgentSpecToStorage(namespaceId, baseAgentSpec, newVersion, uniformId);
        
        // Step 2: Insert draft version row
        resourceManager.insertVersionRow(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, DEFAULT_AUTHOR,
            AiResourceConstants.VERSION_STATUS_DRAFT, newVersion, baseAgentSpec.getDescription(),
            buildStorageJson(namespaceId, name, newVersion));
        
        // Step 3: Update meta's editingVersion pointer
        resourceManager.markEditingVersionCas(namespaceId, meta, info, newVersion,
            "create draft");
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_AGENTSPEC, name, newVersion,
            AiResourceTraceService.OP_CREATE_DRAFT, VisibilityHelper.resolveCurrentIdentity(),
            VisibilityHelper.resolveClientIp());
        return newVersion;
    }
    
    /**
     * Update the content of an existing editing draft. If no meta exists, auto-creates a new draft.
     * Writes updated files to storage and bumps the meta description.
     */
    @Override
    public void updateDraft(String namespaceId, AgentSpec draftAgentSpec) throws NacosException {
        if (draftAgentSpec == null || StringUtils.isBlank(draftAgentSpec.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "AgentSpec name is required");
        }
        String name = draftAgentSpec.getName();
        AiResource meta = resourceManager.findMeta(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        // Auto-create brand-new draft when meta does not exist (unlike Skill, AgentSpec's updateDraft supports auto-creation)
        if (meta == null) {
            createDraftWithAgentSpec(namespaceId, draftAgentSpec, DEFAULT_INITIAL_VERSION, null,
                true);
            AiResourceTraceService.logSuccess(RESOURCE_TYPE_AGENTSPEC, name,
                DEFAULT_INITIAL_VERSION,
                AiResourceTraceService.OP_CREATE_DRAFT, VisibilityHelper.resolveCurrentIdentity(),
                VisibilityHelper.resolveClientIp());
            return;
        }
        // Confirm write permission and an editing draft exists
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        if (StringUtils.isBlank(editing)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "No editing draft exists for agentspec: " + name);
        }
        resourceManager.requireDraftVersion(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, editing);
        
        // Overwrite storage files with new content, update version row and meta description
        long uniformId = System.currentTimeMillis();
        writeAgentSpecToStorage(namespaceId, draftAgentSpec, editing, uniformId);
        resourceManager.updateVersionStorageAndDesc(namespaceId, name, RESOURCE_TYPE_AGENTSPEC,
            editing,
            buildStorageJson(namespaceId, name, editing), draftAgentSpec.getDescription());
        resourceManager.bumpMetaDescription(namespaceId, meta, draftAgentSpec.getDescription());
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_AGENTSPEC, name, editing,
            AiResourceTraceService.OP_UPDATE_DRAFT,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
    }
    
    /**
     * Delete the current editing draft. Removes storage files, version row,
     * and clears the editingVersion pointer in meta.
     */
    @Override
    public void deleteDraft(String namespaceId, String name) throws NacosException {
        resourceManager.doDeleteDraft(namespaceId, name, RESOURCE_TYPE_AGENTSPEC,
            v -> deleteAgentSpecStorageForVersion(namespaceId, name, v.getVersion()));
    }
    
    /**
     * Submit a draft for review and publish.
     *
     * <p>Flow: resolve target version -> build pipeline context (with lazy file loading) ->
     * check if a publish pipeline is available. If no pipeline, publish directly;
     * otherwise move to reviewing and run the pipeline asynchronously.</p>
     */
    @Override
    public String submit(String namespaceId, String name, String version) throws NacosException {
        // Step 1: Verify meta exists and has write permission
        AiResource meta = resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        
        // Step 2: Determine the target version to submit
        String target =
            resourceManager.resolveSubmitTarget(info, version, RESOURCE_TYPE_AGENTSPEC, name);
        
        AiResourceVersion v =
            resourceManager.findVersion(namespaceId, name, RESOURCE_TYPE_AGENTSPEC,
                target);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec version not found: " + name + "@" + target);
        }
        
        final String finalTarget = target;
        
        // Step 3: Build pipeline context (file list uses lazy loading, only read from storage when pipeline actually runs)
        AgentSpecPipelineContext ctx = new AgentSpecPipelineContext();
        ctx.setNamespaceId(namespaceId);
        ctx.setResourceName(name);
        ctx.setVersion(finalTarget);
        ctx.setFilesLoader(() -> {
            try {
                return buildPipelineFiles(loadAgentSpecFromStorage(namespaceId, name, finalTarget));
            } catch (NacosException e) {
                throw new IllegalStateException(
                    "Failed to load AgentSpec files for pipeline execution", e);
            }
        });
        
        // Step 4: Check if a publish pipeline is available
        if (!publishPipelineExecutor.isPipelineAvailable(ctx.getResourceType())) {
            // No pipeline available -> skip review and publish directly
            resourceManager.directPublishVersion(namespaceId, meta, info, finalTarget, true);
            return finalTarget;
        }
        
        // Step 5: Move version status to reviewing, then run pipeline asynchronously; fall back to direct publish if startup fails
        resourceManager.moveToReviewing(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, finalTarget,
            meta, info);
        if (!resourceManager.runPipelineExecution(namespaceId, name, RESOURCE_TYPE_AGENTSPEC,
            finalTarget,
            ctx, publishPipelineExecutor)) {
            resourceManager.directPublishVersion(namespaceId, meta, info, finalTarget, true);
        }
        
        return finalTarget;
    }
    
    /**
     * Publish a version: update version status to online and compute content MD5.
     */
    @Override
    public void publish(String namespaceId, String name, String version,
        boolean updateLatestLabel) throws NacosException {
        resourceManager.doPublish(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, version, true);
        computeAndStoreContentMd5(namespaceId, name, version);
    }
    
    /**
     * Force-publish a draft, reviewing, or reviewed version.
     */
    @Override
    public void forcePublish(String namespaceId, String name, String version,
        boolean updateLatestLabel) throws NacosException {
        resourceManager.doForcePublish(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, version, true);
        computeAndStoreContentMd5(namespaceId, name, version);
    }
    
    /**
     * Compute content MD5 for a published version and persist it to the storage column.
     * Failure is logged but does not break the publish operation.
     */
    private void computeAndStoreContentMd5(String namespaceId, String name, String version) {
        try {
            AgentSpec agentSpec = loadAgentSpecFromStorage(namespaceId, name, version);
            String md5 = AgentSpecContentDigestUtils.computeContentMd5(agentSpec);
            aiResourceVersionPersistService.updateStorageMd5(namespaceId, name,
                RESOURCE_TYPE_AGENTSPEC, version, md5);
        } catch (Exception e) {
            LOGGER.warn("Failed to compute/store content MD5 for agentspec {}@{}",
                name, version, e);
        }
    }
    
    @Override
    public void redraft(String namespaceId, String name, String version) throws NacosException {
        resourceManager.doRedraft(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, version);
    }
    
    /**
     * Update version labels (e.g., "latest") for an AgentSpec.
     */
    @Override
    public void updateLabels(String namespaceId, String name, Map<String, String> labels)
        throws NacosException {
        resourceManager.validateAndUpdateLabels(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, labels);
    }
    
    /**
     * Update business tags on the AgentSpec meta.
     */
    @Override
    public void updateBizTags(String namespaceId, String name, String bizTags)
        throws NacosException {
        AiResource meta = resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        VisibilityHelper.checkWritableResource(meta);
        resourceManager.updateBizTagsCas(namespaceId, meta, bizTags);
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_AGENTSPEC, name, null,
            AiResourceTraceService.OP_UPDATE_BIZ_TAGS,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
    }
    
    /**
     * Toggle online/offline status at either AgentSpec scope (enable/disable the entire spec)
     * or version scope (toggle a specific version's online status).
     */
    @Override
    public void changeOnlineStatus(String namespaceId, String name, String scope, String version,
        boolean online)
        throws NacosException {
        AiResource meta = resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        
        // Determine operation scope: agentspec scope (enable/disable entire spec) vs version scope (toggle single version)
        boolean agentSpecScope =
            SCOPE_AGENTSPEC.equalsIgnoreCase(scope) || StringUtils.isBlank(version);
        if (agentSpecScope) {
            // Enable/disable entire agentspec: update meta status
            resourceManager.metaEnableDisable(namespaceId, meta, online);
            return;
        }
        
        // Single version toggle (unlike Skill, AgentSpec has no index manifest to sync)
        resourceManager.toggleVersionOnlineStatus(namespaceId, meta, info, version, online);
    }
    
    /**
     * Update the visibility scope of an AgentSpec.
     */
    @Override
    public void updateScope(String namespaceId, String name, String scope) throws NacosException {
        resourceManager.doUpdateScope(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, scope);
    }
    
    // ---- Private helper methods ----
    
    /**
     * Build main agentspec content as JSON bytes (manifest.json content stored as-is).
     */
    private static byte[] buildMainContent(AgentSpec agentSpec, long uniformId) {
        AgentSpecMainConfig mainConfig = new AgentSpecMainConfig();
        mainConfig.setName(agentSpec.getName());
        mainConfig.setDescription(agentSpec.getDescription());
        mainConfig.setContent(agentSpec.getContent());
        mainConfig.setUniformId(uniformId);
        List<AgentSpecResourceRef> resourceRefs = new ArrayList<>(
            agentSpec.getResource() != null ? agentSpec.getResource().size() : 16);
        if (agentSpec.getResource() != null) {
            for (Map.Entry<String, AgentSpecResource> entry : agentSpec.getResource().entrySet()) {
                AgentSpecResource resource = entry.getValue();
                AgentSpecResourceRef ref = new AgentSpecResourceRef();
                ref.setName(resource.getName());
                ref.setType(resource.getType());
                resourceRefs.add(ref);
            }
        }
        mainConfig.setResources(resourceRefs);
        return JacksonUtils.toJson(mainConfig).getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * Build resource content as JSON bytes.
     */
    private static byte[] buildResourceContent(AgentSpecResource resource, long uniformId) {
        Map<String, Object> metadata = resource.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>(4);
            resource.setMetadata(metadata);
        }
        metadata.put("uniformId", uniformId);
        return JacksonUtils.toJson(resource).getBytes(StandardCharsets.UTF_8);
    }
    
    /**
     * Resolve the storage provider from system config. Defaults to "nacos_config".
     */
    private static String resolveStorageProvider() {
        String provider =
            EnvUtil.getProperty(Constants.AgentSpecs.AGENTSPEC_STORAGE_PROVIDER_CONFIG_KEY,
                STORAGE_PROVIDER_NACOS_CONFIG);
        return StringUtils.isBlank(provider) ? STORAGE_PROVIDER_NACOS_CONFIG : provider.trim();
    }
    
    /**
     * Build storage metadata JSON for version row (provider + scope).
     */
    private static String buildStorageJson(String namespaceId, String agentSpecName,
        String version) {
        Map<String, Object> json = new HashMap<>(4);
        json.put("provider", resolveStorageProvider());
        json.put("scope", namespaceId + ":" + agentSpecName + ":" + version);
        return JacksonUtils.toJson(json);
    }
    
    /**
     * Compute the next vN version number based on existing versions.
     */
    private String nextVersion(String namespaceId, String name) {
        List<String> existingVersions = resourceManager.listExistingVersions(namespaceId, name,
            RESOURCE_TYPE_AGENTSPEC);
        String maxSemver = VersionUtils.maxSemver(existingVersions);
        if (StringUtils.isNotBlank(maxSemver)) {
            return VersionUtils.nextSemverPatch(maxSemver);
        }
        int maxLegacy = VersionUtils.maxVNumber(existingVersions);
        if (maxLegacy > 0) {
            return "v" + (maxLegacy + 1);
        }
        return DEFAULT_INITIAL_VERSION;
    }
    
    private String resolveSpecifiedDraftVersion(String namespaceId, String name,
        String targetVersion,
        String basedOnVersion, String baseVersion) throws NacosException {
        if (StringUtils.isBlank(targetVersion)) {
            return nextVersion(namespaceId, name);
        }
        String candidate = targetVersion.trim();
        if (!VersionUtils.isSupportedVersionFormat(candidate)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Invalid targetVersion format: " + candidate + ", expected x.y.z or vN");
        }
        List<String> existingVersions = resourceManager.listExistingVersions(namespaceId, name,
            RESOURCE_TYPE_AGENTSPEC);
        if (existingVersions.contains(candidate)) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "targetVersion already exists: " + candidate);
        }
        if (StringUtils.isNotBlank(basedOnVersion) && StringUtils.isNotBlank(baseVersion)) {
            boolean isGreater = VersionUtils.isGreaterVersion(candidate, baseVersion);
            if (!isGreater) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "targetVersion must be greater than basedOnVersion, basedOnVersion="
                        + baseVersion
                        + ", targetVersion=" + candidate);
            }
        }
        return candidate;
    }
    
    /**
     * Check whether the built-in AgentSpec content is missing critical data (e.g., main content or AGENTS.md).
     * Used by bootstrap repair logic to decide if re-writing is needed.
     */
    private static boolean isBuiltInContentMissing(AgentSpec currentAgentSpec,
        AgentSpec bundledAgentSpec) {
        if (bundledAgentSpec == null) {
            return false;
        }
        // Current storage has no content at all -> considered missing
        if (currentAgentSpec == null) {
            return true;
        }
        // Bundled has main content but current storage does not -> missing
        if (StringUtils.isNotBlank(bundledAgentSpec.getContent())
            && StringUtils.isBlank(currentAgentSpec.getContent())) {
            return true;
        }
        // Check if bundled has AGENTS.md resource but current storage is missing it -> needs repair
        Map<String, AgentSpecResource> bundledResources = bundledAgentSpec.getResource();
        if (bundledResources == null || bundledResources.isEmpty()) {
            return false;
        }
        Map<String, AgentSpecResource> currentResources = currentAgentSpec.getResource();
        if (currentResources == null || currentResources.isEmpty()) {
            return true;
        }
        String bundledAgentsContent = extractAgentsContent(bundledResources);
        if (StringUtils.isBlank(bundledAgentsContent)) {
            return false;
        }
        String currentAgentsContent = extractAgentsContent(currentResources);
        return StringUtils.isBlank(currentAgentsContent);
    }
    
    /**
     * Extract the content of the AGENTS.md resource from a resource map (case-insensitive match).
     */
    private static String extractAgentsContent(Map<String, AgentSpecResource> resources) {
        if (resources == null || resources.isEmpty()) {
            return null;
        }
        for (AgentSpecResource resource : resources.values()) {
            if (resource == null || StringUtils.isBlank(resource.getName())) {
                continue;
            }
            String normalizedName = resource.getName().trim();
            int lastSlash = normalizedName.lastIndexOf('/');
            if (lastSlash >= 0) {
                normalizedName = normalizedName.substring(lastSlash + 1);
            }
            if ("AGENTS.md".equalsIgnoreCase(normalizedName)) {
                return resource.getContent();
            }
        }
        return null;
    }
    
    /**
     * Write all AgentSpec files (main config + resources) to storage.
     * Main config is serialized as JSON containing name, description, content, and resource references.
     * Each resource file is serialized as JSON with a uniformId for consistency tracking.
     *
     * <p>Files are persisted concurrently via a dedicated IO executor to reduce latency
     * when AgentSpec contains multiple resource files. Mirrors the Skill implementation.</p>
     */
    private void writeAgentSpecToStorage(String namespaceId, AgentSpec agentSpec, String version,
        long uniformId)
        throws NacosException {
        saveAgentSpecFilesConcurrently(namespaceId, agentSpec, version, uniformId);
    }
    
    /**
     * Persist AgentSpec main config (manifest.json) and all resource files concurrently.
     *
     * <p>Each file (main + N resources) is submitted to {@link ExecutorUtils#getAgentSpecStorageIoExecutor()}
     * and waited on via {@link CompletableFuture#allOf}. Underlying {@link NacosException}s thrown from
     * individual save tasks are unwrapped from {@link CompletionException} and rethrown to keep the
     * original exception semantics aligned with the previous serial implementation.</p>
     */
    private void saveAgentSpecFilesConcurrently(String namespaceId, AgentSpec agentSpec,
        String version, long uniformId) throws NacosException {
        String provider = resolveStorageProvider();
        String agentSpecName = agentSpec.getName();
        Executor executor = ExecutorUtils.getAgentSpecStorageIoExecutor();
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        
        // 1) Main config file (manifest.json)
        byte[] mainContent = buildMainContent(agentSpec, uniformId);
        StorageKey mainKey = NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId,
            NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, agentSpecName, version,
            NacosConfigAiResourceStorage.getMainFilePath(AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID));
        tasks.add(CompletableFuture.runAsync(() -> {
            try {
                storageRouter.route(mainKey).save(mainKey, mainContent);
            } catch (NacosException e) {
                throw new CompletionException(e);
            }
        }, executor));
        
        // 2) Resource files (each carries a uniformId for consistency tracking)
        if (agentSpec.getResource() != null && !agentSpec.getResource().isEmpty()) {
            for (Map.Entry<String, AgentSpecResource> entry : agentSpec.getResource().entrySet()) {
                AgentSpecResource resource = entry.getValue();
                String path =
                    NacosConfigAiResourceStorage.getAgentSpecResourceFilePath(resource.getType(),
                        resource.getName());
                byte[] content = buildResourceContent(resource, uniformId);
                StorageKey resourceKey = NacosConfigAiResourceStorage.buildStorageKey(provider,
                    namespaceId, NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC,
                    agentSpecName, version, path);
                tasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        storageRouter.route(resourceKey).save(resourceKey, content);
                    } catch (NacosException e) {
                        throw new CompletionException(e);
                    }
                }, executor));
            }
        }
        
        try {
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof NacosException) {
                throw (NacosException) cause;
            }
            throw ex;
        }
    }
    
    /**
     * Load an AgentSpec from storage. Reads the main config JSON first to discover resource references,
     * then loads each resource file individually and assembles the full AgentSpec object.
     */
    private AgentSpec loadAgentSpecFromStorage(String namespaceId, String agentSpecName,
        String version)
        throws NacosException {
        // Step 1: Read main config file (manifest.json)
        StorageKey mainKey = NacosConfigAiResourceStorage.buildStorageKey(resolveStorageProvider(),
            namespaceId,
            NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, agentSpecName, version,
            NacosConfigAiResourceStorage.getMainFilePath(AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID));
        byte[] mainBytes = storageRouter.route(mainKey).get(mainKey);
        if (mainBytes == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec not found: " + agentSpecName);
        }
        
        // Step 2: Deserialize main config, extract name/description/content
        AgentSpecMainConfig mainConfig =
            JacksonUtils.toObj(new String(mainBytes, StandardCharsets.UTF_8),
                AgentSpecMainConfig.class);
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setNamespaceId(namespaceId);
        agentSpec.setName(mainConfig.getName());
        agentSpec.setDescription(mainConfig.getDescription());
        agentSpec.setContent(mainConfig.getContent());
        
        // Step 3: Load each resource file based on the resource reference list in main config
        Map<String, AgentSpecResource> resourceMap = new HashMap<>(
            mainConfig.getResources() != null ? mainConfig.getResources().size() : 16);
        if (mainConfig.getResources() != null && !mainConfig.getResources().isEmpty()) {
            for (AgentSpecResourceRef resourceRef : mainConfig.getResources()) {
                String resourceId =
                    AgentSpecUtils.generateResourceId(resourceRef.getType(), resourceRef.getName());
                String path =
                    NacosConfigAiResourceStorage.getAgentSpecResourceFilePath(resourceRef.getType(),
                        resourceRef.getName());
                StorageKey resourceKey =
                    NacosConfigAiResourceStorage.buildStorageKey(resolveStorageProvider(),
                        namespaceId, NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC,
                        agentSpecName, version,
                        path);
                byte[] resourceBytes = storageRouter.route(resourceKey).get(resourceKey);
                if (resourceBytes != null) {
                    AgentSpecResource resource = JacksonUtils.toObj(
                        new String(resourceBytes, StandardCharsets.UTF_8), AgentSpecResource.class);
                    resourceMap.put(resourceId, resource);
                }
            }
        }
        agentSpec.setResource(resourceMap);
        return agentSpec;
    }
    
    /**
     * Load agentspec metadata from storage without reading resource files. Only reads the main config (manifest.json)
     * and builds resource entries with name and type from the reference list.
     */
    private AgentSpec loadAgentSpecMetaFromStorage(String namespaceId, String agentSpecName,
        String version)
        throws NacosException {
        StorageKey mainKey = NacosConfigAiResourceStorage.buildStorageKey(resolveStorageProvider(),
            namespaceId,
            NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, agentSpecName, version,
            NacosConfigAiResourceStorage.getMainFilePath(AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID));
        byte[] mainBytes = storageRouter.route(mainKey).get(mainKey);
        if (mainBytes == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "AgentSpec not found: " + agentSpecName);
        }
        AgentSpecMainConfig mainConfig =
            JacksonUtils.toObj(new String(mainBytes, StandardCharsets.UTF_8),
                AgentSpecMainConfig.class);
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setNamespaceId(namespaceId);
        agentSpec.setName(mainConfig.getName());
        agentSpec.setDescription(mainConfig.getDescription());
        agentSpec.setContent(mainConfig.getContent());
        Map<String, AgentSpecResource> resourceMap = new HashMap<>(
            mainConfig.getResources() != null ? mainConfig.getResources().size() : 16);
        if (mainConfig.getResources() != null && !mainConfig.getResources().isEmpty()) {
            for (AgentSpecResourceRef resourceRef : mainConfig.getResources()) {
                String resourceId =
                    AgentSpecUtils.generateResourceId(resourceRef.getType(), resourceRef.getName());
                AgentSpecResource resource = new AgentSpecResource();
                resource.setName(resourceRef.getName());
                resource.setType(resourceRef.getType());
                resourceMap.put(resourceId, resource);
            }
        }
        agentSpec.setResource(resourceMap);
        return agentSpec;
    }
    
    /**
     * Build pipeline file representations from an AgentSpec for use by the publish pipeline executor.
     * Includes manifest.json (main content) and all resource files.
     */
    private static List<ResourceFileContent> buildPipelineFiles(AgentSpec agentSpec) {
        List<ResourceFileContent> files = new ArrayList<>();
        files.add(new ResourceFileContent("manifest.json",
            agentSpec.getContent() == null ? StringUtils.EMPTY : agentSpec.getContent()));
        if (agentSpec.getResource() != null && !agentSpec.getResource().isEmpty()) {
            for (AgentSpecResource resource : agentSpec.getResource().values()) {
                if (resource == null || StringUtils.isBlank(resource.getName())) {
                    continue;
                }
                files.add(new ResourceFileContent(buildResourcePath(resource),
                    resource.getContent() == null ? StringUtils.EMPTY : resource.getContent()));
            }
        }
        return files;
    }
    
    /**
     * Build a storage-relative file path for a resource: "{type}/{name}" or just "{name}" if type is blank.
     * Avoids path duplication when name already contains the type prefix.
     */
    private static String buildResourcePath(AgentSpecResource resource) {
        if (StringUtils.isBlank(resource.getType())) {
            return resource.getName();
        }
        String normalizedType = resource.getType().trim();
        String normalizedName = resource.getName().trim();
        if (normalizedName.startsWith(normalizedType + "/")) {
            return normalizedName;
        }
        return normalizedType + "/" + normalizedName;
    }
    
    /**
     * Delete all storage files for a given AgentSpec version.
     * Reads the main config first to discover resource references, deletes each resource file,
     * then deletes the main config file itself.
     */
    private void deleteAgentSpecStorageForVersion(String namespaceId, String agentSpecName,
        String version)
        throws NacosException {
        // Step 1: Read main config first to get the resource reference list
        StorageKey mainKey = NacosConfigAiResourceStorage.buildStorageKey(resolveStorageProvider(),
            namespaceId,
            NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, agentSpecName, version,
            NacosConfigAiResourceStorage.getMainFilePath(AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID));
        byte[] mainBytes = storageRouter.route(mainKey).get(mainKey);
        if (mainBytes != null) {
            // Step 2: Delete each resource file
            AgentSpecMainConfig mainConfig =
                JacksonUtils.toObj(new String(mainBytes, StandardCharsets.UTF_8),
                    AgentSpecMainConfig.class);
            if (mainConfig.getResources() != null && !mainConfig.getResources().isEmpty()) {
                for (AgentSpecResourceRef resourceRef : mainConfig.getResources()) {
                    String path = NacosConfigAiResourceStorage.getAgentSpecResourceFilePath(
                        resourceRef.getType(),
                        resourceRef.getName());
                    StorageKey resourceKey =
                        NacosConfigAiResourceStorage.buildStorageKey(resolveStorageProvider(),
                            namespaceId, NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC,
                            agentSpecName, version,
                            path);
                    storageRouter.route(resourceKey).delete(resourceKey);
                }
            }
        }
        // Step 3: Delete the main config file itself
        storageRouter.route(mainKey).delete(mainKey);
    }
    
    // ---- Inner classes ----
    
    /**
     * AgentSpec main config (from manifest.json storage wrapper).
     */
    private static class AgentSpecMainConfig {
        
        private String name;
        
        private String description;
        
        private String content;
        
        private Long uniformId;
        
        private List<AgentSpecResourceRef> resources;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
        }
        
        public Long getUniformId() {
            return uniformId;
        }
        
        public void setUniformId(Long uniformId) {
            this.uniformId = uniformId;
        }
        
        public List<AgentSpecResourceRef> getResources() {
            return resources;
        }
        
        public void setResources(List<AgentSpecResourceRef> resources) {
            this.resources = resources;
        }
    }
    
    /**
     * AgentSpec resource reference (in main config).
     */
    private static class AgentSpecResourceRef {
        
        private String name;
        
        private String type;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getType() {
            return type;
        }
        
        public void setType(String type) {
            this.type = type;
        }
    }
    
}
