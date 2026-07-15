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

package com.alibaba.nacos.config.server.service.notify;

import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class HttpClientManagerTest {
    
    @Test
    void testConstructor() {
        assertNotNull(new HttpClientManager());
    }
    
    @Test
    void testGetTemplates() {
        assertNotNull(HttpClientManager.getNacosRestTemplate());
        assertNotNull(HttpClientManager.getNacosAsyncRestTemplate());
    }
    
    @Test
    void testShutdownHandlesException() {
        HttpClientManager.getNacosRestTemplate();
        try (MockedStatic<HttpClientBeanHolder> holderMock =
            Mockito.mockStatic(HttpClientBeanHolder.class, Mockito.CALLS_REAL_METHODS)) {
            holderMock.when(() -> HttpClientBeanHolder.shutdownNacosSyncRest(
                "com.alibaba.nacos.config.server.service.notify.HttpClientManager"
                    + "$ConfigHttpClientFactory"))
                .thenThrow(new RuntimeException("broken"));
            
            ReflectionTestUtils.invokeMethod(HttpClientManager.class, "shutdown");
        }
    }
}
