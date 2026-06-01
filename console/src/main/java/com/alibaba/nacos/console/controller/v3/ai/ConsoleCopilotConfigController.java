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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.common.ApiType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.copilot.config.CopilotAgentManager;
import com.alibaba.nacos.copilot.config.CopilotConfigStorage;
import com.alibaba.nacos.copilot.config.CopilotProperties;
import com.alibaba.nacos.copilot.constant.CopilotConstants;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Console Copilot configuration controller.
 *
 * @author nacos
 */
@NacosApi
@RestController
@RequestMapping(CopilotConstants.COPILOT_CONSOLE_PATH + "/config")
public class ConsoleCopilotConfigController {
    
    private final CopilotConfigStorage configStorage;
    
    private final CopilotAgentManager agentManager;
    
    @Autowired
    public ConsoleCopilotConfigController(CopilotConfigStorage configStorage,
        CopilotAgentManager agentManager) {
        this.configStorage = configStorage;
        this.agentManager = agentManager;
    }
    
    /**
     * Get current Copilot configuration. Only returns apiKey, model, studioUrl and studioProject fields.
     *
     * @return Simplified CopilotProperties with only apiKey, model, studioUrl and studioProject
     */
    @Since("3.2.0")
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<CopilotProperties> getConfig() throws NacosException {
        CopilotProperties config = configStorage.getConfig();
        if (config == null) {
            // Return default empty config if not configured
            config = new CopilotProperties();
        }
        
        // Create simplified config with only apiKey, model, studioUrl and studioProject
        CopilotProperties simplifiedConfig = new CopilotProperties();
        simplifiedConfig.setApiKey(config.getApiKey());
        simplifiedConfig.setModel(config.getModel());
        simplifiedConfig.setStudioUrl(config.getStudioUrl());
        simplifiedConfig.setStudioProject(config.getStudioProject());
        
        return Result.success(simplifiedConfig);
    }
    
    /**
     * Create or update Copilot configuration. Only accepts apiKey, model, studioUrl and studioProject fields, other
     * fields use defaults.
     *
     * @param config Simplified CopilotProperties with only apiKey, model, studioUrl and studioProject
     * @return success result
     */
    @Since("3.2.0")
    @PostMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Boolean> saveConfig(@RequestBody CopilotProperties config) throws NacosException {
        if (config == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Configuration cannot be null");
        }
        
        // Get existing config to preserve other fields, or create new one with defaults
        CopilotProperties existingConfig = configStorage.getConfig();
        CopilotProperties fullConfig;
        
        if (existingConfig != null) {
            // Use existing config and only update apiKey, model, studioUrl and studioProject
            fullConfig = existingConfig;
        } else {
            // Create new config with default values
            fullConfig = new CopilotProperties();
        }
        
        // Update only apiKey, model, studioUrl and studioProject
        if (config.getApiKey() != null) {
            fullConfig.setApiKey(config.getApiKey());
        }
        if (config.getModel() != null) {
            fullConfig.setModel(config.getModel());
        }
        if (config.getStudioUrl() != null) {
            fullConfig.setStudioUrl(config.getStudioUrl());
        }
        if (config.getStudioProject() != null) {
            fullConfig.setStudioProject(config.getStudioProject());
        }
        
        boolean success = configStorage.saveConfig(fullConfig);
        
        if (success) {
            // Refresh configuration after config update
            agentManager.refreshConfig();
        }
        
        return Result.success(success);
    }
}
