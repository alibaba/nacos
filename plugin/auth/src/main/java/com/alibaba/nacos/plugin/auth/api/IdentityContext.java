/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Identity Context.
 *
 * @author Wuyfee
 */
public class IdentityContext {
    
    /**
     * get context from request.
     */
    private final Map<String, Object> param = new HashMap<>();
    
    /**
     * Canonical names of identity parameters extracted from the original request.
     */
    private final Set<String> requestIdentityNames = new HashSet<>();
    
    /**
     * get key from context.
     *
     * @param key key of request
     * @return value of param key
     */
    public Object getParameter(String key) {
        return param.get(key);
    }
    
    /**
     * Get identity by key.
     *
     * @param key          identity name
     * @param defaultValue default value when the value is {@code null} or the value is not expected class type
     * @param <T>          classes type of identity value
     * @return identity value
     */
    public <T> T getParameter(String key, T defaultValue) {
        if (null == defaultValue) {
            throw new IllegalArgumentException(
                "defaultValue can't be null. Please use #getParameter(String key) replace");
        }
        try {
            Object result = param.get(key);
            if (null != result) {
                return (T) defaultValue.getClass().cast(result);
            }
            return defaultValue;
        } catch (ClassCastException exception) {
            return defaultValue;
        }
    }
    
    /**
     * put key and value to param.
     *
     * @param key   key of request
     * @param value value of request's key
     */
    public void setParameter(String key, Object value) {
        param.put(key, value);
    }
    
    /**
     * Put an identity parameter extracted from the original request.
     *
     * @param key   canonical identity name
     * @param value identity value from the request
     */
    public void setRequestIdentityParameter(String key, String value) {
        requestIdentityNames.add(key);
        param.put(key, value);
    }
    
    /**
     * Get canonical names of identity parameters extracted from the original request.
     *
     * @return request identity names
     */
    public Set<String> getRequestIdentityNames() {
        return Collections.unmodifiableSet(requestIdentityNames);
    }
}
