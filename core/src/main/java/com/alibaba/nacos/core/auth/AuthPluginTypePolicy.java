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

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.api.plugin.PluginTypeConfiguration;
import com.alibaba.nacos.api.plugin.PluginTypePolicy;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;

/**
 * Authentication plugin type policy.
 *
 * @author Nacos
 */
public class AuthPluginTypePolicy implements PluginTypePolicy {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthPluginTypePolicy.class);
    
    private static final String DEFAULT_AUTH_PLUGIN = "nacos";
    
    private String selectedPlugin = DEFAULT_AUTH_PLUGIN;
    
    private boolean explicitlyConfigured;
    
    @Override
    public void initialize(PluginTypeConfiguration configuration) {
        String configuredPlugin = resolveConfiguredPlugin(configuration);
        explicitlyConfigured = StringUtils.isNotBlank(configuredPlugin);
        selectedPlugin = explicitlyConfigured ? configuredPlugin : DEFAULT_AUTH_PLUGIN;
    }
    
    @Override
    public PluginType getPluginType() {
        return PluginType.AUTH;
    }
    
    @Override
    public boolean isActive(PluginTypeConfiguration configuration) {
        return configuration.getBooleanProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, false)
            || configuration.getBooleanProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, true)
            || configuration.getBooleanProperty(Constants.Auth.NACOS_CORE_AUTH_CONSOLE_ENABLED,
                true)
            || explicitlyConfigured;
    }
    
    @Override
    public boolean isPluginEnabledByDefault(String pluginName,
        PluginTypeConfiguration configuration) {
        return pluginName.equalsIgnoreCase(selectedPlugin);
    }
    
    @Override
    public Set<String> getRequiredPluginNames(PluginTypeConfiguration configuration) {
        return Collections.singleton(selectedPlugin);
    }
    
    @Override
    public String getSelectionProperty() {
        return Constants.Auth.NACOS_PLUGIN_AUTH_TYPE;
    }
    
    @Override
    public String getActivationDescription() {
        return "client, admin, or console authentication is enabled, or an auth type is configured";
    }
    
    private String resolveConfiguredPlugin(PluginTypeConfiguration configuration) {
        String selected = configuration.getProperty(Constants.Auth.NACOS_PLUGIN_AUTH_TYPE);
        if (StringUtils.isNotBlank(selected)) {
            return selected.trim();
        }
        selected = configuration.getProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE);
        if (StringUtils.isNotBlank(selected)) {
            LOGGER.warn("[AuthPluginTypePolicy] Auth plugin selection '{}' is read from legacy "
                + "property '{}'. Migrate to '{}'.", selected,
                Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE,
                Constants.Auth.NACOS_PLUGIN_AUTH_TYPE);
            return selected.trim();
        }
        return StringUtils.EMPTY;
    }
}
