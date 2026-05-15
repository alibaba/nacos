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

package com.alibaba.nacos.config.server.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriberStatusTest {
    
    @Test
    void testDefaultConstructor() {
        SubscriberStatus status = new SubscriberStatus();
        assertNull(status.getGroupKey());
        assertNull(status.getMd5());
        assertNull(status.getLastTime());
        assertNull(status.getStatus());
        assertNull(status.getServerIp());
    }
    
    @Test
    void testParameterizedConstructor() {
        SubscriberStatus status = new SubscriberStatus("groupKey", true, "md5", 100L);
        assertEquals("groupKey", status.getGroupKey());
        assertTrue(status.getStatus());
        assertEquals("md5", status.getMd5());
        assertEquals(100L, status.getLastTime());
    }
    
    @Test
    void testSettersAndGetters() {
        SubscriberStatus status = new SubscriberStatus();
        status.setGroupKey("gk");
        status.setMd5("md5val");
        status.setLastTime(200L);
        status.setStatus(false);
        status.setServerIp("127.0.0.1");
        
        assertEquals("gk", status.getGroupKey());
        assertEquals("md5val", status.getMd5());
        assertEquals(200L, status.getLastTime());
        assertEquals(false, status.getStatus());
        assertEquals("127.0.0.1", status.getServerIp());
    }
}
