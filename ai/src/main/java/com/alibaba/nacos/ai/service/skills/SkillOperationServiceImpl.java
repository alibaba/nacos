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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.event.SkillDownloadEvent;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.skills.SkillIndexManifest;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionResult;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeResult;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.visibility.DefaultVisibilityAdvisorConverter;
import com.alibaba.nacos.ai.service.visibility.VisibilityAdvisorConverter;
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
import com.alibaba.nacos.core.paramcheck.ServerParamCheckConfig;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFileContent;
import com.alibaba.nacos.plugin.ai.pipeline.model.SkillPipelineContext;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.plugin.visibility.model.BaseVisibilityPredicate;
import com.alibaba.nacos.plugin.visibility.model.VisibilityQueryContext;
import com.alibaba.nacos.plugin.visibility.spi.QueryAdvisor;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.alibaba.nacos.ai.constant.Constants.Skills;
import static com.alibaba.nacos.ai.model.skills.SkillIndexManifest.LABEL_LATEST;

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

    private static final String META_STATUS_ENABLE = "enable";

    private static final String VERSION_STATUS_ONLINE = "online";

    private static final String DEFAULT_AUTHOR = "-";

    private static final String VERSION_STATUS_DRAFT = "draft";

    private static final String VERSION_STATUS_REVIEWING = "reviewing";

    private static final String VERSION_STATUS_OFFLINE = "offline";

    private static final String META_STATUS_DISABLE = "disable";

    private static final String SKILL_MD_RESOURCE_NAME = "SKILL.md";

    private static final String DEFAULT_INITIAL_UPLOAD_VERSION = "0.0.1";

    private static final Pattern PURE_SEMVER_PATTERN =
            Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

    private static final String SCOPE_SKILL = "skill";

    private static final int MAX_WORKING_VERSION_RETRY = 3;

    private final AiResourceStorageRouter storageRouter;

    private final AiResourcePersistService aiResourcePersistService;

    private final AiResourceVersionPersistService aiResourceVersionPersistService;

    private final PublishPipelineExecutor publishPipelineExecutor;

    private final PipelineExecutionRepository pipelineExecutionRepository;

    private final SkillIndexManifestService manifestService;
    
    private final VisibilityAdvisorConverter visibilityAdvisorConverter;

    public SkillOperationServiceImpl(AiResourcePersistService aiResourcePersistService,
            AiResourceVersionPersistService aiResourceVersionPersistService,
            PublishPipelineExecutor publishPipelineExecutor,
            PipelineExecutionRepository pipelineExecutionRepository,
            SkillIndexManifestService manifestService) {
        this.storageRouter = AiResourceStorageRouter.getInstance();
        this.aiResourcePersistService = aiResourcePersistService;
        this.aiResourceVersionPersistService = aiResourceVersionPersistService;
        this.publishPipelineExecutor = publishPipelineExecutor;
        this.pipelineExecutionRepository = pipelineExecutionRepository;
        this.manifestService = manifestService;
        this.visibilityAdvisorConverter = new DefaultVisibilityAdvisorConverter();
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
        if (overwrite) {
            return overwriteUploadedSkill(namespaceId, skill, uploadVersion);
        }
        String name = skill.getName();
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            createDraftWithSkill(namespaceId, skill, uploadVersion, null, true);
            return name;
        }

        VisibilityHelper.checkWritableResource(meta);
        SkillVersionInfo info = requireVersionInfo(meta);
        if (StringUtils.isNotBlank(info.getEditingVersion()) || StringUtils.isNotBlank(info.getReviewingVersion())) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    "There is already a working version (editing/reviewing), cannot upload");
        }

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

        AiResourceVersion versionRow = new AiResourceVersion();
        versionRow.setNamespaceId(namespaceId);
        versionRow.setName(skillName);
        versionRow.setType(RESOURCE_TYPE_SKILL);
        versionRow.setAuthor(DEFAULT_AUTHOR);
        versionRow.setStatus(VERSION_STATUS_ONLINE);
        versionRow.setVersion(version);
        versionRow.setDesc(skill.getDescription());
        versionRow.setStorage(buildStorageJson(namespaceId, skillName, version, files));
        aiResourceVersionPersistService.insert(versionRow);

        SkillVersionInfo versionInfo = new SkillVersionInfo();
        versionInfo.setOnlineCnt(1);
        Map<String, String> labels = new HashMap<>(4);
        labels.put(LABEL_LATEST, version);
        versionInfo.setLabels(labels);

        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName(skillName);
        meta.setType(RESOURCE_TYPE_SKILL);
        meta.setStatus(META_STATUS_ENABLE);
        meta.setDesc(skill.getDescription());
        meta.setOwner(DEFAULT_AUTHOR);
        meta.setFrom(from);
        meta.setScope(VisibilityConstants.SCOPE_PUBLIC);
        meta.setVersionInfo(JacksonUtils.toJson(versionInfo));
        meta.setMetaVersion(1L);
        aiResourcePersistService.insert(meta);

        SkillIndexManifest manifest = new SkillIndexManifest();
        manifest.setLabels(new HashMap<>(labels));
        Map<String, List<String>> versions = new HashMap<>(4);
        versions.put(version, files);
        manifest.setVersions(versions);
        manifestService.write(namespaceId, skillName, manifest);
    }

    private String overwriteUploadedSkill(String namespaceId, Skill skill, String uploadVersion) throws NacosException {
        String name = skill.getName();
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            createDraftWithSkill(namespaceId, skill, uploadVersion, null, true);
            return name;
        }

        VisibilityHelper.checkWritableResource(meta);
        SkillVersionInfo info = requireVersionInfo(meta);
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
        List<String> existingVersions = listExistingVersions(namespaceId, skillName);
        if (existingVersions.isEmpty()) {
            return candidateVersion;
        }
        String maxSemver = maxSemver(existingVersions);
        boolean candidateExists = existingVersions.contains(candidateVersion);
        if (candidateExists) {
            return nextPatchVersion(maxSemver == null ? candidateVersion : maxSemver);
        }
        if (maxSemver != null && compareSemver(candidateVersion, maxSemver) == 0) {
            return nextPatchVersion(maxSemver);
        }
        return candidateVersion;
    }

    private String resolveNextDraftVersion(String namespaceId, String skillName) throws NacosException {
        List<String> existingVersions = listExistingVersions(namespaceId, skillName);
        String maxSemver = maxSemver(existingVersions);
        if (StringUtils.isNotBlank(maxSemver)) {
            return nextPatchVersion(maxSemver);
        }
        int maxLegacy = maxVersionNumber(existingVersions);
        if (maxLegacy > 0) {
            return "v" + (maxLegacy + 1);
        }
        return DEFAULT_INITIAL_UPLOAD_VERSION;
    }

    private List<String> listExistingVersions(String namespaceId, String skillName) throws NacosException {
        Page<AiResourceVersion> page = aiResourceVersionPersistService.listAll(namespaceId, skillName, 1, 500);
        if (page == null || page.getPageItems() == null || page.getPageItems().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> versions = new ArrayList<>(page.getPageItems().size());
        for (AiResourceVersion item : page.getPageItems()) {
            if (item != null && StringUtils.isNotBlank(item.getVersion())) {
                versions.add(item.getVersion().trim());
            }
        }
        return versions;
    }

    private static String maxSemver(List<String> versions) {
        String max = null;
        for (String raw : versions) {
            String normalized = normalizePureSemver(raw);
            if (normalized == null) {
                continue;
            }
            if (max == null || compareSemver(normalized, max) > 0) {
                max = normalized;
            }
        }
        return max;
    }

    private static String nextPatchVersion(String version) {
        String normalized = normalizePureSemver(version);
        if (normalized == null) {
            return DEFAULT_INITIAL_UPLOAD_VERSION;
        }
        int[] parts = parseSemverParts(normalized);
        if (parts == null) {
            return DEFAULT_INITIAL_UPLOAD_VERSION;
        }
        return parts[0] + "." + parts[1] + "." + (parts[2] + 1);
    }

    private static int compareSemver(String a, String b) {
        int[] pa = parseSemverParts(a);
        int[] pb = parseSemverParts(b);
        if (pa == null && pb == null) {
            return 0;
        }
        if (pa == null) {
            return -1;
        }
        if (pb == null) {
            return 1;
        }
        if (pa[0] != pb[0]) {
            return Integer.compare(pa[0], pb[0]);
        }
        if (pa[1] != pb[1]) {
            return Integer.compare(pa[1], pb[1]);
        }
        return Integer.compare(pa[2], pb[2]);
    }

    private static String normalizePureSemver(String version) {
        if (StringUtils.isBlank(version)) {
            return null;
        }
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        Matcher matcher = PURE_SEMVER_PATTERN.matcher(v);
        if (!matcher.matches()) {
            return null;
        }
        return v;
    }

    private static int[] parseSemverParts(String version) {
        String normalized = normalizePureSemver(version);
        if (normalized == null) {
            return null;
        }
        String[] parts = normalized.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[] {
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
    
    private String resolveSpecifiedDraftVersion(String namespaceId, String skillName, String targetVersion,
            String basedOnVersion, String baseVersion) throws NacosException {
        if (StringUtils.isBlank(targetVersion)) {
            return resolveNextDraftVersion(namespaceId, skillName);
        }
        String candidate = targetVersion.trim();
        if (!isSupportedDraftVersionFormat(candidate)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Invalid targetVersion format: " + candidate + ", expected x.y.z or vN");
        }
        List<String> existingVersions = listExistingVersions(namespaceId, skillName);
        if (existingVersions.contains(candidate)) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    "targetVersion already exists: " + candidate);
        }
        if (StringUtils.isNotBlank(basedOnVersion) && StringUtils.isNotBlank(baseVersion)) {
            validateTargetVersionGreaterThanBase(candidate, baseVersion);
        }
        return candidate;
    }
    
    private static boolean isSupportedDraftVersionFormat(String version) {
        return normalizePureSemver(version) != null || parseLegacyVersionNumber(version) != null;
    }
    
    private static void validateTargetVersionGreaterThanBase(String targetVersion, String baseVersion)
            throws NacosApiException {
        String targetSemver = normalizePureSemver(targetVersion);
        String baseSemver = normalizePureSemver(baseVersion);
        if (targetSemver != null && baseSemver != null && compareSemver(targetSemver, baseSemver) <= 0) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "targetVersion must be greater than basedOnVersion, basedOnVersion=" + baseVersion
                            + ", targetVersion=" + targetVersion);
        }
        Integer targetLegacy = parseLegacyVersionNumber(targetVersion);
        Integer baseLegacy = parseLegacyVersionNumber(baseVersion);
        if (targetLegacy != null && baseLegacy != null && targetLegacy <= baseLegacy) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "targetVersion must be greater than basedOnVersion, basedOnVersion=" + baseVersion
                            + ", targetVersion=" + targetVersion);
        }
    }
    
    private static Integer parseLegacyVersionNumber(String version) {
        if (StringUtils.isBlank(version)) {
            return null;
        }
        String normalized = version.trim();
        if (!(normalized.startsWith("v") || normalized.startsWith("V")) || normalized.length() <= 1) {
            return null;
        }
        try {
            int numeric = Integer.parseInt(normalized.substring(1));
            return numeric > 0 ? numeric : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void overwriteEditingDraft(String namespaceId, Skill skill, AiResource meta, String editing)
            throws NacosException {
        AiResourceVersion versionRow = aiResourceVersionPersistService.find(namespaceId, skill.getName(),
                RESOURCE_TYPE_SKILL, editing);
        if (versionRow == null || !VERSION_STATUS_DRAFT.equalsIgnoreCase(versionRow.getStatus())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Current editing version is not draft: " + editing);
        }
        List<String> files = writeSkillToStorage(namespaceId, skill, editing);
        aiResourceVersionPersistService.updateStorage(namespaceId, skill.getName(), RESOURCE_TYPE_SKILL, editing,
                buildStorageJson(namespaceId, skill.getName(), editing, files));
        bumpMetaDescription(namespaceId, meta, skill.getDescription());
    }

    @Override
    public SkillMeta getSkillDetail(String namespaceId, String skillName) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + skillName);
        }
        ensureReadableOrNotFound(meta, "Skill not found: " + skillName);
        SkillVersionInfo versionInfo = requireVersionInfo(meta);

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
        detail.setEnable(META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus()));
        detail.setBizTags(meta.getBizTags());
        detail.setFrom(meta.getFrom());
        detail.setEditingVersion(versionInfo.getEditingVersion());
        detail.setReviewingVersion(versionInfo.getReviewingVersion());
        detail.setLabels(versionInfo.getLabels());
        detail.setScope(resolveScope(meta));
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
        ensureReadableOrNotFound(meta, "Skill not found: " + skillName);
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

        // 2) meta: admin API can no longer find the skill
        aiResourcePersistService.delete(namespaceId, skillName, RESOURCE_TYPE_SKILL);

        // 3) version rows (list before delete to get storage info)
        Page<AiResourceVersion> versions = aiResourceVersionPersistService.listAll(namespaceId, skillName, 1, 200);
        aiResourceVersionPersistService.deleteByNameAndType(namespaceId, skillName, RESOURCE_TYPE_SKILL);

        // 4) storage files
        if (versions != null && versions.getPageItems() != null) {
            for (AiResourceVersion v : versions.getPageItems()) {
                if (v == null || StringUtils.isBlank(v.getVersion())) {
                    continue;
                }
                deleteSkillStorageForVersion(namespaceId, skillName, v.getVersion(), v.getStorage());
            }
        }
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

        QueryCondition queryCondition = buildQueryCondition(namespaceId, RESOURCE_TYPE_SKILL, nameLike, null,
                VisibilityConstants.ACTION_READ);
        queryCondition.setOrderBy(orderBy);
        if (StringUtils.isNotBlank(owner)) {
            queryCondition.setOwner(owner);
        }
        if (StringUtils.isNotBlank(scope)) {
            queryCondition.setScope(scope);
        }
        if (queryCondition.isAlwaysEmpty()) {
            return buildEmptyPage(pageNo);
        }
        Page<AiResource> metaPage = aiResourcePersistService.list(queryCondition, pageNo, pageSize);
        List<AiResource> filtered = metaPage == null || metaPage.getPageItems() == null ? new ArrayList<>()
                : metaPage.getPageItems();
        List<SkillSummary> items = new ArrayList<>();
        for (AiResource meta : filtered) {
            if (meta == null) {
                continue;
            }
            SkillVersionInfo versionInfo = parseVersionInfo(meta.getVersionInfo());
            SkillSummary item = new SkillSummary();
            item.setNamespaceId(namespaceId);
            item.setName(meta.getName());
            item.setDescription(meta.getDesc());
            item.setEnable(META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus()));
            item.setBizTags(meta.getBizTags());
            item.setFrom(meta.getFrom());
            item.setScope(resolveScope(meta));
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

        Page<SkillSummary> result = new Page<>();
        result.setPageItems(items);
        result.setTotalCount(metaPage == null ? 0 : metaPage.getTotalCount());
        result.setPagesAvailable(metaPage == null ? 0 : metaPage.getPagesAvailable());
        result.setPageNumber(pageNo);
        return result;
    }
    
    private static String resolveScope(AiResource meta) {
        if (meta == null || StringUtils.isBlank(meta.getScope())) {
            return VisibilityConstants.SCOPE_PRIVATE;
        }
        return meta.getScope();
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
        SkillVersionInfo info = requireVersionInfo(meta);
        if (StringUtils.isNotBlank(info.getEditingVersion()) || StringUtils.isNotBlank(info.getReviewingVersion())) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    "There is already a working version (editing/reviewing), cannot create draft");
        }

        // resolveBaseVersion: explicit param > latest label > max semver version > max legacy vN version
        // null means no version exists yet
        String base = resolveBaseVersion(namespaceId, name, meta, basedOnVersion);
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
            AiResourceVersion v = new AiResourceVersion();
            v.setNamespaceId(namespaceId);
            v.setName(name);
            v.setType(RESOURCE_TYPE_SKILL);
            v.setAuthor(StringUtils.isBlank(currentUser) ? DEFAULT_AUTHOR : currentUser);
            v.setStatus(VERSION_STATUS_DRAFT);
            v.setVersion(newVersion);
            v.setDesc(baseSkill.getDescription());
            v.setStorage(buildStorageJson(namespaceId, name, newVersion, files));
            aiResourceVersionPersistService.insert(v);

            info.setEditingVersion(newVersion);
            updateMetaVersionInfoCas(namespaceId, meta, info);
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
        AiResource meta = requireMeta(namespaceId, name);
        VisibilityHelper.checkWritableResource(meta);
        SkillVersionInfo info = requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        if (StringUtils.isBlank(editing)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "No editing draft exists for skill: " + name);
        }
        AiResourceVersion v = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL, editing);
        if (v == null || !VERSION_STATUS_DRAFT.equalsIgnoreCase(v.getStatus())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Current editing version is not draft: " + editing);
        }

        List<String> files = writeSkillToStorage(namespaceId, draftSkill, editing);
        aiResourceVersionPersistService.updateStorage(namespaceId, name, RESOURCE_TYPE_SKILL, editing,
                buildStorageJson(namespaceId, name, editing, files));
        bumpMetaDescription(namespaceId, meta, draftSkill.getDescription());
    }

    @Override
    public void deleteDraft(String namespaceId, String name) throws NacosException {
        AiResource meta = requireMeta(namespaceId, name);
        VisibilityHelper.checkWritableResource(meta);
        SkillVersionInfo info = requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        if (StringUtils.isBlank(editing)) {
            return;
        }
        // Read version row upfront (need status check and storage info before modifying)
        AiResourceVersion v = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL, editing);

        // Delete in reverse order of creation (storage -> version -> meta):
        // 1) meta: clear editingVersion reference first
        info.setEditingVersion(null);
        updateMetaVersionInfoCas(namespaceId, meta, info);

        // 2) version row, then storage files
        if (v != null && VERSION_STATUS_DRAFT.equalsIgnoreCase(v.getStatus())) {
            aiResourceVersionPersistService.delete(namespaceId, name, RESOURCE_TYPE_SKILL, editing);
            deleteSkillStorageForVersion(namespaceId, name, editing, v.getStorage());
        }
    }

    @Override
    public String submit(String namespaceId, String name, String version) throws NacosException {
        AiResource meta = requireMeta(namespaceId, name);
        VisibilityHelper.checkWritableResource(meta);
        SkillVersionInfo info = requireVersionInfo(meta);

        String target = version;
        if (StringUtils.isBlank(target)) {
            target = info.getEditingVersion();
        }
        if (StringUtils.isBlank(target)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "No draft version to submit for skill: " + name);
        }

        AiResourceVersion v = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL, target);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill version not found: " + name + "@" + target);
        }
        
        final String finalTarget = target;
        
        // Move to reviewing before pipeline execution to ensure consistent state.
        aiResourceVersionPersistService.updateStatus(namespaceId, name, RESOURCE_TYPE_SKILL, finalTarget, VERSION_STATUS_REVIEWING);
        info.setEditingVersion(null);
        info.setReviewingVersion(finalTarget);
        updateMetaVersionInfoCas(namespaceId, meta, info);

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
        
        // Pre-generate executionId and write IN_PROGRESS pipelineInfo BEFORE starting async task
        // to eliminate the race condition where async callback could complete before pipelineInfo is written.
        String executionId = UUID.randomUUID().toString();

        // Record pipeline execution id.
        SkillPublishPipelineInfo pipelineInfo = new SkillPublishPipelineInfo();
        pipelineInfo.setExecutionId(executionId);
        pipelineInfo.setStatus(PipelineExecutionStatus.IN_PROGRESS);
        pipelineInfo.setPipeline(new ArrayList<>());
        aiResourceVersionPersistService.updatePublishPipelineInfo(namespaceId, name, RESOURCE_TYPE_SKILL, finalTarget,
                JacksonUtils.toJson(pipelineInfo));

        // Start async pipeline with pre-generated executionId.
        String result = publishPipelineExecutor.execute(ctx,
                r -> onPipelineComplete(namespaceId, name, finalTarget, r), executionId);
        if (StringUtils.isBlank(result)) {
            // Edge case: pipeline became unavailable between isPipelineAvailable check and execute.
            // Clean up pipelineInfo and publish directly.
            aiResourceVersionPersistService.updatePublishPipelineInfo(namespaceId, name, RESOURCE_TYPE_SKILL,
                    finalTarget, null);
            publish(namespaceId, name, finalTarget, true);
        }

        return finalTarget;
    }

    @Override
    public void publish(String namespaceId, String name, String version, boolean updateLatestLabel) throws NacosException {
        AiResource meta = requireMeta(namespaceId, name);
        VisibilityHelper.checkWritableResource(meta);
        SkillVersionInfo info = requireVersionInfo(meta);

        AiResourceVersion v = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL, version);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill version not found: " + name + "@" + version);
        }
        if (!VERSION_STATUS_REVIEWING.equalsIgnoreCase(v.getStatus())
                && !VERSION_STATUS_ONLINE.equalsIgnoreCase(v.getStatus())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Only reviewing version can be published: " + version);
        }

        // Validate pipeline execution result if pipeline exists.
        SkillPublishPipelineInfo pipelineInfo = parseSkillPublishPipelineInfo(v.getPublishPipelineInfo());
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
        if (!VERSION_STATUS_ONLINE.equalsIgnoreCase(v.getStatus())) {
            aiResourceVersionPersistService.updateStatus(namespaceId, name, RESOURCE_TYPE_SKILL, version, VERSION_STATUS_ONLINE);
        }

        // 2) meta: clear working pointers, onlineCnt++, update latest label if required
        if (StringUtils.equals(info.getReviewingVersion(), version)) {
            info.setReviewingVersion(null);
        }
        Integer cnt = info.getOnlineCnt();
        info.setOnlineCnt(cnt == null ? 1 : (cnt + 1));
        if (info.getLabels() == null) {
            info.setLabels(new HashMap<>(4));
        }
        if (updateLatestLabel) {
            info.getLabels().put(LABEL_LATEST, version);
        }
        updateMetaVersionInfoCas(namespaceId, meta, info);

        // Update skill index manifest: add version files and update labels
        SkillIndexManifest manifest = manifestService.loadForUpdate(namespaceId, name);
        manifest.getVersions().put(version, parseStorageFiles(v.getStorage()));
        if (updateLatestLabel) {
            manifest.getLabels().put(LABEL_LATEST, version);
        }
        manifestService.write(namespaceId, name, manifest);
    }

    @Override
    public void forcePublish(String namespaceId, String name, String version, boolean updateLatestLabel) throws NacosException {
        AiResource meta = requireMeta(namespaceId, name);
        VisibilityHelper.checkWritableResource(meta);
        SkillVersionInfo info = requireVersionInfo(meta);

        AiResourceVersion v = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL, version);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill version not found: " + name + "@" + version);
        }
        if (VERSION_STATUS_ONLINE.equalsIgnoreCase(v.getStatus())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Version is already online, force-publish is not needed: " + version);
        }

        LOGGER.warn("[FORCE-PUBLISH] Bypassing pipeline validation for skill {}@{} by user {}",
                name, version, VisibilityHelper.resolveCurrentIdentity());

        // Set version status to online
        aiResourceVersionPersistService.updateStatus(namespaceId, name, RESOURCE_TYPE_SKILL, version,
                VERSION_STATUS_ONLINE);

        // Update meta: clear working pointers (editingVersion or reviewingVersion), increment onlineCnt
        if (StringUtils.equals(info.getEditingVersion(), version)) {
            info.setEditingVersion(null);
        }
        if (StringUtils.equals(info.getReviewingVersion(), version)) {
            info.setReviewingVersion(null);
        }
        Integer cnt = info.getOnlineCnt();
        info.setOnlineCnt(cnt == null ? 1 : (cnt + 1));
        if (info.getLabels() == null) {
            info.setLabels(new HashMap<>(4));
        }
        if (updateLatestLabel) {
            info.getLabels().put(LABEL_LATEST, version);
        }
        updateMetaVersionInfoCas(namespaceId, meta, info);

        // Update skill index manifest
        SkillIndexManifest manifest = manifestService.loadForUpdate(namespaceId, name);
        manifest.getVersions().put(version, parseStorageFiles(v.getStorage()));
        if (updateLatestLabel) {
            manifest.getLabels().put(LABEL_LATEST, version);
        }
        manifestService.write(namespaceId, name, manifest);
    }

    @Override
    public void updateLabels(String namespaceId, String name, Map<String, String> labels) throws NacosException {
        AiResource meta = requireMeta(namespaceId, name);
        VisibilityHelper.checkWritableResource(meta);
        SkillVersionInfo info = requireVersionInfo(meta);
        // Validate: labels must not point to draft or reviewing versions
        if (labels != null) {
            String editing = info.getEditingVersion();
            String reviewing = info.getReviewingVersion();
            for (Map.Entry<String, String> entry : labels.entrySet()) {
                String targetVersion = entry.getValue();
                if (StringUtils.isNotBlank(editing) && editing.equals(targetVersion)) {
                    throw new NacosApiException(NacosException.INVALID_PARAM,
                            ErrorCode.PARAMETER_VALIDATE_ERROR,
                            "Label '" + entry.getKey() + "' cannot point to draft version: " + targetVersion);
                }
                if (StringUtils.isNotBlank(reviewing) && reviewing.equals(targetVersion)) {
                    throw new NacosApiException(NacosException.INVALID_PARAM,
                            ErrorCode.PARAMETER_VALIDATE_ERROR,
                            "Label '" + entry.getKey() + "' cannot point to reviewing version: " + targetVersion);
                }
            }
        }
        info.setLabels(labels == null ? null : new LinkedHashMap<>(labels));
        updateMetaVersionInfoCas(namespaceId, meta, info);

        // Sync labels to manifest
        SkillIndexManifest manifest = manifestService.query(namespaceId, name);
        if (manifest != null) {
            manifest.setLabels(labels == null ? new HashMap<>(4) : new LinkedHashMap<>(labels));
            manifestService.write(namespaceId, name, manifest);
        }
    }

    @Override
    public void updateBizTags(String namespaceId, String name, String bizTags) throws NacosException {
        AiResource meta = requireMeta(namespaceId, name);
        VisibilityHelper.checkWritableResource(meta);
        updateMetaBizTagsCas(namespaceId, meta, bizTags);
    }

    @Override
    public void changeOnlineStatus(String namespaceId, String name, String scope, String version, boolean online)
            throws NacosException {
        AiResource meta = requireMeta(namespaceId, name);
        VisibilityHelper.checkWritableResource(meta);
        SkillVersionInfo info = requireVersionInfo(meta);

        boolean skillScope = SCOPE_SKILL.equalsIgnoreCase(scope) || StringUtils.isBlank(version);
        if (skillScope) {
            metaEnableDisable(namespaceId, meta, online);
            if (online) {
                refreshSkillIndexManifest(namespaceId, name);
            } else {
                manifestService.delete(namespaceId, name);
            }
            return;
        }

        AiResourceVersion v = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL, version);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill version not found: " + name + "@" + version);
        }
        String targetStatus = online ? VERSION_STATUS_ONLINE : VERSION_STATUS_OFFLINE;
        aiResourceVersionPersistService.updateStatus(namespaceId, name, RESOURCE_TYPE_SKILL, version, targetStatus);
        // onlineCnt is best-effort, list/search uses it as hint
        Integer cnt = info.getOnlineCnt() == null ? 0 : info.getOnlineCnt();
        if (online) {
            info.setOnlineCnt(cnt + 1);
        } else {
            info.setOnlineCnt(Math.max(0, cnt - 1));
        }
        updateMetaVersionInfoCas(namespaceId, meta, info);

        // Sync version to manifest
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
        AiResource meta = requireMeta(namespaceId, name);
        VisibilityHelper.checkWritableResource(meta);
        boolean ok = aiResourcePersistService.updateScope(namespaceId, name, RESOURCE_TYPE_SKILL, scope.toUpperCase());
        if (!ok) {
            LOGGER.error("Failed to update scope for skill: {}, namespace: {}, scope: {}", name, namespaceId, scope);
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                    "Failed to update scope for skill: " + name);
        }
    }

    @Override
    public Page<SkillBasicInfo> searchSkills(String namespaceId, String keyword, int pageNo, int pageSize)
            throws NacosException {
        String nameLike = StringUtils.isBlank(keyword) ? null
                : aiResourcePersistService.generateLikeArgument(Constants.ALL_PATTERN + keyword + Constants.ALL_PATTERN);
        QueryCondition queryCondition = buildQueryCondition(namespaceId, RESOURCE_TYPE_SKILL, nameLike, null,
                VisibilityConstants.ACTION_READ);
        if (queryCondition.isAlwaysEmpty()) {
            return buildEmptyPage(pageNo);
        }
        Page<AiResource> metaPage = aiResourcePersistService.list(queryCondition, pageNo, pageSize);
        List<AiResource> filtered = metaPage == null || metaPage.getPageItems() == null ? new ArrayList<>()
                : metaPage.getPageItems();
        List<SkillBasicInfo> items = new ArrayList<>();
        for (AiResource meta : filtered) {
            if (meta == null) {
                continue;
            }
            if (!META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus())) {
                continue;
            }
            SkillVersionInfo info = parseVersionInfo(meta.getVersionInfo());
            if (info == null || info.getOnlineCnt() == null || info.getOnlineCnt() <= 0) {
                continue;
            }
            SkillBasicInfo basicInfo = new SkillBasicInfo();
            basicInfo.setName(meta.getName());
            basicInfo.setDescription(meta.getDesc());
            items.add(basicInfo);
        }
        Page<SkillBasicInfo> result = new Page<>();
        result.setPageItems(items);
        result.setTotalCount(metaPage == null ? 0 : metaPage.getTotalCount());
        result.setPagesAvailable(metaPage == null ? 0 : metaPage.getPagesAvailable());
        result.setPageNumber(pageNo);
        return result;
    }

    @Override
    public Skill querySkill(String namespaceId, String name, String version, String label) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + name);
        }
        ensureReadableOrNotFound(meta, "Skill not found: " + name);
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
        AiResourceVersion versionRow = new AiResourceVersion();
        versionRow.setNamespaceId(namespaceId);
        versionRow.setName(skillName);
        versionRow.setType(RESOURCE_TYPE_SKILL);
        versionRow.setAuthor(StringUtils.isBlank(currentUser) ? DEFAULT_AUTHOR : currentUser);
        versionRow.setStatus(VERSION_STATUS_DRAFT);
        versionRow.setVersion(version);
        versionRow.setDesc(skill.getDescription());
        versionRow.setStorage(buildStorageJson(namespaceId, skillName, version, files));
        aiResourceVersionPersistService.insert(versionRow);

        // 3) create or update meta for editingVersion
        if (isNewSkill) {
            AiResource meta = new AiResource();
            meta.setNamespaceId(namespaceId);
            meta.setName(skillName);
            meta.setType(RESOURCE_TYPE_SKILL);
            meta.setStatus(META_STATUS_ENABLE);
            meta.setDesc(skill.getDescription());
            meta.setOwner(currentUser);
            SkillVersionInfo info = new SkillVersionInfo();
            info.setEditingVersion(version);
            info.setOnlineCnt(0);
            info.setLabels(new HashMap<>(4));
            meta.setVersionInfo(JacksonUtils.toJson(info));
            meta.setMetaVersion(1L);
            aiResourcePersistService.insert(meta);
        } else if (existedMeta != null) {
            SkillVersionInfo info = requireVersionInfo(existedMeta);
            info.setEditingVersion(version);
            updateMetaVersionInfoCas(namespaceId, existedMeta, info);
        }
    }

    private static String resolveSkillStorageProvider() {
        String provider = EnvUtil.getProperty(SKILL_STORAGE_PROVIDER_CONFIG_KEY, STORAGE_PROVIDER_NACOS_CONFIG);
        return StringUtils.isBlank(provider) ? STORAGE_PROVIDER_NACOS_CONFIG : provider.trim();
    }

    private String resolveVersion(AiResource meta, String explicitVersion, String label) {
        if (StringUtils.isNotBlank(label)) {
            SkillVersionInfo info = parseVersionInfo(meta.getVersionInfo());
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
        SkillVersionInfo info = parseVersionInfo(meta.getVersionInfo());
        if (info != null && info.getLabels() != null) {
            String v = info.getLabels().get("latest");
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return null;
    }

    /**
     * Resolves the base version to copy from when creating a draft.
     * Priority: explicit basedOnVersion > "latest" label > highest numeric version.
     * Returns null if no version exists yet (empty draft should be created).
     * Throws NOT_FOUND if an explicit basedOnVersion was given but cannot be resolved.
     */
    private String resolveBaseVersion(String namespaceId, String name, AiResource meta, String basedOnVersion)
            throws NacosException {
        if (StringUtils.isNotBlank(basedOnVersion)) {
            String resolved = resolveVersion(meta, basedOnVersion, null);
            if (StringUtils.isBlank(resolved)) {
                throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        "Base version not found for skill: " + name + ", basedOnVersion: " + basedOnVersion);
            }
            return resolved;
        }
        // No explicit base: try latest label first, then fall back to max semver / legacy vN version.
        String latest = resolveVersion(meta, null, LABEL_LATEST);
        return StringUtils.isNotBlank(latest) ? latest : maxVersionForDraftBase(namespaceId, name);
    }

    private static SkillVersionInfo parseVersionInfo(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JacksonUtils.toObj(json, SkillVersionInfo.class);
        } catch (Exception ignored) {
            return null;
        }
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
            SkillVersionInfo vInfo = parseVersionInfo(meta.getVersionInfo());

            SkillIndexManifest manifest = new SkillIndexManifest();
            manifest.setLabels(vInfo != null && vInfo.getLabels() != null
                    ? new HashMap<>(vInfo.getLabels()) : new HashMap<>(4));
            manifest.setVersions(new HashMap<>(4));

            Page<AiResourceVersion> versionPage = aiResourceVersionPersistService.listAll(namespaceId, name, 1, 200);
            if (versionPage != null && versionPage.getPageItems() != null) {
                for (AiResourceVersion v : versionPage.getPageItems()) {
                    if (v == null || !VERSION_STATUS_ONLINE.equalsIgnoreCase(v.getStatus())) {
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

    private AiResource requireMeta(String namespaceId, String name) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "Skill not found: " + name);
        }
        return meta;
    }

    private static SkillVersionInfo requireVersionInfo(AiResource meta) {
        SkillVersionInfo info = parseVersionInfo(meta == null ? null : meta.getVersionInfo());
        if (info == null) {
            info = new SkillVersionInfo();
            info.setLabels(new HashMap<>(4));
        } else if (info.getLabels() == null) {
            info.setLabels(new HashMap<>(4));
        }
        return info;
    }

    private void updateMetaVersionInfoCas(String namespaceId, AiResource meta, SkillVersionInfo info) throws NacosException {
        if (meta == null || meta.getMetaVersion() == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, "Meta version missing");
        }
        long expected = meta.getMetaVersion();
        AiResource newValue = new AiResource();
        newValue.setStatus(meta.getStatus());
        newValue.setDesc(meta.getDesc());
        newValue.setBizTags(meta.getBizTags());
        newValue.setExt(meta.getExt());
        newValue.setVersionInfo(JacksonUtils.toJson(info));
        for (int i = 0; i < MAX_WORKING_VERSION_RETRY; i++) {
            boolean ok = aiResourcePersistService.updateMetaCas(namespaceId, meta.getName(), meta.getType(), expected, newValue);
            if (ok) {
                return;
            }
            AiResource latest = aiResourcePersistService.find(namespaceId, meta.getName(), meta.getType());
            if (latest == null || latest.getMetaVersion() == null) {
                throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, "Meta cas failed");
            }
            meta = latest;
            expected = latest.getMetaVersion();
            newValue.setStatus(latest.getStatus());
            newValue.setDesc(latest.getDesc());
            newValue.setBizTags(latest.getBizTags());
            newValue.setExt(latest.getExt());
        }
        throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, "Meta update conflict, retry");
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

    private void updateMetaBizTagsCas(String namespaceId, AiResource meta, String bizTags) throws NacosException {
        if (meta == null || meta.getMetaVersion() == null) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, "Meta version missing");
        }
        long expected = meta.getMetaVersion();
        AiResource newValue = new AiResource();
        newValue.setStatus(meta.getStatus());
        newValue.setDesc(meta.getDesc());
        newValue.setBizTags(bizTags);
        newValue.setExt(meta.getExt());
        newValue.setVersionInfo(meta.getVersionInfo());
        for (int i = 0; i < MAX_WORKING_VERSION_RETRY; i++) {
            boolean ok = aiResourcePersistService.updateMetaCas(namespaceId, meta.getName(), meta.getType(), expected,
                    newValue);
            if (ok) {
                return;
            }
            AiResource latest = aiResourcePersistService.find(namespaceId, meta.getName(), meta.getType());
            if (latest == null || latest.getMetaVersion() == null) {
                throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, "Meta cas failed");
            }
            meta = latest;
            expected = latest.getMetaVersion();
            newValue.setStatus(latest.getStatus());
            newValue.setDesc(latest.getDesc());
            newValue.setExt(latest.getExt());
            newValue.setVersionInfo(latest.getVersionInfo());
        }
        throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "Meta update conflict, retry");
    }

    private void metaEnableDisable(String namespaceId, AiResource meta, boolean enable) throws NacosException {
        SkillVersionInfo info = requireVersionInfo(meta);
        AiResource newValue = new AiResource();
        newValue.setStatus(enable ? META_STATUS_ENABLE : META_STATUS_DISABLE);
        newValue.setDesc(meta.getDesc());
        newValue.setBizTags(meta.getBizTags());
        newValue.setExt(meta.getExt());
        newValue.setVersionInfo(JacksonUtils.toJson(info));
        long expected = meta.getMetaVersion() == null ? 0 : meta.getMetaVersion();
        for (int i = 0; i < MAX_WORKING_VERSION_RETRY; i++) {
            boolean ok = aiResourcePersistService.updateMetaCas(namespaceId, meta.getName(), meta.getType(), expected, newValue);
            if (ok) {
                return;
            }
            AiResource latest = aiResourcePersistService.find(namespaceId, meta.getName(), meta.getType());
            if (latest == null || latest.getMetaVersion() == null) {
                throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR, "Meta cas failed");
            }
            meta = latest;
            expected = latest.getMetaVersion();
            newValue.setDesc(latest.getDesc());
            newValue.setBizTags(latest.getBizTags());
            newValue.setExt(latest.getExt());
        }
        throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT, "Meta update conflict, retry");
    }

    /**
     * Returns the version string to use as draft base fallback.
     * Priority: max semver (e.g. 1.2.3) > max legacy vN (e.g. v7).
     */
    private String maxVersionForDraftBase(String namespaceId, String name) throws NacosException {
        List<String> existingVersions = listExistingVersions(namespaceId, name);
        String maxSemverVersion = maxSemver(existingVersions);
        if (StringUtils.isNotBlank(maxSemverVersion)) {
            return maxSemverVersion;
        }
        return maxVersionByNumber(name, existingVersions);
    }

    /**
     * Returns the version string with the highest numeric suffix (e.g. "v3" when versions are v1/v2/v3),
     * or null if no numeric legacy version exists.
     */
    private String maxVersionByNumber(String name, List<String> existingVersions) {
        int max = maxVersionNumber(existingVersions);
        return max == 0 ? null : "v" + max;
    }

    /**
     * Returns the highest legacy numeric version number from versions like v1/v2.
     */
    private int maxVersionNumber(List<String> existingVersions) {
        int max = 0;
        if (existingVersions != null) {
            for (String version : existingVersions) {
                if (StringUtils.isBlank(version)) {
                    continue;
                }
                String s = version.trim();
                if (!s.startsWith("v")) {
                    continue;
                }
                try {
                    int n = Integer.parseInt(s.substring(1));
                    if (n > max) {
                        max = n;
                    }
                } catch (Exception ignored) {
                    // ignore non-numeric legacy versions
                }
            }
        }
        return max;
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

    private void onPipelineComplete(String namespaceId, String name, String version, PipelineExecutionResult result) {
        try {
            SkillPublishPipelineInfo info = new SkillPublishPipelineInfo();
            info.setExecutionId(result == null ? null : result.getExecutionId());
            info.setStatus(result == null ? PipelineExecutionStatus.REJECTED : result.getStatus());
            info.setPipeline(result == null ? null : result.getPipeline());
            aiResourceVersionPersistService.updatePublishPipelineInfo(namespaceId, name, RESOURCE_TYPE_SKILL, version,
                    JacksonUtils.toJson(info));

            if (result == null || result.getStatus() != PipelineExecutionStatus.APPROVED) {
                // Reject back to draft and move reviewing -> editing (best effort).
                aiResourceVersionPersistService.updateStatus(namespaceId, name, RESOURCE_TYPE_SKILL, version, VERSION_STATUS_DRAFT);
                AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL);
                if (meta != null) {
                    SkillVersionInfo vInfo = requireVersionInfo(meta);
                    if (StringUtils.equals(vInfo.getReviewingVersion(), version)) {
                        vInfo.setReviewingVersion(null);
                        vInfo.setEditingVersion(version);
                        try {
                            updateMetaVersionInfoCas(namespaceId, meta, vInfo);
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

    private static SkillPublishPipelineInfo parseSkillPublishPipelineInfo(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            SkillPublishPipelineInfo info = JacksonUtils.toObj(json, SkillPublishPipelineInfo.class);
            if (info == null || StringUtils.isBlank(info.getExecutionId())) {
                return null;
            }
            return info;
        } catch (Exception ignored) {
            return null;
        }
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

    private void bumpMetaDescription(String namespaceId, AiResource meta, String description) {
        if (meta == null || meta.getMetaVersion() == null) {
            return;
        }
        long expected = meta.getMetaVersion();
        AiResource newValue = new AiResource();
        newValue.setStatus(meta.getStatus());
        newValue.setDesc(description);
        newValue.setBizTags(meta.getBizTags());
        newValue.setExt(meta.getExt());
        newValue.setVersionInfo(meta.getVersionInfo());

        for (int i = 0; i < 3; i++) {
            boolean ok = aiResourcePersistService.updateMetaCas(namespaceId, meta.getName(), meta.getType(), expected, newValue);
            if (ok) {
                return;
            }
            AiResource latest = aiResourcePersistService.find(namespaceId, meta.getName(), meta.getType());
            if (latest == null || latest.getMetaVersion() == null) {
                return;
            }
            meta = latest;
            expected = latest.getMetaVersion();
            newValue.setStatus(meta.getStatus());
            newValue.setBizTags(latest.getBizTags());
            newValue.setExt(latest.getExt());
            newValue.setVersionInfo(latest.getVersionInfo());
        }
    }
    
    private QueryCondition buildQueryCondition(String namespaceId, String resourceType, String nameLike,
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
    
    private void ensureReadableOrNotFound(AiResource resource, String notFoundMessage) throws NacosException {
        if (VisibilityHelper.canReadResource(resource)) {
            return;
        }
        throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, notFoundMessage);
    }
    
    private static <T> Page<T> buildEmptyPage(int pageNo) {
        Page<T> page = new Page<>();
        page.setPageItems(new ArrayList<>());
        page.setTotalCount(0);
        page.setPagesAvailable(0);
        page.setPageNumber(pageNo);
        return page;
    }
    
    private static class SkillVersionInfo {
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

    private static class SkillPublishPipelineInfo {

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
}
