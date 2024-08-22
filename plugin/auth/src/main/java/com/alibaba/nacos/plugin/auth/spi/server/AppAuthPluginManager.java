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
 * Load App Auth Plugins.
 */
public class AppAuthPluginManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AppAuthPluginManager.class);
    
    private static final AppAuthPluginManager INSTANCE = new AppAuthPluginManager();
    
    /**
     * The relationship of context type and {@link AuthPluginService}.
     */
    private final Map<String, AppAuthPluginService> authServiceMap = new HashMap<>();
    
    private AppAuthPluginManager() {
        initAuthServices();
    }
    
    private void initAuthServices() {
        Collection<AppAuthPluginService> appAuthPluginServices = NacosServiceLoader.load(AppAuthPluginService.class);
        for (AppAuthPluginService each : appAuthPluginServices) {
            if (StringUtils.isEmpty(each.getAuthServiceName())) {
                LOGGER.warn(
                        "[AppAuthPluginManager] Load AppAuthPluginService({}) AuthServiceName(null/empty) fail. "
                                + "Please Add AuthServiceName to resolve.", each.getClass());
                continue;
            }
            authServiceMap.put(each.getAuthServiceName(), each);
            LOGGER.info("[AppAuthPluginManager] Load AppAuthPluginService({}) AuthServiceName({}) successfully.",
                    each.getClass(), each.getAuthServiceName());
        }
    }
    
    public static AppAuthPluginManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * get AppAuthPluginService instance which AppAuthPluginService.getType() is type.
     *
     * @param authServiceName AuthServiceName, mark a AppAuthPluginService instance.
     * @return AppAuthPluginService instance.
     */
    public Optional<AppAuthPluginService> findAppAuthServiceSpiImpl(String authServiceName) {
        return Optional.ofNullable(authServiceMap.get(authServiceName));
    }
}
