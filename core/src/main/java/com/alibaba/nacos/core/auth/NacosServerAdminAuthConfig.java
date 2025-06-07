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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nacos Server auth configurations.
 *
 * @author xiweng.yy
 */
public class NacosServerAdminAuthConfig extends AbstractDynamicConfig implements NacosAuthConfig {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosServerAdminAuthConfig.class);
    
    public static final String NACOS_SERVER_ADMIN_AUTH_SCOPE = ApiType.ADMIN_API.name();
    
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
    
    public NacosServerAdminAuthConfig() {
        super("NacosServerAdminAuth");
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
    
    @Override
    public String getAuthScope() {
        return NACOS_SERVER_ADMIN_AUTH_SCOPE;
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
    
    @Override
    protected void getConfigFromEnv() {
        try {
            authEnabled = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_ADMIN_ENABLED, Boolean.class, true);
            nacosAuthSystemType = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SYSTEM_TYPE, "");
            serverIdentityKey = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_KEY, "");
            serverIdentityValue = EnvUtil.getProperty(Constants.Auth.NACOS_CORE_AUTH_SERVER_IDENTITY_VALUE, "");
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
        return "NacosServerAdminAuthConfig{" + "authEnabled=" + authEnabled + ", nacosAuthSystemType='"
                + nacosAuthSystemType + '\'' + ", serverIdentityKey='" + serverIdentityKey + '\''
                + ", serverIdentityValue='" + serverIdentityValue + '\'' + '}';
    }
}
