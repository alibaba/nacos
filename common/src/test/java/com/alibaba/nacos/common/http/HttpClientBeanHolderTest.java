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

package com.alibaba.nacos.common.http;

import com.alibaba.nacos.common.http.client.NacosAsyncRestTemplate;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo remove this
@MockitoSettings(strictness = Strictness.LENIENT)
class HttpClientBeanHolderTest {
    
    private Map<String, NacosRestTemplate> cachedRestTemplateMap;
    
    private Map<String, NacosRestTemplate> restMap;
    
    private Map<String, NacosAsyncRestTemplate> cachedAsyncRestTemplateMap;
    
    private Map<String, NacosAsyncRestTemplate> restAsyncMap;
    
    @Mock
    private NacosRestTemplate mockRestTemplate;
    
    @Mock
    private NacosAsyncRestTemplate mockAsyncRestTemplate;
    
    @Mock
    private HttpClientFactory mockFactory;
    
    @BeforeEach
    void setUp() throws Exception {
        cachedRestTemplateMap = new HashMap<>();
        cachedAsyncRestTemplateMap = new HashMap<>();
        restMap = (Map<String, NacosRestTemplate>) getCachedMap("SINGLETON_REST");
        restAsyncMap = (Map<String, NacosAsyncRestTemplate>) getCachedMap("SINGLETON_ASYNC_REST");
        cachedRestTemplateMap.putAll(restMap);
        cachedAsyncRestTemplateMap.putAll(restAsyncMap);
        restMap.clear();
        restAsyncMap.clear();
        when(mockFactory.createNacosRestTemplate()).thenReturn(mockRestTemplate);
        when(mockFactory.createNacosAsyncRestTemplate()).thenReturn(mockAsyncRestTemplate);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        restMap.putAll(cachedRestTemplateMap);
        restAsyncMap.putAll(cachedAsyncRestTemplateMap);
        cachedRestTemplateMap.clear();
        cachedAsyncRestTemplateMap.clear();
    }
    
    private Object getCachedMap(String mapName) throws NoSuchFieldException, IllegalAccessException {
        Field field = HttpClientBeanHolder.class.getDeclaredField(mapName);
        field.setAccessible(true);
        return field.get(HttpClientBeanHolder.class);
    }
    
    @Test
    void testGetNacosRestTemplateWithDefault() {
        assertTrue(restMap.isEmpty());
        NacosRestTemplate actual = HttpClientBeanHolder.getNacosRestTemplate((Logger) null);
        assertEquals(1, restMap.size());
        NacosRestTemplate duplicateGet = HttpClientBeanHolder.getNacosRestTemplate((Logger) null);
        assertEquals(1, restMap.size());
        assertEquals(actual, duplicateGet);
    }
    
    @Test
    void testGetNacosRestTemplateForNullFactory() {
        assertThrows(NullPointerException.class, () -> {
            HttpClientBeanHolder.getNacosRestTemplate((HttpClientFactory) null);
        });
    }
    
    @Test
    void testGetNacosRestTemplateWithCustomFactory() {
        assertTrue(restMap.isEmpty());
        HttpClientBeanHolder.getNacosRestTemplate((Logger) null);
        assertEquals(1, restMap.size());
        NacosRestTemplate actual = HttpClientBeanHolder.getNacosRestTemplate(mockFactory);
        assertEquals(2, restMap.size());
        assertEquals(mockRestTemplate, actual);
    }
    
    @Test
    void testGetNacosAsyncRestTemplateWithDefault() {
        assertTrue(restAsyncMap.isEmpty());
        NacosAsyncRestTemplate actual = HttpClientBeanHolder.getNacosAsyncRestTemplate((Logger) null);
        assertEquals(1, restAsyncMap.size());
        NacosAsyncRestTemplate duplicateGet = HttpClientBeanHolder.getNacosAsyncRestTemplate((Logger) null);
        assertEquals(1, restAsyncMap.size());
        assertEquals(actual, duplicateGet);
    }
    
    @Test
    void testGetNacosAsyncRestTemplateForNullFactory() {
        assertThrows(NullPointerException.class, () -> {
            HttpClientBeanHolder.getNacosAsyncRestTemplate((HttpClientFactory) null);
        });
    }
    
    @Test
    void testGetNacosAsyncRestTemplateWithCustomFactory() {
        assertTrue(restAsyncMap.isEmpty());
        HttpClientBeanHolder.getNacosAsyncRestTemplate((Logger) null);
        assertEquals(1, restAsyncMap.size());
        NacosAsyncRestTemplate actual = HttpClientBeanHolder.getNacosAsyncRestTemplate(mockFactory);
        assertEquals(2, restAsyncMap.size());
        assertEquals(mockAsyncRestTemplate, actual);
    }
    
    @Test
    void shutdown() throws Exception {
        HttpClientBeanHolder.getNacosRestTemplate((Logger) null);
        HttpClientBeanHolder.getNacosAsyncRestTemplate((Logger) null);
        assertEquals(1, restMap.size());
        assertEquals(1, restAsyncMap.size());
        HttpClientBeanHolder.shutdown(DefaultHttpClientFactory.class.getName());
        assertEquals(0, restMap.size());
        assertEquals(0, restAsyncMap.size());
    }
}