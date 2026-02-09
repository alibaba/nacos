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

package com.alibaba.nacos.legacy.adapter.config;

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Registers all legacy v1/v2 API controller packages with {@link ControllerMethodsCache}
 * when running in NacosServerWebApplication context.
 *
 * @author xiweng.yy
 */
@Configuration
public class LegacyAdapterWebConfigForServer {

    private static final String LEGACY_CONSOLE = "com.alibaba.nacos.legacy.adapter.console";
    private static final String LEGACY_CORE = "com.alibaba.nacos.legacy.adapter.core";
    private static final String LEGACY_CONFIG = "com.alibaba.nacos.legacy.adapter.config";
    private static final String LEGACY_NAMING = "com.alibaba.nacos.legacy.adapter.naming";

    private final ControllerMethodsCache methodsCache;

    public LegacyAdapterWebConfigForServer(ControllerMethodsCache methodsCache) {
        this.methodsCache = methodsCache;
    }

    /**
     * Registers all legacy adapter packages in ControllerMethodsCache.
     */
    @PostConstruct
    public void init() {
        methodsCache.initClassMethod(LEGACY_CONSOLE);
        methodsCache.initClassMethod(LEGACY_CORE);
        methodsCache.initClassMethod(LEGACY_CONFIG);
        methodsCache.initClassMethod(LEGACY_NAMING);
    }
}
