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
import com.alibaba.nacos.springdoc.openapi.NacosLocaleCachedOpenApiService;
import com.alibaba.nacos.springdoc.openapi.NacosOnlyTagControllerOpenApiBuilderCustomizer;
import com.alibaba.nacos.springdoc.openapi.NacosSecurityOpenApiCustomizer;
import com.alibaba.nacos.springdoc.openapi.NacosTagSorterOpenApiCustomizer;
import com.alibaba.nacos.springdoc.operation.NacosExampleI18nOperationCustomize;
import com.alibaba.nacos.springdoc.operation.NacosRequestBodyHiddenOperationCustomizer;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.customizers.OpenApiBuilderCustomizer;
import org.springdoc.core.customizers.ServerBaseUrlCustomizer;
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
 * Nacos web server doc configuration.
 *
 * @author xiweng.yy
 */
@Configuration
@NacosWebBean
public class ServerDocConfig {
    
    private final SpringDocConfigProperties springDocConfigProperties;
    
    public ServerDocConfig(SpringDocConfigProperties springDocConfigProperties) {
        this.springDocConfigProperties = springDocConfigProperties;
    }
    
    @PostConstruct
    public void initSpringDocProperties() {
        springDocConfigProperties.setDefaultProducesMediaType(MediaType.APPLICATION_JSON_VALUE);
        springDocConfigProperties.setDefaultConsumesMediaType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
    }
    
    @Bean
    public OpenApiBuilderCustomizer nacosServerOnlyTagConrollerOpenApiBuilderCustomizer() {
        return new NacosOnlyTagControllerOpenApiBuilderCustomizer();
    }
    
    @Bean
    public OpenAPIService nacosServerOpenApiService(Optional<OpenAPI> openApi, SecurityService securityParser,
            SpringDocConfigProperties springDocConfigProperties, PropertyResolverUtils propertyResolverUtils,
            Optional<List<OpenApiBuilderCustomizer>> openApiBuilderCustomisers,
            Optional<List<ServerBaseUrlCustomizer>> serverBaseUrlCustomisers,
            Optional<JavadocProvider> javadocProvider) {
        return new NacosLocaleCachedOpenApiService(openApi, securityParser, springDocConfigProperties,
                propertyResolverUtils, openApiBuilderCustomisers, serverBaseUrlCustomisers, javadocProvider);
    }
    
    @Bean
    public GlobalOpenApiCustomizer nacosServerSecurityRequirementOpenApiCustomizer(
            PropertyResolverUtils propertyResolverUtils) {
        return new NacosSecurityOpenApiCustomizer(propertyResolverUtils);
    }
    
    @Bean
    public GlobalOpenApiCustomizer nacosServerTagSorterOpenApiCustomizer() {
        return new NacosTagSorterOpenApiCustomizer();
    }
    
    @Bean
    public GlobalOperationCustomizer nacosServerRequestBodyHiddenOperationCustomize() {
        return new NacosRequestBodyHiddenOperationCustomizer();
    }
    
    @Bean
    public GlobalOperationCustomizer nacosServerExampleI18nOperationCustomize(PropertyResolverUtils propertyResolverUtils) {
        return new NacosExampleI18nOperationCustomize(propertyResolverUtils);
    }
}
