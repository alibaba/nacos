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

package com.alibaba.nacos.plugin.auth.impl;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.ConfigItemEffectMode;
import com.alibaba.nacos.api.plugin.ConfigItemType;
import com.alibaba.nacos.api.plugin.PluginConfigSpec;
import com.alibaba.nacos.plugin.auth.impl.authenticate.IAuthenticationManager;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.ldap.LdapAuthPluginConfig;
import com.alibaba.nacos.plugin.auth.impl.ldap.LdapAuthPluginConfigProvider;
import com.alibaba.nacos.plugin.auth.impl.ldap.LdapPluginDependencyChecker;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * LDAP auth plugin service.
 *
 * @author onewe
 */
public class LdapAuthPluginService extends AbstractNacosAuthPluginService
    implements PluginConfigSpec, LdapAuthPluginConfigProvider {
    
    private static final List<ConfigItemDefinition> CONFIG_DEFINITIONS =
        buildConfigDefinitions();
    
    private volatile LdapAuthPluginConfig config = LdapAuthPluginConfig.defaults();
    
    private static List<ConfigItemDefinition> buildConfigDefinitions() {
        ConfigItemDefinition url = restartDefinition(LdapAuthPluginConfig.URL, "LDAP URL",
            ConfigItemType.STRING, LdapAuthPluginConfig.DEFAULT_URL,
            AuthConstants.NACOS_CORE_AUTH_LDAP_URL);
        ConfigItemDefinition baseDn = restartDefinition(LdapAuthPluginConfig.BASE_DN,
            "LDAP base DN", ConfigItemType.STRING, LdapAuthPluginConfig.DEFAULT_BASE_DN,
            AuthConstants.NACOS_CORE_AUTH_LDAP_BASEDC);
        ConfigItemDefinition timeout = restartDefinition(LdapAuthPluginConfig.TIMEOUT,
            "LDAP timeout", ConfigItemType.NUMBER,
            Long.toString(LdapAuthPluginConfig.DEFAULT_TIMEOUT),
            AuthConstants.NACOS_CORE_AUTH_LDAP_TIMEOUT);
        ConfigItemDefinition userDn = restartDefinition(LdapAuthPluginConfig.USER_DN,
            "LDAP bind user DN", ConfigItemType.STRING, LdapAuthPluginConfig.DEFAULT_USER_DN,
            AuthConstants.NACOS_CORE_AUTH_LDAP_USERDN);
        ConfigItemDefinition password = restartDefinition(LdapAuthPluginConfig.PASSWORD,
            "LDAP bind password", ConfigItemType.STRING, LdapAuthPluginConfig.DEFAULT_PASSWORD,
            AuthConstants.NACOS_CORE_AUTH_LDAP_PASSWORD);
        password.setSensitive(true);
        ConfigItemDefinition filterPrefix = restartDefinition(
            LdapAuthPluginConfig.FILTER_PREFIX, "LDAP user filter attribute",
            ConfigItemType.STRING, LdapAuthPluginConfig.DEFAULT_FILTER_PREFIX,
            AuthConstants.NACOS_CORE_AUTH_LDAP_FILTER_PREFIX);
        ConfigItemDefinition caseSensitive = restartDefinition(
            LdapAuthPluginConfig.CASE_SENSITIVE, "LDAP username case sensitivity",
            ConfigItemType.BOOLEAN,
            Boolean.toString(LdapAuthPluginConfig.DEFAULT_CASE_SENSITIVE),
            AuthConstants.NACOS_CORE_AUTH_CASE_SENSITIVE);
        ConfigItemDefinition ignorePartialResult = restartDefinition(
            LdapAuthPluginConfig.IGNORE_PARTIAL_RESULT_EXCEPTION,
            "Ignore LDAP partial result exceptions", ConfigItemType.BOOLEAN,
            Boolean.toString(LdapAuthPluginConfig.DEFAULT_IGNORE_PARTIAL_RESULT_EXCEPTION),
            AuthConstants.NACOS_CORE_AUTH_IGNORE_PARTIAL_RESULT_EXCEPTION);
        return Collections.unmodifiableList(Arrays.asList(url, baseDn, timeout, userDn, password,
            filterPrefix, caseSensitive, ignorePartialResult));
    }
    
    private static ConfigItemDefinition restartDefinition(String key, String name,
        ConfigItemType type, String defaultValue, String alias) {
        return new ConfigItemDefinition.Builder(key, name, type)
            .description(name).defaultValue(defaultValue)
            .aliases(Collections.singletonList(alias))
            .effectMode(ConfigItemEffectMode.RESTART).build();
    }
    
    @Override
    public String getAuthServiceName() {
        return AuthConstants.LDAP_AUTH_PLUGIN_TYPE;
    }
    
    @Override
    public List<ConfigItemDefinition> getConfigDefinitions() {
        return CONFIG_DEFINITIONS;
    }
    
    @Override
    public void applyConfig(Map<String, String> effectiveConfig) {
        config = LdapAuthPluginConfig.from(effectiveConfig);
    }
    
    @Override
    public Map<String, String> getCurrentConfig() {
        return config.toMap();
    }
    
    @Override
    public LdapAuthPluginConfig getConfig() {
        return config;
    }
    
    @Override
    protected void checkNacosAuthManager() {
        if (null == authenticationManager) {
            authenticationManager = ApplicationUtils.getBean(
                LdapPluginDependencyChecker.LDAP_AUTHENTICATION_MANAGER_BEAN_NAME,
                IAuthenticationManager.class);
        }
    }
}
