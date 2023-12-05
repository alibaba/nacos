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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HttpClientBeanHolderTest {
    
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
    
    @Before
    public void setUp() throws Exception {
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
    
    @After
    public void tearDown() throws Exception {
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
    public void testGetNacosRestTemplateWithDefault() {
        assertTrue(restMap.isEmpty());
        NacosRestTemplate actual = HttpClientBeanHolder.getNacosRestTemplate((Logger) null);
        assertEquals(1, restMap.size());
        NacosRestTemplate duplicateGet = HttpClientBeanHolder.getNacosRestTemplate((Logger) null);
        assertEquals(1, restMap.size());
        assertEquals(actual, duplicateGet);
    }
    
    @Test(expected = NullPointerException.class)
    public void testGetNacosRestTemplateForNullFactory() {
        HttpClientBeanHolder.getNacosRestTemplate((HttpClientFactory) null);
    }
    
    @Test
    public void testGetNacosRestTemplateWithCustomFactory() {
        assertTrue(restMap.isEmpty());
        HttpClientBeanHolder.getNacosRestTemplate((Logger) null);
        assertEquals(1, restMap.size());
        NacosRestTemplate actual = HttpClientBeanHolder.getNacosRestTemplate(mockFactory);
        assertEquals(2, restMap.size());
        assertEquals(mockRestTemplate, actual);
    }
    
    @Test
    public void testGetNacosAsyncRestTemplateWithDefault() {
        assertTrue(restAsyncMap.isEmpty());
        NacosAsyncRestTemplate actual = HttpClientBeanHolder.getNacosAsyncRestTemplate((Logger) null);
        assertEquals(1, restAsyncMap.size());
        NacosAsyncRestTemplate duplicateGet = HttpClientBeanHolder.getNacosAsyncRestTemplate((Logger) null);
        assertEquals(1, restAsyncMap.size());
        assertEquals(actual, duplicateGet);
    }
    
    @Test(expected = NullPointerException.class)
    public void testGetNacosAsyncRestTemplateForNullFactory() {
        HttpClientBeanHolder.getNacosAsyncRestTemplate((HttpClientFactory) null);
    }
    
    @Test
    public void testGetNacosAsyncRestTemplateWithCustomFactory() {
        assertTrue(restAsyncMap.isEmpty());
        HttpClientBeanHolder.getNacosAsyncRestTemplate((Logger) null);
        assertEquals(1, restAsyncMap.size());
        NacosAsyncRestTemplate actual = HttpClientBeanHolder.getNacosAsyncRestTemplate(mockFactory);
        assertEquals(2, restAsyncMap.size());
        assertEquals(mockAsyncRestTemplate, actual);
    }
    
    @Test
    public void shutdown() throws Exception {
        HttpClientBeanHolder.getNacosRestTemplate((Logger) null);
        HttpClientBeanHolder.getNacosAsyncRestTemplate((Logger) null);
        assertEquals(1, restMap.size());
        assertEquals(1, restAsyncMap.size());
        HttpClientBeanHolder.shutdown(DefaultHttpClientFactory.class.getName());
        assertEquals(0, restMap.size());
        assertEquals(0, restAsyncMap.size());
    }
}