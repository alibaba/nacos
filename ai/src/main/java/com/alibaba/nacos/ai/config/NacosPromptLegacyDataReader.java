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

package com.alibaba.nacos.ai.config;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.utils.PromptDataIdUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.model.ConfigAllInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default {@link PromptLegacyDataReader} for open-source Nacos.
 * Reads legacy prompt data from Nacos Config ({@code nacos-ai-prompt} group).
 *
 * @author nacos
 * @since 3.2.0
 */
@Component
public class NacosPromptLegacyDataReader implements PromptLegacyDataReader {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosPromptLegacyDataReader.class);
    
    public static final String TYPE = "nacos";
    
    private static final int SCAN_PAGE_SIZE = 100;
    
    private static final String PROMPT_GROUP = Constants.Prompt.PROMPT_GROUP;
    
    private static final String DEFAULT_NAMESPACE = com.alibaba.nacos.api.common.Constants.DEFAULT_NAMESPACE_ID;
    
    private final ConfigInfoPersistService configInfoPersistService;
    
    private final ConfigQueryChainService configQueryChainService;
    
    public NacosPromptLegacyDataReader(ConfigInfoPersistService configInfoPersistService,
            ConfigQueryChainService configQueryChainService) {
        this.configInfoPersistService = configInfoPersistService;
        this.configQueryChainService = configQueryChainService;
    }
    
    @Override
    public String type() {
        return TYPE;
    }
    
    @Override
    public List<LegacyPromptData> scanLegacyPrompts() {
        List<String> promptKeys = scanPromptKeys();
        List<LegacyPromptData> result = new ArrayList<>();
        for (String promptKey : promptKeys) {
            LegacyPromptData data = buildLegacyPromptData(promptKey);
            if (data != null) {
                result.add(data);
            }
        }
        return result;
    }
    
    @Override
    public PromptVersionInfo readVersionContent(String promptKey, String version) {
        String versionDataId = PromptDataIdUtils.buildVersionDataId(promptKey, version);
        ConfigAllInfo configAllInfo = configInfoPersistService.findConfigAllInfo(versionDataId, PROMPT_GROUP,
                DEFAULT_NAMESPACE);
        if (configAllInfo == null || StringUtils.isBlank(configAllInfo.getContent())) {
            return null;
        }
        PromptVersionInfo info;
        try {
            info = JacksonUtils.toObj(configAllInfo.getContent(), PromptVersionInfo.class);
        } catch (Exception e) {
            info = new PromptVersionInfo();
            info.setTemplate(configAllInfo.getContent());
        }
        info.setPromptKey(promptKey);
        info.setVersion(version);
        // Use Config table md5 if not already in content JSON
        if (info.getMd5() == null && configAllInfo.getMd5() != null) {
            info.setMd5(configAllInfo.getMd5());
        }
        // srcUser from createUser (Config advance info)
        if (info.getSrcUser() == null && configAllInfo.getCreateUser() != null) {
            info.setSrcUser(configAllInfo.getCreateUser());
        }
        return info;
    }
    
    private List<String> scanPromptKeys() {
        List<String> promptKeys = new ArrayList<>();
        int pageNo = 1;
        while (true) {
            Page<ConfigInfo> page = configInfoPersistService.findConfigInfo4Page(pageNo, SCAN_PAGE_SIZE, null,
                    PROMPT_GROUP, DEFAULT_NAMESPACE, null);
            if (page == null || page.getPageItems() == null || page.getPageItems().isEmpty()) {
                break;
            }
            for (ConfigInfo info : page.getPageItems()) {
                if (PromptDataIdUtils.isDescriptorDataId(info.getDataId())) {
                    String key = PromptDataIdUtils.extractPromptKeyFromDescriptorDataId(info.getDataId());
                    if (StringUtils.isNotBlank(key)) {
                        promptKeys.add(key);
                    }
                }
            }
            if (page.getPageItems().size() < SCAN_PAGE_SIZE) {
                break;
            }
            pageNo++;
        }
        return promptKeys;
    }
    
    private LegacyPromptData buildLegacyPromptData(String promptKey) {
        LegacyDescriptor descriptor = readConfigJson(
                PromptDataIdUtils.buildDescriptorDataId(promptKey), LegacyDescriptor.class);
        LegacyLabelVersionMapping mapping = readConfigJson(
                PromptDataIdUtils.buildLabelVersionMappingDataId(promptKey), LegacyLabelVersionMapping.class);
        
        if (mapping == null || mapping.versions == null || mapping.versions.isEmpty()) {
            LOGGER.warn("Prompt '{}' has no versions in mapping, skip", promptKey);
            return null;
        }
        
        LegacyPromptData data = new LegacyPromptData();
        data.setPromptKey(promptKey);
        data.setDescription(descriptor != null ? descriptor.description : null);
        data.setBizTags(descriptor != null ? descriptor.bizTags : null);
        data.setLabels(mapping.labels != null ? mapping.labels : new HashMap<>());
        data.setLatestVersion(mapping.latestVersion);
        data.setVersions(mapping.versions);
        return data;
    }
    
    private <T> T readConfigJson(String dataId, Class<T> clazz) {
        String content = readConfigContent(dataId);
        if (StringUtils.isBlank(content)) {
            return null;
        }
        try {
            return JacksonUtils.toObj(content, clazz);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse config '{}' as {}: {}", dataId, clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }
    
    private String readConfigContent(String dataId) {
        try {
            ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(dataId,
                    PROMPT_GROUP, DEFAULT_NAMESPACE);
            ConfigQueryChainResponse response = configQueryChainService.handle(request);
            if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
                return null;
            }
            return response.getContent();
        } catch (Exception e) {
            LOGGER.warn("Failed to read config '{}': {}", dataId, e.getMessage());
            return null;
        }
    }
    
    // ========== Legacy Config JSON structures (for deserialization only) ==========
    
    /**
     * Legacy prompt descriptor stored in Config as {promptKey}.descriptor.json.
     */
    static class LegacyDescriptor {
        
        public int schemaVersion = 1;
        
        public String promptKey;
        
        public String description;
        
        public List<String> bizTags = new ArrayList<>();
        
        public Long gmtModified;
    }
    
    /**
     * Legacy prompt label/version mapping stored in Config as {promptKey}.label-version-mapping.json.
     */
    static class LegacyLabelVersionMapping {
        
        public int schemaVersion = 1;
        
        public String promptKey;
        
        public List<String> versions = new ArrayList<>();
        
        public Map<String, String> labels = new HashMap<>();
        
        public String latestVersion;
        
        public Long gmtModified;
    }
}
