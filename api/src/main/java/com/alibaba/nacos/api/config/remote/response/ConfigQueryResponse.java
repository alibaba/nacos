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

import java.util.HashMap;
import java.util.Map;

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
    
    String contentType;
    
    Map<String, String> labels = new HashMap<String, String>();
    
    public ConfigQueryResponse() {
    }
    
    /**
     * add label to this response.
     *
     * @param key   key.
     * @param value value.
     */
    public void addLabel(String key, String value) {
        this.labels.put(key, value);
    }
    
    /**
     * Buid fail response.
     *
     * @param errorCode errorCode.
     * @param message   message.
     * @return
     */
    public static ConfigQueryResponse buildFailResponse(int errorCode, String message) {
        ConfigQueryResponse response = new ConfigQueryResponse();
        response.setErrorInfo(errorCode, message);
        return response;
    }
    
    /**
     * Buidl success resposne.
     *
     * @param content content.
     * @return
     */
    public static ConfigQueryResponse buildSuccessResponse(String content) {
        ConfigQueryResponse response = new ConfigQueryResponse();
        response.setContent(content);
        return response;
    }
    
    /**
     * Getter method for property <tt>labels</tt>.
     *
     * @return property value of labels
     */
    public Map<String, String> getLabels() {
        return labels;
    }
    
    /**
     * Setter method for property <tt>labels</tt>.
     *
     * @param labels value to be assigned to property labels
     */
    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
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
