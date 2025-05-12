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

package com.alibaba.nacos.sys.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationUtilsTest {
    
    @Mock
    ConfigurableApplicationContext context;
    
    @Mock
    AutowireCapableBeanFactory factory;
    
    @BeforeEach
    void setUp() {
        ApplicationUtils.injectContext(context);
    }
    
    @AfterEach
    void tearDown() {
        ApplicationUtils.injectContext(null);
    }
    
    @Test
    void testIsStarted() {
        assertFalse(ApplicationUtils.isStarted());
        ApplicationUtils.setStarted(true);
        assertTrue(ApplicationUtils.isStarted());
    }
    
    @Test
    void testGetBeanByName() {
        when(context.getBean("testBeanName")).thenReturn(context);
        assertEquals(context, ApplicationUtils.getBean("testBeanName"));
    }
    
    @Test
    void testGetBeanByType() {
        when(context.getBean(ConfigurableApplicationContext.class)).thenReturn(context);
        assertEquals(context, ApplicationUtils.getBean(ConfigurableApplicationContext.class));
    }
    
    @Test
    void testGetBeanByNameAneType() {
        when(context.getBean("testBeanName", ConfigurableApplicationContext.class)).thenReturn(context);
        assertEquals(context, ApplicationUtils.getBean("testBeanName", ConfigurableApplicationContext.class));
    }
    
    @Test
    void testGetBeanByNameAndParams() {
        Object o = new Object();
        when(context.getBean("testBeanName", o)).thenReturn(context);
        assertEquals(context, ApplicationUtils.getBean("testBeanName", o));
    }
    
    @Test
    void testGetBeanByTypeAndParams() {
        Object o = new Object();
        when(context.getBean(ConfigurableApplicationContext.class, o)).thenReturn(context);
        assertEquals(context, ApplicationUtils.getBean(ConfigurableApplicationContext.class, o));
    }
    
    @Test
    void testGetBeanIfExist() {
        Consumer consumer = mock(Consumer.class);
        when(context.getBean(ConfigurableApplicationContext.class)).thenReturn(context);
        ApplicationUtils.getBeanIfExist(ConfigurableApplicationContext.class, consumer);
        verify(consumer).accept(context);
    }
    
    @Test
    void testGetBeanIfExistNonExist() {
        Consumer consumer = mock(Consumer.class);
        when(context.getBean(ConfigurableApplicationContext.class)).thenThrow(
                new NoSuchBeanDefinitionException("test"));
        ApplicationUtils.getBeanIfExist(ConfigurableApplicationContext.class, consumer);
        verify(consumer, never()).accept(context);
    }
    
    @Test
    void testContainsBean() {
        when(context.containsBean("testBeanName")).thenReturn(true);
        assertTrue(ApplicationUtils.containsBean("testBeanName"));
    }
    
    @Test
    void testGetType() {
        when(context.getType("testBeanName")).thenAnswer(invocationOnMock -> ConfigurableApplicationContext.class);
        assertEquals(ConfigurableApplicationContext.class, ApplicationUtils.getType("testBeanName"));
    }
    
    @Test
    void testPublishEvent() {
        Object o = new Object();
        ApplicationUtils.publishEvent(o);
        verify(context).publishEvent(o);
    }
    
    @Test
    void testGetResources() throws IOException {
        Resource[] resources = new Resource[0];
        when(context.getResources("test")).thenReturn(resources);
        assertEquals(resources, ApplicationUtils.getResources("test"));
    }
    
    @Test
    void testGetResource() {
        Resource resource = mock(Resource.class);
        when(context.getResource("test")).thenReturn(resource);
        assertEquals(resource, ApplicationUtils.getResource("test"));
    }
    
    @Test
    void testGetClassLoader() {
        when(context.getClassLoader()).thenReturn(Thread.currentThread().getContextClassLoader());
        assertEquals(Thread.currentThread().getContextClassLoader(), ApplicationUtils.getClassLoader());
    }
    
    @Test
    void initialize() {
        ApplicationUtils utils = new ApplicationUtils();
        ApplicationUtils.injectContext(null);
        utils.initialize(context);
        assertEquals(context, ApplicationUtils.getApplicationContext());
        
        ConfigurableApplicationContext subContext = mock(ConfigurableApplicationContext.class);
        when(subContext.getParent()).thenReturn(context);
        utils.initialize(subContext);
        assertEquals(subContext, ApplicationUtils.getApplicationContext());
        
        ConfigurableApplicationContext subContext2 = mock(ConfigurableApplicationContext.class);
        when(subContext2.getParent()).thenReturn(context);
        utils.initialize(subContext2);
        assertEquals(subContext, ApplicationUtils.getApplicationContext());
    }
}