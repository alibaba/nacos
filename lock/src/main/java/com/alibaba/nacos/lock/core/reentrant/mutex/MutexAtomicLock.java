/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
 * Legacy non-reentrant mutex lock (backward-compatible).
 *
 * <p>Preserved for backward compatibility with {@code NACOS_LOCK} type.
 * Does not track owner - any client can release any lock.
 * For new code, use {@link ReentrantAtomicLock} or {@link NonReentrantAtomicLock}.
 *
 * @author 985492783@qq.com
 * @description MutexAtomicLock
 * @date 2023/7/10 15:33
 */
public class MutexAtomicLock extends AbstractAtomicLock {
    
    @Serial
    private static final long serialVersionUID = -3460985546826855524L;
    
    private static final int EMPTY = 0;
    
    private static final String LEGACY_OWNER = "legacy-migrated";
    
    /**
     * Backward-compatible field for Hessian deserialization of old snapshots.
     *
     * <p>The old MutexAtomicLock used {@code AtomicInteger state} (EMPTY=0 / FULL=1)
     * to track lock status. Hessian serializes AtomicInteger as its int value,
     * so this Integer field can receive the old value during deserialization.
     *
     * <p>After deserialization, {@link #migrateFromLegacy()} converts this to the
     * new owner-based model. Marked transient so it is NOT included in future
     * snapshot writes.
     */
    @SuppressWarnings("unused")
    private transient Integer state;
    
    private transient boolean legacyMigrated;
    
    public MutexAtomicLock(String key) {
        super(key);
    }
    
    /**
     * Migrate legacy snapshot data to the new owner-based model.
     *
     * <p>Called by {@link com.alibaba.nacos.lock.persistence.NacosLockSnapshotOperation}
     * after Hessian deserialization to convert old EMPTY/FULL state into the new format.
     */
    public void migrateFromLegacy() {
        if (legacyMigrated) {
            return;
        }
        legacyMigrated = true;
        if (state == null) {
            return;
        }
        // migrateFromLegacy 在反序列化后调用，需自行加锁
        lock.lock();
        try {
            if (state == EMPTY) {
                owner = null;
                reentrantCount = 0;
                expiredTimestamp = 0;
            } else {
                reentrantCount = 1;
                owner = LEGACY_OWNER;
            }
            state = null;
        } finally {
            lock.unlock();
        }
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
