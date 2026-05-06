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
import com.alibaba.nacos.core.utils.StringPool;

import java.io.Serializable;

/**
 * config cache .
 *
 * @author shiyiyue1102
 */
public class ConfigCache implements Serializable {
    
    volatile String md5 = Constants.NULL;
    
    volatile String encryptedDataKey;
    
    volatile long lastModifiedTs;
    
    /**
     * clear cache.
     */
    public void clear() {
        this.md5 = Constants.NULL;
        this.encryptedDataKey = null;
        this.lastModifiedTs = -1L;
    }
    
    public ConfigCache() {
    }
    
    public ConfigCache(String md5, long lastModifiedTs) {
        this.md5 = StringPool.get(md5);
        this.lastModifiedTs = lastModifiedTs;
    }
    
    public String getMd5() {
        return md5;
    }
    
    public String getEncryptedDataKey() {
        return encryptedDataKey;
    }
    
    public void setEncryptedDataKey(String encryptedDataKey) {
        this.encryptedDataKey = encryptedDataKey;
    }
    
    public void setMd5(String md5) {
        this.md5 = StringPool.get(md5);
    }
    
    public long getLastModifiedTs() {
        return lastModifiedTs;
    }
    
    public void setLastModifiedTs(long lastModifiedTs) {
        this.lastModifiedTs = lastModifiedTs;
    }
}