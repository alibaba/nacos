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

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.customizers.OpenApiBuilderCustomizer;
import org.springdoc.core.service.OpenAPIService;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * spring doc nacos only tag controller open api builder customizer.
 *
 * @author xiweng.yy
 */
public class NacosOnlyTagControllerOpenApiBuilderCustomizer implements OpenApiBuilderCustomizer {
    
    @Override
    public void customise(OpenAPIService openApiService) {
        Map<String, Object> needGenerateControllers = openApiService.getMappingsMap().entrySet().parallelStream()
                .filter(controller -> (AnnotationUtils.findAnnotation(controller.getValue().getClass(), Tag.class)
                        != null)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        openApiService.getMappingsMap().clear();
        openApiService.getMappingsMap().putAll(needGenerateControllers);
        openApiService.getControllerAdviceMap().clear();
    }
}
