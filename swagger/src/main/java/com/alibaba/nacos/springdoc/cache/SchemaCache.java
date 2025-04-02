/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.springdoc.cache;

import io.swagger.v3.oas.models.media.Schema;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * spring doc schema cache.
 *
 * @author xiweng.yy
 */
public class SchemaCache {
    
    private final Map<String, Schema> respSchemas = new ConcurrentHashMap<>();
    
    public void put(String key, Schema schema) {
        respSchemas.put(key, schema);
    }
    
    public void putAll(Map<String, Schema> schemas) {
        respSchemas.putAll(schemas);
    }
    
    public Map<String, Schema> getAllSchemas() {
        return respSchemas;
    }
}
