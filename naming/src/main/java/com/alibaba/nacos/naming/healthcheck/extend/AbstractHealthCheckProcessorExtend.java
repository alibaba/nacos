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

package com.alibaba.nacos.naming.healthcheck.extend;

import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.SingletonBeanRegistry;

import java.util.Set;

/**
 * Abstract Health Check Processor Extend.
 *
 * @author sunmengying
 */
public abstract class AbstractHealthCheckProcessorExtend implements BeanFactoryAware {

    protected SingletonBeanRegistry registry;

    /**
     * Add HealthCheckProcessorV2.
     *
     * @param origin Origin Checker Type
     * @return Extend Processor Type
     */
    abstract Set<String> addProcessor(Set<String> origin);

    protected String lowerFirstChar(String simpleName) {
        if (StringUtils.isBlank(simpleName)) {
            throw new IllegalArgumentException("can't find extend processor class name");
        }
        return String.valueOf(simpleName.charAt(0)).toLowerCase() + simpleName.substring(1);
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (beanFactory instanceof SingletonBeanRegistry) {
            this.registry = (SingletonBeanRegistry) beanFactory;
        }
    }
}
