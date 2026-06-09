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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for WaitEntry.
 *
 * @author DHX
 * @date 2026/06/01
 */
class WaitEntryTest {
    
    @Test
    void testIsExpiredWhenDeadlinePassed() {
        WaitEntry entry = new WaitEntry("owner-1", "conn-1",
            System.currentTimeMillis() - 1000, System.currentTimeMillis() - 500);
        assertTrue(entry.isExpired());
    }
    
    @Test
    void testIsExpiredWhenDeadlineNotPassed() {
        WaitEntry entry = new WaitEntry("owner-1", "conn-1",
            System.currentTimeMillis(), System.currentTimeMillis() + 10000);
        assertFalse(entry.isExpired());
    }
    
    @Test
    void testIsExpiredWhenDeadlineZero() {
        // deadline=0 means no wait timeout, should never expire
        WaitEntry entry = new WaitEntry("owner-1", "conn-1",
            System.currentTimeMillis(), 0);
        assertFalse(entry.isExpired());
    }
    
    @Test
    void testConstructorAndGetters() {
        long now = System.currentTimeMillis();
        WaitEntry entry = new WaitEntry("owner-1", "conn-1", now, now + 5000);
        
        assertEquals("owner-1", entry.getOwner());
        assertEquals("conn-1", entry.getConnectionId());
        assertEquals(now, entry.getEnqueueTime());
        assertEquals(now + 5000, entry.getWaitDeadline());
    }
    
    @Test
    void testSetters() {
        WaitEntry entry = new WaitEntry();
        entry.setOwner("owner-1");
        entry.setConnectionId("conn-1");
        entry.setEnqueueTime(1000L);
        entry.setWaitDeadline(2000L);
        
        assertEquals("owner-1", entry.getOwner());
        assertEquals("conn-1", entry.getConnectionId());
        assertEquals(1000L, entry.getEnqueueTime());
        assertEquals(2000L, entry.getWaitDeadline());
    }
}
