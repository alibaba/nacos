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

package com.alibaba.nacos.ai.service.skills;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.event.SkillDownloadEvent;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.api.ai.model.skills.BatchUploadResult;
import com.alibaba.nacos.ai.model.skills.SkillIndexManifest;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionResult;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.service.trace.AiResourceTraceService;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.ai.utils.ExecutorUtils;
import com.alibaba.nacos.ai.utils.SkillRequestUtil;
import com.alibaba.nacos.ai.utils.SkillZipParser;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.ai.model.skills.SkillMeta;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.paramcheck.AbstractParamChecker;
import com.alibaba.nacos.common.paramcheck.ParamCheckResponse;
import com.alibaba.nacos.common.paramcheck.ParamCheckerManager;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.paramcheck.ServerParamCheckConfig;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFileContent;
import com.alibaba.nacos.plugin.ai.pipeline.model.SkillPipelineContext;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import static com.alibaba.nacos.ai.constant.Constants.Skills;

/**
 * Skill operation service implementation.
 *
 * <p>Manages the full lifecycle of skills: upload, bootstrap, draft/review/publish workflow, querying, and deletion.
 * Each skill consists of a SKILL.md file (markdown body with YAML front-matter) and optional resource files.
 * All file content is persisted through {@link AiResourceStorageRouter}, with metadata tracked via
 * {@link AiResourcePersistService} (meta row) and {@link AiResourceVersionPersistService} (version rows).
 * A {@link SkillIndexManifest} stored in Nacos config serves as a lightweight index for client-side discovery.</p>
 *
 * <p>Version lifecycle: Draft -> (Submit) -> Reviewing -> (Pipeline approved) -> Reviewed -> (Publish) -> Online.
 * Pipeline rejected returns to Draft. When no pipeline is configured, submit publishes directly to Online.</p>
 *
 * @author nacos
 */
@org.springframework.stereotype.Service
public class SkillOperationServiceImpl implements SkillOperationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillOperationServiceImpl.class);
    
    /**
     * Default storage provider for skills when system config is not specified.
     */
    private static final String STORAGE_PROVIDER_NACOS_CONFIG = "nacos_config";
    
    /**
     * System config key for skill storage provider.
     *
     * <p>Similar to Nacos datasource type selection, this allows choosing
     * different storage providers via service-level configuration.</p>
     */
    private static final String SKILL_STORAGE_PROVIDER_CONFIG_KEY =
        "nacos.ai.skill.storage.provider";
    
    private static final String AUTO_PUBLISH_AFTER_REVIEW_ENABLED_KEY =
        "nacos.ai.skill.auto-publish-after-review.enabled";
    
    private static final String RESOURCE_TYPE_SKILL = "skill";
    
    private static final String DEFAULT_AUTHOR = "-";
    
    private static final String SKILL_MD_RESOURCE_NAME = "SKILL.md";
    
    private static final String DEFAULT_INITIAL_UPLOAD_VERSION = "0.0.1";
    
    private static final String SCOPE_SKILL = "skill";
    
    private final AiResourceStorageRouter storageRouter;
    
    private final AiResourcePersistService aiResourcePersistService;
    
    private final AiResourceVersionPersistService aiResourceVersionPersistService;
    
    private final PublishPipelineExecutor publishPipelineExecutor;
    
    private final SkillIndexManifestService manifestService;
    
    private final AiResourceManager resourceManager;
    
    public SkillOperationServiceImpl(AiResourcePersistService aiResourcePersistService,
        AiResourceVersionPersistService aiResourceVersionPersistService,
        PublishPipelineExecutor publishPipelineExecutor,
        SkillIndexManifestService manifestService,
        AiResourceManager resourceManager) {
        this.storageRouter = AiResourceStorageRouter.getInstance();
        this.aiResourcePersistService = aiResourcePersistService;
        this.aiResourceVersionPersistService = aiResourceVersionPersistService;
        this.publishPipelineExecutor = publishPipelineExecutor;
        this.manifestService = manifestService;
        this.resourceManager = resourceManager;
    }
    
    /**
     * Upload a skill from a ZIP archive.
     *
     * <p>Flow: parse ZIP -> validate name -> resolve version -> check existing meta.
     * If overwrite=true, replaces the current editing draft or creates a new one.
     * If overwrite=false, fails when a working version (editing/reviewing) already exists.</p>
     */
    @Override
    public String uploadSkillFromZip(SkillUploadRequest request) throws NacosException {
        Skill skill = SkillZipParser.parseSkillFromZip(request.getZipBytes(),
            request.getNamespaceId());
        if (skill == null || StringUtils.isBlank(skill.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Skill name is required");
        }
        String uploadVersion = resolveUploadVersion(skill.getSkillMd(), request.getZipBytes(),
            request.getTargetVersion());
        return doUploadSingleSkill(request.getNamespaceId(), skill, uploadVersion,
            request.isOverwrite(), request.getCommitMsg());
    }
    
    /**
     * Batch upload multiple skills from a single zip archive using best-effort strategy.
     */
    @Override
    public BatchUploadResult batchUploadSkillsFromZip(String namespaceId, byte[] zipBytes,
        boolean overwrite)
        throws NacosException {
        SkillZipParser.MultiSkillParseResult parseResult =
            SkillZipParser.parseMultipleSkillsFromZip(zipBytes, namespaceId);
        BatchUploadResult result = new BatchUploadResult();
        
        // Record parse failures from invalid skill folders
        for (SkillZipParser.ParseFailure failure : parseResult.getFailures()) {
            result.addFailed(failure.getFolder(), failure.getReason());
        }
        
        for (Skill skill : parseResult.getSkills()) {
            String skillName = skill.getName();
            try {
                if (StringUtils.isBlank(skillName)) {
                    result.addFailed("unknown", "Skill name is required in YAML front matter");
                    continue;
                }
                String uploadVersion = resolveUploadVersion(skill.getSkillMd(), null, null);
                doUploadSingleSkill(namespaceId, skill, uploadVersion, overwrite, null);
                result.addSucceeded(skillName);
            } catch (Exception e) {
                LOGGER.warn("Batch upload failed for skill [{}]: {}", skillName, e.getMessage());
                result.addFailed(skillName != null ? skillName : "unknown", e.getMessage());
            }
        }
        return result;
    }
    
    /**
     * Core logic for uploading a single skill: validate, check meta, create/overwrite draft, log.
     */
    private String doUploadSingleSkill(String namespaceId, Skill skill, String uploadVersion,
        boolean overwrite, String commitMsg) throws NacosException {
        String name = skill.getName();
        validateSkillNameByParamChecker(name);
        
        AiResource meta = resourceManager.findMeta(namespaceId, name, RESOURCE_TYPE_SKILL);
        if (overwrite) {
            return overwriteUploadedSkill(namespaceId, skill, uploadVersion, meta, commitMsg);
        }
        if (meta == null) {
            createDraftWithSkill(namespaceId, skill, uploadVersion, null, true, commitMsg);
            AiResourceTraceService.logSuccess(RESOURCE_TYPE_SKILL, name, uploadVersion,
                AiResourceTraceService.OP_UPLOAD,
                VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
            return name;
        }
        
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        AiResourceManager.ensureNoWorkingVersion(info, "upload");
        
        String newVersion = resolveFinalUploadVersion(namespaceId, name, uploadVersion);
        createDraftWithSkill(namespaceId, skill, newVersion, meta, false, commitMsg);
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_SKILL, name, newVersion,
            AiResourceTraceService.OP_UPLOAD,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
        return name;
    }
    
    /**
     * Bootstrap a built-in skill from a ZIP archive (delegates to the overload with null source).
     */
    @Override
    public void bootstrapSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        bootstrapSkillFromZip(namespaceId, zipBytes, null);
    }
    
    /**
     * Bootstrap a built-in skill from a ZIP archive. Skips if the skill already exists.
     *
     * <p>Unlike upload, this directly writes storage and creates a published meta + version
     * in one step (no draft/review workflow), and also initializes the index manifest.</p>
     */
    @Override
    public void bootstrapSkillFromZip(String namespaceId, byte[] zipBytes, String from)
        throws NacosException {
        // Step 1: Parse ZIP and validate
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, namespaceId);
        if (skill == null || StringUtils.isBlank(skill.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Skill name is required");
        }
        validateSkillNameByParamChecker(skill.getName());
        String skillName = skill.getName();
        // Step 2: Skip if already exists, don't overwrite user-customized content
        if (resourceManager.findMeta(namespaceId, skillName, RESOURCE_TYPE_SKILL) != null) {
            LOGGER.info("Skip built-in skill bootstrap because skill already exists: {}",
                skillName);
            return;
        }
        
        // Step 3: Write to storage (unlike upload, bootstrap skips draft workflow and writes directly)
        String version = resolveFinalUploadVersion(namespaceId, skillName,
            resolveUploadVersion(skill.getSkillMd(), null, null));
        // Normalize frontmatter before writing (bootstrap = first create)
        SkillRequestUtil.normalizeSkillFrontmatter(skill, skillName, version, true);
        List<String> files = writeSkillToStorage(namespaceId, skill, version);
        
        // Step 4: Insert meta + version rows with status directly set to online (published)
        String storageJson = buildStorageJson(namespaceId, skillName, version, files);
        resourceManager.insertBootstrapMeta(namespaceId, skillName, RESOURCE_TYPE_SKILL,
            skill.getDescription(), null, DEFAULT_AUTHOR, from, version, storageJson);
        
        // Step 5: Initialize index manifest for client discovery
        SkillIndexManifest manifest = new SkillIndexManifest();
        Map<String, String> manifestLabels = new HashMap<>(4);
        manifestLabels.put(AiResourceConstants.LABEL_LATEST, version);
        manifest.setLabels(manifestLabels);
        Map<String, List<String>> versions = new HashMap<>(4);
        versions.put(version, files);
        manifest.setVersions(versions);
        manifestService.write(namespaceId, skillName, manifest);
    }
    
    /**
     * Handle overwrite upload: if an editing draft exists, overwrite it in-place;
     * otherwise create a new draft with a bumped version.
     */
    private String overwriteUploadedSkill(String namespaceId, Skill skill, String uploadVersion,
        AiResource meta, String commitMsg)
        throws NacosException {
        String name = skill.getName();
        // No meta record = brand-new skill, create directly
        if (meta == null) {
            createDraftWithSkill(namespaceId, skill, uploadVersion, null, true, commitMsg);
            return name;
        }
        
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        // Existing editing draft: overwrite draft content in-place (no new version number)
        if (StringUtils.isNotBlank(editing)) {
            overwriteEditingDraft(namespaceId, skill, meta, editing, commitMsg);
            return name;
        }
        
        // No editing draft: assign new version number and create new draft
        String newVersion = resolveFinalUploadVersion(namespaceId, name, uploadVersion);
        createDraftWithSkill(namespaceId, skill, newVersion, meta, false, commitMsg);
        return name;
    }
    
    /**
     * Resolve the upload version.
     * Priority: SKILL.md YAML front-matter (version / metadata.version) -> meta.json in ZIP -> user-specified targetVersion -> default "0.0.1".
     * Rejects non-{@code x.y.z}/{@code vN} versions from any source so invalid values cannot reach storage.
     */
    private String resolveUploadVersion(String skillMd, byte[] zipBytes, String targetVersion)
        throws NacosApiException {
        String versionFromSkillMd = resolveVersionFromSkillMd(skillMd);
        if (StringUtils.isNotBlank(versionFromSkillMd)) {
            return validateUploadVersionFormat(versionFromSkillMd, "SKILL.md frontmatter");
        }
        String versionFromMetaJson = SkillZipParser.resolveVersionFromZip(zipBytes);
        if (StringUtils.isNotBlank(versionFromMetaJson)) {
            return validateUploadVersionFormat(versionFromMetaJson, "_meta.json");
        }
        if (StringUtils.isNotBlank(targetVersion)) {
            return validateUploadVersionFormat(targetVersion.trim(), "targetVersion parameter");
        }
        return DEFAULT_INITIAL_UPLOAD_VERSION;
    }
    
    private static String validateUploadVersionFormat(String version, String source)
        throws NacosApiException {
        if (!VersionUtils.isSupportedVersionFormat(version)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Invalid version from " + source + ": '" + version
                    + "', expected x.y.z or vN");
        }
        return version;
    }
    
    /**
     * Extract version string from SKILL.md YAML front-matter ("version" or "metadata.version" key).
     */
    private static String resolveVersionFromSkillMd(String skillMd) {
        if (StringUtils.isBlank(skillMd)) {
            return null;
        }
        Map<String, String> yaml = SkillZipParser.parseYamlFrontMatterFromMarkdown(skillMd);
        if (yaml == null || yaml.isEmpty()) {
            return null;
        }
        String version = yaml.get("version");
        if (StringUtils.isBlank(version)) {
            version = yaml.get("metadata.version");
        }
        return StringUtils.isBlank(version) ? null : version.trim();
    }
    
    /**
     * Ensure the candidate version doesn't collide with existing versions.
     * If collision occurs, bump to next patch version based on the max existing semver.
     */
    private String resolveFinalUploadVersion(String namespaceId, String skillName,
        String candidateVersion) throws NacosException {
        List<String> existingVersions =
            resourceManager.listExistingVersions(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (existingVersions.isEmpty()) {
            return candidateVersion;
        }
        String maxSemver = VersionUtils.maxSemver(existingVersions);
        boolean candidateExists = existingVersions.contains(candidateVersion);
        // Candidate version already taken -> bump patch based on current max semver
        if (candidateExists) {
            return VersionUtils.nextSemverPatch(maxSemver == null ? candidateVersion : maxSemver);
        }
        // Candidate version equals max semver (edge case not in list) -> bump patch as well
        if (maxSemver != null
            && VersionUtils.compareSemverVersion(candidateVersion, maxSemver) == 0) {
            return VersionUtils.nextSemverPatch(maxSemver);
        }
        return candidateVersion;
    }
    
    /**
     * Resolve the next draft version number. Tries semver patch bump first, falls back to legacy vN format.
     */
    private String resolveNextDraftVersion(String namespaceId, String skillName)
        throws NacosException {
        List<String> existingVersions =
            resourceManager.listExistingVersions(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        String maxSemver = VersionUtils.maxSemver(existingVersions);
        if (StringUtils.isNotBlank(maxSemver)) {
            return VersionUtils.nextSemverPatch(maxSemver);
        }
        int maxLegacy = VersionUtils.maxVNumber(existingVersions);
        if (maxLegacy > 0) {
            return "v" + (maxLegacy + 1);
        }
        return DEFAULT_INITIAL_UPLOAD_VERSION;
    }
    
    /**
     * Resolve a user-specified target version for draft creation.
     * Validates format, checks for conflicts with existing versions, and ensures it's greater than the base version.
     * Falls back to {@link #resolveNextDraftVersion} if targetVersion is blank.
     */
    private String resolveSpecifiedDraftVersion(String namespaceId, String skillName,
        String targetVersion,
        String basedOnVersion, String baseVersion) throws NacosException {
        if (StringUtils.isBlank(targetVersion)) {
            return resolveNextDraftVersion(namespaceId, skillName);
        }
        String candidate = targetVersion.trim();
        if (!VersionUtils.isSupportedVersionFormat(candidate)) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Invalid targetVersion format: " + candidate + ", expected x.y.z or vN");
        }
        List<String> existingVersions =
            resourceManager.listExistingVersions(namespaceId, skillName, RESOURCE_TYPE_SKILL);
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
     * Overwrite an existing editing draft's storage content and update meta description.
     */
    private void overwriteEditingDraft(String namespaceId, Skill skill, AiResource meta,
        String editing, String commitMsg)
        throws NacosException {
        resourceManager.requireDraftVersion(namespaceId, skill.getName(), RESOURCE_TYPE_SKILL,
            editing);
        // Normalize frontmatter before writing (overwrite = existing skill, not first create)
        SkillRequestUtil.normalizeSkillFrontmatter(skill, skill.getName(), editing, false);
        List<String> files = writeSkillToStorage(namespaceId, skill, editing);
        String storageJson = buildStorageJson(namespaceId, skill.getName(), editing, files);
        if (StringUtils.isNotBlank(commitMsg)) {
            resourceManager.updateVersionStorageAndDesc(namespaceId, skill.getName(),
                RESOURCE_TYPE_SKILL, editing, storageJson, commitMsg);
        } else {
            resourceManager.updateVersionStorage(namespaceId, skill.getName(),
                RESOURCE_TYPE_SKILL, editing, storageJson);
        }
        resourceManager.bumpMetaDescription(namespaceId, meta, skill.getDescription());
    }
    
    /**
     * Get skill detail metadata including all version summaries, labels, and online count.
     */
    @Override
    public SkillMeta getSkillDetail(String namespaceId, String skillName) throws NacosException {
        // Step 1: Find meta and verify read permission
        AiResource meta = resourceManager.findMeta(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Skill not found: " + skillName);
        }
        resourceManager.ensureReadableOrNotFound(meta, "Skill not found: " + skillName);
        ResourceVersionInfo versionInfo = AiResourceManager.requireVersionInfo(meta);
        
        // Step 2: Load all version rows and assemble version summary list
        Page<AiResourceVersion> versionPage = resourceManager.listVersions(namespaceId, skillName,
            RESOURCE_TYPE_SKILL, null, 1, 200);
        List<SkillMeta.SkillVersionSummary> versionSummaries = new ArrayList<>();
        if (versionPage != null && versionPage.getPageItems() != null) {
            for (AiResourceVersion v : versionPage.getPageItems()) {
                if (v == null) {
                    continue;
                }
                SkillMeta.SkillVersionSummary summary = new SkillMeta.SkillVersionSummary();
                summary.setVersion(v.getVersion());
                summary.setStatus(v.getStatus());
                summary.setAuthor(v.getAuthor());
                summary.setCommitMsg(v.getDesc());
                summary.setCreateTime(v.getGmtCreate() == null ? null : v.getGmtCreate().getTime());
                summary.setUpdateTime(
                    v.getGmtModified() == null ? null : v.getGmtModified().getTime());
                summary.setPublishPipelineInfo(v.getPublishPipelineInfo());
                summary.setDownloadCount(v.getDownloadCount());
                versionSummaries.add(summary);
            }
        }
        
        // Step 3: Merge meta info and version list into SkillMeta detail response
        SkillMeta detail = new SkillMeta();
        detail.setNamespaceId(namespaceId);
        detail.setName(skillName);
        detail.setDescription(meta.getDesc());
        detail.setOwner(meta.getOwner());
        detail.setEnable(AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus()));
        detail.setBizTags(meta.getBizTags());
        detail.setFrom(meta.getFrom());
        detail.setEditingVersion(versionInfo.getEditingVersion());
        detail.setReviewingVersion(versionInfo.getReviewingVersion());
        detail.setLabels(versionInfo.getLabels());
        detail.setScope(AiResourceManager.resolveScope(meta));
        detail.setOnlineCnt(versionInfo.getOnlineCnt());
        detail
            .setUpdateTime(meta.getGmtModified() == null ? null : meta.getGmtModified().getTime());
        detail.setVersions(versionSummaries);
        detail.setDownloadCount(meta.getDownloadCount());
        return detail;
    }
    
    /**
     * Get the full content of a specific skill version by reading its files from storage.
     */
    @Override
    public Skill getSkillVersionDetail(String namespaceId, String skillName, String version)
        throws NacosException {
        AiResource meta = resourceManager.findMeta(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Skill not found: " + skillName);
        }
        resourceManager.ensureReadableOrNotFound(meta, "Skill not found: " + skillName);
        if (StringUtils.isBlank(version)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Version is required for skill version detail");
        }
        AiResourceVersion versionRow = resourceManager.findVersion(namespaceId, skillName,
            RESOURCE_TYPE_SKILL, version);
        if (versionRow == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Skill version not found: " + skillName + "@" + version);
        }
        return loadSkillFromStorage(namespaceId, skillName, version, versionRow.getStorage());
    }
    
    /**
     * Download a specific skill version (same as getVersionDetail but also publishes a download event for metrics).
     */
    @Override
    public Skill downloadSkillVersion(String namespaceId, String skillName, String version)
        throws NacosException {
        Skill skill = getSkillVersionDetail(namespaceId, skillName, version);
        NotifyCenter.publishEvent(
            new SkillDownloadEvent(namespaceId, skillName, RESOURCE_TYPE_SKILL, version));
        return skill;
    }
    
    /**
     * Delete a skill entirely. Order: index manifest -> meta + all version rows -> storage files.
     * Deleting index manifest first cuts off client discovery immediately.
     */
    @Override
    public void deleteSkill(String namespaceId, String skillName) throws NacosException {
        AiResource meta = resourceManager.findMeta(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            return;
        }
        VisibilityHelper.checkWritableResource(meta);
        
        // Delete in strict reverse order of creation (storage -> version -> meta -> index):
        // 1) index config first: cut off client discovery immediately
        manifestService.delete(namespaceId, skillName);
        
        // 2) meta, version rows, and storage files
        resourceManager.deleteResourceWithVersions(namespaceId, skillName, RESOURCE_TYPE_SKILL,
            v -> deleteSkillStorageForVersion(namespaceId, skillName, v.getVersion(),
                v.getStorage()));
    }
    
    @Override
    public Page<SkillSummary> listSkills(String namespaceId, String skillName, String search,
        int pageNo,
        int pageSize) throws NacosException {
        return listSkills(namespaceId, skillName, search, null, pageNo, pageSize);
    }
    
    @Override
    public Page<SkillSummary> listSkills(String namespaceId, String skillName, String search,
        String orderBy,
        int pageNo, int pageSize) throws NacosException {
        return listSkills(namespaceId, skillName, search, orderBy, null, null, pageNo, pageSize);
    }
    
    /**
     * List skills with optional name filter, ordering, owner/scope/bizTag filter, and pagination.
     * Supports both accurate and fuzzy name matching.
     */
    @Override
    public Page<SkillSummary> listSkills(String namespaceId, String skillName, String search,
        String orderBy,
        String owner, String scope, String bizTag, int pageNo, int pageSize) throws NacosException {
        // Step 1: Build name matching condition: exact match or fuzzy match (with wildcards)
        String nameLike = null;
        if (StringUtils.isNotBlank(skillName)) {
            if (Skills.SEARCH_ACCURATE.equalsIgnoreCase(search)) {
                nameLike = skillName;
            } else {
                nameLike = resourceManager.generateLikeArgument(
                    Constants.ALL_PATTERN + skillName + Constants.ALL_PATTERN);
            }
        }
        
        // Step 2: Assemble query conditions (with visibility filtering) and execute paginated query
        QueryCondition queryCondition =
            resourceManager.buildQueryCondition(namespaceId, RESOURCE_TYPE_SKILL, nameLike, null,
                VisibilityConstants.ACTION_READ);
        queryCondition.setOrderBy(orderBy);
        if (StringUtils.isNotBlank(owner)) {
            queryCondition.setOwner(owner);
        }
        if (StringUtils.isNotBlank(scope)) {
            queryCondition.setScope(scope);
        }
        if (StringUtils.isNotBlank(bizTag)) {
            queryCondition.setBizTagsLike(
                resourceManager
                    .generateLikeArgument(Constants.ALL_PATTERN + bizTag + Constants.ALL_PATTERN));
        }
        if (queryCondition.isAlwaysEmpty()) {
            return AiResourceManager.buildEmptyPage(pageNo);
        }
        Page<AiResource> metaPage = resourceManager.listMeta(queryCondition, pageNo, pageSize);
        // Step 3: Convert meta rows to SkillSummary (with version labels, editing/reviewing status, etc.)
        List<AiResource> filtered =
            metaPage == null || metaPage.getPageItems() == null ? new ArrayList<>()
                : metaPage.getPageItems();
        List<SkillSummary> items = new ArrayList<>();
        for (AiResource meta : filtered) {
            if (meta == null) {
                continue;
            }
            ResourceVersionInfo versionInfo =
                AiResourceManager.parseVersionInfo(meta.getVersionInfo());
            SkillSummary item = new SkillSummary();
            item.setNamespaceId(namespaceId);
            item.setName(meta.getName());
            item.setDescription(meta.getDesc());
            item.setOwner(meta.getOwner());
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
        
        return AiResourceManager.buildPageResult(items, metaPage, pageNo);
    }
    
    /**
     * Create a new draft version for an existing or brand-new skill.
     *
     * <p>For brand-new skills, initialContent (skillCard) is required.
     * For existing skills, if basedOnVersion is specified, copies content from that version;
     * otherwise resolves the latest/base version to fork from. Ensures no other working version exists.</p>
     *
     * @return the newly created draft version string
     */
    @Override
    public String createDraft(String namespaceId, String name, String basedOnVersion,
        String targetVersion,
        Skill initialContent, String commitMsg)
        throws NacosException {
        AiResource meta = resourceManager.findMeta(namespaceId, name, RESOURCE_TYPE_SKILL);
        
        // ---- Case A: Brand-new skill (meta does not exist) ----
        if (meta == null) {
            // Brand-new skill does not support basedOnVersion (no existing version to fork from)
            if (StringUtils.isNotBlank(basedOnVersion)) {
                throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + name
                        + ", cannot use basedOnVersion for a brand-new skill");
            }
            // Brand-new skill requires initial content
            if (initialContent == null) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "skillCard is required when creating a brand-new skill draft");
            }
            String version = StringUtils.isBlank(targetVersion)
                ? resolveFinalUploadVersion(namespaceId, name,
                    resolveUploadVersion(initialContent.getSkillMd(), null, null))
                : resolveSpecifiedDraftVersion(namespaceId, name, targetVersion, null, null);
            createDraftWithSkill(namespaceId, initialContent, version, null, true, commitMsg);
            AiResourceTraceService.logSuccess(RESOURCE_TYPE_SKILL, name, version,
                AiResourceTraceService.OP_CREATE_DRAFT,
                VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
            return version;
        }
        
        // ---- Case B: Existing skill (fork from existing version or provide new content) ----
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        // Ensure no version is currently being edited or reviewed
        AiResourceManager.ensureNoWorkingVersion(info, "create draft");
        
        // Resolve base version: priority basedOnVersion param > latest label > max semver > max vN
        String base = resourceManager.resolveBaseVersion(namespaceId, name, RESOURCE_TYPE_SKILL,
            meta, basedOnVersion);
        String newVersion =
            resolveSpecifiedDraftVersion(namespaceId, name, targetVersion, basedOnVersion, base);
        
        if (StringUtils.isBlank(base)) {
            // No existing version to fork from -> initial content is required
            if (initialContent == null) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_MISSING,
                    "skillCard is required when no published version exists to fork from");
            }
            createDraftWithSkill(namespaceId, initialContent, newVersion, meta, false, commitMsg);
        } else {
            // Forking from existing version -> initialContent must not be set (fork first, then use PUT /draft to edit)
            if (initialContent != null) {
                throw new NacosApiException(NacosException.INVALID_PARAM,
                    ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "skillCard must not be set when creating a draft from an existing version; omit it to fork, then use PUT /draft to edit");
            }
            // Step 1: Read content from base version and copy to new version's storage path
            AiResourceVersion baseVersionRow = resourceManager.findVersion(namespaceId, name,
                RESOURCE_TYPE_SKILL, base);
            Skill baseSkill = loadSkillFromStorage(namespaceId, name, base,
                baseVersionRow != null ? baseVersionRow.getStorage() : null);
            List<String> files = writeSkillToStorage(namespaceId, baseSkill, newVersion);
            
            // Step 2: Insert draft version row
            String currentUser = VisibilityHelper.resolveCurrentIdentity();
            String versionDesc = StringUtils.isNotBlank(commitMsg) ? commitMsg : "";
            resourceManager.insertVersionRow(namespaceId, name, RESOURCE_TYPE_SKILL,
                StringUtils.isBlank(currentUser) ? DEFAULT_AUTHOR : currentUser,
                AiResourceConstants.VERSION_STATUS_DRAFT, newVersion, versionDesc,
                buildStorageJson(namespaceId, name, newVersion, files));
            
            // Step 3: Update meta's editingVersion pointer
            info.setEditingVersion(newVersion);
            resourceManager.updateVersionInfoCas(namespaceId, meta, info);
        }
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_SKILL, name, newVersion,
            AiResourceTraceService.OP_CREATE_DRAFT,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
        return newVersion;
    }
    
    /**
     * Update the content of an existing editing draft. Validates required fields,
     * writes updated files to storage, and bumps the meta description.
     */
    @Override
    public void updateDraft(String namespaceId, Skill draftSkill, String commitMsg)
        throws NacosException {
        // Step 1: Validate parameters
        if (draftSkill == null || StringUtils.isBlank(draftSkill.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Skill name is required");
        }
        if (StringUtils.isBlank(draftSkill.getDescription())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                "Skill description is required");
        }
        SkillRequestUtil.validateSkillMarkdownBody("skillMd", draftSkill.getSkillMd());
        // Step 2: Confirm meta exists, has write permission, and has an editing draft
        String name = draftSkill.getName();
        AiResource meta = resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE_SKILL);
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        if (StringUtils.isBlank(editing)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "No editing draft exists for skill: " + name);
        }
        resourceManager.requireDraftVersion(namespaceId, name, RESOURCE_TYPE_SKILL, editing);
        
        // Normalize frontmatter before writing to storage (editing = existing skill, not first create)
        SkillRequestUtil.normalizeSkillFrontmatter(draftSkill, name, editing, false);
        
        // Step 3: Overwrite storage files with new content, update version row's storage JSON and meta description
        List<String> files = writeSkillToStorage(namespaceId, draftSkill, editing);
        String storageJson = buildStorageJson(namespaceId, name, editing, files);
        if (StringUtils.isNotBlank(commitMsg)) {
            resourceManager.updateVersionStorageAndDesc(namespaceId, name, RESOURCE_TYPE_SKILL,
                editing,
                storageJson, commitMsg);
        } else {
            resourceManager.updateVersionStorage(namespaceId, name, RESOURCE_TYPE_SKILL, editing,
                storageJson);
        }
        resourceManager.bumpMetaDescription(namespaceId, meta, draftSkill.getDescription());
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_SKILL, name, editing,
            AiResourceTraceService.OP_UPDATE_DRAFT,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
    }
    
    /**
     * Delete the current editing draft. Clears the editingVersion pointer in meta first,
     * then removes the version row and its storage files.
     */
    @Override
    public void deleteDraft(String namespaceId, String name) throws NacosException {
        resourceManager.doDeleteDraft(namespaceId, name, RESOURCE_TYPE_SKILL,
            v -> deleteSkillStorageForVersion(namespaceId, name, v.getVersion(),
                v.getStorage()));
    }
    
    /**
     * Submit a draft for review and publish.
     *
     * <p>Flow: resolve target version -> move status to "reviewing" ->
     * check if a publish pipeline is available. If pipeline is available, run it asynchronously;
     * otherwise publish directly.</p>
     */
    @Override
    public String submit(String namespaceId, String name, String version) throws NacosException {
        // Step 1: Verify meta exists and has write permission
        AiResource meta = resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE_SKILL);
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        
        // Step 2: Determine the target version to submit (prefer explicit version, otherwise infer from editing/reviewing)
        String target =
            resourceManager.resolveSubmitTarget(info, version, RESOURCE_TYPE_SKILL, name);
        
        AiResourceVersion v =
            resourceManager.findVersion(namespaceId, name, RESOURCE_TYPE_SKILL, target);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Skill version not found: " + name + "@" + target);
        }
        
        final String finalTarget = target;
        
        // Step 3: Move version status from draft to reviewing
        resourceManager.moveToReviewing(namespaceId, name, RESOURCE_TYPE_SKILL, finalTarget, meta,
            info);
        
        // Step 4: Build pipeline context (containing SKILL.md and all resource files)
        Skill skill = loadSkillFromStorage(namespaceId, name, finalTarget, v.getStorage());
        SkillPipelineContext ctx = new SkillPipelineContext();
        ctx.setNamespaceId(namespaceId);
        ctx.setResourceName(name);
        ctx.setVersion(finalTarget);
        ctx.setFiles(buildPipelineFiles(skill));
        
        // Step 5: Check if a publish pipeline is available
        if (!publishPipelineExecutor.isPipelineAvailable(ctx.getResourceType())) {
            // No pipeline available -> skip review and publish directly
            publish(namespaceId, name, finalTarget, true);
            return finalTarget;
        }
        
        // Step 6: Run pipeline asynchronously; fall back to direct publish if startup fails
        if (!resourceManager.runPipelineExecution(namespaceId, name, RESOURCE_TYPE_SKILL,
            finalTarget,
            ctx, publishPipelineExecutor,
            r -> onSkillPipelineComplete(namespaceId, name, finalTarget, r))) {
            publish(namespaceId, name, finalTarget, true);
        }
        
        return finalTarget;
    }
    
    private void onSkillPipelineComplete(String namespaceId, String name, String version,
        PipelineExecutionResult result) {
        resourceManager.onPipelineComplete(namespaceId, name, RESOURCE_TYPE_SKILL, version, result);
        if (result == null || result.getStatus() != PipelineExecutionStatus.APPROVED) {
            return;
        }
        if (!EnvUtil.getProperty(AUTO_PUBLISH_AFTER_REVIEW_ENABLED_KEY, Boolean.class, false)) {
            return;
        }
        try {
            publishApprovedBySystem(namespaceId, name, version, true);
        } catch (Throwable ex) {
            LOGGER.error("Failed to auto publish approved skill {}@{}", name, version, ex);
        }
    }
    
    private void publishApprovedBySystem(String namespaceId, String name, String version,
        boolean updateLatestLabel)
        throws NacosException {
        AiResourceVersion v =
            resourceManager.doSystemPublish(namespaceId, name, RESOURCE_TYPE_SKILL, version,
                updateLatestLabel);
        SkillIndexManifest manifest = manifestService.loadForUpdate(namespaceId, name);
        manifest.getVersions().put(version, parseStorageFiles(v.getStorage()));
        if (updateLatestLabel) {
            manifest.getLabels().put(AiResourceConstants.LABEL_LATEST, version);
        }
        manifestService.write(namespaceId, name, manifest);
    }
    
    /**
     * Publish a version: update version status to online and write the version into the index manifest.
     */
    @Override
    public void publish(String namespaceId, String name, String version, boolean updateLatestLabel)
        throws NacosException {
        // Step 1: Update version status to online, clear reviewing pointer in meta
        AiResourceVersion v =
            resourceManager.doPublish(namespaceId, name, RESOURCE_TYPE_SKILL, version,
                updateLatestLabel);
        
        // Step 2: Write version's file list to index manifest (for client discovery)
        SkillIndexManifest manifest = manifestService.loadForUpdate(namespaceId, name);
        manifest.getVersions().put(version, parseStorageFiles(v.getStorage()));
        if (updateLatestLabel) {
            manifest.getLabels().put(AiResourceConstants.LABEL_LATEST, version);
        }
        manifestService.write(namespaceId, name, manifest);
    }
    
    /**
     * Force-publish a version regardless of its current status. Same manifest update as {@link #publish}.
     */
    @Override
    public void forcePublish(String namespaceId, String name, String version,
        boolean updateLatestLabel) throws NacosException {
        AiResourceVersion v =
            resourceManager.doForcePublish(namespaceId, name, RESOURCE_TYPE_SKILL, version,
                updateLatestLabel);
        
        SkillIndexManifest manifest = manifestService.loadForUpdate(namespaceId, name);
        manifest.getVersions().put(version, parseStorageFiles(v.getStorage()));
        if (updateLatestLabel) {
            manifest.getLabels().put(AiResourceConstants.LABEL_LATEST, version);
        }
        manifestService.write(namespaceId, name, manifest);
    }
    
    @Override
    public void redraft(String namespaceId, String name, String version) throws NacosException {
        resourceManager.doRedraft(namespaceId, name, RESOURCE_TYPE_SKILL, version);
    }
    
    /**
     * Update version labels (e.g., "latest") for a skill. Syncs both meta versionInfo and index manifest.
     */
    @Override
    public void updateLabels(String namespaceId, String name, Map<String, String> labels)
        throws NacosException {
        resourceManager.validateAndUpdateLabels(namespaceId, name, RESOURCE_TYPE_SKILL, labels);
        
        SkillIndexManifest manifest = manifestService.query(namespaceId, name);
        if (manifest != null) {
            manifest.setLabels(labels == null ? new HashMap<>(4) : new LinkedHashMap<>(labels));
            manifestService.write(namespaceId, name, manifest);
        }
    }
    
    /**
     * Update business tags on the skill meta.
     */
    @Override
    public void updateBizTags(String namespaceId, String name, String bizTags)
        throws NacosException {
        AiResource meta = resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE_SKILL);
        VisibilityHelper.checkWritableResource(meta);
        resourceManager.updateBizTagsCas(namespaceId, meta, bizTags);
        AiResourceTraceService.logSuccess(RESOURCE_TYPE_SKILL, name, null,
            AiResourceTraceService.OP_UPDATE_BIZ_TAGS,
            VisibilityHelper.resolveCurrentIdentity(), VisibilityHelper.resolveClientIp());
    }
    
    /**
     * Toggle online/offline status at either skill scope (enable/disable the entire skill)
     * or version scope (toggle a specific version's online status).
     *
     * <p>Skill-scope online: rebuilds the index manifest from all online versions.
     * Skill-scope offline: deletes the index manifest entirely.
     * Version-scope: adds/removes the specific version entry from the manifest.</p>
     */
    @Override
    public void changeOnlineStatus(String namespaceId, String name, String scope, String version,
        boolean online)
        throws NacosException {
        AiResource meta = resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE_SKILL);
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        
        // Determine operation scope: skill scope (enable/disable entire skill) vs version scope (toggle single version)
        boolean skillScope = SCOPE_SKILL.equalsIgnoreCase(scope) || StringUtils.isBlank(version);
        if (skillScope) {
            // Enable/disable entire skill: update meta status
            resourceManager.metaEnableDisable(namespaceId, meta, online);
            if (online) {
                // On re-enable: rebuild index manifest from all online versions in DB
                refreshSkillIndexManifest(namespaceId, name);
            } else {
                // On disable: delete index manifest so clients can no longer discover it
                manifestService.delete(namespaceId, name);
            }
            return;
        }
        
        // Single version toggle: switch version status and sync index manifest
        AiResourceVersion v =
            resourceManager.toggleVersionOnlineStatus(namespaceId, meta, info, version, online);
        if (v == null) {
            return;
        }
        
        if (online) {
            // Going online: add this version's file list to manifest
            List<String> files = parseStorageFiles(v.getStorage());
            if (files != null && !files.isEmpty()) {
                SkillIndexManifest manifest = manifestService.loadForUpdate(namespaceId, name);
                manifest.getVersions().put(version, files);
                manifestService.write(namespaceId, name, manifest);
            }
        } else {
            // Going offline: remove this version from manifest
            SkillIndexManifest manifest = manifestService.query(namespaceId, name);
            if (manifest != null && manifest.getVersions() != null) {
                manifest.getVersions().remove(version);
                manifestService.write(namespaceId, name, manifest);
            }
        }
    }
    
    /**
     * Update the visibility scope of a skill.
     */
    @Override
    public void updateScope(String namespaceId, String name, String scope) throws NacosException {
        resourceManager.doUpdateScope(namespaceId, name, RESOURCE_TYPE_SKILL, scope);
    }
    
    /**
     * Search skills by keyword (fuzzy name match). Only returns enabled skills with at least one online version.
     */
    @Override
    public Page<SkillBasicInfo> searchSkills(String namespaceId, String keyword, int pageNo,
        int pageSize)
        throws NacosException {
        // Build fuzzy query condition
        String nameLike = StringUtils.isBlank(keyword) ? null
            : resourceManager
                .generateLikeArgument(Constants.ALL_PATTERN + keyword + Constants.ALL_PATTERN);
        QueryCondition queryCondition =
            resourceManager.buildQueryCondition(namespaceId, RESOURCE_TYPE_SKILL, nameLike, null,
                VisibilityConstants.ACTION_READ);
        if (queryCondition.isAlwaysEmpty()) {
            return AiResourceManager.buildEmptyPage(pageNo);
        }
        Page<AiResource> metaPage = resourceManager.listMeta(queryCondition, pageNo, pageSize);
        List<AiResource> filtered =
            metaPage == null || metaPage.getPageItems() == null ? new ArrayList<>()
                : metaPage.getPageItems();
        List<SkillBasicInfo> items = new ArrayList<>();
        for (AiResource meta : filtered) {
            if (meta == null) {
                continue;
            }
            // Only return enabled skills with at least one online version (for client-side search)
            if (!AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus())) {
                continue;
            }
            ResourceVersionInfo info = AiResourceManager.parseVersionInfo(meta.getVersionInfo());
            if (info == null || info.getOnlineCnt() == null || info.getOnlineCnt() <= 0) {
                continue;
            }
            SkillBasicInfo basicInfo = new SkillBasicInfo();
            basicInfo.setName(meta.getName());
            basicInfo.setDescription(meta.getDesc());
            items.add(basicInfo);
        }
        return AiResourceManager.buildPageResult(items, metaPage, pageNo);
    }
    
    /**
     * Query a skill for client consumption. Resolves the target version via explicit version, label, or manifest,
     * loads the skill content from the index manifest's file list, and publishes a download event.
     */
    @Override
    public Skill querySkill(String namespaceId, String name, String version, String label)
        throws NacosException {
        // Step 1: Verify meta exists and is readable
        AiResource meta = resourceManager.findMeta(namespaceId, name, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Skill not found: " + name);
        }
        resourceManager.ensureReadableOrNotFound(meta, "Skill not found: " + name);
        // Step 2: Find available versions from index manifest
        SkillIndexManifest manifest = manifestService.query(namespaceId, name);
        if (manifest == null || manifest.getVersions() == null
            || manifest.getVersions().isEmpty()) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Skill not found: " + name);
        }
        
        // Step 3: Resolve actual version from version/label params (labels like "latest" are looked up in manifest)
        String resolved = SkillIndexManifestService.resolveVersion(manifest, version, label);
        if (StringUtils.isBlank(resolved)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Skill version not found: " + name);
        }
        
        // Step 4: Get file list from manifest and read storage content
        List<String> files = manifest.getVersions().get(resolved);
        if (files == null || files.isEmpty()) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "Skill version not found: " + name + "@" + resolved);
        }
        
        Skill skill = loadSkillFromFiles(namespaceId, name, resolved, files);
        // Step 5: Publish download event for download count tracking
        NotifyCenter.publishEvent(
            new SkillDownloadEvent(namespaceId, name, RESOURCE_TYPE_SKILL, resolved));
        return skill;
    }
    
    // ========== Private methods ==========
    
    /**
     * Core draft creation logic shared by upload, overwrite, and createDraft flows.
     * Steps: 1) write all resource files to storage, 2) insert a draft version row,
     * 3) create or update the meta row with editingVersion pointer.
     */
    private void createDraftWithSkill(String namespaceId, Skill skill, String version,
        AiResource existedMeta,
        boolean isNewSkill) throws NacosException {
        createDraftWithSkill(namespaceId, skill, version, existedMeta, isNewSkill, null);
    }
    
    private void createDraftWithSkill(String namespaceId, Skill skill, String version,
        AiResource existedMeta,
        boolean isNewSkill, String commitMsg) throws NacosException {
        String skillName = skill.getName();
        String currentUser = VisibilityHelper.resolveCurrentIdentity();
        
        // Normalize frontmatter before writing to storage
        SkillRequestUtil.normalizeSkillFrontmatter(skill, skillName, version, existedMeta == null);
        
        // 1) write all resources (including SKILL.md) to storage
        List<String> files = writeSkillToStorage(namespaceId, skill, version);
        
        // 2) insert draft version row
        String versionDesc = StringUtils.isNotBlank(commitMsg) ? commitMsg : "";
        resourceManager.insertVersionRow(namespaceId, skillName, RESOURCE_TYPE_SKILL,
            StringUtils.isBlank(currentUser) ? DEFAULT_AUTHOR : currentUser,
            AiResourceConstants.VERSION_STATUS_DRAFT, version, versionDesc,
            buildStorageJson(namespaceId, skillName, version, files));
        
        // 3) create or update meta for editingVersion
        resourceManager.initOrUpdateMetaForDraft(namespaceId, skillName, RESOURCE_TYPE_SKILL,
            skill.getDescription(), null, version, existedMeta, isNewSkill);
    }
    
    /**
     * Resolve the storage provider from system config. Defaults to "nacos_config".
     */
    private static String resolveSkillStorageProvider() {
        String provider =
            EnvUtil.getProperty(SKILL_STORAGE_PROVIDER_CONFIG_KEY, STORAGE_PROVIDER_NACOS_CONFIG);
        return StringUtils.isBlank(provider) ? STORAGE_PROVIDER_NACOS_CONFIG : provider.trim();
    }
    
    /**
     * Build storage metadata JSON for version row (provider + scope + file list).
     */
    private static String buildStorageJson(String namespaceId, String skillName, String version,
        List<String> files) {
        Map<String, Object> json = new HashMap<>(4);
        json.put("provider", resolveSkillStorageProvider());
        json.put("scope", namespaceId + ":" + skillName + ":" + version);
        json.put("files", files);
        return JacksonUtils.toJson(json);
    }
    
    /**
     * Parse the file path list from storage JSON.
     */
    @SuppressWarnings("unchecked")
    private static List<String> parseStorageFiles(String storageJson) {
        if (StringUtils.isBlank(storageJson)) {
            return null;
        }
        try {
            Map<String, Object> map = JacksonUtils.toObj(storageJson, Map.class);
            Object files = map.get("files");
            if (files instanceof List) {
                return (List<String>) files;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
    
    /**
     * Refresh skill index manifest from current DB state (used when skill is re-enabled).
     * Rebuilds manifest with all online versions and labels from meta.
     * Best-effort: failures are logged but not propagated.
     */
    private void refreshSkillIndexManifest(String namespaceId, String name) {
        try {
            AiResource meta = resourceManager.findMeta(namespaceId, name, RESOURCE_TYPE_SKILL);
            if (meta == null) {
                return;
            }
            ResourceVersionInfo vInfo = AiResourceManager.parseVersionInfo(meta.getVersionInfo());
            
            // Step 1: Copy labels from meta (e.g., latest tag)
            SkillIndexManifest manifest = new SkillIndexManifest();
            manifest.setLabels(vInfo != null && vInfo.getLabels() != null
                ? new HashMap<>(vInfo.getLabels()) : new HashMap<>(4));
            manifest.setVersions(new HashMap<>(4));
            
            // Step 2: Iterate all version rows, collect online versions and their file lists
            Page<AiResourceVersion> versionPage = resourceManager.listVersions(namespaceId, name,
                RESOURCE_TYPE_SKILL, null, 1, 200);
            if (versionPage != null && versionPage.getPageItems() != null) {
                for (AiResourceVersion v : versionPage.getPageItems()) {
                    if (v == null || !AiResourceConstants.VERSION_STATUS_ONLINE
                        .equalsIgnoreCase(v.getStatus())) {
                        continue;
                    }
                    List<String> files = parseStorageFiles(v.getStorage());
                    if (files != null && !files.isEmpty()) {
                        manifest.getVersions().put(v.getVersion(), files);
                    }
                }
            }
            
            // Step 3: Only write manifest when online versions exist
            if (!manifest.getVersions().isEmpty()) {
                manifestService.write(namespaceId, name, manifest);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to refresh skill index manifest for {}: {}", name, e.getMessage());
        }
    }
    
    /**
     * Validate skill name using the configured parameter checker (e.g., character set, length).
     */
    private void validateSkillNameByParamChecker(String skillName) throws NacosApiException {
        ParamInfo paramInfo = new ParamInfo();
        paramInfo.setSkillName(skillName);
        String checkerType = ServerParamCheckConfig.getInstance().getActiveParamChecker();
        AbstractParamChecker paramChecker =
            ParamCheckerManager.getInstance().getParamChecker(checkerType);
        ParamCheckResponse response =
            paramChecker.checkParamInfoList(Collections.singletonList(paramInfo));
        if (!response.isSuccess()) {
            throw new NacosApiException(NacosException.INVALID_PARAM,
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                response.getMessage());
        }
    }
    
    /**
     * Write all skill files to storage as raw file contents (no JSON envelope).
     *
     * @return list of stored file paths (for use in buildStorageJson)
     */
    private List<String> writeSkillToStorage(String namespaceId, Skill skill, String version)
        throws NacosException {
        String provider = resolveSkillStorageProvider();
        String skillName = skill.getName();
        List<String> files = new ArrayList<>();
        
        // Save concurrently to reduce latency when skill has multiple resource files.
        Executor executor = ExecutorUtils.getSkillStorageIoExecutor();
        List<CompletableFuture<Void>> tasks = new ArrayList<>();
        
        // 1) Store SKILL.md as raw markdown content
        String mdPath = SKILL_MD_RESOURCE_NAME;
        byte[] mdBytes =
            (skill.getSkillMd() == null ? "" : skill.getSkillMd()).getBytes(StandardCharsets.UTF_8);
        StorageKey mdKey =
            NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId, skillName, version,
                mdPath);
        files.add(mdPath);
        tasks.add(CompletableFuture.runAsync(() -> {
            try {
                storageRouter.route(mdKey).save(mdKey, mdBytes);
            } catch (NacosException e) {
                throw new CompletionException(e);
            }
        }, executor));
        
        // 2) Store each resource file as raw content
        if (skill.getResource() != null && !skill.getResource().isEmpty()) {
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                SkillResource resource = entry.getValue();
                if (resource == null) {
                    continue;
                }
                String path = buildResourceFilePath(resource);
                byte[] content = (resource.getContent() == null ? "" : resource.getContent())
                    .getBytes(StandardCharsets.UTF_8);
                StorageKey resourceKey =
                    NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId, skillName,
                        version, path);
                files.add(path);
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
        
        return files;
    }
    
    /**
     * Build pipeline file representations from a Skill for use by the publish pipeline executor.
     * Includes SKILL.md and all resource files.
     */
    private static List<ResourceFileContent> buildPipelineFiles(Skill skill) {
        List<ResourceFileContent> files = new ArrayList<>();
        ResourceFileContent skillMd = new ResourceFileContent();
        skillMd.setFilePath("SKILL.md");
        skillMd.setContent(skill.getSkillMd());
        files.add(skillMd);
        if (skill.getResource() != null && !skill.getResource().isEmpty()) {
            for (SkillResource r : skill.getResource().values()) {
                if (r == null) {
                    continue;
                }
                ResourceFileContent f = new ResourceFileContent();
                f.setFilePath(buildResourcePath(skill.getName(), r));
                f.setContent(r.getContent());
                files.add(f);
            }
        }
        return files;
    }
    
    private static String buildResourcePath(String skillName, SkillResource r) {
        String type = r.getType();
        if (StringUtils.isBlank(type)) {
            return skillName + "/" + r.getName();
        }
        return skillName + "/" + type + "/" + r.getName();
    }
    
    /**
     * Load skill from storage by reading all resource files listed in storageJson.
     * SKILL.md content provides name/description and markdown body; others populate the resource map.
     */
    private Skill loadSkillFromStorage(String namespaceId, String skillName, String version,
        String storageJson)
        throws NacosException {
        List<String> files = parseStorageFiles(storageJson);
        if (files == null || files.isEmpty()) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                "No files found in storage for skill: " + skillName + "@" + version);
        }
        return loadSkillFromFiles(namespaceId, skillName, version, files);
    }
    
    /**
     * Load skill from storage by reading all files from the given file list.
     * SKILL.md content provides name/description and markdown body; others populate the resource map.
     */
    private Skill loadSkillFromFiles(String namespaceId, String skillName, String version,
        List<String> files)
        throws NacosException {
        String provider = resolveSkillStorageProvider();
        Skill skill = new Skill();
        skill.setNamespaceId(namespaceId);
        Map<String, SkillResource> resourceMap = new HashMap<>(files.size());
        
        // Read storage files one by one, handle differently based on filename:
        for (String filePath : files) {
            StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId,
                skillName, version,
                filePath);
            byte[] bytes = storageRouter.route(key).get(key);
            if (bytes == null) {
                continue;
            }
            String fileContent = new String(bytes, StandardCharsets.UTF_8);
            if (SKILL_MD_RESOURCE_NAME.equals(filePath)) {
                // SKILL.md: set as markdown body, extract name/description from YAML front-matter
                skill.setSkillMd(fileContent);
                parseSkillBaseInfoFromSkillMd(fileContent, skill);
            } else {
                // Other files: add as resource files to the resource map
                SkillResource resource = buildResourceFromStoredFile(filePath, fileContent);
                String resourceId =
                    SkillUtils.generateResourceId(resource.getType(), resource.getName());
                resourceMap.put(resourceId, resource);
            }
        }
        
        skill.setResource(resourceMap);
        return skill;
    }
    
    /**
     * Build a storage-relative file path for a resource: "{type}/{name}" or just "{name}" if type is blank.
     */
    private static String buildResourceFilePath(SkillResource resource) {
        String type = resource.getType();
        if (StringUtils.isBlank(type)) {
            return resource.getName();
        }
        return type + "/" + resource.getName();
    }
    
    /**
     * Reconstruct a SkillResource from a stored file path and its raw content.
     * Splits the path into type/name and detects binary encoding.
     */
    private static SkillResource buildResourceFromStoredFile(String filePath, String content) {
        SkillResource resource = new SkillResource();
        int idx = filePath.lastIndexOf('/');
        String type = idx > 0 ? filePath.substring(0, idx) : "";
        String name = idx >= 0 ? filePath.substring(idx + 1) : filePath;
        resource.setType(type);
        resource.setName(name);
        resource.setContent(content);
        if (isBinaryResource(name)) {
            Map<String, Object> metadata = new HashMap<>(2);
            metadata.put(SkillZipParser.METADATA_ENCODING, SkillZipParser.METADATA_ENCODING_BASE64);
            resource.setMetadata(metadata);
        }
        return resource;
    }
    
    private static boolean isBinaryResource(String fileName) {
        return SkillZipParser.isBinaryResource(fileName);
    }
    
    /**
     * Extract name and description from SKILL.md YAML front-matter and populate the Skill object.
     */
    private static void parseSkillBaseInfoFromSkillMd(String markdown, Skill skill) {
        if (StringUtils.isBlank(markdown) || skill == null) {
            return;
        }
        Map<String, String> frontMatter = SkillZipParser.parseYamlFrontMatterFromMarkdown(markdown);
        if (frontMatter.containsKey("name")) {
            skill.setName(frontMatter.get("name"));
        }
        if (frontMatter.containsKey("description")) {
            skill.setDescription(frontMatter.get("description"));
        }
    }
    
    /**
     * Delete all storage files for a given skill version using the file list from storageJson.
     */
    private void deleteSkillStorageForVersion(String namespaceId, String skillName, String version,
        String storageJson)
        throws NacosException {
        List<String> files = parseStorageFiles(storageJson);
        if (files == null || files.isEmpty()) {
            return;
        }
        String provider = resolveSkillStorageProvider();
        for (String filePath : files) {
            StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId,
                skillName, version,
                filePath);
            storageRouter.route(key).delete(key);
        }
    }
    
}
