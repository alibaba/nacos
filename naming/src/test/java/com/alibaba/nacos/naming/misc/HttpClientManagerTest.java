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

package com.alibaba.nacos.naming.misc;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.pool.PoolStats;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpClientManagerTest {
    
    @Test
    void testMonitorHealthCheckPoolRunsWithPoolingManager() throws Exception {
        PoolingAsyncClientConnectionManager manager =
            mock(PoolingAsyncClientConnectionManager.class);
        HttpRoute route = new HttpRoute(new HttpHost("http", "127.0.0.1", 8848));
        when(manager.getRoutes()).thenReturn(Collections.singleton(route));
        when(manager.getStats(route)).thenReturn(new PoolStats(1, 2, 3, 4));
        when(manager.getTotalStats()).thenReturn(new PoolStats(4, 3, 2, 1));
        
        newMonitorHealthCheckPool(manager).run();
        
        verify(manager).closeExpired();
        verify(manager).getStats(route);
        verify(manager).getTotalStats();
    }
    
    @Test
    void testMonitorHealthCheckPoolSwallowsCastFailure() throws Exception {
        AsyncClientConnectionManager manager = mock(AsyncClientConnectionManager.class);
        
        assertDoesNotThrow(() -> newMonitorHealthCheckPool(manager).run());
    }
    
    private Runnable newMonitorHealthCheckPool(AsyncClientConnectionManager manager)
        throws Exception {
        Class<?> clazz =
            Class.forName("com.alibaba.nacos.naming.misc.HttpClientManager$MonitorHealthCheckPool");
        Constructor<?> constructor =
            clazz.getDeclaredConstructor(AsyncClientConnectionManager.class);
        constructor.setAccessible(true);
        return (Runnable) constructor.newInstance(manager);
    }
}
