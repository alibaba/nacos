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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseMonitorTest {
    
    @BeforeEach
    void setUp() {
        ResponseMonitor.refresh();
    }
    
    @Test
    void testAddConfigTimeLessThan50() {
        ResponseMonitor.addConfigTime(10);
        String result = ResponseMonitor.getStringForPrint();
        assertNotNull(result);
        assertTrue(result.contains("getConfig monitor"));
    }
    
    @Test
    void testAddConfigTimeAllBuckets() {
        ResponseMonitor.addConfigTime(10);
        ResponseMonitor.addConfigTime(60);
        ResponseMonitor.addConfigTime(150);
        ResponseMonitor.addConfigTime(300);
        ResponseMonitor.addConfigTime(700);
        ResponseMonitor.addConfigTime(1500);
        ResponseMonitor.addConfigTime(2500);
        ResponseMonitor.addConfigTime(5000);
        String result = ResponseMonitor.getStringForPrint();
        assertNotNull(result);
        assertTrue(result.contains("0-50ms:"));
    }
    
    @Test
    void testRefresh() {
        ResponseMonitor.addConfigTime(10);
        ResponseMonitor.refresh();
        ResponseMonitor.addConfigTime(20);
        String result = ResponseMonitor.getStringForPrint();
        assertNotNull(result);
    }
}
