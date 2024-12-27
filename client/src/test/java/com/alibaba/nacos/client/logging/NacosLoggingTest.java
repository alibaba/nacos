/*
 *
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
 *
 */

package com.alibaba.nacos.client.logging;

import com.alibaba.nacos.common.logging.NacosLoggingAdapter;
import com.alibaba.nacos.common.logging.NacosLoggingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class NacosLoggingTest {
    
    @Mock
    NacosLoggingAdapter loggingAdapter;
    
    NacosLoggingProperties loggingProperties;
    
    NacosLogging instance;
    
    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        loggingProperties = new NacosLoggingProperties("", new Properties());
        instance = NacosLogging.getInstance();
        Field loggingPropertiesField = NacosLogging.class.getDeclaredField("loggingProperties");
        loggingPropertiesField.setAccessible(true);
        loggingPropertiesField.set(instance, loggingProperties);
    }
    
    @Test
    void testGetInstance() {
        NacosLogging instance = NacosLogging.getInstance();
        assertNotNull(instance);
    }
    
    @Test
    void testLoadConfiguration() throws NoSuchFieldException, IllegalAccessException {
        instance = NacosLogging.getInstance();
        Field nacosLogging = NacosLogging.class.getDeclaredField("loggingAdapter");
        nacosLogging.setAccessible(true);
        nacosLogging.set(instance, loggingAdapter);
        instance.loadConfiguration();
        Mockito.verify(loggingAdapter, Mockito.times(1)).loadConfiguration(loggingProperties);
    }
    
    @Test
    void testLoadConfigurationWithException() throws NoSuchFieldException, IllegalAccessException {
        instance = NacosLogging.getInstance();
        Field nacosLoggingField = NacosLogging.class.getDeclaredField("loggingAdapter");
        nacosLoggingField.setAccessible(true);
        NacosLoggingAdapter cachedLogging = (NacosLoggingAdapter) nacosLoggingField.get(instance);
        try {
            doThrow(new RuntimeException()).when(loggingAdapter).loadConfiguration(loggingProperties);
            nacosLoggingField.set(instance, loggingAdapter);
            instance.loadConfiguration();
            // without exception thrown
        } finally {
            nacosLoggingField.set(instance, cachedLogging);
        }
    }
}