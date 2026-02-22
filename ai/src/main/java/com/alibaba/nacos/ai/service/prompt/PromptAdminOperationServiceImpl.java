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

import com.alibaba.nacos.ai.utils.PromptDataIdUtils;
import com.alibaba.nacos.ai.utils.PromptVersionUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptMetaSummary;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.alibaba.nacos.ai.constant.Constants.ALL_PATTERN;
import static com.alibaba.nacos.ai.constant.Constants.Prompt.PROMPT_CONFIG_TYPE;
import static com.alibaba.nacos.ai.constant.Constants.Prompt.PROMPT_GROUP;
import static com.alibaba.nacos.ai.constant.Constants.Prompt.SEARCH_BLUR;

/**
 * Prompt admin operation service implementation.
 *
 * @author nacos
 */
@Service
public class PromptAdminOperationServiceImpl implements PromptAdminOperationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PromptAdminOperationServiceImpl.class);
    
    private final ConfigOperationService configOperationService;
    
    private final ConfigDetailService configDetailService;
    
    private final ConfigInfoPersistService configInfoPersistService;
    
    public PromptAdminOperationServiceImpl(ConfigOperationService configOperationService,
            ConfigDetailService configDetailService, ConfigInfoPersistService configInfoPersistService) {
        this.configOperationService = configOperationService;
        this.configDetailService = configDetailService;
        this.configInfoPersistService = configInfoPersistService;
    }
    
    @Override
    public boolean publishPromptVersion(String namespaceId, String promptKey, String version, String template, String commitMsg,
            String description, List<String> bizTags, String srcUser, String srcIp) throws NacosException {
        validatePromptKeyAndVersion(promptKey, version);
        if (!PromptVersionUtils.isValidVersion(version)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Version must be in format major.minor.patch");
        }
        if (StringUtils.isBlank(template)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `template` not present");
        }
        PromptMetaSnapshot snapshot = loadMetaSnapshot(namespaceId, promptKey);
        boolean newPrompt = snapshot.getMeta() == null;
        if (!newPrompt && (description != null || bizTags != null)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "description and bizTags can only be set on first publish, use updatePromptMetadata afterwards");
        }
        PromptMetaInfo meta = snapshot.getMeta() == null ? PromptMetaUtils.initEmptyMeta(promptKey) : snapshot.getMeta();
        if (meta.getVersions().contains(version)) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    String.format("prompt version `%s` already exists", version));
        }
        String versionDataId = PromptDataIdUtils.buildVersionDataId(promptKey, version);
        ConfigInfoWrapper versionConfig = configInfoPersistService.findConfigInfo(versionDataId, PROMPT_GROUP, namespaceId);
        if (versionConfig != null && StringUtils.isNotBlank(versionConfig.getContent())) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    String.format("prompt version `%s` already exists", version));
        }
        
        long now = System.currentTimeMillis();
        PromptVersionInfo versionInfo = new PromptVersionInfo();
        versionInfo.setPromptKey(promptKey);
        versionInfo.setVersion(version);
        versionInfo.setTemplate(template);
        versionInfo.setCommitMsg(commitMsg);
        versionInfo.setGmtModified(now);
        publishConfig(namespaceId, versionDataId, JacksonUtils.toJson(versionInfo), srcUser, srcIp, null, false, null);
        
        meta.getVersions().add(version);
        meta.getVersions().sort(buildVersionComparator());
        meta.setLatestVersion(meta.getVersions().get(meta.getVersions().size() - 1));
        if (newPrompt && StringUtils.isNotBlank(description)) {
            meta.setDescription(description);
        }
        if (newPrompt && bizTags != null) {
            meta.setBizTags(new ArrayList<>(bizTags));
        }
        meta.setGmtModified(now);
        
        publishMeta(namespaceId, promptKey, meta, snapshot.getMd5(), srcUser, srcIp);
        refreshLatestMirror(namespaceId, promptKey, meta.getLatestVersion(), srcUser, srcIp);
        return true;
    }
    
    @Override
    public boolean bindLabel(String namespaceId, String promptKey, String label, String version, String srcUser, String srcIp)
            throws NacosException {
        validatePromptKeyAndVersion(promptKey, version);
        if (StringUtils.isBlank(label)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `label` not present");
        }
        PromptMetaSnapshot snapshot = requireMetaSnapshot(namespaceId, promptKey);
        PromptMetaInfo meta = snapshot.getMeta();
        if (!meta.getVersions().contains(version)) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("prompt version `%s` not found", version));
        }
        meta.getLabels().put(label, version);
        meta.setGmtModified(System.currentTimeMillis());
        publishMeta(namespaceId, promptKey, meta, snapshot.getMd5(), srcUser, srcIp);
        return true;
    }
    
    @Override
    public boolean unbindLabel(String namespaceId, String promptKey, String label, String srcUser, String srcIp)
            throws NacosException {
        if (StringUtils.isBlank(promptKey) || StringUtils.isBlank(label)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `promptKey` and `label` not present");
        }
        PromptMetaSnapshot snapshot = requireMetaSnapshot(namespaceId, promptKey);
        PromptMetaInfo meta = snapshot.getMeta();
        meta.getLabels().remove(label);
        meta.setGmtModified(System.currentTimeMillis());
        publishMeta(namespaceId, promptKey, meta, snapshot.getMd5(), srcUser, srcIp);
        return true;
    }
    
    @Override
    public boolean deletePrompt(String namespaceId, String promptKey, String srcUser, String srcIp) throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `promptKey` not present");
        }
        PromptMetaSnapshot snapshot = loadMetaSnapshot(namespaceId, promptKey);
        configOperationService.deleteConfig(PromptDataIdUtils.buildMetaDataId(promptKey), PROMPT_GROUP, namespaceId, null, srcIp,
                srcUser, null);
        configOperationService.deleteConfig(PromptDataIdUtils.buildLatestDataId(promptKey), PROMPT_GROUP, namespaceId, null, srcIp,
                srcUser, null);
        if (snapshot.getMeta() != null) {
            for (String version : new ArrayList<>(snapshot.getMeta().getVersions())) {
                String versionDataId = PromptDataIdUtils.buildVersionDataId(promptKey, version);
                configOperationService.deleteConfig(versionDataId, PROMPT_GROUP, namespaceId, null, srcIp, srcUser, null);
            }
        }
        return true;
    }
    
    @Override
    public boolean updatePromptMetadata(String namespaceId, String promptKey, String description, List<String> bizTags, String srcUser,
            String srcIp) throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `promptKey` not present");
        }
        PromptMetaSnapshot snapshot = requireMetaSnapshot(namespaceId, promptKey);
        PromptMetaInfo meta = snapshot.getMeta();
        if (description != null) {
            meta.setDescription(description);
        }
        if (bizTags != null) {
            meta.setBizTags(new ArrayList<>(bizTags));
        }
        meta.setGmtModified(System.currentTimeMillis());
        publishMeta(namespaceId, promptKey, meta, snapshot.getMd5(), srcUser, srcIp);
        return true;
    }
    
    @Override
    public Page<PromptMetaSummary> listPrompts(String namespaceId, String promptKey, String search, String bizTags, int pageNo,
            int pageSize) throws NacosException {
        String metaPattern;
        if (StringUtils.isEmpty(promptKey) || SEARCH_BLUR.equalsIgnoreCase(search)) {
            String keyPattern = StringUtils.isNotBlank(promptKey) ? promptKey : StringUtils.EMPTY;
            metaPattern = ALL_PATTERN + keyPattern + ALL_PATTERN + ".meta.json";
            search = SEARCH_BLUR;
        } else {
            metaPattern = PromptDataIdUtils.buildMetaDataId(promptKey);
        }
        Map<String, Object> configAdvanceInfo = null;
        if (StringUtils.isNotBlank(bizTags)) {
            configAdvanceInfo = new HashMap<>(2);
            configAdvanceInfo.put("config_tags", bizTags);
        }
        Page<ConfigInfo> configPage = configDetailService.findConfigInfoPage(search, pageNo, pageSize, metaPattern, PROMPT_GROUP,
                namespaceId, configAdvanceInfo);
        List<PromptMetaSummary> items = configPage.getPageItems().stream().map(each -> {
            try {
                PromptMetaInfo meta = JacksonUtils.toObj(each.getContent(), PromptMetaInfo.class);
                PromptMetaSummary result = new PromptMetaSummary();
                result.setSchemaVersion(meta.getSchemaVersion());
                result.setPromptKey(
                        StringUtils.isBlank(meta.getPromptKey())
                                ? PromptDataIdUtils.extractPromptKeyFromMetaDataId(each.getDataId())
                                : meta.getPromptKey());
                result.setDescription(meta.getDescription());
                result.setLatestVersion(meta.getLatestVersion());
                result.setBizTags(meta.getBizTags() == null ? new ArrayList<>(4) : new ArrayList<>(meta.getBizTags()));
                result.setGmtModified(meta.getGmtModified());
                return result;
            } catch (Exception ex) {
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
        Page<PromptMetaSummary> result = new Page<>();
        result.setPageNumber(pageNo);
        result.setPagesAvailable(configPage.getPagesAvailable());
        result.setTotalCount(configPage.getTotalCount());
        result.setPageItems(items);
        return result;
    }
    
    @Override
    public Page<PromptVersionSummary> listPromptVersions(String namespaceId, String promptKey, int pageNo, int pageSize)
            throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `promptKey` not present");
        }
        PromptMetaInfo meta = getPromptMeta(namespaceId, promptKey);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("prompt `%s` not found", promptKey));
        }
        List<String> versions = new ArrayList<>(meta.getVersions());
        versions.sort(buildVersionComparator().reversed());
        int totalCount = versions.size();
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int startIndex = (safePageNo - 1) * safePageSize;
        int endIndex = Math.min(startIndex + safePageSize, totalCount);
        List<String> pagedVersions = startIndex < totalCount ? versions.subList(startIndex, endIndex) : Collections.emptyList();
        List<PromptVersionSummary> items = pagedVersions.stream().map(each -> {
            PromptVersionSummary result = new PromptVersionSummary();
            result.setPromptKey(promptKey);
            result.setVersion(each);
            try {
                PromptVersionInfo detail = queryPromptDetail(namespaceId, promptKey, each, null);
                result.setCommitMsg(detail.getCommitMsg());
                result.setSrcUser(detail.getSrcUser());
                result.setGmtModified(detail.getGmtModified());
            } catch (NacosException ex) {
                LOGGER.warn("Query prompt version detail failed for prompt={}, version={}", promptKey, each, ex);
            }
            return result;
        }).collect(Collectors.toList());
        Page<PromptVersionSummary> result = new Page<>();
        result.setPageItems(items);
        result.setTotalCount(totalCount);
        result.setPagesAvailable((int) Math.ceil((double) totalCount / (double) safePageSize));
        result.setPageNumber(safePageNo);
        return result;
    }
    
    @Override
    public PromptMetaInfo getPromptMeta(String namespaceId, String promptKey) throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            return null;
        }
        ConfigInfoWrapper metaConfig = configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildMetaDataId(promptKey), PROMPT_GROUP,
                namespaceId);
        if (metaConfig == null || StringUtils.isBlank(metaConfig.getContent())) {
            return null;
        }
        PromptMetaInfo meta = JacksonUtils.toObj(metaConfig.getContent(), PromptMetaInfo.class);
        return PromptMetaUtils.normalizeMeta(meta);
    }
    
    @Override
    public PromptVersionInfo queryPromptDetail(String namespaceId, String promptKey, String version, String label)
            throws NacosException {
        if (StringUtils.isBlank(promptKey)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `promptKey` not present");
        }
        PromptMetaInfo meta = getPromptMeta(namespaceId, promptKey);
        if (meta == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("prompt `%s` not found", promptKey));
        }
        String targetVersion = PromptMetaUtils.resolveTargetVersion(meta, version, label);
        String versionDataId = PromptDataIdUtils.buildVersionDataId(promptKey, targetVersion);
        ConfigAllInfo versionConfig = configInfoPersistService.findConfigAllInfo(versionDataId, PROMPT_GROUP, namespaceId);
        if (versionConfig == null || StringUtils.isBlank(versionConfig.getContent())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("prompt `%s` version `%s` not found", promptKey, targetVersion));
        }
        PromptVersionInfo result = JacksonUtils.toObj(versionConfig.getContent(), PromptVersionInfo.class);
        result.setPromptKey(promptKey);
        result.setVersion(targetVersion);
        result.setMd5(versionConfig.getMd5());
        result.setSrcUser(versionConfig.getCreateUser());
        return result;
    }
    
    private PromptMetaSnapshot loadMetaSnapshot(String namespaceId, String promptKey) {
        ConfigInfoWrapper metaConfig = configInfoPersistService.findConfigInfo(PromptDataIdUtils.buildMetaDataId(promptKey), PROMPT_GROUP,
                namespaceId);
        if (metaConfig == null || StringUtils.isBlank(metaConfig.getContent())) {
            return PromptMetaSnapshot.empty();
        }
        PromptMetaInfo meta = PromptMetaUtils.normalizeMeta(JacksonUtils.toObj(metaConfig.getContent(), PromptMetaInfo.class));
        return new PromptMetaSnapshot(meta, metaConfig.getMd5());
    }
    
    private PromptMetaSnapshot requireMetaSnapshot(String namespaceId, String promptKey) throws NacosException {
        PromptMetaSnapshot snapshot = loadMetaSnapshot(namespaceId, promptKey);
        if (snapshot.getMeta() == null) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("prompt `%s` not found", promptKey));
        }
        return snapshot;
    }
    
    private void refreshLatestMirror(String namespaceId, String promptKey, String latestVersion, String srcUser, String srcIp)
            throws NacosException {
        String latestDataId = PromptDataIdUtils.buildLatestDataId(promptKey);
        String versionDataId = PromptDataIdUtils.buildVersionDataId(promptKey, latestVersion);
        ConfigInfoWrapper versionConfig = configInfoPersistService.findConfigInfo(versionDataId, PROMPT_GROUP, namespaceId);
        if (versionConfig == null || StringUtils.isBlank(versionConfig.getContent())) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    String.format("latest version content `%s` not found", latestVersion));
        }
        publishConfig(namespaceId, latestDataId, versionConfig.getContent(), srcUser, srcIp, null, true, null);
    }
    
    private void publishMeta(String namespaceId, String promptKey, PromptMetaInfo meta, String casMd5, String srcUser,
            String srcIp) throws NacosException {
        String metaDataId = PromptDataIdUtils.buildMetaDataId(promptKey);
        publishConfig(namespaceId, metaDataId, JacksonUtils.toJson(meta), srcUser, srcIp, casMd5, true,
                joinBizTags(meta.getBizTags()));
    }
    
    private void publishConfig(String namespaceId, String dataId, String content, String srcUser, String srcIp, String casMd5,
            boolean updateForExist, String configTags) throws NacosException {
        ConfigForm form = new ConfigForm();
        form.setDataId(dataId);
        form.setGroup(PROMPT_GROUP);
        form.setNamespaceId(namespaceId);
        form.setType(PROMPT_CONFIG_TYPE);
        form.setContent(content);
        form.setSrcUser(srcUser);
        if (StringUtils.isNotBlank(configTags)) {
            form.setConfigTags(configTags);
        }
        ConfigRequestInfo requestInfo = new ConfigRequestInfo();
        requestInfo.setSrcIp(srcIp);
        requestInfo.setUpdateForExist(updateForExist);
        if (StringUtils.isNotBlank(casMd5)) {
            requestInfo.setCasMd5(casMd5);
        }
        configOperationService.publishConfig(form, requestInfo, null);
    }
    
    private void validatePromptKeyAndVersion(String promptKey, String version) throws NacosApiException {
        if (StringUtils.isBlank(promptKey) || StringUtils.isBlank(version)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter `promptKey` and `version` not present");
        }
    }
    
    private Comparator<String> buildVersionComparator() {
        return (left, right) -> {
            if (Objects.equals(left, right)) {
                return 0;
            }
            if (!PromptVersionUtils.isValidVersion(left) || !PromptVersionUtils.isValidVersion(right)) {
                return left.compareTo(right);
            }
            return PromptVersionUtils.compareVersion(left, right);
        };
    }
    
    private String joinBizTags(List<String> bizTags) {
        if (bizTags == null || bizTags.isEmpty()) {
            return null;
        }
        return bizTags.stream().filter(StringUtils::isNotBlank).map(String::trim).distinct()
                .collect(Collectors.joining(","));
    }
    
}
