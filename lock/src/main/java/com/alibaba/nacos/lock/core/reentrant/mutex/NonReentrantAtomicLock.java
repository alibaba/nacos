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

import com.alibaba.nacos.lock.core.reentrant.AbstractAtomicLock;
import com.alibaba.nacos.lock.model.LockInfo;

import java.io.Serial;

/**
 * Non-reentrant distributed lock implementation.
 *
 * <p>Unlike {@link ReentrantAtomicLock}, the same owner cannot acquire the lock
 * more than once. A second attempt by the same owner returns false.
 * This helps detect reentrancy bugs early.
 *
 * @author DHX
 * @date 2026/05/29
 */
public class NonReentrantAtomicLock extends AbstractAtomicLock {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    public NonReentrantAtomicLock(String key) {
        super(key);
    }
    
    @Override
    protected Boolean doTryLock(LockInfo lockInfo) {
        if (owner == null) {
            owner = lockInfo.getOwner();
            connectionId = lockInfo.getConnectionId();
            reentrantCount = 1;
            expiredTimestamp = lockInfo.getEndTime();
            return true;
        }
        return false;
    }
    
    @Override
    protected Boolean doUnLock(LockInfo lockInfo) {
        if (owner == null) {
            return false;
        }
        owner = null;
        connectionId = null;
        reentrantCount = 0;
        expiredTimestamp = 0;
        return true;
    }
}
