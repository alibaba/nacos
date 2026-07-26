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

package com.alibaba.nacos.naming.core.v2.pojo;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthCheckInstancePublishInfoTest {
    
    @Test
    void testHealthCheckStateAndCounters() {
        HealthCheckInstancePublishInfo instance = new HealthCheckInstancePublishInfo("1.1.1.1",
            8848);
        instance.setLastHeartBeatTime(123L);
        instance.initHealthCheck();
        
        assertEquals(123L, instance.getLastHeartBeatTime());
        assertTrue(instance.tryStartCheck());
        assertFalse(instance.tryStartCheck());
        instance.finishCheck();
        assertTrue(instance.tryStartCheck());
        instance.getOkCount().set(3);
        instance.getFailCount().set(5);
        instance.resetOkCount();
        instance.resetFailCount();
        instance.setCheckRt(100L);
        
        assertEquals(0, instance.getOkCount().get());
        assertEquals(0, instance.getFailCount().get());
        Object healthCheckStatus = ReflectionTestUtils.getField(instance, "healthCheckStatus");
        assertEquals(100L, ReflectionTestUtils.getField(healthCheckStatus, "checkRt"));
    }
}
