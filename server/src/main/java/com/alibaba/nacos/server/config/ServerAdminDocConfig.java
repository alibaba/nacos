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

package com.alibaba.nacos.server.config;

import com.alibaba.nacos.core.web.NacosWebBean;
import com.alibaba.nacos.springdoc.cache.SchemaCache;
import com.alibaba.nacos.springdoc.openapi.NacosBasicInfoOpenApiCustomizer;
import com.alibaba.nacos.springdoc.openapi.NacosGenericSchemaOpenApiCustomizer;
import com.alibaba.nacos.springdoc.operation.NacosGenericSchemaOperationCustomize;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.utils.PropertyResolverUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * spring doc configuration for nacos web server admin api.
 *
 * @author xiweng.yy
 */
@Configuration
@NacosWebBean
public class ServerAdminDocConfig {
    
    @Bean
    public GroupedOpenApi adminOpenApi(PropertyResolverUtils propertyResolverUtils) {
        String[] paths = {"/v3/admin/**"};
        SchemaCache clientSchemaCache = adminSchemaCache();
        OpenApiCustomizer basicInfoOpenApiCustomizer = nacosAdminBasicInfoOpenApiCustomizer(propertyResolverUtils);
        OpenApiCustomizer genericSchemaOpenApiCustomizer = nacosAdminGenericSchemaOpenApiCustomizer(clientSchemaCache);
        OperationCustomizer genericSchemaOperationCustomizer = nacosAdminGenericSchemaOperationCustomize(clientSchemaCache);
        GroupedOpenApi.Builder builder = GroupedOpenApi.builder().group("admin-api").pathsToMatch(paths);
        builder.addOpenApiCustomizer(basicInfoOpenApiCustomizer);
        builder.addOpenApiCustomizer(genericSchemaOpenApiCustomizer);
        builder.addOperationCustomizer(genericSchemaOperationCustomizer);
        return builder.build();
    }
    
    public OpenApiCustomizer nacosAdminBasicInfoOpenApiCustomizer(PropertyResolverUtils propertyResolverUtils) {
        return new NacosBasicInfoOpenApiCustomizer("nacos.admin.api.title", "nacos.admin.api.description",
                propertyResolverUtils);
    }
    
    public OpenApiCustomizer nacosAdminGenericSchemaOpenApiCustomizer(SchemaCache consoleSchemaCache) {
        return new NacosGenericSchemaOpenApiCustomizer(consoleSchemaCache);
    }
    
    public OperationCustomizer nacosAdminGenericSchemaOperationCustomize(SchemaCache consoleSchemaCache) {
        return new NacosGenericSchemaOperationCustomize(consoleSchemaCache);
    }
    
    public SchemaCache adminSchemaCache() {
        return new SchemaCache();
    }
}
