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

package com.alibaba.nacos.client.config.filter.impl;

import com.alibaba.nacos.api.config.filter.IConfigContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Config Context.
 *
 * @author Nacos
 */
public class ConfigContext implements IConfigContext {
    
    private final Map<String, Object> param = new HashMap<String, Object>();
    
    @Override
    public Object getParameter(String key) {
        return param.get(key);
    }
    
    @Override
    public void setParameter(String key, Object value) {
        param.put(key, value);
    }
    
}
