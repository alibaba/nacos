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
import com.alibaba.nacos.ai.model.skills.SkillIndexManifest;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.ai.utils.ExecutorUtils;
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
    private static final String SKILL_STORAGE_PROVIDER_CONFIG_KEY = "nacos.ai.skill.storage.provider";

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

    @Override
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes, boolean overwrite) throws NacosException {
        return uploadSkillFromZip(namespaceId, zipBytes, null, overwrite);
    }

    @Override
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes, String zipFileName, boolean overwrite)
            throws NacosException {
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, namespaceId);
        if (skill == null || StringUtils.isBlank(skill.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING, "Skill name is required");
        }
        validateSkillNameByParamChecker(skill.getName());
        String uploadVersion = resolveUploadVersion(skill.getSkillMd(), zipBytes);
        String name = skill.getName();
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL);
        if (overwrite) {
            return overwriteUploadedSkill(namespaceId, skill, uploadVersion, meta);
        }
        if (meta == null) {
            createDraftWithSkill(namespaceId, skill, uploadVersion, null, true);
            return name;
        }

        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        AiResourceManager.ensureNoWorkingVersion(info, "upload");

        String newVersion = resolveFinalUploadVersion(namespaceId, name, uploadVersion);
        createDraftWithSkill(namespaceId, skill, newVersion, meta, false);
        return name;
    }

    @Override
    public void bootstrapSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        bootstrapSkillFromZip(namespaceId, zipBytes, null);
    }

    @Override
    public void bootstrapSkillFromZip(String namespaceId, byte[] zipBytes, String from) throws NacosException {
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, namespaceId);
        if (skill == null || StringUtils.isBlank(skill.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING, "Skill name is required");
        }
        validateSkillNameByParamChecker(skill.getName());
        String skillName = skill.getName();
        if (aiResourcePersistService.find(namespaceId, skillName, RESOURCE_TYPE_SKILL) != null) {
            LOGGER.info("Skip built-in skill bootstrap because skill already exists: {}", skillName);
            return;
        }

        String version = resolveFinalUploadVersion(namespaceId, skillName, resolveUploadVersion(skill.getSkillMd(), null));
        List<String> files = writeSkillToStorage(namespaceId, skill, version);

        String storageJson = buildStorageJson(namespaceId, skillName, version, files);
        resourceManager.insertBootstrapMeta(namespaceId, skillName, RESOURCE_TYPE_SKILL,
                skill.getDescription(), null, DEFAULT_AUTHOR, from, version, storageJson);

        SkillIndexManifest manifest = new SkillIndexManifest();
        Map<String, String> manifestLabels = new HashMap<>(4);
        manifestLabels.put(AiResourceConstants.LABEL_LATEST, version);
        manifest.setLabels(manifestLabels);
        Map<String, List<String>> versions = new HashMap<>(4);
        versions.put(version, files);
        manifest.setVersions(versions);
        manifestService.write(namespaceId, skillName, manifest);
    }

    private String overwriteUploadedSkill(String namespaceId, Skill skill, String uploadVersion, AiResource meta)
            throws NacosException {
        String name = skill.getName();
        if (meta == null) {
            createDraftWithSkill(namespaceId, skill, uploadVersion, null, true);
            return name;
        }

        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        if (StringUtils.isNotBlank(editing)) {
            overwriteEditingDraft(namespaceId, skill, meta, editing);
            return name;
        }

        String newVersion = resolveFinalUploadVersion(namespaceId, name, uploadVersion);
        createDraftWithSkill(namespaceId, skill, newVersion, meta, false);
        return name;
    }

    private String resolveUploadVersion(String skillMd, byte[] zipBytes) {
        String versionFromSkillMd = resolveVersionFromSkillMd(skillMd);
        if (StringUtils.isNotBlank(versionFromSkillMd)) {
            return versionFromSkillMd;
        }
        String versionFromMetaJson = SkillZipParser.resolveVersionFromZip(zipBytes);
        if (StringUtils.isNotBlank(versionFromMetaJson)) {
            return versionFromMetaJson;
        }
        return DEFAULT_INITIAL_UPLOAD_VERSION;
    }

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

    private String resolveFinalUploadVersion(String namespaceId, String skillName, String candidateVersion) throws NacosException {
        List<String> existingVersions = resourceManager.listExistingVersions(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (existingVersions.isEmpty()) {
            return candidateVersion;
        }
        String maxSemver = VersionUtils.maxSemver(existingVersions);
        boolean candidateExists = existingVersions.contains(candidateVersion);
        if (candidateExists) {
            return VersionUtils.nextSemverPatch(maxSemver == null ? candidateVersion : maxSemver);
        }
        if (maxSemver != null && VersionUtils.compareSemverVersion(candidateVersion, maxSemver) == 0) {
            return VersionUtils.nextSemverPatch(maxSemver);
        }
        return candidateVersion;
    }

    private String resolveNextDraftVersion(String namespaceId, String skillName) throws NacosException {
        List<String> existingVersions = resourceManager.listExistingVersions(namespaceId, skillName, RESOURCE_TYPE_SKILL);
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

    private String resolveSpecifiedDraftVersion(String namespaceId, String skillName, String targetVersion,
            String basedOnVersion, String baseVersion) throws NacosException {
        if (StringUtils.isBlank(targetVersion)) {
            return resolveNextDraftVersion(namespaceId, skillName);
        }
        String candidate = targetVersion.trim();
        if (!VersionUtils.isSupportedVersionFormat(candidate)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Invalid targetVersion format: " + candidate + ", expected x.y.z or vN");
        }
        List<String> existingVersions = resourceManager.listExistingVersions(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (existingVersions.contains(candidate)) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    "targetVersion already exists: " + candidate);
        }
        if (StringUtils.isNotBlank(basedOnVersion) && StringUtils.isNotBlank(baseVersion)) {
            Boolean isGreater = VersionUtils.isGreaterVersion(candidate, baseVersion);
            if (isGreater != null && !isGreater) {
                throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "targetVersion must be greater than basedOnVersion, basedOnVersion=" + baseVersion
                                + ", targetVersion=" + candidate);
            }
        }
        return candidate;
    }

    private void overwriteEditingDraft(String namespaceId, Skill skill, AiResource meta, String editing)
            throws NacosException {
        resourceManager.requireDraftVersion(namespaceId, skill.getName(), RESOURCE_TYPE_SKILL, editing);
        List<String> files = writeSkillToStorage(namespaceId, skill, editing);
        aiResourceVersionPersistService.updateStorage(namespaceId, skill.getName(), RESOURCE_TYPE_SKILL, editing,
                buildStorageJson(namespaceId, skill.getName(), editing, files));
        resourceManager.bumpMetaDescription(namespaceId, meta, skill.getDescription());
    }

    @Override
    public SkillMeta getSkillDetail(String namespaceId, String skillName) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + skillName);
        }
        resourceManager.ensureReadableOrNotFound(meta, "Skill not found: " + skillName);
        ResourceVersionInfo versionInfo = AiResourceManager.requireVersionInfo(meta);

        // Load all version summaries
        Page<AiResourceVersion> versionPage = aiResourceVersionPersistService.listAll(namespaceId, skillName, 1, 200);
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
                summary.setDescription(v.getDesc());
                summary.setCreateTime(v.getGmtCreate() == null ? null : v.getGmtCreate().getTime());
                summary.setUpdateTime(v.getGmtModified() == null ? null : v.getGmtModified().getTime());
                summary.setPublishPipelineInfo(v.getPublishPipelineInfo());
                summary.setDownloadCount(v.getDownloadCount());
                versionSummaries.add(summary);
            }
        }

        SkillMeta detail = new SkillMeta();
        detail.setNamespaceId(namespaceId);
        detail.setName(skillName);
        detail.setDescription(meta.getDesc());
        detail.setEnable(AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus()));
        detail.setBizTags(meta.getBizTags());
        detail.setFrom(meta.getFrom());
        detail.setEditingVersion(versionInfo.getEditingVersion());
        detail.setReviewingVersion(versionInfo.getReviewingVersion());
        detail.setLabels(versionInfo.getLabels());
        detail.setScope(AiResourceManager.resolveScope(meta));
        detail.setOnlineCnt(versionInfo.getOnlineCnt());
        detail.setUpdateTime(meta.getGmtModified() == null ? null : meta.getGmtModified().getTime());
        detail.setVersions(versionSummaries);
        detail.setDownloadCount(meta.getDownloadCount());
        return detail;
    }

    @Override
    public Skill getSkillVersionDetail(String namespaceId, String skillName, String version) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + skillName);
        }
        resourceManager.ensureReadableOrNotFound(meta, "Skill not found: " + skillName);
        if (StringUtils.isBlank(version)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Version is required for skill version detail");
        }
        AiResourceVersion versionRow = aiResourceVersionPersistService.find(namespaceId, skillName,
                RESOURCE_TYPE_SKILL, version);
        if (versionRow == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill version not found: " + skillName + "@" + version);
        }
        return loadSkillFromStorage(namespaceId, skillName, version, versionRow.getStorage());
    }

    @Override
    public Skill downloadSkillVersion(String namespaceId, String skillName, String version) throws NacosException {
        Skill skill = getSkillVersionDetail(namespaceId, skillName, version);
        NotifyCenter.publishEvent(
                new SkillDownloadEvent(namespaceId, skillName, RESOURCE_TYPE_SKILL, version));
        return skill;
    }

    @Override
    public void deleteSkill(String namespaceId, String skillName) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            return;
        }
        VisibilityHelper.checkWritableResource(meta);

        // Delete in strict reverse order of creation (storage -> version -> meta -> index):
        // 1) index config first: cut off client discovery immediately
        manifestService.delete(namespaceId, skillName);

        // 2) meta, version rows, and storage files
        resourceManager.deleteResourceWithVersions(namespaceId, skillName, RESOURCE_TYPE_SKILL,
                v -> deleteSkillStorageForVersion(namespaceId, skillName, v.getVersion(), v.getStorage()));
    }

    @Override
    public Page<SkillSummary> listSkills(String namespaceId, String skillName, String search, int pageNo,
                                          int pageSize) throws NacosException {
        return listSkills(namespaceId, skillName, search, null, pageNo, pageSize);
    }

    @Override
    public Page<SkillSummary> listSkills(String namespaceId, String skillName, String search, String orderBy,
                                          int pageNo, int pageSize) throws NacosException {
        return listSkills(namespaceId, skillName, search, orderBy, null, null, pageNo, pageSize);
    }

    @Override
    public Page<SkillSummary> listSkills(String namespaceId, String skillName, String search, String orderBy,
            String owner, String scope, int pageNo, int pageSize) throws NacosException {
        String nameLike = null;
        if (StringUtils.isNotBlank(skillName)) {
            if (Skills.SEARCH_ACCURATE.equalsIgnoreCase(search)) {
                nameLike = skillName;
            } else {
                nameLike = aiResourcePersistService.generateLikeArgument(
                        Constants.ALL_PATTERN + skillName + Constants.ALL_PATTERN);
            }
        }

        QueryCondition queryCondition = resourceManager.buildQueryCondition(namespaceId, RESOURCE_TYPE_SKILL, nameLike, null,
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
        Page<AiResource> metaPage = aiResourcePersistService.list(queryCondition, pageNo, pageSize);
        List<AiResource> filtered = metaPage == null || metaPage.getPageItems() == null ? new ArrayList<>()
                : metaPage.getPageItems();
        List<SkillSummary> items = new ArrayList<>();
        for (AiResource meta : filtered) {
            if (meta == null) {
                continue;
            }
            ResourceVersionInfo versionInfo = AiResourceManager.parseVersionInfo(meta.getVersionInfo());
            SkillSummary item = new SkillSummary();
            item.setNamespaceId(namespaceId);
            item.setName(meta.getName());
            item.setDescription(meta.getDesc());
            item.setEnable(AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus()));
            item.setBizTags(meta.getBizTags());
            item.setFrom(meta.getFrom());
            item.setScope(AiResourceManager.resolveScope(meta));
            item.setUpdateTime(meta.getGmtModified() == null ? null : meta.getGmtModified().getTime());
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
    

    @Override
    public String createDraft(String namespaceId, String name, String basedOnVersion, String targetVersion,
            Skill initialContent)
            throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL);

        if (meta == null) {
            // Brand-new skill: no existing resource to check write permission against
            if (StringUtils.isNotBlank(basedOnVersion)) {
                throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        "Skill not found: " + name + ", cannot use basedOnVersion for a brand-new skill");
            }
            if (initialContent == null) {
                throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                        "skillCard is required when creating a brand-new skill draft");
            }
            String version = StringUtils.isBlank(targetVersion)
                    ? resolveFinalUploadVersion(namespaceId, name, resolveUploadVersion(initialContent.getSkillMd(), null))
                    : resolveSpecifiedDraftVersion(namespaceId, name, targetVersion, null, null);
            createDraftWithSkill(namespaceId, initialContent, version, null, true);
            return version;
        }

        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        AiResourceManager.ensureNoWorkingVersion(info, "create draft");

        // resolveBaseVersion: explicit param > latest label > max semver version > max legacy vN version
        // null means no version exists yet
        String base = resourceManager.resolveBaseVersion(namespaceId, name, RESOURCE_TYPE_SKILL, meta, basedOnVersion);
        String newVersion = resolveSpecifiedDraftVersion(namespaceId, name, targetVersion, basedOnVersion, base);

        if (StringUtils.isBlank(base)) {
            if (initialContent == null) {
                throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                        "skillCard is required when no published version exists to fork from");
            }
            createDraftWithSkill(namespaceId, initialContent, newVersion, meta, false);
        } else {
            if (initialContent != null) {
                throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "skillCard must not be set when creating a draft from an existing version; omit it to fork, then use PUT /draft to edit");
            }
            // Copy storage content from base version
            AiResourceVersion baseVersionRow = aiResourceVersionPersistService.find(namespaceId, name,
                    RESOURCE_TYPE_SKILL, base);
            Skill baseSkill = loadSkillFromStorage(namespaceId, name, base,
                    baseVersionRow != null ? baseVersionRow.getStorage() : null);
            List<String> files = writeSkillToStorage(namespaceId, baseSkill, newVersion);

            String currentUser = VisibilityHelper.resolveCurrentIdentity();
            resourceManager.insertVersionRow(namespaceId, name, RESOURCE_TYPE_SKILL,
                    StringUtils.isBlank(currentUser) ? DEFAULT_AUTHOR : currentUser,
                    AiResourceConstants.VERSION_STATUS_DRAFT, newVersion, baseSkill.getDescription(),
                    buildStorageJson(namespaceId, name, newVersion, files));

            info.setEditingVersion(newVersion);
            resourceManager.updateVersionInfoCas(namespaceId, meta, info);
        }
        return newVersion;
    }

    @Override
    public void updateDraft(String namespaceId, Skill draftSkill) throws NacosException {
        if (draftSkill == null || StringUtils.isBlank(draftSkill.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING, "Skill name is required");
        }
        if (StringUtils.isBlank(draftSkill.getDescription())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Skill description is required");
        }
        if (StringUtils.isBlank(draftSkill.getSkillMd())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Skill markdown is required");
        }
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

        List<String> files = writeSkillToStorage(namespaceId, draftSkill, editing);
        aiResourceVersionPersistService.updateStorage(namespaceId, name, RESOURCE_TYPE_SKILL, editing,
                buildStorageJson(namespaceId, name, editing, files));
        resourceManager.bumpMetaDescription(namespaceId, meta, draftSkill.getDescription());
    }

    @Override
    public void deleteDraft(String namespaceId, String name) throws NacosException {
        AiResource meta = resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE_SKILL);
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        if (StringUtils.isBlank(editing)) {
            return;
        }
        // Read version row upfront (need status check and storage info before modifying)
        AiResourceVersion v = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL, editing);

        // Delete in reverse order of creation (storage -> version -> meta):
        // 1) meta: clear editingVersion reference first
        info.setEditingVersion(null);
        resourceManager.updateVersionInfoCas(namespaceId, meta, info);

        // 2) version row, then storage files
        if (v != null && AiResourceConstants.VERSION_STATUS_DRAFT.equalsIgnoreCase(v.getStatus())) {
            aiResourceVersionPersistService.delete(namespaceId, name, RESOURCE_TYPE_SKILL, editing);
            deleteSkillStorageForVersion(namespaceId, name, editing, v.getStorage());
        }
    }

    @Override
    public String submit(String namespaceId, String name, String version) throws NacosException {
        AiResource meta = resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE_SKILL);
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);

        String target = resourceManager.resolveSubmitTarget(info, version, RESOURCE_TYPE_SKILL, name);

        AiResourceVersion v = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL, target);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill version not found: " + name + "@" + target);
        }
        
        final String finalTarget = target;
        
        resourceManager.moveToReviewing(namespaceId, name, RESOURCE_TYPE_SKILL, finalTarget, meta, info);

        // Build context for pipeline execution (multi-file skill representation).
        Skill skill = loadSkillFromStorage(namespaceId, name, finalTarget, v.getStorage());
        SkillPipelineContext ctx = new SkillPipelineContext();
        ctx.setNamespaceId(namespaceId);
        ctx.setResourceName(name);
        ctx.setVersion(finalTarget);
        ctx.setFiles(buildPipelineFiles(skill));

        // Check pipeline availability before starting async execution.
        if (!publishPipelineExecutor.isPipelineAvailable(ctx.getResourceType())) {
            // Pipeline disabled or no matched nodes -> publish directly.
            publish(namespaceId, name, finalTarget, true);
            return finalTarget;
        }

        if (!resourceManager.runPipelineExecution(namespaceId, name, RESOURCE_TYPE_SKILL, finalTarget,
                ctx, publishPipelineExecutor)) {
            publish(namespaceId, name, finalTarget, true);
        }

        return finalTarget;
    }

    @Override
    public void publish(String namespaceId, String name, String version, boolean updateLatestLabel) throws NacosException {
        AiResourceVersion v = resourceManager.doPublish(namespaceId, name, RESOURCE_TYPE_SKILL, version,
                updateLatestLabel);
        
        SkillIndexManifest manifest = manifestService.loadForUpdate(namespaceId, name);
        manifest.getVersions().put(version, parseStorageFiles(v.getStorage()));
        if (updateLatestLabel) {
            manifest.getLabels().put(AiResourceConstants.LABEL_LATEST, version);
        }
        manifestService.write(namespaceId, name, manifest);
    }

    @Override
    public void forcePublish(String namespaceId, String name, String version, boolean updateLatestLabel) throws NacosException {
        AiResourceVersion v = resourceManager.doForcePublish(namespaceId, name, RESOURCE_TYPE_SKILL, version,
                updateLatestLabel);
        
        SkillIndexManifest manifest = manifestService.loadForUpdate(namespaceId, name);
        manifest.getVersions().put(version, parseStorageFiles(v.getStorage()));
        if (updateLatestLabel) {
            manifest.getLabels().put(AiResourceConstants.LABEL_LATEST, version);
        }
        manifestService.write(namespaceId, name, manifest);
    }

    @Override
    public void updateLabels(String namespaceId, String name, Map<String, String> labels) throws NacosException {
        resourceManager.validateAndUpdateLabels(namespaceId, name, RESOURCE_TYPE_SKILL, labels);
        
        SkillIndexManifest manifest = manifestService.query(namespaceId, name);
        if (manifest != null) {
            manifest.setLabels(labels == null ? new HashMap<>(4) : new LinkedHashMap<>(labels));
            manifestService.write(namespaceId, name, manifest);
        }
    }

    @Override
    public void updateBizTags(String namespaceId, String name, String bizTags) throws NacosException {
        AiResource meta = resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE_SKILL);
        VisibilityHelper.checkWritableResource(meta);
        resourceManager.updateBizTagsCas(namespaceId, meta, bizTags);
    }

    @Override
    public void changeOnlineStatus(String namespaceId, String name, String scope, String version, boolean online)
            throws NacosException {
        AiResource meta = resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE_SKILL);
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);

        boolean skillScope = SCOPE_SKILL.equalsIgnoreCase(scope) || StringUtils.isBlank(version);
        if (skillScope) {
            resourceManager.metaEnableDisable(namespaceId, meta, online);
            if (online) {
                refreshSkillIndexManifest(namespaceId, name);
            } else {
                manifestService.delete(namespaceId, name);
            }
            return;
        }

        AiResourceVersion v = resourceManager.toggleVersionOnlineStatus(namespaceId, meta, info, version, online);
        if (v == null) {
            return;
        }
        
        if (online) {
            List<String> files = parseStorageFiles(v.getStorage());
            if (files != null && !files.isEmpty()) {
                SkillIndexManifest manifest = manifestService.loadForUpdate(namespaceId, name);
                manifest.getVersions().put(version, files);
                manifestService.write(namespaceId, name, manifest);
            }
        } else {
            SkillIndexManifest manifest = manifestService.query(namespaceId, name);
            if (manifest != null && manifest.getVersions() != null) {
                manifest.getVersions().remove(version);
                manifestService.write(namespaceId, name, manifest);
            }
        }
    }

    @Override
    public void updateScope(String namespaceId, String name, String scope) throws NacosException {
        resourceManager.doUpdateScope(namespaceId, name, RESOURCE_TYPE_SKILL, scope);
    }

    @Override
    public Page<SkillBasicInfo> searchSkills(String namespaceId, String keyword, int pageNo, int pageSize)
            throws NacosException {
        String nameLike = StringUtils.isBlank(keyword) ? null
                : aiResourcePersistService.generateLikeArgument(Constants.ALL_PATTERN + keyword + Constants.ALL_PATTERN);
        QueryCondition queryCondition = resourceManager.buildQueryCondition(namespaceId, RESOURCE_TYPE_SKILL, nameLike, null,
                VisibilityConstants.ACTION_READ);
        if (queryCondition.isAlwaysEmpty()) {
            return AiResourceManager.buildEmptyPage(pageNo);
        }
        Page<AiResource> metaPage = aiResourcePersistService.list(queryCondition, pageNo, pageSize);
        List<AiResource> filtered = metaPage == null || metaPage.getPageItems() == null ? new ArrayList<>()
                : metaPage.getPageItems();
        List<SkillBasicInfo> items = new ArrayList<>();
        for (AiResource meta : filtered) {
            if (meta == null) {
                continue;
            }
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

    @Override
    public Skill querySkill(String namespaceId, String name, String version, String label) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + name);
        }
        resourceManager.ensureReadableOrNotFound(meta, "Skill not found: " + name);
        SkillIndexManifest manifest = manifestService.query(namespaceId, name);
        if (manifest == null || manifest.getVersions() == null || manifest.getVersions().isEmpty()) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + name);
        }

        String resolved = SkillIndexManifestService.resolveVersion(manifest, version, label);
        if (StringUtils.isBlank(resolved)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill version not found: " + name);
        }

        List<String> files = manifest.getVersions().get(resolved);
        if (files == null || files.isEmpty()) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill version not found: " + name + "@" + resolved);
        }

        Skill skill = loadSkillFromFiles(namespaceId, name, resolved, files);
        NotifyCenter.publishEvent(
                new SkillDownloadEvent(namespaceId, name, RESOURCE_TYPE_SKILL, resolved));
        return skill;
    }

    // ========== Private methods ==========

    private void createDraftWithSkill(String namespaceId, Skill skill, String version, AiResource existedMeta,
            boolean isNewSkill) throws NacosException {
        String skillName = skill.getName();
        String currentUser = VisibilityHelper.resolveCurrentIdentity();

        // 1) write all resources (including SKILL.md) to storage
        List<String> files = writeSkillToStorage(namespaceId, skill, version);

        // 2) insert draft version row
        resourceManager.insertVersionRow(namespaceId, skillName, RESOURCE_TYPE_SKILL,
                StringUtils.isBlank(currentUser) ? DEFAULT_AUTHOR : currentUser,
                AiResourceConstants.VERSION_STATUS_DRAFT, version, skill.getDescription(),
                buildStorageJson(namespaceId, skillName, version, files));

        // 3) create or update meta for editingVersion
        resourceManager.initOrUpdateMetaForDraft(namespaceId, skillName, RESOURCE_TYPE_SKILL,
                skill.getDescription(), null, version, existedMeta, isNewSkill);
    }

    private static String resolveSkillStorageProvider() {
        String provider = EnvUtil.getProperty(SKILL_STORAGE_PROVIDER_CONFIG_KEY, STORAGE_PROVIDER_NACOS_CONFIG);
        return StringUtils.isBlank(provider) ? STORAGE_PROVIDER_NACOS_CONFIG : provider.trim();
    }

    /**
     * Build storage metadata JSON for version row (provider + scope + file list).
     */
    private static String buildStorageJson(String namespaceId, String skillName, String version, List<String> files) {
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
            AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL);
            if (meta == null) {
                return;
            }
            ResourceVersionInfo vInfo = AiResourceManager.parseVersionInfo(meta.getVersionInfo());

            SkillIndexManifest manifest = new SkillIndexManifest();
            manifest.setLabels(vInfo != null && vInfo.getLabels() != null
                    ? new HashMap<>(vInfo.getLabels()) : new HashMap<>(4));
            manifest.setVersions(new HashMap<>(4));

            Page<AiResourceVersion> versionPage = aiResourceVersionPersistService.listAll(namespaceId, name, 1, 200);
            if (versionPage != null && versionPage.getPageItems() != null) {
                for (AiResourceVersion v : versionPage.getPageItems()) {
                    if (v == null || !AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(v.getStatus())) {
                        continue;
                    }
                    List<String> files = parseStorageFiles(v.getStorage());
                    if (files != null && !files.isEmpty()) {
                        manifest.getVersions().put(v.getVersion(), files);
                    }
                }
            }

            if (!manifest.getVersions().isEmpty()) {
                manifestService.write(namespaceId, name, manifest);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to refresh skill index manifest for {}: {}", name, e.getMessage());
        }
    }
    
    private void validateSkillNameByParamChecker(String skillName) throws NacosApiException {
        ParamInfo paramInfo = new ParamInfo();
        paramInfo.setSkillName(skillName);
        String checkerType = ServerParamCheckConfig.getInstance().getActiveParamChecker();
        AbstractParamChecker paramChecker = ParamCheckerManager.getInstance().getParamChecker(checkerType);
        ParamCheckResponse response = paramChecker.checkParamInfoList(Collections.singletonList(paramInfo));
        if (!response.isSuccess()) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    response.getMessage());
        }
    }

    /**
     * Write all skill files to storage as raw file contents (no JSON envelope).
     *
     * @return list of stored file paths (for use in buildStorageJson)
     */
    private List<String> writeSkillToStorage(String namespaceId, Skill skill, String version) throws NacosException {
        String provider = resolveSkillStorageProvider();
        String skillName = skill.getName();
        List<String> files = new ArrayList<>();

        // Save concurrently to reduce latency when skill has multiple resource files.
        Executor executor = ExecutorUtils.getSkillStorageIoExecutor();
        List<CompletableFuture<Void>> tasks = new ArrayList<>();

        // 1) Store SKILL.md as raw markdown content
        String mdPath = SKILL_MD_RESOURCE_NAME;
        byte[] mdBytes = (skill.getSkillMd() == null ? "" : skill.getSkillMd()).getBytes(StandardCharsets.UTF_8);
        StorageKey mdKey = NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId, skillName, version,
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
                byte[] content = (resource.getContent() == null ? "" : resource.getContent()).getBytes(StandardCharsets.UTF_8);
                StorageKey resourceKey = NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId, skillName,
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
    private Skill loadSkillFromStorage(String namespaceId, String skillName, String version, String storageJson)
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
    private Skill loadSkillFromFiles(String namespaceId, String skillName, String version, List<String> files)
            throws NacosException {
        String provider = resolveSkillStorageProvider();
        Skill skill = new Skill();
        skill.setNamespaceId(namespaceId);
        Map<String, SkillResource> resourceMap = new HashMap<>(files.size());

        for (String filePath : files) {
            StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId, skillName, version,
                    filePath);
            byte[] bytes = storageRouter.route(key).get(key);
            if (bytes == null) {
                continue;
            }
            String fileContent = new String(bytes, StandardCharsets.UTF_8);
            if (SKILL_MD_RESOURCE_NAME.equals(filePath)) {
                skill.setSkillMd(fileContent);
                parseSkillBaseInfoFromSkillMd(fileContent, skill);
            } else {
                SkillResource resource = buildResourceFromStoredFile(filePath, fileContent);
                String resourceId = SkillUtils.generateResourceId(resource.getType(), resource.getName());
                resourceMap.put(resourceId, resource);
            }
        }

        skill.setResource(resourceMap);
        return skill;
    }

    private static String buildResourceFilePath(SkillResource resource) {
        String type = resource.getType();
        if (StringUtils.isBlank(type)) {
            return resource.getName();
        }
        return type + "/" + resource.getName();
    }

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
    private void deleteSkillStorageForVersion(String namespaceId, String skillName, String version, String storageJson)
            throws NacosException {
        List<String> files = parseStorageFiles(storageJson);
        if (files == null || files.isEmpty()) {
            return;
        }
        String provider = resolveSkillStorageProvider();
        for (String filePath : files) {
            StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId, skillName, version,
                    filePath);
            storageRouter.route(key).delete(key);
        }
    }

}
