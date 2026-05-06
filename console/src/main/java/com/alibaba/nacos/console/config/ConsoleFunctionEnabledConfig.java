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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.naming.selector.SelectorManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Do some config and bean initialize when `functionMode` set. Such as `config` and `naming`.
 *
 * @author xiweng.yy
 */
@Configuration
public class ConsoleFunctionEnabledConfig {
    
    /**
     * If `functionMode` set as `config`,
     * the naming module bean will not be loaded, but console api required {@link SelectorManager} to do selector parser.
     *
     * @return {@link SelectorManager} bean
     */
    @Bean
    @ConditionalOnMissingBean
    public SelectorManager selectorManager() {
        return new SelectorManager();
    }
}
