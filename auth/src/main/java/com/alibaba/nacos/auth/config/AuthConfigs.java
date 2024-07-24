/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.auth.config;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.JustForTest;
import com.alibaba.nacos.common.event.ServerConfigChangeEvent;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.module.ModuleState;
import com.alibaba.nacos.sys.module.ModuleStateHolder;
import com.alibaba.nacos.sys.utils.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * Auth related configurations.
 *
 * @author nkorange
 * @author mai.jh
 * @since 1.2.0
 */
@Configuration
public class AuthConfigs extends Subscriber<ServerConfigChangeEvent> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthConfigs.class);
    
    private static final String PREFIX = "nacos.core.auth.plugin";
    
    @JustForTest
    private static Boolean cachingEnabled = null;
    
    /**
     * Whether auth enabled.
     */
    @Value("${" + Constants.Auth.NACOS_CORE_AUTH_ENABLED + ":false}")
    private boolean authEnabled;
    
    /**
     * Which auth system is in use.
     */
    @Value("${" + Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE + ":}")
    private String nacosAuthSystemType;
    
    @Value("${" + Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY + ":}")
    private String serverIdentityKey;
    
    @Value("${" + Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE + ":}")
    private String serverIdentityValue;
    
    @Value("${" + Constants.Auth.NACOS_CORE_AUTH_ENABLE_USER_AGENT_AUTH_WHITE + ":false}")
    private boolean enableUserAgentAuthWhite;
    
    private boolean hasGlobalAdminRole;
    
    private Map<String, Properties> authPluginProperties = new HashMap<>();
    
    public AuthConfigs() {
        NotifyCenter.registerSubscriber(this);
        refreshPluginProperties();
    }
    
    /**
     * Validate auth config.
     *
     * @throws NacosException If the config is not valid.
     */
    @PostConstruct
    public void validate() throws NacosException {
        if (!authEnabled) {
            return;
        }
        if (StringUtils.isEmpty(nacosAuthSystemType)) {
            throw new NacosException(AuthErrorCode.INVALID_TYPE.getCode(), AuthErrorCode.INVALID_TYPE.getMsg());
        }
        if (EnvUtil.getStandaloneMode()) {
            return;
        }
        if (StringUtils.isEmpty(serverIdentityKey) || StringUtils.isEmpty(serverIdentityValue)) {
            throw new NacosException(AuthErrorCode.EMPTY_IDENTITY.getCode(), AuthErrorCode.EMPTY_IDENTITY.getMsg());
        }
    }
    
    private void refreshPluginProperties() {
        try {
            Map<String, Properties> newProperties = new HashMap<>(1);
            Properties properties = PropertiesUtil.getPropertiesWithPrefix(EnvUtil.getEnvironment(), PREFIX);
            if (properties != null) {
                for (String each : properties.stringPropertyNames()) {
                    int typeIndex = each.indexOf('.');
                    String type = each.substring(0, typeIndex);
                    String subKey = each.substring(typeIndex + 1);
                    newProperties.computeIfAbsent(type, key -> new Properties())
                            .setProperty(subKey, properties.getProperty(each));
                }
            }
            authPluginProperties = newProperties;
        } catch (Exception e) {
            LOGGER.warn("Refresh plugin properties failed ", e);
        }
    }
    
    public boolean isHasGlobalAdminRole() {
        return hasGlobalAdminRole;
    }
    
    public void setHasGlobalAdminRole(boolean hasGlobalAdminRole) {
        this.hasGlobalAdminRole = hasGlobalAdminRole;
    }
    
    public String getNacosAuthSystemType() {
        return nacosAuthSystemType;
    }
    
    public String getServerIdentityKey() {
        return serverIdentityKey;
    }
    
    public String getServerIdentityValue() {
        return serverIdentityValue;
    }
    
    public boolean isEnableUserAgentAuthWhite() {
        return enableUserAgentAuthWhite;
    }
    
    /**
     * auth function is open.
     *
     * @return auth function is open
     */
    public boolean isAuthEnabled() {
        return authEnabled;
    }
    
    /**
     * Whether permission information can be cached.
     *
     * @return bool
     */
    public boolean isCachingEnabled() {
        if (Objects.nonNull(AuthConfigs.cachingEnabled)) {
            return cachingEnabled;
        }
        return ConvertUtils.toBoolean(EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_CACHING_ENABLED, "true"));
    }
    
    public Properties getAuthPluginProperties(String authType) {
        if (!authPluginProperties.containsKey(authType)) {
            LOGGER.warn("Can't find properties for type {}, will use empty properties", authType);
            return new Properties();
        }
        return authPluginProperties.get(authType);
    }
    
    @JustForTest
    public static void setCachingEnabled(boolean cachingEnabled) {
        AuthConfigs.cachingEnabled = cachingEnabled;
    }
    
    @Override
    public void onEvent(ServerConfigChangeEvent event) {
        try {
            authEnabled = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, Boolean.class, false);
            cachingEnabled = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_CACHING_ENABLED, Boolean.class, true);
            serverIdentityKey = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "");
            serverIdentityValue = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "");
            enableUserAgentAuthWhite = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLE_USER_AGENT_AUTH_WHITE,
                    Boolean.class, false);
            nacosAuthSystemType = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "");
            refreshPluginProperties();
            ModuleStateHolder.getInstance().getModuleState(AuthModuleStateBuilder.AUTH_MODULE)
                    .ifPresent(moduleState -> {
                        ModuleState temp = new AuthModuleStateBuilder().build();
                        moduleState.getStates().putAll(temp.getStates());
                    });
        } catch (Exception e) {
            LOGGER.warn("Upgrade auth config from env failed, use old value", e);
        }
    }
    
    @Override
    public Class<? extends Event> subscribeType() {
        return ServerConfigChangeEvent.class;
    }
}
