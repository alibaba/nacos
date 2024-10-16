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

package com.alibaba.nacos.sys.env;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class OperatingSystemBeanManagerTest {
    
    @Test
    void testGetOperatingSystemBean() {
        assertNotNull(OperatingSystemBeanManager.getOperatingSystemBean());
    }
    
    @Test
    void testGetSystemCpuUsage() {
        assertDoesNotThrow(OperatingSystemBeanManager::getSystemCpuUsage);
    }
    
    @Test
    void testGetProcessCpuUsage() {
        assertDoesNotThrow(OperatingSystemBeanManager::getProcessCpuUsage);
    }
    
    @Test
    void testGetTotalPhysicalMem() {
        assertDoesNotThrow(OperatingSystemBeanManager::getTotalPhysicalMem);
    }
    
    @Test
    void testGetFreePhysicalMem() {
        assertDoesNotThrow(OperatingSystemBeanManager::getFreePhysicalMem);
    }
    
    @Test
    void testLoadOneWithException() {
        assertNull(ReflectionTestUtils.invokeMethod(OperatingSystemBeanManager.class, "loadOne",
                Collections.singletonList("com.alibaba.nacos.NonExistClass")));
    }
    
    @Test
    void testDeduceMethodWithException() {
        assertNull(
                ReflectionTestUtils.invokeMethod(OperatingSystemBeanManager.class, "deduceMethod", "nonExistMethod"));
    }
}