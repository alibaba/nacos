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

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.agentspecs.AgentSpecAdminDetail;
import com.alibaba.nacos.ai.model.agentspecs.AgentSpecAdminListItem;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecution;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionResult;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeResult;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.ai.utils.AgentSpecZipParser;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecBasicInfo;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.pipeline.model.AgentSpecPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFileContent;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AgentSpec operation service implementation. Mirrors {@code SkillOperationServiceImpl} with AgentSpec types.
 *
 * @author nacos
 */
@Service
public class AgentSpecOperationServiceImpl implements AgentSpecOperationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentSpecOperationServiceImpl.class);
    
    private static final String STORAGE_PROVIDER_NACOS_CONFIG = "nacos_config";
    
    private static final String RESOURCE_TYPE_AGENTSPEC = "agentspec";
    
    private static final String META_STATUS_ENABLE = "enable";
    
    private static final String META_STATUS_DISABLE = "disable";
    
    private static final String VERSION_STATUS_ONLINE = "online";
    
    private static final String VERSION_STATUS_DRAFT = "draft";
    
    private static final String VERSION_STATUS_REVIEWING = "reviewing";
    
    private static final String VERSION_STATUS_OFFLINE = "offline";
    
    private static final String DEFAULT_AUTHOR = "nacos";
    
    private static final String LABEL_LATEST = "latest";
    
    private static final String SCOPE_AGENTSPEC = "agentspec";
    
    private static final int MAX_WORKING_VERSION_RETRY = 3;
    
    private final AiResourceStorageRouter storageRouter;
    
    private final AiResourcePersistService aiResourcePersistService;
    
    private final AiResourceVersionPersistService aiResourceVersionPersistService;
    
    private final PublishPipelineExecutor publishPipelineExecutor;
    
    private final PipelineExecutionRepository pipelineExecutionRepository;
    
    public AgentSpecOperationServiceImpl(AiResourcePersistService aiResourcePersistService,
            AiResourceVersionPersistService aiResourceVersionPersistService,
            PublishPipelineExecutor publishPipelineExecutor,
            PipelineExecutionRepository pipelineExecutionRepository) {
        this.storageRouter = AiResourceStorageRouter.getInstance();
        this.aiResourcePersistService = aiResourcePersistService;
        this.aiResourceVersionPersistService = aiResourceVersionPersistService;
        this.publishPipelineExecutor = publishPipelineExecutor;
        this.pipelineExecutionRepository = pipelineExecutionRepository;
    }
    
    private void createDraftWithAgentSpec(String namespaceId, AgentSpec agentSpec, String version,
            AiResource existedMeta, boolean isNew) throws NacosException {
        String agentSpecName = agentSpec.getName();
        long uniformId = System.currentTimeMillis();
        
        // 1) write storage for draft version
        byte[] mainContent = buildMainContent(agentSpec, uniformId);
        StorageKey mainKey = NacosConfigAiResourceStorage.buildStorageKey(resolveStorageProvider(), namespaceId,
                NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, agentSpecName, version,
                NacosConfigAiResourceStorage.getMainFilePath(AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID));
        storageRouter.route(mainKey).save(mainKey, mainContent);
        
        if (agentSpec.getResource() != null && !agentSpec.getResource().isEmpty()) {
            for (Map.Entry<String, AgentSpecResource> entry : agentSpec.getResource().entrySet()) {
                AgentSpecResource resource = entry.getValue();
                String path = NacosConfigAiResourceStorage.getAgentSpecResourceFilePath(resource.getType(),
                        resource.getName());
                byte[] resourceContent = buildResourceContent(resource, uniformId);
                StorageKey resourceKey = NacosConfigAiResourceStorage.buildStorageKey(resolveStorageProvider(),
                        namespaceId, NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, agentSpecName, version,
                        path);
                storageRouter.route(resourceKey).save(resourceKey, resourceContent);
            }
        }
        
        // 2) insert draft version row
        AiResourceVersion versionRow = new AiResourceVersion();
        versionRow.setNamespaceId(namespaceId);
        versionRow.setName(agentSpecName);
        versionRow.setType(RESOURCE_TYPE_AGENTSPEC);
        versionRow.setAuthor(DEFAULT_AUTHOR);
        versionRow.setStatus(VERSION_STATUS_DRAFT);
        versionRow.setVersion(version);
        versionRow.setDesc(agentSpec.getDescription());
        versionRow.setStorage(buildStorageJson(namespaceId, agentSpecName, version));
        aiResourceVersionPersistService.insert(versionRow);
        
        // 3) create or update meta for editingVersion
        if (isNew) {
            AiResource meta = new AiResource();
            meta.setNamespaceId(namespaceId);
            meta.setName(agentSpecName);
            meta.setType(RESOURCE_TYPE_AGENTSPEC);
            meta.setStatus(META_STATUS_ENABLE);
            meta.setDesc(agentSpec.getDescription());
            AgentSpecVersionInfo info = new AgentSpecVersionInfo();
            info.setEditingVersion(version);
            info.setOnlineCnt(0);
            info.setLabels(new HashMap<>(4));
            meta.setVersionInfo(JacksonUtils.toJson(info));
            meta.setMetaVersion(1L);
            aiResourcePersistService.insert(meta);
        } else if (existedMeta != null) {
            AgentSpecVersionInfo info = requireVersionInfo(existedMeta);
            info.setEditingVersion(version);
            updateMetaVersionInfoCas(namespaceId, existedMeta, info);
        }
    }
    
    @Override
    public AgentSpecAdminDetail getAgentSpecDetail(String namespaceId, String agentSpecName, String version)
            throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec not found: " + agentSpecName);
        }
        AgentSpecVersionInfo versionInfo = requireVersionInfo(meta);
        String resolvedVersion = StringUtils.isBlank(version) ? resolveVersion(meta, null, null) : version;
        
        AgentSpec agentSpec = null;
        AiResourceVersion versionRow = null;
        if (StringUtils.isNotBlank(resolvedVersion)) {
            versionRow = aiResourceVersionPersistService.find(namespaceId, agentSpecName,
                    RESOURCE_TYPE_AGENTSPEC, resolvedVersion);
            if (versionRow == null) {
                throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        "AgentSpec version not found: " + agentSpecName + "@" + resolvedVersion);
            }
            try {
                agentSpec = loadAgentSpecFromStorage(namespaceId, agentSpecName, resolvedVersion);
            } catch (NacosException ignored) {
                // version row exists but storage missing
            }
        }
        
        String versionStatus = null;
        if (versionRow != null) {
            versionStatus = versionRow.getStatus();
        }
        
        Page<AiResourceVersion> versionPage = aiResourceVersionPersistService.listAll(namespaceId, agentSpecName, 1,
                200);
        List<AgentSpecAdminDetail.AgentSpecVersionSummary> versionSummaries = new ArrayList<>();
        if (versionPage != null && versionPage.getPageItems() != null) {
            for (AiResourceVersion v : versionPage.getPageItems()) {
                if (v == null) {
                    continue;
                }
                AgentSpecAdminDetail.AgentSpecVersionSummary summary =
                        new AgentSpecAdminDetail.AgentSpecVersionSummary();
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
        
        AgentSpecAdminDetail detail = new AgentSpecAdminDetail();
        detail.setAgentSpec(agentSpec);
        detail.setEnable(META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus()));
        detail.setVersion(resolvedVersion);
        detail.setVersionStatus(versionStatus);
        detail.setEditingVersion(versionInfo.getEditingVersion());
        detail.setReviewingVersion(versionInfo.getReviewingVersion());
        detail.setLabels(versionInfo.getLabels());
        detail.setOnlineCnt(versionInfo.getOnlineCnt());
        detail.setUpdateTime(meta.getGmtModified() == null ? null : meta.getGmtModified().getTime());
        detail.setVersions(versionSummaries);
        return detail;
    }

    @Override
    public AgentSpecAdminDetail getAgentSpecDetail(String namespaceId, String agentSpecName) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec not found: " + agentSpecName);
        }
        AgentSpecVersionInfo versionInfo = requireVersionInfo(meta);
        
        // Load all version summaries
        Page<AiResourceVersion> versionPage = aiResourceVersionPersistService.listAll(namespaceId, agentSpecName, 1,
                200);
        List<AgentSpecAdminDetail.AgentSpecVersionSummary> versionSummaries = new ArrayList<>();
        if (versionPage != null && versionPage.getPageItems() != null) {
            for (AiResourceVersion v : versionPage.getPageItems()) {
                if (v == null) {
                    continue;
                }
                AgentSpecAdminDetail.AgentSpecVersionSummary summary =
                        new AgentSpecAdminDetail.AgentSpecVersionSummary();
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
        
        AgentSpecAdminDetail detail = new AgentSpecAdminDetail();
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
    public AgentSpec getAgentSpecVersionDetail(String namespaceId, String agentSpecName, String version)
            throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec not found: " + agentSpecName);
        }
        if (StringUtils.isBlank(version)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Version is required for agentspec version detail");
        }
        AiResourceVersion versionRow = aiResourceVersionPersistService.find(namespaceId, agentSpecName,
                RESOURCE_TYPE_AGENTSPEC, version);
        if (versionRow == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec version not found: " + agentSpecName + "@" + version);
        }
        return loadAgentSpecFromStorage(namespaceId, agentSpecName, version);
    }
    
    @Override
    public void deleteAgentSpec(String namespaceId, String agentSpecName) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            return;
        }
        
        aiResourcePersistService.delete(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
        
        Page<AiResourceVersion> versions = aiResourceVersionPersistService.listAll(namespaceId, agentSpecName, 1, 200);
        aiResourceVersionPersistService.deleteByNameAndType(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
        
        if (versions != null && versions.getPageItems() != null) {
            for (AiResourceVersion v : versions.getPageItems()) {
                if (v == null || StringUtils.isBlank(v.getVersion())) {
                    continue;
                }
                deleteAgentSpecStorageForVersion(namespaceId, agentSpecName, v.getVersion());
            }
        }
    }
    
    @Override
    public Page<AgentSpecAdminListItem> listAgentSpecs(String namespaceId, String agentSpecName, String search,
            int pageNo, int pageSize) throws NacosException {
        String nameLike = null;
        if (StringUtils.isNotBlank(agentSpecName)) {
            if (Constants.AgentSpecs.SEARCH_ACCURATE.equalsIgnoreCase(search)) {
                nameLike = agentSpecName;
            } else {
                nameLike = Constants.ALL_PATTERN + agentSpecName + Constants.ALL_PATTERN;
            }
        }
        
        Page<AiResource> metaPage = aiResourcePersistService.list(namespaceId, RESOURCE_TYPE_AGENTSPEC, nameLike, null,
                pageNo, pageSize);
        List<AgentSpecAdminListItem> items = new ArrayList<>();
        if (metaPage != null && metaPage.getPageItems() != null) {
            for (AiResource meta : metaPage.getPageItems()) {
                if (meta == null) {
                    continue;
                }
                AgentSpecVersionInfo versionInfo = parseVersionInfo(meta.getVersionInfo());
                AgentSpecAdminListItem item = new AgentSpecAdminListItem();
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
        
        Page<AgentSpecAdminListItem> result = new Page<>();
        result.setPageItems(items);
        result.setTotalCount(metaPage == null ? 0 : metaPage.getTotalCount());
        result.setPagesAvailable(metaPage == null ? 0 : metaPage.getPagesAvailable());
        result.setPageNumber(pageNo);
        return result;
    }
    
    @Override
    public String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        AgentSpec agentSpec = AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, namespaceId);
        if (agentSpec == null || StringUtils.isBlank(agentSpec.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "AgentSpec name is required");
        }
        String name = agentSpec.getName();
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            String version = "v1";
            createDraftWithAgentSpec(namespaceId, agentSpec, version, null, true);
            return name;
        }
        
        AgentSpecVersionInfo info = requireVersionInfo(meta);
        if (StringUtils.isNotBlank(info.getEditingVersion()) || StringUtils.isNotBlank(info.getReviewingVersion())) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    "There is already a working version (editing/reviewing), cannot upload");
        }
        
        String newVersion = nextVersion(namespaceId, name);
        createDraftWithAgentSpec(namespaceId, agentSpec, newVersion, meta, false);
        return name;
    }
    
    @Override
    public Page<AgentSpecBasicInfo> searchAgentSpecs(String namespaceId, String keyword, int pageNo, int pageSize)
            throws NacosException {
        String nameLike = StringUtils.isBlank(keyword) ? null
                : (Constants.ALL_PATTERN + keyword + Constants.ALL_PATTERN);
        Page<AiResource> metaPage = aiResourcePersistService.list(namespaceId, RESOURCE_TYPE_AGENTSPEC, nameLike, null,
                pageNo, pageSize);
        List<AgentSpecBasicInfo> items = new ArrayList<>();
        if (metaPage != null && metaPage.getPageItems() != null) {
            for (AiResource meta : metaPage.getPageItems()) {
                if (meta == null) {
                    continue;
                }
                if (!META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus())) {
                    continue;
                }
                AgentSpecVersionInfo info = parseVersionInfo(meta.getVersionInfo());
                if (info == null || info.getOnlineCnt() == null || info.getOnlineCnt() <= 0) {
                    continue;
                }
                AgentSpecBasicInfo basicInfo = new AgentSpecBasicInfo();
                basicInfo.setName(meta.getName());
                basicInfo.setDescription(meta.getDesc());
                items.add(basicInfo);
            }
        }
        Page<AgentSpecBasicInfo> result = new Page<>();
        result.setPageItems(items);
        result.setTotalCount(metaPage == null ? 0 : metaPage.getTotalCount());
        result.setPagesAvailable(metaPage == null ? 0 : metaPage.getPagesAvailable());
        result.setPageNumber(pageNo);
        return result;
    }
    
    @Override
    public AgentSpec queryAgentSpec(String namespaceId, String name, String version, String label)
            throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec not found: " + name);
        }
        if (!META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec disabled: " + name);
        }
        String resolved = resolveVersion(meta, version, label);
        if (StringUtils.isBlank(resolved)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec version not found: " + name);
        }
        AiResourceVersion versionRow = aiResourceVersionPersistService.find(namespaceId, name,
                RESOURCE_TYPE_AGENTSPEC, resolved);
        if (versionRow == null || !VERSION_STATUS_ONLINE.equalsIgnoreCase(versionRow.getStatus())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec version not online: " + name);
        }
        return loadAgentSpecFromStorage(namespaceId, name, resolved);
    }

    @Override
    public String createDraft(String namespaceId, String name, String basedOnVersion) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            if (StringUtils.isNotBlank(basedOnVersion)) {
                throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        "AgentSpec not found: " + name + ", cannot use basedOnVersion for a brand-new agentspec");
            }
            AgentSpec emptyAgentSpec = new AgentSpec();
            emptyAgentSpec.setName(name);
            emptyAgentSpec.setNamespaceId(namespaceId);
            createDraftWithAgentSpec(namespaceId, emptyAgentSpec, "v1", null, true);
            return "v1";
        }

        AgentSpecVersionInfo info = requireVersionInfo(meta);
        if (StringUtils.isNotBlank(info.getEditingVersion()) || StringUtils.isNotBlank(info.getReviewingVersion())) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    "There is already a working version (editing/reviewing), cannot create draft");
        }

        String newVersion = nextVersion(namespaceId, name);
        String base = resolveBaseVersion(namespaceId, name, meta, basedOnVersion);
        if (StringUtils.isBlank(base)) {
            AgentSpec emptyAgentSpec = new AgentSpec();
            emptyAgentSpec.setName(name);
            emptyAgentSpec.setNamespaceId(namespaceId);
            createDraftWithAgentSpec(namespaceId, emptyAgentSpec, newVersion, meta, false);
            return newVersion;
        }

        // 1) copy storage content
        AgentSpec baseAgentSpec = loadAgentSpecFromStorage(namespaceId, name, base);
        long uniformId = System.currentTimeMillis();
        writeAgentSpecToStorage(namespaceId, baseAgentSpec, newVersion, uniformId);

        // 2) insert draft version row
        AiResourceVersion v = new AiResourceVersion();
        v.setNamespaceId(namespaceId);
        v.setName(name);
        v.setType(RESOURCE_TYPE_AGENTSPEC);
        v.setAuthor(DEFAULT_AUTHOR);
        v.setStatus(VERSION_STATUS_DRAFT);
        v.setVersion(newVersion);
        v.setDesc(baseAgentSpec.getDescription());
        v.setStorage(buildStorageJson(namespaceId, name, newVersion));
        aiResourceVersionPersistService.insert(v);

        // 3) update meta pointers
        info.setEditingVersion(newVersion);
        updateMetaVersionInfoCas(namespaceId, meta, info);
        return newVersion;
    }
    
    @Override
    public void updateDraft(String namespaceId, AgentSpec draftAgentSpec) throws NacosException {
        if (draftAgentSpec == null || StringUtils.isBlank(draftAgentSpec.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "AgentSpec name is required");
        }
        String name = draftAgentSpec.getName();
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            createDraftWithAgentSpec(namespaceId, draftAgentSpec, "v1", null, true);
            return;
        }
        AgentSpecVersionInfo info = requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        if (StringUtils.isBlank(editing)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "No editing draft exists for agentspec: " + name);
        }
        AiResourceVersion v = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_AGENTSPEC,
                editing);
        if (v == null || !VERSION_STATUS_DRAFT.equalsIgnoreCase(v.getStatus())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Current editing version is not draft: " + editing);
        }
        
        long uniformId = System.currentTimeMillis();
        writeAgentSpecToStorage(namespaceId, draftAgentSpec, editing, uniformId);
        aiResourceVersionPersistService.updateStorageAndDesc(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, editing,
            buildStorageJson(namespaceId, name, editing), draftAgentSpec.getDescription());
        bumpMetaDescription(namespaceId, meta, draftAgentSpec.getDescription());
    }
    
    @Override
    public void deleteDraft(String namespaceId, String name) throws NacosException {
        AiResource meta = requireMeta(namespaceId, name);
        AgentSpecVersionInfo info = requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        if (StringUtils.isBlank(editing)) {
            return;
        }
        AiResourceVersion v = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_AGENTSPEC,
                editing);
        if (v != null && VERSION_STATUS_DRAFT.equalsIgnoreCase(v.getStatus())) {
            deleteAgentSpecStorageForVersion(namespaceId, name, editing);
            aiResourceVersionPersistService.delete(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, editing);
        }
        info.setEditingVersion(null);
        updateMetaVersionInfoCas(namespaceId, meta, info);
    }
    
    @Override
    public String submit(String namespaceId, String name, String version) throws NacosException {
        AiResource meta = requireMeta(namespaceId, name);
        AgentSpecVersionInfo info = requireVersionInfo(meta);
        
        String target = version;
        if (StringUtils.isBlank(target)) {
            target = info.getEditingVersion();
        }
        if (StringUtils.isBlank(target)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "No draft version to submit for agentspec: " + name);
        }
        
        AiResourceVersion v = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_AGENTSPEC,
                target);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec version not found: " + name + "@" + target);
        }
        
        final String finalTarget = target;
        
        // Build context for pipeline execution using the AgentSpec file layout.
        AgentSpecPipelineContext ctx = new AgentSpecPipelineContext();
        ctx.setNamespaceId(namespaceId);
        ctx.setResourceName(name);
        ctx.setVersion(finalTarget);
        ctx.setFilesLoader(() -> {
            try {
                return buildPipelineFiles(loadAgentSpecFromStorage(namespaceId, name, finalTarget));
            } catch (NacosException e) {
                throw new IllegalStateException("Failed to load AgentSpec files for pipeline execution", e);
            }
        });
        
        String executionId = publishPipelineExecutor.execute(ctx,
                result -> onPipelineComplete(namespaceId, name, finalTarget, result));
        if (StringUtils.isBlank(executionId)) {
            // Pipeline disabled or no matched nodes -> publish directly.
            directPublishWithoutPipeline(namespaceId, meta, info, name, finalTarget, true);
            return finalTarget;
        }
        
        // Move to reviewing and record pipeline execution id
        aiResourceVersionPersistService.updateStatus(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, finalTarget,
                VERSION_STATUS_REVIEWING);
        info.setEditingVersion(null);
        info.setReviewingVersion(finalTarget);
        updateMetaVersionInfoCas(namespaceId, meta, info);
        
        AgentSpecPublishPipelineInfo pipelineInfo = new AgentSpecPublishPipelineInfo();
        pipelineInfo.setExecutionId(executionId);
        pipelineInfo.setStatus(PipelineExecutionStatus.IN_PROGRESS);
        pipelineInfo.setPipeline(new ArrayList<>());
        aiResourceVersionPersistService.updatePublishPipelineInfo(namespaceId, name, RESOURCE_TYPE_AGENTSPEC,
                finalTarget, JacksonUtils.toJson(pipelineInfo));
        
        return finalTarget;
    }

    private void directPublishWithoutPipeline(String namespaceId, AiResource meta, AgentSpecVersionInfo info,
            String name, String version, boolean updateLatestLabel) throws NacosException {
        aiResourceVersionPersistService.updateStatus(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, version,
                VERSION_STATUS_ONLINE);
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
    }
    
    @Override
    public void publish(String namespaceId, String name, String version, boolean updateLatestLabel)
            throws NacosException {
        AiResource meta = requireMeta(namespaceId, name);
        AgentSpecVersionInfo info = requireVersionInfo(meta);
        
        AiResourceVersion v = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_AGENTSPEC,
                version);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec version not found: " + name + "@" + version);
        }
        if (!VERSION_STATUS_REVIEWING.equalsIgnoreCase(v.getStatus())
                && !VERSION_STATUS_ONLINE.equalsIgnoreCase(v.getStatus())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Only reviewing version can be published: " + version);
        }
        
        // Validate pipeline execution result if pipeline exists
        AgentSpecPublishPipelineInfo pipelineInfo = parsePublishPipelineInfo(v.getPublishPipelineInfo());
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
            aiResourceVersionPersistService.updateStatus(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, version,
                    VERSION_STATUS_ONLINE);
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
        AgentSpecVersionInfo info = requireVersionInfo(meta);
        info.setLabels(labels == null ? null : new LinkedHashMap<>(labels));
        updateMetaVersionInfoCas(namespaceId, meta, info);
    }
    
    @Override
    public void changeOnlineStatus(String namespaceId, String name, String scope, String version, boolean online)
            throws NacosException {
        AiResource meta = requireMeta(namespaceId, name);
        AgentSpecVersionInfo info = requireVersionInfo(meta);
        
        boolean agentSpecScope = SCOPE_AGENTSPEC.equalsIgnoreCase(scope) || StringUtils.isBlank(version);
        if (agentSpecScope) {
            metaEnableDisable(namespaceId, meta, online);
            return;
        }
        
        AiResourceVersion v = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_AGENTSPEC,
                version);
        if (v == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec version not found: " + name + "@" + version);
        }
        String targetStatus = online ? VERSION_STATUS_ONLINE : VERSION_STATUS_OFFLINE;
        aiResourceVersionPersistService.updateStatus(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, version,
                targetStatus);
        Integer cnt = info.getOnlineCnt() == null ? 0 : info.getOnlineCnt();
        if (online) {
            info.setOnlineCnt(cnt + 1);
        } else {
            info.setOnlineCnt(Math.max(0, cnt - 1));
        }
        updateMetaVersionInfoCas(namespaceId, meta, info);
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
    
    private static String resolveStorageProvider() {
        String provider = EnvUtil.getProperty(Constants.AgentSpecs.AGENTSPEC_STORAGE_PROVIDER_CONFIG_KEY,
                STORAGE_PROVIDER_NACOS_CONFIG);
        return StringUtils.isBlank(provider) ? STORAGE_PROVIDER_NACOS_CONFIG : provider.trim();
    }

    /**
     * Resolves the base version to copy from when creating a draft.
     * Priority: explicit basedOnVersion > latest label > highest numeric version.
     * Returns null if no version exists yet.
     */
    private String resolveBaseVersion(String namespaceId, String name, AiResource meta, String basedOnVersion)
            throws NacosException {
        if (StringUtils.isNotBlank(basedOnVersion)) {
            String resolved = resolveVersion(meta, basedOnVersion, null);
            if (StringUtils.isBlank(resolved)) {
                throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        "Base version not found for agentspec: " + name + ", basedOnVersion: " + basedOnVersion);
            }
            return resolved;
        }
        String latest = resolveVersion(meta, null, LABEL_LATEST);
        return StringUtils.isNotBlank(latest) ? latest : maxVersionByNumber(namespaceId, name);
    }
    
    private String resolveVersion(AiResource meta, String explicitVersion, String label) {
        if (StringUtils.isNotBlank(label)) {
            AgentSpecVersionInfo info = parseVersionInfo(meta.getVersionInfo());
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
        AgentSpecVersionInfo info = parseVersionInfo(meta.getVersionInfo());
        if (info != null && info.getLabels() != null) {
            String v = info.getLabels().get(LABEL_LATEST);
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return null;
    }
    
    private static AgentSpecVersionInfo parseVersionInfo(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return JacksonUtils.toObj(json, AgentSpecVersionInfo.class);
        } catch (Exception ignored) {
            return null;
        }
    }
    
    private static String buildStorageJson(String namespaceId, String agentSpecName, String version) {
        Map<String, Object> json = new HashMap<>(4);
        json.put("provider", resolveStorageProvider());
        json.put("scope", namespaceId + ":" + agentSpecName + ":" + version);
        return JacksonUtils.toJson(json);
    }
    
    private AiResource requireMeta(String namespaceId, String name) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec not found: " + name);
        }
        return meta;
    }
    
    private static AgentSpecVersionInfo requireVersionInfo(AiResource meta) {
        AgentSpecVersionInfo info = parseVersionInfo(meta == null ? null : meta.getVersionInfo());
        if (info == null) {
            info = new AgentSpecVersionInfo();
            info.setLabels(new HashMap<>(4));
        } else if (info.getLabels() == null) {
            info.setLabels(new HashMap<>(4));
        }
        return info;
    }
    
    private void updateMetaVersionInfoCas(String namespaceId, AiResource meta, AgentSpecVersionInfo info)
            throws NacosException {
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
            newValue.setBizTags(latest.getBizTags());
            newValue.setExt(latest.getExt());
        }
        throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "Meta update conflict, retry");
    }
    
    private void metaEnableDisable(String namespaceId, AiResource meta, boolean enable) throws NacosException {
        AgentSpecVersionInfo info = requireVersionInfo(meta);
        AiResource newValue = new AiResource();
        newValue.setStatus(enable ? META_STATUS_ENABLE : META_STATUS_DISABLE);
        newValue.setDesc(meta.getDesc());
        newValue.setBizTags(meta.getBizTags());
        newValue.setExt(meta.getExt());
        newValue.setVersionInfo(JacksonUtils.toJson(info));
        long expected = meta.getMetaVersion() == null ? 0 : meta.getMetaVersion();
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
            newValue.setDesc(latest.getDesc());
            newValue.setBizTags(latest.getBizTags());
            newValue.setExt(latest.getExt());
        }
        throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                "Meta update conflict, retry");
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

    private String maxVersionByNumber(String namespaceId, String name) {
        Page<AiResourceVersion> page = aiResourceVersionPersistService.listAll(namespaceId, name, 1, 200);
        int max = 0;
        String resolved = null;
        if (page != null && page.getPageItems() != null) {
            for (AiResourceVersion v : page.getPageItems()) {
                if (v == null || StringUtils.isBlank(v.getVersion())) {
                    continue;
                }
                String current = v.getVersion().trim();
                if (!current.startsWith("v")) {
                    continue;
                }
                try {
                    int numeric = Integer.parseInt(current.substring(1));
                    if (numeric > max) {
                        max = numeric;
                        resolved = current;
                    }
                } catch (Exception ignored) {
                    // ignore non-numeric version
                }
            }
        }
        return resolved;
    }
    
    private void writeAgentSpecToStorage(String namespaceId, AgentSpec agentSpec, String version, long uniformId)
            throws NacosException {
        byte[] mainContent = buildMainContent(agentSpec, uniformId);
        StorageKey mainKey = NacosConfigAiResourceStorage.buildStorageKey(resolveStorageProvider(), namespaceId,
                NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, agentSpec.getName(), version,
                NacosConfigAiResourceStorage.getMainFilePath(AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID));
        storageRouter.route(mainKey).save(mainKey, mainContent);
        if (agentSpec.getResource() != null && !agentSpec.getResource().isEmpty()) {
            for (Map.Entry<String, AgentSpecResource> entry : agentSpec.getResource().entrySet()) {
                AgentSpecResource resource = entry.getValue();
                String path = NacosConfigAiResourceStorage.getAgentSpecResourceFilePath(resource.getType(),
                        resource.getName());
                byte[] content = buildResourceContent(resource, uniformId);
                StorageKey resourceKey = NacosConfigAiResourceStorage.buildStorageKey(resolveStorageProvider(),
                        namespaceId, NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, agentSpec.getName(),
                        version, path);
                storageRouter.route(resourceKey).save(resourceKey, content);
            }
        }
    }
    
    private void onPipelineComplete(String namespaceId, String name, String version,
            PipelineExecutionResult result) {
        try {
            AgentSpecPublishPipelineInfo info = new AgentSpecPublishPipelineInfo();
            info.setExecutionId(result == null ? null : result.getExecutionId());
            info.setStatus(result == null ? PipelineExecutionStatus.REJECTED : result.getStatus());
            info.setPipeline(result == null ? null : result.getPipeline());
            aiResourceVersionPersistService.updatePublishPipelineInfo(namespaceId, name, RESOURCE_TYPE_AGENTSPEC,
                    version, JacksonUtils.toJson(info));
            
            if (result == null || result.getStatus() != PipelineExecutionStatus.APPROVED) {
                aiResourceVersionPersistService.updateStatus(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, version,
                        VERSION_STATUS_DRAFT);
                AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
                if (meta != null) {
                    AgentSpecVersionInfo vInfo = requireVersionInfo(meta);
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
    
    private AgentSpec loadAgentSpecFromStorage(String namespaceId, String agentSpecName, String version)
            throws NacosException {
        StorageKey mainKey = NacosConfigAiResourceStorage.buildStorageKey(resolveStorageProvider(), namespaceId,
                NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, agentSpecName, version,
                NacosConfigAiResourceStorage.getMainFilePath(AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID));
        byte[] mainBytes = storageRouter.route(mainKey).get(mainKey);
        if (mainBytes == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec not found: " + agentSpecName);
        }
        
        AgentSpecMainConfig mainConfig = JacksonUtils.toObj(new String(mainBytes, StandardCharsets.UTF_8),
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
                String resourceId = AgentSpecUtils.generateResourceId(resourceRef.getType(), resourceRef.getName());
                String path = NacosConfigAiResourceStorage.getAgentSpecResourceFilePath(resourceRef.getType(),
                        resourceRef.getName());
                StorageKey resourceKey = NacosConfigAiResourceStorage.buildStorageKey(resolveStorageProvider(),
                        namespaceId, NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, agentSpecName, version,
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
    
    private void deleteAgentSpecStorageForVersion(String namespaceId, String agentSpecName, String version)
            throws NacosException {
        StorageKey mainKey = NacosConfigAiResourceStorage.buildStorageKey(resolveStorageProvider(), namespaceId,
                NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, agentSpecName, version,
                NacosConfigAiResourceStorage.getMainFilePath(AgentSpecUtils.AGENTSPEC_MAIN_DATA_ID));
        byte[] mainBytes = storageRouter.route(mainKey).get(mainKey);
        if (mainBytes != null) {
            AgentSpecMainConfig mainConfig = JacksonUtils.toObj(new String(mainBytes, StandardCharsets.UTF_8),
                    AgentSpecMainConfig.class);
            if (mainConfig.getResources() != null && !mainConfig.getResources().isEmpty()) {
                for (AgentSpecResourceRef resourceRef : mainConfig.getResources()) {
                    String path = NacosConfigAiResourceStorage.getAgentSpecResourceFilePath(resourceRef.getType(),
                            resourceRef.getName());
                    StorageKey resourceKey = NacosConfigAiResourceStorage.buildStorageKey(resolveStorageProvider(),
                            namespaceId, NacosConfigAiResourceStorage.RESOURCE_TYPE_AGENTSPEC, agentSpecName, version,
                            path);
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
            boolean ok = aiResourcePersistService.updateMetaCas(namespaceId, meta.getName(), meta.getType(), expected,
                    newValue);
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
    
    private static AgentSpecPublishPipelineInfo parsePublishPipelineInfo(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            AgentSpecPublishPipelineInfo info = JacksonUtils.toObj(json, AgentSpecPublishPipelineInfo.class);
            if (info == null || StringUtils.isBlank(info.getExecutionId())) {
                return null;
            }
            return info;
        } catch (Exception ignored) {
            return null;
        }
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
    
    private static class AgentSpecVersionInfo {
        
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
    
    private static class AgentSpecPublishPipelineInfo {
        
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
