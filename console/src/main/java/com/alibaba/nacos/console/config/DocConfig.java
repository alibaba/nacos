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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.console.controller.v3.ConsoleServerStateController;
import com.alibaba.nacos.springdoc.openapi.NacosBasicInfoOpenApiCustomizer;
import com.alibaba.nacos.springdoc.operation.NacosExampleI18nOperationCustomize;
import com.alibaba.nacos.springdoc.operation.NacosGenericSchemaOperationCustomize;
import com.alibaba.nacos.springdoc.openapi.NacosLocaleCachedOpenApiService;
import com.alibaba.nacos.springdoc.openapi.NacosOnlyTagControllerOpenApiBuilderCustomizer;
import com.alibaba.nacos.springdoc.operation.NacosRequestBodyHiddenOperationCustomizer;
import com.alibaba.nacos.springdoc.openapi.NacosGenericSchemaOpenApiCustomizer;
import com.alibaba.nacos.springdoc.openapi.NacosSecurityOpenApiCustomizer;
import com.alibaba.nacos.springdoc.cache.SchemaCache;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.customizers.OpenApiBuilderCustomizer;
import org.springdoc.core.customizers.ServerBaseUrlCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.providers.JavadocProvider;
import org.springdoc.core.service.OpenAPIService;
import org.springdoc.core.service.SecurityService;
import org.springdoc.core.utils.PropertyResolverUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

/**
 * spring doc configuration for nacos console.
 *
 * @author xiweng.yy
 */
@Configuration
public class DocConfig {
    
    private final SpringDocConfigProperties springDocConfigProperties;
    
    public DocConfig(SpringDocConfigProperties springDocConfigProperties) {
        this.springDocConfigProperties = springDocConfigProperties;
    }
    
    @PostConstruct
    public void initSpringDocProperties() {
        springDocConfigProperties.setDefaultProducesMediaType(MediaType.APPLICATION_JSON_VALUE);
        springDocConfigProperties.setDefaultConsumesMediaType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }
    
    @Bean
    public GroupedOpenApi consoleOpenApi() {
        String[] packages = {ConsoleServerStateController.class.getPackageName()};
        return GroupedOpenApi.builder().group("console-api").packagesToScan(packages).build();
    }
    
    @Bean
    public OpenApiBuilderCustomizer nacosConsoleOnlyTagConrollerOpenApiBuilderCustomizer() {
        return new NacosOnlyTagControllerOpenApiBuilderCustomizer();
    }
    
    @Bean
    public OpenAPIService nacosConsoleOpenApiService(Optional<OpenAPI> openApi, SecurityService securityParser,
            SpringDocConfigProperties springDocConfigProperties, PropertyResolverUtils propertyResolverUtils,
            Optional<List<OpenApiBuilderCustomizer>> openApiBuilderCustomisers,
            Optional<List<ServerBaseUrlCustomizer>> serverBaseUrlCustomisers,
            Optional<JavadocProvider> javadocProvider) {
        return new NacosLocaleCachedOpenApiService(openApi, securityParser, springDocConfigProperties,
                propertyResolverUtils, openApiBuilderCustomisers, serverBaseUrlCustomisers, javadocProvider);
    }
    
    @Bean
    public GlobalOpenApiCustomizer nacosConsoleBasicInfoOpenApiCustomizer(PropertyResolverUtils propertyResolverUtils) {
        return new NacosBasicInfoOpenApiCustomizer("nacos.console.api.title", "nacos.console.api.description",
                propertyResolverUtils);
    }
    
    @Bean
    public GlobalOpenApiCustomizer nacosConsoleGenericSchemaOpenApiCustomizer(SchemaCache consoleSchemaCache) {
        return new NacosGenericSchemaOpenApiCustomizer(consoleSchemaCache);
    }
    
    @Bean
    public GlobalOpenApiCustomizer nacosConsoleSecurityRequirementOpenApiCustomizer(
            PropertyResolverUtils propertyResolverUtils) {
        return new NacosSecurityOpenApiCustomizer(propertyResolverUtils);
    }
    
    @Bean
    public SchemaCache consoleSchemaCache() {
        return new SchemaCache();
    }
    
    @Bean
    public GlobalOperationCustomizer nacosConsoleGenericSchemaOperationCustomize(SchemaCache consoleSchemaCache) {
        return new NacosGenericSchemaOperationCustomize(consoleSchemaCache);
    }
    
    @Bean
    public GlobalOperationCustomizer nacosConsoleRequestBodyHiddenOperationCustomize() {
        return new NacosRequestBodyHiddenOperationCustomizer();
    }
    
    @Bean
    public GlobalOperationCustomizer nacosConsoleExampleI18nOperationCustomize(PropertyResolverUtils propertyResolverUtils) {
        return new NacosExampleI18nOperationCustomize(propertyResolverUtils);
    }
    
}
