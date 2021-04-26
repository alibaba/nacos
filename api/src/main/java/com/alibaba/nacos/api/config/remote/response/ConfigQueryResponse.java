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

package com.alibaba.nacos.api.config.remote.response;

import com.alibaba.nacos.api.remote.response.Response;

/**
 * ConfigQueryResponse.
 *
 * @author liuzunfei
 * @version $Id: ConfigQueryResponse.java, v 0.1 2020年07月14日 2:47 PM liuzunfei Exp $
 */
public class ConfigQueryResponse extends Response {
    
    public static final int CONFIG_NOT_FOUND = 300;
    
    public static final int CONFIG_QUERY_CONFLICT = 400;
    
    String content;
    
    String encryptedDataKey;
    
    String contentType;
    
    String md5;
    
    long lastModified;
    
    boolean isBeta;
    
    String tag;
    
    public ConfigQueryResponse() {
    }
    
    /**
     * Build fail response.
     *
     * @param errorCode errorCode.
     * @param message   message.
     * @return response.
     */
    public static ConfigQueryResponse buildFailResponse(int errorCode, String message) {
        ConfigQueryResponse response = new ConfigQueryResponse();
        response.setErrorInfo(errorCode, message);
        return response;
    }
    
    /**
     * Build success response.
     *
     * @param content content.
     * @return response.
     */
    public static ConfigQueryResponse buildSuccessResponse(String content) {
        ConfigQueryResponse response = new ConfigQueryResponse();
        response.setContent(content);
        return response;
    }
    
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
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
    
    public boolean isBeta() {
        return isBeta;
    }
    
    public void setBeta(boolean beta) {
        isBeta = beta;
    }
    
    /**
     * Getter method for property <tt>content</tt>.
     *
     * @return property value of content
     */
    public String getContent() {
        return content;
    }
    
    /**
     * Setter method for property <tt>content</tt>.
     *
     * @param content value to be assigned to property content
     */
    public void setContent(String content) {
        this.content = content;
    }
    
    public void setEncryptedDataKey(String encryptedDataKey) {
        this.encryptedDataKey = encryptedDataKey;
    }
    
    public String getEncryptedDataKey() {
        return encryptedDataKey;
    }
    
    /**
     * Getter method for property <tt>contentType</tt>.
     *
     * @return property value of contentType
     */
    public String getContentType() {
        return contentType;
    }
    
    /**
     * Setter method for property <tt>contentType</tt>.
     *
     * @param contentType value to be assigned to property contentType
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
