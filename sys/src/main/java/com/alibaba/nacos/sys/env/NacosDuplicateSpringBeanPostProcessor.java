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
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Nacos {@link InstantiationAwareBeanPostProcessor} to reduce duplicate rebuild bean for spring beans.
 *
 * <p>
 *     For some important spring beans like spring context beans, if reuse from parent, will cause some problem. So skip.
 * </p>
 *
 * @author xiweng.yy
 */
public class NacosDuplicateSpringBeanPostProcessor extends AbstractNacosDuplicateBeanPostProcessor {
    
    public NacosDuplicateSpringBeanPostProcessor(ConfigurableApplicationContext context) {
        super(context);
    }
    
    @Override
    protected boolean isReUsingBean(Class<?> beanClass, String beanName, BeanDefinition beanDefinition) {
        return !isContextBean(beanClass);
    }
    
    private boolean isContextBean(Class<?> beanClass) {
        return isContextClass(beanClass.getCanonicalName());
    }
    
    private boolean isContextClass(String beanClassName) {
        return beanClassName.startsWith("org.springframework.context")
                || beanClassName.startsWith("org.springframework.boot.context");
    }
}
