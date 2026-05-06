/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.dump;

import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.util.HashMap;

/**
 * The type History config cleaner manager.
 *
 * @author Sunrisea
 */
public class HistoryConfigCleanerManager {
    
    private static HashMap<String, HistoryConfigCleaner> historyConfigCleanerMap = new HashMap<String, HistoryConfigCleaner>();
    
    static {
        NacosServiceLoader.load(HistoryConfigCleaner.class).forEach(historyConfigCleaner -> {
            historyConfigCleanerMap.put(historyConfigCleaner.getName(), historyConfigCleaner);
        });
        historyConfigCleanerMap.put("nacos", new DefaultHistoryConfigCleaner());
    }
    
    /**
     * Gets history config cleaner.
     *
     * @param name the name
     * @return the history config cleaner
     */
    public static HistoryConfigCleaner getHistoryConfigCleaner(String name) {
        return historyConfigCleanerMap.getOrDefault(name, historyConfigCleanerMap.get("nacos"));
    }
}
