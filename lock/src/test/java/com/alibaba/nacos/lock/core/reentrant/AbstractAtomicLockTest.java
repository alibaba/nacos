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

import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.lock.core.reentrant.mutex.MutexAtomicLock;
import com.alibaba.nacos.lock.model.LockInfo;
import com.alibaba.nacos.lock.model.WaitEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void testTryLockAsQueueHeadAcquiresAndRemovesHead() {
        LockInfo waiter1 = createLockInfo("owner-1", "conn-1", 5000);
        LockInfo waiter2 = createLockInfo("owner-2", "conn-2", 5000);
        lock.addWaiter(waiter1);
        lock.addWaiter(waiter2);
        
        LockResult result = lock.tryLockAsQueueHead(waiter1);
        
        assertTrue(result.isSuccess());
        assertEquals("owner-1", lock.getOwner());
        assertEquals(1, lock.getWaitQueue().size());
        assertEquals("owner-2", lock.getWaitQueue().get(0).getOwner());
    }
    
    @Test
    void testTryLockAsQueueHeadRejectsNonHead() {
        LockInfo waiter1 = createLockInfo("owner-1", "conn-1", 5000);
        LockInfo waiter2 = createLockInfo("owner-2", "conn-2", 5000);
        lock.addWaiter(waiter1);
        lock.addWaiter(waiter2);
        
        LockResult result = lock.tryLockAsQueueHead(waiter2);
        
        assertFalse(result.isSuccess());
        assertNull(lock.getOwner());
        assertEquals(2, lock.getWaitQueue().size());
        assertEquals("owner-1", lock.getWaitQueue().get(0).getOwner());
        assertEquals("owner-2", lock.getWaitQueue().get(1).getOwner());
    }
    
    @Test
    void testTryLockAsQueueHeadKeepsHeadWhenLockStillHeld() {
        LockInfo owner = createLockInfo("owner-0", "conn-0", 5000);
        LockInfo waiter = createLockInfo("owner-1", "conn-1", 5000);
        lock.tryLock(owner);
        lock.addWaiter(waiter);
        
        LockResult result = lock.tryLockAsQueueHead(waiter);
        
        assertFalse(result.isSuccess());
        assertTrue(result.isWaiting());
        assertEquals("owner-0", lock.getOwner());
        assertEquals(1, lock.getWaitQueue().size());
        assertEquals("owner-1", lock.getWaitQueue().get(0).getOwner());
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
    
    // ==================== M3: null owner bypasses unlock verification ====================
    
    @Test
    void testUnLockWithNullOwnerReleasesAnyLock() {
        LockInfo lockInfo = createLockInfo("real-owner", "conn-1", 5000);
        lock.tryLock(lockInfo);
        assertEquals("real-owner", lock.getOwner());
        
        // Another client sends RELEASE with null owner — bypasses owner check
        LockInfo nullOwnerInfo = new LockInfo();
        nullOwnerInfo.setOwner(null);
        nullOwnerInfo.setKey(lockInfo.getKey());
        assertTrue(lock.unLock(nullOwnerInfo),
            "null owner should NOT be able to release someone else's lock");
        assertNull(lock.getOwner(), "Lock released by unauthenticated request");
    }
    
    // ==================== forceRelease connectionId 清除 ====================
    
    @Test
    @DisplayName("forceRelease() 应清除 connectionId")
    void testForceReleaseClearsConnectionId() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 30000);
        lock.tryLock(lockInfo);
        assertEquals("conn-1", lock.getConnectionId());
        
        lock.forceRelease();
        
        assertNull(lock.getOwner());
        assertEquals(0, lock.getReentrantCount());
        assertEquals(0, lock.getExpiredTimestamp());
        assertNull(lock.getConnectionId(),
            "forceRelease() 应清除 connectionId");
    }
    
    @Test
    @DisplayName("forceRelease 后 connectionId 已清除，不会误匹配")
    void testForceReleaseClearsConnectionIdNoStaleMatch() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 30000);
        lock.tryLock(lockInfo);
        
        lock.forceRelease();
        assertTrue(lock.isClear());
        
        assertNull(lock.getConnectionId(),
            "forceRelease 后 connectionId 应为 null");
        
        assertFalse(lock.forceRelease(),
            "对已释放的锁再次 forceRelease 应返回 false");
    }
    
    // ==================== tryLockAsQueueHead deadline 保持 ====================
    
    @Test
    @DisplayName("tryLockAsQueueHead 重试失败时不应重置等待 deadline")
    void testTryLockAsQueueHeadDoesNotResetDeadline() throws InterruptedException {
        LockInfo holder = createLockInfo("owner-0", "conn-0", 30000);
        lock.tryLock(holder);
        
        LockInfo waiter = createLockInfo("owner-1", "conn-1", 5000);
        lock.addWaiter(waiter);
        
        WaitEntry head = lock.getWaitQueue().get(0);
        long originalDeadline = head.getWaitDeadline();
        
        Thread.sleep(100);
        
        LockInfo retryInfo = createLockInfo("owner-1", "conn-1", 5000);
        retryInfo.setWaitTime(5000L);
        LockResult result = lock.tryLockAsQueueHead(retryInfo);
        
        assertFalse(result.isSuccess());
        assertTrue(result.isWaiting());
        
        WaitEntry headAfter = lock.getWaitQueue().get(0);
        long newDeadline = headAfter.getWaitDeadline();
        
        assertEquals(originalDeadline, newDeadline,
            "tryLockAsQueueHead 重试失败时不应重置 deadline");
    }
    
    @Test
    @DisplayName("tryLockAsQueueHead 重试不延长 deadline，等待者正确过期")
    void testDeadlineNotResetWaiterExpiresCorrectly() {
        LockInfo holder = createLockInfo("owner-0", "conn-0", 30000);
        lock.tryLock(holder);
        
        LockInfo waiter = createLockInfo("owner-1", "conn-1", 100);
        lock.addWaiter(waiter);
        
        for (int i = 0; i < 5; i++) {
            LockInfo retryInfo = createLockInfo("owner-1", "conn-1", 100);
            retryInfo.setWaitTime(100L);
            lock.tryLockAsQueueHead(retryInfo);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        assertFalse(lock.hasWaiters(),
            "等待者应已过期（原始 deadline 为入队后 100ms）");
    }
    
    // ==================== 过期等待者静默丢弃 ====================
    
    @Test
    @DisplayName("过期等待者被 peekFirstWaiter 静默丢弃")
    void testExpiredWaitersDiscardedWithoutNotification() {
        LockInfo holder = createLockInfo("owner-0", "conn-0", 30000);
        lock.tryLock(holder);
        
        LockInfo expiredWaiter = createLockInfo("owner-expired", "conn-expired", 1000);
        lock.addWaiter(expiredWaiter);
        lock.getWaitQueue().get(0).setWaitDeadline(System.currentTimeMillis() - 1000);
        
        LockInfo validWaiter = createLockInfo("owner-valid", "conn-valid", 10000);
        lock.addWaiter(validWaiter);
        
        assertEquals(2, lock.getWaitQueue().size());
        
        WaitEntry first = lock.peekFirstWaiter();
        assertNotNull(first);
        assertEquals("owner-valid", first.getOwner());
        
        assertEquals(1, lock.getWaitQueue().size(),
            "过期等待者被静默移除");
    }
    
    @Test
    @DisplayName("pollFirstWaiter 跳过中间的过期条目")
    void testPollFirstWaiterDiscardsExpiredMiddleEntries() {
        LockInfo holder = createLockInfo("owner-0", "conn-0", 30000);
        lock.tryLock(holder);
        
        lock.addWaiter(createLockInfo("owner-1", "conn-1", 10000));
        lock.addWaiter(createLockInfo("owner-2", "conn-2", 10000));
        lock.addWaiter(createLockInfo("owner-3", "conn-3", 10000));
        assertEquals(3, lock.getWaitQueue().size());
        
        lock.getWaitQueue().get(0).setWaitDeadline(System.currentTimeMillis() - 2000);
        lock.getWaitQueue().get(1).setWaitDeadline(System.currentTimeMillis() - 1000);
        
        WaitEntry entry = lock.pollFirstWaiter();
        assertNotNull(entry);
        assertEquals("owner-3", entry.getOwner());
        
        assertEquals(0, lock.getWaitQueue().size());
    }
    
    // ==================== removeWaiter(owner, connectionId) ====================
    
    @Test
    @DisplayName("removeWaiter 按 owner+connectionId 精确移除")
    void testRemoveWaiterByOwnerAndConnection() {
        lock.addWaiter(createLockInfo("owner-1", "conn-1", 5000));
        lock.addWaiter(createLockInfo("owner-2", "conn-2", 5000));
        lock.addWaiter(createLockInfo("owner-1", "conn-3", 5000));
        
        assertTrue(lock.removeStaleWaiter("owner-1"));
        assertEquals(2, lock.getWaitQueue().size());
        // 第一个 owner-1 被移除，owner-1:conn-3 仍在
        assertEquals("owner-2", lock.getWaitQueue().get(0).getOwner());
        assertEquals("owner-1", lock.getWaitQueue().get(1).getOwner());
        assertEquals("conn-3", lock.getWaitQueue().get(1).getConnectionId());
    }
    
    @Test
    @DisplayName("removeWaiter 找不到匹配时返回 false")
    void testRemoveWaiterNotFound() {
        lock.addWaiter(createLockInfo("owner-1", "conn-1", 5000));
        assertFalse(lock.removeStaleWaiter("owner-99"));
        assertEquals(1, lock.getWaitQueue().size());
    }
    
    @Test
    @DisplayName("removeWaiter 空队列返回 false")
    void testRemoveWaiterEmptyQueue() {
        assertFalse(lock.removeStaleWaiter("owner-1"));
    }
    
    // ==================== tryLockAsQueueHead 边界情况 ====================
    
    @Test
    @DisplayName("tryLockAsQueueHead 传 null 返回 fail")
    void testTryLockAsQueueHeadNull() {
        LockResult result = lock.tryLockAsQueueHead(null);
        assertFalse(result.isSuccess());
    }
    
    @Test
    @DisplayName("tryLockAsQueueHead 队列为空时返回 fail")
    void testTryLockAsQueueHeadEmptyQueue() {
        LockInfo info = createLockInfo("owner-1", "conn-1", 5000);
        LockResult result = lock.tryLockAsQueueHead(info);
        assertFalse(result.isSuccess());
    }
    
    @Test
    @DisplayName("tryLockAsQueueHead 锁被持有且 waitTime<=0 时返回 fail")
    void testTryLockAsQueueHeadLockHeldNoWait() {
        LockInfo holder = createLockInfo("owner-0", "conn-0", 30000);
        lock.tryLock(holder);
        
        LockInfo waiter = createLockInfo("owner-1", "conn-1", 0);
        lock.addWaiter(waiter);
        
        // waitTime=0, 锁被持有 → 返回 fail 而非 waiting
        LockResult result = lock.tryLockAsQueueHead(waiter);
        assertFalse(result.isSuccess());
        assertFalse(result.isWaiting());
    }
    
    // ==================== forceRelease 清除所有字段 ====================
    
    @Test
    @DisplayName("forceRelease 清除所有状态字段")
    void testForceReleaseClearsAllFields() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 30000);
        lock.tryLock(lockInfo);
        
        lock.forceRelease();
        
        assertNull(lock.getOwner());
        assertEquals(0, lock.getReentrantCount());
        assertEquals(0, lock.getExpiredTimestamp());
        assertNull(lock.getConnectionId());
        assertTrue(lock.isClear());
    }
    
    // ==================== autoExpire 过期后清除所有字段 ====================
    
    @Test
    @DisplayName("autoExpire 过期后清除所有字段")
    void testAutoExpireClearsAllFields() {
        LockInfo lockInfo = createLockInfo("owner-1", "conn-1", 0);
        lockInfo.setEndTime(System.currentTimeMillis() - 1000);
        assertTrue(lock.tryLock(lockInfo));
        
        assertTrue(lock.autoExpire());
        assertNull(lock.getOwner());
        assertEquals(0, lock.getReentrantCount());
        assertEquals(0, lock.getExpiredTimestamp());
        assertNull(lock.getConnectionId());
    }
    
    private LockInfo createLockInfo(String owner, String connectionId, long waitTime) {
        LockInfo lockInfo = new LockInfo();
        lockInfo.setOwner(owner);
        lockInfo.setConnectionId(connectionId);
        lockInfo.setWaitTime(waitTime);
        lockInfo.setEndTime(System.currentTimeMillis() + 30000);
        return lockInfo;
    }
}
