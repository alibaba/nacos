/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
package com.alibaba.nacos.console.config;


import com.alibaba.nacos.core.code.ControllerMethodsCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @author nkorange
 * @since 1.2.0
 */
@Component
public class ConsoleConfig {

    @Autowired
    private ControllerMethodsCache methodsCache;

    @PostConstruct
    public void init() {
        methodsCache.initClassMethod("com.alibaba.nacos.naming.controllers");
        methodsCache.initClassMethod("com.alibaba.nacos.console.controller");
        methodsCache.initClassMethod("com.alibaba.nacos.config.server.controller");
    }
}
