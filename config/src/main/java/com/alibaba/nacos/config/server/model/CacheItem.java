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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * Use for beta.
     */
    public volatile boolean isBeta = false;
    
    public volatile List<String> ips4Beta;
    
    ConfigCache configCacheBeta = null;
    
    /**
     * Use for batch.
     */
    public volatile boolean isBatch = false;
    
    public volatile int delimiter = 0;
    
    ConfigCache configCacheBatch = null;
    
    /**
     * Use for tag.
     */
    private volatile Map<String, ConfigCache> configCacheTags = null;
    
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
    
    public boolean isBeta() {
        return isBeta;
    }
    
    public void setBeta(boolean isBeta) {
        this.isBeta = isBeta;
    }
    
    /**
     * remove beta.
     */
    public void removeBeta() {
        this.isBeta = false;
        this.ips4Beta = null;
        configCacheBeta = null;
    }
    
    public List<String> getIps4Beta() {
        return ips4Beta;
    }
    
    public void setIps4Beta(List<String> ips4Beta) {
        this.ips4Beta = ips4Beta;
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
     * init beta cache if empty.
     */
    public void initBetaCacheIfEmpty() {
        if (this.configCacheBeta == null) {
            this.configCacheBeta = new ConfigCache();
        }
        if (this.ips4Beta == null) {
            this.ips4Beta = new ArrayList<>();
        }
    }
    
    /**
     * get config cache beta.
     *
     * @return
     */
    public ConfigCache getConfigCacheBeta() {
        return configCacheBeta;
    }
    
    /**
     * init batch cache if empty.
     */
    public void initBatchCacheIfEmpty() {
        if (this.configCacheBatch == null) {
            this.configCacheBatch = new ConfigCache();
        }
    }
    
    public ConfigCache getConfigCacheBatch() {
        return configCacheBatch;
    }
    
    /**
     * remove batch.
     */
    public void removeBatch() {
        this.configCacheBatch = null;
        this.isBatch = false;
    }
    
    /**
     * init config tags if empty.
     */
    public void initConfigTagsIfEmpty() {
        if (this.getConfigCacheTags() == null) {
            this.configCacheTags = new HashMap<>(16);
        }
    }
    
    /**
     * init config tag if empty.
     *
     * @param tag tag.
     */
    public void initConfigTagsIfEmpty(String tag) {
        initConfigTagsIfEmpty();
        if (!this.configCacheTags.containsKey(tag)) {
            this.configCacheTags.put(tag, new ConfigCache());
        }
    }
    
    public void clearConfigTags() {
        this.configCacheTags = null;
    }
    
    public Map<String, ConfigCache> getConfigCacheTags() {
        return configCacheTags;
    }
    
    public boolean isBatch() {
        return isBatch;
    }
    
    public void setBatch(boolean batch) {
        isBatch = batch;
    }
    
    public int getDelimiter() {
        return delimiter;
    }
    
    public void setDelimiter(int delimiter) {
        this.delimiter = delimiter;
    }
    
    public long getTagLastModified(String tag) {
        if (configCacheTags == null || !configCacheTags.containsKey(tag)) {
            return -1L;
        }
        return configCacheTags.get(tag).getLastModifiedTs();
    }
    
    public String getTagEncryptedDataKey(String tag) {
        if (configCacheTags == null || !configCacheTags.containsKey(tag)) {
            return null;
        }
        return configCacheTags.get(tag).getEncryptedDataKey();
    }
    
    public String getTagMd5(String tag, String encode) {
        if (configCacheTags == null || !configCacheTags.containsKey(tag)) {
            return null;
        }
        return configCacheTags.get(tag).getMd5(encode);
    }
    
}
