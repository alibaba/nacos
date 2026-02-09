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

package com.alibaba.nacos.ai.service.prompt;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.api.ai.model.prompt.PromptBasicInfo;
import com.alibaba.nacos.api.ai.model.prompt.PromptDetail;
import com.alibaba.nacos.api.ai.model.prompt.PromptHistoryItem;
import com.alibaba.nacos.ai.utils.PromptVersionUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigChangePublisher;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.HistoryService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.service.trace.ConfigTraceService;
import com.alibaba.nacos.config.server.utils.TimeUtils;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.alibaba.nacos.ai.constant.Constants.Prompt.EXT_PROMPT_COMMIT_MSG;
import static com.alibaba.nacos.ai.constant.Constants.Prompt.EXT_PROMPT_VERSION;
import static com.alibaba.nacos.ai.constant.Constants.Prompt.FIELD_COMMIT_MSG;
import static com.alibaba.nacos.ai.constant.Constants.Prompt.FIELD_PROMPT_KEY;
import static com.alibaba.nacos.ai.constant.Constants.Prompt.FIELD_TEMPLATE;
import static com.alibaba.nacos.ai.constant.Constants.Prompt.FIELD_VERSION;
import static com.alibaba.nacos.ai.constant.Constants.Prompt.PROMPT_CONFIG_TYPE;
import static com.alibaba.nacos.ai.constant.Constants.Prompt.PROMPT_GROUP;

/**
 * Prompt operation service implementation.
 *
 * @author nacos
 */
@Service
public class PromptOperationServiceImpl implements PromptOperationService {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(PromptOperationServiceImpl.class);
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final ConfigDetailService configDetailService;
    
    private final HistoryService historyService;
    
    private final ConfigInfoPersistService configInfoPersistService;
    
    public PromptOperationServiceImpl(ConfigQueryChainService configQueryChainService,
            ConfigOperationService configOperationService, ConfigDetailService configDetailService,
            HistoryService historyService, ConfigInfoPersistService configInfoPersistService) {
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
        this.configDetailService = configDetailService;
        this.historyService = historyService;
        this.configInfoPersistService = configInfoPersistService;
    }
    
    @Override
    public boolean publishPrompt(String namespaceId, String promptKey, String version, String template,
            String commitMsg, String description, String promptTags, String srcUser, String srcIp) throws NacosException {
        
        // 1. Validate version format
        if (!PromptVersionUtils.isValidVersion(version)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "Version must be in format 'major.minor.patch' (e.g., '1.0.0')");
        }
        
        // 2. Get current config to check version
        String dataId = PromptVersionUtils.buildDataId(promptKey);
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(dataId, PROMPT_GROUP, namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        
        String currentVersion = null;
        String currentMd5 = null;
        
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL
                || response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_GRAY) {
            // Parse current version from content
            try {
                JsonNode contentNode = JacksonUtils.toObj(response.getContent(), JsonNode.class);
                if (contentNode.has(FIELD_VERSION)) {
                    currentVersion = contentNode.get(FIELD_VERSION).asText();
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse current prompt content for version check: {}", promptKey, e);
            }
            currentMd5 = response.getMd5();
        }
        
        // 3. Validate version is greater than current
        if (!PromptVersionUtils.isVersionGreater(version, currentVersion)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    String.format("New version '%s' must be greater than current version '%s'", version, currentVersion));
        }
        
        // 4. Build content JSON
        Map<String, Object> contentMap = new HashMap<>(4);
        contentMap.put(FIELD_PROMPT_KEY, promptKey);
        contentMap.put(FIELD_VERSION, version);
        if (StringUtils.isNotBlank(template)) {
            contentMap.put(FIELD_TEMPLATE, template);
        }
        if (StringUtils.isNotBlank(commitMsg)) {
            contentMap.put(FIELD_COMMIT_MSG, commitMsg);
        }
        String content = JacksonUtils.toJson(contentMap);
        
        // 5. Build ConfigForm
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(dataId);
        configForm.setGroup(PROMPT_GROUP);
        configForm.setNamespaceId(namespaceId);
        configForm.setContent(content);
        configForm.setType(PROMPT_CONFIG_TYPE);
        configForm.setSrcUser(srcUser);
        if (StringUtils.isNotBlank(description)) {
            configForm.setDesc(description);
        }
        if (StringUtils.isNotBlank(promptTags)) {
            configForm.setConfigTags(promptTags);
        }
        
        // 6. Build ConfigRequestInfo with CAS
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setSrcIp(srcIp);
        if (currentMd5 != null) {
            configRequestInfo.setCasMd5(currentMd5);
        }
        
        // 7. Publish config
        configOperationService.publishConfig(configForm, configRequestInfo, null);
        
        return true;
    }
    
    @Override
    public PromptDetail getPromptDetail(String namespaceId, String promptKey) throws NacosException {
        String dataId = PromptVersionUtils.buildDataId(promptKey);
        
        // Direct query using configInfoPersistService.findConfigAllInfo to get all fields including desc
        ConfigAllInfo configAllInfo = configInfoPersistService.findConfigAllInfo(dataId, PROMPT_GROUP, namespaceId);
        if (configAllInfo == null) {
            return null;
        }
        
        PromptDetail detail = buildPromptDetail(namespaceId, promptKey, configAllInfo.getContent(), 
                configAllInfo.getMd5(), null, null);
        
        // Set description
        if (StringUtils.isNotBlank(configAllInfo.getDesc())) {
            detail.setDescription(configAllInfo.getDesc());
        }
        
        // Set promptTags
        if (StringUtils.isNotBlank(configAllInfo.getConfigTags())) {
            detail.setPromptTags(configAllInfo.getConfigTags());
        }
        
        // Set update time from modifyTime
        if (configAllInfo.getModifyTime() > 0) {
            detail.setUpdateTime(configAllInfo.getModifyTime());
        }
        
        return detail;
    }
    
    @Override
    public boolean deletePrompt(String namespaceId, String promptKey, String srcUser, String srcIp)
            throws NacosException {
        String dataId = PromptVersionUtils.buildDataId(promptKey);
        return configOperationService.deleteConfig(dataId, PROMPT_GROUP, namespaceId, null, srcIp, srcUser, null);
    }
    
    @Override
    public Page<PromptBasicInfo> listPrompts(String namespaceId, String promptKey, String search, int pageNo,
            int pageSize) throws NacosException {
        
        String dataIdPattern;
        if (StringUtils.isEmpty(promptKey) || Constants.Prompt.SEARCH_BLUR.equalsIgnoreCase(search)) {
            search = Constants.Prompt.SEARCH_BLUR;
            // Use pattern like "*promptKey*.json" for blur search
            String keyPattern = StringUtils.isNotBlank(promptKey) ? promptKey : "";
            dataIdPattern = Constants.ALL_PATTERN + keyPattern + Constants.ALL_PATTERN + Constants.Prompt.PROMPT_DATA_ID_SUFFIX;
        } else {
            search = Constants.Prompt.SEARCH_ACCURATE;
            dataIdPattern = PromptVersionUtils.buildDataId(promptKey);
        }
        
        Page<ConfigInfo> configInfoPage = configDetailService.findConfigInfoPage(search, pageNo, pageSize, 
                dataIdPattern, PROMPT_GROUP, namespaceId, null);
        
        List<PromptBasicInfo> promptList = configInfoPage.getPageItems().stream()
                .map(configInfo -> {
                    try {
                        PromptBasicInfo basicInfo = new PromptBasicInfo();
                        basicInfo.setNamespaceId(namespaceId);
                        basicInfo.setPromptKey(PromptVersionUtils.extractPromptKey(configInfo.getDataId()));
                        
                        // Parse content for version
                        if (StringUtils.isNotBlank(configInfo.getContent())) {
                            JsonNode contentNode = JacksonUtils.toObj(configInfo.getContent(), JsonNode.class);
                            if (contentNode.has(FIELD_VERSION)) {
                                basicInfo.setVersion(contentNode.get(FIELD_VERSION).asText());
                            }
                        }
                        
                        // Set description from config metadata (c_desc)
                        if (StringUtils.isNotBlank(configInfo.getDesc())) {
                            basicInfo.setDescription(configInfo.getDesc());
                        }
                        
                        // Set promptTags from config tags
                        if (StringUtils.isNotBlank(configInfo.getConfigTags())) {
                            basicInfo.setPromptTags(configInfo.getConfigTags());
                        }
                        
                        // Set update time from gmtModified
                        if (configInfo.getGmtModified() != null) {
                            basicInfo.setUpdateTime(configInfo.getGmtModified());
                        }
                        
                        return basicInfo;
                    } catch (Exception e) {
                        LOGGER.warn("Failed to parse prompt config: {}", configInfo.getDataId(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        Page<PromptBasicInfo> result = new Page<>();
        result.setPageItems(promptList);
        result.setTotalCount(configInfoPage.getTotalCount());
        result.setPagesAvailable((int) Math.ceil((double) configInfoPage.getTotalCount() / (double) pageSize));
        result.setPageNumber(pageNo);
        
        return result;
    }
    
    /**
     * Maximum number of history versions to return per Prompt.
     * This limit helps control performance since we need to fetch content for each record.
     */
    private static final int MAX_HISTORY_VERSIONS = 10;
    
    @Override
    public Page<PromptHistoryItem> listPromptHistory(String namespaceId, String promptKey, int pageNo, int pageSize)
            throws NacosException {
        String dataId = PromptVersionUtils.buildDataId(promptKey);
        
        // Fetch more records to account for filtering and deduplication
        // Fetch extra records because same version may have multiple history records
        int fetchSize = MAX_HISTORY_VERSIONS * 2;
        Page<ConfigHistoryInfo> historyPage = historyService.listConfigHistory(dataId, PROMPT_GROUP, namespaceId, 
                1, fetchSize);
        
        List<ConfigHistoryInfo> allRecords = historyPage.getPageItems();
        
        // Filter records according to Prompt history version semantics:
        // 1. Exclude opType = "I" (Insert) - first creation is not a history version
        // 2. Exclude records at and before the most recent Delete operation
        List<ConfigHistoryInfo> filteredRecords = filterPromptHistoryRecords(allRecords);
        
        // Build history items from all filtered records
        List<PromptHistoryItem> historyItems = filteredRecords.stream()
                .map(historyInfo -> buildPromptHistoryItem(namespaceId, promptKey, dataId, historyInfo))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        // Deduplicate by version - keep only the first occurrence of each version (most recent)
        LinkedHashMap<String, PromptHistoryItem> versionMap = new LinkedHashMap<>();
        for (PromptHistoryItem item : historyItems) {
            String version = item.getVersion();
            if (StringUtils.isNotBlank(version) && !versionMap.containsKey(version)) {
                versionMap.put(version, item);
            }
        }
        List<PromptHistoryItem> deduplicatedItems = new ArrayList<>(versionMap.values());
        
        // Limit to MAX_HISTORY_VERSIONS after deduplication
        if (deduplicatedItems.size() > MAX_HISTORY_VERSIONS) {
            deduplicatedItems = deduplicatedItems.subList(0, MAX_HISTORY_VERSIONS);
        }
        
        // Apply pagination on deduplicated results
        int totalCount = deduplicatedItems.size();
        int startIndex = (pageNo - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, totalCount);
        
        List<PromptHistoryItem> pagedItems = startIndex < totalCount 
                ? deduplicatedItems.subList(startIndex, endIndex) 
                : Collections.emptyList();
        
        Page<PromptHistoryItem> result = new Page<>();
        result.setPageItems(pagedItems);
        result.setTotalCount(totalCount);
        result.setPagesAvailable((int) Math.ceil((double) totalCount / (double) pageSize));
        result.setPageNumber(pageNo);
        
        return result;
    }
    
    /**
     * Filter config history records to get Prompt history versions.
     * 
     * <p>Prompt history version semantics differs from config history:
     * <ul>
     *     <li>Exclude opType="I" (Insert) - first creation is current version, not history</li>
     *     <li>Exclude opType="D" (Delete) and all records before it - after re-creation, old history is irrelevant</li>
     *     <li>Limit to MAX_HISTORY_VERSIONS (20) - older versions are not returned</li>
     * </ul>
     * 
     * <p>Only opType="U" (Update) records after the last Insert are included,
     * representing the replaced versions that became history.
     */
    private List<ConfigHistoryInfo> filterPromptHistoryRecords(List<ConfigHistoryInfo> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<ConfigHistoryInfo> result = new ArrayList<>();
        
        for (ConfigHistoryInfo record : records) {
            String opType = record.getOpType();
            
            // Stop at Delete operation - records before this are from previous lifecycle
            if ("D".equals(opType)) {
                break;
            }
            
            // Skip Insert operation - first creation is not a history version
            if ("I".equals(opType)) {
                continue;
            }
            
            // Include Update operations - these are replaced versions (history versions)
            if ("U".equals(opType)) {
                result.add(record);
                // Stop when reaching max history versions limit
                if (result.size() >= MAX_HISTORY_VERSIONS) {
                    break;
                }
            }
        }
        
        return result;
    }
    
    /**
     * Build PromptHistoryItem from ConfigHistoryInfo.
     * 
     * <p>First tries to parse version from extInfo. If not found (which happens for Update operations
     * because extInfo is copied from old ConfigAllInfo which doesn't have prompt_version),
     * falls back to fetching history detail and parsing from content.
     */
    private PromptHistoryItem buildPromptHistoryItem(String namespaceId, String promptKey, 
            String dataId, ConfigHistoryInfo historyInfo) {
        PromptHistoryItem item = new PromptHistoryItem();
        item.setId(historyInfo.getId());
        item.setPromptKey(promptKey);
        item.setSrcUser(historyInfo.getSrcUser());
        item.setOpType(historyInfo.getOpType());
        if (historyInfo.getLastModifiedTime() != null) {
            item.setPublishTime(historyInfo.getLastModifiedTime().getTime());
        }
        
        // Try to parse version and commitMsg from extInfo first
        if (StringUtils.isNotBlank(historyInfo.getExtInfo())) {
            try {
                JsonNode extInfoNode = JacksonUtils.toObj(historyInfo.getExtInfo(), JsonNode.class);
                if (extInfoNode.has(EXT_PROMPT_VERSION)) {
                    item.setVersion(extInfoNode.get(EXT_PROMPT_VERSION).asText());
                }
                if (extInfoNode.has(EXT_PROMPT_COMMIT_MSG)) {
                    item.setCommitMsg(extInfoNode.get(EXT_PROMPT_COMMIT_MSG).asText());
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse extInfo for history: {}", historyInfo.getId(), e);
            }
        }
        
        // Fallback: if version not found in extInfo, fetch history detail and parse from content
        // This is needed because Update operations copy extInfo from old ConfigAllInfo,
        // which doesn't include prompt_version field
        if (StringUtils.isBlank(item.getVersion())) {
            try {
                ConfigHistoryInfo detailInfo = historyService.getConfigHistoryInfo(
                        dataId, PROMPT_GROUP, namespaceId, historyInfo.getId());
                if (detailInfo != null && StringUtils.isNotBlank(detailInfo.getContent())) {
                    JsonNode contentNode = JacksonUtils.toObj(detailInfo.getContent(), JsonNode.class);
                    if (contentNode.has(FIELD_VERSION)) {
                        item.setVersion(contentNode.get(FIELD_VERSION).asText());
                    }
                    if (StringUtils.isBlank(item.getCommitMsg()) && contentNode.has(FIELD_COMMIT_MSG)) {
                        item.setCommitMsg(contentNode.get(FIELD_COMMIT_MSG).asText());
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to fetch history detail for: {}", historyInfo.getId(), e);
            }
        }
        
        return item;
    }
    
    @Override
    public PromptDetail getPromptHistoryDetail(String namespaceId, String promptKey, Long historyId)
            throws NacosException {
        String dataId = PromptVersionUtils.buildDataId(promptKey);
        try {
            ConfigHistoryInfo historyInfo = historyService.getConfigHistoryInfo(dataId, PROMPT_GROUP, namespaceId, historyId);
            if (historyInfo == null) {
                return null;
            }
            
            Long publishTime = historyInfo.getLastModifiedTime() != null 
                    ? historyInfo.getLastModifiedTime().getTime() : null;
            
            return buildPromptDetail(namespaceId, promptKey, historyInfo.getContent(), historyInfo.getMd5(),
                    historyInfo.getExtInfo(), publishTime);
        } catch (AccessException e) {
            throw new NacosApiException(NacosException.NO_RIGHT, ErrorCode.ACCESS_DENIED, e.getMessage());
        }
    }
    
    @Override
    public boolean updatePromptMetadata(String namespaceId, String promptKey, String description, String promptTags,
            String srcUser, String srcIp) throws NacosException {
        
        String dataId = PromptVersionUtils.buildDataId(promptKey);
        
        // Update metadata only (no history record, no content change)
        configInfoPersistService.updateConfigInfoMetadata(dataId, PROMPT_GROUP, namespaceId, promptTags, description);
        
        // Notify config change
        final Timestamp time = TimeUtils.getCurrentTime();
        ConfigTraceService.logPersistenceEvent(dataId, PROMPT_GROUP, namespaceId, null, time.getTime(), srcIp,
                ConfigTraceService.PERSISTENCE_EVENT_METADATA, ConfigTraceService.PERSISTENCE_TYPE_PUB, null);
        ConfigChangePublisher.notifyConfigChange(new ConfigDataChangeEvent(dataId, PROMPT_GROUP, namespaceId, time.getTime()));
        
        return true;
    }
    
    /**
     * Build PromptDetail from content and metadata.
     */
    private PromptDetail buildPromptDetail(String namespaceId, String promptKey, String content, String md5,
            String extInfo, Long publishTime) {
        PromptDetail detail = new PromptDetail();
        detail.setNamespaceId(namespaceId);
        detail.setPromptKey(promptKey);
        detail.setMd5(md5);
        
        if (publishTime != null) {
            detail.setUpdateTime(publishTime);
        }
        
        // Parse content
        if (StringUtils.isNotBlank(content)) {
            try {
                JsonNode contentNode = JacksonUtils.toObj(content, JsonNode.class);
                if (contentNode.has(FIELD_VERSION)) {
                    detail.setVersion(contentNode.get(FIELD_VERSION).asText());
                }
                if (contentNode.has(FIELD_TEMPLATE)) {
                    detail.setTemplate(contentNode.get(FIELD_TEMPLATE).asText());
                }
                if (contentNode.has(FIELD_COMMIT_MSG)) {
                    detail.setCommitMsg(contentNode.get(FIELD_COMMIT_MSG).asText());
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse prompt content: {}", promptKey, e);
            }
        }
        
        // Parse extInfo for version/commitMsg (override from content if present)
        if (StringUtils.isNotBlank(extInfo)) {
            try {
                JsonNode extInfoNode = JacksonUtils.toObj(extInfo, JsonNode.class);
                if (extInfoNode.has(EXT_PROMPT_VERSION) && StringUtils.isBlank(detail.getVersion())) {
                    detail.setVersion(extInfoNode.get(EXT_PROMPT_VERSION).asText());
                }
                if (extInfoNode.has(EXT_PROMPT_COMMIT_MSG) && StringUtils.isBlank(detail.getCommitMsg())) {
                    detail.setCommitMsg(extInfoNode.get(EXT_PROMPT_COMMIT_MSG).asText());
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse extInfo: {}", promptKey, e);
            }
        }
        
        return detail;
    }
}
