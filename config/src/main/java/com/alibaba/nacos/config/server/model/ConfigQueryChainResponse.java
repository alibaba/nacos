/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

import java.util.Objects;

/**
 * ConfigQueryChainResponse.
 * @author Nacos
 */
public class ConfigQueryChainResponse {
    
    private String content;
    
    private String contentType;
    
    private String encryptedDataKey;
    
    private String md5;
    
    private long lastModified;
    
    private ConfigCacheGray matchedGray;
    
    private ConfigQueryStatus status;
    
    public enum ConfigQueryStatus {
        BETA,
        TAG,
        TAG_NOT_FOUND,
        FORMAL,
        CONFIG_QUERY_CONFLICT,
        CONFIG_NOT_FOUND,
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getContentType() {
        return contentType;
    }
    
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    
    public String getEncryptedDataKey() {
        return encryptedDataKey;
    }
    
    public void setEncryptedDataKey(String encryptedDataKey) {
        this.encryptedDataKey = encryptedDataKey;
    }
    
    public String getMd5() {
        return md5;
    }
    
    public void setMd5(String md5) {
        this.md5 = md5;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
    
    public ConfigCacheGray getMatchedGray() {
        return matchedGray;
    }
    
    public void setMatchedGray(ConfigCacheGray matchedGray) {
        this.matchedGray = matchedGray;
    }
    
    public ConfigQueryStatus getStatus() {
        return status;
    }
    
    public void setStatus(ConfigQueryStatus status) {
        this.status = status;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ConfigQueryChainResponse that = (ConfigQueryChainResponse) o;
        return lastModified == that.lastModified
                && Objects.equals(content, that.content)
                && Objects.equals(contentType, that.contentType)
                && Objects.equals(encryptedDataKey, that.encryptedDataKey)
                && Objects.equals(md5, that.md5)
                && Objects.equals(matchedGray, that.matchedGray)
                && status == that.status;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(content, contentType, encryptedDataKey, md5, lastModified, matchedGray, status);
    }
}