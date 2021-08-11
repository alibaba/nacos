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

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Load Plugins.
 *
 * @author Wuyfee
 */
public class AuthPluginManager {
    
    private static final AuthPluginManager INSTANCE = new AuthPluginManager();
    
    private final Set<AuthService> authServices;
    
    public AuthPluginManager() {
        authServices = new HashSet<>(NacosServiceLoader.load(AuthService.class));
    }
    
    public static AuthPluginManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * get AuthService instance which AuthService.getType() is type.
     * @param type AuthService.
     * @return AuthService instance.
     */
    public Optional<AuthService> findAuthServiceSpiImpl(String type) {
        for (AuthService authService : authServices) {
            if (authService.getType().toString().equals(type)) {
                return Optional.of(authService);
            }
        }
        return Optional.empty();
    }
    
}
