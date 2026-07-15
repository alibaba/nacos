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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LongPollingConnectionMetricsCollectorTest {
    
    private MockedStatic<ApplicationUtils> applicationUtilsMockedStatic;
    
    private LongPollingService longPollingService;
    
    @BeforeEach
    void setUp() {
        longPollingService = Mockito.mock(LongPollingService.class);
        ReflectionTestUtils.setField(longPollingService, "allSubs",
            new ConcurrentLinkedQueue<>());
        applicationUtilsMockedStatic = Mockito.mockStatic(ApplicationUtils.class);
        applicationUtilsMockedStatic
            .when(() -> ApplicationUtils.getBean(LongPollingService.class))
            .thenReturn(longPollingService);
    }
    
    @AfterEach
    void tearDown() {
        applicationUtilsMockedStatic.close();
    }
    
    @Test
    void testGetName() {
        LongPollingConnectionMetricsCollector collector =
            new LongPollingConnectionMetricsCollector();
        assertEquals("long_polling", collector.getName());
    }
    
    @Test
    void testGetTotalCount() {
        LongPollingConnectionMetricsCollector collector =
            new LongPollingConnectionMetricsCollector();
        assertEquals(0, collector.getTotalCount());
    }
    
    @Test
    void testGetCountForIp() {
        LongPollingConnectionMetricsCollector collector =
            new LongPollingConnectionMetricsCollector();
        assertEquals(0, collector.getCountForIp("127.0.0.1"));
    }
}
