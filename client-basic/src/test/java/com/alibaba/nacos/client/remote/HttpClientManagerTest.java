/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.remote;

import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;

class HttpClientManagerTest {
    
    @Test
    void testGetInstanceReturnsSingleton() {
        HttpClientManager first = HttpClientManager.getInstance();
        HttpClientManager second = HttpClientManager.getInstance();
        assertNotNull(first);
        assertSame(first, second);
    }
    
    @Test
    void testGetConnectTimeoutOrDefaultUsesMin() {
        // Below 1000 ms minimum → returns 1000
        assertEquals(1000, HttpClientManager.getInstance().getConnectTimeoutOrDefault(500));
    }
    
    @Test
    void testGetConnectTimeoutOrDefaultUsesProvidedWhenLarger() {
        assertEquals(2000, HttpClientManager.getInstance().getConnectTimeoutOrDefault(2000));
    }
    
    @Test
    void testGetNacosRestTemplate() {
        NacosRestTemplate template = HttpClientManager.getInstance().getNacosRestTemplate();
        assertNotNull(template);
    }
    
    @Test
    void testShutdownDoesNotThrow() {
        // Note: shutdown closes the shared NacosRestTemplate. Run this test last via
        // method name ordering (JUnit runs in alphabetical order without explicit ordering).
        Assertions.assertDoesNotThrow(() -> HttpClientManager.getInstance().shutdown());
    }
    
    @Test
    void testShutdownSwallowsException() {
        try (MockedStatic<HttpClientBeanHolder> mocked =
            Mockito.mockStatic(HttpClientBeanHolder.class)) {
            mocked.when(() -> HttpClientBeanHolder.shutdownNacosSyncRest(anyString()))
                .thenThrow(new RuntimeException("forced"));
            Assertions.assertDoesNotThrow(() -> HttpClientManager.getInstance().shutdown());
        }
    }
    
    @Test
    void testHttpClientFactoryInternalsViaReflection() throws Exception {
        Field factoryField = HttpClientManager.class.getDeclaredField("HTTP_CLIENT_FACTORY");
        factoryField.setAccessible(true);
        Object factory = factoryField.get(null);
        Method buildConfig = factory.getClass().getDeclaredMethod("buildHttpClientConfig");
        buildConfig.setAccessible(true);
        Object config = buildConfig.invoke(factory);
        assertNotNull(config);
        Method assignLogger = factory.getClass().getDeclaredMethod("assignLogger");
        assignLogger.setAccessible(true);
        assertNotNull(assignLogger.invoke(factory));
    }
}
