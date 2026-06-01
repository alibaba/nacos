/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.client.HttpClientRequestInterceptor;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.client.response.HttpClientResponse;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.model.RequestHttpEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;

class ConfigHttpClientManagerTest {
    
    @Test
    void test() {
        final ConfigHttpClientManager instance1 = ConfigHttpClientManager.getInstance();
        final ConfigHttpClientManager instance2 = ConfigHttpClientManager.getInstance();
        
        assertEquals(instance1, instance2);
        
        final NacosRestTemplate nacosRestTemplate = instance1.getNacosRestTemplate();
        assertNotNull(nacosRestTemplate);
        
        final int time1 = instance1.getConnectTimeoutOrDefault(10);
        assertEquals(1000, time1);
        final int time2 = instance1.getConnectTimeoutOrDefault(2000);
        assertEquals(2000, time2);
        
        Assertions.assertDoesNotThrow(() -> {
            instance1.shutdown();
        });
    }
    
    @Test
    void testGetNacosRestTemplateAddsLimiterInterceptorOnce() {
        ConfigHttpClientManager instance = ConfigHttpClientManager.getInstance();
        // first call adds interceptor
        NacosRestTemplate t1 = instance.getNacosRestTemplate();
        int afterFirst = t1.getInterceptors().size();
        // second call should not add a duplicate
        NacosRestTemplate t2 = instance.getNacosRestTemplate();
        assertEquals(afterFirst, t2.getInterceptors().size());
    }
    
    @Test
    void testShutdownSwallowsException() {
        try (MockedStatic<HttpClientBeanHolder> mocked =
            Mockito.mockStatic(HttpClientBeanHolder.class)) {
            mocked.when(() -> HttpClientBeanHolder.shutdownNacosSyncRest(anyString()))
                .thenThrow(new RuntimeException("forced"));
            Assertions.assertDoesNotThrow(() -> ConfigHttpClientManager.getInstance().shutdown());
        }
    }
    
    @Test
    void testConfigHttpClientFactoryInternalsViaReflection() throws Exception {
        java.lang.reflect.Field f =
            ConfigHttpClientManager.class.getDeclaredField("HTTP_CLIENT_FACTORY");
        f.setAccessible(true);
        Object factory = f.get(null);
        Method buildConfig = factory.getClass().getDeclaredMethod("buildHttpClientConfig");
        buildConfig.setAccessible(true);
        assertNotNull(buildConfig.invoke(factory));
        Method assignLogger = factory.getClass().getDeclaredMethod("assignLogger");
        assignLogger.setAccessible(true);
        assertNotNull(assignLogger.invoke(factory));
    }
    
    @Test
    void testLimiterHttpClientRequestInterceptorIsInterceptDelegatesToLimiter() throws Exception {
        Class<?> interceptorClass = Class.forName(
            "com.alibaba.nacos.client.config.impl.ConfigHttpClientManager$"
                + "LimiterHttpClientRequestInterceptor");
        Constructor<?> ctor = interceptorClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        HttpClientRequestInterceptor interceptor =
            (HttpClientRequestInterceptor) ctor.newInstance();
        URI uri = new URI("http://example.com/path");
        // Empty body branch
        RequestHttpEntity emptyEntity = new RequestHttpEntity(Header.newInstance(), null);
        // Returns boolean depending on Limiter; we just verify it runs without throwing
        Assertions.assertDoesNotThrow(() -> interceptor.isIntercept(uri, "GET", emptyEntity));
    }
    
    @Test
    void testLimiterHttpClientRequestInterceptorIntercept() throws Exception {
        Class<?> interceptorClass = Class.forName(
            "com.alibaba.nacos.client.config.impl.ConfigHttpClientManager$"
                + "LimiterHttpClientRequestInterceptor");
        Constructor<?> ctor = interceptorClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        HttpClientRequestInterceptor interceptor =
            (HttpClientRequestInterceptor) ctor.newInstance();
        HttpClientResponse response = interceptor.intercept();
        assertNotNull(response);
        assertEquals(NacosException.CLIENT_OVER_THRESHOLD, response.getStatusCode());
        assertEquals(Header.EMPTY, response.getHeaders());
        assertNull(response.getStatusText());
        try (InputStream body = response.getBody()) {
            byte[] buf = new byte[256];
            int n = body.read(buf);
            String content = new String(buf, 0, n, StandardCharsets.UTF_8);
            assertEquals("More than client-side current limit threshold", content);
        }
        Assertions.assertDoesNotThrow(response::close);
    }
    
    @Test
    void testLimiterInterceptorHandlesNonEmptyBody() throws Exception {
        Class<?> interceptorClass = Class.forName(
            "com.alibaba.nacos.client.config.impl.ConfigHttpClientManager$"
                + "LimiterHttpClientRequestInterceptor");
        Constructor<?> ctor = interceptorClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        HttpClientRequestInterceptor interceptor =
            (HttpClientRequestInterceptor) ctor.newInstance();
        URI uri = new URI("http://example.com/x");
        RequestHttpEntity entity = new RequestHttpEntity(Header.newInstance(), "non-empty-body");
        Assertions.assertDoesNotThrow(() -> interceptor.isIntercept(uri, "POST", entity));
    }
}
