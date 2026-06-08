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
import com.alibaba.nacos.lock.model.LockKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ReentrantAtomicLock.
 *
 * @author DHX
 * @date 2026/05/31
 */
class ReentrantAtomicLockTest {
    
    private ReentrantAtomicLock lock;
    
    @BeforeEach
    void setUp() {
        lock = new ReentrantAtomicLock("test-key");
    }
    
    @Test
    void testBasicLockAndUnlock() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 30000);
        assertTrue(lock.tryLock(lockInfo));
        assertEquals("owner-1", lock.getOwner());
        assertEquals(1, lock.getReentrantCount());
        
        assertTrue(lock.unLock(lockInfo));
        assertNull(lock.getOwner());
        assertEquals(0, lock.getReentrantCount());
    }
    
    @Test
    void testReentrantLock() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 30000);
        
        assertTrue(lock.tryLock(lockInfo));
        assertEquals(1, lock.getReentrantCount());
        
        assertTrue(lock.tryLock(lockInfo));
        assertEquals(2, lock.getReentrantCount());
        
        assertTrue(lock.tryLock(lockInfo));
        assertEquals(3, lock.getReentrantCount());
        
        assertTrue(lock.unLock(lockInfo));
        assertEquals(2, lock.getReentrantCount());
        
        assertTrue(lock.unLock(lockInfo));
        assertEquals(1, lock.getReentrantCount());
        
        assertTrue(lock.unLock(lockInfo));
        assertEquals(0, lock.getReentrantCount());
        assertNull(lock.getOwner());
    }
    
    @Test
    void testUnlockUnderflowProtection() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 30000);
        
        assertTrue(lock.tryLock(lockInfo));
        assertEquals(1, lock.getReentrantCount());
        
        assertTrue(lock.unLock(lockInfo));
        assertEquals(0, lock.getReentrantCount());
        assertNull(lock.getOwner());
        
        // After full release, owner is null, unLock correctly returns false
        assertFalse(lock.unLock(lockInfo));
        assertEquals(0, lock.getReentrantCount());
        assertNull(lock.getOwner());
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
    void testUnlockWhenLockNotHeldShouldReturnFalse() {
        // Lock is freshly created, never locked — reentrantCount == 0, owner == null
        LockInfo emptyOwnerInfo = new LockInfo();
        emptyOwnerInfo.setKey(new LockKey("test", "test-key"));
        emptyOwnerInfo.setOwner(null);
        emptyOwnerInfo.setEndTime(System.currentTimeMillis() + 30000);
        
        // doUnLock is called because lockInfo.owner is null (bypasses owner check in unLock).
        // BUG: doUnLock returns true when reentrantCount == 0, but the lock isn't held.
        Boolean result = lock.unLock(emptyOwnerInfo);
        
        assertFalse(result, "unlocking an unheld lock should return false");
        assertNull(lock.getOwner());
        assertEquals(0, lock.getReentrantCount());
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
    void testRemoveWaiterByConnection() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 30000);
        lock.tryLock(lockInfo);
        
        LockInfo waiter1 = createLockInfo("owner-2", "conn-2", 30000);
        waiter1.setWaitTime(5000L);
        lock.addWaiter(waiter1);
        
        LockInfo waiter2 = createLockInfo("owner-3", "conn-3", 30000);
        waiter2.setWaitTime(5000L);
        lock.addWaiter(waiter2);
        
        assertEquals(2, lock.getWaitQueue().size());
        
        lock.removeWaiterByConnection("conn-2");
        assertEquals(1, lock.getWaitQueue().size());
        assertEquals("owner-3", lock.getWaitQueue().get(0).getOwner());
    }
    
    @Test
    void testForceRelease() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 30000);
        lock.tryLock(lockInfo);
        lock.tryLock(lockInfo);
        assertEquals(2, lock.getReentrantCount());
        
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
    
    private LockInfo createLockInfo(String owner, String connectionId, long expireTime) {
        LockInfo lockInfo = new LockInfo();
        lockInfo.setOwner(owner);
        lockInfo.setConnectionId(connectionId);
        lockInfo.setEndTime(System.currentTimeMillis() + expireTime);
        return lockInfo;
    }
}
