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
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigCacheMd5PostProcessorDelegateTest {
    
    MockedConstruction<NacosConfigCacheMd5PostProcessor> mockedConstruction;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<NacosServiceLoader> nacosServiceLoaderMockedStatic;
    
    @Mock
    public NacosConfigCacheMd5PostProcessor mockConfigCacheMd5PostProcessor;
    
    @BeforeEach
    void setUp() {
        envUtilMockedStatic = mockStatic(EnvUtil.class);
        mockedConstruction = mockConstruction(NacosConfigCacheMd5PostProcessor.class);
        nacosServiceLoaderMockedStatic = mockStatic(NacosServiceLoader.class);
        
    }
    
    @AfterEach
    void tearDown() {
        envUtilMockedStatic.close();
        mockedConstruction.close();
        nacosServiceLoaderMockedStatic.close();
    }
    
    @Test
    void test1() {
        when(mockConfigCacheMd5PostProcessor.getPostProcessorName()).thenReturn("nacos");
        envUtilMockedStatic.when(() -> EnvUtil.getProperty("nacos.config.cache.type", "nacos")).thenReturn("lalala");
        nacosServiceLoaderMockedStatic.when(() -> NacosServiceLoader.load(ConfigCacheMd5PostProcessor.class))
                .thenReturn(Collections.singletonList(mockConfigCacheMd5PostProcessor));
        ConfigCacheMd5PostProcessorDelegate.getInstance().postProcess(null, null);
        doNothing().when(mockedConstruction.constructed().get(0)).postProcess(null, null);
        verify(mockConfigCacheMd5PostProcessor, times(0)).postProcess(null, null);
        verify(mockedConstruction.constructed().get(0), times(1)).postProcess(null, null);
    }
    
    @Test
    void test2()
            throws NoSuchFieldException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        
        when(mockConfigCacheMd5PostProcessor.getPostProcessorName()).thenReturn("nacos");
        doNothing().when(mockConfigCacheMd5PostProcessor).postProcess(null, null);
        envUtilMockedStatic.when(() -> EnvUtil.getProperty("nacos.config.cache.type", "nacos")).thenReturn("nacos");
        nacosServiceLoaderMockedStatic.when(() -> NacosServiceLoader.load(ConfigCacheMd5PostProcessor.class))
                .thenReturn(Collections.singletonList(mockConfigCacheMd5PostProcessor));
        Constructor constructor = ConfigCacheMd5PostProcessorDelegate.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Field field = ConfigCacheMd5PostProcessorDelegate.class.getDeclaredField("INSTANCE");
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        field.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        ConfigCacheMd5PostProcessorDelegate delegate = (ConfigCacheMd5PostProcessorDelegate) constructor.newInstance();
        field.set(null, delegate);
        ConfigCacheMd5PostProcessorDelegate.getInstance().postProcess(null, null);
        verify(mockConfigCacheMd5PostProcessor, times(1)).postProcess(null, null);
    }
}