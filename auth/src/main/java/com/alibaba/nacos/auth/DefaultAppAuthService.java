/*
 *
 *  * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.alibaba.nacos.auth;

import com.alibaba.nacos.auth.config.AuthConfigs;
import com.alibaba.nacos.auth.util.Loggers;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.spi.server.AppAuthPluginManager;
import com.alibaba.nacos.plugin.auth.spi.server.AppAuthPluginService;
import com.google.common.collect.Maps;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Default app auth service.
 */
public class DefaultAppAuthService implements AppAuthService {
    
    protected AuthConfigs authConfigs;
    
    public DefaultAppAuthService(AuthConfigs authConfigs) {
        this.authConfigs = authConfigs;
    }
    
    @Override
    public Map<String, Set<String>> initAppPermissions(IdentityContext identityContext) {
        try {
            Optional<AppAuthPluginService> appAuthPluginService = AppAuthPluginManager.getInstance()
                    .findAppAuthServiceSpiImpl(authConfigs.getNacosAppAuthSystemType());
            if (appAuthPluginService.isPresent()) {
                return appAuthPluginService.get().getAppPermissions(identityContext);
            }
        } catch (Exception e) {
            Loggers.AUTH.error("init user app permission failed", e);
        }
        return Maps.newHashMap();
    }
}
