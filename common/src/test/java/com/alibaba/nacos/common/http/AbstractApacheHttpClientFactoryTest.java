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

import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.request.DefaultHttpClientRequest;
import com.alibaba.nacos.common.http.client.request.HttpClientRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;

import java.lang.reflect.Field;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class AbstractApacheHttpClientFactoryTest {
    
    @Mock
    private Logger logger;
    
    @Before
    public void setUp() throws Exception {
    }
    
    @After
    public void tearDown() throws Exception {
    }
    
    @Test
    public void testCreateNacosRestTemplate() throws NoSuchFieldException, IllegalAccessException {
        HttpClientFactory factory = new AbstractApacheHttpClientFactory() {
            @Override
            protected HttpClientConfig buildHttpClientConfig() {
                return HttpClientConfig.builder().build();
            }
            
            @Override
            protected Logger assignLogger() {
                return logger;
            }
        };
        NacosRestTemplate template = factory.createNacosRestTemplate();
        assertNotNull(template);
        Field field = NacosRestTemplate.class.getDeclaredField("requestClient");
        field.setAccessible(true);
        HttpClientRequest requestClient = (HttpClientRequest) field.get(template);
        assertTrue(requestClient instanceof DefaultHttpClientRequest);
    }
}