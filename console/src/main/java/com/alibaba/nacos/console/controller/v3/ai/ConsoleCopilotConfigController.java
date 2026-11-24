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
import com.alibaba.nacos.common.utils.MD5Utils;
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
import com.alibaba.nacos.copilot.config.StoredCopilotConfig;
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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

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
    
    private static final int MAX_SAVE_ATTEMPTS = 3;
    
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
        view.setEffectiveSelectedConnection(status.getEffectiveSelectedConnection());
        view.setSelectedConnectionSource(status.getSelectionSource());
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
     * <p>Only the submitted fields are patched into the stored document. The save persists
     * first, then publishes the resolved snapshot, so a failed persist leaves the in-memory
     * configuration unchanged. A compare-and-set guard makes a concurrent save re-read and
     * re-apply the patch instead of dropping the other update.
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
        // A null selection keeps the stored value, an empty one clears it, and a non-empty
        // one is the declared spelling of the connection that passed validation.
        String submittedSelection = resolveSubmittedSelection(config.getSelectedConnection());
        
        for (int attempt = 0; attempt < MAX_SAVE_ATTEMPTS; attempt++) {
            StoredCopilotConfig current = readStoredDocument();
            Map<String, Object> document = new LinkedHashMap<>(
                current == null ? new LinkedHashMap<>() : current.getFields());
            applyPatch(document, config, submittedSelection, current);
            boolean persisted;
            try {
                persisted = publishStoredConfig(request, document, current);
            } catch (NacosApiException e) {
                if (isCasConflict(e) && attempt < MAX_SAVE_ATTEMPTS - 1) {
                    continue;
                }
                throw e;
            }
            if (!persisted) {
                return Result.success(false);
            }
            activateStoredConfig();
            return Result.success(true);
        }
        throw new NacosApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(),
            ErrorCode.RESOURCE_CONFLICT,
            "Copilot configuration changed concurrently, please retry");
    }
    
    /**
     * Resolve the submitted selection against the connection table of the last refresh.
     *
     * @param submittedConnection raw value from the request, {@code null} keeps the stored one
     * @return {@code null} to keep the stored value, an empty string to clear it, or the
     *     declared connection name to store
     */
    private String resolveSubmittedSelection(String submittedConnection) throws NacosException {
        if (submittedConnection == null) {
            return null;
        }
        String trimmed = submittedConnection.trim();
        if (trimmed.isEmpty()) {
            return "";
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
        return connection.getName();
    }
    
    private void applyPatch(Map<String, Object> document, ConsoleCopilotConfigForm patch,
        String submittedSelection, StoredCopilotConfig current) {
        String submittedApiKey = patch.getApiKey();
        if (submittedApiKey != null && !PluginConfigMasker.isMaskedValue(submittedApiKey)) {
            if (StringUtils.isBlank(submittedApiKey)) {
                // The legacy console always submits an empty key when the field is untouched,
                // so an empty value only clears a credential that is actually stored and must
                // not start blocking the environment for configurations that never stored one.
                if (hasStoredApiKey(current)) {
                    document.put(StoredCopilotConfig.API_KEY, "");
                    document.put(StoredCopilotConfig.API_KEY_CLEARED, Boolean.TRUE);
                }
            } else {
                document.put(StoredCopilotConfig.API_KEY, submittedApiKey);
                document.remove(StoredCopilotConfig.API_KEY_CLEARED);
            }
        }
        if (patch.getModel() != null) {
            document.put(StoredCopilotConfig.MODEL, patch.getModel());
        }
        if (patch.getStudioUrl() != null) {
            document.put(StoredCopilotConfig.STUDIO_URL, patch.getStudioUrl());
        }
        if (patch.getStudioProject() != null) {
            document.put(StoredCopilotConfig.STUDIO_PROJECT, patch.getStudioProject());
        }
        if (submittedSelection != null) {
            document.put(StoredCopilotConfig.SELECTED_CONNECTION,
                submittedSelection.isEmpty() ? null : submittedSelection);
        }
    }
    
    private boolean hasStoredApiKey(StoredCopilotConfig current) {
        return current != null
            && StringUtils.isNotEmpty(current.getProperties().getApiKey());
    }
    
    private boolean isCasConflict(NacosApiException exception) {
        return exception.getDetailErrCode() == ErrorCode.RESOURCE_CONFLICT.getCode();
    }
    
    /**
     * Publish the patched document and resolve the new snapshot. The stored document is
     * persisted before anything is activated, so a failed persist keeps the current
     * in-memory configuration.
     */
    private void activateStoredConfig() throws NacosApiException {
        try {
            agentManager.refreshConfig();
        } catch (RuntimeException e) {
            String reason = StringUtils.isBlank(e.getMessage())
                ? e.getClass().getSimpleName() : e.getMessage();
            throw new NacosApiException(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ErrorCode.SERVER_ERROR, "Copilot configuration was stored but not activated: "
                    + reason + "; this node keeps the previous configuration until the next "
                    + "refresh");
        }
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
        StoredCopilotConfig stored = readStoredDocument();
        return stored == null ? null : stored.getProperties();
    }
    
    private StoredCopilotConfig readStoredDocument() {
        try {
            ConfigDetailInfo configInfo =
                configProxy.getConfigDetail(CONFIG_DATA_ID, CONFIG_GROUP, getConfigNamespace());
            if (configInfo == null || StringUtils.isBlank(configInfo.getContent())) {
                return null;
            }
            return StoredCopilotConfig.parse(configInfo.getContent());
        } catch (Exception e) {
            return null;
        }
    }
    
    private boolean publishStoredConfig(HttpServletRequest request, Map<String, Object> document,
        StoredCopilotConfig current) throws NacosException {
        ConfigForm configForm = new ConfigForm();
        configForm.setDataId(CONFIG_DATA_ID);
        configForm.setGroup(CONFIG_GROUP);
        configForm.setNamespaceId(getConfigNamespace());
        configForm.setContent(JacksonUtils.toJson(document));
        configForm.setAppName(CONFIG_APP_NAME);
        configForm.setSrcUser(CONFIG_SRC_USER);
        configForm.setDesc(CONFIG_DESC);
        configForm.setType(CONFIG_TYPE);
        
        ConfigRequestInfo configRequestInfo = new ConfigRequestInfo();
        configRequestInfo.setSrcIp(RequestUtil.getRemoteIp(request));
        configRequestInfo.setSrcType(Constants.HTTP);
        configRequestInfo.setRequestIpApp(RequestUtil.getAppName(request));
        if (current != null && StringUtils.isNotBlank(current.getContent())) {
            configRequestInfo.setCasMd5(
                MD5Utils.md5Hex(current.getContent(), StandardCharsets.UTF_8.name()));
        }
        return Boolean.TRUE.equals(configProxy.publishConfig(configForm, configRequestInfo));
    }
    
    private String getConfigNamespace() {
        return NamespaceUtil.processNamespaceParameter(configNamespace);
    }
}
