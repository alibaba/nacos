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

package com.alibaba.nacos.server;

import com.alibaba.nacos.core.web.NacosWebBean;
import com.alibaba.nacos.sys.env.Constants;
import com.alibaba.nacos.sys.env.NacosDuplicateConfigurationBeanPostProcessor;
import com.alibaba.nacos.sys.env.NacosDuplicateSpringBeanPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Bean Post Processor Configuration for nacos web server.
 *
 * @author xiweng.yy
 */
@Configuration
@NacosWebBean
@ConditionalOnProperty(value = Constants.NACOS_DUPLICATE_BEAN_ENHANCEMENT_ENABLED, havingValue = "true", matchIfMissing = true)
public class NacosWebBeanPostProcessorConfiguration {
    
    @Bean
    public InstantiationAwareBeanPostProcessor nacosDuplicateSpringBeanPostProcessor(
            ConfigurableApplicationContext context) {
        return new NacosDuplicateSpringBeanPostProcessor(context);
    }
    
    @Bean
    public InstantiationAwareBeanPostProcessor nacosDuplicateConfigurationBeanPostProcessor(
            ConfigurableApplicationContext context) {
        return new NacosDuplicateConfigurationBeanPostProcessor(context);
    }
}
