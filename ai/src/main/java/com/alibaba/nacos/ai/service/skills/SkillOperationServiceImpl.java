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
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.skills.SkillAdminDetail;
import com.alibaba.nacos.ai.model.skills.SkillAdminListItem;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionResult;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeResult;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.ai.utils.SkillZipParser;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFileContent;
import com.alibaba.nacos.plugin.ai.pipeline.model.SkillPipelineContext;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private static final String META_STATUS_ENABLE = "enable";

    private static final String VERSION_STATUS_ONLINE = "online";

    private static final String DEFAULT_AUTHOR = "nacos";

    private static final String VERSION_STATUS_DRAFT = "draft";

    private static final String VERSION_STATUS_REVIEWING = "reviewing";

    private static final String VERSION_STATUS_OFFLINE = "offline";

    private static final String META_STATUS_DISABLE = "disable";

    private static final String LABEL_LATEST = "latest";

    private static final String SKILL_MD_RESOURCE_NAME = "SKILL.md";

    private static final String SCOPE_SKILL = "skill";

    private static final int MAX_WORKING_VERSION_RETRY = 3;

    private final AiResourceStorageRouter storageRouter;

    private final AiResourcePersistService aiResourcePersistService;

    private final AiResourceVersionPersistService aiResourceVersionPersistService;

    private final PublishPipelineExecutor publishPipelineExecutor;

    private final PipelineExecutionRepository pipelineExecutionRepository;

    public SkillOperationServiceImpl(AiResourcePersistService aiResourcePersistService,
            AiResourceVersionPersistService aiResourceVersionPersistService,
            PublishPipelineExecutor publishPipelineExecutor,
            PipelineExecutionRepository pipelineExecutionRepository) {
        this.storageRouter = AiResourceStorageRouter.getInstance();
        this.aiResourcePersistService = aiResourcePersistService;
        this.aiResourceVersionPersistService = aiResourceVersionPersistService;
        this.publishPipelineExecutor = publishPipelineExecutor;
        this.pipelineExecutionRepository = pipelineExecutionRepository;
    }

    @Override
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, namespaceId);
        if (skill == null || StringUtils.isBlank(skill.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING, "Skill name is required");
        }
        String name = skill.getName();
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            // New skill: create meta row and first draft version v1
            String version = "v1";
            createDraftWithSkill(namespaceId, skill, version, null, true);
            return name;
        }

        SkillVersionInfo info = requireVersionInfo(meta);
        if (StringUtils.isNotBlank(info.getEditingVersion()) || StringUtils.isNotBlank(info.getReviewingVersion())) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    "There is already a working version (editing/reviewing), cannot upload");
        }

        String newVersion = nextVersion(namespaceId, name);
        createDraftWithSkill(namespaceId, skill, newVersion, meta, false);
        return name;
    }

    @Override
    public SkillAdminDetail getSkillDetail(String namespaceId, String skillName) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + skillName);
        }
        SkillVersionInfo versionInfo = requireVersionInfo(meta);

        // Load all version summaries
        Page<AiResourceVersion> versionPage = aiResourceVersionPersistService.listAll(namespaceId, skillName, 1, 200);
        List<SkillAdminDetail.SkillVersionSummary> versionSummaries = new ArrayList<>();
        if (versionPage != null && versionPage.getPageItems() != null) {
            for (AiResourceVersion v : versionPage.getPageItems()) {
                if (v == null) {
                    continue;
                }
                SkillAdminDetail.SkillVersionSummary summary = new SkillAdminDetail.SkillVersionSummary();
                summary.setVersion(v.getVersion());
                summary.setStatus(v.getStatus());
                summary.setAuthor(v.getAuthor());
                summary.setDescription(v.getDesc());
                summary.setCreateTime(v.getGmtCreate() == null ? null : v.getGmtCreate().getTime());
                summary.setUpdateTime(v.getGmtModified() == null ? null : v.getGmtModified().getTime());
                summary.setPublishPipelineInfo(v.getPublishPipelineInfo());
                versionSummaries.add(summary);
            }
        }

        SkillAdminDetail detail = new SkillAdminDetail();
        detail.setEnable(META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus()));
        detail.setEditingVersion(versionInfo.getEditingVersion());
        detail.setReviewingVersion(versionInfo.getReviewingVersion());
        detail.setLabels(versionInfo.getLabels());
        detail.setOnlineCnt(versionInfo.getOnlineCnt());
        detail.setUpdateTime(meta.getGmtModified() == null ? null : meta.getGmtModified().getTime());
        detail.setVersions(versionSummaries);
        return detail;
    }

    @Override
    public Skill getSkillVersionDetail(String namespaceId, String skillName, String version) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + skillName);
        }
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
    public void deleteSkill(String namespaceId, String skillName) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            return;
        }

        // Delete in strict reverse order of creation (storage -> version -> meta -> index):
        // 1) index config first: cut off client discovery immediately
        deleteSkillIndexConfig(namespaceId, skillName);

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
    public Page<SkillAdminListItem> listSkills(String namespaceId, String skillName, String search, int pageNo,
            int pageSize) throws NacosException {
        String nameLike = null;
        if (StringUtils.isNotBlank(skillName)) {
            if (Skills.SEARCH_ACCURATE.equalsIgnoreCase(search)) {
                nameLike = skillName;
            } else {
                nameLike = Constants.ALL_PATTERN + skillName + Constants.ALL_PATTERN;
            }
        }

        Page<AiResource> metaPage = aiResourcePersistService.list(namespaceId, RESOURCE_TYPE_SKILL, nameLike, null, pageNo,
                pageSize);
        List<SkillAdminListItem> items = new ArrayList<>();
        if (metaPage != null && metaPage.getPageItems() != null) {
            for (AiResource meta : metaPage.getPageItems()) {
                if (meta == null) {
                    continue;
                }
                SkillVersionInfo versionInfo = parseVersionInfo(meta.getVersionInfo());
                SkillAdminListItem item = new SkillAdminListItem();
                item.setNamespaceId(namespaceId);
                item.setName(meta.getName());
                item.setDescription(meta.getDesc());
                item.setEnable(META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus()));
                item.setBizTags(meta.getBizTags());
                item.setUpdateTime(meta.getGmtModified() == null ? null : meta.getGmtModified().getTime());
                if (versionInfo != null) {
                    item.setLabels(versionInfo.getLabels());
                    item.setEditingVersion(versionInfo.getEditingVersion());
                    item.setReviewingVersion(versionInfo.getReviewingVersion());
                    item.setOnlineCnt(versionInfo.getOnlineCnt());
                }
                items.add(item);
            }
        }

        Page<SkillAdminListItem> result = new Page<>();
        result.setPageItems(items);
        result.setTotalCount(metaPage == null ? 0 : metaPage.getTotalCount());
        result.setPagesAvailable(metaPage == null ? 0 : metaPage.getPagesAvailable());
        result.setPageNumber(pageNo);
        return result;
    }

    @Override
    public String createDraft(String namespaceId, String name, String basedOnVersion) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL);

        if (meta == null) {
            // Brand-new skill: meta does not exist yet, always create an empty draft
            if (StringUtils.isNotBlank(basedOnVersion)) {
                throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        "Skill not found: " + name + ", cannot use basedOnVersion for a brand-new skill");
            }
            Skill emptySkill = new Skill();
            emptySkill.setName(name);
            emptySkill.setNamespaceId(namespaceId);
            createDraftWithSkill(namespaceId, emptySkill, "v1", null, true);
            return "v1";
        }

        SkillVersionInfo info = requireVersionInfo(meta);
        if (StringUtils.isNotBlank(info.getEditingVersion()) || StringUtils.isNotBlank(info.getReviewingVersion())) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    "There is already a working version (editing/reviewing), cannot create draft");
        }

        String newVersion = nextVersion(namespaceId, name);
        // resolveBaseVersion: explicit param > latest label > max version number; null means no version exists yet
        String base = resolveBaseVersion(namespaceId, name, meta, basedOnVersion);

        if (StringUtils.isBlank(base)) {
            // No version exists yet: create an empty draft
            Skill emptySkill = new Skill();
            emptySkill.setName(name);
            emptySkill.setNamespaceId(namespaceId);
            createDraftWithSkill(namespaceId, emptySkill, newVersion, meta, false);
        } else {
            // Copy storage content from base version
            AiResourceVersion baseVersionRow = aiResourceVersionPersistService.find(namespaceId, name,
                    RESOURCE_TYPE_SKILL, base);
            Skill baseSkill = loadSkillFromStorage(namespaceId, name, base,
                    baseVersionRow != null ? baseVersionRow.getStorage() : null);
            List<String> files = writeSkillToStorage(namespaceId, baseSkill, newVersion);

            AiResourceVersion v = new AiResourceVersion();
            v.setNamespaceId(namespaceId);
            v.setName(name);
            v.setType(RESOURCE_TYPE_SKILL);
            v.setAuthor(DEFAULT_AUTHOR);
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
        String name = draftSkill.getName();
        AiResource meta = requireMeta(namespaceId, name);
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
        
        // Build context for pipeline execution (multi-file skill representation).
        Skill skill = loadSkillFromStorage(namespaceId, name, finalTarget, v.getStorage());
        SkillPipelineContext ctx = new SkillPipelineContext();
        ctx.setNamespaceId(namespaceId);
        ctx.setResourceName(name);
        ctx.setVersion(finalTarget);
        ctx.setFiles(buildPipelineFiles(skill));

        // Execute asynchronously via standard pipeline engine.
        String executionId = publishPipelineExecutor.execute(ctx,
                result -> onPipelineComplete(namespaceId, name, finalTarget, result));
        if (StringUtils.isBlank(executionId)) {
            // Pipeline disabled or no matched nodes -> publish directly.
            publish(namespaceId, name, finalTarget, true);
            return finalTarget;
        }

        // Move to reviewing and record pipeline execution id.
        aiResourceVersionPersistService.updateStatus(namespaceId, name, RESOURCE_TYPE_SKILL, finalTarget, VERSION_STATUS_REVIEWING);
        info.setEditingVersion(null);
        info.setReviewingVersion(finalTarget);
        updateMetaVersionInfoCas(namespaceId, meta, info);

        SkillPublishPipelineInfo pipelineInfo = new SkillPublishPipelineInfo();
        pipelineInfo.setExecutionId(executionId);
        pipelineInfo.setStatus(PipelineExecutionStatus.IN_PROGRESS);
        pipelineInfo.setPipeline(new ArrayList<>());
        aiResourceVersionPersistService.updatePublishPipelineInfo(namespaceId, name, RESOURCE_TYPE_SKILL, finalTarget,
                JacksonUtils.toJson(pipelineInfo));

        return finalTarget;
    }

    @Override
    public void publish(String namespaceId, String name, String version, boolean updateLatestLabel) throws NacosException {
        AiResource meta = requireMeta(namespaceId, name);
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

        // Write skill index config for client-side config caching
        writeSkillIndexConfig(namespaceId, name, version, parseStorageFiles(v.getStorage()));
    }

    @Override
    public void updateLabels(String namespaceId, String name, Map<String, String> labels) throws NacosException {
        AiResource meta = requireMeta(namespaceId, name);
        SkillVersionInfo info = requireVersionInfo(meta);
        info.setLabels(labels == null ? null : new LinkedHashMap<>(labels));
        updateMetaVersionInfoCas(namespaceId, meta, info);
    }

    @Override
    public void changeOnlineStatus(String namespaceId, String name, String scope, String version, boolean online)
            throws NacosException {
        AiResource meta = requireMeta(namespaceId, name);
        SkillVersionInfo info = requireVersionInfo(meta);

        boolean skillScope = SCOPE_SKILL.equalsIgnoreCase(scope) || StringUtils.isBlank(version);
        if (skillScope) {
            metaEnableDisable(namespaceId, meta, online);
            if (online) {
                refreshSkillIndexConfig(namespaceId, name);
            } else {
                deleteSkillIndexConfig(namespaceId, name);
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
    }

    @Override
    public Page<SkillBasicInfo> searchSkills(String namespaceId, String keyword, int pageNo, int pageSize)
            throws NacosException {
        String nameLike = StringUtils.isBlank(keyword) ? null : (Constants.ALL_PATTERN + keyword + Constants.ALL_PATTERN);
        Page<AiResource> metaPage = aiResourcePersistService.list(namespaceId, RESOURCE_TYPE_SKILL, nameLike, null, pageNo,
                pageSize);
        List<SkillBasicInfo> items = new ArrayList<>();
        if (metaPage != null && metaPage.getPageItems() != null) {
            for (AiResource meta : metaPage.getPageItems()) {
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
                // Client only receives name and description
                SkillBasicInfo basicInfo = new SkillBasicInfo();
                basicInfo.setName(meta.getName());
                basicInfo.setDescription(meta.getDesc());
                items.add(basicInfo);
            }
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
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "Skill not found: " + name);
        }
        if (!META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "Skill disabled: " + name);
        }
        String resolved = resolveVersion(meta, version, label);
        if (StringUtils.isBlank(resolved)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill version not found: " + name);
        }
        AiResourceVersion versionRow = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL, resolved);
        if (versionRow == null || !VERSION_STATUS_ONLINE.equalsIgnoreCase(versionRow.getStatus())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill version not online: " + name);
        }
        return loadSkillFromStorage(namespaceId, name, resolved, versionRow.getStorage());
    }

    // ========== Private methods ==========

    private void createDraftWithSkill(String namespaceId, Skill skill, String version, AiResource existedMeta,
            boolean isNewSkill) throws NacosException {
        String skillName = skill.getName();

        // 1) write all resources (including SKILL.md) to storage
        List<String> files = writeSkillToStorage(namespaceId, skill, version);

        // 2) insert draft version row
        AiResourceVersion versionRow = new AiResourceVersion();
        versionRow.setNamespaceId(namespaceId);
        versionRow.setName(skillName);
        versionRow.setType(RESOURCE_TYPE_SKILL);
        versionRow.setAuthor(DEFAULT_AUTHOR);
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
        // No explicit base: try latest label first, then fall back to highest numbered version
        String latest = resolveVersion(meta, null, LABEL_LATEST);
        return StringUtils.isNotBlank(latest) ? latest : maxVersionByNumber(namespaceId, name);
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
     * Write skill index config (manifest) to Nacos Config for client-side caching.
     * Stored at group={@code skill_{name}}, dataId={@code skill_index.json}.
     */
    private void writeSkillIndexConfig(String namespaceId, String skillName, String version, List<String> files)
            throws NacosException {
        Map<String, Object> index = new HashMap<>(4);
        index.put("version", version);
        index.put("files", files);
        String provider = resolveSkillStorageProvider();
        StorageKey key = NacosConfigAiResourceStorage.buildManifestStorageKey(provider, namespaceId, skillName);
        byte[] content = JacksonUtils.toJson(index).getBytes(StandardCharsets.UTF_8);
        storageRouter.route(key).save(key, content);
    }

    /**
     * Delete skill index config.
     */
    private void deleteSkillIndexConfig(String namespaceId, String skillName) throws NacosException {
        String provider = resolveSkillStorageProvider();
        StorageKey key = NacosConfigAiResourceStorage.buildManifestStorageKey(provider, namespaceId, skillName);
        storageRouter.route(key).delete(key);
    }

    /**
     * Refresh skill index config from current meta (used when skill is re-enabled).
     * Best-effort: failures are logged but not propagated.
     */
    private void refreshSkillIndexConfig(String namespaceId, String name) {
        try {
            AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL);
            if (meta == null) {
                return;
            }
            SkillVersionInfo vInfo = parseVersionInfo(meta.getVersionInfo());
            if (vInfo == null || vInfo.getLabels() == null) {
                return;
            }
            String latestVersion = vInfo.getLabels().get(LABEL_LATEST);
            if (StringUtils.isBlank(latestVersion)) {
                return;
            }
            AiResourceVersion versionRow = aiResourceVersionPersistService.find(namespaceId, name,
                    RESOURCE_TYPE_SKILL, latestVersion);
            if (versionRow == null || !VERSION_STATUS_ONLINE.equalsIgnoreCase(versionRow.getStatus())) {
                return;
            }
            List<String> files = parseStorageFiles(versionRow.getStorage());
            if (files == null || files.isEmpty()) {
                return;
            }
            writeSkillIndexConfig(namespaceId, name, latestVersion, files);
        } catch (Exception e) {
            LOGGER.warn("Failed to refresh skill index config for {}: {}", name, e.getMessage());
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

    private String nextVersion(String namespaceId, String name) {
        return "v" + (maxVersionNumber(namespaceId, name) + 1);
    }

    /**
     * Returns the version string with the highest numeric suffix (e.g. "v3" when versions are v1/v2/v3),
     * or null if no numeric versions exist.
     */
    private String maxVersionByNumber(String namespaceId, String name) {
        int max = maxVersionNumber(namespaceId, name);
        return max == 0 ? null : "v" + max;
    }

    /**
     * Returns the highest numeric version number across all existing version rows.
     * Returns 0 if no numeric version exists.
     */
    private int maxVersionNumber(String namespaceId, String name) {
        Page<AiResourceVersion> page = aiResourceVersionPersistService.listAll(namespaceId, name, 1, 200);
        int max = 0;
        if (page != null && page.getPageItems() != null) {
            for (AiResourceVersion v : page.getPageItems()) {
                if (v == null || StringUtils.isBlank(v.getVersion())) {
                    continue;
                }
                String s = v.getVersion().trim();
                if (s.startsWith("v")) {
                    try {
                        int n = Integer.parseInt(s.substring(1));
                        if (n > max) {
                            max = n;
                        }
                    } catch (Exception ignored) {
                        // ignore non-numeric version
                    }
                }
            }
        }
        return max;
    }

    /**
     * Write all skill resources to storage uniformly, including SKILL.md as a SkillResource.
     *
     * @return list of stored file paths (for use in buildStorageJson)
     */
    private List<String> writeSkillToStorage(String namespaceId, Skill skill, String version) throws NacosException {
        String provider = resolveSkillStorageProvider();
        String skillName = skill.getName();
        List<String> files = new ArrayList<>();

        // 1) Store SKILL.md as a SkillResource (carries name/description/instruction in metadata)
        SkillResource skillMdResource = buildSkillMdResource(skill);
        String mdPath = NacosConfigAiResourceStorage.getResourceFilePath(null, SKILL_MD_RESOURCE_NAME);
        byte[] mdBytes = JacksonUtils.toJson(skillMdResource).getBytes(StandardCharsets.UTF_8);
        StorageKey mdKey = NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId, skillName, version,
                mdPath);
        storageRouter.route(mdKey).save(mdKey, mdBytes);
        files.add(mdPath);

        // 2) Store each resource file
        if (skill.getResource() != null && !skill.getResource().isEmpty()) {
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                SkillResource resource = entry.getValue();
                String path = NacosConfigAiResourceStorage.getResourceFilePath(resource.getType(), resource.getName());
                byte[] content = JacksonUtils.toJson(resource).getBytes(StandardCharsets.UTF_8);
                StorageKey resourceKey = NacosConfigAiResourceStorage.buildStorageKey(provider, namespaceId, skillName,
                        version, path);
                storageRouter.route(resourceKey).save(resourceKey, content);
                files.add(path);
            }
        }

        return files;
    }

    /**
     * Build SKILL.md as a SkillResource with markdown content and metadata for name/description/instruction.
     */
    private static SkillResource buildSkillMdResource(Skill skill) {
        SkillResource resource = new SkillResource();
        resource.setName(SKILL_MD_RESOURCE_NAME);
        resource.setContent(SkillUtils.toMarkdown(skill));
        Map<String, Object> metadata = new HashMap<>(4);
        metadata.put("name", skill.getName());
        metadata.put("description", skill.getDescription());
        metadata.put("instruction", skill.getInstruction());
        resource.setMetadata(metadata);
        return resource;
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
        skillMd.setContent(SkillUtils.toMarkdown(skill));
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
     * SKILL.md resource provides name/description/instruction; others populate the resource map.
     */
    private Skill loadSkillFromStorage(String namespaceId, String skillName, String version, String storageJson)
            throws NacosException {
        List<String> files = parseStorageFiles(storageJson);
        if (files == null || files.isEmpty()) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "No files found in storage for skill: " + skillName + "@" + version);
        }

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
            SkillResource resource = JacksonUtils.toObj(new String(bytes, StandardCharsets.UTF_8), SkillResource.class);
            if (resource == null) {
                continue;
            }
            if (SKILL_MD_RESOURCE_NAME.equals(resource.getName())) {
                // Extract name/description/instruction from SKILL.md metadata
                Map<String, Object> metadata = resource.getMetadata();
                if (metadata != null) {
                    skill.setName((String) metadata.get("name"));
                    skill.setDescription((String) metadata.get("description"));
                    skill.setInstruction((String) metadata.get("instruction"));
                }
            } else {
                String resourceId = SkillUtils.generateResourceId(resource.getType(), resource.getName());
                resourceMap.put(resourceId, resource);
            }
        }

        skill.setResource(resourceMap);
        return skill;
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
            newValue.setBizTags(meta.getBizTags());
            newValue.setExt(meta.getExt());
            newValue.setVersionInfo(meta.getVersionInfo());
        }
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
