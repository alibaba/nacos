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
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
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
    
    private final ConfigQueryChainService configQueryChainService;
    
    private final ConfigOperationService configOperationService;
    
    private final ConfigDetailService configDetailService;
    
    private final SyncEffectService syncEffectService;
    
    public SkillOperationServiceImpl(ConfigQueryChainService configQueryChainService,
            ConfigOperationService configOperationService, ConfigDetailService configDetailService,
            SyncEffectService syncEffectService) {
        this.configQueryChainService = configQueryChainService;
        this.configOperationService = configOperationService;
        this.configDetailService = configDetailService;
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
            
            // 2. Build main config (main.json)
            String skillGroup = "nacos-ai-skill-" + skill.getName();
            ConfigForm mainConfigForm = buildMainConfigForm(skill, namespaceId, skillGroup);
            ConfigRequestInfo mainConfigRequest = new ConfigRequestInfo();
            mainConfigRequest.setUpdateForExist(Boolean.FALSE);
            configOperationService.publishConfig(mainConfigForm, mainConfigRequest, null);
            
            // 3. Build and publish resource configs
            if (skill.getResource() != null && !skill.getResource().isEmpty()) {
                String resourceGroup = skillGroup + "_resource";
                for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                    String resourceName = entry.getKey();
                    SkillResource resource = entry.getValue();
                    
                    ConfigForm resourceConfigForm = buildResourceConfigForm(resource, namespaceId, resourceGroup, resourceName);
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
        String skillGroup = "nacos-ai-skill-" + skillName;
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest("main.json", skillGroup,
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
            String resourceGroup = skillGroup + "_resource";
            for (Map.Entry<String, SkillResourceRef> entry : mainConfig.getResource().entrySet()) {
                String resourceName = entry.getKey();
                
                // Query resource config
                String resourceDataId = resourceName + ".json";
                ConfigQueryChainRequest resourceRequest = ConfigQueryChainRequest.buildConfigQueryChainRequest(
                        resourceDataId, resourceGroup, namespaceId);
                ConfigQueryChainResponse resourceResponse = configQueryChainService.handle(resourceRequest);
                
                if (resourceResponse.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL
                        || resourceResponse.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_GRAY) {
                    SkillResource resource = JacksonUtils.toObj(resourceResponse.getContent(), SkillResource.class);
                    resourceMap.put(resourceName, resource);
                } else {
                    LOGGER.warn("Resource configuration not found: dataId={}, group={}", resourceDataId, resourceGroup);
                }
            }
        }
        skill.setResource(resourceMap);
        
        return skill;
    }
    
    @Override
    public void updateSkill(Skill skill, String namespaceId) throws NacosException {
        // 1. Check if skill exists
        String skillGroup = "nacos-ai-skill-" + skill.getName();
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest("main.json", skillGroup,
                namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            throw new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Skill not found: " + skill.getName());
        }
        
        // 2. Update main config
        ConfigForm mainConfigForm = buildMainConfigForm(skill, namespaceId, skillGroup);
        ConfigRequestInfo mainConfigRequest = new ConfigRequestInfo();
        mainConfigRequest.setUpdateForExist(Boolean.TRUE);
        configOperationService.publishConfig(mainConfigForm, mainConfigRequest, null);
        
        // 3. Update resource configs
        if (skill.getResource() != null && !skill.getResource().isEmpty()) {
            String resourceGroup = skillGroup + "_resource";
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                String resourceName = entry.getKey();
                SkillResource resource = entry.getValue();
                
                ConfigForm resourceConfigForm = buildResourceConfigForm(resource, namespaceId, resourceGroup, resourceName);
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
        String skillGroup = "nacos-ai-skill-" + skillName;
        ConfigQueryChainRequest request = ConfigQueryChainRequest.buildConfigQueryChainRequest("main.json", skillGroup,
                namespaceId);
        ConfigQueryChainResponse response = configQueryChainService.handle(request);
        
        if (response.getStatus() == ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND) {
            return; // Already deleted
        }
        
        // 2. Delete all resource configs
        SkillMainConfig mainConfig = JacksonUtils.toObj(response.getContent(), SkillMainConfig.class);
        if (mainConfig.getResource() != null && !mainConfig.getResource().isEmpty()) {
            String resourceGroup = skillGroup + "_resource";
            for (String resourceName : mainConfig.getResource().keySet()) {
                String resourceDataId = resourceName + ".json";
                configOperationService.deleteConfig(resourceDataId, resourceGroup, namespaceId, null, null, "nacos",
                        null);
            }
        }
        
        // 3. Delete main config
        configOperationService.deleteConfig("main.json", skillGroup, namespaceId, null, null, "nacos", null);
    }
    
    @Override
    public Page<SkillBasicInfo> listSkills(String namespaceId, String skillName, String search, int pageNo,
            int pageSize) throws NacosException {
        String dataId;
        if (StringUtils.isEmpty(skillName) || Skills.SEARCH_BLUR.equalsIgnoreCase(search)) {
            search = Skills.SEARCH_BLUR;
            dataId = Constants.ALL_PATTERN + skillName + Constants.ALL_PATTERN;
        } else {
            search = Skills.SEARCH_ACCURATE;
            dataId = skillName;
        }
        
        // Search by group pattern: nacos-ai-skill-*
        String groupPattern = "nacos-ai-skill-" + Constants.ALL_PATTERN;
        Page<ConfigInfo> configInfoPage = configDetailService.findConfigInfoPage(search, pageNo, pageSize, "main.json",
                groupPattern, namespaceId, null);
        
        List<SkillBasicInfo> skillBasicInfos = configInfoPage.getPageItems().stream().map(configInfo -> {
            try {
                SkillMainConfig mainConfig = JacksonUtils.toObj(configInfo.getContent(), SkillMainConfig.class);
                SkillBasicInfo basicInfo = new SkillBasicInfo();
                basicInfo.setNamespaceId(namespaceId);
                basicInfo.setName(mainConfig.getName());
                basicInfo.setDescription(mainConfig.getDescription());
                // ConfigInfo doesn't have modifyTime field, set to null or use current time
                basicInfo.setUpdateTime(System.currentTimeMillis());
                return basicInfo;
            } catch (Exception e) {
                LOGGER.warn("Failed to parse skill config: {}", configInfo.getDataId(), e);
                return null;
            }
        }).filter(java.util.Objects::nonNull).toList();
        
        Page<SkillBasicInfo> result = new Page<>();
        result.setPageItems(skillBasicInfos);
        result.setTotalCount(configInfoPage.getTotalCount());
        result.setPagesAvailable((int) Math.ceil((double) configInfoPage.getTotalCount() / (double) pageSize));
        result.setPageNumber(pageNo);
        
        return result;
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
        
        // Build resource references (without content)
        Map<String, SkillResourceRef> resourceRefs = new HashMap<>(
                skill.getResource() != null ? skill.getResource().size() : 16);
        if (skill.getResource() != null) {
            for (Map.Entry<String, SkillResource> entry : skill.getResource().entrySet()) {
                SkillResource resource = entry.getValue();
                SkillResourceRef ref = new SkillResourceRef();
                ref.setName(resource.getName());
                ref.setType(resource.getType());
                resourceRefs.put(entry.getKey(), ref);
            }
        }
        mainConfig.setResource(resourceRefs);
        
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId("main.json");
        configForm.setGroup(skillGroup);
        configForm.setNamespaceId(namespaceId);
        configForm.setContent(JacksonUtils.toJson(mainConfig));
        configForm.setConfigTags("nacos.internal.config=skill");
        configForm.setAppName(skill.getName());
        configForm.setSrcUser("nacos");
        configForm.setType(ConfigType.JSON.getType());
        
        return configForm;
    }
    
    /**
     * Build resource config form.
     */
    private ConfigForm buildResourceConfigForm(SkillResource resource, String namespaceId, String resourceGroup,
            String resourceName) {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(resourceName + ".json");
        configForm.setGroup(resourceGroup);
        configForm.setNamespaceId(namespaceId);
        configForm.setContent(JacksonUtils.toJson(resource));
        configForm.setConfigTags("nacos.internal.config=skill-resource");
        configForm.setAppName(resource.getName());
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
