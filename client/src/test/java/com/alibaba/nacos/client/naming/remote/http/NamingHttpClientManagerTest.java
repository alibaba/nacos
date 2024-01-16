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

package com.alibaba.nacos.client.naming.remote.http;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.request.HttpClientRequest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTP_PREFIX;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class NamingHttpClientManagerTest {
    
    @Test
    public void testGetInstance() {
        Assert.assertNotNull(NamingHttpClientManager.getInstance());
    }
    
    @Test
    public void testGetPrefix() {
        assertEquals(HTTP_PREFIX, NamingHttpClientManager.getInstance().getPrefix());
    }
    
    @Test
    public void testGetNacosRestTemplate() {
        Assert.assertNotNull(NamingHttpClientManager.getInstance().getNacosRestTemplate());
    }
    
    @Test
    public void testShutdown() throws NoSuchFieldException, IllegalAccessException, NacosException, IOException {
        //given
        NamingHttpClientManager instance = NamingHttpClientManager.getInstance();
        
        HttpClientRequest mockHttpClientRequest = Mockito.mock(HttpClientRequest.class);
        Field requestClient = NacosRestTemplate.class.getDeclaredField("requestClient");
        requestClient.setAccessible(true);
        requestClient.set(instance.getNacosRestTemplate(), mockHttpClientRequest);
        // when
        NamingHttpClientManager.getInstance().shutdown();
        // then
        verify(mockHttpClientRequest, times(1)).close();
    }
    
    @Test
    public void testShutdownWithException() throws Exception {
        String key = "com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager$NamingHttpClientFactory";
        try {
            HttpClientBeanHolder.shutdownNacosSyncRest(key);
        } catch (Exception ignored) {
        }
        Field field = HttpClientBeanHolder.class.getDeclaredField("SINGLETON_REST");
        field.setAccessible(true);
        Map<String, NacosRestTemplate> map = (Map<String, NacosRestTemplate>) field.get(null);
        NacosRestTemplate mockRest = mock(NacosRestTemplate.class);
        map.put(key, mockRest);
        doThrow(new RuntimeException("test")).when(mockRest).close();
        NamingHttpClientManager.getInstance().shutdown();
        assertEquals(mockRest, map.remove(key));
    }
}