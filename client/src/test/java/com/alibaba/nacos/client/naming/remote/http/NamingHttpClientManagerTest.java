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
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.request.HttpClientRequest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class NamingHttpClientManagerTest {
    
    @Test
    public void testGetInstance() {
        Assert.assertNotNull(NamingHttpClientManager.getInstance());
    }
    
    @Test
    public void testGetPrefix() {
        Assert.assertEquals("http://", NamingHttpClientManager.getInstance().getPrefix());
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
    
}