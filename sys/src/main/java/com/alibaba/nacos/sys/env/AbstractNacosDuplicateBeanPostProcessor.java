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

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Abstract Nacos Duplicate Bean Post Processor of {@link InstantiationAwareBeanPostProcessor} to reduce duplicate rebuild bean for spring beans.
 *
 * @author xiweng.yy
 */
public abstract class AbstractNacosDuplicateBeanPostProcessor implements InstantiationAwareBeanPostProcessor {
    
    private final ConfigurableApplicationContext coreContext;
    
    protected AbstractNacosDuplicateBeanPostProcessor(ConfigurableApplicationContext context) {
        coreContext = null == context.getParent() ? context : (ConfigurableApplicationContext) context.getParent();
    }
    
    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
        if (!coreContext.containsBean(beanName)) {
            return null;
        }
        BeanDefinition beanDefinition = coreContext.getBeanFactory().getBeanDefinition(beanName);
        return isReUsingBean(beanClass, beanName, beanDefinition) ? coreContext.getBean(beanName) : null;
    }
    
    /**
     * Judge whether re-use beans from core context.
     *
     * @param beanClass bean class
     * @param beanName bean name
     * @param beanDefinition bean definition
     * @return {@code true} means re-use beans from core context, otherwise {@code false} means to re-build bean in sub context.
     */
    protected abstract boolean isReUsingBean(Class<?> beanClass, String beanName, BeanDefinition beanDefinition);
}
