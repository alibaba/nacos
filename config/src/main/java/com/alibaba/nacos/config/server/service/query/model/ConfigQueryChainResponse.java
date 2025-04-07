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

package com.alibaba.nacos.config.server.service.query.model;

import com.alibaba.nacos.config.server.model.ConfigCacheGray;
import com.alibaba.nacos.config.server.service.query.enums.ResponseCode;

import java.util.Objects;

/**
 * ConfigQueryChainResponse.
 *
 * @author Nacos
 */
public class ConfigQueryChainResponse {
    
    private String content;
    
    private String contentType;
    
    private String configType;
    
    private String encryptedDataKey;
    
    private String md5;
    
    private long lastModified;
    
    private ConfigCacheGray matchedGray;
    
    private int resultCode;
    
    private String message;
    
    private ConfigQueryStatus status;
    
    public enum ConfigQueryStatus {
        /**
         * Indicates that the configuration was found and is formal.
         */
        CONFIG_FOUND_FORMAL,
        
        /**
         * Indicates that the configuration was found and is gray.
         */
        CONFIG_FOUND_GRAY,
        
        /**
         * Indicates that the configuration special tag was not found.
         */
        SPECIAL_TAG_CONFIG_NOT_FOUND,
        
        /**
         * Indicates that the configuration was not found.
         */
        CONFIG_NOT_FOUND,
        
        /**
         * Indicates a conflict in the configuration query.
         */
        CONFIG_QUERY_CONFLICT,
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
    
    public int getResultCode() {
        return resultCode;
    }
    
    public void setResultCode(int resultCode) {
        this.resultCode = resultCode;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public ConfigQueryStatus getStatus() {
        return status;
    }
    
    public void setStatus(ConfigQueryStatus status) {
        this.status = status;
    }
    
    /**
     * Build fail response.
     *
     * @param errorCode errorCode.
     * @param message   message.
     * @return response.
     */
    public static ConfigQueryChainResponse buildFailResponse(int errorCode, String message) {
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setErrorInfo(errorCode, message);
        return response;
    }
    
    public void setErrorInfo(int errorCode, String errorMsg) {
        this.resultCode = ResponseCode.FAIL.getCode();
        this.message = errorMsg;
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
                && Objects.equals(resultCode, that.resultCode)
                && Objects.equals(message, that.message)
                && status == that.status;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(content, contentType, encryptedDataKey, md5, lastModified, matchedGray, resultCode, message, status);
    }
}