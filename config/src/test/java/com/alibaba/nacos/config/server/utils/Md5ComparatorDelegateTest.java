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

package com.alibaba.nacos.config.server.utils;

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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;

import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Md5ComparatorDelegateTest {
    
    public MockedStatic<EnvUtil> envUtilMockedStatic;
    
    public MockedStatic<NacosServiceLoader> nacosServiceLoaderMockedStatic;
    
    public MockedConstruction<NacosMd5Comparator> nacosMd5ComparatorMockedConstruction;
    
    @Mock
    public NacosMd5Comparator nacosMd5Comparator;
    
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
    public void test() {
        envUtilMockedStatic.when(() -> EnvUtil.getProperty("nacos.config.cache.type", "nacos")).thenReturn("lalala");
        nacosServiceLoaderMockedStatic.when(() -> NacosServiceLoader.load(Md5Comparator.class))
                .thenReturn(Collections.singletonList(nacosMd5Comparator));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HashMap<String, String> clientMd5Map = new HashMap<>();
        nacosMd5ComparatorMockedConstruction = mockConstruction(NacosMd5Comparator.class, (mock, context) -> {
            when(mock.compareMd5(request, response, clientMd5Map)).thenReturn(null);
        });
        Md5ComparatorDelegate.getInstance().compareMd5(request, response, clientMd5Map);
        verify(nacosMd5Comparator, times(0)).compareMd5(request, response, clientMd5Map);
        nacosMd5ComparatorMockedConstruction.close();
    }
    
    @Test
    public void test2() throws Exception {
        when(nacosMd5Comparator.getName()).thenReturn("nacos");
        envUtilMockedStatic.when(() -> EnvUtil.getProperty("nacos.config.cache.type", "nacos")).thenReturn("nacos");
        nacosServiceLoaderMockedStatic.when(() -> NacosServiceLoader.load(Md5Comparator.class))
                .thenReturn(Collections.singletonList(nacosMd5Comparator));
        Constructor constructor = Md5ComparatorDelegate.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Field field = Md5ComparatorDelegate.class.getDeclaredField("INSTANCE");
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        field.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        Md5ComparatorDelegate delegate = (Md5ComparatorDelegate) constructor.newInstance();
        field.set(null, delegate);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HashMap<String, String> clientMd5Map = new HashMap<>();
        Md5ComparatorDelegate.getInstance().compareMd5(request, response, clientMd5Map);
        verify(nacosMd5Comparator, times(1)).compareMd5(request, response, clientMd5Map);
    }
}