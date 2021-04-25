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

package com.alibaba.nacos.api.config;

import com.alibaba.nacos.api.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Config data type.
 *
 * @author liaochuntao
 **/
public enum ConfigType {
    
    /**
     * config type is "properties".
     */
    PROPERTIES("properties"),
    
    /**
     * config type is "xml".
     */
    XML("xml"),
    
    /**
     * config type is "json".
     */
    JSON("json"),
    
    /**
     * config type is "text".
     */
    TEXT("text"),
    
    /**
     * config type is "html".
     */
    HTML("html"),
    
    /**
     * config type is "yaml".
     */
    YAML("yaml"),
    
    /**
     * not a real type.
     */
    UNSET("unset");
    
    private final String type;
    
    private static final Map<String, ConfigType> LOCAL_MAP = new HashMap<String, ConfigType>();
    
    static {
        for (ConfigType configType : values()) {
            LOCAL_MAP.put(configType.getType(), configType);
        }
    }
    
    ConfigType(String type) {
        this.type = type;
    }
    
    public String getType() {
        return type;
    }
    
    public static ConfigType getDefaultType() {
        return TEXT;
    }
    
    /**
     * check input type is valid.
     *
     * @param type config type
     * @return it the type valid
     */
    public static Boolean isValidType(String type) {
        if (StringUtils.isBlank(type)) {
            return false;
        }
        return null != LOCAL_MAP.get(type) ? true : false;
    }
}
