/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.spi.server;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Load Plugins.
 *
 * @author Wuyfee
 * @author xiweng.yy
 */
public class AuthPluginManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthPluginManager.class);
    
    private static final AuthPluginManager INSTANCE = new AuthPluginManager();
    
    /**
     * The relationship of context type and {@link AuthPluginService}.
     */
    private final Map<String, AuthPluginService> authServiceMap = new HashMap<>();
    
    private AuthPluginManager() {
        initAuthServices();
    }
    
    private void initAuthServices() {
        Collection<AuthPluginService> authPluginServices = NacosServiceLoader.load(AuthPluginService.class);
        for (AuthPluginService each : authPluginServices) {
            if (StringUtils.isEmpty(each.getAuthServiceName())) {
                LOGGER.warn(
                        "[AuthPluginManager] Load AuthPluginService({}) AuthServiceName(null/empty) fail. Please Add AuthServiceName to resolve.",
                        each.getClass());
                continue;
            }
            authServiceMap.put(each.getAuthServiceName(), each);
            LOGGER.info("[AuthPluginManager] Load AuthPluginService({}) AuthServiceName({}) successfully.",
                    each.getClass(), each.getAuthServiceName());
        }
    }
    
    public static AuthPluginManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * get AuthPluginService instance which AuthPluginService.getType() is type.
     *
     * @param authServiceName AuthServiceName, mark a AuthPluginService instance.
     * @return AuthPluginService instance.
     */
    public Optional<AuthPluginService> findAuthServiceSpiImpl(String authServiceName) {
        return Optional.ofNullable(authServiceMap.get(authServiceName));
    }
    
}
