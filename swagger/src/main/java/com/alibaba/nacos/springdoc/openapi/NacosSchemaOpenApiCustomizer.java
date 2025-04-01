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

package com.alibaba.nacos.springdoc.openapi;

import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.springdoc.cache.SchemaCache;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * spring doc nacos schema open api customizer.
 *
 * @author xiweng.yy
 */
public class NacosSchemaOpenApiCustomizer implements GlobalOpenApiCustomizer {
    
    private final SchemaCache consoleSchemaCache;
    
    public NacosSchemaOpenApiCustomizer(SchemaCache consoleSchemaCache) {
        this.consoleSchemaCache = consoleSchemaCache;
    }
    
    @Override
    public void customise(OpenAPI openApi) {
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        Set<String> shouldRemoveRespSchemas = schemas.keySet().stream()
                .filter(schema -> schema.startsWith(Result.class.getSimpleName())).collect(Collectors.toSet());
        shouldRemoveRespSchemas.forEach(schemas::remove);
        schemas.putAll(consoleSchemaCache.getAllSchemas());
    }
}
