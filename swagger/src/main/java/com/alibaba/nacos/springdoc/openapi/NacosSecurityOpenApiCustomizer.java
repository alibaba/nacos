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
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.utils.PropertyResolverUtils;

/**
 * spring doc nacos security open api customizer.
 *
 * @author xiweng.yy
 */
public class NacosSecurityOpenApiCustomizer implements GlobalOpenApiCustomizer {
    
    private final PropertyResolverUtils propertyResolverUtils;
    
    public NacosSecurityOpenApiCustomizer(PropertyResolverUtils propertyResolverUtils) {
        this.propertyResolverUtils = propertyResolverUtils;
    }
    
    @Override
    public void customise(OpenAPI openApi) {
        String accessTokenDesc = propertyResolverUtils.resolve("nacos.api.auth.description",
                LocaleThreadLocalHolder.getLocale());
        openApi.getComponents().addSecuritySchemes("nacos",
                new SecurityScheme().type(SecurityScheme.Type.APIKEY).name("accessToken").in(SecurityScheme.In.HEADER)
                        .description(accessTokenDesc));
    }
}
