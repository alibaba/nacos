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

package com.alibaba.nacos.api.config.model;

/**
 * Nacos configuration history detail information.
 *
 * @author xiweng.yy
 */
public class ConfigHistoryDetailInfo extends ConfigHistoryBasicInfo {
    
    private static final long serialVersionUID = 5498431203024164923L;
    
    private String content;
    
    private String encryptedDataKey;
    
    private String grayName;
    
    private String extInfo;
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getEncryptedDataKey() {
        return encryptedDataKey;
    }
    
    public void setEncryptedDataKey(String encryptedDataKey) {
        this.encryptedDataKey = encryptedDataKey;
    }
    
    public String getGrayName() {
        return grayName;
    }
    
    public void setGrayName(String grayName) {
        this.grayName = grayName;
    }
    
    public String getExtInfo() {
        return extInfo;
    }
    
    public void setExtInfo(String extInfo) {
        this.extInfo = extInfo;
    }
}
