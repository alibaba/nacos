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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Login identity context.
 *
 * @author Nacos
 */
public class LoginIdentityContext {
    
    /**
     * get context from request.
     */
    private final Map<String, String> param = new HashMap<>();
    
    /**
     * get key from context.
     *
     * @param key key of request
     * @return value of param key
     */
    public String getParameter(String key) {
        return param.get(key);
    }
    
    /**
     * get key from context; if blank return default value.
     *
     * @param key key of request
     * @return value of param key
     */
    public String getParameter(String key, String defaultValue) {
        String val = param.get(key);
        return val == null ? defaultValue : val;
    }
    
    /**
     * put key and value to param.
     *
     * @param key   key of request
     * @param value value of request's key
     */
    public void setParameter(String key, String value) {
        param.put(key, value);
    }
    
    /**
     * put all parameters from Map.
     *
     * @param parameters map of parameters
     */
    public void setParameters(Map<String, String> parameters) {
        param.putAll(parameters);
    }
    
    /**
     * get all keys of param map.
     *
     * @return set all param keys.
     */
    public Set<String> getAllKey() {
        return param.keySet();
    }
    
}
