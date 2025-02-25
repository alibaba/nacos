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

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.auth.config.AuthErrorCode;
import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.config.AbstractDynamicConfig;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.alibaba.nacos.sys.utils.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Nacos Server auth configurations.
 *
 * @author xiweng.yy
 */
public class NacosServerAuthConfig extends AbstractDynamicConfig implements NacosAuthConfig {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosServerAuthConfig.class);
    
    private static final String PREFIX = "nacos.core.auth.plugin";
    
    public static final String NACOS_SERVER_AUTH_SCOPE = ApiType.OPEN_API.name();
    
    /**
     * Whether server auth enabled.
     */
    private boolean authEnabled;
    
    /**
     * Which auth system is in use.
     */
    private String nacosAuthSystemType;
    
    private String serverIdentityKey;
    
    private String serverIdentityValue;
    
    private Map<String, Properties> authPluginProperties = new HashMap<>();
    
    public NacosServerAuthConfig() {
        super("NacosServerAuth");
        resetConfig();
        validate();
    }
    
    /**
     * Validate auth config.
     */
    private void validate() {
        if (!authEnabled) {
            return;
        }
        if (StringUtils.isEmpty(nacosAuthSystemType)) {
            throw new NacosRuntimeException(AuthErrorCode.INVALID_TYPE.getCode(), AuthErrorCode.INVALID_TYPE.getMsg());
        }
        if (StringUtils.isEmpty(serverIdentityKey) || StringUtils.isEmpty(serverIdentityValue)) {
            throw new NacosRuntimeException(AuthErrorCode.EMPTY_IDENTITY.getCode(),
                    AuthErrorCode.EMPTY_IDENTITY.getMsg());
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
    
    @Override
    public String getAuthScope() {
        return NACOS_SERVER_AUTH_SCOPE;
    }
    
    /**
     * server auth function is open.
     *
     * @return server auth function is open
     */
    @Override
    public boolean isAuthEnabled() {
        return authEnabled;
    }
    
    @Override
    public String getNacosAuthSystemType() {
        return nacosAuthSystemType;
    }
    
    @Override
    public boolean isSupportServerIdentity() {
        return true;
    }
    
    @Override
    public String getServerIdentityKey() {
        return serverIdentityKey;
    }
    
    @Override
    public String getServerIdentityValue() {
        return serverIdentityValue;
    }
    
    public Properties getAuthPluginProperties(String authType) {
        if (!authPluginProperties.containsKey(authType)) {
            LOGGER.warn("Can't find properties for type {}, will use empty properties", authType);
            return new Properties();
        }
        return authPluginProperties.get(authType);
    }
    
    @Override
    protected void getConfigFromEnv() {
        try {
            authEnabled = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_ENABLED, Boolean.class, false);
            nacosAuthSystemType = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "");
            serverIdentityKey = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "");
            serverIdentityValue = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "");
            refreshPluginProperties();
        } catch (Exception e) {
            LOGGER.warn("Upgrade auth config from env failed, use old value", e);
        }
    }
    
    @Override
    protected String printConfig() {
        return toString();
    }
    
    @Override
    public String toString() {
        return "NacosServerAuthConfig{" + "authEnabled=" + authEnabled + ", nacosAuthSystemType='" + nacosAuthSystemType
                + '\'' + ", serverIdentityKey='" + serverIdentityKey + '\'' + ", serverIdentityValue='"
                + serverIdentityValue + '\'' + ", authPluginProperties=" + authPluginProperties + '}';
    }
}
