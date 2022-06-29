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

package com.alibaba.nacos.client.env;

import com.alibaba.nacos.common.utils.StringUtils;

abstract class AbstractNacosEnvironment implements NacosEnvironment {
    
    @Override
    public Boolean getBoolean(String key) {
    
        final String value = getProperty(key);
    
        if (StringUtils.equalsIgnoreCase(value, Boolean.TRUE.toString())) {
            return Boolean.TRUE;
        }
    
        if (StringUtils.equalsIgnoreCase(value, Boolean.FALSE.toString())) {
            return Boolean.FALSE;
        }
    
        return null;
    }
    
    @Override
    public Integer getInteger(String key) {
    
        final String value = getProperty(key);
        
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (Exception e) {
            // ignore
            return null;
        }
    }
    
    @Override
    public Long getLong(String key) {

        final String value = getProperty(key);
    
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (Exception e) {
            // ignore
            return null;
        }
    }
    
}
