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

package com.alibaba.nacos.config.server.monitor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MetricsMonitorTest {
    
    @Test
    void testGetConfigCountMonitor() {
        assertNotNull(MetricsMonitor.getConfigCountMonitor());
    }
    
    @Test
    void testGetNotifyTaskMonitor() {
        assertNotNull(MetricsMonitor.getNotifyTaskMonitor());
    }
    
    @Test
    void testGetNotifyClientTaskMonitor() {
        assertNotNull(MetricsMonitor.getNotifyClientTaskMonitor());
    }
    
    @Test
    void testGetFuzzySearchMonitor() {
        assertNotNull(MetricsMonitor.getFuzzySearchMonitor());
    }
    
    @Test
    void testGetConfigSubscriberMonitor() {
        assertNotNull(MetricsMonitor.getConfigSubscriberMonitor("v1"));
        assertNotNull(MetricsMonitor.getConfigSubscriberMonitor("v2"));
    }
    
    @Test
    void testGetConfigChangeCount() {
        assertNotNull(MetricsMonitor.getConfigChangeCount());
    }
    
    @Test
    void testGetIllegalArgumentException() {
        assertNotNull(MetricsMonitor.getIllegalArgumentException());
    }
    
    @Test
    void testGetNacosException() {
        assertNotNull(MetricsMonitor.getNacosException());
    }
    
    @Test
    void testGetUnhealthException() {
        assertNotNull(MetricsMonitor.getUnhealthException());
    }
}
