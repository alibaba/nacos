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

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.repository.QueryCondition;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.ai.utils.AgentSpecSeedArchiveReader;
import com.alibaba.nacos.ai.utils.AgentSpecZipParser;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecBasicInfo;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecMeta;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecSummary;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.plugin.ai.pipeline.model.AgentSpecPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.ResourceFileContent;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.visibility.constant.VisibilityConstants;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
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
    
    private static final String DEFAULT_AUTHOR = "nacos";
    
    private static final String SCOPE_AGENTSPEC = "agentspec";
    
    private final AiResourceStorageRouter storageRouter;
    
    private final AiResourcePersistService aiResourcePersistService;
    
    private final AiResourceVersionPersistService aiResourceVersionPersistService;
    
    private final PublishPipelineExecutor publishPipelineExecutor;
    
    private final AiResourceManager resourceManager;
    
    public AgentSpecOperationServiceImpl(AiResourcePersistService aiResourcePersistService,
            AiResourceVersionPersistService aiResourceVersionPersistService,
            PublishPipelineExecutor publishPipelineExecutor,
            AiResourceManager resourceManager) {
        this.storageRouter = AiResourceStorageRouter.getInstance();
        this.aiResourcePersistService = aiResourcePersistService;
        this.aiResourceVersionPersistService = aiResourceVersionPersistService;
        this.publishPipelineExecutor = publishPipelineExecutor;
        this.resourceManager = resourceManager;
    }
    
    private void createDraftWithAgentSpec(String namespaceId, AgentSpec agentSpec, String version,
            AiResource existedMeta, boolean isNew) throws NacosException {
        String agentSpecName = agentSpec.getName();
        long uniformId = System.currentTimeMillis();
        String currentUser = VisibilityHelper.resolveCurrentIdentity();
        
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
        resourceManager.insertVersionRow(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC,
                StringUtils.isBlank(currentUser) ? DEFAULT_AUTHOR : currentUser,
                AiResourceConstants.VERSION_STATUS_DRAFT, version, agentSpec.getDescription(),
                buildStorageJson(namespaceId, agentSpecName, version));
        
        // 3) create or update meta for editingVersion
        resourceManager.initOrUpdateMetaForDraft(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC,
                agentSpec.getDescription(), agentSpec.getBizTags(), version, existedMeta, isNew);
    }
    
    @Override
    public AgentSpecMeta getAgentSpecDetail(String namespaceId, String agentSpecName, String version)
            throws NacosException {
        return getAgentSpecDetail(namespaceId, agentSpecName);
    }

    @Override
    public AgentSpecMeta getAgentSpecDetail(String namespaceId, String agentSpecName) throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec not found: " + agentSpecName);
        }
        resourceManager.ensureReadableOrNotFound(meta, "AgentSpec not found: " + agentSpecName);
        ResourceVersionInfo versionInfo = AiResourceManager.requireVersionInfo(meta);
        Page<AiResourceVersion> versionPage = aiResourceVersionPersistService.list(namespaceId, agentSpecName,
                RESOURCE_TYPE_AGENTSPEC, null, 1, 200);
        List<AgentSpecMeta.AgentSpecVersionSummary> versionSummaries = new ArrayList<>();
        if (versionPage != null && versionPage.getPageItems() != null) {
            for (AiResourceVersion v : versionPage.getPageItems()) {
                if (v == null) {
                    continue;
                }
                AgentSpecMeta.AgentSpecVersionSummary summary = new AgentSpecMeta.AgentSpecVersionSummary();
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
        
        AgentSpecMeta detail = new AgentSpecMeta();
        detail.setNamespaceId(meta.getNamespaceId());
        detail.setName(meta.getName());
        detail.setDescription(meta.getDesc());
        detail.setBizTags(meta.getBizTags());
        detail.setFrom(meta.getFrom());
        detail.setEnable(AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus()));
        detail.setScope(AiResourceManager.resolveScope(meta));
        detail.setEditingVersion(versionInfo.getEditingVersion());
        detail.setReviewingVersion(versionInfo.getReviewingVersion());
        detail.setLabels(versionInfo.getLabels());
        detail.setOnlineCnt(versionInfo.getOnlineCnt());
        detail.setUpdateTime(meta.getGmtModified() == null ? null : meta.getGmtModified().getTime());
        detail.setDownloadCount(meta.getDownloadCount());
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
        resourceManager.ensureReadableOrNotFound(meta, "AgentSpec not found: " + agentSpecName);
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
        VisibilityHelper.checkWritableResource(meta);
        
        resourceManager.deleteResourceWithVersions(namespaceId, agentSpecName, RESOURCE_TYPE_AGENTSPEC,
                v -> deleteAgentSpecStorageForVersion(namespaceId, agentSpecName, v.getVersion()));
    }
    
    @Override
    public Page<AgentSpecSummary> listAgentSpecs(String namespaceId, String agentSpecName, String search,
            int pageNo, int pageSize) throws NacosException {
        return listAgentSpecs(namespaceId, agentSpecName, search, null, null, null, pageNo, pageSize);
    }

    @Override
    public Page<AgentSpecSummary> listAgentSpecs(String namespaceId, String agentSpecName, String search,
            String orderBy, String owner, String scope, int pageNo, int pageSize) throws NacosException {
        String nameLike = null;
        if (StringUtils.isNotBlank(agentSpecName)) {
            if (Constants.AgentSpecs.SEARCH_ACCURATE.equalsIgnoreCase(search)) {
                nameLike = agentSpecName;
            } else {
                nameLike = aiResourcePersistService.generateLikeArgument(
                        Constants.ALL_PATTERN + agentSpecName + Constants.ALL_PATTERN);
            }
        }
        
        QueryCondition queryCondition = resourceManager.buildQueryCondition(namespaceId, RESOURCE_TYPE_AGENTSPEC, nameLike, null,
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
        List<AgentSpecSummary> items = new ArrayList<>();
        if (metaPage != null && metaPage.getPageItems() != null) {
            for (AiResource meta : metaPage.getPageItems()) {
                if (meta == null) {
                    continue;
                }
                ResourceVersionInfo versionInfo = AiResourceManager.parseVersionInfo(meta.getVersionInfo());
                AgentSpecSummary item = new AgentSpecSummary();
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
        }
        
        return AiResourceManager.buildPageResult(items, metaPage, pageNo);
    }
    
    @Override
    public String uploadAgentSpecFromZip(String namespaceId, byte[] zipBytes, boolean overwrite)
            throws NacosException {
        List<AgentSpecSeedArchiveReader.AgentSpecPackage> packages = readUploadPackages(zipBytes);
        if (!packages.isEmpty()) {
            if (packages.size() == 1) {
                return uploadSingleAgentSpecFromZip(namespaceId, packages.get(0).getZipBytes(), overwrite);
            }
            List<String> importedNames = new ArrayList<>(packages.size());
            for (AgentSpecSeedArchiveReader.AgentSpecPackage each : packages) {
                importedNames.add(uploadSingleAgentSpecFromZip(namespaceId, each.getZipBytes(), overwrite));
            }
            return String.format("Imported %d agentspecs: %s", importedNames.size(),
                    String.join(", ", importedNames));
        }
        return uploadSingleAgentSpecFromZip(namespaceId, zipBytes, overwrite);
    }

    private List<AgentSpecSeedArchiveReader.AgentSpecPackage> readUploadPackages(byte[] zipBytes) throws NacosException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipBytes)) {
            return AgentSpecSeedArchiveReader.read(inputStream);
        } catch (IOException e) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR, e,
                    "Failed to read agentspec zip archive");
        }
    }

    private String uploadSingleAgentSpecFromZip(String namespaceId, byte[] zipBytes, boolean overwrite)
            throws NacosException {
        AgentSpec agentSpec = AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, namespaceId);
        if (agentSpec == null || StringUtils.isBlank(agentSpec.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "AgentSpec name is required");
        }
        String name = agentSpec.getName();
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        if (overwrite) {
            return overwriteUploadedAgentSpec(namespaceId, agentSpec, meta);
        }
        if (meta == null) {
            String version = "v1";
            createDraftWithAgentSpec(namespaceId, agentSpec, version, null, true);
            return name;
        }
        
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        AiResourceManager.ensureNoWorkingVersion(info, "upload");
        
        String newVersion = nextVersion(namespaceId, name);
        createDraftWithAgentSpec(namespaceId, agentSpec, newVersion, meta, false);
        resourceManager.syncImportedMeta(namespaceId, meta, agentSpec.getDescription(), agentSpec.getBizTags());
        return name;
    }
    
    @Override
    public void bootstrapAgentSpecFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        bootstrapAgentSpecFromZip(namespaceId, zipBytes, null);
    }

    @Override
    public void bootstrapAgentSpecFromZip(String namespaceId, byte[] zipBytes, String from) throws NacosException {
        AgentSpec agentSpec = AgentSpecZipParser.parseAgentSpecFromZip(zipBytes, namespaceId);
        if (agentSpec == null || StringUtils.isBlank(agentSpec.getName())) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "AgentSpec name is required");
        }
        String name = agentSpec.getName();
        AiResource existingMeta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        if (existingMeta != null) {
            if (repairBuiltInAgentSpecIfBroken(namespaceId, existingMeta, agentSpec)) {
                LOGGER.info("Repaired built-in agentspec bootstrap content for {}", name);
                return;
            }
            LOGGER.info("Skip built-in agentspec bootstrap because agentspec already exists: {}", name);
            return;
        }
        
        String version = "v1";
        long uniformId = System.currentTimeMillis();
        writeAgentSpecToStorage(namespaceId, agentSpec, version, uniformId);
        
        resourceManager.insertBootstrapMeta(namespaceId, name, RESOURCE_TYPE_AGENTSPEC,
                agentSpec.getDescription(), agentSpec.getBizTags(), DEFAULT_AUTHOR, from, version,
                buildStorageJson(namespaceId, name, version));
    }

    private boolean repairBuiltInAgentSpecIfBroken(String namespaceId, AiResource meta, AgentSpec bundledAgentSpec)
            throws NacosException {
        if (meta == null || bundledAgentSpec == null || StringUtils.isBlank(bundledAgentSpec.getName())) {
            return false;
        }
        if (!StringUtils.equals(DEFAULT_AUTHOR, meta.getOwner())) {
            return false;
        }
        ResourceVersionInfo versionInfo = AiResourceManager.requireVersionInfo(meta);
        if (StringUtils.isNotBlank(versionInfo.getEditingVersion()) || StringUtils.isNotBlank(versionInfo.getReviewingVersion())) {
            return false;
        }
        String latestVersion = versionInfo.getLabels() == null ? null : versionInfo.getLabels().get(AiResourceConstants.LABEL_LATEST);
        if (StringUtils.isBlank(latestVersion)) {
            return false;
        }
        AiResourceVersion versionRow = aiResourceVersionPersistService.find(namespaceId, bundledAgentSpec.getName(),
                RESOURCE_TYPE_AGENTSPEC, latestVersion);
        if (versionRow == null || !AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(versionRow.getStatus())
                || !StringUtils.equals(DEFAULT_AUTHOR, versionRow.getAuthor())) {
            return false;
        }

        AgentSpec currentAgentSpec;
        try {
            currentAgentSpec = loadAgentSpecFromStorage(namespaceId, bundledAgentSpec.getName(), latestVersion);
        } catch (NacosException e) {
            currentAgentSpec = null;
        }
        if (!isBuiltInContentMissing(currentAgentSpec, bundledAgentSpec)) {
            return false;
        }

        long uniformId = System.currentTimeMillis();
        writeAgentSpecToStorage(namespaceId, bundledAgentSpec, latestVersion, uniformId);
        aiResourceVersionPersistService.updateStorageAndDesc(namespaceId, bundledAgentSpec.getName(),
                RESOURCE_TYPE_AGENTSPEC, latestVersion, buildStorageJson(namespaceId, bundledAgentSpec.getName(),
                        latestVersion), bundledAgentSpec.getDescription());
        resourceManager.syncImportedMeta(namespaceId, meta, bundledAgentSpec.getDescription(), bundledAgentSpec.getBizTags());
        return true;
    }

    private String overwriteUploadedAgentSpec(String namespaceId, AgentSpec agentSpec, AiResource meta)
            throws NacosException {
        String name = agentSpec.getName();
        if (meta == null) {
            createDraftWithAgentSpec(namespaceId, agentSpec, "v1", null, true);
            return name;
        }

        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        if (StringUtils.isNotBlank(editing)) {
            overwriteEditingDraft(namespaceId, agentSpec, meta, editing);
            return name;
        }

        String newVersion = nextVersion(namespaceId, name);
        createDraftWithAgentSpec(namespaceId, agentSpec, newVersion, meta, false);
        resourceManager.syncImportedMeta(namespaceId, meta, agentSpec.getDescription(), agentSpec.getBizTags());
        return name;
    }

    private void overwriteEditingDraft(String namespaceId, AgentSpec agentSpec, AiResource meta, String editing)
            throws NacosException {
        resourceManager.requireDraftVersion(namespaceId, agentSpec.getName(), RESOURCE_TYPE_AGENTSPEC, editing);
        long uniformId = System.currentTimeMillis();
        writeAgentSpecToStorage(namespaceId, agentSpec, editing, uniformId);
        aiResourceVersionPersistService.updateStorageAndDesc(namespaceId, agentSpec.getName(),
                RESOURCE_TYPE_AGENTSPEC, editing, buildStorageJson(namespaceId, agentSpec.getName(), editing),
                agentSpec.getDescription());
        resourceManager.syncImportedMeta(namespaceId, meta, agentSpec.getDescription(), agentSpec.getBizTags());
    }
    
    
    @Override
    public Page<AgentSpecBasicInfo> searchAgentSpecs(String namespaceId, String keyword, int pageNo, int pageSize)
            throws NacosException {
        String nameLike = StringUtils.isBlank(keyword) ? null
                : aiResourcePersistService.generateLikeArgument(Constants.ALL_PATTERN + keyword + Constants.ALL_PATTERN);
        QueryCondition queryCondition = resourceManager.buildQueryCondition(namespaceId, RESOURCE_TYPE_AGENTSPEC, nameLike, null,
                VisibilityConstants.ACTION_READ);
        if (queryCondition.isAlwaysEmpty()) {
            return AiResourceManager.buildEmptyPage(pageNo);
        }
        Page<AiResource> metaPage = aiResourcePersistService.list(queryCondition, pageNo, pageSize);
        List<AgentSpecBasicInfo> items = new ArrayList<>();
        if (metaPage != null && metaPage.getPageItems() != null) {
            for (AiResource meta : metaPage.getPageItems()) {
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
                AgentSpecBasicInfo basicInfo = new AgentSpecBasicInfo();
                basicInfo.setName(meta.getName());
                basicInfo.setDescription(meta.getDesc());
                items.add(basicInfo);
            }
        }
        return AiResourceManager.buildPageResult(items, metaPage, pageNo);
    }
    
    @Override
    public AgentSpec queryAgentSpec(String namespaceId, String name, String version, String label)
            throws NacosException {
        AiResource meta = aiResourcePersistService.find(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec not found: " + name);
        }
        resourceManager.ensureReadableOrNotFound(meta, "AgentSpec not found: " + name);
        if (!AiResourceConstants.META_STATUS_ENABLE.equalsIgnoreCase(meta.getStatus())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec disabled: " + name);
        }
        String resolved = AiResourceManager.resolveVersion(meta, version, label);
        if (StringUtils.isBlank(resolved)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentSpec version not found: " + name);
        }
        AiResourceVersion versionRow = aiResourceVersionPersistService.find(namespaceId, name,
                RESOURCE_TYPE_AGENTSPEC, resolved);
        if (versionRow == null || !AiResourceConstants.VERSION_STATUS_ONLINE.equalsIgnoreCase(versionRow.getStatus())) {
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

        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        AiResourceManager.ensureNoWorkingVersion(info, "create draft");

        String newVersion = nextVersion(namespaceId, name);
        String base = resourceManager.resolveBaseVersion(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, meta, basedOnVersion);
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
        resourceManager.insertVersionRow(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, DEFAULT_AUTHOR,
                AiResourceConstants.VERSION_STATUS_DRAFT, newVersion, baseAgentSpec.getDescription(),
                buildStorageJson(namespaceId, name, newVersion));

        // 3) update meta pointers
        info.setEditingVersion(newVersion);
        resourceManager.updateVersionInfoCas(namespaceId, meta, info);
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
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        if (StringUtils.isBlank(editing)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "No editing draft exists for agentspec: " + name);
        }
        resourceManager.requireDraftVersion(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, editing);
        
        long uniformId = System.currentTimeMillis();
        writeAgentSpecToStorage(namespaceId, draftAgentSpec, editing, uniformId);
        aiResourceVersionPersistService.updateStorageAndDesc(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, editing,
            buildStorageJson(namespaceId, name, editing), draftAgentSpec.getDescription());
        resourceManager.bumpMetaDescription(namespaceId, meta, draftAgentSpec.getDescription());
    }
    
    @Override
    public void deleteDraft(String namespaceId, String name) throws NacosException {
        AiResource meta = resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        String editing = info.getEditingVersion();
        if (StringUtils.isBlank(editing)) {
            return;
        }
        AiResourceVersion v = aiResourceVersionPersistService.find(namespaceId, name, RESOURCE_TYPE_AGENTSPEC,
                editing);
        if (v != null && AiResourceConstants.VERSION_STATUS_DRAFT.equalsIgnoreCase(v.getStatus())) {
            deleteAgentSpecStorageForVersion(namespaceId, name, editing);
            aiResourceVersionPersistService.delete(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, editing);
        }
        info.setEditingVersion(null);
        resourceManager.updateVersionInfoCas(namespaceId, meta, info);
    }
    
    @Override
    public String submit(String namespaceId, String name, String version) throws NacosException {
        AiResource meta = resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        
        String target = resourceManager.resolveSubmitTarget(info, version, RESOURCE_TYPE_AGENTSPEC, name);
        
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
        
        // Check pipeline availability before starting async execution.
        if (!publishPipelineExecutor.isPipelineAvailable(ctx.getResourceType())) {
            resourceManager.directPublishVersion(namespaceId, meta, info, finalTarget, true);
            return finalTarget;
        }
        
        resourceManager.moveToReviewing(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, finalTarget, meta, info);
        if (!resourceManager.runPipelineExecution(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, finalTarget,
                ctx, publishPipelineExecutor)) {
            resourceManager.directPublishVersion(namespaceId, meta, info, finalTarget, true);
        }
        
        return finalTarget;
    }

    @Override
    public void publish(String namespaceId, String name, String version, boolean updateLatestLabel)
            throws NacosException {
        resourceManager.doPublish(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, version, updateLatestLabel);
    }
    
    @Override
    public void forcePublish(String namespaceId, String name, String version, boolean updateLatestLabel)
            throws NacosException {
        resourceManager.doForcePublish(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, version, updateLatestLabel);
    }
    
    @Override
    public void updateLabels(String namespaceId, String name, Map<String, String> labels) throws NacosException {
        resourceManager.validateAndUpdateLabels(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, labels);
    }

    @Override
    public void updateBizTags(String namespaceId, String name, String bizTags) throws NacosException {
        AiResource meta = resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        VisibilityHelper.checkWritableResource(meta);
        resourceManager.updateBizTagsCas(namespaceId, meta, bizTags);
    }
    
    @Override
    public void changeOnlineStatus(String namespaceId, String name, String scope, String version, boolean online)
            throws NacosException {
        AiResource meta = resourceManager.requireMeta(namespaceId, name, RESOURCE_TYPE_AGENTSPEC);
        VisibilityHelper.checkWritableResource(meta);
        ResourceVersionInfo info = AiResourceManager.requireVersionInfo(meta);
        
        boolean agentSpecScope = SCOPE_AGENTSPEC.equalsIgnoreCase(scope) || StringUtils.isBlank(version);
        if (agentSpecScope) {
            resourceManager.metaEnableDisable(namespaceId, meta, online);
            return;
        }
        
        resourceManager.toggleVersionOnlineStatus(namespaceId, meta, info, version, online);
    }

    @Override
    public void updateScope(String namespaceId, String name, String scope) throws NacosException {
        resourceManager.doUpdateScope(namespaceId, name, RESOURCE_TYPE_AGENTSPEC, scope);
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
    
    private static String buildStorageJson(String namespaceId, String agentSpecName, String version) {
        Map<String, Object> json = new HashMap<>(4);
        json.put("provider", resolveStorageProvider());
        json.put("scope", namespaceId + ":" + agentSpecName + ":" + version);
        return JacksonUtils.toJson(json);
    }

    private String nextVersion(String namespaceId, String name) {
        return VersionUtils.nextVNumberVersion(
                resourceManager.listExistingVersions(namespaceId, name, RESOURCE_TYPE_AGENTSPEC));
    }

    private static boolean isBuiltInContentMissing(AgentSpec currentAgentSpec, AgentSpec bundledAgentSpec) {
        if (bundledAgentSpec == null) {
            return false;
        }
        if (currentAgentSpec == null) {
            return true;
        }
        if (StringUtils.isNotBlank(bundledAgentSpec.getContent()) && StringUtils.isBlank(currentAgentSpec.getContent())) {
            return true;
        }
        Map<String, AgentSpecResource> bundledResources = bundledAgentSpec.getResource();
        if (bundledResources == null || bundledResources.isEmpty()) {
            return false;
        }
        Map<String, AgentSpecResource> currentResources = currentAgentSpec.getResource();
        if (currentResources == null || currentResources.isEmpty()) {
            return true;
        }
        String bundledAgentsContent = extractAgentsContent(bundledResources);
        if (StringUtils.isBlank(bundledAgentsContent)) {
            return false;
        }
        String currentAgentsContent = extractAgentsContent(currentResources);
        return StringUtils.isBlank(currentAgentsContent);
    }

    private static String extractAgentsContent(Map<String, AgentSpecResource> resources) {
        if (resources == null || resources.isEmpty()) {
            return null;
        }
        for (AgentSpecResource resource : resources.values()) {
            if (resource == null || StringUtils.isBlank(resource.getName())) {
                continue;
            }
            String normalizedName = resource.getName().trim();
            int lastSlash = normalizedName.lastIndexOf('/');
            if (lastSlash >= 0) {
                normalizedName = normalizedName.substring(lastSlash + 1);
            }
            if ("AGENTS.md".equalsIgnoreCase(normalizedName)) {
                return resource.getContent();
            }
        }
        return null;
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
    
}
