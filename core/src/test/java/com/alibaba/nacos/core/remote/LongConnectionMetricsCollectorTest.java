/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LongConnectionMetricsCollectorTest {
    
    private MockedStatic<ApplicationUtils> applicationUtilsMock;
    
    @Mock
    private ConnectionManager connectionManager;
    
    private LongConnectionMetricsCollector collector;
    
    @BeforeEach
    void setUp() {
        applicationUtilsMock = Mockito.mockStatic(ApplicationUtils.class);
        applicationUtilsMock.when(() -> ApplicationUtils.getBean(ConnectionManager.class))
            .thenReturn(connectionManager);
        collector = new LongConnectionMetricsCollector();
    }
    
    @AfterEach
    void tearDown() {
        if (applicationUtilsMock != null) {
            applicationUtilsMock.close();
        }
    }
    
    @Test
    void testGetName() {
        assertEquals("long_connection", collector.getName());
    }
    
    @Test
    void testGetTotalCount() {
        when(connectionManager.currentClientsCount()).thenReturn(10);
        assertEquals(10, collector.getTotalCount());
    }
    
    @Test
    void testGetCountForIpWhenPresent() {
        Map<String, AtomicInteger> map = new HashMap<>();
        map.put("192.168.1.1", new AtomicInteger(3));
        when(connectionManager.getConnectionForClientIp()).thenReturn(map);
        assertEquals(3, collector.getCountForIp("192.168.1.1"));
    }
    
    @Test
    void testGetCountForIpWhenAbsent() {
        when(connectionManager.getConnectionForClientIp()).thenReturn(new HashMap<>());
        assertEquals(0, collector.getCountForIp("10.0.0.1"));
    }
}
