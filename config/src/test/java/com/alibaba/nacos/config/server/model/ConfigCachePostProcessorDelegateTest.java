/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model;

import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Collections;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigCachePostProcessorDelegateTest {
    
    MockedConstruction<NacosConfigCachePostProcessor> mockedConstruction;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<NacosServiceLoader> nacosServiceLoaderMockedStatic;
    
    @Mock
    public NacosConfigCachePostProcessor mockConfigCacheMd5PostProcessor;
    
    @BeforeEach
    void setUp() {
        envUtilMockedStatic = mockStatic(EnvUtil.class);
        nacosServiceLoaderMockedStatic = mockStatic(NacosServiceLoader.class);
    }
    
    @AfterEach
    void tearDown() {
        envUtilMockedStatic.close();
        nacosServiceLoaderMockedStatic.close();
    }
    
    @Test
    void test1() {
        envUtilMockedStatic.when(() -> EnvUtil.getProperty("nacos.config.cache.type", "nacos")).thenReturn("lalala");
        nacosServiceLoaderMockedStatic.when(() -> NacosServiceLoader.load(ConfigCachePostProcessor.class))
                .thenReturn(Collections.singletonList(mockConfigCacheMd5PostProcessor));
        ConfigCachePostProcessorDelegate.getInstance().postProcess(null, null);
        verify(mockConfigCacheMd5PostProcessor, times(0)).postProcess(null, null);
    }
    
    @Test
    void test2()
            throws NoSuchFieldException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        when(mockConfigCacheMd5PostProcessor.getName()).thenReturn("nacos");
        doNothing().when(mockConfigCacheMd5PostProcessor).postProcess(null, null);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty("nacos.config.cache.type", "nacos")).thenReturn("nacos");
        nacosServiceLoaderMockedStatic.when(() -> NacosServiceLoader.load(ConfigCachePostProcessor.class))
                .thenReturn(Collections.singletonList(mockConfigCacheMd5PostProcessor));
        Constructor constructor = ConfigCachePostProcessorDelegate.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Field field = ConfigCachePostProcessorDelegate.class.getDeclaredField("INSTANCE");
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        field.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        ConfigCachePostProcessorDelegate delegate = (ConfigCachePostProcessorDelegate) constructor.newInstance();
        field.set(null, delegate);
        ConfigCachePostProcessorDelegate.getInstance().postProcess(null, null);
        verify(mockConfigCacheMd5PostProcessor, times(1)).postProcess(null, null);
    }
}