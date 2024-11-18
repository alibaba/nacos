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

package com.alibaba.nacos.auth.config;

import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.sys.module.ModuleState;
import com.alibaba.nacos.sys.module.ModuleStateBuilder;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

import java.util.Optional;

/**
 * Module state builder for auth module.
 *
 * @author xiweng.yy
 */
public class AuthModuleStateBuilder implements ModuleStateBuilder {
    
    public static final String AUTH_MODULE = "auth";
    
    public static final String AUTH_ENABLED = "auth_enabled";
    
    public static final String LOGIN_PAGE_ENABLED = "login_page_enabled";
    
    public static final String AUTH_SYSTEM_TYPE = "auth_system_type";
    
    public static final String AUTH_ADMIN_REQUEST = "auth_admin_request";
    
    private boolean cacheable;
    
    @Override
    public ModuleState build() {
        ModuleState result = new ModuleState(AUTH_MODULE);
        AuthConfigs authConfigs = ApplicationUtils.getBean(AuthConfigs.class);
        result.newState(AUTH_ENABLED, authConfigs.isAuthEnabled());
        result.newState(LOGIN_PAGE_ENABLED, isLoginPageEnabled(authConfigs));
        result.newState(AUTH_SYSTEM_TYPE, authConfigs.getNacosAuthSystemType());
        result.newState(AUTH_ADMIN_REQUEST, isAdminRequest(authConfigs));
        return result;
    }
    
    @Override
    public boolean isCacheable() {
        return cacheable;
    }
    
    private Boolean isLoginPageEnabled(AuthConfigs authConfigs) {
        Optional<AuthPluginService> authPluginService = AuthPluginManager.getInstance()
                .findAuthServiceSpiImpl(authConfigs.getNacosAuthSystemType());
        return authPluginService.map(AuthPluginService::isLoginEnabled).orElse(false);
    }
    
    private Boolean isAdminRequest(AuthConfigs authConfigs) {
        Optional<AuthPluginService> authPluginService = AuthPluginManager.getInstance()
                .findAuthServiceSpiImpl(authConfigs.getNacosAuthSystemType());
        boolean isAdminRequest = authPluginService.map(AuthPluginService::isAdminRequest).orElse(true);
        if (!isAdminRequest) {
            cacheable = true;
        }
        return isAdminRequest;
    }
}
