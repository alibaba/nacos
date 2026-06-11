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

package com.alibaba.nacos.ai.service.prompt;

import static com.alibaba.nacos.ai.constant.AiResourceConstants.LABEL_LATEST;
import static com.alibaba.nacos.ai.constant.AiResourceConstants.VERSION_STATUS_DRAFT;
import static com.alibaba.nacos.ai.constant.AiResourceConstants.VERSION_STATUS_ONLINE;
import static com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT;

import com.alibaba.nacos.ai.config.PromptDataMigrationTask;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeResult;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.service.trace.AiResourceTraceService;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionResult;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.ai.utils.PromptVersionUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptVariable;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.ai.event.PromptDownloadEvent;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFileContent;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFilesPipelineContext;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Prompt lifecycle operation service implementation.
 *
 * <p>Follows the same governance pattern as {@code SkillOperationServiceImpl}:
 * DB metadata (ai_resource + ai_resource_version) + NacosConfig content (via AiResourceStorageRouter).</p>
 *
 * @author nacos
 */
@Service
public class PromptOperationServiceImpl implements PromptOperationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PromptOperationServiceImpl.class);
    
    private static final String PROMPT_STORAGE_PROVIDER_CONFIG_KEY =
        "nacos.ai.prompt.storage.provider";
    
    private static final String DEFAULT_AUTHOR = "-";
    
    private static final String DEFAULT_INITIAL_VERSION = "0.0.1";
    
    private final AiResourceStorageRouter storageRouter;
    
    private final PublishPipelineExecutor publishPipelineExecutor;
    
    private final ConfigOperationService configOperationService;
    
    private final AiResourceManager resourceManager;
    
    private final PromptDataMigrationTask promptDataMigrationTask;
    
    public PromptOperationServiceImpl(PublishPipelineExecutor publishPipelineExecutor,
        ConfigOperationService configOperationService,
        AiResourceManager resourceManager,
        PromptDataMigrationTask promptDataMigrationTask) {
        this.storageRouter = AiResourceStorageRouter.getInstance();
        this.publishPipelineExecutor = publishPipelineExecutor;
        this.configOperationService = configOperationService;
        this.resourceManager = resourceManager;
        this.promptDataMigrationTask = promptDataMigrationTask;
    }
    
    // ========== Admin APIs ==========
    
    @Override
    public String createDraft(String namespaceId, String promptKey, String basedOnVersion,
        String targetVersion,
        String template, List<PromptVariable> variables, String commitMsg, String description,
        String bizTags)
        throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "promptKey is required");
        }
        
        AiResource meta = resourceManager.findMeta(namespaceId, promptKey, RESOURCE_TYPE_PROMPT);
        
        if (meta == null) {
            // Brand-new prompt: require template
            if (StringUtils.isBlank(template)) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "template is required when creating a new prompt");
            }
            String version =
                StringUtils.isBlank(targetVersion) ? DEFAULT_INITIAL_VERSION : targetVersion;
            validateVersion(version);
            
            writePromptToStorage(namespaceId, promptKey, version, template, variables);
            
            String currentUser = VisibilityHelper.resolveCurrentIdentity();
            resourceManager.insertVersionRow(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                StringUtils.isBlank(currentUser) ? DEFAULT_AUTHOR : currentUser,
                VERSION_STATUS_DRAFT, version, commitMsg,
                buildStorageJson(namespaceId, promptKey, version));
            
            resourceManager.initOrUpdateMetaForDraft(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                description, bizTags, version, null, true);
            AiResourceTraceService.logSuccess(RESOURCE_TYPE_PROMPT, promptKey, version,
                AiResourceTraceService.OP_CREATE_DRAFT, VisibilityHelper.resolveCurrentIdentity(),
                VisibilityHelper.resolveClientIp());
            
            return version;
        }
        
        // Existing prompt
        VisibilityHelper.checkWritableResource(meta);
        PromptVersionInfoPojo info = requireVersionInfo(meta);
        ResourceVersionInfo resourceInfo = toResourceVersionInfo(info);
        AiResourceManager.ensureNoWorkingVersion(resourceInfo, "create draft");
        
        if (StringUtils.isNotBlank(basedOnVersion)) {
            // Fork from existing version
            AiResourceVersion baseRow = resourceManager.findVersion(namespaceId, promptKey,
                RESOURCE_TYPE_PROMPT, basedOnVersion);
            if (baseRow == null) {
                throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Base version not found: " + basedOnVersion);
            }
            PromptVersionInfo baseContent =
                loadPromptFromStorage(namespaceId, promptKey, basedOnVersion);
            String newVersion = StringUtils.isBlank(targetVersion)
                ? incrementVersion(basedOnVersion) : targetVersion;
            validateVersion(newVersion);
            checkVersionNotExists(namespaceId, promptKey, newVersion);
            
            writePromptToStorage(namespaceId, promptKey, newVersion,
                baseContent.getTemplate(), baseContent.getVariables());
            
            String currentUser = VisibilityHelper.resolveCurrentIdentity();
            resourceManager.insertVersionRow(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                StringUtils.isBlank(currentUser) ? DEFAULT_AUTHOR : currentUser,
                VERSION_STATUS_DRAFT, newVersion, commitMsg,
                buildStorageJson(namespaceId, promptKey, newVersion));
            
            resourceManager.markEditingVersionCas(namespaceId, meta, resourceInfo, newVersion,
                "create draft");
            AiResourceTraceService.logSuccess(RESOURCE_TYPE_PROMPT, promptKey, newVersion,
                AiResourceTraceService.OP_CREATE_DRAFT, VisibilityHelper.resolveCurrentIdentity(),
                VisibilityHelper.resolveClientIp(), "basedOn=" + basedOnVersion);
            return newVersion;
        }
        
        // New content from scratch (existing prompt, no base version)
        if (StringUtils.isBlank(template)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "template is required when not forking from an existing version");
        }
        String newVersion = StringUtils.isBlank(targetVersion)
            ? resolveNextVersion(namespaceId, promptKey) : targetVersion;
        validateVersion(newVersion);
        checkVersionNotExists(namespaceId, promptKey, newVersion);
        
        writePromptToStorage(namespaceId, promptKey, newVersion, template, variables);
        
        String currentUser = VisibilityHelper.resolveCurrentIdentity();
        resourceManager.insertVersionRow(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
            StringUtils.isBlank(currentUser) ? DEFAULT_AUTHOR : currentUser,
            VERSION_STATUS_DRAFT, newVersion, commitMsg,
            buildStorageJson(namespaceId, promptKey, newVersion));
        
        resourceManager.markEditingVersionCas(namespaceId, meta, resourceInfo, newVersion,
            "create draft");
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_PROMPT, promptKey, newVersion,
            AiResourceTraceService.OP_CREATE_DRAFT, VisibilityHelper.resolveCurrentIdentity(),
            VisibilityHelper.resolveClientIp());
        return newVersion;
    }
    
    @Override
    public void updateDraft(String namespaceId, String promptKey, String template,
        List<PromptVariable> variables,
        String commitMsg) throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "promptKey is required");
        }
        if (StringUtils.isBlank(template)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "template is required");
        }
        AiResource meta = requireMeta(namespaceId, promptKey);
        VisibilityHelper.checkWritableResource(meta);
        PromptVersionInfoPojo info = requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        if (StringUtils.isBlank(editing)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "No editing draft exists for prompt: " + promptKey);
        }
        resourceManager.requireDraftVersion(namespaceId, promptKey, RESOURCE_TYPE_PROMPT, editing);
        
        writePromptToStorage(namespaceId, promptKey, editing, template, variables);
        
        // Update commitMsg in DB if provided
        if (StringUtils.isNotBlank(commitMsg)) {
            String storageJson = buildStorageJson(namespaceId, promptKey, editing);
            resourceManager.updateVersionStorageAndDesc(namespaceId, promptKey,
                RESOURCE_TYPE_PROMPT,
                editing, storageJson, commitMsg);
        }
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_PROMPT, promptKey, editing,
            AiResourceTraceService.OP_UPDATE_DRAFT, VisibilityHelper.resolveCurrentIdentity(),
            VisibilityHelper.resolveClientIp());
    }
    
    @Override
    public void deleteDraft(String namespaceId, String promptKey) throws NacosException {
        resourceManager.doDeleteDraft(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
            v -> deletePromptStorageForVersion(namespaceId, promptKey, v.getVersion()));
    }
    
    @Override
    public String submit(String namespaceId, String promptKey, String version)
        throws NacosException {
        AiResource meta = requireMeta(namespaceId, promptKey);
        VisibilityHelper.checkWritableResource(meta);
        PromptVersionInfoPojo info = requireVersionInfo(meta);
        ResourceVersionInfo resourceInfo = toResourceVersionInfo(info);
        
        String target =
            resourceManager.resolveSubmitTarget(resourceInfo, version, RESOURCE_TYPE_PROMPT,
                promptKey);
        final String finalTarget = target;
        
        // Move to reviewing before pipeline execution
        resourceManager.moveToReviewing(namespaceId, promptKey, RESOURCE_TYPE_PROMPT, finalTarget,
            meta, resourceInfo);
        
        // Build pipeline context
        ResourceFilesPipelineContext ctx = new ResourceFilesPipelineContext();
        ctx.setResourceType(PublishPipelineResourceType.PROMPT);
        ctx.setNamespaceId(namespaceId);
        ctx.setResourceName(promptKey);
        ctx.setVersion(finalTarget);
        
        PromptVersionInfo content = loadPromptFromStorage(namespaceId, promptKey, finalTarget);
        List<ResourceFileContent> pipelineFiles = new ArrayList<>();
        ResourceFileContent mainFile = new ResourceFileContent();
        mainFile.setFilePath(PromptUtils.PROMPT_MAIN_DATA_ID);
        mainFile.setContent(JacksonUtils.toJson(content));
        pipelineFiles.add(mainFile);
        ctx.setFiles(pipelineFiles);
        
        // Check pipeline availability
        if (!publishPipelineExecutor.isPipelineAvailable(ctx.getResourceType())) {
            publish(namespaceId, promptKey, finalTarget, true);
            return finalTarget;
        }
        
        // Pre-generate executionId
        String executionId = UUID.randomUUID().toString();
        
        PromptPublishPipelineInfo pipelineInfo = new PromptPublishPipelineInfo();
        pipelineInfo.setExecutionId(executionId);
        pipelineInfo.setStatus(PipelineExecutionStatus.IN_PROGRESS);
        pipelineInfo.setPipeline(new ArrayList<>());
        resourceManager.updateVersionPublishPipelineInfo(namespaceId, promptKey,
            RESOURCE_TYPE_PROMPT,
            finalTarget, JacksonUtils.toJson(pipelineInfo));
        
        String result = publishPipelineExecutor.execute(ctx,
            r -> onPipelineComplete(namespaceId, promptKey, finalTarget, r), executionId);
        if (StringUtils.isBlank(result)) {
            resourceManager.updateVersionPublishPipelineInfo(namespaceId, promptKey,
                RESOURCE_TYPE_PROMPT,
                finalTarget, null);
            publish(namespaceId, promptKey, finalTarget, true);
        }
        
        return finalTarget;
    }
    
    @Override
    public void publish(String namespaceId, String promptKey, String version,
        boolean updateLatestLabel)
        throws NacosException {
        resourceManager.doPublish(namespaceId, promptKey, RESOURCE_TYPE_PROMPT, version, true);
        try {
            refreshLatestMirror(namespaceId, promptKey);
        } catch (Exception e) {
            LOGGER.warn("Failed to refresh latest mirror for prompt: {}", promptKey, e);
        }
    }
    
    @Override
    public void forcePublish(String namespaceId, String promptKey, String version,
        boolean updateLatestLabel)
        throws NacosException {
        resourceManager.doForcePublish(namespaceId, promptKey, RESOURCE_TYPE_PROMPT, version,
            true);
        try {
            refreshLatestMirror(namespaceId, promptKey);
        } catch (Exception e) {
            LOGGER.warn("Failed to refresh latest mirror for prompt: {}", promptKey, e);
        }
    }
    
    @Override
    public void redraft(String namespaceId, String promptKey, String version)
        throws NacosException {
        resourceManager.doRedraft(namespaceId, promptKey, RESOURCE_TYPE_PROMPT, version);
    }
    
    @Override
    public void changeOnlineStatus(String namespaceId, String promptKey, String version,
        boolean online)
        throws NacosException {
        AiResource meta = requireMeta(namespaceId, promptKey);
        VisibilityHelper.checkWritableResource(meta);
        PromptVersionInfoPojo info = requireVersionInfo(meta);
        ResourceVersionInfo resourceInfo = toResourceVersionInfo(info);
        String oldLatest =
            resourceInfo.getLabels() == null ? null : resourceInfo.getLabels().get(LABEL_LATEST);
        AiResourceVersion v =
            resourceManager.toggleVersionOnlineStatus(namespaceId, meta, resourceInfo, version,
                online);
        if (v == null) {
            return;
        }
        String newLatest =
            resourceInfo.getLabels() == null ? null : resourceInfo.getLabels().get(LABEL_LATEST);
        syncLatestMirrorIfChanged(namespaceId, promptKey, oldLatest, newLatest);
    }
    
    @Override
    public void updateLabels(String namespaceId, String promptKey, Map<String, String> labels)
        throws NacosException {
        resourceManager.validateAndUpdateLabels(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
            labels);
    }
    
    @Override
    public void updateBizTags(String namespaceId, String promptKey, String bizTags)
        throws NacosException {
        AiResource meta = requireMeta(namespaceId, promptKey);
        VisibilityHelper.checkWritableResource(meta);
        updateMetaBizTagsCas(namespaceId, meta, bizTags);
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_PROMPT, promptKey, null,
            AiResourceTraceService.OP_UPDATE_BIZ_TAGS, VisibilityHelper.resolveCurrentIdentity(),
            VisibilityHelper.resolveClientIp());
    }
    
    @Override
    public void updateDescription(String namespaceId, String promptKey, String description)
        throws NacosException {
        AiResource meta = requireMeta(namespaceId, promptKey);
        VisibilityHelper.checkWritableResource(meta);
        updateMetaDescriptionCas(namespaceId, meta, description);
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_PROMPT, promptKey, null,
            AiResourceTraceService.OP_UPDATE_DESCRIPTION, VisibilityHelper.resolveCurrentIdentity(),
            VisibilityHelper.resolveClientIp());
    }
    
    @Override
    public void deletePrompt(String namespaceId, String promptKey) throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "promptKey is required");
        }
        AiResource meta = resourceManager.findMeta(namespaceId, promptKey, RESOURCE_TYPE_PROMPT);
        if (meta != null) {
            VisibilityHelper.checkWritableResource(meta);
        }
        
        // Delete all version storage content
        List<AiResourceVersion> allVersions = loadAllVersionRows(namespaceId, promptKey);
        for (AiResourceVersion v : allVersions) {
            if (v == null) {
                continue;
            }
            try {
                deletePromptStorageForVersion(namespaceId, promptKey, v.getVersion());
            } catch (Exception e) {
                LOGGER.warn("Failed to delete storage for prompt version: {}@{}", promptKey,
                    v.getVersion(), e);
            }
        }
        
        // Delete legacy latest mirror in nacos-ai-prompt group
        deleteLegacyLatestMirror(namespaceId, promptKey);
        
        // Clean up legacy Config entries (descriptor, mapping, version configs) to prevent re-migration
        List<String> versionStrings = new ArrayList<>();
        for (AiResourceVersion v : allVersions) {
            if (v != null) {
                versionStrings.add(v.getVersion());
            }
        }
        promptDataMigrationTask.cleanupLegacyConfig(namespaceId, promptKey, versionStrings);
        
        // Delete DB rows
        resourceManager.deleteVersionsByNameAndType(namespaceId, promptKey, RESOURCE_TYPE_PROMPT);
        resourceManager.deleteMeta(namespaceId, promptKey, RESOURCE_TYPE_PROMPT);
    }
    
    private void deleteLegacyLatestMirror(String namespaceId, String promptKey) {
        try {
            final String latestDataId = PromptVersionUtils.buildDataId(promptKey);
            configOperationService.deleteConfig(latestDataId, Constants.Prompt.PROMPT_GROUP,
                namespaceId, null, null,
                "nacos", null);
        } catch (Exception e) {
            LOGGER.warn("Failed to delete legacy latest mirror for prompt: {}", promptKey, e);
        }
    }
    
    @Override
    public PromptMetaInfo getPromptDetail(String namespaceId, String promptKey)
        throws NacosException {
        AiResource meta = requireMeta(namespaceId, promptKey);
        PromptVersionInfoPojo versionInfo = requireVersionInfo(meta);
        
        PromptMetaInfo detail = new PromptMetaInfo();
        detail.setPromptKey(promptKey);
        detail.setDescription(meta.getDesc());
        detail.setLatestVersion(
            versionInfo.getLabels() != null ? versionInfo.getLabels().get(LABEL_LATEST) : null);
        detail.setEditingVersion(versionInfo.getEditingVersion());
        detail.setReviewingVersion(versionInfo.getReviewingVersion());
        detail.setOnlineCnt(versionInfo.getOnlineCnt());
        detail.setLabels(versionInfo.getLabels());
        detail
            .setGmtModified(meta.getGmtModified() == null ? null : meta.getGmtModified().getTime());
        detail.setBizTags(parseBizTagsList(meta.getBizTags()));
        detail.setBizTagsStr(meta.getBizTags());
        detail.setDownloadCount(meta.getDownloadCount());
        
        // Load version list
        List<AiResourceVersion> allVersions = loadAllVersionRows(namespaceId, promptKey);
        List<String> versionStrings = new ArrayList<>();
        List<PromptVersionSummary> versionDetails = new ArrayList<>();
        for (AiResourceVersion v : allVersions) {
            if (v != null) {
                versionStrings.add(v.getVersion());
                PromptVersionSummary vs = new PromptVersionSummary();
                vs.setPromptKey(promptKey);
                vs.setVersion(v.getVersion());
                vs.setStatus(v.getStatus());
                vs.setSrcUser(v.getAuthor());
                vs.setCommitMsg(v.getDesc());
                vs.setGmtModified(v.getGmtModified() == null ? null : v.getGmtModified().getTime());
                vs.setPublishPipelineInfo(v.getPublishPipelineInfo());
                vs.setDownloadCount(v.getDownloadCount());
                versionDetails.add(vs);
            }
        }
        detail.setVersions(versionStrings);
        detail.setVersionDetails(versionDetails);
        return detail;
    }
    
    @Override
    public PromptVersionInfo getPromptVersionDetail(String namespaceId, String promptKey,
        String version)
        throws NacosException {
        requireMeta(namespaceId, promptKey);
        if (StringUtils.isBlank(version)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "version is required");
        }
        AiResourceVersion versionRow = resourceManager.findVersion(namespaceId, promptKey,
            RESOURCE_TYPE_PROMPT, version);
        if (versionRow == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Prompt version not found: " + promptKey + "@" + version);
        }
        PromptVersionInfo result = loadPromptFromStorage(namespaceId, promptKey, version);
        result.setSrcUser(versionRow.getAuthor());
        result.setCommitMsg(versionRow.getDesc());
        result.setStatus(versionRow.getStatus());
        return result;
    }
    
    @Override
    public PromptVersionInfo downloadPromptVersion(String namespaceId, String promptKey,
        String version)
        throws NacosException {
        PromptVersionInfo info = getPromptVersionDetail(namespaceId, promptKey, version);
        NotifyCenter.publishEvent(new PromptDownloadEvent(namespaceId, promptKey, version));
        return info;
    }
    
    @Override
    public Page<PromptMetaSummary> listPrompts(String namespaceId, String promptKey, String search,
        String bizTags,
        int pageNo, int pageSize) throws NacosException {
        String nameLike = null;
        if (StringUtils.isNotBlank(promptKey)) {
            if ("blur".equalsIgnoreCase(search)) {
                nameLike = resourceManager.generateLikeArgument(
                    Constants.ALL_PATTERN + promptKey + Constants.ALL_PATTERN);
            } else {
                nameLike = promptKey;
            }
        }
        String bizTagsLike = StringUtils.isNotBlank(bizTags)
            ? resourceManager
                .generateLikeArgument(Constants.ALL_PATTERN + bizTags + Constants.ALL_PATTERN)
            : null;
        
        Page<AiResource> metaPage =
            resourceManager.listMetaByType(namespaceId, RESOURCE_TYPE_PROMPT, nameLike,
                bizTagsLike, pageNo, pageSize);
        
        List<PromptMetaSummary> items = new ArrayList<>();
        if (metaPage != null && metaPage.getPageItems() != null) {
            for (AiResource resource : metaPage.getPageItems()) {
                if (resource == null) {
                    continue;
                }
                PromptVersionInfoPojo vInfo = parseVersionInfo(resource.getVersionInfo());
                PromptMetaSummary summary = new PromptMetaSummary();
                summary.setPromptKey(resource.getName());
                summary.setDescription(resource.getDesc());
                summary.setLatestVersion(
                    vInfo != null && vInfo.getLabels() != null ? vInfo.getLabels().get(LABEL_LATEST)
                        : null);
                summary.setEditingVersion(vInfo != null ? vInfo.getEditingVersion() : null);
                summary.setReviewingVersion(vInfo != null ? vInfo.getReviewingVersion() : null);
                summary.setOnlineCnt(vInfo != null ? vInfo.getOnlineCnt() : null);
                summary.setLabels(vInfo != null ? vInfo.getLabels() : null);
                summary.setGmtModified(
                    resource.getGmtModified() == null ? null : resource.getGmtModified().getTime());
                summary.setBizTags(parseBizTagsList(resource.getBizTags()));
                summary.setBizTagsStr(resource.getBizTags());
                summary.setDownloadCount(resource.getDownloadCount());
                items.add(summary);
            }
        }
        
        Page<PromptMetaSummary> result = new Page<>();
        result.setPageNumber(pageNo);
        result.setTotalCount(metaPage == null ? 0 : metaPage.getTotalCount());
        result.setPagesAvailable(metaPage == null ? 0 : metaPage.getPagesAvailable());
        result.setPageItems(items);
        return result;
    }
    
    @Override
    public Page<PromptVersionSummary> listPromptVersions(String namespaceId, String promptKey,
        int pageNo,
        int pageSize) throws NacosException {
        requireMeta(namespaceId, promptKey);
        
        Page<AiResourceVersion> versionPage = resourceManager.listVersions(namespaceId, promptKey,
            RESOURCE_TYPE_PROMPT, null, pageNo, pageSize);
        
        List<PromptVersionSummary> items = new ArrayList<>();
        if (versionPage != null && versionPage.getPageItems() != null) {
            for (AiResourceVersion v : versionPage.getPageItems()) {
                if (v == null) {
                    continue;
                }
                PromptVersionSummary summary = new PromptVersionSummary();
                summary.setPromptKey(promptKey);
                summary.setVersion(v.getVersion());
                summary.setStatus(v.getStatus());
                summary.setSrcUser(v.getAuthor());
                summary.setCommitMsg(v.getDesc());
                summary.setGmtModified(
                    v.getGmtModified() == null ? null : v.getGmtModified().getTime());
                summary.setDownloadCount(v.getDownloadCount());
                items.add(summary);
            }
        }
        
        Page<PromptVersionSummary> result = new Page<>();
        result.setPageNumber(pageNo);
        result.setTotalCount(versionPage == null ? 0 : versionPage.getTotalCount());
        result.setPagesAvailable(versionPage == null ? 0 : versionPage.getPagesAvailable());
        result.setPageItems(items);
        return result;
    }
    
    // ========== Client APIs ==========
    
    @Override
    public PromptVersionInfo queryPrompt(String namespaceId, String promptKey, String version,
        String label)
        throws NacosException {
        AiResource meta = resourceManager.findMeta(namespaceId, promptKey, RESOURCE_TYPE_PROMPT);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Prompt not found: " + promptKey);
        }
        
        PromptVersionInfoPojo info = requireVersionInfo(meta);
        String resolved = resolveClientVersion(info, version, label);
        if (StringUtils.isBlank(resolved)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Prompt version not found: " + promptKey);
        }
        
        // Verify version is online
        AiResourceVersion versionRow = resourceManager.findVersion(namespaceId, promptKey,
            RESOURCE_TYPE_PROMPT, resolved);
        if (versionRow == null || !VERSION_STATUS_ONLINE.equalsIgnoreCase(versionRow.getStatus())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Prompt version not online: " + promptKey + "@" + resolved);
        }
        
        PromptVersionInfo result = loadPromptFromStorage(namespaceId, promptKey, resolved);
        result.setSrcUser(versionRow.getAuthor());
        result.setCommitMsg(versionRow.getDesc());
        return result;
    }
    
    @Override
    public void refreshLatestMirror(String namespaceId, String promptKey) throws NacosException {
        AiResource meta = resourceManager.findMeta(namespaceId, promptKey, RESOURCE_TYPE_PROMPT);
        if (meta == null) {
            return;
        }
        PromptVersionInfoPojo info = parseVersionInfo(meta.getVersionInfo());
        if (info == null || info.getLabels() == null) {
            return;
        }
        String latestVersion = info.getLabels().get(LABEL_LATEST);
        if (StringUtils.isBlank(latestVersion)) {
            return;
        }
        refreshLatestMirror(namespaceId, promptKey, latestVersion);
    }
    
    private void refreshLatestMirror(String namespaceId, String promptKey, String latestVersion)
        throws NacosException {
        
        // Read content from new storage
        PromptVersionInfo content = loadPromptFromStorage(namespaceId, promptKey, latestVersion);
        if (content == null) {
            return;
        }
        
        // Write to legacy location: group=nacos-ai-prompt, dataId=promptKey.json
        String latestDataId = PromptVersionUtils.buildDataId(promptKey);
        ConfigForm form = new ConfigForm();
        form.setDataId(latestDataId);
        form.setGroup(Constants.Prompt.PROMPT_GROUP);
        form.setNamespaceId(namespaceId);
        form.setType(Constants.Prompt.PROMPT_CONFIG_TYPE);
        form.setContent(JacksonUtils.toJson(content));
        ConfigRequestInfo requestInfo = new ConfigRequestInfo();
        requestInfo.setUpdateForExist(true);
        configOperationService.publishConfig(form, requestInfo, null);
    }
    
    private void syncLatestMirrorIfChanged(String namespaceId, String promptKey, String oldLatest,
        String newLatest) {
        if (StringUtils.equals(oldLatest, newLatest)) {
            return;
        }
        if (StringUtils.isBlank(newLatest)) {
            deleteLegacyLatestMirror(namespaceId, promptKey);
            return;
        }
        try {
            refreshLatestMirror(namespaceId, promptKey, newLatest);
        } catch (Exception e) {
            LOGGER.warn("Failed to refresh latest mirror for prompt: {}", promptKey, e);
        }
    }
    
    // ========== Private methods ==========
    
    private void writePromptToStorage(String namespaceId, String promptKey, String version,
        String template,
        List<PromptVariable> variables) throws NacosException {
        String provider = resolvePromptStorageProvider();
        
        PromptVersionInfo content = new PromptVersionInfo();
        content.setPromptKey(promptKey);
        content.setVersion(version);
        content.setTemplate(template);
        content.setVariables(variables);
        content.setGmtModified(System.currentTimeMillis());
        
        // Pre-compute md5 from content without md5 field, then store it
        String contentJson = JacksonUtils.toJson(content);
        String md5 = MD5Utils.md5Hex(contentJson, StandardCharsets.UTF_8.name());
        content.setMd5(md5);
        
        byte[] contentBytes = JacksonUtils.toJson(content).getBytes(StandardCharsets.UTF_8);
        StorageKey storageKey = NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId,
            RESOURCE_TYPE_PROMPT, promptKey, version,
            PromptUtils.PROMPT_MAIN_DATA_ID);
        storageRouter.route(storageKey).save(storageKey, contentBytes);
    }
    
    private PromptVersionInfo loadPromptFromStorage(String namespaceId, String promptKey,
        String version)
        throws NacosException {
        String provider = resolvePromptStorageProvider();
        StorageKey storageKey = NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId,
            RESOURCE_TYPE_PROMPT, promptKey, version,
            PromptUtils.PROMPT_MAIN_DATA_ID);
        byte[] data = storageRouter.route(storageKey).get(storageKey);
        if (data == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Prompt content not found: " + promptKey + "@" + version);
        }
        PromptVersionInfo result =
            JacksonUtils.toObj(new String(data, StandardCharsets.UTF_8), PromptVersionInfo.class);
        result.setPromptKey(promptKey);
        result.setVersion(version);
        return result;
    }
    
    private void deletePromptStorageForVersion(String namespaceId, String promptKey,
        String version) {
        try {
            String provider = resolvePromptStorageProvider();
            StorageKey storageKey =
                NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId,
                    RESOURCE_TYPE_PROMPT, promptKey, version,
                    PromptUtils.PROMPT_MAIN_DATA_ID);
            storageRouter.route(storageKey).delete(storageKey);
        } catch (Exception e) {
            LOGGER.warn("Failed to delete prompt storage: {}@{}", promptKey, version, e);
        }
    }
    
    private static String buildStorageJson(String namespaceId, String promptKey, String version) {
        Map<String, Object> json = new HashMap<>(4);
        json.put("provider", resolvePromptStorageProvider());
        json.put("scope", namespaceId + ":" + promptKey + ":" + version);
        json.put("files", Collections.singletonList(PromptUtils.PROMPT_MAIN_DATA_ID));
        return JacksonUtils.toJson(json);
    }
    
    private static String resolvePromptStorageProvider() {
        String provider =
            EnvUtil.getProperty(PROMPT_STORAGE_PROVIDER_CONFIG_KEY,
                NacosConfigAiResourceStorage.TYPE);
        return StringUtils.isBlank(provider) ? NacosConfigAiResourceStorage.TYPE : provider.trim();
    }
    
    private AiResource requireMeta(String namespaceId, String promptKey) throws NacosException {
        AiResource meta = resourceManager.findMeta(namespaceId, promptKey, RESOURCE_TYPE_PROMPT);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Prompt not found: " + promptKey);
        }
        return meta;
    }
    
    /**
     * Parse biz tags JSON string to list. Supports JSON array format and comma-separated fallback.
     */
    private static List<String> parseBizTagsList(String bizTags) {
        if (StringUtils.isBlank(bizTags)) {
            return new ArrayList<>();
        }
        try {
            return JacksonUtils.toObj(bizTags, List.class);
        } catch (Exception e) {
            // Fallback: treat as comma-separated
            List<String> result = new ArrayList<>();
            for (String tag : bizTags.split(",")) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result;
        }
    }
    
    private static PromptVersionInfoPojo requireVersionInfo(AiResource meta) {
        PromptVersionInfoPojo info = parseVersionInfo(meta == null ? null : meta.getVersionInfo());
        if (info == null) {
            info = new PromptVersionInfoPojo();
            info.setLabels(new HashMap<>(4));
        } else if (info.getLabels() == null) {
            info.setLabels(new HashMap<>(4));
        }
        return info;
    }
    
    private static PromptVersionInfoPojo parseVersionInfo(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JacksonUtils.toObj(json, PromptVersionInfoPojo.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    private void updateMetaBizTagsCas(String namespaceId, AiResource meta, String bizTags)
        throws NacosException {
        resourceManager.updateBizTagsCas(namespaceId, meta, bizTags);
    }
    
    private void updateMetaDescriptionCas(String namespaceId, AiResource meta, String description)
        throws NacosException {
        if (meta == null || meta.getMetaVersion() == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                "Meta version missing");
        }
        resourceManager.bumpMetaDescription(namespaceId, meta, description);
    }
    
    private void validateVersion(String version) throws NacosApiException {
        if (!PromptVersionUtils.isValidVersion(version)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Version must be in format major.minor.patch, got: " + version);
        }
    }
    
    /**
     * Load all version rows for a prompt by paginating through all pages.
     */
    private List<AiResourceVersion> loadAllVersionRows(String namespaceId, String promptKey) {
        List<AiResourceVersion> all = new ArrayList<>();
        int pageNo = 1;
        int pageSize = 200;
        while (true) {
            Page<AiResourceVersion> page = resourceManager.listVersions(namespaceId, promptKey,
                RESOURCE_TYPE_PROMPT, null, pageNo, pageSize);
            if (page == null || page.getPageItems() == null || page.getPageItems().isEmpty()) {
                break;
            }
            all.addAll(page.getPageItems());
            if (page.getPageItems().size() < pageSize) {
                break;
            }
            pageNo++;
        }
        return all;
    }
    
    private void checkVersionNotExists(String namespaceId, String promptKey, String version)
        throws NacosException {
        AiResourceVersion existing =
            resourceManager.findVersion(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                version);
        if (existing != null) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "Prompt version already exists: " + version);
        }
    }
    
    private String resolveNextVersion(String namespaceId, String promptKey) {
        List<AiResourceVersion> allVersions = loadAllVersionRows(namespaceId, promptKey);
        if (allVersions.isEmpty()) {
            return DEFAULT_INITIAL_VERSION;
        }
        String maxVersion = null;
        for (AiResourceVersion v : allVersions) {
            if (v == null || !PromptVersionUtils.isValidVersion(v.getVersion())) {
                continue;
            }
            if (maxVersion == null
                || PromptVersionUtils.compareVersion(v.getVersion(), maxVersion) > 0) {
                maxVersion = v.getVersion();
            }
        }
        return maxVersion == null ? DEFAULT_INITIAL_VERSION : incrementVersion(maxVersion);
    }
    
    private static String incrementVersion(String version) {
        if (!PromptVersionUtils.isValidVersion(version)) {
            return DEFAULT_INITIAL_VERSION;
        }
        String[] parts = version.split("\\.");
        int patch = Integer.parseInt(parts[2]) + 1;
        return parts[0] + "." + parts[1] + "." + patch;
    }
    
    /**
     * Resolve version for client query. Priority: explicit version > label > latest.
     */
    private String resolveClientVersion(PromptVersionInfoPojo info, String version, String label) {
        if (StringUtils.isNotBlank(version)) {
            return version;
        }
        if (StringUtils.isNotBlank(label) && info.getLabels() != null) {
            String resolved = info.getLabels().get(label);
            if (StringUtils.isNotBlank(resolved)) {
                return resolved;
            }
        }
        // Default to latest
        if (info.getLabels() != null) {
            return info.getLabels().get(LABEL_LATEST);
        }
        return null;
    }
    
    private void onPipelineComplete(String namespaceId, String promptKey, String version,
        PipelineExecutionResult result) {
        resourceManager.onPipelineComplete(namespaceId, promptKey, RESOURCE_TYPE_PROMPT, version,
            result);
    }
    
    // ========== Inner classes ==========
    
    private static class PromptVersionInfoPojo {
        
        private String editingVersion;
        
        private String reviewingVersion;
        
        private Integer onlineCnt;
        
        private Map<String, String> labels;
        
        public String getEditingVersion() {
            return editingVersion;
        }
        
        public void setEditingVersion(String editingVersion) {
            this.editingVersion = editingVersion;
        }
        
        public String getReviewingVersion() {
            return reviewingVersion;
        }
        
        public void setReviewingVersion(String reviewingVersion) {
            this.reviewingVersion = reviewingVersion;
        }
        
        public Integer getOnlineCnt() {
            return onlineCnt;
        }
        
        public void setOnlineCnt(Integer onlineCnt) {
            this.onlineCnt = onlineCnt;
        }
        
        public Map<String, String> getLabels() {
            return labels;
        }
        
        public void setLabels(Map<String, String> labels) {
            this.labels = labels;
        }
    }
    
    private static class PromptPublishPipelineInfo {
        
        private String executionId;
        
        private PipelineExecutionStatus status;
        
        private List<PipelineNodeResult> pipeline;
        
        public String getExecutionId() {
            return executionId;
        }
        
        public void setExecutionId(String executionId) {
            this.executionId = executionId;
        }
        
        public PipelineExecutionStatus getStatus() {
            return status;
        }
        
        public void setStatus(PipelineExecutionStatus status) {
            this.status = status;
        }
        
        public List<PipelineNodeResult> getPipeline() {
            return pipeline;
        }
        
        public void setPipeline(List<PipelineNodeResult> pipeline) {
            this.pipeline = pipeline;
        }
    }
    
    private static ResourceVersionInfo toResourceVersionInfo(PromptVersionInfoPojo info) {
        ResourceVersionInfo result = new ResourceVersionInfo();
        if (info == null) {
            result.setLabels(new HashMap<>(4));
            return result;
        }
        result.setEditingVersion(info.getEditingVersion());
        result.setReviewingVersion(info.getReviewingVersion());
        result.setOnlineCnt(info.getOnlineCnt());
        result.setLabels(
            info.getLabels() == null ? new HashMap<>(4) : new HashMap<>(info.getLabels()));
        return result;
    }
    
    // ========== Legacy compatibility implementations (deprecated) ==========
    
    @Deprecated
    @Override
    public boolean publishPromptVersion(String namespaceId, String promptKey, String version,
        String template,
        String commitMsg, String description, String bizTags, List<PromptVariable> variables)
        throws NacosException {
        createDraft(namespaceId, promptKey, null, version, template, variables, commitMsg,
            description, bizTags);
        submit(namespaceId, promptKey, version);
        return true;
    }
    
    @Deprecated
    @Override
    public PromptMetaInfo getPromptMeta(String namespaceId, String promptKey)
        throws NacosException {
        return getPromptDetail(namespaceId, promptKey);
    }
    
    @Deprecated
    @Override
    public PromptVersionInfo queryPromptDetail(String namespaceId, String promptKey, String version,
        String label)
        throws NacosException {
        return queryPrompt(namespaceId, promptKey, version, label);
    }
    
    @Deprecated
    @Override
    public boolean bindLabel(String namespaceId, String promptKey, String label, String version)
        throws NacosException {
        rejectReservedLatestLabel(label);
        PromptMetaInfo detail = getPromptDetail(namespaceId, promptKey);
        Map<String, String> labels =
            detail.getLabels() != null ? new HashMap<>(detail.getLabels()) : new HashMap<>();
        removeReservedLatestLabel(labels);
        labels.put(label, version);
        updateLabels(namespaceId, promptKey, labels);
        return true;
    }
    
    @Deprecated
    @Override
    public boolean unbindLabel(String namespaceId, String promptKey, String label)
        throws NacosException {
        rejectReservedLatestLabel(label);
        PromptMetaInfo detail = getPromptDetail(namespaceId, promptKey);
        Map<String, String> labels =
            detail.getLabels() != null ? new HashMap<>(detail.getLabels()) : new HashMap<>();
        removeReservedLatestLabel(labels);
        labels.remove(label);
        updateLabels(namespaceId, promptKey, labels);
        return true;
    }
    
    private static void rejectReservedLatestLabel(String label) throws NacosException {
        if (StringUtils.equalsIgnoreCase(LABEL_LATEST, label)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Label `latest` is reserved and cannot be updated manually.");
        }
    }
    
    private static void removeReservedLatestLabel(Map<String, String> labels) {
        if (labels == null || labels.isEmpty()) {
            return;
        }
        labels.keySet().removeIf(label -> StringUtils.equalsIgnoreCase(LABEL_LATEST, label));
    }
    
    @Deprecated
    @Override
    public boolean updatePromptMetadata(String namespaceId, String promptKey, String description,
        String bizTags)
        throws NacosException {
        if (description != null) {
            updateDescription(namespaceId, promptKey, description);
        }
        if (bizTags != null) {
            updateBizTags(namespaceId, promptKey, bizTags);
        }
        return true;
    }
}
