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
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Locale;
import java.util.Map;
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
    
    public static String getId() {
        return applicationContext.getId();
    }
    
    public static String getApplicationName() {
        return applicationContext.getApplicationName();
    }
    
    public static String getDisplayName() {
        return applicationContext.getDisplayName();
    }
    
    public static long getStartupDate() {
        return applicationContext.getStartupDate();
    }
    
    public static ApplicationContext getParent() {
        return applicationContext.getParent();
    }
    
    public static AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
        return applicationContext.getAutowireCapableBeanFactory();
    }
    
    public static boolean isStarted() {
        return started;
    }
    
    public static void setStarted(boolean started) {
        ApplicationUtils.started = started;
    }
    
    public static BeanFactory getParentBeanFactory() {
        return applicationContext.getParentBeanFactory();
    }
    
    public static boolean containsLocalBean(String name) {
        return applicationContext.containsLocalBean(name);
    }
    
    public static boolean containsBeanDefinition(String beanName) {
        return applicationContext.containsLocalBean(beanName);
    }
    
    public static int getBeanDefinitionCount() {
        return applicationContext.getBeanDefinitionCount();
    }
    
    public static String[] getBeanDefinitionNames() {
        return applicationContext.getBeanDefinitionNames();
    }
    
    public static String[] getBeanNamesForType(ResolvableType type) {
        return applicationContext.getBeanNamesForType(type);
    }
    
    public static String[] getBeanNamesForType(Class<?> type) {
        return applicationContext.getBeanNamesForType(type);
    }
    
    public static String[] getBeanNamesForType(Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
        return applicationContext.getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
    }
    
    public static <T> Map<String, T> getBeansOfType(Class<T> type) throws BeansException {
        return applicationContext.getBeansOfType(type);
    }
    
    public static <T> Map<String, T> getBeansOfType(Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
            throws BeansException {
        return applicationContext.getBeansOfType(type, includeNonSingletons, allowEagerInit);
    }
    
    public static String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
        return applicationContext.getBeanNamesForAnnotation(annotationType);
    }
    
    public static Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType)
            throws BeansException {
        return applicationContext.getBeansWithAnnotation(annotationType);
    }
    
    public static <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
            throws NoSuchBeanDefinitionException {
        return applicationContext.findAnnotationOnBean(beanName, annotationType);
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
    
    public static <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
        return applicationContext.getBeanProvider(requiredType);
    }
    
    public static <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
        return applicationContext.getBeanProvider(requiredType);
    }
    
    public static boolean containsBean(String name) {
        return applicationContext.containsBean(name);
    }
    
    public static boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
        return applicationContext.isSingleton(name);
    }
    
    public static boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
        return applicationContext.isPrototype(name);
    }
    
    public static boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
        return applicationContext.isTypeMatch(name, typeToMatch);
    }
    
    public static boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
        return applicationContext.isTypeMatch(name, typeToMatch);
    }
    
    public static Class<?> getType(String name) throws NoSuchBeanDefinitionException {
        return applicationContext.getType(name);
    }
    
    public static String[] getAliases(String name) {
        return applicationContext.getAliases(name);
    }
    
    public static void publishEvent(Object event) {
        applicationContext.publishEvent(event);
    }
    
    public static String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        return applicationContext.getMessage(code, args, defaultMessage, locale);
    }
    
    public static String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
        return applicationContext.getMessage(code, args, locale);
    }
    
    public static String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
        return applicationContext.getMessage(resolvable, locale);
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
        applicationContext = context;
    }
}
