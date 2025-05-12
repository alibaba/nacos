/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.auth.config.NacosAuthConfig;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.config.AbstractDynamicConfig;
import com.alibaba.nacos.plugin.auth.constant.ApiType;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.sys.env.EnvUtil;

/**
 * Nacos console auth configurations.
 *
 * @author xiweng.yy
 */
public class NacosConsoleAuthConfig extends AbstractDynamicConfig implements NacosAuthConfig {
    
    public static final String NACOS_CONSOLE_AUTH_SCOPE = ApiType.CONSOLE_API.name();
    
    /**
     * Whether console auth enabled.
     */
    private boolean authEnabled;
    
    /**
     * Which auth system is in use.
     */
    private String nacosAuthSystemType;
    
    private String serverIdentityKey;
    
    private String serverIdentityValue;
    
    public NacosConsoleAuthConfig() {
        super("NacosConsoleAuth");
        resetConfig();
    }
    
    @Override
    public String getAuthScope() {
        return NACOS_CONSOLE_AUTH_SCOPE;
    }
    
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
        return StringUtils.isNotBlank(serverIdentityKey) && StringUtils.isNotBlank(serverIdentityValue);
    }
    
    @Override
    public String getServerIdentityKey() {
        return serverIdentityKey;
    }
    
    @Override
    public String getServerIdentityValue() {
        return serverIdentityValue;
    }
    
    @Override
    protected void getConfigFromEnv() {
        authEnabled = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_CONSOLE_ENABLED, Boolean.class, true);
        nacosAuthSystemType = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "");
        serverIdentityKey = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "");
        serverIdentityValue = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "");
    }
    
    @Override
    protected String printConfig() {
        return toString();
    }
    
    @Override
    public String toString() {
        return "NacosConsoleAuthConfig{" + "authEnabled=" + authEnabled + ", nacosAuthSystemType='"
                + nacosAuthSystemType + '\'' + '}';
    }
}
