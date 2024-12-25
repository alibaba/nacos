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

package com.alibaba.nacos.sys.env;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Nacos {@link InstantiationAwareBeanPostProcessor} to reduce duplicate rebuild bean for spring configuration.
 *
 * @author xiweng.yy
 */
public class NacosDuplicateConfigurationBeanPostProcessor extends AbstractNacosDuplicateBeanPostProcessor {
    
    public NacosDuplicateConfigurationBeanPostProcessor(ConfigurableApplicationContext context) {
        super(context);
    }
    
    @Override
    protected boolean isReUsingBean(Class<?> beanClass, String beanName, BeanDefinition beanDefinition) {
        return isConfiguration(beanClass);
    }
    
    private boolean isConfiguration(Class<?> beanClass) {
        return null != beanClass.getAnnotation(Configuration.class) || null != beanClass.getAnnotation(
                AutoConfiguration.class);
    }
}
