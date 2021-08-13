/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.auth;

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
 */
public class AuthPluginManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthPluginManager.class);
    
    private static final AuthPluginManager INSTANCE = new AuthPluginManager();
    
    /**
     * The relationship of context type and {@link AuthService}.
     */
    private Map<String, AuthService> authServiceMap = new HashMap<>();
    
    public AuthPluginManager() {
        initAuthServices();
    }
    
    public static AuthPluginManager getInstance() {
        return INSTANCE;
    }
    
    private void initAuthServices() {
        Collection<AuthService> authServices = NacosServiceLoader.load(AuthService.class);
        for (AuthService authService : authServices) {
            if (StringUtils.isEmpty(authService.getAuthServiceName())) {
                LOGGER.warn(
                        "[AuthPluginManager] Load AuthService({}) AuthServiceName(null/empty) fail. Please Add AuthServiceName to resolve.",
                        authService.getClass());
                continue;
            }
            authServiceMap.put(authService.getAuthServiceName(), authService);
            LOGGER.info("[AuthPluginManager] Load AuthService({}) AuthServiceName({}) successfully.",
                    authService.getClass(), authService.getAuthServiceName());
        }
    }
    
    /**
     * get AuthService instance which AuthService.getType() is type.
     *
     * @param authServiceName AuthServiceName, mark a AuthService instance.
     * @return AuthService instance.
     */
    public Optional<AuthService> findAuthServiceSpiImpl(String authServiceName) {
        return Optional.ofNullable(authServiceMap.get(authServiceName));
    }
    
}
