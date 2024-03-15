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

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import static org.mockito.Mockito.doThrow;

public class NacosLoggingTest {
    
    @Test
    public void testGetInstance() {
        NacosLogging instance = NacosLogging.getInstance();
        Assert.assertNotNull(instance);
    }
    
    @Test
    public void testLoadConfiguration() throws NoSuchFieldException, IllegalAccessException {
        NacosLogging instance = NacosLogging.getInstance();
        AbstractNacosLogging mockLogging = Mockito.mock(AbstractNacosLogging.class);
        Field nacosLogging = NacosLogging.class.getDeclaredField("nacosLogging");
        nacosLogging.setAccessible(true);
        nacosLogging.set(instance, mockLogging);
        instance.loadConfiguration();
        Mockito.verify(mockLogging, Mockito.times(1)).loadConfiguration();
    }
    
    @Test
    public void testLoadConfigurationWithException() throws NoSuchFieldException, IllegalAccessException {
        NacosLogging instance = NacosLogging.getInstance();
        Field nacosLoggingField = NacosLogging.class.getDeclaredField("nacosLogging");
        nacosLoggingField.setAccessible(true);
        AbstractNacosLogging cachedLogging = (AbstractNacosLogging) nacosLoggingField.get(instance);
        try {
            AbstractNacosLogging mockLogging = Mockito.mock(AbstractNacosLogging.class);
            doThrow(new RuntimeException()).when(mockLogging).loadConfiguration();
            nacosLoggingField.set(instance, mockLogging);
            instance.loadConfiguration();
            // without exception thrown
        } finally {
            nacosLoggingField.set(instance, cachedLogging);
        }
    }
}