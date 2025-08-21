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

package com.alibaba.nacos.sys.utils;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Nacos global tool class.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class ApplicationUtils implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    
    private static ApplicationContext applicationContext;
    
    private static boolean started = false;
    
    public static boolean isStarted() {
        return started;
    }
    
    public static void setStarted(boolean started) {
        ApplicationUtils.started = started;
    }
    
    public static Object getBean(String name) throws BeansException {
        return applicationContext.getBean(name);
    }
    
    public static <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        return applicationContext.getBean(name, requiredType);
    }
    
    public static Object getBean(String name, Object... args) throws BeansException {
        return applicationContext.getBean(name, args);
    }
    
    public static <T> T getBean(Class<T> requiredType) throws BeansException {
        return applicationContext.getBean(requiredType);
    }
    
    public static <T> void getBeanIfExist(Class<T> requiredType, Consumer<T> consumer) throws BeansException {
        try {
            T bean = applicationContext.getBean(requiredType);
            consumer.accept(bean);
        } catch (NoSuchBeanDefinitionException ignore) {
        }
    }
    
    public static <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
        return applicationContext.getBean(requiredType, args);
    }
    
    public static boolean containsBean(String name) {
        return applicationContext.containsBean(name);
    }
    
    public static Class<?> getType(String name) throws NoSuchBeanDefinitionException {
        return applicationContext.getType(name);
    }
    
    public static void publishEvent(Object event) {
        applicationContext.publishEvent(event);
    }
    
    public static Resource[] getResources(String locationPattern) throws IOException {
        return applicationContext.getResources(locationPattern);
    }
    
    public static Resource getResource(String location) {
        return applicationContext.getResource(location);
    }
    
    public static ClassLoader getClassLoader() {
        return applicationContext.getClassLoader();
    }
    
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }
    
    public static void injectContext(ConfigurableApplicationContext context) {
        ApplicationUtils.applicationContext = context;
    }
    
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (null == applicationContext) {
            // First time be called, set the context directly.
            applicationContext = context;
        } else if (context.getParent() == applicationContext) {
            // Not first time be called, which means sub context initialize, only store the first sub context.
            applicationContext = context;
        }
    }
}
