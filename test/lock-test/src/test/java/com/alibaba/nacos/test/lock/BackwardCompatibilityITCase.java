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

import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.lock.factory.SimpleLockFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 向后兼容性测试.
 *
 * @author DHX
 * @date
 */
public class BackwardCompatibilityITCase extends BaseLockITCase {
    @Test
    @DisplayName("向后兼容性: 老客户端不传 owner 应使用 connectionId 作为默认值")
    void testBackwardCompatibility_NoOwnerShouldUseConnectionId() throws Exception {
        String key = generateUniqueKey("backward-compat-no-owner");

        // 创建锁，不设置 owner（模拟老客户端）
        LockInstance lock = new LockInstance();
        lock.setKey(key);
        lock.setLockType(LockConstants.REENTRANT_LOCK_TYPE);
        lock.setExpiredTime(30000L);
        // 注意：没有调用 setOwner()

        Boolean lockResult = lockService.lock(lock);

        assertTrue(lockResult,
                "Lock should succeed even without owner. "
                        + "Server should use connectionId as default owner for backward compatibility. "
                        + "If this assertion fails, backward compatibility is broken.");

        lockService.unLock(lock);
    }
}
