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

package com.alibaba.nacos.core.web;

import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Nacos core web configuration.
 *
 * @author xiweng.yy
 */
@Configuration
@NacosWebBean
public class NacosCoreWebConfiguration {
    
    private final ControllerMethodsCache methodsCache;
    
    public NacosCoreWebConfiguration(ControllerMethodsCache methodsCache) {
        this.methodsCache = methodsCache;
    }
    
    @PostConstruct
    public void init() {
        methodsCache.initClassMethod("com.alibaba.nacos.core.controller");
    }
}
