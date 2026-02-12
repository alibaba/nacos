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
import com.alibaba.nacos.api.ai.model.skills.SkillUtils;
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

import java.util.ArrayList;
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
            
            // 2. Build main config (skill.json)
            SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skill.getName());
            long uniformId = System.currentTimeMillis();
            ConfigForm mainConfigForm = buildMainConfigForm(skill, namespaceId, mainConfigInfo.getGroup(), uniformId);
            ConfigRequestInfo mainConfigRequest = new ConfigRequestInfo();
            Boolean mainPublishResult = configOperationService.publishConfig(mainConfigForm, mainConfigRequest, null);
            if (mainPublishResult == null || !mainPublishResult) {
                throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                        String.format("Failed to publish main config for skill: %s", skill.getName()));
            }
            
            // 3. Build and publish resource configs
            if (skill.getResource() != null && !skill.getResource().isEmpty()) {
                for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                    SkillResource resource = entry.getValue();
                    SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                            skill.getName(), resource.getType(), resource.getName());
                    
                    ConfigForm resourceConfigForm = buildResourceConfigForm(resource, namespaceId, 
                            resourceConfigInfo.getGroup(), resourceConfigInfo.getDataId(), uniformId);
                    ConfigRequestInfo resourceConfigRequest = new ConfigRequestInfo();
                    Boolean resourcePublishResult = configOperationService.publishConfig(resourceConfigForm, resourceConfigRequest, null);
                    if (resourcePublishResult == null || !resourcePublishResult) {
                        throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                                String.format("Failed to publish resource config for skill: %s, resource: %s", 
                                        skill.getName(), resource.getName()));
                    }
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
        SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skillName);
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(
                mainConfigInfo.getDataId(), mainConfigInfo.getGroup(), namespaceId);
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
                mainConfig.getResources() != null ? mainConfig.getResources().size() : 16);
        if (mainConfig.getResources() != null && !mainConfig.getResources().isEmpty()) {
            for (SkillResourceRef resourceRef : mainConfig.getResources()) {
                // Generate resourceId from type and name
                String resourceId = SkillUtils.generateResourceId(resourceRef.getType(), resourceRef.getName());
                
                // Query resource config using resourceRef info
                SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                        skillName, resourceRef.getType(), resourceRef.getName());
                ConfigQueryChainRequest resourceRequest = ConfigQueryChainRequest.buildConfigQueryChainRequest(
                        resourceConfigInfo.getDataId(), resourceConfigInfo.getGroup(), namespaceId);
                ConfigQueryChainResponse resourceResponse = configQueryChainService.handle(resourceRequest);
                
                if (resourceResponse.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL
                        || resourceResponse.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_GRAY) {
                    SkillResource resource = JacksonUtils.toObj(resourceResponse.getContent(), SkillResource.class);
                    // Use resourceId as key so multi-level paths and same filename in different folders are unique
                    resourceMap.put(resourceId, resource);
                } else {
                    LOGGER.warn("Resource configuration not found: dataId={}, group={}", 
                            resourceConfigInfo.getDataId(), resourceConfigInfo.getGroup());
                }
            }
        }
        skill.setResource(resourceMap);
        
        return skill;
    }
    
    @Override
    public void updateSkill(Skill skill, String namespaceId) throws NacosException {
        // 1. Check if skill exists and get existing main config
        SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skill.getName());
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(
                mainConfigInfo.getDataId(), mainConfigInfo.getGroup(), namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + skill.getName());
        }
        
        // 3. Parse existing main config to get all existing resources
        SkillMainConfig existingMainConfig = JacksonUtils.toObj(response.getContent(), SkillMainConfig.class);
        
        // 4. Generate uniform timestamp for all configs in this update
        long uniformId = System.currentTimeMillis();
        
        // 5. Update main config with uniformId
        ConfigForm mainConfigForm = buildMainConfigForm(skill, namespaceId, mainConfigInfo.getGroup(), uniformId);
        ConfigRequestInfo mainConfigRequest = new ConfigRequestInfo();
        mainConfigRequest.setUpdateForExist(Boolean.TRUE);
        Boolean mainUpdateResult = configOperationService.publishConfig(mainConfigForm, mainConfigRequest, null);
        if (mainUpdateResult == null || !mainUpdateResult) {
            throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                    String.format("Failed to update main config for skill: %s", skill.getName()));
        }
        
        // 6. Build a set of existing resource keys for comparison
        java.util.Set<String> existingResourceKeys = new java.util.HashSet<>();
        if (existingMainConfig.getResources() != null) {
            for (SkillResourceRef resourceRef : existingMainConfig.getResources()) {
                String key = SkillUtils.generateResourceId(resourceRef.getType(), resourceRef.getName());
                existingResourceKeys.add(key);
            }
        }
        
        // 7. Build a set of new resource keys
        java.util.Set<String> newResourceKeys = new java.util.HashSet<>();
        if (skill.getResource() != null) {
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                SkillResource resource = entry.getValue();
                String key = SkillUtils.generateResourceId(resource.getType(), resource.getName());
                newResourceKeys.add(key);
            }
        }
        
        // 8. Process all resources from the new skill object
        if (skill.getResource() != null && !skill.getResource().isEmpty()) {
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                SkillResource resource = entry.getValue();
                SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                        skill.getName(), resource.getType(), resource.getName());
                
                String resourceKey = SkillUtils.generateResourceId(resource.getType(), resource.getName());
                boolean isNewResource = !existingResourceKeys.contains(resourceKey);
                
                ConfigForm resourceConfigForm = buildResourceConfigForm(resource, namespaceId,
                        resourceConfigInfo.getGroup(), resourceConfigInfo.getDataId(), uniformId);
                ConfigRequestInfo resourceConfigRequest = new ConfigRequestInfo();
                
                if (!isNewResource) {
                    // Update existing resource
                    resourceConfigRequest.setUpdateForExist(Boolean.TRUE);
                }
                // For new resources, don't set updateForExist - this will create them
                
                Boolean resourcePublishResult = configOperationService.publishConfig(resourceConfigForm, resourceConfigRequest, null);
                if (resourcePublishResult == null || !resourcePublishResult) {
                    throw new NacosApiException(NacosException.SERVER_ERROR, ErrorCode.SERVER_ERROR,
                            String.format("Failed to %s resource config for skill: %s, resource: %s",
                                    isNewResource ? "create" : "update", skill.getName(), resource.getName()));
                }
            }
        }
        
        // 9. Delete resources that were removed
        if (existingMainConfig.getResources() != null && !existingMainConfig.getResources().isEmpty()) {
            for (SkillResourceRef resourceRef : existingMainConfig.getResources()) {
                String key = SkillUtils.generateResourceId(resourceRef.getType(), resourceRef.getName());
                if (!newResourceKeys.contains(key)) {
                    // This resource was removed, delete it
                    SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                            skill.getName(), resourceRef.getType(), resourceRef.getName());
                    configOperationService.deleteConfig(resourceConfigInfo.getDataId(), resourceConfigInfo.getGroup(),
                            namespaceId, null, null, "nacos", null);
                }
            }
        }
        
        long startOperationTime = System.currentTimeMillis();
        syncEffectService.toSync(mainConfigForm, startOperationTime);
    }
    
    @Override
    public void deleteSkill(String namespaceId, String skillName) throws NacosException {
        // 1. Query main config to get resource list
        SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skillName);
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest(
                mainConfigInfo.getDataId(), mainConfigInfo.getGroup(), namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            return; // Already deleted
        }
        
        // 2. Delete all resource configs
        SkillMainConfig mainConfig = JacksonUtils.toObj(response.getContent(), SkillMainConfig.class);
        if (mainConfig.getResources() != null && !mainConfig.getResources().isEmpty()) {
            for (SkillResourceRef resourceRef : mainConfig.getResources()) {
                SkillUtils.ConfigInfo resourceConfigInfo = SkillUtils.buildSkillResourceConfigInfo(
                        skillName, resourceRef.getType(), resourceRef.getName());
                configOperationService.deleteConfig(resourceConfigInfo.getDataId(), resourceConfigInfo.getGroup(), 
                        namespaceId, null, null, "nacos", null);
            }
        }
        
        // 3. Delete main config
        configOperationService.deleteConfig(mainConfigInfo.getDataId(), mainConfigInfo.getGroup(), 
                namespaceId, null, null, "nacos", null);
    }
    
    @Override
    public Page<SkillBasicInfo> listSkills(String namespaceId, String skillName, String search, int pageNo,
            int pageSize) throws NacosException {
        // Only query skill.json (main config), not resource_*.json
        String dataId = SkillUtils.SKILL_MAIN_DATA_ID;
        String groupPattern;
        
        if (StringUtils.isEmpty(skillName)) {
            // Query all skills: group=skill_*
            groupPattern = SkillUtils.SKILL_GROUP_PREFIX + Constants.ALL_PATTERN;
        } else if (Skills.SEARCH_ACCURATE.equalsIgnoreCase(search)) {
            // Exact match: group=skill_{skillName}
            SkillUtils.ConfigInfo mainConfigInfo = SkillUtils.buildSkillMainConfigInfo(skillName);
            groupPattern = mainConfigInfo.getGroup();
        } else {
            // Blur search: group=skill_*{skillName}*
            groupPattern = SkillUtils.SKILL_GROUP_PREFIX + Constants.ALL_PATTERN + skillName + Constants.ALL_PATTERN;
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
        configForm.setGroup(mainConfigInfo.getGroup());
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
        configForm.setType(ConfigType.JSON.getType());
        
        return configForm;
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
}
