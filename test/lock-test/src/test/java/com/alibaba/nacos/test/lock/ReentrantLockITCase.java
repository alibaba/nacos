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
 * 可重入锁集成测试.
 *
 * @author DHX
 * @date 2026/05/30
 */
public class ReentrantLockITCase extends BaseLockITCase {

    @Test
    @DisplayName("IT-011: 可重入锁 - 同一客户端可以多次获取")
    void testReentrantLockMultipleAcquire() throws Exception {
        String key = generateUniqueKey("reentrant-test");
        LockInstance lock = createReentrantLock(key);
        lock.setOwner("client-reentrant");

        // 第一次加锁
        Boolean result1 = lockService.lock(lock);
        assertTrue(result1, "First lock should succeed");

        // 第二次加锁（同一客户端）
        Boolean result2 = lockService.lock(lock);
        assertTrue(result2, "Second lock should succeed (reentrant)");

        // 第三次加锁
        Boolean result3 = lockService.lock(lock);
        assertTrue(result3, "Third lock should succeed (reentrant)");

        // 第一次解锁
        Boolean unlock1 = lockService.unLock(lock);
        assertTrue(unlock1, "First unlock should succeed");

        // 第二次解锁
        Boolean unlock2 = lockService.unLock(lock);
        assertTrue(unlock2, "Second unlock should succeed");

        // 第三次解锁（完全释放）
        Boolean unlock3 = lockService.unLock(lock);
        assertTrue(unlock3, "Third unlock should succeed");
    }

    @Test
    @DisplayName("IT-012: 非可重入锁 - 同一客户端不能重复获取")
    void testNonReentrantLockRejectsReentry() throws Exception {
        String key = generateUniqueKey("non-reentrant-test");
        LockInstance lock = createNonReentrantLock(key);
        lock.setOwner("client-non-reentrant");

        // 第一次加锁
        Boolean result1 = lockService.lock(lock);
        assertTrue(result1, "First lock should succeed");

        // 第二次加锁（同一客户端，应该失败）
        Boolean result2 = lockService.lock(lock);
        assertFalse(result2, "Second lock should fail (non-reentrant)");

        // 解锁
        Boolean unlock = lockService.unLock(lock);
        assertTrue(unlock, "Unlock should succeed");

        // 解锁后再次加锁应该成功
        Boolean result3 = lockService.lock(lock);
        assertTrue(result3, "Lock after unlock should succeed");

        // 清理
        lockService.unLock(lock);
    }

}
