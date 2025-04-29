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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.core.auth;

import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.sys.module.AbstractServerModuleStateBuilder;
import com.alibaba.nacos.sys.module.ModuleState;

import java.util.Optional;

/**
 * Module state builder for auth module.
 *
 * @author xiweng.yy
 */
public class AuthModuleStateBuilder extends AbstractServerModuleStateBuilder {
    
    public static final String AUTH_MODULE = "auth";
    
    public static final String AUTH_ENABLED = "auth_enabled";
    
    public static final String AUTH_SYSTEM_TYPE = "auth_system_type";
    
    public static final String AUTH_ADMIN_REQUEST = "auth_admin_request";
    
    private boolean cacheable;
    
    @Override
    public ModuleState build() {
        ModuleState result = new ModuleState(AUTH_MODULE);
        boolean authEnabled = NacosAuthConfigHolder.getInstance()
                .isAnyAuthEnabled(NacosServerAuthConfig.NACOS_SERVER_AUTH_SCOPE,
                        NacosServerAdminAuthConfig.NACOS_SERVER_ADMIN_AUTH_SCOPE);
        String authSystemType = NacosAuthConfigHolder.getInstance().getNacosAuthSystemType();
        result.newState(AUTH_ENABLED, authEnabled);
        result.newState(AUTH_SYSTEM_TYPE, authSystemType);
        result.newState(AUTH_ADMIN_REQUEST, isAdminRequest(authSystemType));
        return result;
    }
    
    @Override
    public boolean isCacheable() {
        return cacheable;
    }
    
    private Boolean isAdminRequest(String authConfigs) {
        Optional<AuthPluginService> authPluginService = AuthPluginManager.getInstance()
                .findAuthServiceSpiImpl(authConfigs);
        boolean isAdminRequest = authPluginService.map(AuthPluginService::isAdminRequest).orElse(true);
        if (!isAdminRequest) {
            cacheable = true;
        }
        return isAdminRequest;
    }
}
