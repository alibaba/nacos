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

import com.alibaba.nacos.auth.common.GrantTypes;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
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
    private Map<GrantTypes, AuthService> authServiceMap = new HashMap<>();
    
    public AuthPluginManager() {
        initAuthServices();
    }
    
    public static AuthPluginManager getInstance() {
        return INSTANCE;
    }
    
    private void initAuthServices() {
        Collection<AuthService> authServices = NacosServiceLoader.load(AuthService.class);
        for (AuthService authService : authServices) {
            if (authServiceMap.containsKey(authService.getType())) {
                LOGGER.warn("[AuthPluginManager] init AuthService, AuthService type {} has value, ignore it.",
                        authService.getType());
                continue;
            }
            authServiceMap.put(authService.getType(), authService);
            LOGGER.info("[AuthPluginManager] Load AuthService({}) type({}) successfully.", authService.getClass(),
                    authService.getType());
        }
    }
    
    /**
     * get AuthService instance which AuthService.getType() is type.
     * @param type AuthService.
     * @return AuthService instance.
     */
    public Optional<AuthService> findAuthServiceSpiImpl(GrantTypes type) {
        for (Map.Entry<GrantTypes, AuthService> entry : authServiceMap.entrySet()) {
            if (authServiceMap.containsKey(entry.getKey())) {
                return Optional.of(authServiceMap.get(type));
            }
        }
        return Optional.empty();
    }
    
}
