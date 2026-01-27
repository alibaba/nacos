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
import com.alibaba.nacos.ai.service.SyncEffectService;
import com.alibaba.nacos.ai.utils.SkillZipParser;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillBasicInfo;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.exception.ConfigAlreadyExistsException;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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
    
    private static final String SKILL_NAME_PATTERN = "^[a-zA-Z_-]+$";
    
    /**
     * Validate that name does not contain double underscores.
     */
    private void validateNoDoubleUnderscore(String name, String fieldName) throws NacosException {
        if (StringUtils.isNotBlank(name) && name.contains("__")) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    String.format("%s cannot contain double underscores (__)", fieldName));
        }
    }
    
    /**
     * Generate resource ID from resource type and name.
     * Format: {type}_{resourcename}
     * If resourcename ends with .xx, convert the last . to __
     */
    private String generateResourceId(String type, String resourceName) {
        if (StringUtils.isBlank(resourceName)) {
            return "";
        }
        
        // If resourcename ends with .xx, convert the last . to __
        String processedName = resourceName;
        if (resourceName.matches(".*\\.[a-zA-Z0-9]+$")) {
            // Replace only the last dot before the extension
            int lastDotIndex = resourceName.lastIndexOf('.');
            if (lastDotIndex > 0) {
                processedName = resourceName.substring(0, lastDotIndex) + "__" + resourceName.substring(lastDotIndex + 1);
            }
        }
        
        if (StringUtils.isNotBlank(type)) {
            return type + "_" + processedName;
        } else {
            return processedName;
        }
    }
    
    /**
     * Parse resource ID to get type and resource name.
     * Format: {type}_{resourcename} or {resourcename}
     * If resourcename contains __, convert __ back to .
     */
    private String[] parseResourceId(String resourceId) {
        if (StringUtils.isBlank(resourceId)) {
            return new String[]{"", ""};
        }
        
        int underscoreIndex = resourceId.indexOf('_');
        if (underscoreIndex > 0) {
            String type = resourceId.substring(0, underscoreIndex);
            String resourceName = resourceId.substring(underscoreIndex + 1);
            // Convert __ back to .
            resourceName = resourceName.replace("__", ".");
            return new String[]{type, resourceName};
        } else {
            // No type, just resource name
            String resourceName = resourceId.replace("__", ".");
            return new String[]{"", resourceName};
        }
    }
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final ConfigInfoPersistService configInfoPersistService;
    
    private final SyncEffectService syncEffectService;
    
    public SkillOperationServiceImpl(ConfigQueryChainService configQueryChainService,
            ConfigOperationService configOperationService,
            ConfigInfoPersistService configInfoPersistService, SyncEffectService syncEffectService) {
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
        this.configInfoPersistService = configInfoPersistService;
        this.syncEffectService = syncEffectService;
    }
    
    @Override
    public String registerSkill(Skill skill, String namespaceId) throws NacosException {
        try {
            // 1. Validate skill name (only allow English letters, underscore, hyphen)
            if (StringUtils.isBlank(skill.getName())) {
                throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                        "Skill name is required");
            }
            if (!skill.getName().matches(SKILL_NAME_PATTERN)) {
                throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                        "Skill name can only contain English letters, underscore, and hyphen");
            }
            validateNoDoubleUnderscore(skill.getName(), "Skill name");
            
            // 2. Validate resource names
            if (skill.getResource() != null && !skill.getResource().isEmpty()) {
                for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                    SkillResource resource = entry.getValue();
                    if (resource.getName() != null) {
                        validateNoDoubleUnderscore(resource.getName(), "Resource name");
                    }
                }
            }
            
            // 3. Build main config (skill.json)
            String skillGroup = "skill_" + skill.getName();
            ConfigForm mainConfigForm = buildMainConfigForm(skill, namespaceId, skillGroup);
            ConfigRequestInfo mainConfigRequest = new ConfigRequestInfo();
            mainConfigRequest.setUpdateForExist(Boolean.FALSE);
            configOperationService.publishConfig(mainConfigForm, mainConfigRequest, null);
            
            // 4. Build and publish resource configs
            if (skill.getResource() != null && !skill.getResource().isEmpty()) {
                for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                    SkillResource resource = entry.getValue();
                    String resourceId = generateResourceId(resource.getType(), resource.getName());
                    
                    ConfigForm resourceConfigForm = buildResourceConfigForm(resource, namespaceId, skillGroup, resourceId);
                    ConfigRequestInfo resourceConfigRequest = new ConfigRequestInfo();
                    resourceConfigRequest.setUpdateForExist(Boolean.FALSE);
                    configOperationService.publishConfig(resourceConfigForm, resourceConfigRequest, null);
                }
            }
            
            long startOperationTime = System.currentTimeMillis();
            syncEffectService.toSync(mainConfigForm, startOperationTime);
            
            return skill.getName();
        } catch (ConfigAlreadyExistsException e) {
            throw new NacosApiException(NacosException.CONFLICT, ErrorCode.RESOURCE_CONFLICT,
                    String.format("Skill name %s already exists", skill.getName()));
        }
    }
    
    @Override
    public Skill getSkillDetail(String namespaceId, String skillName) throws NacosException {
        // 1. Query main config
        String skillGroup = "skill_" + skillName;
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest("skill.json", skillGroup,
                namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + skillName);
        }
        
        // 2. Parse main config
        SkillMainConfig mainConfig = JacksonUtils.toObj(response.getContent(), SkillMainConfig.class);
        
        // 3. Build Skill object
        Skill skill = new Skill();
        skill.setNamespaceId(namespaceId);
        skill.setName(mainConfig.getName());
        skill.setDescription(mainConfig.getDescription());
        skill.setInstruction(mainConfig.getInstruction());
        
        // 4. Query all resource configs
        Map<String, SkillResource> resourceMap = new HashMap<>(
                mainConfig.getResource() != null ? mainConfig.getResource().size() : 16);
        if (mainConfig.getResource() != null && !mainConfig.getResource().isEmpty()) {
            for (Map.Entry<String, SkillResourceRef> entry : mainConfig.getResource().entrySet()) {
                String resourceId = entry.getKey();
                
                // Query resource config
                String resourceDataId = "resource_" + resourceId + ".json";
                ConfigQueryChainRequest resourceRequest = ConfigQueryChainRequest.buildConfigQueryChainRequest(
                        resourceDataId, skillGroup, namespaceId);
                ConfigQueryChainResponse resourceResponse = configQueryChainService.handle(resourceRequest);
                
                if (resourceResponse.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL
                        || resourceResponse.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_GRAY) {
                    SkillResource resource = JacksonUtils.toObj(resourceResponse.getContent(), SkillResource.class);
                    // Use resource name as key (from resource object, not resourceId)
                    resourceMap.put(resource.getName() != null ? resource.getName() : resourceId, resource);
                } else {
                    LOGGER.warn("Resource configuration not found: dataId={}, group={}", resourceDataId, skillGroup);
                }
            }
        }
        skill.setResource(resourceMap);
        
        return skill;
    }
    
    @Override
    public void updateSkill(Skill skill, String namespaceId) throws NacosException {
        // 1. Validate skill name and resource names
        validateNoDoubleUnderscore(skill.getName(), "Skill name");
        if (skill.getResource() != null && !skill.getResource().isEmpty()) {
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                SkillResource resource = entry.getValue();
                if (resource.getName() != null) {
                    validateNoDoubleUnderscore(resource.getName(), "Resource name");
                }
            }
        }
        
        // 2. Check if skill exists
        String skillGroup = "skill_" + skill.getName();
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest("skill.json", skillGroup,
                namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + skill.getName());
        }
        
        // 3. Update main config
        ConfigForm mainConfigForm = buildMainConfigForm(skill, namespaceId, skillGroup);
        ConfigRequestInfo mainConfigRequest = new ConfigRequestInfo();
        mainConfigRequest.setUpdateForExist(Boolean.TRUE);
        configOperationService.publishConfig(mainConfigForm, mainConfigRequest, null);
        
        // 4. Update resource configs
        if (skill.getResource() != null && !skill.getResource().isEmpty()) {
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                SkillResource resource = entry.getValue();
                String resourceId = generateResourceId(resource.getType(), resource.getName());
                
                ConfigForm resourceConfigForm = buildResourceConfigForm(resource, namespaceId, skillGroup, resourceId);
                ConfigRequestInfo resourceConfigRequest = new ConfigRequestInfo();
                resourceConfigRequest.setUpdateForExist(Boolean.TRUE);
                configOperationService.publishConfig(resourceConfigForm, resourceConfigRequest, null);
            }
        }
        
        long startOperationTime = System.currentTimeMillis();
        syncEffectService.toSync(mainConfigForm, startOperationTime);
    }
    
    @Override
    public void deleteSkill(String namespaceId, String skillName) throws NacosException {
        // 1. Query main config to get resource list
        String skillGroup = "skill_" + skillName;
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest("skill.json", skillGroup,
                namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            return; // Already deleted
        }
        
        // 2. Delete all resource configs
        SkillMainConfig mainConfig = JacksonUtils.toObj(response.getContent(), SkillMainConfig.class);
        if (mainConfig.getResource() != null && !mainConfig.getResource().isEmpty()) {
            for (String resourceId : mainConfig.getResource().keySet()) {
                String resourceDataId = "resource_" + resourceId + ".json";
                configOperationService.deleteConfig(resourceDataId, skillGroup, namespaceId, null, null, "nacos",
                        null);
            }
        }
        
        // 3. Delete main config
        configOperationService.deleteConfig("skill.json", skillGroup, namespaceId, null, null, "nacos", null);
    }
    
    @Override
    public Page<SkillBasicInfo> listSkills(String namespaceId, String skillName, String search, int pageNo,
            int pageSize) throws NacosException {
        // Only query skill.json (main config), not resource_*.json
        String dataId = "skill.json";
        String groupPattern;
        
        if (StringUtils.isEmpty(skillName)) {
            // Query all skills: group=skill_*
            groupPattern = "skill_" + Constants.ALL_PATTERN;
        } else if (Skills.SEARCH_ACCURATE.equalsIgnoreCase(search)) {
            // Exact match: group=skill_{skillName}
            groupPattern = "skill_" + skillName;
        } else {
            // Blur search: group=skill_*{skillName}*
            groupPattern = "skill_" + Constants.ALL_PATTERN + skillName + Constants.ALL_PATTERN;
        }
        
        // Use ConfigInfoPersistService to query config list (now includes gmt_modified)
        Page<ConfigInfo> configInfoPage = configInfoPersistService.findConfigInfoLike4Page(pageNo, pageSize, dataId,
                groupPattern, namespaceId, null);
       
 
        List<SkillBasicInfo> skillBasicInfos = configInfoPage.getPageItems().stream().map(configInfo -> {
            try {
                SkillMainConfig mainConfig = JacksonUtils.toObj(configInfo.getContent(), SkillMainConfig.class);
                
               
                SkillBasicInfo basicInfo = new SkillBasicInfo();
                basicInfo.setNamespaceId(namespaceId);
                basicInfo.setName(mainConfig.getName());
                basicInfo.setDescription(mainConfig.getDescription());
                // Get modify time directly from ConfigInfo
                basicInfo.setUpdateTime(configInfo.getGmtModified());
                return basicInfo;
            } catch (Exception e) {
                LOGGER.warn("Failed to parse skill config: dataId={}, group={}", configInfo.getDataId(), configInfo.getGroup(), e);
                return null;
            }
        }).filter(java.util.Objects::nonNull).toList();
        
        Page<SkillBasicInfo> result = new Page<>();
        // For blur search with filtering, we need to adjust total count
        // But for simplicity, use the original total count (may be slightly inaccurate for blur search)
        result.setPageItems(skillBasicInfos);
        result.setTotalCount(configInfoPage.getTotalCount());
        result.setPagesAvailable((int) Math.ceil((double) configInfoPage.getTotalCount() / (double) pageSize));
        result.setPageNumber(pageNo);
        
        return result;
    }
    
    @Override
    public String uploadSkillFromZip(String namespaceId, byte[] zipBytes) throws NacosException {
        Skill skill = SkillZipParser.parseSkillFromZip(zipBytes, namespaceId);
        return registerSkill(skill, namespaceId);
    }
    
    /**
     * Build main config form.
     */
    private ConfigForm buildMainConfigForm(Skill skill, String namespaceId, String skillGroup) {
        // Build main config (only references, no content)
        SkillMainConfig mainConfig = new SkillMainConfig();
        mainConfig.setName(skill.getName());
        mainConfig.setDescription(skill.getDescription());
        mainConfig.setInstruction(skill.getInstruction());
        
        // Build resource references (without content), use resourceId as key
        Map<String, SkillResourceRef> resourceRefs = new HashMap<>(
                skill.getResource() != null ? skill.getResource().size() : 16);
        if (skill.getResource() != null) {
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                SkillResource resource = entry.getValue();
                String resourceId = generateResourceId(resource.getType(), resource.getName());
                SkillResourceRef ref = new SkillResourceRef();
                ref.setName(resource.getName());
                ref.setType(resource.getType());
                resourceRefs.put(resourceId, ref);
            }
        }
        mainConfig.setResource(resourceRefs);
        
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("skill.json");
        configForm.setGroup(skillGroup);
        configForm.setNamespaceId(namespaceId);
        configForm.setContent(JacksonUtils.toJson(mainConfig));
        configForm.setConfigTags("nacos.internal.config=skill");
        configForm.setSrcUser("nacos");
        configForm.setType(ConfigType.JSON.getType());
        
        return configForm;
    }
    
    /**
     * Build resource config form.
     */
    private ConfigForm buildResourceConfigForm(SkillResource resource, String namespaceId, String skillGroup,
            String resourceId) {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("resource_" + resourceId + ".json");
        configForm.setGroup(skillGroup);
        configForm.setNamespaceId(namespaceId);
        configForm.setContent(JacksonUtils.toJson(resource));
        configForm.setConfigTags("nacos.internal.config=skill-resource");
        configForm.setSrcUser("nacos");
        configForm.setType(ConfigType.JSON.getType());
        
        return configForm;
    }
    
    /**
     * Skill main config (from main.json).
     */
    private static class SkillMainConfig {
        private String name;
        private String description;
        private String instruction;
        private Map<String, SkillResourceRef> resource;
        
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
        
        public Map<String, SkillResourceRef> getResource() {
            return resource;
        }
        
        public void setResource(Map<String, SkillResourceRef> resource) {
            this.resource = resource;
        }
    }
    
    /**
     * Skill resource reference (in main.json).
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
}
