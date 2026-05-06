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

import com.alibaba.nacos.sys.env.mock.MockAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.LifecycleProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NacosDuplicateConfigurationBeanPostProcessorTest {
    
    @Mock
    NacosDuplicateConfigurationBeanPostProcessor processor;
    
    @Mock
    ConfigurableApplicationContext context;
    
    @Mock
    ConfigurableListableBeanFactory beanFactory;
    
    @Mock
    BeanDefinition beanDefinition;
    
    @BeforeEach
    void setUp() {
        processor = new NacosDuplicateConfigurationBeanPostProcessor(context);
    }
    
    @Test
    void testPostProcessBeforeInstantiationNonExist() {
        Class beanClass = LifecycleProcessor.class;
        assertNull(processor.postProcessBeforeInstantiation(beanClass, "lifecycleProcessor"));
        verify(context, never()).getBean("lifecycleProcessor");
    }
    
    @Test
    void testPostProcessBeforeInstantiationForConfigurationAnnotation() {
        String beanName = "com.alibaba.nacos.sys.env.mock.MockAutoConfiguration$MockConfiguration";
        when(context.containsBean(beanName)).thenReturn(true);
        when(context.getBeanFactory()).thenReturn(beanFactory);
        when(beanFactory.getBeanDefinition(beanName)).thenReturn(beanDefinition);
        Class beanClass = MockAutoConfiguration.MockConfiguration.class;
        MockAutoConfiguration.MockConfiguration existBean = new MockAutoConfiguration.MockConfiguration();
        when(context.getBean(beanName)).thenReturn(existBean);
        assertEquals(existBean, processor.postProcessBeforeInstantiation(beanClass, beanName));
    }
    
    @Test
    void testPostProcessBeforeInstantiationForAutoConfigurationAnnotation() {
        String beanName = "com.alibaba.nacos.sys.env.mock.MockAutoConfiguration";
        when(context.containsBean(beanName)).thenReturn(true);
        when(context.getBeanFactory()).thenReturn(beanFactory);
        when(beanFactory.getBeanDefinition(beanName)).thenReturn(beanDefinition);
        Class beanClass = MockAutoConfiguration.class;
        MockAutoConfiguration existBean = new MockAutoConfiguration();
        when(context.getBean(beanName)).thenReturn(existBean);
        assertEquals(existBean, processor.postProcessBeforeInstantiation(beanClass, beanName));
    }
    
    @Test
    void testPostProcessBeforeInstantiationForNormalBean() {
        when(context.containsBean("testBean")).thenReturn(true);
        when(context.getBeanFactory()).thenReturn(beanFactory);
        when(beanFactory.getBeanDefinition("testBean")).thenReturn(beanDefinition);
        Class beanClass = NacosDuplicateConfigurationBeanPostProcessor.class;
        assertNull(processor.postProcessBeforeInstantiation(beanClass, "testBean"));
        verify(context, never()).getBean("testBean");
    }
}