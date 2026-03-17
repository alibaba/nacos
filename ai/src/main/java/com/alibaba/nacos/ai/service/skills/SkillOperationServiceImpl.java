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
import com.alibaba.nacos.ai.service.SyncEffectService;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.utils.SkillZipParser;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFileContent;
import com.alibaba.nacos.plugin.ai.pipeline.model.SkillPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;
import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineServiceBuilder;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.alibaba.nacos.ai.constant.Constants.Skills;

/**
 * Skill operation service implementation.
 *
 * @author nacos
 */
@org.springframework.stereotype.Service
public class SkillOperationServiceImpl implements SkillOperationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillOperationServiceImpl.class);
    
    private static final String SKILL_NAME_PATTERN = "^[a-zA-Z_-]+$";

    private static final String STORAGE_PROVIDER_NACOS_CONFIG = "nacos_config";

    private static final String RESOURCE_TYPE_SKILL = "skill";

    private static final String META_STATUS_ENABLE = "enable";

    private static final String VERSION_STATUS_ONLINE = "online";

    private static final String DEFAULT_AUTHOR = "nacos";

    private static final String VERSION_STATUS_DRAFT = "draft";

    private static final String VERSION_STATUS_REVIEWING = "reviewing";

    private static final String VERSION_STATUS_OFFLINE = "offline";

    private static final String META_STATUS_DISABLE = "disable";

    private static final String LABEL_LATEST = "latest";

    private static final String SCOPE_SKILL = "skill";

    private static final int MAX_WORKING_VERSION_RETRY = 3;

    private final ExecutorService pipelineExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "nacos-skill-pipeline");
        t.setDaemon(true);
        return t;
    });
    
    private final SyncEffectService syncEffectService;

    private final AiResourceStorageRouter storageRouter;
    
    private final AiResourcePersistService aiResourcePersistService;
    
    private final AiResourceVersionPersistService aiResourceVersionPersistService;
    
    public SkillOperationServiceImpl(SyncEffectService syncEffectService, AiResourcePersistService aiResourcePersistService,
            AiResourceVersionPersistService aiResourceVersionPersistService) {
        this.syncEffectService = syncEffectService;
        this.storageRouter = AiResourceStorageRouter.getInstance();
        this.aiResourcePersistService = aiResourcePersistService;
        this.aiResourceVersionPersistService = aiResourceVersionPersistService;
    }
    
    @Override
    public String registerSkill(Skill skill, String namespaceId) throws NacosException {
        // 1. Validate skill name (only allow English letters, underscore, hyphen)
        if (StringUtils.isBlank(skill.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Skill name is required");
        }
        if (!skill.getName().matches(SKILL_NAME_PATTERN)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Skill name can only contain English letters, underscore, and hyphen");
        }

        String skillName = skill.getName();
        AiResource existed = aiResourcePersistService.find(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (existed != null) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    String.format("Skill name %s already exists", skillName));
        }

        String version = "v1";
        long uniformId = System.currentTimeMillis();
        String versionGroup = buildSkillVersionGroup(skillName, version);

        // 2. Build main config (skill.json) for version group
        SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skillName);
        ConfigForm mainConfigForm = buildMainConfigForm(skill, namespaceId, versionGroup, uniformId);

        // 3. Save configs via Storage (version scoped)
        StorageKey mainKey = nacosConfigKey(namespaceId, versionGroup, mainConfigInfo.getDataId());
        storageRouter.route(mainKey).save(mainKey, mainConfigForm.getContent().getBytes(StandardCharsets.UTF_8));

        if (skill.getResource() != null && !skill.getResource().isEmpty()) {
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                SkillResource resource = entry.getValue();
                SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                        skillName, resource.getType(), resource.getName());

                ConfigForm resourceConfigForm = buildResourceConfigForm(resource, namespaceId, versionGroup,
                        resourceConfigInfo.getDataId(), uniformId);

                StorageKey resourceKey = nacosConfigKey(namespaceId, versionGroup, resourceConfigInfo.getDataId());
                storageRouter.route(resourceKey).save(resourceKey, resourceConfigForm.getContent().getBytes(StandardCharsets.UTF_8));
            }
        }

        // 4. Insert ai_resource_version
        AiResourceVersion versionRow = new AiResourceVersion();
        versionRow.setNamespaceId(namespaceId);
        versionRow.setName(skillName);
        versionRow.setType(RESOURCE_TYPE_SKILL);
        versionRow.setAuthor(DEFAULT_AUTHOR);
        versionRow.setStatus(VERSION_STATUS_ONLINE);
        versionRow.setVersion(version);
        versionRow.setDesc(skill.getDescription());
        versionRow.setStorage(buildStorageJson(versionGroup));
        aiResourceVersionPersistService.insert(versionRow);

        // 5. Insert ai_resource meta
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName(skillName);
        meta.setType(RESOURCE_TYPE_SKILL);
        meta.setStatus(META_STATUS_ENABLE);
        meta.setDesc(skill.getDescription());
        SkillVersionInfo info = new SkillVersionInfo();
        info.setOnlineCnt(1);
        info.setLabels(Map.of("latest", version));
        meta.setVersionInfo(JacksonUtils.toJson(info));
        meta.setMetaVersion(1L);
        aiResourcePersistService.insert(meta);

        syncEffectService.toSync(mainConfigForm, System.currentTimeMillis());
        return skillName;
    }
    
    @Override
    public Skill getSkillDetail(String namespaceId, String skillName) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + skillName);
        }
        String version = resolveVersion(meta, null, null);
        if (StringUtils.isBlank(version)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill version not found: " + skillName);
        }
        return loadSkillFromStorage(namespaceId, skillName, version);
    }
    
    @Override
    public void updateSkill(Skill skill, String namespaceId) throws NacosException {
        String skillName = skill.getName();
        AiResource meta = aiResourcePersistService.find(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + skillName);
        }
        String version = resolveVersion(meta, null, null);
        if (StringUtils.isBlank(version)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill version not found: " + skillName);
        }

        String versionGroup = buildSkillVersionGroup(skillName, version);
        SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skillName);
        StorageKey mainKey = nacosConfigKey(namespaceId, versionGroup, mainConfigInfo.getDataId());
        byte[] existingMainBytes = storageRouter.route(mainKey).get(mainKey);
        if (existingMainBytes == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill storage not found: " + skillName);
        }

        SkillMainConfig existingMainConfig = JacksonUtils.toObj(new String(existingMainBytes, StandardCharsets.UTF_8),
                SkillMainConfig.class);

        long uniformId = System.currentTimeMillis();
        ConfigForm mainConfigForm = buildMainConfigForm(skill, namespaceId, versionGroup, uniformId);
        storageRouter.route(mainKey).save(mainKey, mainConfigForm.getContent().getBytes(StandardCharsets.UTF_8));

        updateSkillResourcesInGroup(storageRouter, skill, namespaceId, versionGroup, uniformId);
        deleteRemovedResourcesInGroup(storageRouter, skillName, namespaceId, versionGroup, existingMainConfig,
                skill.getResource() == null ? Map.of() : skill.getResource());

        bumpMetaDescription(namespaceId, meta, skill.getDescription());
        syncEffectService.toSync(mainConfigForm, System.currentTimeMillis());
    }
    
    private void updateSkillResourcesInGroup(AiResourceStorageRouter router, Skill skill, String namespaceId,
            String versionGroup, long uniformId) throws NacosException {
        if (skill.getResource() == null || skill.getResource().isEmpty()) {
            return;
        }
        for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
            SkillResource resource = entry.getValue();
            SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                    skill.getName(), resource.getType(), resource.getName());
            ConfigForm resourceConfigForm = buildResourceConfigForm(resource, namespaceId, versionGroup,
                    resourceConfigInfo.getDataId(), uniformId);
            StorageKey resourceKey = nacosConfigKey(namespaceId, versionGroup, resourceConfigInfo.getDataId());
            router.route(resourceKey).save(resourceKey, resourceConfigForm.getContent().getBytes(StandardCharsets.UTF_8));
        }
    }
    
    private void deleteRemovedResourcesInGroup(AiResourceStorageRouter router, String skillName, String namespaceId,
            String versionGroup, SkillMainConfig existingMainConfig, Map<String, SkillResource> newResources)
            throws NacosException {
        if (existingMainConfig.getResources() == null || existingMainConfig.getResources().isEmpty()) {
            return;
        }
        for (SkillResourceRef resourceRef : existingMainConfig.getResources()) {
            String key = SkillUtils.generateResourceId(resourceRef.getType(), resourceRef.getName());
            if (!newResources.containsKey(key)) {
                SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                        skillName, resourceRef.getType(), resourceRef.getName());
                StorageKey resourceKey = nacosConfigKey(namespaceId, versionGroup, resourceConfigInfo.getDataId());
                router.route(resourceKey).delete(resourceKey);
            }
        }
    }
    
    @Override
    public void deleteSkill(String namespaceId, String skillName) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, skillName, RESOURCE_TYPE_SKILL);
        if (meta == null) {
            return;
        }

        Page<AiResourceVersion> versions = aiResourceVersionPersistService.listAll(namespaceId, skillName, 1, 200);
        if (versions != null && versions.getPageItems() != null) {
            for (AiResourceVersion v : versions.getPageItems()) {
                if (v == null || StringUtils.isBlank(v.getVersion())) {
                    continue;
                }
                deleteSkillStorageForVersion(namespaceId, skillName, v.getVersion());
            }
        }

        aiResourceVersionPersistService.deleteByName(namespaceId, skillName);
        aiResourcePersistService.delete(namespaceId, skillName, RESOURCE_TYPE_SKILL);
    }
    
    @Override
    public Page<SkillBasicInfo> listSkills(String namespaceId, String skillName, String search, int pageNo,
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
        List<SkillBasicInfo> items = new ArrayList<>();
        if (metaPage != null && metaPage.getPageItems() != null) {
            for (AiResource meta : metaPage.getPageItems()) {
                if (meta == null) {
                    continue;
                }
                SkillBasicInfo basicInfo = new SkillBasicInfo();
                basicInfo.setNamespaceId(namespaceId);
                basicInfo.setName(meta.getName());
                basicInfo.setDescription(meta.getDesc());
                basicInfo.setUpdateTime(meta.getGmtModified() == null ? null : meta.getGmtModified().getTime());
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
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, namespaceId);
        return registerSkill(skill, namespaceId);
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
                SkillBasicInfo basicInfo = new SkillBasicInfo();
                basicInfo.setNamespaceId(namespaceId);
                basicInfo.setName(meta.getName());
                basicInfo.setDescription(meta.getDesc());
                basicInfo.setUpdateTime(meta.getGmtModified() == null ? null : meta.getGmtModified().getTime());
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
        return loadSkillFromStorage(namespaceId, name, resolved);
    }

    @Override
    public String createDraft(String namespaceId, String name, String basedOnVersion) throws NacosException {
        AiResource meta = requireMeta(namespaceId, name);
        SkillVersionInfo info = requireVersionInfo(meta);
        if (StringUtils.isNotBlank(info.getEditingVersion()) || StringUtils.isNotBlank(info.getReviewingVersion())) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    "There is already a working version (editing/reviewing), cannot create draft");
        }

        String base = StringUtils.isBlank(basedOnVersion) ? resolveVersion(meta, null, LABEL_LATEST) : basedOnVersion;
        if (StringUtils.isBlank(base)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Base version not found for skill: " + name);
        }

        String newVersion = nextVersion(namespaceId, name);
        String dstGroup = buildSkillVersionGroup(name, newVersion);

        // 1) copy storage content (skill.json + resources)
        Skill baseSkill = loadSkillFromStorage(namespaceId, name, base);
        long uniformId = System.currentTimeMillis();
        writeSkillToGroup(namespaceId, baseSkill, dstGroup, uniformId);

        // 2) insert draft version row
        AiResourceVersion v = new AiResourceVersion();
        v.setNamespaceId(namespaceId);
        v.setName(name);
        v.setType(RESOURCE_TYPE_SKILL);
        v.setAuthor(DEFAULT_AUTHOR);
        v.setStatus(VERSION_STATUS_DRAFT);
        v.setVersion(newVersion);
        v.setDesc(baseSkill.getDescription());
        v.setStorage(buildStorageJson(dstGroup));
        aiResourceVersionPersistService.insert(v);

        // 3) update meta pointers (editingVersion)
        info.setEditingVersion(newVersion);
        updateMetaVersionInfoCas(namespaceId, meta, info);
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

        String draftGroup = buildSkillVersionGroup(name, editing);
        long uniformId = System.currentTimeMillis();
        writeSkillToGroup(namespaceId, draftSkill, draftGroup, uniformId);
        aiResourceVersionPersistService.updateStorage(namespaceId, name, RESOURCE_TYPE_SKILL, editing, buildStorageJson(draftGroup));
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
        AiResourceVersion v = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL, editing);
        if (v != null && VERSION_STATUS_DRAFT.equalsIgnoreCase(v.getStatus())) {
            deleteSkillStorageForVersion(namespaceId, name, editing);
            aiResourceVersionPersistService.delete(namespaceId, name, RESOURCE_TYPE_SKILL, editing);
        }
        info.setEditingVersion(null);
        updateMetaVersionInfoCas(namespaceId, meta, info);
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

        List<PublishPipelineService> pipelines = findSkillPipelines();
        if (pipelines.isEmpty()) {
            publish(namespaceId, name, target, true);
            return target;
        }

        // move to reviewing
        aiResourceVersionPersistService.updateStatus(namespaceId, name, RESOURCE_TYPE_SKILL, target, VERSION_STATUS_REVIEWING);
        info.setEditingVersion(null);
        info.setReviewingVersion(target);
        updateMetaVersionInfoCas(namespaceId, meta, info);

        String reviewingVersion = target;
        pipelineExecutor.execute(() -> runPipelineAsync(namespaceId, name, reviewingVersion));
        return reviewingVersion;
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

        List<PublishPipelineService> pipelines = findSkillPipelines();
        if (!pipelines.isEmpty()) {
            PipelineSnapshot snapshot = parsePipelineSnapshot(v.getPublishPipelineInfo());
            if (snapshot == null || !snapshot.isAllPassed()) {
                throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                        "Pipeline not passed, cannot publish: " + version);
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
    
    /**
     * Build main config form.
     */
    private ConfigForm buildMainConfigForm(Skill skill, String namespaceId, String skillGroup, long uniformId) {
        // Build main config (only references, no content)
        SkillMainConfig mainConfig = new SkillMainConfig();
        mainConfig.setName(skill.getName());
        mainConfig.setDescription(skill.getDescription());
        mainConfig.setInstruction(skill.getInstruction());
        mainConfig.setUniformId(uniformId);
        
        // Build resource references (without content)
        List<SkillResourceRef> resourceRefs = new ArrayList<>(
                skill.getResource() != null ? skill.getResource().size() : 16);
        if (skill.getResource() != null) {
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                SkillResource resource = entry.getValue();
                SkillResourceRef ref = new SkillResourceRef();
                ref.setName(resource.getName());
                ref.setType(resource.getType());
                resourceRefs.add(ref);
            }
        }
        mainConfig.setResources(resourceRefs);
        
        SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skill.getName());
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(mainConfigInfo.getDataId());
        configForm.setGroup(skillGroup);
        configForm.setNamespaceId(namespaceId);
        configForm.setContent(JacksonUtils.toJson(mainConfig));
        configForm.setConfigTags("nacos.internal.config=skill");
        configForm.setSrcUser("nacos");
        
        return configForm;
    }
    
    /**
     * Build resource config form.
     */
    private ConfigForm buildResourceConfigForm(SkillResource resource, String namespaceId, String skillGroup,
            String resourceDataId, long uniformId) {
        // Add uniformId to resource metadata
        Map<String, Object> metadata = resource.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>(4);
            resource.setMetadata(metadata);
        }
        metadata.put("uniformId", uniformId);
        
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(resourceDataId);
        configForm.setGroup(skillGroup);
        configForm.setNamespaceId(namespaceId);
        configForm.setContent(JacksonUtils.toJson(resource));
        configForm.setConfigTags("nacos.internal.config=skill-resource");
        configForm.setSrcUser("nacos");
        
        return configForm;
    }

    private static StorageKey nacosConfigKey(String namespaceId, String group, String dataId) {
        return new StorageKey(STORAGE_PROVIDER_NACOS_CONFIG, namespaceId + ":" + group + ":" + dataId);
    }

    private static String buildSkillVersionGroup(String skillName, String version) {
        return SkillUtils.SKILL_GROUP_PREFIX + skillName + "__" + version;
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

    private static String buildStorageJson(String versionGroup) {
        Map<String, Object> json = new HashMap<>(4);
        json.put("provider", STORAGE_PROVIDER_NACOS_CONFIG);
        json.put("group", versionGroup);
        return JacksonUtils.toJson(json);
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
        return "v" + (max + 1);
    }

    private void writeSkillToGroup(String namespaceId, Skill skill, String versionGroup, long uniformId) throws NacosException {
        SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skill.getName());
        ConfigForm mainConfigForm = buildMainConfigForm(skill, namespaceId, versionGroup, uniformId);
        StorageKey mainKey = nacosConfigKey(namespaceId, versionGroup, mainConfigInfo.getDataId());
        storageRouter.route(mainKey).save(mainKey, mainConfigForm.getContent().getBytes(StandardCharsets.UTF_8));
        if (skill.getResource() != null && !skill.getResource().isEmpty()) {
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                SkillResource resource = entry.getValue();
                SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                        skill.getName(), resource.getType(), resource.getName());
                ConfigForm resourceConfigForm = buildResourceConfigForm(resource, namespaceId, versionGroup,
                        resourceConfigInfo.getDataId(), uniformId);
                StorageKey resourceKey = nacosConfigKey(namespaceId, versionGroup, resourceConfigInfo.getDataId());
                storageRouter.route(resourceKey).save(resourceKey, resourceConfigForm.getContent().getBytes(StandardCharsets.UTF_8));
            }
        }
        syncEffectService.toSync(mainConfigForm, System.currentTimeMillis());
    }

    private List<PublishPipelineService> findSkillPipelines() {
        List<PublishPipelineService> result = new ArrayList<>();
        for (PublishPipelineServiceBuilder b : NacosServiceLoader.load(PublishPipelineServiceBuilder.class)) {
            try {
                PublishPipelineService svc = b.build();
                if (svc != null) {
                    result.add(svc);
                }
            } catch (Throwable ex) {
                LOGGER.warn("Failed to build pipeline service: {}", b, ex);
            }
        }
        // fallback: allow direct service SPI as well
        for (PublishPipelineService svc : NacosServiceLoader.load(PublishPipelineService.class)) {
            if (svc != null) {
                result.add(svc);
            }
        }
        result.removeIf(svc -> svc.pipelineResourceTypes() == null
                || java.util.Arrays.stream(svc.pipelineResourceTypes()).noneMatch(t -> t != null && "SKILL".equals(t.name())));
        result.sort((a, b) -> Integer.compare(a.getPreferOrder(), b.getPreferOrder()));
        return result;
    }

    private void runPipelineAsync(String namespaceId, String name, String version) {
        try {
            List<PublishPipelineService> pipelines = findSkillPipelines();
            if (pipelines.isEmpty()) {
                return;
            }
            Skill skill = loadSkillFromStorage(namespaceId, name, version);
            SkillPipelineContext ctx = new SkillPipelineContext();
            ctx.setNamespaceId(namespaceId);
            ctx.setResourceName(name);
            ctx.setVersion(version);
            ctx.setFiles(buildPipelineFiles(skill));

            List<Map<String, Object>> executed = new ArrayList<>();
            boolean allPassed = true;
            for (PublishPipelineService p : pipelines) {
                long start = System.currentTimeMillis();
                PublishPipelineResult r = p.execute(ctx);
                long dur = System.currentTimeMillis() - start;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("pipelineId", p.pipelineId());
                item.put("executedAt", System.currentTimeMillis());
                if (r != null) {
                    item.put("passed", r.isPassed());
                    item.put("comments", r.getComments());
                } else {
                    item.put("passed", false);
                }
                item.put("costMs", dur);
                executed.add(item);
                if (r == null || !r.isPassed()) {
                    allPassed = false;
                    break;
                }
            }

            PipelineSnapshot snapshot = new PipelineSnapshot();
            snapshot.setAllPassed(allPassed);
            snapshot.setPipeline(executed);
            aiResourceVersionPersistService.updatePublishPipelineInfo(namespaceId, name, RESOURCE_TYPE_SKILL, version,
                    JacksonUtils.toJson(snapshot));

            if (!allPassed) {
                // reject back to draft and move reviewing -> editing
                aiResourceVersionPersistService.updateStatus(namespaceId, name, RESOURCE_TYPE_SKILL, version, VERSION_STATUS_DRAFT);
                AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_SKILL);
                if (meta != null) {
                    SkillVersionInfo info = requireVersionInfo(meta);
                    if (StringUtils.equals(info.getReviewingVersion(), version)) {
                        info.setReviewingVersion(null);
                        info.setEditingVersion(version);
                        try {
                            updateMetaVersionInfoCas(namespaceId, meta, info);
                        } catch (Exception ex) {
                            LOGGER.warn("Failed to rollback meta working pointers for {}@{}", name, version, ex);
                        }
                    }
                }
            }
        } catch (Throwable ex) {
            LOGGER.error("Run pipeline failed for {}@{}", name, version, ex);
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

    private static PipelineSnapshot parsePipelineSnapshot(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JacksonUtils.toObj(json, PipelineSnapshot.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Skill loadSkillFromStorage(String namespaceId, String skillName, String version) throws NacosException {
        String versionGroup = buildSkillVersionGroup(skillName, version);
        SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skillName);
        StorageKey mainKey = nacosConfigKey(namespaceId, versionGroup, mainConfigInfo.getDataId());
        byte[] mainBytes = storageRouter.route(mainKey).get(mainKey);
        if (mainBytes == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + skillName);
        }

        SkillMainConfig mainConfig = JacksonUtils.toObj(new String(mainBytes, StandardCharsets.UTF_8), SkillMainConfig.class);
        Skill skill = new Skill();
        skill.setNamespaceId(namespaceId);
        skill.setName(mainConfig.getName());
        skill.setDescription(mainConfig.getDescription());
        skill.setInstruction(mainConfig.getInstruction());

        Map<String, SkillResource> resourceMap = new HashMap<>(
                mainConfig.getResources() != null ? mainConfig.getResources().size() : 16);
        if (mainConfig.getResources() != null && !mainConfig.getResources().isEmpty()) {
            for (SkillResourceRef resourceRef : mainConfig.getResources()) {
                String resourceId = SkillUtils.generateResourceId(resourceRef.getType(), resourceRef.getName());
                SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                        skillName, resourceRef.getType(), resourceRef.getName());
                StorageKey resourceKey = nacosConfigKey(namespaceId, versionGroup, resourceConfigInfo.getDataId());
                byte[] resourceBytes = storageRouter.route(resourceKey).get(resourceKey);
                if (resourceBytes != null) {
                    SkillResource resource = JacksonUtils.toObj(new String(resourceBytes, StandardCharsets.UTF_8), SkillResource.class);
                    resourceMap.put(resourceId, resource);
                }
            }
        }
        skill.setResource(resourceMap);
        return skill;
    }

    private void deleteSkillStorageForVersion(String namespaceId, String skillName, String version) throws NacosException {
        String versionGroup = buildSkillVersionGroup(skillName, version);
        SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skillName);
        StorageKey mainKey = nacosConfigKey(namespaceId, versionGroup, mainConfigInfo.getDataId());
        byte[] mainBytes = storageRouter.route(mainKey).get(mainKey);
        if (mainBytes != null) {
            SkillMainConfig mainConfig = JacksonUtils.toObj(new String(mainBytes, StandardCharsets.UTF_8), SkillMainConfig.class);
            if (mainConfig.getResources() != null && !mainConfig.getResources().isEmpty()) {
                for (SkillResourceRef resourceRef : mainConfig.getResources()) {
                    SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                            skillName, resourceRef.getType(), resourceRef.getName());
                    StorageKey resourceKey = nacosConfigKey(namespaceId, versionGroup, resourceConfigInfo.getDataId());
                    storageRouter.route(resourceKey).delete(resourceKey);
                }
            }
        }
        storageRouter.route(mainKey).delete(mainKey);
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
    
    /**
     * Skill main config (from skill.json).
     */
    private static class SkillMainConfig {
        private String name;
        private String description;
        private String instruction;
        private Long uniformId;
        private List<SkillResourceRef> resources;
        
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
        
        public String getInstruction() {
            return instruction;
        }
        
        public void setInstruction(String instruction) {
            this.instruction = instruction;
        }
        
        public Long getUniformId() {
            return uniformId;
        }
        
        public void setUniformId(Long uniformId) {
            this.uniformId = uniformId;
        }
        
        public List<SkillResourceRef> getResources() {
            return resources;
        }
        
        public void setResources(List<SkillResourceRef> resources) {
            this.resources = resources;
        }
    }
    
    /**
     * Skill resource reference (in skill.json).
     */
    private static class SkillResourceRef {
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

    private static class PipelineSnapshot {
        private boolean allPassed;
        private List<Map<String, Object>> pipeline;

        public boolean isAllPassed() {
            return allPassed;
        }

        public void setAllPassed(boolean allPassed) {
            this.allPassed = allPassed;
        }

        public List<Map<String, Object>> getPipeline() {
            return pipeline;
        }

        public void setPipeline(List<Map<String, Object>> pipeline) {
            this.pipeline = pipeline;
        }
    }
}
