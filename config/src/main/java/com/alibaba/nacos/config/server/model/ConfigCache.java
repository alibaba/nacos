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

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * config cache .
 *
 * @author shiyiyue1102
 */
public class ConfigCache implements Serializable {
    
    volatile String md5Gbk = Constants.NULL;
    
    volatile String md5Utf8 = Constants.NULL;
    
    volatile String encryptedDataKey;
    
    volatile long lastModifiedTs;
    
    /**
     * clear cache.
     */
    public void clear() {
        this.md5Gbk = Constants.NULL;
        this.md5Utf8 = Constants.NULL;
        this.encryptedDataKey = null;
        this.lastModifiedTs = -1L;
    }
    
    public ConfigCache() {
    }
    
    public String getMd5(String encode) {
        if (UTF_8.name().equalsIgnoreCase(encode)) {
            return md5Utf8;
        } else {
            return md5Gbk;
        }
    }
    
    public String getEncryptedDataKey() {
        return encryptedDataKey;
    }
    
    public void setEncryptedDataKey(String encryptedDataKey) {
        this.encryptedDataKey = encryptedDataKey;
    }
    
    public ConfigCache(String md5Gbk, String md5Utf8, long lastModifiedTs) {
        this.md5Gbk = StringPool.get(md5Gbk);
        this.md5Utf8 = StringPool.get(md5Utf8);
        this.lastModifiedTs = lastModifiedTs;
    }
    
    public String getMd5Gbk() {
        return md5Gbk;
    }
    
    public void setMd5Gbk(String md5Gbk) {
        this.md5Gbk = StringPool.get(md5Gbk);
    }
    
    public String getMd5Utf8() {
        return md5Utf8;
    }
    
    public void setMd5Utf8(String md5Utf8) {
        this.md5Utf8 = StringPool.get(md5Utf8);
    }
    
    public long getLastModifiedTs() {
        return lastModifiedTs;
    }
    
    public void setLastModifiedTs(long lastModifiedTs) {
        this.lastModifiedTs = lastModifiedTs;
    }
}