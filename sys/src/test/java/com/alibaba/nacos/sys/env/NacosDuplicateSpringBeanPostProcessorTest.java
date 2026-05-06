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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.BoundConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.LifecycleProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosDuplicateSpringBeanPostProcessorTest {
    
    @Mock
    NacosDuplicateSpringBeanPostProcessor processor;
    
    @Mock
    ConfigurableApplicationContext context;
    
    @Mock
    ConfigurableListableBeanFactory beanFactory;
    
    @Mock
    BeanDefinition beanDefinition;
    
    @BeforeEach
    void setUp() {
        processor = new NacosDuplicateSpringBeanPostProcessor(context);
    }
    
    @Test
    void testPostProcessBeforeInstantiationNonExist() {
        Class beanClass = LifecycleProcessor.class;
        assertNull(processor.postProcessBeforeInstantiation(beanClass, "lifecycleProcessor"));
        verify(context, never()).getBean("lifecycleProcessor");
    }
    
    @Test
    void testPostProcessBeforeInstantiationForContextBean() {
        when(context.containsBean("lifecycleProcessor")).thenReturn(true);
        when(context.getBeanFactory()).thenReturn(beanFactory);
        when(beanFactory.getBeanDefinition("lifecycleProcessor")).thenReturn(beanDefinition);
        Class beanClass = LifecycleProcessor.class;
        assertNull(processor.postProcessBeforeInstantiation(beanClass, "lifecycleProcessor"));
        verify(context, never()).getBean("lifecycleProcessor");
    }
    
    @Test
    void testPostProcessBeforeInstantiationForBootContextBean() {
        when(context.containsBean("boundConfigurationProperties")).thenReturn(true);
        when(context.getBeanFactory()).thenReturn(beanFactory);
        when(beanFactory.getBeanDefinition("boundConfigurationProperties")).thenReturn(beanDefinition);
        Class beanClass = BoundConfigurationProperties.class;
        assertNull(processor.postProcessBeforeInstantiation(beanClass, "boundConfigurationProperties"));
        verify(context, never()).getBean("boundConfigurationProperties");
    }
    
    @Test
    void testPostProcessBeforeInstantiationForNotContextBean() {
        when(context.containsBean("testBean")).thenReturn(true);
        when(context.getBeanFactory()).thenReturn(beanFactory);
        when(beanFactory.getBeanDefinition("testBean")).thenReturn(beanDefinition);
        Class beanClass = NacosDuplicateSpringBeanPostProcessorTest.class;
        when(context.getBean("testBean")).thenReturn(this);
        assertEquals(this, processor.postProcessBeforeInstantiation(beanClass, "testBean"));
    }
}