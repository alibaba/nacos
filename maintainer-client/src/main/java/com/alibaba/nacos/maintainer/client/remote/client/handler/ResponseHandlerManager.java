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

package com.alibaba.nacos.maintainer.client.remote.client.handler;

import com.alibaba.nacos.maintainer.client.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JavaType;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class ResponseHandlerManager {
    
    public static final String STRING_TYPE = "java.lang.String";
    
    public static final String REST_RESULT_TYPE = "com.alibaba.nacos.maintainer.client.result.HttpRestResult";
    
    private static final Map<String, ResponseHandler> RESPONSE_HANDLER_MAP = new HashMap<>();
    
    private static final ResponseHandlerManager INSTANCE = new ResponseHandlerManager();
    
    private ResponseHandlerManager() {
        initDefaultResponseHandler();
    }
    
    public static ResponseHandlerManager getInstance() {
        return INSTANCE;
    }
    
    private void initDefaultResponseHandler() {
        // init response handler
        RESPONSE_HANDLER_MAP.put(STRING_TYPE, new StringResponseHandler());
        RESPONSE_HANDLER_MAP.put(REST_RESULT_TYPE, new RestResultResponseHandler());
    }
    
    /**
     * Select a response handler by responseType.
     *
     * @param responseType responseType
     * @return ResponseHandler
     */
    public ResponseHandler selectResponseHandler(Type responseType) {
        ResponseHandler responseHandler = null;
        if (responseType == null) {
            responseHandler = RESPONSE_HANDLER_MAP.get(REST_RESULT_TYPE);
        }
        if (responseHandler == null) {
            JavaType javaType = JacksonUtils.constructJavaType(responseType);
            String name = javaType.getRawClass().getName();
            responseHandler = RESPONSE_HANDLER_MAP.get(name);
        }
        responseHandler.setResponseType(responseType);
        return responseHandler;
    }
}