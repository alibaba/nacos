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

package com.alibaba.nacos.test.lock;

import com.alibaba.nacos.api.lock.model.LockInstance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 基础锁操作集成测试.
 *
 * @author DHX
 * @date 2026/05/30
 */
public class BasicLockITCase extends BaseLockITCase {

    @Test
    @DisplayName("IT-001: 基础加锁解锁流程")
    void testBasicLockUnlockFlow() throws Exception {
        String key = generateUniqueKey("basic-test");
        LockInstance lock = createReentrantLock(key);

        // 加锁
        Boolean lockResult = lockService.lock(lock);
        assertTrue(lockResult, "Lock acquisition should succeed");

        // 解锁
        Boolean unlockResult = lockService.unLock(lock);
        assertTrue(unlockResult, "Unlock should succeed");
    }

    @Test
    @DisplayName("IT-002: 重复解锁应失败")
    void testDuplicateUnlockShouldFail() throws Exception {
        String key = generateUniqueKey("duplicate-unlock-test");
        LockInstance lock = createReentrantLock(key);

        // 加锁
        Boolean lockResult = lockService.lock(lock);
        assertTrue(lockResult);

        // 第一次解锁
        Boolean unlockResult1 = lockService.unLock(lock);
        assertTrue(unlockResult1);

        // 第二次解锁应失败
        Boolean unlockResult2 = lockService.unLock(lock);
        assertFalse(unlockResult2, "Second unlock should fail");
    }

    @Test
    @DisplayName("IT-003: 不同客户端不能解锁他人的锁")
    void testCannotUnlockOthersLock() throws Exception {
        String key = generateUniqueKey("ownership-test");

        // 客户端 A 加锁
        LockInstance lockA = createReentrantLock(key);
        Boolean lockResultA = lockService.lock(lockA);
        assertTrue(lockResultA);

        // 客户端 B 尝试解锁 A 的锁（lockB 已有不同的随机 UUID owner）
        LockInstance lockB = createReentrantLock(key);
        Boolean unlockResultB = lockService.unLock(lockB);
        assertFalse(unlockResultB, "Client B should not be able to unlock A's lock");

        // 清理：A 解锁
        Boolean unlockResultA = lockService.unLock(lockA);
        assertTrue(unlockResultA);
    }

    @Test
    @DisplayName("IT-004: 锁过期后自动释放")
    void testLockAutoExpiration() throws Exception {
        String key = generateUniqueKey("expiration-test");
        LockInstance lock = createReentrantLock(key);
        lock.setExpiredTime(2000L); // 2秒过期

        // 加锁
        Boolean lockResult = lockService.lock(lock);
        assertTrue(lockResult);

        // 等待锁过期
        Thread.sleep(3000);

        // 另一个客户端应该能获取锁
        LockInstance lock2 = createReentrantLock(key);
        Boolean lockResult2 = lockService.lock(lock2);
        assertTrue(lockResult2, "Should be able to acquire expired lock");

        // 清理
        lockService.unLock(lock2);
    }

    @Test
    @DisplayName("IT-005: 续租锁 - renew 延长锁过期时间")
    void testLockRenew() throws Exception {
        String key = generateUniqueKey("renew-test");
        LockInstance lock = createReentrantLock(key);
        lock.setExpiredTime(2000L); // 2秒过期

        // 加锁
        Boolean lockResult = lockService.lock(lock);
        assertTrue(lockResult, "Lock acquisition should succeed");

        // 续租（延长到5秒）
        lock.setExpiredTime(5000L);
        Boolean renewResult = lockService.renew(lock);
        assertTrue(renewResult, "Renew should succeed");

        // 等待原始过期时间（2秒 + 1秒缓冲）
        Thread.sleep(3000);

        // 锁应该仍然存在（因为续租了）
        LockInstance lock2 = createReentrantLock(key);
        Boolean lockResult2 = lockService.lock(lock2);
        assertFalse(lockResult2, "Lock should still be held after renew");

        // 等待续租后的过期时间（再等3秒）
        Thread.sleep(3000);

        // 锁现在应该过期了
        LockInstance lock3 = createReentrantLock(key);
        Boolean lockResult3 = lockService.lock(lock3);
        assertTrue(lockResult3, "Lock should expire after renewed time");

        // 清理
        lockService.unLock(lock3);
    }

    @Test
    @DisplayName("IT-006: 不同客户端不能续租他人的锁")
    void testCannotRenewOthersLock() throws Exception {
        String key = generateUniqueKey("renew-ownership-test");

        // 客户端 A 加锁
        LockInstance lockA = createReentrantLock(key);
        Boolean lockResultA = lockService.lock(lockA);
        assertTrue(lockResultA);

        // 客户端 B 尝试续租 A 的锁（lockB 已有不同的随机 UUID owner）
        LockInstance lockB = createReentrantLock(key);
        lockB.setExpiredTime(30000L);
        Boolean renewResultB = lockService.renew(lockB);
        assertFalse(renewResultB, "Client B should not be able to renew A's lock");

        // 清理：A 解锁
        Boolean unlockResultA = lockService.unLock(lockA);
        assertTrue(unlockResultA);
    }

    @Test
    @DisplayName("IT-007: 互斥性 - 锁被持有时其他客户端无法获取")
    void testLockMutualExclusion() throws Exception {
        String key = generateUniqueKey("mutex-test");

        // 客户端 A 获取锁
        LockInstance lockA = createReentrantLock(key);
        Boolean resultA = lockService.lock(lockA);
        assertTrue(resultA, "Client A should acquire the lock");

        // 客户端 B 尝试获取同一把锁（应该失败）
        LockInstance lockB = createReentrantLock(key);
        Boolean resultB = lockService.lock(lockB);
        assertFalse(resultB, "Client B should fail when A holds the lock");

        // 客户端 A 释放锁
        lockService.unLock(lockA);

        // 客户端 B 现在可以获取锁
        Boolean resultB2 = lockService.lock(lockB);
        assertTrue(resultB2, "Client B should acquire after A releases");

        // 清理
        lockService.unLock(lockB);
    }

    @Test
    @DisplayName("IT-008: 锁过期后再次解锁失败")
    void testExpiredLockUnlockRejected() throws Exception {
        String key = generateUniqueKey("expired-unlock-fail-test");

        // 1. 获取锁，设置短过期时间
        LockInstance lock = createReentrantLock(key);
        lock.setExpiredTime(2000L); // 2秒过期
        Boolean acquired = lockService.lock(lock);
        assertTrue(acquired, "Lock should be acquired successfully");

        // 2. 等待锁过期
        Thread.sleep(3000);

        // 3. 尝试解锁 -> 服务端应该拒绝（锁已过期）
        Boolean unlockResult = lockService.unLock(lock);
        assertFalse(unlockResult,
                "Unlock should fail (return false) when lock has expired. "
                        + "BUG: Server is incorrectly allowing unlock of expired locks (returns true).");
    }
}