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
 * Registers only console-related legacy v1/v2 API controller package with
 * {@link ControllerMethodsCache} when running in NacosConsole context.
 *
 * @author xiweng.yy
 */
@Configuration
public class LegacyAdapterWebConfigForConsole {

    private static final String LEGACY_CONSOLE = "com.alibaba.nacos.legacy.adapter.console";

    private final ControllerMethodsCache methodsCache;

    public LegacyAdapterWebConfigForConsole(ControllerMethodsCache methodsCache) {
        this.methodsCache = methodsCache;
    }

    /**
     * Registers console legacy adapter package in ControllerMethodsCache.
     */
    @PostConstruct
    public void init() {
        methodsCache.initClassMethod(LEGACY_CONSOLE);
    }
}
