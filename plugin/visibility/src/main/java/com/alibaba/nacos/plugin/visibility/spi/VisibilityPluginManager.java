/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.visibility.spi;

import com.alibaba.nacos.api.plugin.PluginStateCheckerHolder;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Manager for loading and accessing {@link VisibilityService} implementations.
 *
 * @author xiweng.yy
 */
public class VisibilityPluginManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(VisibilityPluginManager.class);
    
    private static final VisibilityPluginManager INSTANCE = new VisibilityPluginManager();
    
    private final Map<String, VisibilityService> visibilityServiceMap = new HashMap<>();
    
    private VisibilityPluginManager() {
        initVisibilityServices();
    }
    
    private void initVisibilityServices() {
        Collection<VisibilityService> services = NacosServiceLoader.load(VisibilityService.class);
        for (VisibilityService each : services) {
            if (StringUtils.isEmpty(each.getVisibilityServiceName())) {
                LOGGER.warn(
                        "[VisibilityPluginManager] Load VisibilityService({}) VisibilityServiceName(null/empty) fail."
                                + " Please add VisibilityServiceName to resolve.", each.getClass());
                continue;
            }
            visibilityServiceMap.put(each.getVisibilityServiceName(), each);
            LOGGER.info("[VisibilityPluginManager] Load VisibilityService({}) VisibilityServiceName({}) successfully.",
                    each.getClass(), each.getVisibilityServiceName());
        }
    }
    
    public static VisibilityPluginManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Find a visibility service by name.
     *
     * @param serviceName service name
     * @return optional visibility service
     */
    public Optional<VisibilityService> findVisibilityService(String serviceName) {
        if (!PluginStateCheckerHolder.isPluginEnabled(PluginType.VISIBILITY.getType(), serviceName)) {
            LOGGER.debug("[VisibilityPluginManager] Plugin VISIBILITY:{} is disabled", serviceName);
            return Optional.empty();
        }
        return Optional.ofNullable(visibilityServiceMap.get(serviceName));
    }
    
    public Map<String, VisibilityService> getAllPlugins() {
        return Collections.unmodifiableMap(visibilityServiceMap);
    }
}
