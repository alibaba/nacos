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
import com.alibaba.nacos.api.config.model.ConfigDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.console.proxy.config.ConfigProxy;
import com.alibaba.nacos.copilot.config.CopilotAgentManager;
import com.alibaba.nacos.copilot.config.CopilotProperties;
import com.alibaba.nacos.copilot.constant.CopilotConstants;
import jakarta.servlet.http.HttpServletRequest;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    
    private static final String CONFIG_DATA_ID = "copilot-config.json";
    
    private static final String CONFIG_GROUP = "nacos-copilot";
    
    private static final String CONFIG_APP_NAME = "nacos-copilot";
    
    private static final String CONFIG_SRC_USER = "system";
    
    private static final String CONFIG_DESC = "Copilot configuration";
    
    private static final String CONFIG_TYPE = "json";
    
    private final CopilotAgentManager agentManager;
    
    private final ConfigProxy configProxy;
    
    @Value("${nacos.copilot.config.namespace:public}")
    private String configNamespace;
    
    @Autowired
    public ConsoleCopilotConfigController(CopilotAgentManager agentManager,
        ConfigProxy configProxy) {
        this.agentManager = agentManager;
        this.configProxy = configProxy;
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
        CopilotProperties config = getStoredConfig();
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
     * @param request HTTP servlet request.
     * @param config Simplified CopilotProperties with only apiKey, model, studioUrl and studioProject
     * @return success result
     */
    @Since("3.2.0")
    @PostMapping
    @Secured(action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Boolean> saveConfig(HttpServletRequest request,
        @RequestBody CopilotProperties config)
        throws NacosException {
        if (config == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Configuration cannot be null");
        }
        
        // Get existing config to preserve other fields, or create new one with defaults
        CopilotProperties existingConfig = getStoredConfig();
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
        
        boolean success = publishStoredConfig(request, fullConfig);
        
        if (success) {
            // Refresh configuration after config update
            agentManager.refreshConfig();
        }
        
        return Result.success(success);
    }
    
    private CopilotProperties getStoredConfig() {
        try {
            ConfigDetailInfo configInfo =
                configProxy.getConfigDetail(CONFIG_DATA_ID, CONFIG_GROUP, getConfigNamespace());
            if (configInfo == null || configInfo.getContent() == null) {
                return null;
            }
            return JacksonUtils.toObj(configInfo.getContent(), CopilotProperties.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    private boolean publishStoredConfig(HttpServletRequest request, CopilotProperties config)
        throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(CONFIG_DATA_ID);
        configForm.setGroup(CONFIG_GROUP);
        configForm.setNamespaceId(getConfigNamespace());
        configForm.setContent(JacksonUtils.toJson(config));
        configForm.setAppName(CONFIG_APP_NAME);
        configForm.setSrcUser(CONFIG_SRC_USER);
        configForm.setDesc(CONFIG_DESC);
        configForm.setType(CONFIG_TYPE);
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setSrcIp(RequestUtil.getRemoteIp(request));
        configRequestInfo.setSrcType(Constants.HTTP);
        configRequestInfo.setRequestIpApp(RequestUtil.getAppName(request));
        return Boolean.TRUE.equals(configProxy.publishConfig(configForm, configRequestInfo));
    }
    
    private String getConfigNamespace() {
        return NamespaceUtil.processNamespaceParameter(configNamespace);
    }
}
