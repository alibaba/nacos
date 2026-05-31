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

package com.alibaba.nacos.api.config;

import java.io.Serializable;

/**
 * Config query result containing content and metadata.
 *
 * <p>This class provides access to configuration content along with
 * its metadata like MD5 hash for CAS operations.</p>
 *
 * @author nacos
 * @since 3.0
 */
public class ConfigQueryResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Configuration content.
     */
    private String content;
    
    /**
     * MD5 hash of the content.
     */
    private String md5;
    
    /**
     * Configuration type (json, yaml, properties, etc.).
     */
    private String configType;
    
    /**
     * Encrypted data key (if encryption is enabled).
     */
    private String encryptedDataKey;
    
    public ConfigQueryResult() {
    }
    
    public ConfigQueryResult(String content, String md5) {
        this.content = content;
        this.md5 = md5;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getMd5() {
        return md5;
    }
    
    public void setMd5(String md5) {
        this.md5 = md5;
    }
    
    public String getConfigType() {
        return configType;
    }
    
    public void setConfigType(String configType) {
        this.configType = configType;
    }
    
    public String getEncryptedDataKey() {
        return encryptedDataKey;
    }
    
    public void setEncryptedDataKey(String encryptedDataKey) {
        this.encryptedDataKey = encryptedDataKey;
    }
    
    @Override
    public String toString() {
        return "ConfigQueryResult{" + "content='"
            + (content != null ? content.substring(0, Math.min(50, content.length())) + "..."
                : "null")
            + '\'' + ", md5='" + md5 + '\'' + ", configType='" + configType + '\'' + '}';
    }
}
