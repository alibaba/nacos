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

package com.alibaba.nacos.config.server.model;

import com.alibaba.nacos.config.server.utils.SimpleReadWriteLock;
import com.alibaba.nacos.core.utils.StringPool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cache item.
 *
 * @author Nacos
 */
public class CacheItem {

    final String groupKey;

    public String type;

    ConfigCache configCache = new ConfigCache();

    /**
     * Use for gray.
     */
    private volatile Map<String, ConfigCacheGray> configCacheGray = null;

    List<ConfigCacheGray> sortedConfigCacheGrayList = null;

    private final SimpleReadWriteLock rwLock = new SimpleReadWriteLock();

    public CacheItem(String groupKey, String encryptedDataKey) {
        this.groupKey = StringPool.get(groupKey);
        this.getConfigCache().setEncryptedDataKey(encryptedDataKey);
    }

    public CacheItem(String groupKey) {
        this.groupKey = StringPool.get(groupKey);
    }

    public ConfigCache getConfigCache() {
        return configCache;
    }

    public SimpleReadWriteLock getRwLock() {
        return rwLock;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getGroupKey() {
        return groupKey;
    }

    /**
     * init config gray if empty.
     */
    public void initConfigGrayIfEmpty() {
        if (this.configCacheGray == null) {
            this.configCacheGray = new HashMap<>(4);
        }
    }

    /**
     * init config gray if empty.
     *
     * @param grayName gray name.
     */
    public void initConfigGrayIfEmpty(String grayName) {
        initConfigGrayIfEmpty();
        if (!this.configCacheGray.containsKey(grayName)) {
            this.configCacheGray.put(grayName, new ConfigCacheGray(grayName));
        }
    }

    public List<ConfigCacheGray> getSortConfigGrays() {
        return sortedConfigCacheGrayList;
    }

    /**
     * sort config gray.
     */
    public void sortConfigGray() {
        if (configCacheGray == null || configCacheGray.isEmpty()) {
            sortedConfigCacheGrayList = null;
            return;
        }

        sortedConfigCacheGrayList = configCacheGray.values().stream().sorted((o1, o2) -> {
            if (o1.getPriority() != o2.getPriority()) {
                return Integer.compare(o1.getPriority(), o2.getPriority()) * -1;
            } else {
                return o1.getGrayName().compareTo(o2.getGrayName());
            }

        }).collect(Collectors.toList());
    }

    public Map<String, ConfigCacheGray> getConfigCacheGray() {
        return configCacheGray;
    }

    public void clearConfigGrays() {
        this.configCacheGray = null;
        this.sortedConfigCacheGrayList = null;
    }

}
