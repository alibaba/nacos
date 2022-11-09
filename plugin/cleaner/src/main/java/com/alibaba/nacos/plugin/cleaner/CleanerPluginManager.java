/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.cleaner;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.plugin.cleaner.spi.CleanerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * config history manager.
 *
 * @author vivid
 */
public class CleanerPluginManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CleanerService.class);
    
    private static final Map<String, CleanerService> CLEANER_SERVICE_MAP = new ConcurrentHashMap<>();
    
    private static final CleanerPluginManager INSTANCE = new CleanerPluginManager();
    
    private CleanerPluginManager() {
        init();
    }
    
    /**
     * manager initiate.
     */
    public void init() {
        Collection<CleanerService> cleanerServices = NacosServiceLoader.load(CleanerService.class);
        for (CleanerService cleanerService : cleanerServices) {
            CLEANER_SERVICE_MAP.put(cleanerService.name(), cleanerService);
            LOGGER.info("[cleanerPluginManager] Load cleanerServices({}) name({}) successfully.",
                    cleanerService.getClass(), cleanerService.name());
        }
    }
    
    public static CleanerPluginManager getInstance() {
        return INSTANCE;
    }
    
    public Optional<CleanerService> findImpl(String name) {
        return Optional.ofNullable(CLEANER_SERVICE_MAP.get(name));
    }
    
}
