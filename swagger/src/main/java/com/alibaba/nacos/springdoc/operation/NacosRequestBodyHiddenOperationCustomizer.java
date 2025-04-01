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

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.web.method.HandlerMethod;

/**
 * spring doc nacos request body hidden operation customizer.
 *
 * @author xiweng.yy
 */
public class NacosRequestBodyHiddenOperationCustomizer implements GlobalOperationCustomizer {
    
    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (!handlerMethod.getMethod().isAnnotationPresent(RequestBody.class)) {
            operation.setRequestBody(null);
        }
        return operation;
    }
}
