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

import com.alibaba.nacos.springdoc.cache.LocaleThreadLocalHolder;
import io.swagger.v3.oas.models.OpenAPI;
import org.springdoc.core.customizers.OpenApiBuilderCustomizer;
import org.springdoc.core.customizers.ServerBaseUrlCustomizer;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.providers.JavadocProvider;
import org.springdoc.core.service.OpenAPIService;
import org.springdoc.core.service.SecurityService;
import org.springdoc.core.utils.PropertyResolverUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * spring doc nacos locale cached open api service.
 *
 * @author xiweng.yy
 */
public class NacosLocaleCachedOpenApiService extends OpenAPIService {
    
    public NacosLocaleCachedOpenApiService(Optional<OpenAPI> openApi, SecurityService securityParser,
            SpringDocConfigProperties springDocConfigProperties, PropertyResolverUtils propertyResolverUtils,
            Optional<List<OpenApiBuilderCustomizer>> openApiBuilderCustomizers,
            Optional<List<ServerBaseUrlCustomizer>> serverBaseUrlCustomizers,
            Optional<JavadocProvider> javadocProvider) {
        super(openApi, securityParser, springDocConfigProperties, propertyResolverUtils, openApiBuilderCustomizers,
                serverBaseUrlCustomizers, javadocProvider);
    }
    
    @Override
    public OpenAPI build(Locale locale) {
        LocaleThreadLocalHolder.setLocale(locale);
        return super.build(locale);
    }
}
