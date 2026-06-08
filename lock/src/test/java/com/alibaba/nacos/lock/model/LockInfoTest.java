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

package com.alibaba.nacos.lock.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LockInfo 单元测试.
 *
 * @author DHX
 * @date 2026/06/06
 */
class LockInfoTest {
    
    @Test
    void testDefaultValues() {
        LockInfo info = new LockInfo();
        assertNull(info.getKey());
        assertNull(info.getOwner());
        assertNull(info.getConnectionId());
        assertNull(info.getParams());
        assertNull(info.getEndTime());
        assertNull(info.getWaitTime());
        assertFalse(info.isWaiterRetry());
    }
    
    @Test
    void testSettersAndGetters() {
        LockInfo info = new LockInfo();
        LockKey key = new LockKey("REENTRANT", "test-key");
        
        info.setKey(key);
        info.setOwner("owner-1");
        info.setConnectionId("conn-1");
        info.setEndTime(12345L);
        info.setWaitTime(5000L);
        info.setWaiterRetry(true);
        
        assertEquals(key, info.getKey());
        assertEquals("owner-1", info.getOwner());
        assertEquals("conn-1", info.getConnectionId());
        assertEquals(12345L, info.getEndTime());
        assertEquals(5000L, info.getWaitTime());
        assertTrue(info.isWaiterRetry());
    }
    
    @Test
    void testWaiterRetryDefaultFalse() {
        LockInfo info = new LockInfo();
        assertFalse(info.isWaiterRetry(),
            "waiterRetry 默认应为 false");
    }
    
    @Test
    void testWaiterRetryToggle() {
        LockInfo info = new LockInfo();
        info.setWaiterRetry(true);
        assertTrue(info.isWaiterRetry());
        info.setWaiterRetry(false);
        assertFalse(info.isWaiterRetry());
    }
}
