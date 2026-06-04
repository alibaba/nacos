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
 * Reentrant distributed lock implementation.
 *
 * <p>The same owner can acquire the lock multiple times. A reentrant count
 * tracks how many times the lock has been acquired. The lock is fully released
 * only when the count drops to zero.
 *
 * @author DHX
 * @date 2026/05/29
 */
public class ReentrantAtomicLock extends AbstractAtomicLock {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    public ReentrantAtomicLock(String key) {
        super(key);
    }
    
    @Override
    protected Boolean doTryLock(LockInfo lockInfo) {
        String requestOwner = lockInfo.getOwner();
        if (getOwner() == null) {
            setOwner(requestOwner);
            setConnectionId(lockInfo.getConnectionId());
            setReentrantCount(1);
            setExpiredTimestamp(lockInfo.getEndTime());
            return true;
        }
        if (requestOwner.equals(getOwner())) {
            setReentrantCount(getReentrantCount() + 1);
            setConnectionId(lockInfo.getConnectionId());
            setExpiredTimestamp(lockInfo.getEndTime());
            return true;
        }
        return false;
    }
    
    @Override
    protected Boolean doUnLock(LockInfo lockInfo) {
        if (getReentrantCount() <= 0) {
            return false;
        }
        setReentrantCount(getReentrantCount() - 1);
        if (getReentrantCount() == 0) {
            setOwner(null);
            setConnectionId(null);
            setExpiredTimestamp(0);
        }
        return true;
    }
}
