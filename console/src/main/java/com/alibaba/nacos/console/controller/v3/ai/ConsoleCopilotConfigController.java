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
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemType;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigRequestInfo;
import com.alibaba.nacos.config.server.model.form.ConfigForm;
import com.alibaba.nacos.config.server.utils.RequestUtil;
import com.alibaba.nacos.console.model.ai.ConsoleCopilotConfig;
import com.alibaba.nacos.console.model.ai.ConsoleCopilotConfigForm;
import com.alibaba.nacos.console.proxy.config.ConfigProxy;
import com.alibaba.nacos.copilot.config.CopilotAgentManager;
import com.alibaba.nacos.copilot.config.CopilotProperties;
import com.alibaba.nacos.copilot.connection.CopilotConnectionInfo;
import com.alibaba.nacos.copilot.connection.CopilotConnectionStatus;
import com.alibaba.nacos.copilot.constant.CopilotConstants;
import com.alibaba.nacos.core.plugin.config.PluginConfigMasker;
import jakarta.servlet.http.HttpServletRequest;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

import static com.alibaba.nacos.plugin.auth.constant.Constants.Resource.CONSOLE_RESOURCE_NAME_PREFIX;

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
    
    private static final ConfigItemDefinition API_KEY_DEFINITION =
        new ConfigItemDefinition.Builder("apiKey", "Copilot api key", ConfigItemType.STRING)
            .sensitive(true).build();
    
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
     * Get current Copilot configuration.
     *
     * <p>The stored api key is returned masked, and the connection metadata never carries a
     * credential.
     *
     * @return configuration view with the masked api key and the connection metadata
     */
    @Since("3.2.0")
    @GetMapping
    @Secured(resource = CONSOLE_RESOURCE_NAME_PREFIX + "copilot/config",
        action = ActionTypes.READ, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<ConsoleCopilotConfig> getConfig() throws NacosException {
        CopilotProperties storedConfig = getStoredConfig();
        CopilotProperties config =
            storedConfig == null ? new CopilotProperties() : storedConfig;
        
        ConsoleCopilotConfig view = new ConsoleCopilotConfig();
        view.setApiKey(maskApiKey(config.getApiKey()));
        view.setModel(config.getModel());
        view.setStudioUrl(config.getStudioUrl());
        view.setStudioProject(config.getStudioProject());
        view.setSelectedConnection(config.getSelectedConnection());
        
        CopilotConnectionStatus status = agentManager.getConnectionStatus();
        view.setConnections(status.getConnections());
        view.setActiveConnection(status.getActiveConnection());
        
        return Result.success(view);
    }
    
    /**
     * Create or update Copilot configuration.
     *
     * <p>The api key is three-state: absent or masked keeps the stored key, a new value
     * replaces it, and an empty value clears it. Clearing a stored key also stops the legacy
     * connection from falling back to the process environment. The connection selection must
     * name an existing, enabled, and available connection; an empty value clears it so routing
     * uses the default order.
     *
     * @param request HTTP servlet request.
     * @param config configuration fields to update, other fields are ignored
     * @return success result
     */
    @Since("3.2.0")
    @PostMapping
    @Secured(resource = CONSOLE_RESOURCE_NAME_PREFIX + "copilot/config",
        action = ActionTypes.WRITE, signType = SignType.AI, apiType = ApiType.CONSOLE_API)
    public Result<Boolean> saveConfig(HttpServletRequest request,
        @RequestBody ConsoleCopilotConfigForm config)
        throws NacosException {
        if (config == null) {
            throw new NacosException(NacosException.INVALID_PARAM, "Configuration cannot be null");
        }
        
        CopilotProperties existingConfig = getStoredConfig();
        CopilotProperties fullConfig =
            existingConfig == null ? new CopilotProperties() : existingConfig;
        
        applyApiKey(fullConfig, config.getApiKey());
        if (config.getModel() != null) {
            fullConfig.setModel(config.getModel());
        }
        if (config.getStudioUrl() != null) {
            fullConfig.setStudioUrl(config.getStudioUrl());
        }
        if (config.getStudioProject() != null) {
            fullConfig.setStudioProject(config.getStudioProject());
        }
        applySelectedConnection(fullConfig, config.getSelectedConnection());
        
        boolean success = publishStoredConfig(request, fullConfig);
        
        if (success) {
            // Refresh configuration after config update
            agentManager.refreshConfig();
        }
        
        return Result.success(success);
    }
    
    private void applyApiKey(CopilotProperties target, String submittedApiKey) {
        if (submittedApiKey == null || PluginConfigMasker.isMaskedValue(submittedApiKey)) {
            return;
        }
        if (StringUtils.isBlank(submittedApiKey)) {
            // The legacy console always submits an empty key when the field is untouched, so
            // an empty value only clears a credential that is actually stored and must not
            // start blocking the environment for configurations that never stored one.
            if (StringUtils.isNotEmpty(target.getApiKey())) {
                target.setApiKey("");
                target.setApiKeyCleared(Boolean.TRUE);
            }
            return;
        }
        target.setApiKey(submittedApiKey);
        target.setApiKeyCleared(null);
    }
    
    private void applySelectedConnection(CopilotProperties target,
        String submittedConnection) throws NacosException {
        if (submittedConnection == null) {
            return;
        }
        String trimmed = submittedConnection.trim();
        if (trimmed.isEmpty()) {
            target.setSelectedConnection(null);
            return;
        }
        CopilotConnectionInfo connection = findConnection(trimmed);
        if (connection == null) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(),
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Copilot connection [" + trimmed + "] does not exist");
        }
        if (!connection.isEnabled()) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(),
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Copilot connection [" + trimmed + "] is disabled");
        }
        if (!connection.isAvailable()) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(),
                ErrorCode.PARAMETER_VALIDATE_ERROR,
                "Copilot connection [" + trimmed + "] is unavailable: "
                    + connection.getUnavailableReason());
        }
        // Store the declared spelling so the selection matches the connection table exactly.
        target.setSelectedConnection(connection.getName());
    }
    
    private CopilotConnectionInfo findConnection(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        for (CopilotConnectionInfo connection : agentManager.getConnectionStatus()
            .getConnections()) {
            if (connection.getName() != null
                && connection.getName().toLowerCase(Locale.ROOT).equals(normalized)) {
                return connection;
            }
        }
        return null;
    }
    
    private String maskApiKey(String apiKey) {
        if (StringUtils.isBlank(apiKey)) {
            return apiKey;
        }
        return PluginConfigMasker.mask(API_KEY_DEFINITION, apiKey);
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
