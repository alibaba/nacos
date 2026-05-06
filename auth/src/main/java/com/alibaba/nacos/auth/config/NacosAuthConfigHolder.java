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

package com.alibaba.nacos.auth.config;

import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Nacos SPI Holder for {@link NacosAuthConfig}.
 *
 * @author xiweng.yy
 */
public class NacosAuthConfigHolder {
    
    private static final NacosAuthConfigHolder INSTANCE = new NacosAuthConfigHolder();
    
    private final Map<String, NacosAuthConfig> nacosAuthConfigMap;
    
    NacosAuthConfigHolder() {
        this.nacosAuthConfigMap = new HashMap<>();
        for (NacosAuthConfig each : NacosServiceLoader.load(NacosAuthConfig.class)) {
            nacosAuthConfigMap.put(each.getAuthScope(), each);
        }
    }
    
    public static NacosAuthConfigHolder getInstance() {
        return INSTANCE;
    }
    
    public NacosAuthConfig getNacosAuthConfigByScope(String scope) {
        return nacosAuthConfigMap.get(scope);
    }
    
    public Collection<NacosAuthConfig> getAllNacosAuthConfig() {
        return nacosAuthConfigMap.values();
    }
    
    public boolean isAnyAuthEnabled() {
        return nacosAuthConfigMap.values().stream().anyMatch(NacosAuthConfig::isAuthEnabled);
    }
    
    /**
     * Is any auth config by input scope is enabled.
     *
     * @param scope the scopes to check whether enabled
     * @return {@code true} if any input scope auth is enabled, {@code false} all input scope auth is disabled.
     */
    public boolean isAnyAuthEnabled(String... scope) {
        for (String each : scope) {
            NacosAuthConfig config = nacosAuthConfigMap.get(each);
            if (null != config && config.isAuthEnabled()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get nacos auth system type from the first {@link NacosAuthConfig}.
     *
     * <p>
     *     It should be same with for all {@link NacosAuthConfig}s in one nacos server.
     * </p>
     * @return nacos auth system type
     */
    public String getNacosAuthSystemType() {
        return nacosAuthConfigMap.values().stream().findFirst().map(NacosAuthConfig::getNacosAuthSystemType)
                .orElse(null);
    }
}
