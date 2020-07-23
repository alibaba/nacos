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

package com.alibaba.nacos.api.remote.request;

import java.util.HashMap;
import java.util.Map;

/**
 * Request.
 *
 * @author liuzunfei
 */
public abstract class Request {
    
    private final Map<String, String> headers = new HashMap<String, String>();
    
    /**
     * put header.
     *
     * @param key   key of value.
     * @param value value.
     */
    public void putHeader(String key, String value) {
        headers.put(key, value);
    }
    
    /**
     * put headers .
     *
     * @param headers headers to put.
     */
    public void putAllHeader(Map<String, String> headers) {
        headers.putAll(headers);
    }
    
    /**
     * get a header value .
     *
     * @param key key of value.
     * @return return value of key. return null if not exist.
     */
    public String getHeader(String key) {
        return headers.get(key);
    }
    
    /**
     * get a header value of defaut value.
     *
     * @param key          key of value.
     * @param defaultValue default value if key is not exist.
     * @return return final value.
     */
    public String getHeader(String key, String defaultValue) {
        String value = headers.get(key);
        return (value == null) ? defaultValue : value;
    }
    
    /**
     * Getter method for property <tt>type</tt>.
     *
     * @return property value of type
     */
    public abstract String getType();
    
    /**
     * Getter method for property <tt>type</tt>.
     *
     * @return property value of type
     */
    public abstract String getModule();
    
    /**
     * Getter method for property <tt>headers</tt>.
     *
     * @return property value of headers
     */
    public Map<String, String> getHeaders() {
        return headers;
    }
}
