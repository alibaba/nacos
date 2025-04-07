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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
    void testGetId() {
        when(context.getId()).thenReturn("testId");
        assertEquals("testId", ApplicationUtils.getId());
    }
    
    @Test
    void testGetApplicationName() {
        when(context.getApplicationName()).thenReturn("testName");
        assertEquals("testName", ApplicationUtils.getApplicationName());
    }
    
    @Test
    void testGetDisplayName() {
        when(context.getDisplayName()).thenReturn("testDisplayName");
        assertEquals("testDisplayName", ApplicationUtils.getDisplayName());
    }
    
    @Test
    void testGetStartupDate() {
        long currentTime = System.currentTimeMillis();
        when(context.getStartupDate()).thenReturn(currentTime);
        assertEquals(currentTime, ApplicationUtils.getStartupDate());
    }
    
    @Test
    void testGetParent() {
        when(context.getParent()).thenReturn(context);
        assertEquals(context, ApplicationUtils.getParent());
    }
    
    @Test
    void testGetAutowireCapableBeanFactory() {
        when(context.getAutowireCapableBeanFactory()).thenReturn(factory);
        assertEquals(factory, ApplicationUtils.getAutowireCapableBeanFactory());
    }
    
    @Test
    void testIsStarted() {
        assertFalse(ApplicationUtils.isStarted());
        ApplicationUtils.setStarted(true);
        assertTrue(ApplicationUtils.isStarted());
    }
    
    @Test
    void testGetParentBeanFactory() {
        when(context.getParentBeanFactory()).thenReturn(factory);
        assertEquals(factory, ApplicationUtils.getParentBeanFactory());
    }
    
    @Test
    void testContainsLocalBean() {
        when(context.containsLocalBean("testBeanName")).thenReturn(true);
        assertTrue(ApplicationUtils.containsLocalBean("testBeanName"));
    }
    
    @Test
    void testContainsBeanDefinition() {
        when(context.containsBeanDefinition("testBeanName")).thenReturn(true);
        assertTrue(ApplicationUtils.containsBeanDefinition("testBeanName"));
    }
    
    @Test
    void testGetBeanDefinitionCount() {
        when(context.getBeanDefinitionCount()).thenReturn(10);
        assertEquals(10, ApplicationUtils.getBeanDefinitionCount());
    }
    
    @Test
    void testGetBeanDefinitionNames() {
        String[] names = new String[10];
        when(context.getBeanDefinitionNames()).thenReturn(names);
        assertEquals(names, ApplicationUtils.getBeanDefinitionNames());
    }
    
    @Test
    void testGetBeanNamesForType() {
        ResolvableType type = ResolvableType.forClass(ConfigurableApplicationContext.class);
        String[] names = new String[10];
        when(context.getBeanNamesForType(type)).thenReturn(names);
        assertEquals(names, ApplicationUtils.getBeanNamesForType(type));
    }
    
    @Test
    void testGetBeanNamesForTypeWithClass() {
        String[] names = new String[10];
        when(context.getBeanNamesForType(ConfigurableApplicationContext.class)).thenReturn(names);
        assertEquals(names, ApplicationUtils.getBeanNamesForType(ConfigurableApplicationContext.class));
    }
    
    @Test
    void testGetBeanNamesForTypeMultipleParams() {
        String[] names = new String[10];
        when(context.getBeanNamesForType(ConfigurableApplicationContext.class, true, true)).thenReturn(names);
        assertEquals(names, ApplicationUtils.getBeanNamesForType(ConfigurableApplicationContext.class, true, true));
    }
    
    @Test
    void testGetBeansOfType() {
        Map<String, ConfigurableApplicationContext> maps = new HashMap<>();
        maps.put("testBeanName", context);
        when(context.getBeansOfType(ConfigurableApplicationContext.class)).thenReturn(maps);
        assertEquals(maps, ApplicationUtils.getBeansOfType(ConfigurableApplicationContext.class));
    }
    
    @Test
    void testGetBeansOfTypeMultipleParams() {
        Map<String, ConfigurableApplicationContext> maps = new HashMap<>();
        maps.put("testBeanName", context);
        when(context.getBeansOfType(ConfigurableApplicationContext.class, true, true)).thenReturn(maps);
        assertEquals(maps, ApplicationUtils.getBeansOfType(ConfigurableApplicationContext.class, true, true));
        
    }
    
    @Test
    void testGetBeanNamesForAnnotation() {
        String[] names = new String[10];
        when(context.getBeanNamesForAnnotation(Service.class)).thenReturn(names);
        assertEquals(names, ApplicationUtils.getBeanNamesForAnnotation(Service.class));
    }
    
    @Test
    void testGetBeansWithAnnotation() {
        Map<String, Object> maps = new HashMap<>();
        maps.put("testBeanName", context);
        when(context.getBeansWithAnnotation(Service.class)).thenReturn(maps);
        assertEquals(maps, ApplicationUtils.getBeansWithAnnotation(Service.class));
    }
    
    @Test
    void testFindAnnotationOnBean() throws NoSuchMethodException {
        Method method = ApplicationUtilsTest.class.getDeclaredMethod("testFindAnnotationOnBean");
        Test annotation = method.getAnnotation(Test.class);
        when(context.findAnnotationOnBean("testBeanName", Test.class)).thenReturn(annotation);
        assertEquals(annotation, ApplicationUtils.findAnnotationOnBean("testBeanName", Test.class));
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
    void testGetBeanProviderByClass() {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(context.getBeanProvider(ConfigurableApplicationContext.class)).thenReturn(provider);
        assertEquals(provider, ApplicationUtils.getBeanProvider(ConfigurableApplicationContext.class));
    }
    
    @Test
    void testGetBeanProviderByType() {
        ResolvableType type = ResolvableType.forClass(ConfigurableApplicationContext.class);
        ObjectProvider provider = mock(ObjectProvider.class);
        when(context.getBeanProvider(type)).thenReturn(provider);
        assertEquals(provider, ApplicationUtils.getBeanProvider(type));
    }
    
    @Test
    void testContainsBean() {
        when(context.containsBean("testBeanName")).thenReturn(true);
        assertTrue(ApplicationUtils.containsBean("testBeanName"));
    }
    
    @Test
    void isSingleton() {
        when(context.isSingleton("testBeanName")).thenReturn(true);
        assertTrue(ApplicationUtils.isSingleton("testBeanName"));
    }
    
    @Test
    void isPrototype() {
        when(context.isPrototype("testBeanName")).thenReturn(true);
        assertTrue(ApplicationUtils.isPrototype("testBeanName"));
    }
    
    @Test
    void testIsTypeMatchByClass() {
        when(context.isTypeMatch("testBeanName", ConfigurableApplicationContext.class)).thenReturn(true);
        assertTrue(ApplicationUtils.isTypeMatch("testBeanName", ConfigurableApplicationContext.class));
    }
    
    @Test
    void testIsTypeMatchByType() {
        ResolvableType type = ResolvableType.forClass(ConfigurableApplicationContext.class);
        when(context.isTypeMatch("testBeanName", type)).thenReturn(true);
        assertTrue(ApplicationUtils.isTypeMatch("testBeanName", type));
    }
    
    @Test
    void testGetType() {
        when(context.getType("testBeanName")).thenAnswer(invocationOnMock -> ConfigurableApplicationContext.class);
        assertEquals(ConfigurableApplicationContext.class, ApplicationUtils.getType("testBeanName"));
    }
    
    @Test
    void testGetAliases() {
        String[] names = new String[10];
        when(context.getAliases("testBeanName")).thenReturn(names);
        assertEquals(names, ApplicationUtils.getAliases("testBeanName"));
    }
    
    @Test
    void testPublishEvent() {
        Object o = new Object();
        ApplicationUtils.publishEvent(o);
        verify(context).publishEvent(o);
    }
    
    @Test
    void testGetMessageWithDefault() {
        when(context.getMessage("test", null, "default", Locale.getDefault())).thenReturn("test");
        assertEquals("test", ApplicationUtils.getMessage("test", null, "default", Locale.getDefault()));
    }
    
    @Test
    void testGetMessage() {
        when(context.getMessage("test", null, Locale.getDefault())).thenReturn("test");
        assertEquals("test", ApplicationUtils.getMessage("test", null, Locale.getDefault()));
    }
    
    @Test
    void testGetMessageByMessageSourceResolvable() {
        MessageSourceResolvable resolvable = mock(MessageSourceResolvable.class);
        when(context.getMessage(resolvable, Locale.getDefault())).thenReturn("test");
        assertEquals("test", ApplicationUtils.getMessage(resolvable, Locale.getDefault()));
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
        utils.initialize(context);
        assertEquals(context, ApplicationUtils.getApplicationContext());
    }
}