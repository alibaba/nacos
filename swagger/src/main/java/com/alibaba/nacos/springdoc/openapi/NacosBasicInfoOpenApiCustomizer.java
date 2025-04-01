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

import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.springdoc.cache.LocaleThreadLocalHolder;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.utils.PropertyResolverUtils;

/**
 * spring doc nacos basic info open api customizer.
 *
 * @author xiweng.yy
 */
public class NacosBasicInfoOpenApiCustomizer implements GlobalOpenApiCustomizer {
    
    private final String title;
    
    private final String description;
    
    private final PropertyResolverUtils propertyResolverUtils;
    
    public NacosBasicInfoOpenApiCustomizer(String title, String description,
            PropertyResolverUtils propertyResolverUtils) {
        this.title = title;
        this.description = description;
        this.propertyResolverUtils = propertyResolverUtils;
    }
    
    @Override
    public void customise(OpenAPI openApi) {
        openApi.getInfo().setTitle(propertyResolverUtils.resolve(title, LocaleThreadLocalHolder.getLocale()));
        openApi.getInfo()
                .setDescription(propertyResolverUtils.resolve(description, LocaleThreadLocalHolder.getLocale()));
        openApi.getInfo().version(VersionUtils.version);
        License license = new License();
        license.setName("Apache 2.0");
        license.setUrl("https://github.com/alibaba/nacos/blob/develop/LICENSE");
        openApi.getInfo().setLicense(license);
    }
}
