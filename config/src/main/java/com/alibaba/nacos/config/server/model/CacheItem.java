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

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.utils.SimpleReadWriteLock;
import com.alibaba.nacos.core.utils.StringPool;

import java.util.List;
import java.util.Map;

/**
 * Cache item.
 *
 * @author Nacos
 */
public class CacheItem {
    
    public CacheItem(String groupKey) {
        this.groupKey = StringPool.get(groupKey);
    }
    
    public String getMd5() {
        return md5;
    }
    
    public void setMd5(String md5) {
        this.md5 = md5;
    }
    
    public long getLastModifiedTs() {
        return lastModifiedTs;
    }
    
    public void setLastModifiedTs(long lastModifiedTs) {
        this.lastModifiedTs = lastModifiedTs;
    }
    
    public boolean isBeta() {
        return isBeta;
    }
    
    public void setBeta(boolean isBeta) {
        this.isBeta = isBeta;
    }
    
    public String getMd54Beta() {
        return md54Beta;
    }
    
    public void setMd54Beta(String md54Beta) {
        this.md54Beta = md54Beta;
    }
    
    public List<String> getIps4Beta() {
        return ips4Beta;
    }
    
    public void setIps4Beta(List<String> ips4Beta) {
        this.ips4Beta = ips4Beta;
    }
    
    public long getLastModifiedTs4Beta() {
        return lastModifiedTs4Beta;
    }
    
    public void setLastModifiedTs4Beta(long lastModifiedTs4Beta) {
        this.lastModifiedTs4Beta = lastModifiedTs4Beta;
    }
    
    public SimpleReadWriteLock getRwLock() {
        return rwLock;
    }
    
    public void setRwLock(SimpleReadWriteLock rwLock) {
        this.rwLock = rwLock;
    }
    
    public String getGroupKey() {
        return groupKey;
    }
    
    public Map<String, String> getTagMd5() {
        return tagMd5;
    }
    
    public Map<String, Long> getTagLastModifiedTs() {
        return tagLastModifiedTs;
    }
    
    public void setTagMd5(Map<String, String> tagMd5) {
        this.tagMd5 = tagMd5;
    }
    
    public void setTagLastModifiedTs(Map<String, Long> tagLastModifiedTs) {
        this.tagLastModifiedTs = tagLastModifiedTs;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    final String groupKey;
    
    public volatile String md5 = Constants.NULL;
    
    public volatile long lastModifiedTs;
    
    /**
     * Use for beta.
     */
    public volatile boolean isBeta = false;
    
    public volatile String md54Beta = Constants.NULL;
    
    public volatile List<String> ips4Beta;
    
    public volatile long lastModifiedTs4Beta;
    
    public volatile Map<String, String> tagMd5;
    
    public volatile Map<String, Long> tagLastModifiedTs;
    
    public SimpleReadWriteLock rwLock = new SimpleReadWriteLock();
    
    public String type;
}
