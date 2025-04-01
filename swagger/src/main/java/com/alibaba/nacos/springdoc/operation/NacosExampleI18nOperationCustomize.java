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

package com.alibaba.nacos.springdoc.operation;

import com.alibaba.nacos.api.exception.runtime.NacosDeserializationException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.springdoc.cache.LocaleThreadLocalHolder;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.utils.PropertyResolverUtils;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;

/**
 * spring doc nacos example i18n operation customize.
 *
 * @author xiweng.yy
 */
public class NacosExampleI18nOperationCustomize implements GlobalOperationCustomizer {
    
    private final PropertyResolverUtils propertyResolverUtils;
    
    public NacosExampleI18nOperationCustomize(PropertyResolverUtils propertyResolverUtils) {
        this.propertyResolverUtils = propertyResolverUtils;
    }
    
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        operation.getResponses().forEach((status, apiResponse) -> {
            apiResponse.getContent();
            apiResponse.getContent().forEach((key, value) -> {
                Schema<?> schema = value.getSchema();
                if (null == schema) {
                    return;
                }
                Object example = schema.getExample();
                if (!(example instanceof String)) {
                    return;
                }
                String i18nExample = propertyResolverUtils.resolve((String) example,
                        LocaleThreadLocalHolder.getLocale());
                if (key.equals(MediaType.APPLICATION_JSON_VALUE)) {
                    try {
                        schema.setExample(JacksonUtils.toObj(i18nExample));
                    } catch (NacosDeserializationException e) {
                        schema.setExample(i18nExample);
                    }
                } else {
                    schema.setExample(i18nExample);
                }
            });
        });
        return operation;
    }
}
