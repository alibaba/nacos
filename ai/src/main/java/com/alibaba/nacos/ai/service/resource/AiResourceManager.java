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

package com.alibaba.nacos.ai.service.resource;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.model.PipelineCallback;
import com.alibaba.nacos.api.ai.model.pipeline.PipelineExecution;
import com.alibaba.nacos.api.ai.model.pipeline.PipelineExecutionResult;
import com.alibaba.nacos.api.ai.model.pipeline.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.trace.AiResourceTraceService;
import com.alibaba.nacos.ai.service.visibility.DefaultVisibilityAdvisorConverter;
import com.alibaba.nacos.ai.service.visibility.VisibilityAdvisorConverter;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFilesPipelineContext;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.model.BaseVisibilityPredicate;
import com.alibaba.nacos.plugin.visibility.model.VisibilityQueryContext;
import com.alibaba.nacos.plugin.visibility.spi.QueryAdvisor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Shared manager for common AI resource operations (Skill, AgentSpec, etc.).
 *
 * <p>Centralises duplicated CAS update, query, validation, version-resolution and
 * pipeline-callback logic that was previously copy-pasted across
 * {@code SkillOperationServiceImpl} and {@code AgentSpecOperationServiceImpl}.</p>
 *
 * @author nacos
 */
@Service
public class AiResourceManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AiResourceManager.class);
    
    private static final int LATEST_ONLINE_VERSION_PAGE_SIZE = 500;
    
    private final AiResourcePersistService aiResourcePersistService;
    
    private final AiResourceVersionPersistService aiResourceVersionPersistService;
    
    private final PipelineExecutionRepository pipelineExecutionRepository;
    
    private final VisibilityAdvisorConverter visibilityAdvisorConverter;
    
    public AiResourceManager(AiResourcePersistService aiResourcePersistService,
        AiResourceVersionPersistService aiResourceVersionPersistService,
        PipelineExecutionRepository pipelineExecutionRepository) {
        this.aiResourcePersistService = aiResourcePersistService;
        this.aiResourceVersionPersistService = aiResourceVersionPersistService;
        this.pipelineExecutionRepository = pipelineExecutionRepository;
        this.visibilityAdvisorConverter = new DefaultVisibilityAdvisorConverter();
    }
    
    // ---- 2.1 CAS update methods ----
    
    /**
     * Result of a CAS update loop.
     */
    enum CasResult {
        /** CAS succeeded. */
        SUCCESS,
        /** Meta row disappeared or lost its metaVersion during retry. */
        META_LOST,
        /** All retry attempts exhausted. */
        MAX_RETRIES
    }
    
    @FunctionalInterface
    private interface VersionInfoMutator {
        
        ResourceVersionInfo mutate(ResourceVersionInfo latestInfo) throws NacosException;
    }
    
    /**
     * Generic CAS retry loop.  On conflict the {@code onConflictRefresh} callback is invoked to
     * refresh non-target fields from the latest meta row; target fields (the ones being updated)
     * stay unchanged.
     *
     * @param namespaceId       namespace
     * @param name              resource name
     * @param type              resource type
     * @param initialExpected   initial expected metaVersion
     * @param newValue          the mutable value carrier whose fields are written on each attempt
     * @param onConflictRefresh (newValue, latestMeta) → refresh non-target fields
     * @return the outcome of the loop
     */
    CasResult doCasLoop(String namespaceId, String name, String type, long initialExpected,
        AiResource newValue,
        BiConsumer<AiResource, AiResource> onConflictRefresh) {
        long expected = initialExpected;
        for (int i = 0; i < AiResourceConstants.MAX_WORKING_VERSION_RETRY; i++) {
            if (aiResourcePersistService.updateMetaCas(namespaceId, name, type, expected,
                newValue)) {
                return CasResult.SUCCESS;
            }
            AiResource latest = aiResourcePersistService.find(namespaceId, name, type);
            if (latest == null || latest.getMetaVersion() == null) {
                return CasResult.META_LOST;
            }
            expected = latest.getMetaVersion();
            onConflictRefresh.accept(newValue, latest);
        }
        return CasResult.MAX_RETRIES;
    }
    
    /**
     * Translate a non-SUCCESS CasResult into the appropriate exception for strict callers.
     */
    private void handleStrictCasResult(CasResult result) throws NacosException {
        if (result == CasResult.META_LOST) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Meta cas failed");
        }
        if (result == CasResult.MAX_RETRIES) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "Meta update conflict, retry");
        }
    }
    
    /**
     * CAS-update the versionInfo field of a resource meta row.
     */
    public void updateVersionInfoCas(String namespaceId, AiResource meta, ResourceVersionInfo info)
        throws NacosException {
        if (meta == null || meta.getMetaVersion() == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Meta version missing");
        }
        AiResource newValue = buildVersionInfoUpdateValue(meta, info);
        CasResult result =
            doCasLoop(namespaceId, meta.getName(), meta.getType(), meta.getMetaVersion(), newValue,
                (nv, latest) -> {
                    nv.setStatus(latest.getStatus());
                    nv.setDesc(latest.getDesc());
                    nv.setBizTags(latest.getBizTags());
                    nv.setExt(latest.getExt());
                });
        handleStrictCasResult(result);
    }
    
    private void updateVersionInfoCas(String namespaceId, AiResource meta,
        VersionInfoMutator mutator) throws NacosException {
        updateVersionInfoCas(namespaceId, meta, null, mutator);
    }
    
    private void updateVersionInfoCas(String namespaceId, AiResource meta,
        ResourceVersionInfo initialInfo, VersionInfoMutator mutator) throws NacosException {
        updateVersionInfoCas(namespaceId, meta, initialInfo, mutator, null);
    }
    
    private void updateVersionInfoCas(String namespaceId, AiResource meta,
        ResourceVersionInfo initialInfo, VersionInfoMutator mutator,
        BiConsumer<AiResource, AiResource> valueCustomizer) throws NacosException {
        if (meta == null || meta.getMetaVersion() == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Meta version missing");
        }
        AiResource latestMeta = meta;
        long expected = meta.getMetaVersion();
        for (int i = 0; i < AiResourceConstants.MAX_WORKING_VERSION_RETRY; i++) {
            ResourceVersionInfo latestInfo =
                i == 0 && initialInfo != null ? initialInfo : requireVersionInfo(latestMeta);
            ResourceVersionInfo nextInfo = mutator.mutate(latestInfo);
            AiResource newValue = buildVersionInfoUpdateValue(latestMeta, nextInfo);
            if (valueCustomizer != null) {
                valueCustomizer.accept(newValue, latestMeta);
            }
            if (aiResourcePersistService.updateMetaCas(namespaceId, meta.getName(), meta.getType(),
                expected, newValue)) {
                return;
            }
            latestMeta = aiResourcePersistService.find(namespaceId, meta.getName(), meta.getType());
            if (latestMeta == null || latestMeta.getMetaVersion() == null) {
                throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                    "Meta cas failed");
            }
            expected = latestMeta.getMetaVersion();
        }
        throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
            "Meta update conflict, retry");
    }
    
    private AiResource buildVersionInfoUpdateValue(AiResource meta, ResourceVersionInfo info) {
        AiResource newValue = new AiResource();
        newValue.setStatus(meta.getStatus());
        newValue.setDesc(meta.getDesc());
        newValue.setBizTags(meta.getBizTags());
        newValue.setExt(meta.getExt());
        newValue.setVersionInfo(JacksonUtils.toJson(info));
        return newValue;
    }
    
    /**
     * CAS-update the bizTags field of a resource meta row.
     */
    public void updateBizTagsCas(String namespaceId, AiResource meta, String bizTags)
        throws NacosException {
        if (meta == null || meta.getMetaVersion() == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Meta version missing");
        }
        AiResource newValue = new AiResource();
        newValue.setStatus(meta.getStatus());
        newValue.setDesc(meta.getDesc());
        newValue.setBizTags(bizTags);
        newValue.setExt(meta.getExt());
        newValue.setVersionInfo(meta.getVersionInfo());
        CasResult result =
            doCasLoop(namespaceId, meta.getName(), meta.getType(), meta.getMetaVersion(), newValue,
                (nv, latest) -> {
                    nv.setStatus(latest.getStatus());
                    nv.setDesc(latest.getDesc());
                    nv.setExt(latest.getExt());
                    nv.setVersionInfo(latest.getVersionInfo());
                });
        handleStrictCasResult(result);
    }
    
    /**
     * CAS-update the meta status to enable or disable.
     */
    public void metaEnableDisable(String namespaceId, AiResource meta, boolean enable)
        throws NacosException {
        ResourceVersionInfo info = requireVersionInfo(meta);
        AiResource newValue = new AiResource();
        newValue.setStatus(enable ? AiResourceConstants.META_STATUS_ENABLE
            : AiResourceConstants.META_STATUS_DISABLE);
        newValue.setDesc(meta.getDesc());
        newValue.setBizTags(meta.getBizTags());
        newValue.setExt(meta.getExt());
        newValue.setVersionInfo(JacksonUtils.toJson(info));
        long expected = meta.getMetaVersion() == null ? 0 : meta.getMetaVersion();
        CasResult result =
            doCasLoop(namespaceId, meta.getName(), meta.getType(), expected, newValue,
                (nv, latest) -> {
                    nv.setDesc(latest.getDesc());
                    nv.setBizTags(latest.getBizTags());
                    nv.setExt(latest.getExt());
                });
        handleStrictCasResult(result);
        String operation =
            enable ? AiResourceTraceService.OP_ENABLE : AiResourceTraceService.OP_DISABLE;
        AiResourceTraceService.logSuccess(meta.getType(), meta.getName(), null, operation,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
    }
    
    /**
     * Best-effort CAS-update the description field of a resource meta row.
     */
    public void bumpMetaDescription(String namespaceId, AiResource meta, String description) {
        if (meta == null || meta.getMetaVersion() == null) {
            return;
        }
        AiResource newValue = new AiResource();
        newValue.setStatus(meta.getStatus());
        newValue.setDesc(description);
        newValue.setBizTags(meta.getBizTags());
        newValue.setExt(meta.getExt());
        newValue.setVersionInfo(meta.getVersionInfo());
        doCasLoop(namespaceId, meta.getName(), meta.getType(), meta.getMetaVersion(), newValue,
            (nv, latest) -> {
                nv.setStatus(latest.getStatus());
                nv.setBizTags(latest.getBizTags());
                nv.setExt(latest.getExt());
                nv.setVersionInfo(latest.getVersionInfo());
            });
    }
    
    /**
     * Best-effort CAS-update both description and bizTags for an imported resource meta.
     */
    public void syncImportedMeta(String namespaceId, AiResource meta, String description,
        String bizTags) {
        if (meta == null || meta.getMetaVersion() == null) {
            return;
        }
        String resolvedDescription =
            StringUtils.isBlank(description) ? meta.getDesc() : description;
        String resolvedBizTags = StringUtils.isBlank(bizTags) ? meta.getBizTags() : bizTags;
        AiResource newValue = new AiResource();
        newValue.setStatus(meta.getStatus());
        newValue.setDesc(resolvedDescription);
        newValue.setBizTags(resolvedBizTags);
        newValue.setExt(meta.getExt());
        newValue.setVersionInfo(meta.getVersionInfo());
        doCasLoop(namespaceId, meta.getName(), meta.getType(), meta.getMetaVersion(), newValue,
            (nv, latest) -> {
                nv.setStatus(latest.getStatus());
                nv.setExt(latest.getExt());
                nv.setVersionInfo(latest.getVersionInfo());
            });
    }
    
    /**
     * Best-effort CAS-update source field for an imported resource meta.
     */
    public void syncImportedSource(String namespaceId, AiResource meta, String source) {
        if (meta == null || meta.getMetaVersion() == null || StringUtils.isBlank(source)) {
            return;
        }
        long expected = meta.getMetaVersion();
        for (int i = 0; i < AiResourceConstants.MAX_WORKING_VERSION_RETRY; i++) {
            if (aiResourcePersistService.updateSourceCas(namespaceId, meta.getName(),
                meta.getType(), expected, source)) {
                return;
            }
            AiResource latest = aiResourcePersistService.find(namespaceId, meta.getName(),
                meta.getType());
            if (latest == null || latest.getMetaVersion() == null) {
                return;
            }
            expected = latest.getMetaVersion();
        }
    }
    
    // ---- 2.2 Query / validation helpers ----
    
    /**
     * Load meta row or throw NOT_FOUND.
     */
    public AiResource requireMeta(String namespaceId, String name, String type)
        throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, name, type);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                type + " not found: " + name);
        }
        return meta;
    }
    
    /**
     * Find meta row by namespace/name/type.
     */
    public AiResource findMeta(String namespaceId, String name, String type) {
        return aiResourcePersistService.find(namespaceId, name, type);
    }
    
    /**
     * Find version row by namespace/name/type/version.
     */
    public AiResourceVersion findVersion(String namespaceId, String name, String type,
        String version) {
        return aiResourceVersionPersistService.find(namespaceId, name, type, version);
    }
    
    /**
     * Update version row storage/description.
     */
    public void updateVersionStorageAndDesc(String namespaceId, String name, String type,
        String version,
        String storageJson, String description) {
        aiResourceVersionPersistService.updateStorageAndDesc(namespaceId, name, type, version,
            storageJson, description);
    }
    
    /**
     * Update version row storage.
     */
    public void updateVersionStorage(String namespaceId, String name, String type, String version,
        String storageJson) {
        aiResourceVersionPersistService.updateStorage(namespaceId, name, type, version,
            storageJson);
    }
    
    /**
     * Update version row status.
     */
    public void updateVersionStatus(String namespaceId, String name, String type, String version,
        String status) {
        aiResourceVersionPersistService.updateStatus(namespaceId, name, type, version, status);
    }
    
    /**
     * Update version row publish pipeline info.
     */
    public void updateVersionPublishPipelineInfo(String namespaceId, String name, String type,
        String version,
        String publishPipelineInfo) {
        aiResourceVersionPersistService.updatePublishPipelineInfo(namespaceId, name, type, version,
            publishPipelineInfo);
    }
    
    /**
     * Delete one version row.
     */
    public void deleteVersion(String namespaceId, String name, String type, String version) {
        aiResourceVersionPersistService.delete(namespaceId, name, type, version);
    }
    
    /**
     * Delete all version rows for a resource.
     */
    public void deleteVersionsByNameAndType(String namespaceId, String name, String type) {
        aiResourceVersionPersistService.deleteByNameAndType(namespaceId, name, type);
    }
    
    /**
     * Delete meta row by namespace/name/type.
     */
    public void deleteMeta(String namespaceId, String name, String type) {
        aiResourcePersistService.delete(namespaceId, name, type);
    }
    
    /**
     * Generate LIKE argument.
     */
    public String generateLikeArgument(String value) {
        return aiResourcePersistService.generateLikeArgument(value);
    }
    
    /**
     * List meta rows by basic prompt-style filtering.
     */
    public Page<AiResource> listMetaByType(String namespaceId, String type, String nameLike,
        String bizTagsLike,
        int pageNo, int pageSize) {
        return aiResourcePersistService.list(namespaceId, type, nameLike, bizTagsLike, pageNo,
            pageSize);
    }
    
    /**
     * List meta rows by query condition.
     */
    public Page<AiResource> listMeta(QueryCondition queryCondition, int pageNo, int pageSize) {
        return aiResourcePersistService.list(queryCondition, pageNo, pageSize);
    }
    
    /**
     * List version rows.
     */
    public Page<AiResourceVersion> listVersions(String namespaceId, String name, String type,
        String status,
        int pageNo, int pageSize) {
        return aiResourceVersionPersistService.list(namespaceId, name, type, status, pageNo,
            pageSize);
    }
    
    /**
     * Parse and guarantee a non-null {@link ResourceVersionInfo} from the meta row.
     */
    public static ResourceVersionInfo requireVersionInfo(AiResource meta) {
        ResourceVersionInfo info = parseVersionInfo(meta == null ? null : meta.getVersionInfo());
        if (info == null) {
            info = new ResourceVersionInfo();
            info.setLabels(new HashMap<>(4));
        } else if (info.getLabels() == null) {
            info.setLabels(new HashMap<>(4));
        }
        return info;
    }
    
    /**
     * Deserialise version info JSON; returns {@code null} on blank/invalid input.
     */
    public static ResourceVersionInfo parseVersionInfo(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JacksonUtils.toObj(json, ResourceVersionInfo.class);
        } catch (Exception ignored) {
            return null;
        }
    }
    
    /**
     * Deserialise publish pipeline info JSON; returns {@code null} on blank/invalid input.
     */
    public static PublishPipelineInfo parsePublishPipelineInfo(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            PublishPipelineInfo info = JacksonUtils.toObj(json, PublishPipelineInfo.class);
            if (info == null || StringUtils.isBlank(info.getExecutionId())) {
                return null;
            }
            return info;
        } catch (Exception ignored) {
            return null;
        }
    }
    
    /**
     * Throw NOT_FOUND if the current user cannot read the given resource.
     */
    public void ensureReadableOrNotFound(AiResource resource, String notFoundMessage)
        throws NacosException {
        if (VisibilityHelper.canReadResource(resource)) {
            return;
        }
        throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            notFoundMessage);
    }
    
    /**
     * Build a {@link QueryCondition} with visibility filtering applied.
     */
    public QueryCondition buildQueryCondition(String namespaceId, String resourceType,
        String nameLike,
        String bizTagsLike, String action) {
        String identity = VisibilityHelper.resolveCurrentIdentity();
        String apiType = VisibilityHelper.resolveCurrentApiType();
        QueryCondition queryCondition = new QueryCondition();
        queryCondition.setNamespaceId(namespaceId);
        queryCondition.setType(resourceType);
        queryCondition.setNameLike(nameLike);
        queryCondition.setBizTagsLike(bizTagsLike);
        VisibilityQueryContext context = new VisibilityQueryContext();
        context.setNamespaceId(namespaceId);
        context.setResourceType(resourceType);
        QueryAdvisor advisor = VisibilityHelper.findVisibilityService()
            .map(service -> service.adviseQuery(identity, action, apiType, context))
            .orElseGet(() -> {
                QueryAdvisor queryAdvisor = new QueryAdvisor();
                queryAdvisor.setBasePredicate(BaseVisibilityPredicate.ALL);
                return queryAdvisor;
            });
        return visibilityAdvisorConverter.convert(queryCondition, identity, advisor, context);
    }
    
    /**
     * Create an empty page result.
     */
    public static <T> Page<T> buildEmptyPage(int pageNo) {
        Page<T> page = new Page<>();
        page.setPageItems(new ArrayList<>());
        page.setTotalCount(0);
        page.setPagesAvailable(0);
        page.setPageNumber(pageNo);
        return page;
    }
    
    /**
     * Resolve scope from meta, defaulting to PRIVATE when blank.
     */
    public static String resolveScope(AiResource meta) {
        if (meta == null || StringUtils.isBlank(meta.getScope())) {
            return VisibilityConstants.SCOPE_PRIVATE;
        }
        return meta.getScope();
    }
    
    // ---- 2.3 Version resolution ----
    
    /**
     * Resolve which version string to use given explicit version, label, and meta state.
     */
    public static String resolveVersion(AiResource meta, String explicitVersion, String label) {
        if (StringUtils.isNotBlank(label)) {
            ResourceVersionInfo info = parseVersionInfo(meta.getVersionInfo());
            if (info != null && info.getLabels() != null) {
                String v = info.getLabels().get(label);
                if (StringUtils.isNotBlank(v)) {
                    return v;
                }
            }
        }
        if (StringUtils.isNotBlank(explicitVersion)) {
            return explicitVersion;
        }
        ResourceVersionInfo info = parseVersionInfo(meta.getVersionInfo());
        if (info != null && info.getLabels() != null) {
            String v = info.getLabels().get(AiResourceConstants.LABEL_LATEST);
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return null;
    }
    
    // ---- 2.4 Pipeline callback ----
    
    /**
     * List all existing version strings for a given resource (name + type).
     */
    public List<String> listExistingVersions(String namespaceId, String name, String type) {
        Page<AiResourceVersion> page =
            aiResourceVersionPersistService.list(namespaceId, name, type, null, 1, 500);
        if (page == null || page.getPageItems() == null || page.getPageItems().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> versions = new ArrayList<>(page.getPageItems().size());
        for (AiResourceVersion v : page.getPageItems()) {
            if (v != null && StringUtils.isNotBlank(v.getVersion())) {
                versions.add(v.getVersion().trim());
            }
        }
        return versions;
    }
    
    /**
     * Construct and insert a version row.
     */
    public void insertVersionRow(String namespaceId, String name, String type, String author,
        String status,
        String version, String description, String storageJson) {
        // Check if a version row already exists (e.g. orphaned row from a failed delete, or concurrent insert).
        // If so, fall back to updating the existing row instead of inserting a duplicate.
        AiResourceVersion existing =
            aiResourceVersionPersistService.find(namespaceId, name, type, version);
        if (existing != null) {
            updateExistingVersionRow(namespaceId, name, type, version, status, description,
                storageJson);
            return;
        }
        AiResourceVersion row = new AiResourceVersion();
        row.setNamespaceId(namespaceId);
        row.setName(name);
        row.setType(type);
        row.setAuthor(author);
        row.setStatus(status);
        row.setVersion(version);
        row.setDesc(description);
        row.setStorage(storageJson);
        try {
            aiResourceVersionPersistService.insert(row);
        } catch (DuplicateKeyException e) {
            // Race condition: version was inserted after our check, fallback to update
            LOGGER.warn("[insertVersionRow] duplicate key for {}/{}/{}/{}, falling back to update",
                namespaceId, name, type, version);
            updateExistingVersionRow(namespaceId, name, type, version, status, description,
                storageJson);
        }
    }
    
    private void updateExistingVersionRow(String namespaceId, String name, String type,
        String version,
        String status, String description, String storageJson) {
        aiResourceVersionPersistService.updateStorageAndDesc(namespaceId, name, type, version,
            storageJson, description);
        aiResourceVersionPersistService.updateStatus(namespaceId, name, type, version, status);
    }
    
    /**
     * Find a version row and verify it is in draft status.
     *
     * @throws NacosApiException if version not found or not in draft status
     */
    public AiResourceVersion requireDraftVersion(String namespaceId, String name, String type,
        String version)
        throws NacosException {
        AiResourceVersion v =
            aiResourceVersionPersistService.find(namespaceId, name, type, version);
        if (v == null
            || !AiResourceConstants.VERSION_STATUS_DRAFT.equalsIgnoreCase(v.getStatus())) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Current editing version is not draft: " + version);
        }
        return v;
    }
    
    /**
     * Set the editingVersion pointer for a newly created draft using mutator CAS.
     *
     * <p>CAS retries re-read the latest versionInfo and re-check that no working version appeared,
     * so concurrent lifecycle or label updates cannot be overwritten by a stale versionInfo.</p>
     */
    public void markEditingVersionCas(String namespaceId, AiResource meta,
        ResourceVersionInfo initialInfo, String version, String action) throws NacosException {
        updateVersionInfoCas(namespaceId, meta, initialInfo, latestInfo -> {
            ensureNoWorkingVersion(latestInfo, action);
            latestInfo.setEditingVersion(version);
            return latestInfo;
        });
    }
    
    /**
     * Publish a version directly (bypass pipeline). Sets version online, clears editing/reviewing pointers,
     * increments onlineCnt, and updates the server-managed latest label.
     *
     * @param updateLatestLabel retained for compatibility and ignored; latest is always updated
     */
    public void directPublishVersion(String namespaceId, AiResource meta, ResourceVersionInfo info,
        String version, boolean updateLatestLabel) throws NacosException {
        String name = meta.getName();
        String type = meta.getType();
        aiResourceVersionPersistService.updateStatus(namespaceId, name, type, version,
            AiResourceConstants.VERSION_STATUS_ONLINE);
        updateVersionInfoCas(namespaceId, meta, info, latestInfo -> {
            if (StringUtils.equals(latestInfo.getEditingVersion(), version)) {
                latestInfo.setEditingVersion(null);
            }
            if (StringUtils.equals(latestInfo.getReviewingVersion(), version)) {
                latestInfo.setReviewingVersion(null);
            }
            Integer cnt = latestInfo.getOnlineCnt();
            latestInfo.setOnlineCnt(cnt == null ? 1 : (cnt + 1));
            if (latestInfo.getLabels() == null) {
                latestInfo.setLabels(new HashMap<>(4));
            }
            latestInfo.getLabels().put(AiResourceConstants.LABEL_LATEST, version);
            return latestInfo;
        });
    }
    
    /**
     * Create both an online version row and a meta row for bootstrap (built-in) resources.
     */
    public void insertBootstrapMeta(String namespaceId, String name, String type,
        String description,
        String bizTags, String owner, String from, String version, String storageJson) {
        insertVersionRow(namespaceId, name, type, owner, AiResourceConstants.VERSION_STATUS_ONLINE,
            version, description, storageJson);
        
        ResourceVersionInfo versionInfo = new ResourceVersionInfo();
        versionInfo.setOnlineCnt(1);
        Map<String, String> labels = new HashMap<>(4);
        labels.put(AiResourceConstants.LABEL_LATEST, version);
        versionInfo.setLabels(labels);
        
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName(name);
        meta.setType(type);
        meta.setStatus(AiResourceConstants.META_STATUS_ENABLE);
        meta.setDesc(description);
        meta.setBizTags(bizTags);
        meta.setOwner(owner);
        meta.setFrom(from);
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        meta.setVersionInfo(JacksonUtils.toJson(versionInfo));
        meta.setMetaVersion(1L);
        aiResourcePersistService.insert(meta);
    }
    
    /**
     * Resolve the target version for a submit operation (explicit version or current editing).
     *
     * @throws NacosApiException if no target version can be determined
     */
    public String resolveSubmitTarget(ResourceVersionInfo info, String version, String type,
        String name)
        throws NacosException {
        String target = version;
        if (StringUtils.isBlank(target)) {
            target = info.getEditingVersion();
        }
        if (StringUtils.isBlank(target)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "No draft version to submit for " + type + ": " + name);
        }
        return target;
    }
    
    /**
     * Transition a version to reviewing status and update meta pointers accordingly.
     *
     * <p>Only versions in {@code draft} status are allowed to enter the review stage.
     * Submitting a version in any other status (reviewing / reviewed / online / offline)
     * is rejected with {@code INVALID_PARAM} to prevent corrupting formal versions.</p>
     */
    public void moveToReviewing(String namespaceId, String name, String type, String version,
        AiResource meta, ResourceVersionInfo info) throws NacosException {
        // Guard: only draft version can be submitted for review.
        requireDraftVersion(namespaceId, name, type, version);
        aiResourceVersionPersistService.updateStatus(namespaceId, name, type, version,
            AiResourceConstants.VERSION_STATUS_REVIEWING);
        updateVersionInfoCas(namespaceId, meta, info, latestInfo -> {
            ensureNoOtherWorkingVersion(latestInfo, version, "submit review");
            if (StringUtils.equals(latestInfo.getEditingVersion(), version)) {
                latestInfo.setEditingVersion(null);
            }
            latestInfo.setReviewingVersion(version);
            return latestInfo;
        });
        AiResourceTraceService.logSuccess(type, name, version,
            AiResourceTraceService.OP_SUBMIT_REVIEW,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
    }
    
    /**
     * Write an IN_PROGRESS pipeline info record for a version.
     */
    public void writePipelineInfoInProgress(String namespaceId, String name, String type,
        String version,
        String executionId) {
        PublishPipelineInfo pipelineInfo = new PublishPipelineInfo();
        pipelineInfo.setExecutionId(executionId);
        pipelineInfo.setStatus(PipelineExecutionStatus.IN_PROGRESS);
        pipelineInfo.setPipeline(new ArrayList<>());
        aiResourceVersionPersistService.updatePublishPipelineInfo(namespaceId, name, type, version,
            JacksonUtils.toJson(pipelineInfo));
    }
    
    /**
     * Clear pipeline info for a version (edge case when pipeline becomes unavailable).
     */
    public void clearPipelineInfo(String namespaceId, String name, String type, String version) {
        aiResourceVersionPersistService.updatePublishPipelineInfo(namespaceId, name, type, version,
            null);
    }
    
    // ---- 2.5 High-level domain-agnostic operations ----
    
    /**
     * Core publish logic: validate pipeline result, set version online, update meta pointers,
     * and update the server-managed latest label.
     *
     * @param updateLatestLabel retained for compatibility and ignored; latest is always updated
     * @return the version row (caller may need it for post-processing, e.g. manifest sync)
     */
    public AiResourceVersion doPublish(String namespaceId, String name, String type, String version,
        boolean updateLatestLabel) throws NacosException {
        return doPublish(namespaceId, name, type, version, true,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
    }
    
    private AiResourceVersion doPublish(String namespaceId, String name, String type,
        String version, boolean checkVisibility, String operator, String clientIp)
        throws NacosException {
        AiResource meta = requireMeta(namespaceId, name, type);
        if (checkVisibility) {
            VisibilityHelper.checkWritableResource(meta);
        }
        ResourceVersionInfo info = requireVersionInfo(meta);
        
        AiResourceVersion v =
            aiResourceVersionPersistService.find(namespaceId, name, type, version);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                type + " version not found: " + name + "@" + version);
        }
        if (!AiResourceConstants.VERSION_STATUS_REVIEWING.equalsIgnoreCase(v.getStatus())
            && !AiResourceConstants.VERSION_STATUS_REVIEWED.equalsIgnoreCase(v.getStatus())
            && !AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(v.getStatus())) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Only reviewing version can be published: " + version);
        }
        
        PublishPipelineInfo pipelineInfo = parsePublishPipelineInfo(v.getPublishPipelineInfo());
        if (pipelineInfo != null && StringUtils.isNotBlank(pipelineInfo.getExecutionId())) {
            PipelineExecution execution =
                pipelineExecutionRepository.findById(pipelineInfo.getExecutionId());
            if (execution == null) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Pipeline execution not found, cannot publish: " + version);
            }
            if (execution.getStatus() != PipelineExecutionStatus.APPROVED) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Pipeline not approved, cannot publish: " + version);
            }
        }
        
        boolean alreadyOnline =
            AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(v.getStatus());
        if (!alreadyOnline) {
            aiResourceVersionPersistService.updateStatus(namespaceId, name, type, version,
                AiResourceConstants.VERSION_STATUS_ONLINE);
        }
        updateVersionInfoCas(namespaceId, meta, info, latestInfo -> {
            if (StringUtils.equals(latestInfo.getReviewingVersion(), version)) {
                latestInfo.setReviewingVersion(null);
            }
            if (!alreadyOnline) {
                Integer cnt = latestInfo.getOnlineCnt();
                latestInfo.setOnlineCnt(cnt == null ? 1 : (cnt + 1));
            }
            if (latestInfo.getLabels() == null) {
                latestInfo.setLabels(new HashMap<>(4));
            }
            latestInfo.getLabels().put(AiResourceConstants.LABEL_LATEST, version);
            return latestInfo;
        });
        AiResourceTraceService.logSuccess(type, name, version, AiResourceTraceService.OP_PUBLISH,
            operator, clientIp);
        return v;
    }
    
    /**
     * Core publish logic for system-triggered pipeline callbacks.
     *
     * @param updateLatestLabel retained for compatibility and ignored; latest is always updated
     * @return the version row (caller may need it for post-processing, e.g. manifest sync)
     */
    public AiResourceVersion doSystemPublish(String namespaceId, String name, String type,
        String version,
        boolean updateLatestLabel) throws NacosException {
        return doPublish(namespaceId, name, type, version, false, "system", "");
    }
    
    /**
     * Core force-publish logic: bypass pipeline validation, set version online, update meta pointers,
     * and update the server-managed latest label.
     *
     * @param updateLatestLabel retained for compatibility and ignored; latest is always updated
     * @return the version row (caller may need it for post-processing, e.g. manifest sync)
     */
    public AiResourceVersion doForcePublish(String namespaceId, String name, String type,
        String version,
        boolean updateLatestLabel) throws NacosException {
        AiResource meta = requireMeta(namespaceId, name, type);
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = requireVersionInfo(meta);
        
        AiResourceVersion v =
            aiResourceVersionPersistService.find(namespaceId, name, type, version);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                type + " version not found: " + name + "@" + version);
        }
        if (AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(v.getStatus())
            || AiResourceConstants.VERSION_STATUS_OFFLINE.equalsIgnoreCase(v.getStatus())) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Force-publish is not allowed for online or offline version: " + version);
        }
        
        LOGGER.warn("[FORCE-PUBLISH] Bypassing pipeline validation for {} {}@{} by user {}",
            type, name, version, VisibilityHelper.resolveCurrentIdentity());
        
        aiResourceVersionPersistService.updateStatus(namespaceId, name, type, version,
            AiResourceConstants.VERSION_STATUS_ONLINE);
        updateVersionInfoCas(namespaceId, meta, info, latestInfo -> {
            if (StringUtils.equals(latestInfo.getEditingVersion(), version)) {
                latestInfo.setEditingVersion(null);
            }
            if (StringUtils.equals(latestInfo.getReviewingVersion(), version)) {
                latestInfo.setReviewingVersion(null);
            }
            Integer cnt = latestInfo.getOnlineCnt();
            latestInfo.setOnlineCnt(cnt == null ? 1 : (cnt + 1));
            if (latestInfo.getLabels() == null) {
                latestInfo.setLabels(new HashMap<>(4));
            }
            latestInfo.getLabels().put(AiResourceConstants.LABEL_LATEST, version);
            return latestInfo;
        });
        AiResourceTraceService.logSuccess(type, name, version,
            AiResourceTraceService.OP_FORCE_PUBLISH,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
        return v;
    }
    
    /**
     * Validate that labels don't reference draft/reviewing versions, then CAS-update labels.
     *
     * @return effective labels after preserving service-managed labels such as {@code latest}
     */
    public Map<String, String> validateAndUpdateLabels(String namespaceId, String name, String type,
        Map<String, String> labels) throws NacosException {
        AiResource meta = requireMeta(namespaceId, name, type);
        VisibilityHelper.checkWritableResource(meta);
        Map<String, String> requestedLabels =
            labels == null ? new LinkedHashMap<>(4) : new LinkedHashMap<>(labels);
        removeReservedLatestLabel(requestedLabels);
        AtomicReference<Map<String, String>> effectiveLabels = new AtomicReference<>();
        updateVersionInfoCas(namespaceId, meta, latestInfo -> {
            Map<String, String> newLabels = new LinkedHashMap<>(requestedLabels);
            mergeReservedLatestLabel(newLabels, latestInfo);
            validateLabelsDoNotPointToWorkingVersion(newLabels, latestInfo);
            latestInfo.setLabels(newLabels);
            effectiveLabels.set(newLabels);
            return latestInfo;
        });
        AiResourceTraceService.logSuccess(type, name, null, AiResourceTraceService.OP_UPDATE_LABELS,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
        return effectiveLabels.get();
    }
    
    private static void mergeReservedLatestLabel(Map<String, String> labels,
        ResourceVersionInfo info) {
        if (info.getLabels() != null
            && info.getLabels().containsKey(AiResourceConstants.LABEL_LATEST)) {
            labels.put(AiResourceConstants.LABEL_LATEST,
                info.getLabels().get(AiResourceConstants.LABEL_LATEST));
        }
    }
    
    private static void validateLabelsDoNotPointToWorkingVersion(Map<String, String> labels,
        ResourceVersionInfo info) throws NacosException {
        if (labels.isEmpty()) {
            return;
        }
        String editing = info.getEditingVersion();
        String reviewing = info.getReviewingVersion();
        for (Map.Entry<String, String> entry : labels.entrySet()) {
            String targetVersion = entry.getValue();
            if (StringUtils.isNotBlank(editing) && editing.equals(targetVersion)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Label '" + entry.getKey() + "' cannot point to draft version: "
                        + targetVersion);
            }
            if (StringUtils.isNotBlank(reviewing) && reviewing.equals(targetVersion)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Label '" + entry.getKey() + "' cannot point to reviewing version: "
                        + targetVersion);
            }
        }
    }
    
    /**
     * Remove client-provided {@code latest} label so service-managed value wins.
     */
    private static void removeReservedLatestLabel(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return;
        }
        labels.keySet().removeIf(label -> AiResourceConstants.LABEL_LATEST.equalsIgnoreCase(label));
    }
    
    /**
     * Update the scope of a resource (requireMeta + checkWritable + persist).
     */
    public void doUpdateScope(String namespaceId, String name, String type, String scope)
        throws NacosException {
        AiResource meta = requireMeta(namespaceId, name, type);
        VisibilityHelper.checkWritableResource(meta);
        boolean ok =
            aiResourcePersistService.updateScope(namespaceId, name, type, scope.toUpperCase());
        if (!ok) {
            LOGGER.error("Failed to update scope for {} {}, namespace: {}, scope: {}", type, name,
                namespaceId, scope);
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Failed to update scope for " + type + ": " + name);
        }
        AiResourceTraceService.logSuccess(type, name, null, AiResourceTraceService.OP_UPDATE_SCOPE,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp(),
            "scope=" + scope);
    }
    
    /**
     * Toggle a single version's online/offline status, adjust meta onlineCnt and maintain
     * service-managed labels.
     *
     * @return the version row if a status change occurred, or {@code null} if already in the target status
     */
    public AiResourceVersion toggleVersionOnlineStatus(String namespaceId, AiResource meta,
        ResourceVersionInfo info, String version, boolean online) throws NacosException {
        String name = meta.getName();
        String type = meta.getType();
        AiResourceVersion v =
            aiResourceVersionPersistService.find(namespaceId, name, type, version);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                type + " version not found: " + name + "@" + version);
        }
        String targetStatus = online ? AiResourceConstants.VERSION_STATUS_ONLINE
            : AiResourceConstants.VERSION_STATUS_OFFLINE;
        if (targetStatus.equalsIgnoreCase(v.getStatus())) {
            return null;
        }
        aiResourceVersionPersistService.updateStatus(namespaceId, name, type, version,
            targetStatus);
        updateVersionInfoCas(namespaceId, meta, info, latestInfo -> {
            Integer cnt = latestInfo.getOnlineCnt() == null ? 0 : latestInfo.getOnlineCnt();
            latestInfo.setOnlineCnt(online ? cnt + 1 : Math.max(0, cnt - 1));
            refreshLatestLabelForOnlineVersions(namespaceId, name, type, latestInfo);
            return latestInfo;
        });
        String operation = online ? AiResourceTraceService.OP_ONLINE_VERSION
            : AiResourceTraceService.OP_OFFLINE_VERSION;
        AiResourceTraceService.logSuccess(type, name, version, operation,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
        return v;
    }
    
    private void refreshLatestLabelForOnlineVersions(String namespaceId, String name, String type,
        ResourceVersionInfo info) {
        if (info.getLabels() == null) {
            info.setLabels(new LinkedHashMap<>(4));
        }
        String nextLatest = resolveLatestOnlineVersion(namespaceId, name, type);
        if (StringUtils.isBlank(nextLatest)) {
            info.getLabels().remove(AiResourceConstants.LABEL_LATEST);
        } else {
            info.getLabels().put(AiResourceConstants.LABEL_LATEST, nextLatest);
        }
    }
    
    private String resolveLatestOnlineVersion(String namespaceId, String name, String type) {
        List<String> versions = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            Page<AiResourceVersion> page =
                aiResourceVersionPersistService.list(namespaceId, name, type,
                    AiResourceConstants.VERSION_STATUS_ONLINE, pageNo,
                    LATEST_ONLINE_VERSION_PAGE_SIZE);
            if (page == null || page.getPageItems() == null || page.getPageItems().isEmpty()) {
                break;
            }
            for (AiResourceVersion v : page.getPageItems()) {
                if (v != null && StringUtils.isNotBlank(v.getVersion())) {
                    versions.add(v.getVersion().trim());
                }
            }
            if (page.getPageItems().size() < LATEST_ONLINE_VERSION_PAGE_SIZE) {
                break;
            }
            pageNo++;
        }
        if (versions.isEmpty()) {
            return null;
        }
        String maxSemver = VersionUtils.maxSemver(versions);
        if (StringUtils.isNotBlank(maxSemver)) {
            return maxSemver;
        }
        String maxVNumber = VersionUtils.maxVNumberVersion(versions);
        if (StringUtils.isNotBlank(maxVNumber)) {
            return maxVNumber;
        }
        String latest = null;
        for (String item : versions) {
            if (latest == null || item.compareTo(latest) > 0) {
                latest = item;
            }
        }
        return latest;
    }
    
    /**
     * Create a new meta row (when {@code isNew}) or CAS-update the editing pointer on an existing one.
     */
    public void initOrUpdateMetaForDraft(String namespaceId, String name, String type,
        String description,
        String bizTags, String version, AiResource existedMeta, boolean isNew)
        throws NacosException {
        if (isNew) {
            String currentUser = VisibilityHelper.resolveCurrentIdentity();
            String defaultScope = VisibilityHelper.resolveDefaultScopeForCreate(type);
            AiResource meta = new AiResource();
            meta.setNamespaceId(namespaceId);
            meta.setName(name);
            meta.setType(type);
            meta.setStatus(AiResourceConstants.META_STATUS_ENABLE);
            meta.setDesc(description);
            meta.setBizTags(bizTags);
            meta.setOwner(currentUser);
            meta.setScope(defaultScope);
            ResourceVersionInfo info = new ResourceVersionInfo();
            info.setEditingVersion(version);
            info.setOnlineCnt(0);
            info.setLabels(new HashMap<>(4));
            meta.setVersionInfo(JacksonUtils.toJson(info));
            meta.setMetaVersion(1L);
            aiResourcePersistService.insert(meta);
        } else if (existedMeta != null) {
            if (existedMeta.getMetaVersion() == null) {
                throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                    "Meta version missing");
            }
            ResourceVersionInfo info = requireVersionInfo(existedMeta);
            boolean syncDescription = StringUtils.isNotBlank(description);
            updateVersionInfoCas(namespaceId, existedMeta, info, latestInfo -> {
                ensureNoEditingVersion(latestInfo, "create draft");
                latestInfo.setEditingVersion(version);
                return latestInfo;
            }, (newValue, latestMeta) -> {
                if (syncDescription) {
                    newValue.setDesc(description);
                }
            });
        }
    }
    
    /**
     * Resolve the base version to copy from when creating a draft.
     *
     * <p>Priority: explicit basedOnVersion → "latest" label → highest semver → highest vN.
     * Returns {@code null} if no version exists yet.</p>
     *
     * @throws NacosApiException if an explicit basedOnVersion was given but cannot be resolved
     */
    public String resolveBaseVersion(String namespaceId, String name, String type, AiResource meta,
        String basedOnVersion) throws NacosException {
        if (StringUtils.isNotBlank(basedOnVersion)) {
            String resolved = resolveVersion(meta, basedOnVersion, null);
            if (StringUtils.isBlank(resolved)) {
                throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Base version not found for " + type + ": " + name + ", basedOnVersion: "
                        + basedOnVersion);
            }
            return resolved;
        }
        String latest = resolveVersion(meta, null, AiResourceConstants.LABEL_LATEST);
        if (StringUtils.isNotBlank(latest)) {
            return latest;
        }
        List<String> existingVersions = listExistingVersions(namespaceId, name, type);
        String maxSemver = VersionUtils.maxSemver(existingVersions);
        return StringUtils.isNotBlank(maxSemver) ? maxSemver
            : VersionUtils.maxVNumberVersion(existingVersions);
    }
    
    /**
     * Ensure no editing or reviewing version exists; throw CONFLICT otherwise.
     *
     * @param info   the parsed version info
     * @param action action description for error message (e.g. "upload", "create draft")
     */
    public static void ensureNoWorkingVersion(ResourceVersionInfo info, String action)
        throws NacosException {
        if (StringUtils.isNotBlank(info.getEditingVersion())
            || StringUtils.isNotBlank(info.getReviewingVersion())) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "There is already a working version (editing/reviewing), cannot " + action);
        }
    }
    
    private static void ensureNoEditingVersion(ResourceVersionInfo info, String action)
        throws NacosException {
        if (StringUtils.isNotBlank(info.getEditingVersion())) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "There is already an editing version, cannot " + action);
        }
    }
    
    private static void ensureNoOtherWorkingVersion(ResourceVersionInfo info, String version,
        String action)
        throws NacosException {
        if ((StringUtils.isNotBlank(info.getEditingVersion())
            && !StringUtils.equals(info.getEditingVersion(), version))
            || (StringUtils.isNotBlank(info.getReviewingVersion())
                && !StringUtils.equals(info.getReviewingVersion(), version))) {
            throwWorkingVersionConflict(action, version);
        }
    }
    
    private static void ensureWorkingVersionMatches(String actual, String expected, String action)
        throws NacosException {
        if (!StringUtils.equals(actual, expected)) {
            throwWorkingVersionConflict(action, expected);
        }
    }
    
    private static void throwWorkingVersionConflict(String action, String version)
        throws NacosException {
        throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
            "Working version changed, cannot " + action + ": " + version);
    }
    
    /**
     * Build a page result from items and the source meta page.
     */
    public static <T> Page<T> buildPageResult(List<T> items, Page<?> sourcePage, int pageNo) {
        Page<T> result = new Page<>();
        result.setPageItems(items);
        result.setTotalCount(sourcePage == null ? 0 : sourcePage.getTotalCount());
        result.setPagesAvailable(sourcePage == null ? 0 : sourcePage.getPagesAvailable());
        result.setPageNumber(pageNo);
        return result;
    }
    
    /**
     * Functional interface for deleting storage associated with a specific version.
     */
    @FunctionalInterface
    public interface VersionStorageDeleter {
        
        void deleteStorage(AiResourceVersion version) throws NacosException;
    }
    
    /**
     * Delete meta and all version rows for a resource, invoking the given deleter for each version's storage.
     */
    public void deleteResourceWithVersions(String namespaceId, String name, String type,
        VersionStorageDeleter storageDeleter) throws NacosException {
        aiResourcePersistService.delete(namespaceId, name, type);
        Page<AiResourceVersion> versions =
            aiResourceVersionPersistService.list(namespaceId, name, type, null, 1, 200);
        aiResourceVersionPersistService.deleteByNameAndType(namespaceId, name, type);
        if (versions != null && versions.getPageItems() != null) {
            for (AiResourceVersion v : versions.getPageItems()) {
                if (v == null || StringUtils.isBlank(v.getVersion())) {
                    continue;
                }
                storageDeleter.deleteStorage(v);
            }
        }
        AiResourceTraceService.logSuccess(type, name, null,
            AiResourceTraceService.OP_DELETE_RESOURCE,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
    }
    
    /**
     * Execute the publish pipeline for a resource version. Returns {@code true} if the pipeline is processing
     * asynchronously. Returns {@code false} if the pipeline fell through synchronously (caller should do direct
     * publish).
     */
    public boolean runPipelineExecution(String namespaceId, String name, String type,
        String version,
        ResourceFilesPipelineContext ctx, PublishPipelineExecutor executor) {
        return runPipelineExecution(namespaceId, name, type, version, ctx, executor,
            r -> onPipelineComplete(namespaceId, name, type, version, r));
    }
    
    /**
     * Execute the publish pipeline for a resource version with a caller-provided completion callback.
     *
     * <p>The initial IN_PROGRESS record is still written here so the version can expose a stable executionId
     * before async execution starts.</p>
     */
    public boolean runPipelineExecution(String namespaceId, String name, String type,
        String version,
        ResourceFilesPipelineContext ctx, PublishPipelineExecutor executor,
        PipelineCallback callback) {
        String executionId = UUID.randomUUID().toString();
        writePipelineInfoInProgress(namespaceId, name, type, version, executionId);
        String result = executor.execute(ctx, callback, executionId);
        if (StringUtils.isBlank(result)) {
            clearPipelineInfo(namespaceId, name, type, version);
            return false;
        }
        return true;
    }
    
    /**
     * Transition a reviewed version back to draft for re-editing.
     *
     * <p>Only versions in {@code reviewed} status can be re-edited. The version number and content
     * are preserved; only the status changes to {@code draft} and meta pointers are updated.</p>
     *
     * @param namespaceId namespace
     * @param name        resource name
     * @param type        resource type (skill, prompt, agentspec)
     * @param version     version to re-edit
     */
    public void doRedraft(String namespaceId, String name, String type, String version)
        throws NacosException {
        AiResource meta = requireMeta(namespaceId, name, type);
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = requireVersionInfo(meta);
        
        AiResourceVersion v =
            aiResourceVersionPersistService.find(namespaceId, name, type, version);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                type + " version not found: " + name + "@" + version);
        }
        if (!AiResourceConstants.VERSION_STATUS_REVIEWED.equalsIgnoreCase(v.getStatus())) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Only reviewed version can be re-edited: " + version);
        }
        
        aiResourceVersionPersistService.updateStatus(namespaceId, name, type, version,
            AiResourceConstants.VERSION_STATUS_DRAFT);
        
        // Mark pipeline info as historical so redraft history is not treated as the current review.
        if (StringUtils.isNotBlank(v.getPublishPipelineInfo())) {
            try {
                PublishPipelineInfo pipelineInfo =
                    JacksonUtils.toObj(v.getPublishPipelineInfo(), PublishPipelineInfo.class);
                pipelineInfo.setHistorical(true);
                aiResourceVersionPersistService.updatePublishPipelineInfo(namespaceId, name, type,
                    version, JacksonUtils.toJson(pipelineInfo));
            } catch (Exception ex) {
                LOGGER.warn("Failed to mark pipeline info as historical for {}@{}", name, version,
                    ex);
            }
        }
        
        if (StringUtils.equals(info.getReviewingVersion(), version)) {
            updateVersionInfoCas(namespaceId, meta, info, latestInfo -> {
                ensureWorkingVersionMatches(latestInfo.getReviewingVersion(), version,
                    "redraft");
                if (StringUtils.isNotBlank(latestInfo.getEditingVersion())
                    && !StringUtils.equals(latestInfo.getEditingVersion(), version)) {
                    throwWorkingVersionConflict("redraft", version);
                }
                latestInfo.setReviewingVersion(null);
                latestInfo.setEditingVersion(version);
                return latestInfo;
            });
        }
        
        AiResourceTraceService.logSuccess(type, name, version,
            AiResourceTraceService.OP_REDRAFT,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
    }
    
    /**
     * Unified delete-draft logic for all resource types.
     *
     * <p>Resolves the target version from editingVersion (primary) or reviewingVersion (fallback
     * for reviewed/draft status), clears the corresponding meta pointer, deletes the version row,
     * and invokes the storage deleter callback.</p>
     *
     * @param namespaceId    namespace
     * @param name           resource name
     * @param type           resource type
     * @param storageDeleter callback to delete resource-specific storage files
     */
    public void doDeleteDraft(String namespaceId, String name, String type,
        VersionStorageDeleter storageDeleter) throws NacosException {
        AiResource meta = requireMeta(namespaceId, name, type);
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        
        if (StringUtils.isBlank(editing)) {
            // Fallback: try reviewingVersion (reviewed/draft status can be deleted)
            String reviewing = info.getReviewingVersion();
            if (StringUtils.isBlank(reviewing)) {
                return;
            }
            AiResourceVersion rv =
                aiResourceVersionPersistService.find(namespaceId, name, type, reviewing);
            if (rv == null || (!AiResourceConstants.VERSION_STATUS_REVIEWED
                .equalsIgnoreCase(rv.getStatus())
                && !AiResourceConstants.VERSION_STATUS_DRAFT
                    .equalsIgnoreCase(rv.getStatus()))) {
                return;
            }
            updateVersionInfoCas(namespaceId, meta, info, latestInfo -> {
                if (StringUtils.isNotBlank(latestInfo.getEditingVersion())) {
                    throwWorkingVersionConflict("delete draft", reviewing);
                }
                ensureWorkingVersionMatches(latestInfo.getReviewingVersion(), reviewing,
                    "delete draft");
                latestInfo.setReviewingVersion(null);
                return latestInfo;
            });
            storageDeleter.deleteStorage(rv);
            deleteVersion(namespaceId, name, type, reviewing);
            AiResourceTraceService.logSuccess(type, name, reviewing,
                AiResourceTraceService.OP_DELETE_DRAFT,
                VisibilityHelper.resolveCurrentIdentity(),
                VisibilityHelper.resolveClientIp());
            return;
        }
        
        AiResourceVersion v =
            aiResourceVersionPersistService.find(namespaceId, name, type, editing);
        
        // Clear meta pointer first
        updateVersionInfoCas(namespaceId, meta, info, latestInfo -> {
            ensureWorkingVersionMatches(latestInfo.getEditingVersion(), editing,
                "delete draft");
            latestInfo.setEditingVersion(null);
            return latestInfo;
        });
        
        // Delete version row and storage only if status is draft
        if (v != null && AiResourceConstants.VERSION_STATUS_DRAFT
            .equalsIgnoreCase(v.getStatus())) {
            storageDeleter.deleteStorage(v);
            deleteVersion(namespaceId, name, type, editing);
        }
        AiResourceTraceService.logSuccess(type, name, editing,
            AiResourceTraceService.OP_DELETE_DRAFT,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
    }
    
    /**
     * Handle pipeline completion: persist pipeline info and transition version status.
     *
     * <p>Both approved and rejected results transition to {@code reviewed}. Users must explicitly
     * call redraft to return to draft.</p>
     */
    public void onPipelineComplete(String namespaceId, String name, String type, String version,
        PipelineExecutionResult result) {
        try {
            PublishPipelineInfo info = new PublishPipelineInfo();
            info.setExecutionId(result == null ? null : result.getExecutionId());
            info.setStatus(result == null ? PipelineExecutionStatus.REJECTED : result.getStatus());
            info.setPipeline(result == null ? null : result.getPipeline());
            aiResourceVersionPersistService.updatePublishPipelineInfo(namespaceId, name, type,
                version,
                JacksonUtils.toJson(info));
            
            boolean approved =
                result != null && result.getStatus() == PipelineExecutionStatus.APPROVED;
            
            aiResourceVersionPersistService.updateStatus(namespaceId, name, type, version,
                AiResourceConstants.VERSION_STATUS_REVIEWED);
            AiResourceTraceService.logSuccess(type, name, version,
                approved ? AiResourceTraceService.OP_REVIEW_APPROVED
                    : AiResourceTraceService.OP_REVIEW_REJECTED,
                "system", "", result == null ? null : result.getExecutionId());
        } catch (Throwable ex) {
            LOGGER.error("Pipeline callback failed for {}@{}", name, version, ex);
        }
    }
}
