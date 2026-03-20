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

package com.alibaba.nacos.plugin.datafilter.spi;

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
 * Manager for loading and accessing {@link DataFilterService} SPI implementations.
 *
 * @author xiweng.yy
 */
public class DataFilterPluginManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(DataFilterPluginManager.class);
    
    private static final DataFilterPluginManager INSTANCE = new DataFilterPluginManager();
    
    private final Map<String, DataFilterService> filterServiceMap = new HashMap<>();
    
    private DataFilterPluginManager() {
        initFilterServices();
    }
    
    private void initFilterServices() {
        Collection<DataFilterService> services = NacosServiceLoader.load(DataFilterService.class);
        for (DataFilterService each : services) {
            if (StringUtils.isEmpty(each.getFilterServiceName())) {
                LOGGER.warn(
                        "[DataFilterPluginManager] Load DataFilterService({}) FilterServiceName(null/empty) fail. "
                                + "Please add FilterServiceName to resolve.", each.getClass());
                continue;
            }
            filterServiceMap.put(each.getFilterServiceName(), each);
            LOGGER.info("[DataFilterPluginManager] Load DataFilterService({}) FilterServiceName({}) successfully.",
                    each.getClass(), each.getFilterServiceName());
        }
    }
    
    public static DataFilterPluginManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Find a DataFilterService by name.
     *
     * @param filterServiceName name of the data filter service
     * @return optional DataFilterService instance
     */
    public Optional<DataFilterService> findFilterService(String filterServiceName) {
        if (!PluginStateCheckerHolder.isPluginEnabled(PluginType.DATA_FILTER.getType(), filterServiceName)) {
            LOGGER.debug("[DataFilterPluginManager] Plugin DATA_FILTER:{} is disabled", filterServiceName);
            return Optional.empty();
        }
        return Optional.ofNullable(filterServiceMap.get(filterServiceName));
    }
    
    /**
     * Get all registered data filter plugins.
     *
     * @return unmodifiable map of filter service name to DataFilterService
     */
    public Map<String, DataFilterService> getAllPlugins() {
        return Collections.unmodifiableMap(filterServiceMap);
    }
}
