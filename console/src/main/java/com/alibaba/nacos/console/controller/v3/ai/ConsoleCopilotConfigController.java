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

import com.alibaba.nacos.api.annotation.NacosApi;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.copilot.config.CopilotAgentManager;
import com.alibaba.nacos.copilot.config.CopilotConfigStorage;
import com.alibaba.nacos.copilot.config.CopilotProperties;
import com.alibaba.nacos.copilot.constant.CopilotConstants;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
     * Get current Copilot configuration.
     *
     * @return CopilotProperties
     */
    @GetMapping
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<CopilotProperties> getConfig() throws NacosException {
        CopilotProperties config = configStorage.getConfig();
        if (config == null) {
            // Return default empty config if not configured
            config = new CopilotProperties();
        }
        return Result.success(config);
    }
    
    /**
     * Create or update Copilot configuration.
     *
     * @param config CopilotProperties to save
     * @return success result
     */
    @PostMapping
    @PutMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Boolean> saveConfig(@RequestBody CopilotProperties config) throws NacosException {
        if (config == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Configuration cannot be null");
        }
        
        boolean success = configStorage.saveConfig(config);
        
        if (success) {
            // Refresh configuration after config update
            agentManager.refreshConfig();
        }
        
        return Result.success(success);
    }
}
