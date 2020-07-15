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
import com.alibaba.nacos.api.remote.response.ResponseCode;

/**
 * ConfigQueryResponse.
 * @author liuzunfei
 * @version $Id: ConfigQueryResponse.java, v 0.1 2020年07月14日 2:47 PM liuzunfei Exp $
 */
public class ConfigQueryResponse extends Response {
    
    
    String content;
    
    public static final int CONFIG_NOT_FOUND = 300;
    
    public static final int CONFIG_QUERY_CONFLICT = 400;
    
    /**
     * Buid fail response.
     *
     * @param errorCode
     * @param message
     * @return
     */
    public static ConfigQueryResponse buildFailResponse(int errorCode, String message) {
        ConfigQueryResponse response = new ConfigQueryResponse(ResponseCode.FAIL.getCode(), message);
        response.setErrorCode(errorCode);
        return response;
    }
    
    /**
     * Buidl success resposne
     *
     * @param content
     * @return
     */
    public static ConfigQueryResponse buildSuccessResponse(String content) {
        ConfigQueryResponse response = new ConfigQueryResponse(ResponseCode.SUCCESS.getCode(), "");
        response.setContent(content);
        return response;
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
    
    public ConfigQueryResponse(int resultCode, String message) {
        super(ConfigResponseTypeConstants.CONFIG_QUERY, resultCode, message);
    }
    
}
