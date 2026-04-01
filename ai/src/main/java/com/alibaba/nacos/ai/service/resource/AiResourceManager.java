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
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionResult;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.visibility.DefaultVisibilityAdvisorConverter;
import com.alibaba.nacos.ai.service.visibility.VisibilityAdvisorConverter;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.model.BaseVisibilityPredicate;
import com.alibaba.nacos.plugin.visibility.model.VisibilityQueryContext;
import com.alibaba.nacos.plugin.visibility.spi.QueryAdvisor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
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
    
    private final AiResourcePersistService aiResourcePersistService;
    
    private final AiResourceVersionPersistService aiResourceVersionPersistService;
    
    private final VisibilityAdvisorConverter visibilityAdvisorConverter;
    
    public AiResourceManager(AiResourcePersistService aiResourcePersistService,
            AiResourceVersionPersistService aiResourceVersionPersistService) {
        this.aiResourcePersistService = aiResourcePersistService;
        this.aiResourceVersionPersistService = aiResourceVersionPersistService;
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
    CasResult doCasLoop(String namespaceId, String name, String type, long initialExpected, AiResource newValue,
            BiConsumer<AiResource, AiResource> onConflictRefresh) {
        long expected = initialExpected;
        for (int i = 0; i < AiResourceConstants.MAX_WORKING_VERSION_RETRY; i++) {
            if (aiResourcePersistService.updateMetaCas(namespaceId, name, type, expected, newValue)) {
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
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, "Meta cas failed");
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
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, "Meta version missing");
        }
        AiResource newValue = new AiResource();
        newValue.setStatus(meta.getStatus());
        newValue.setDesc(meta.getDesc());
        newValue.setBizTags(meta.getBizTags());
        newValue.setExt(meta.getExt());
        newValue.setVersionInfo(JacksonUtils.toJson(info));
        CasResult result = doCasLoop(namespaceId, meta.getName(), meta.getType(), meta.getMetaVersion(), newValue,
                (nv, latest) -> {
                    nv.setStatus(latest.getStatus());
                    nv.setDesc(latest.getDesc());
                    nv.setBizTags(latest.getBizTags());
                    nv.setExt(latest.getExt());
                });
        handleStrictCasResult(result);
    }
    
    /**
     * CAS-update the bizTags field of a resource meta row.
     */
    public void updateBizTagsCas(String namespaceId, AiResource meta, String bizTags) throws NacosException {
        if (meta == null || meta.getMetaVersion() == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, "Meta version missing");
        }
        AiResource newValue = new AiResource();
        newValue.setStatus(meta.getStatus());
        newValue.setDesc(meta.getDesc());
        newValue.setBizTags(bizTags);
        newValue.setExt(meta.getExt());
        newValue.setVersionInfo(meta.getVersionInfo());
        CasResult result = doCasLoop(namespaceId, meta.getName(), meta.getType(), meta.getMetaVersion(), newValue,
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
    public void metaEnableDisable(String namespaceId, AiResource meta, boolean enable) throws NacosException {
        ResourceVersionInfo info = requireVersionInfo(meta);
        AiResource newValue = new AiResource();
        newValue.setStatus(enable ? AiResourceConstants.META_STATUS_ENABLE : AiResourceConstants.META_STATUS_DISABLE);
        newValue.setDesc(meta.getDesc());
        newValue.setBizTags(meta.getBizTags());
        newValue.setExt(meta.getExt());
        newValue.setVersionInfo(JacksonUtils.toJson(info));
        long expected = meta.getMetaVersion() == null ? 0 : meta.getMetaVersion();
        CasResult result = doCasLoop(namespaceId, meta.getName(), meta.getType(), expected, newValue,
                (nv, latest) -> {
                    nv.setDesc(latest.getDesc());
                    nv.setBizTags(latest.getBizTags());
                    nv.setExt(latest.getExt());
                });
        handleStrictCasResult(result);
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
    public void syncImportedMeta(String namespaceId, AiResource meta, String description, String bizTags) {
        if (meta == null || meta.getMetaVersion() == null) {
            return;
        }
        String resolvedDescription = StringUtils.isBlank(description) ? meta.getDesc() : description;
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
    
    // ---- 2.2 Query / validation helpers ----
    
    /**
     * Load meta row or throw NOT_FOUND.
     */
    public AiResource requireMeta(String namespaceId, String name, String type) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, name, type);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    type + " not found: " + name);
        }
        return meta;
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
    public void ensureReadableOrNotFound(AiResource resource, String notFoundMessage) throws NacosException {
        if (VisibilityHelper.canReadResource(resource)) {
            return;
        }
        throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, notFoundMessage);
    }
    
    /**
     * Build a {@link QueryCondition} with visibility filtering applied.
     */
    public QueryCondition buildQueryCondition(String namespaceId, String resourceType, String nameLike,
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
                .map(service -> service.adviseQuery(identity, action, apiType, context)).orElseGet(() -> {
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
     * Handle pipeline completion: persist pipeline info and rollback to draft on rejection.
     */
    public void onPipelineComplete(String namespaceId, String name, String type, String version,
            PipelineExecutionResult result) {
        try {
            PublishPipelineInfo info = new PublishPipelineInfo();
            info.setExecutionId(result == null ? null : result.getExecutionId());
            info.setStatus(result == null ? PipelineExecutionStatus.REJECTED : result.getStatus());
            info.setPipeline(result == null ? null : result.getPipeline());
            aiResourceVersionPersistService.updatePublishPipelineInfo(namespaceId, name, type, version,
                    JacksonUtils.toJson(info));
            
            if (result == null || result.getStatus() != PipelineExecutionStatus.APPROVED) {
                // Reject back to draft and move reviewing -> editing (best effort).
                aiResourceVersionPersistService.updateStatus(namespaceId, name, type, version,
                        AiResourceConstants.VERSION_STATUS_DRAFT);
                AiResource meta = aiResourcePersistService.find(namespaceId, name, type);
                if (meta != null) {
                    ResourceVersionInfo vInfo = requireVersionInfo(meta);
                    if (StringUtils.equals(vInfo.getReviewingVersion(), version)) {
                        vInfo.setReviewingVersion(null);
                        vInfo.setEditingVersion(version);
                        try {
                            updateVersionInfoCas(namespaceId, meta, vInfo);
                        } catch (Exception ex) {
                            LOGGER.warn("Failed to rollback meta working pointers for {}@{}", name, version, ex);
                        }
                    }
                }
            }
        } catch (Throwable ex) {
            LOGGER.error("Pipeline callback failed for {}@{}", name, version, ex);
        }
    }
}
