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

package com.alibaba.nacos.legacy.adapter.auth;

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Registers legacy auth adapter package in ControllerMethodsCache when
 * default-auth-plugin (new version) is present.
 *
 * @author xiweng.yy
 */
@Configuration
public class LegacyAdapterWebConfigForAuth {

    private static final String LEGACY_AUTH = "com.alibaba.nacos.legacy.adapter.auth";

    private final ControllerMethodsCache methodsCache;

    public LegacyAdapterWebConfigForAuth(ControllerMethodsCache methodsCache) {
        this.methodsCache = methodsCache;
    }

    /**
     * Registers legacy auth package in ControllerMethodsCache.
     */
    @PostConstruct
    public void init() {
        methodsCache.initClassMethod(LEGACY_AUTH);
    }
}
