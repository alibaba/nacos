/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.controller.compatibility;

import com.alibaba.nacos.plugin.auth.constant.ApiType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Nacos old API compatibility annotation.
 * <p>
 *     Marked old API will be deprecated in future version, but for some users need time to refactor and move to new API.
 *     In this situation, change the configuration in {@link ApiCompatibilityConfig} to open the old API usage.
 * </p>
 *
 * @author xiweng.yy
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Compatibility {
    
    /**
     * The type of API. Distinguishing {@link ApiType}.
     *
     * @return the type of the API
     */
    ApiType apiType() default ApiType.OPEN_API;
    
    /**
     * APIs can replace this deprecated API.
     *
     * @return API list.
     */
    String alternatives() default "";
}
