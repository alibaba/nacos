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

package com.alibaba.nacos.lock.core.reentrant.mutex;

import com.alibaba.nacos.lock.model.LockInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for NonReentrantAtomicLock.
 *
 * @author DHX
 * @date 2026/06/01
 */
class NonReentrantAtomicLockTest {
    
    private NonReentrantAtomicLock lock;
    
    @BeforeEach
    void setUp() {
        lock = new NonReentrantAtomicLock("test-key");
    }
    
    @Test
    void testBasicLockAndUnlock() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 30000);
        assertTrue(lock.tryLock(lockInfo));
        assertEquals("owner-1", lock.getOwner());
        assertEquals("conn-1", lock.getConnectionId());
        assertEquals(1, lock.getReentrantCount());
        
        assertTrue(lock.unLock(lockInfo));
        assertNull(lock.getOwner());
        assertNull(lock.getConnectionId());
        assertEquals(0, lock.getReentrantCount());
    }
    
    @Test
    void testSameOwnerCannotReenter() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 30000);
        assertTrue(lock.tryLock(lockInfo));
        assertFalse(lock.tryLock(lockInfo), "Non-reentrant lock should reject same owner reentry");
        assertEquals(1, lock.getReentrantCount());
    }
    
    @Test
    void testDifferentOwnerCannotLock() {
        LockInfo lockInfo1 = createLockInfo("owner-1", "conn-1", 30000);
        LockInfo lockInfo2 = createLockInfo("owner-2", "conn-2", 30000);
        
        assertTrue(lock.tryLock(lockInfo1));
        assertFalse(lock.tryLock(lockInfo2));
        assertEquals("owner-1", lock.getOwner());
    }
    
    @Test
    void testDifferentOwnerCannotUnlock() {
        LockInfo lockInfo1 = createLockInfo("owner-1", "conn-1", 30000);
        LockInfo lockInfo2 = createLockInfo("owner-2", "conn-2", 30000);
        
        assertTrue(lock.tryLock(lockInfo1));
        assertFalse(lock.unLock(lockInfo2));
        assertEquals("owner-1", lock.getOwner());
        assertEquals(1, lock.getReentrantCount());
    }
    
    @Test
    void testUnlockClearsAllState() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 30000);
        lock.tryLock(lockInfo);
        lock.unLock(lockInfo);
        
        assertNull(lock.getOwner());
        assertNull(lock.getConnectionId());
        assertEquals(0, lock.getReentrantCount());
        assertEquals(0, lock.getExpiredTimestamp());
    }
    
    @Test
    void testForceRelease() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 30000);
        lock.tryLock(lockInfo);
        
        assertTrue(lock.forceRelease());
        assertNull(lock.getOwner());
        assertEquals(0, lock.getReentrantCount());
    }
    
    @Test
    void testAutoExpire() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", -1000);
        lock.tryLock(lockInfo);
        
        assertTrue(lock.autoExpire());
        assertNull(lock.getOwner());
        assertEquals(0, lock.getReentrantCount());
    }
    
    @Test
    void testAutoExpireNotExpired() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 30000);
        lock.tryLock(lockInfo);
        
        assertFalse(lock.autoExpire());
        assertEquals("owner-1", lock.getOwner());
    }
    
    @Test
    void testLockAfterUnlock() {
        LockInfo lockInfo1 = createLockInfo("owner-1", "conn-1", 30000);
        LockInfo lockInfo2 = createLockInfo("owner-2", "conn-2", 30000);
        
        assertTrue(lock.tryLock(lockInfo1));
        assertTrue(lock.unLock(lockInfo1));
        assertTrue(lock.tryLock(lockInfo2));
        assertEquals("owner-2", lock.getOwner());
    }
    
    private LockInfo createLockInfo(String owner, String connectionId, long expireTime) {
        LockInfo lockInfo = new LockInfo();
        lockInfo.setOwner(owner);
        lockInfo.setConnectionId(connectionId);
        lockInfo.setEndTime(System.currentTimeMillis() + expireTime);
        return lockInfo;
    }
}
