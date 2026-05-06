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

import com.alibaba.nacos.ai.config.PromptDataMigrationTask;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeResult;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
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
    
    private static final String STORAGE_PROVIDER_NACOS_CONFIG = "nacos_config";
    
    private static final String PROMPT_STORAGE_PROVIDER_CONFIG_KEY = "nacos.ai.prompt.storage.provider";
    
    private static final String RESOURCE_TYPE_PROMPT = "prompt";
    
    private static final String VERSION_STATUS_ONLINE = "online";
    
    private static final String VERSION_STATUS_DRAFT = "draft";
    
    private static final String VERSION_STATUS_REVIEWING = "reviewing";
    
    private static final String VERSION_STATUS_OFFLINE = "offline";
    
    private static final String DEFAULT_AUTHOR = "-";
    
    private static final String LABEL_LATEST = "latest";
    
    private static final String DEFAULT_INITIAL_VERSION = "0.0.1";
    
    private static final String PROMPT_CONFIG_TYPE = "json";
    
    private final AiResourceStorageRouter storageRouter;
    
    private final PublishPipelineExecutor publishPipelineExecutor;
    
    private final PipelineExecutionRepository pipelineExecutionRepository;
    
    private final ConfigOperationService configOperationService;
    
    private final AiResourceManager resourceManager;
    
    private final PromptDataMigrationTask promptDataMigrationTask;
    
    public PromptOperationServiceImpl(PublishPipelineExecutor publishPipelineExecutor,
            PipelineExecutionRepository pipelineExecutionRepository,
            ConfigOperationService configOperationService,
            AiResourceManager resourceManager,
            PromptDataMigrationTask promptDataMigrationTask) {
        this.storageRouter = AiResourceStorageRouter.getInstance();
        this.publishPipelineExecutor = publishPipelineExecutor;
        this.pipelineExecutionRepository = pipelineExecutionRepository;
        this.configOperationService = configOperationService;
        this.resourceManager = resourceManager;
        this.promptDataMigrationTask = promptDataMigrationTask;
    }
    
    // ========== Admin APIs ==========
    
    @Override
    public String createDraft(String namespaceId, String promptKey, String basedOnVersion, String targetVersion,
            String template, List<PromptVariable> variables, String commitMsg, String description, String bizTags)
            throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "promptKey is required");
        }
        
        AiResource meta = resourceManager.findMeta(namespaceId, promptKey, RESOURCE_TYPE_PROMPT);
        
        if (meta == null) {
            // Brand-new prompt: require template
            if (StringUtils.isBlank(template)) {
                throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                        "template is required when creating a new prompt");
            }
            String version = StringUtils.isBlank(targetVersion) ? DEFAULT_INITIAL_VERSION : targetVersion;
            validateVersion(version);
            
            writePromptToStorage(namespaceId, promptKey, version, template, variables);
            
            String currentUser = VisibilityHelper.resolveCurrentIdentity();
            resourceManager.insertVersionRow(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                    StringUtils.isBlank(currentUser) ? DEFAULT_AUTHOR : currentUser,
                    VERSION_STATUS_DRAFT, version, commitMsg, buildStorageJson(namespaceId, promptKey, version));
            
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
        if (StringUtils.isNotBlank(info.getEditingVersion()) || StringUtils.isNotBlank(info.getReviewingVersion())) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    "There is already a working version (editing/reviewing), cannot create draft");
        }
        
        if (StringUtils.isNotBlank(basedOnVersion)) {
            // Fork from existing version
            AiResourceVersion baseRow = resourceManager.findVersion(namespaceId, promptKey,
                    RESOURCE_TYPE_PROMPT, basedOnVersion);
            if (baseRow == null) {
                throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        "Base version not found: " + basedOnVersion);
            }
            PromptVersionInfo baseContent = loadPromptFromStorage(namespaceId, promptKey, basedOnVersion);
            String newVersion = StringUtils.isBlank(targetVersion)
                    ? incrementVersion(basedOnVersion) : targetVersion;
            validateVersion(newVersion);
            checkVersionNotExists(namespaceId, promptKey, newVersion);
            
            writePromptToStorage(namespaceId, promptKey, newVersion,
                    baseContent.getTemplate(), baseContent.getVariables());
            
            String currentUser = VisibilityHelper.resolveCurrentIdentity();
            resourceManager.insertVersionRow(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                    StringUtils.isBlank(currentUser) ? DEFAULT_AUTHOR : currentUser,
                    VERSION_STATUS_DRAFT, newVersion, commitMsg, buildStorageJson(namespaceId, promptKey, newVersion));
            
            info.setEditingVersion(newVersion);
            updateMetaVersionInfoCas(namespaceId, meta, info);
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
                VERSION_STATUS_DRAFT, newVersion, commitMsg, buildStorageJson(namespaceId, promptKey, newVersion));
        
        info.setEditingVersion(newVersion);
        updateMetaVersionInfoCas(namespaceId, meta, info);
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_PROMPT, promptKey, newVersion,
                AiResourceTraceService.OP_CREATE_DRAFT, VisibilityHelper.resolveCurrentIdentity(),
                VisibilityHelper.resolveClientIp());
        return newVersion;
    }
    
    @Override
    public void updateDraft(String namespaceId, String promptKey, String template, List<PromptVariable> variables,
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
            resourceManager.updateVersionStorageAndDesc(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                    editing, storageJson, commitMsg);
        }
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_PROMPT, promptKey, editing,
                AiResourceTraceService.OP_UPDATE_DRAFT, VisibilityHelper.resolveCurrentIdentity(),
                VisibilityHelper.resolveClientIp());
    }
    
    @Override
    public void deleteDraft(String namespaceId, String promptKey) throws NacosException {
        AiResource meta = requireMeta(namespaceId, promptKey);
        VisibilityHelper.checkWritableResource(meta);
        PromptVersionInfoPojo info = requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        if (StringUtils.isBlank(editing)) {
            return;
        }
        
        AiResourceVersion v = resourceManager.findVersion(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                editing);
        
        // 1) meta: clear editingVersion reference first
        info.setEditingVersion(null);
        updateMetaVersionInfoCas(namespaceId, meta, info);
        
        // 2) version row, then storage files
        if (v != null && VERSION_STATUS_DRAFT.equalsIgnoreCase(v.getStatus())) {
            resourceManager.deleteVersion(namespaceId, promptKey, RESOURCE_TYPE_PROMPT, editing);
            deletePromptStorageForVersion(namespaceId, promptKey, editing);
        }
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_PROMPT, promptKey, editing,
                AiResourceTraceService.OP_DELETE_DRAFT, VisibilityHelper.resolveCurrentIdentity(),
                VisibilityHelper.resolveClientIp());
    }
    
    @Override
    public String submit(String namespaceId, String promptKey, String version) throws NacosException {
        AiResource meta = requireMeta(namespaceId, promptKey);
        VisibilityHelper.checkWritableResource(meta);
        PromptVersionInfoPojo info = requireVersionInfo(meta);
        
        String target = version;
        if (StringUtils.isBlank(target)) {
            target = info.getEditingVersion();
        }
        if (StringUtils.isBlank(target)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "No draft version to submit for prompt: " + promptKey);
        }
        
        AiResourceVersion v = resourceManager.findVersion(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                target);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Prompt version not found: " + promptKey + "@" + target);
        }
        
        final String finalTarget = target;
        
        // Move to reviewing before pipeline execution
        resourceManager.updateVersionStatus(namespaceId, promptKey, RESOURCE_TYPE_PROMPT, finalTarget,
                VERSION_STATUS_REVIEWING);
        info.setEditingVersion(null);
        info.setReviewingVersion(finalTarget);
        updateMetaVersionInfoCas(namespaceId, meta, info);
        
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
        resourceManager.updateVersionPublishPipelineInfo(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                finalTarget, JacksonUtils.toJson(pipelineInfo));
        
        String result = publishPipelineExecutor.execute(ctx,
                r -> onPipelineComplete(namespaceId, promptKey, finalTarget, r), executionId);
        if (StringUtils.isBlank(result)) {
            resourceManager.updateVersionPublishPipelineInfo(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                    finalTarget, null);
            publish(namespaceId, promptKey, finalTarget, true);
        }
        
        return finalTarget;
    }
    
    @Override
    public void publish(String namespaceId, String promptKey, String version, boolean updateLatestLabel)
            throws NacosException {
        AiResource meta = requireMeta(namespaceId, promptKey);
        VisibilityHelper.checkWritableResource(meta);
        PromptVersionInfoPojo info = requireVersionInfo(meta);
        
        AiResourceVersion v = resourceManager.findVersion(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                version);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Prompt version not found: " + promptKey + "@" + version);
        }
        if (!VERSION_STATUS_REVIEWING.equalsIgnoreCase(v.getStatus())
                && !VERSION_STATUS_ONLINE.equalsIgnoreCase(v.getStatus())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Only reviewing version can be published: " + version);
        }
        
        // Validate pipeline execution result if pipeline exists
        PromptPublishPipelineInfo pipelineInfo = parsePublishPipelineInfo(v.getPublishPipelineInfo());
        if (pipelineInfo != null && StringUtils.isNotBlank(pipelineInfo.getExecutionId())) {
            PipelineExecution execution = pipelineExecutionRepository.findById(pipelineInfo.getExecutionId());
            if (execution == null) {
                throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "Pipeline execution not found, cannot publish: " + version);
            }
            if (execution.getStatus() != PipelineExecutionStatus.APPROVED) {
                throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "Pipeline not approved, cannot publish: " + version);
            }
        }
        
        // 1) version status -> online (idempotent)
        boolean alreadyOnline = VERSION_STATUS_ONLINE.equalsIgnoreCase(v.getStatus());
        if (!alreadyOnline) {
            resourceManager.updateVersionStatus(namespaceId, promptKey, RESOURCE_TYPE_PROMPT, version,
                    VERSION_STATUS_ONLINE);
        }
        
        // 2) meta: clear working pointers, onlineCnt++ (only when not already online), update latest label
        if (StringUtils.equals(info.getReviewingVersion(), version)) {
            info.setReviewingVersion(null);
        }
        if (!alreadyOnline) {
            Integer cnt = info.getOnlineCnt();
            info.setOnlineCnt(cnt == null ? 1 : (cnt + 1));
        }
        if (info.getLabels() == null) {
            info.setLabels(new HashMap<>(4));
        }
        if (updateLatestLabel) {
            info.getLabels().put(LABEL_LATEST, version);
        }
        updateMetaVersionInfoCas(namespaceId, meta, info);
        
        // 3) Refresh latest mirror for backward compatibility
        if (updateLatestLabel) {
            try {
                refreshLatestMirror(namespaceId, promptKey);
            } catch (Exception e) {
                LOGGER.warn("Failed to refresh latest mirror for prompt: {}", promptKey, e);
            }
        }
    }
    
    @Override
    public void forcePublish(String namespaceId, String promptKey, String version, boolean updateLatestLabel)
            throws NacosException {
        AiResource meta = requireMeta(namespaceId, promptKey);
        VisibilityHelper.checkWritableResource(meta);
        PromptVersionInfoPojo info = requireVersionInfo(meta);
        
        AiResourceVersion v = resourceManager.findVersion(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                version);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Prompt version not found: " + promptKey + "@" + version);
        }
        
        // Allow force-publish from draft/reviewing status
        boolean alreadyOnline = VERSION_STATUS_ONLINE.equalsIgnoreCase(v.getStatus());
        if (!alreadyOnline) {
            resourceManager.updateVersionStatus(namespaceId, promptKey, RESOURCE_TYPE_PROMPT, version,
                    VERSION_STATUS_ONLINE);
        }
        
        // Clear working pointers if this version was editing or reviewing
        if (StringUtils.equals(info.getEditingVersion(), version)) {
            info.setEditingVersion(null);
        }
        if (StringUtils.equals(info.getReviewingVersion(), version)) {
            info.setReviewingVersion(null);
        }
        if (!alreadyOnline) {
            Integer cnt = info.getOnlineCnt();
            info.setOnlineCnt(cnt == null ? 1 : (cnt + 1));
        }
        if (info.getLabels() == null) {
            info.setLabels(new HashMap<>(4));
        }
        if (updateLatestLabel) {
            info.getLabels().put(LABEL_LATEST, version);
        }
        updateMetaVersionInfoCas(namespaceId, meta, info);
        
        if (updateLatestLabel) {
            try {
                refreshLatestMirror(namespaceId, promptKey);
            } catch (Exception e) {
                LOGGER.warn("Failed to refresh latest mirror for prompt: {}", promptKey, e);
            }
        }
    }
    
    @Override
    public void changeOnlineStatus(String namespaceId, String promptKey, String version, boolean online)
            throws NacosException {
        AiResource meta = requireMeta(namespaceId, promptKey);
        VisibilityHelper.checkWritableResource(meta);
        PromptVersionInfoPojo info = requireVersionInfo(meta);
        
        AiResourceVersion v = resourceManager.findVersion(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                version);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Prompt version not found: " + promptKey + "@" + version);
        }
        String targetStatus = online ? VERSION_STATUS_ONLINE : VERSION_STATUS_OFFLINE;
        String currentStatus = v.getStatus();
        
        // Skip if already in target status
        if (targetStatus.equalsIgnoreCase(currentStatus)) {
            return;
        }
        
        resourceManager.updateVersionStatus(namespaceId, promptKey, RESOURCE_TYPE_PROMPT, version,
                targetStatus);
        
        Integer cnt = info.getOnlineCnt() == null ? 0 : info.getOnlineCnt();
        if (online) {
            info.setOnlineCnt(cnt + 1);
        } else {
            info.setOnlineCnt(Math.max(0, cnt - 1));
        }
        updateMetaVersionInfoCas(namespaceId, meta, info);
        String traceOp = online ? AiResourceTraceService.OP_ONLINE_VERSION
                : AiResourceTraceService.OP_OFFLINE_VERSION;
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_PROMPT, promptKey, version, traceOp,
                VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
    }
    
    @Override
    public void updateLabels(String namespaceId, String promptKey, Map<String, String> labels) throws NacosException {
        AiResource meta = requireMeta(namespaceId, promptKey);
        VisibilityHelper.checkWritableResource(meta);
        PromptVersionInfoPojo info = requireVersionInfo(meta);
        
        // Protect latest label: if it already exists, prevent removal
        Map<String, String> newLabels = labels == null ? new HashMap<>(4) : new HashMap<>(labels);
        if (!newLabels.containsKey(LABEL_LATEST) && info.getLabels() != null
                && info.getLabels().containsKey(LABEL_LATEST)) {
            newLabels.put(LABEL_LATEST, info.getLabels().get(LABEL_LATEST));
        }
        
        info.setLabels(newLabels);
        updateMetaVersionInfoCas(namespaceId, meta, info);
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_PROMPT, promptKey, null,
                AiResourceTraceService.OP_UPDATE_LABELS, VisibilityHelper.resolveCurrentIdentity(),
                VisibilityHelper.resolveClientIp());
        
        // Refresh latest mirror if latest label changed
        if (labels != null && labels.containsKey(LABEL_LATEST)) {
            try {
                refreshLatestMirror(namespaceId, promptKey);
            } catch (Exception e) {
                LOGGER.warn("Failed to refresh latest mirror for prompt: {}", promptKey, e);
            }
        }
    }
    
    @Override
    public void updateBizTags(String namespaceId, String promptKey, String bizTags) throws NacosException {
        AiResource meta = requireMeta(namespaceId, promptKey);
        VisibilityHelper.checkWritableResource(meta);
        updateMetaBizTagsCas(namespaceId, meta, bizTags);
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_PROMPT, promptKey, null,
                AiResourceTraceService.OP_UPDATE_BIZ_TAGS, VisibilityHelper.resolveCurrentIdentity(),
                VisibilityHelper.resolveClientIp());
    }
    
    @Override
    public void updateDescription(String namespaceId, String promptKey, String description) throws NacosException {
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
                LOGGER.warn("Failed to delete storage for prompt version: {}@{}", promptKey, v.getVersion(), e);
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
            configOperationService.deleteConfig(latestDataId, Constants.Prompt.PROMPT_GROUP, namespaceId, null, null,
                    "nacos", null);
        } catch (Exception e) {
            LOGGER.warn("Failed to delete legacy latest mirror for prompt: {}", promptKey, e);
        }
    }
    
    @Override
    public PromptMetaInfo getPromptDetail(String namespaceId, String promptKey) throws NacosException {
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
        detail.setGmtModified(meta.getGmtModified() == null ? null : meta.getGmtModified().getTime());
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
    public PromptVersionInfo getPromptVersionDetail(String namespaceId, String promptKey, String version)
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
    public PromptVersionInfo downloadPromptVersion(String namespaceId, String promptKey, String version)
            throws NacosException {
        PromptVersionInfo info = getPromptVersionDetail(namespaceId, promptKey, version);
        NotifyCenter.publishEvent(new PromptDownloadEvent(namespaceId, promptKey, version));
        return info;
    }
    
    @Override
    public Page<PromptMetaSummary> listPrompts(String namespaceId, String promptKey, String search, String bizTags,
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
                ? resourceManager.generateLikeArgument(Constants.ALL_PATTERN + bizTags + Constants.ALL_PATTERN)
                : null;
        
        Page<AiResource> metaPage = resourceManager.listMetaByType(namespaceId, RESOURCE_TYPE_PROMPT, nameLike,
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
                        vInfo != null && vInfo.getLabels() != null ? vInfo.getLabels().get(LABEL_LATEST) : null);
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
    public Page<PromptVersionSummary> listPromptVersions(String namespaceId, String promptKey, int pageNo,
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
                summary.setGmtModified(v.getGmtModified() == null ? null : v.getGmtModified().getTime());
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
    public PromptVersionInfo queryPrompt(String namespaceId, String promptKey, String version, String label)
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
        form.setType(PROMPT_CONFIG_TYPE);
        form.setContent(JacksonUtils.toJson(content));
        ConfigRequestInfo requestInfo = new ConfigRequestInfo();
        requestInfo.setUpdateForExist(true);
        configOperationService.publishConfig(form, requestInfo, null);
    }
    
    // ========== Private methods ==========
    
    private void writePromptToStorage(String namespaceId, String promptKey, String version, String template,
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
                NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, promptKey, version,
                PromptUtils.PROMPT_MAIN_DATA_ID);
        storageRouter.route(storageKey).save(storageKey, contentBytes);
    }
    
    private PromptVersionInfo loadPromptFromStorage(String namespaceId, String promptKey, String version)
            throws NacosException {
        String provider = resolvePromptStorageProvider();
        StorageKey storageKey = NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId,
                NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, promptKey, version,
                PromptUtils.PROMPT_MAIN_DATA_ID);
        byte[] data = storageRouter.route(storageKey).get(storageKey);
        if (data == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Prompt content not found: " + promptKey + "@" + version);
        }
        PromptVersionInfo result = JacksonUtils.toObj(new String(data, StandardCharsets.UTF_8), PromptVersionInfo.class);
        result.setPromptKey(promptKey);
        result.setVersion(version);
        return result;
    }
    
    private void deletePromptStorageForVersion(String namespaceId, String promptKey, String version) {
        try {
            String provider = resolvePromptStorageProvider();
            StorageKey storageKey = NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId,
                    NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, promptKey, version,
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
        String provider = EnvUtil.getProperty(PROMPT_STORAGE_PROVIDER_CONFIG_KEY, STORAGE_PROVIDER_NACOS_CONFIG);
        return StringUtils.isBlank(provider) ? STORAGE_PROVIDER_NACOS_CONFIG : provider.trim();
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
    
    private static PromptPublishPipelineInfo parsePublishPipelineInfo(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JacksonUtils.toObj(json, PromptPublishPipelineInfo.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    private void updateMetaVersionInfoCas(String namespaceId, AiResource meta, PromptVersionInfoPojo info)
            throws NacosException {
        resourceManager.updateVersionInfoCas(namespaceId, meta, toResourceVersionInfo(info));
    }
    
    private void updateMetaBizTagsCas(String namespaceId, AiResource meta, String bizTags) throws NacosException {
        resourceManager.updateBizTagsCas(namespaceId, meta, bizTags);
    }
    
    private void updateMetaDescriptionCas(String namespaceId, AiResource meta, String description)
            throws NacosException {
        if (meta == null || meta.getMetaVersion() == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, "Meta version missing");
        }
        resourceManager.bumpMetaDescription(namespaceId, meta, description);
    }
    
    private void validateVersion(String version) throws NacosApiException {
        if (!PromptVersionUtils.isValidVersion(version)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
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
    
    private void checkVersionNotExists(String namespaceId, String promptKey, String version) throws NacosException {
        AiResourceVersion existing = resourceManager.findVersion(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
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
            if (maxVersion == null || PromptVersionUtils.compareVersion(v.getVersion(), maxVersion) > 0) {
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
        try {
            AiResourceVersion v = resourceManager.findVersion(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                    version);
            if (v == null) {
                LOGGER.warn("Pipeline complete but version row not found: {}@{}", promptKey, version);
                return;
            }
            
            PromptPublishPipelineInfo pipelineInfo = parsePublishPipelineInfo(v.getPublishPipelineInfo());
            if (pipelineInfo == null) {
                pipelineInfo = new PromptPublishPipelineInfo();
            }
            pipelineInfo.setStatus(result.getStatus());
            pipelineInfo.setPipeline(result.getPipeline());
            resourceManager.updateVersionPublishPipelineInfo(namespaceId, promptKey, RESOURCE_TYPE_PROMPT,
                    version, JacksonUtils.toJson(pipelineInfo));
            
            if (result.getStatus() == PipelineExecutionStatus.APPROVED) {
                AiResourceTraceService.logSuccess(RESOURCE_TYPE_PROMPT, promptKey, version,
                        AiResourceTraceService.OP_REVIEW_APPROVED, "system", "", result.getExecutionId());
            } else {
                // Reject back to draft and move reviewing -> editing (best effort).
                resourceManager.updateVersionStatus(namespaceId, promptKey, RESOURCE_TYPE_PROMPT, version,
                        VERSION_STATUS_DRAFT);
                AiResource meta = resourceManager.findMeta(namespaceId, promptKey, RESOURCE_TYPE_PROMPT);
                if (meta != null) {
                    PromptVersionInfoPojo info = requireVersionInfo(meta);
                    if (StringUtils.equals(info.getReviewingVersion(), version)) {
                        info.setReviewingVersion(null);
                        info.setEditingVersion(version);
                        try {
                            updateMetaVersionInfoCas(namespaceId, meta, info);
                        } catch (Exception ex) {
                            LOGGER.warn("Failed to rollback meta working pointers for {}@{}", promptKey, version, ex);
                        }
                    }
                }
                AiResourceTraceService.logSuccess(RESOURCE_TYPE_PROMPT, promptKey, version,
                        AiResourceTraceService.OP_REVIEW_REJECTED, "system", "", result.getExecutionId());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to handle pipeline completion for prompt: {}@{}", promptKey, version, e);
        }
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
        result.setLabels(info.getLabels() == null ? new HashMap<>(4) : new HashMap<>(info.getLabels()));
        return result;
    }
    
    // ========== Legacy compatibility implementations (deprecated) ==========
    
    @Deprecated
    @Override
    public boolean publishPromptVersion(String namespaceId, String promptKey, String version, String template,
            String commitMsg, String description, String bizTags, List<PromptVariable> variables)
            throws NacosException {
        createDraft(namespaceId, promptKey, null, version, template, variables, commitMsg, description, bizTags);
        submit(namespaceId, promptKey, version);
        return true;
    }
    
    @Deprecated
    @Override
    public PromptMetaInfo getPromptMeta(String namespaceId, String promptKey) throws NacosException {
        return getPromptDetail(namespaceId, promptKey);
    }
    
    @Deprecated
    @Override
    public PromptVersionInfo queryPromptDetail(String namespaceId, String promptKey, String version, String label)
            throws NacosException {
        return queryPrompt(namespaceId, promptKey, version, label);
    }
    
    @Deprecated
    @Override
    public boolean bindLabel(String namespaceId, String promptKey, String label, String version)
            throws NacosException {
        PromptMetaInfo detail = getPromptDetail(namespaceId, promptKey);
        Map<String, String> labels = detail.getLabels() != null ? new HashMap<>(detail.getLabels()) : new HashMap<>();
        labels.put(label, version);
        updateLabels(namespaceId, promptKey, labels);
        return true;
    }
    
    @Deprecated
    @Override
    public boolean unbindLabel(String namespaceId, String promptKey, String label) throws NacosException {
        PromptMetaInfo detail = getPromptDetail(namespaceId, promptKey);
        Map<String, String> labels = detail.getLabels() != null ? new HashMap<>(detail.getLabels()) : new HashMap<>();
        labels.remove(label);
        updateLabels(namespaceId, promptKey, labels);
        return true;
    }
    
    @Deprecated
    @Override
    public boolean updatePromptMetadata(String namespaceId, String promptKey, String description, String bizTags)
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
