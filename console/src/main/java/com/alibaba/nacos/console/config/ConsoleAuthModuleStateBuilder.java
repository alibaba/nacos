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
import com.alibaba.nacos.auth.config.NacosAuthConfigHolder;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AuthPluginService;
import com.alibaba.nacos.sys.module.AbstractConsoleModuleStateBuilder;
import com.alibaba.nacos.sys.module.ModuleState;

import java.util.Optional;

/**
 * Module state builder for auth module.
 *
 * @author xiweng.yy
 */
public class ConsoleAuthModuleStateBuilder extends AbstractConsoleModuleStateBuilder {
    
    public static final String AUTH_MODULE = "console_auth";
    
    public static final String AUTH_ENABLED = "auth_enabled";
    
    public static final String LOGIN_PAGE_ENABLED = "login_page_enabled";
    
    public static final String AUTH_SYSTEM_TYPE = "auth_system_type";
    
    private boolean cacheable;
    
    @Override
    public ModuleState build() {
        ModuleState result = new ModuleState(AUTH_MODULE);
        NacosAuthConfig authConfig = NacosAuthConfigHolder.getInstance()
                .getNacosAuthConfigByScope(NacosConsoleAuthConfig.NACOS_CONSOLE_AUTH_SCOPE);
        result.newState(AUTH_ENABLED, authConfig.isAuthEnabled());
        result.newState(LOGIN_PAGE_ENABLED, isLoginPageEnabled(authConfig));
        result.newState(AUTH_SYSTEM_TYPE, authConfig.getNacosAuthSystemType());
        return result;
    }
    
    @Override
    public boolean isCacheable() {
        return cacheable;
    }
    
    private Boolean isLoginPageEnabled(NacosAuthConfig authConfigs) {
        Optional<AuthPluginService> authPluginService = AuthPluginManager.getInstance()
                .findAuthServiceSpiImpl(authConfigs.getNacosAuthSystemType());
        return authPluginService.map(AuthPluginService::isLoginEnabled).orElse(false);
    }
    
}
