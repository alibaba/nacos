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

package com.alibaba.nacos.ai.config;

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * AI MCP spring configuration.
 *
 * @author xiweng.yy
 */
@Configuration
public class McpConfiguration {
    
    private final ControllerMethodsCache methodsCache;
    
    public McpConfiguration(ControllerMethodsCache methodsCache) {
        this.methodsCache = methodsCache;
    }
    
    @PostConstruct
    public void init() {
        methodsCache.initClassMethod("com.alibaba.nacos.ai.controller");
    }
}
