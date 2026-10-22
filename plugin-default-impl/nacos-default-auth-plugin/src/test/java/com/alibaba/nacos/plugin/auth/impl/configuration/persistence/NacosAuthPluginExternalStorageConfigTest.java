/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.auth.impl.configuration.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import com.alibaba.nacos.plugin.auth.impl.persistence.ExternalPermissionPersistServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.persistence.ExternalRolePersistServiceImpl;
import com.alibaba.nacos.plugin.auth.impl.persistence.ExternalUserPersistServiceImpl;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosAuthPluginExternalStorageConfigTest {
    
    @ParameterizedTest
    @ValueSource(strings = {"permissionPersistService", "rolePersistService", "userPersistService"})
    void shouldRetainDatasourceInitializationDuringAotProcessing(String beanName) throws Exception {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerBeanDefinition("configuration",
            new RootBeanDefinition(NacosAuthPluginExternalStorageConfig.class));
        RootBeanDefinition definition = new RootBeanDefinition(
            NacosAuthPluginExternalStorageConfig.class.getMethod(beanName).getReturnType());
        definition.setFactoryBeanName("configuration");
        definition.setFactoryMethodName(beanName);
        factory.registerBeanDefinition(beanName, definition);
        RegisteredBean bean = RegisteredBean.of(factory, beanName);
        assertFalse(bean.getBeanClass().isInterface());
        new CommonAnnotationBeanPostProcessor().processAheadOfTime(bean);
        String[] methods = bean.getMergedBeanDefinition().getInitMethodNames();
        assertNotNull(methods);
        assertTrue(Arrays.stream(methods)
            .anyMatch(method -> method.equals("init") || method.endsWith(".init")));
        assertTrue(factory.getSingletonCount() == 0);
    }
    
    @Test
    void shouldKeepTheExistingJvmBeanImplementations() {
        NacosAuthPluginExternalStorageConfig configuration =
            new NacosAuthPluginExternalStorageConfig();
        assertInstanceOf(ExternalPermissionPersistServiceImpl.class,
            configuration.permissionPersistService());
        assertInstanceOf(ExternalRolePersistServiceImpl.class, configuration.rolePersistService());
        assertInstanceOf(ExternalUserPersistServiceImpl.class, configuration.userPersistService());
    }
}
