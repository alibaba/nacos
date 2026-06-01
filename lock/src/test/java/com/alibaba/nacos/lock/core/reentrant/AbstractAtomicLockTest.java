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

package com.alibaba.nacos.lock.core.reentrant;

import com.alibaba.nacos.lock.core.reentrant.mutex.MutexAtomicLock;
import com.alibaba.nacos.lock.model.LockInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for AbstractAtomicLock.
 *
 * @author DHX
 * @date 2026/05/31
 */
class AbstractAtomicLockTest {

    private MutexAtomicLock lock;

    @BeforeEach
    void setUp() {
        lock = new MutexAtomicLock("test-key");
    }

    @Test
    void testAddWaiter() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 5000);
        int position = lock.addWaiter(lockInfo);
        assertEquals(0, position);
        assertEquals(1, lock.getWaitQueue().size());
    }

    @Test
    void testRemoveWaiterByConnection() {
        LockInfo waiter1 = createLockInfo("owner-1", "conn-1", 5000);
        LockInfo waiter2 = createLockInfo("owner-2", "conn-2", 5000);
        LockInfo waiter3 = createLockInfo("owner-3", "conn-1", 5000);

        lock.addWaiter(waiter1);
        lock.addWaiter(waiter2);
        lock.addWaiter(waiter3);

        assertEquals(3, lock.getWaitQueue().size());

        lock.removeWaiterByConnection("conn-1");

        assertEquals(1, lock.getWaitQueue().size());
        assertEquals("owner-2", lock.getWaitQueue().get(0).getOwner());
    }

    @Test
    void testRemoveWaiterByConnectionEmpty() {
        lock.removeWaiterByConnection("conn-1");
        assertEquals(0, lock.getWaitQueue().size());
    }

    @Test
    void testPollFirstWaiter() {
        LockInfo waiter1 = createLockInfo("owner-1", "conn-1", 5000);
        LockInfo waiter2 = createLockInfo("owner-2", "conn-2", 5000);

        lock.addWaiter(waiter1);
        lock.addWaiter(waiter2);

        var entry = lock.pollFirstWaiter();
        assertEquals("owner-1", entry.getOwner());
        assertEquals(1, lock.getWaitQueue().size());
    }

    @Test
    void testPollFirstWaiterEmpty() {
        var entry = lock.pollFirstWaiter();
        assertEquals(null, entry);
    }

    @Test
    void testClearWaiters() {
        LockInfo waiter1 = createLockInfo("owner-1", "conn-1", 5000);
        LockInfo waiter2 = createLockInfo("owner-2", "conn-2", 5000);

        lock.addWaiter(waiter1);
        lock.addWaiter(waiter2);

        assertEquals(2, lock.getWaitQueue().size());

        lock.clearWaiters();

        assertEquals(0, lock.getWaitQueue().size());
    }

    @Test
    void testAddWaiterDeduplicatesByOwnerAndConnection() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 5000);

        lock.addWaiter(lockInfo);
        lock.addWaiter(lockInfo);
        lock.addWaiter(lockInfo);

        assertEquals(1, lock.getWaitQueue().size(),
                "addWaiter() should deduplicate by owner+connectionId");
    }

    @Test
    void testAddWaiterDedupUpdatesDeadline() {
        LockInfo first = createLockInfo("owner-1", "conn-1", 1000);
        LockInfo second = createLockInfo("owner-1", "conn-1", 9000);

        lock.addWaiter(first);
        long originalDeadline = lock.getWaitQueue().get(0).getWaitDeadline();

        lock.addWaiter(second);
        long updatedDeadline = lock.getWaitQueue().get(0).getWaitDeadline();

        assertEquals(1, lock.getWaitQueue().size());
        assertTrue(updatedDeadline > originalDeadline,
                "Dedup should update the deadline to the newer value");
    }

    @Test
    void testAddWaiterNoDedupForDifferentOwner() {
        LockInfo info1 = createLockInfo("owner-1", "conn-1", 5000);
        LockInfo info2 = createLockInfo("owner-2", "conn-1", 5000);
        LockInfo info3 = createLockInfo("owner-1", "conn-2", 5000);

        lock.addWaiter(info1);
        lock.addWaiter(info2);
        lock.addWaiter(info3);

        assertEquals(3, lock.getWaitQueue().size(),
                "Different owner or connection should not be deduplicated");
    }

    // ==================== renew tests ====================

    @Test
    void testRenewSuccess() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 5000);
        lock.tryLock(lockInfo);

        long originalExpiry = lock.getExpiredTimestamp();
        LockInfo renewInfo = new LockInfo();
        renewInfo.setOwner("owner-1");
        renewInfo.setEndTime(originalExpiry + 10000);

        assertTrue(lock.renew(renewInfo));
        assertEquals(originalExpiry + 10000, lock.getExpiredTimestamp());
    }

    @Test
    void testRenewNullLockInfo() {
        assertFalse(lock.renew(null));
    }

    @Test
    void testRenewNullOwner() {
        LockInfo renewInfo = new LockInfo();
        renewInfo.setOwner(null);
        assertFalse(lock.renew(renewInfo));
    }

    @Test
    void testRenewOwnerMismatch() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 5000);
        lock.tryLock(lockInfo);

        LockInfo renewInfo = new LockInfo();
        renewInfo.setOwner("owner-2");
        renewInfo.setEndTime(System.currentTimeMillis() + 10000);
        assertFalse(lock.renew(renewInfo));
    }

    // ==================== isClear tests ====================

    @Test
    void testIsClearWhenNoOwner() {
        assertTrue(lock.isClear());
    }

    @Test
    void testIsClearWhenOwned() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 5000);
        lock.tryLock(lockInfo);
        assertFalse(lock.isClear());
    }

    @Test
    void testIsClearAfterUnlock() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 5000);
        lock.tryLock(lockInfo);
        lock.unLock(lockInfo);
        assertTrue(lock.isClear());
    }

    // ==================== forceRelease edge cases ====================

    @Test
    void testForceReleaseWhenNotHeld() {
        assertFalse(lock.forceRelease());
    }

    // ==================== autoExpire edge cases ====================

    @Test
    void testAutoExpireNotExpired() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 30000);
        lock.tryLock(lockInfo);
        assertFalse(lock.autoExpire());
        assertEquals("owner-1", lock.getOwner());
    }

    @Test
    void testAutoExpireNoExpiry() {
        // No lock held, no expiry set
        assertFalse(lock.autoExpire());
    }

    // ==================== null LockInfo tests ====================

    @Test
    void testTryLockNullLockInfo() {
        assertFalse(lock.tryLock(null));
    }

    @Test
    void testUnLockNullLockInfo() {
        assertFalse(lock.unLock(null));
    }

    // ==================== hasWaiters / peekFirstWaiter ====================

    @Test
    void testHasWaitersEmpty() {
        assertFalse(lock.hasWaiters());
    }

    @Test
    void testHasWaitersNonEmpty() {
        lock.addWaiter(createLockInfo("owner-1", "conn-1", 5000));
        assertTrue(lock.hasWaiters());
    }

    @Test
    void testPeekFirstWaiterExpiredInMiddle() throws InterruptedException {
        LockInfo expired = createLockInfo("owner-1", "conn-1", 50);
        LockInfo valid = createLockInfo("owner-2", "conn-2", 5000);

        lock.addWaiter(expired);
        lock.addWaiter(valid);
        Thread.sleep(100);

        // Peek should skip expired entry
        var entry = lock.peekFirstWaiter();
        assertEquals("owner-2", entry.getOwner());
        assertEquals(1, lock.getWaitQueue().size());
    }

    // ==================== removeStaleWaiter ====================

    @Test
    void testRemoveStaleWaiter() {
        lock.addWaiter(createLockInfo("owner-1", "conn-1", 5000));
        lock.addWaiter(createLockInfo("owner-2", "conn-2", 5000));

        assertTrue(lock.removeStaleWaiter("owner-1"));
        assertEquals(1, lock.getWaitQueue().size());
        assertEquals("owner-2", lock.getWaitQueue().get(0).getOwner());
    }

    @Test
    void testRemoveStaleWaiterNotFound() {
        lock.addWaiter(createLockInfo("owner-1", "conn-1", 5000));
        assertFalse(lock.removeStaleWaiter("owner-99"));
        assertEquals(1, lock.getWaitQueue().size());
    }

    // ==================== drainAllWaiters ====================

    @Test
    void testDrainAllWaiters() {
        lock.addWaiter(createLockInfo("owner-1", "conn-1", 5000));
        lock.addWaiter(createLockInfo("owner-2", "conn-2", 5000));
        lock.addWaiter(createLockInfo("owner-3", "conn-3", 5000));

        var drained = lock.drainAllWaiters();
        assertEquals(3, drained.size());
        assertEquals(0, lock.getWaitQueue().size());
    }

    @Test
    void testDrainAllWaitersEmpty() {
        var drained = lock.drainAllWaiters();
        assertEquals(0, drained.size());
    }

    // ==================== removeExpiredWaiters ====================

    @Test
    void testRemoveExpiredWaiters() throws InterruptedException {
        LockInfo expired1 = createLockInfo("owner-1", "conn-1", 50);
        LockInfo expired2 = createLockInfo("owner-2", "conn-2", 50);
        LockInfo valid = createLockInfo("owner-3", "conn-3", 5000);

        lock.addWaiter(expired1);
        lock.addWaiter(expired2);
        lock.addWaiter(valid);
        Thread.sleep(100);

        var expired = lock.removeExpiredWaiters();
        assertEquals(2, expired.size());
        assertEquals(1, lock.getWaitQueue().size());
        assertEquals("owner-3", lock.getWaitQueue().get(0).getOwner());
    }

    // ==================== initTransientFields ====================

    @Test
    void testInitTransientFieldsWhenNull() {
        // Simulate deserialization: lock field is null
        lock.initTransientFields();
        // Should not throw, and lock should still work
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 5000);
        assertTrue(lock.tryLock(lockInfo));
    }

    @Test
    void testWaiterExpiration() throws InterruptedException {
        LockInfo expiredWaiter = createLockInfo("owner-1", "conn-1", 50);
        LockInfo validWaiter = createLockInfo("owner-2", "conn-2", 5000);

        lock.addWaiter(expiredWaiter);
        Thread.sleep(100);
        lock.addWaiter(validWaiter);

        var entry = lock.pollFirstWaiter();
        assertEquals("owner-2", entry.getOwner());
    }

    private LockInfo createLockInfo(String owner, String connectionId, long waitTimeMs) {
        LockInfo lockInfo = new LockInfo();
        lockInfo.setOwner(owner);
        lockInfo.setConnectionId(connectionId);
        lockInfo.setWaitTimeMs(waitTimeMs);
        lockInfo.setEndTime(System.currentTimeMillis() + 30000);
        return lockInfo;
    }
}
