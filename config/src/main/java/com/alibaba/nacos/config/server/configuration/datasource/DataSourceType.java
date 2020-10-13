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

package com.alibaba.nacos.config.server.configuration.datasource;

import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * DataSourceType.
 *
 * @author Nacos
 */
public enum DataSourceType {
    /**
     * 内存.
     */
    EMBEDDED,
    /**
     * mysql.
     */
    MYSQL,
    /**
     * oracle.
     */
    ORACLE,
    /**
     * postgresql.
     */
    POSTGRESQL;
    
    private static final Map<String, DataSourceType> MAPPINGS = new HashMap<>(16);
    
    static {
        for (DataSourceType dataSourceType : values()) {
            MAPPINGS.put(dataSourceType.name(), dataSourceType);
        }
    }
    
    public boolean matches(String method) {
        return (this == resolve(method));
    }
    
    @Nullable
    public static DataSourceType resolve(@Nullable String dataSourceType) {
        
        return (dataSourceType != null ? MAPPINGS.get(dataSourceType.toUpperCase()) : null);
    }
}
