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
import com.alibaba.nacos.copilot.config.CopilotModelProviderRegistry;
import com.alibaba.nacos.copilot.config.CopilotProperties;
import com.alibaba.nacos.copilot.config.CopilotProviderMetadata;
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

import java.util.List;

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
    
    private final CopilotModelProviderRegistry providerRegistry;
    
    @Value("${nacos.copilot.config.namespace:public}")
    private String configNamespace;
    
    @Autowired
    public ConsoleCopilotConfigController(CopilotAgentManager agentManager,
        ConfigProxy configProxy, CopilotModelProviderRegistry providerRegistry) {
        this.agentManager = agentManager;
        this.configProxy = configProxy;
        this.providerRegistry = providerRegistry;
    }
    
    /**
     * Get current Copilot configuration.
     *
     * @return console-managed Copilot configuration
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
        
        // Return only fields managed by the console.
        CopilotProperties simplifiedConfig = new CopilotProperties();
        simplifiedConfig.setApiKey(config.getApiKey());
        simplifiedConfig.setProvider(config.getProvider());
        simplifiedConfig.setProtocol(config.getProtocol());
        simplifiedConfig.setRegion(config.getRegion());
        simplifiedConfig.setModel(config.getModel());
        simplifiedConfig.setBaseUrl(config.getBaseUrl());
        simplifiedConfig.setStudioUrl(config.getStudioUrl());
        simplifiedConfig.setStudioProject(config.getStudioProject());
        
        return Result.success(simplifiedConfig);
    }
    
    /**
     * Get available Copilot providers and their configuration options.
     *
     * @return provider metadata
     */
    @Since("3.2.0")
    @GetMapping("/providers")
    @Secured(action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<List<CopilotProviderMetadata>> getProviders() {
        return Result.success(providerRegistry.getProviderMetadata());
    }
    
    /**
     * Create or update Copilot configuration.
     *
     * @param request HTTP servlet request.
     * @param config console-managed Copilot configuration
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
            // Preserve fields that are not managed by the console.
            fullConfig = existingConfig;
        } else {
            // Create new config with default values
            fullConfig = new CopilotProperties();
        }
        
        // Update only fields managed by the console.
        if (config.getApiKey() != null) {
            fullConfig.setApiKey(config.getApiKey());
        }
        if (config.getProvider() != null) {
            fullConfig.setProvider(config.getProvider());
        }
        if (config.getProtocol() != null) {
            fullConfig.setProtocol(config.getProtocol());
        }
        if (config.getRegion() != null) {
            fullConfig.setRegion(config.getRegion());
        }
        if (config.getModel() != null) {
            fullConfig.setModel(config.getModel());
        }
        if (config.getBaseUrl() != null) {
            fullConfig.setBaseUrl(config.getBaseUrl());
        }
        if (config.getStudioUrl() != null) {
            fullConfig.setStudioUrl(config.getStudioUrl());
        }
        if (config.getStudioProject() != null) {
            fullConfig.setStudioProject(config.getStudioProject());
        }
        try {
            providerRegistry.validate(fullConfig);
        } catch (IllegalArgumentException e) {
            throw new NacosException(NacosException.INVALID_PARAM, e.getMessage());
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
