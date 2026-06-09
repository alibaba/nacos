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

package com.alibaba.nacos.lock.core.reentrant;

import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.lock.model.LockInfo;
import com.alibaba.nacos.lock.model.WaitEntry;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstract atomic lock with owner tracking, reentrant count, and wait queue.
 *
 * <p>Subclasses implement {@link #doTryLock(LockInfo)} and {@link #doUnLock(LockInfo)}
 * to define reentrant vs non-reentrant behavior. Common logic (owner check,
 * expiry, wait queue, renew, forceRelease) is handled in this base class.
 *
 * @author 985492783@qq.com
 * @description AtomicLock
 * @date 2023/7/10 14:50
 */
public abstract class AbstractAtomicLock implements AtomicLockService, Serializable {
    
    private static final long serialVersionUID = -3460985546856855524L;
    
    private final String key;
    
    protected String owner;
    
    protected String connectionId;
    
    protected int reentrantCount;
    
    protected long expiredTimestamp;
    
    private final LinkedList<WaitEntry> waitQueue = new LinkedList<>();
    
    /**
     * ReentrantLock for thread-safe access to lock state.
     *
     * <p>Replaces synchronized keyword to provide better fairness control and
     * avoid priority inversion when LockExpireScanner holds the monitor while
     * clients try to acquire locks.
     *
     * <p>Marked as transient because ReentrantLock is not serializable.
     * Must be reinitialized after deserialization via readObject().
     */
    protected transient ReentrantLock lock = new ReentrantLock();
    
    public AbstractAtomicLock(String key) {
        this.key = key;
    }
    
    /**
     * Initialize transient fields after deserialization.
     *
     * <p>Must be called after Hessian (or any non-standard) deserialization,
     * because Hessian does not invoke readObject(). The transient ReentrantLock
     * field must be reinitialized to avoid NullPointerException.
     */
    public void initTransientFields() {
        if (this.lock == null) {
            this.lock = new ReentrantLock();
        }
    }
    
    @Override
    public String getKey() {
        return key;
    }
    
    public String getOwner() {
        lock.lock();
        try {
            return owner;
        } finally {
            lock.unlock();
        }
    }
    
    public String getConnectionId() {
        lock.lock();
        try {
            return connectionId;
        } finally {
            lock.unlock();
        }
    }
    
    public int getReentrantCount() {
        lock.lock();
        try {
            return reentrantCount;
        } finally {
            lock.unlock();
        }
    }
    
    public long getExpiredTimestamp() {
        lock.lock();
        try {
            return expiredTimestamp;
        } finally {
            lock.unlock();
        }
    }
    
    public List<WaitEntry> getWaitQueue() {
        lock.lock();
        try {
            return new ArrayList<>(waitQueue);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Remove all waiters associated with the given connection.
     *
     * @param connectionId the gRPC connection ID
     */
    public void removeWaiterByConnection(String connectionId) {
        lock.lock();
        try {
            waitQueue.removeIf(w -> connectionId.equals(w.getConnectionId()));
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Add a waiter to the queue when lock acquisition fails.
     *
     * @param lockInfo lock request info with connectionId and waitTime
     * @return position in queue (0-based)
     */
    public int addWaiter(LockInfo lockInfo) {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            long deadline = lockInfo.getWaitTime() > 0 ? now + lockInfo.getWaitTime() : 0;
            for (int i = 0; i < waitQueue.size(); i++) {
                WaitEntry existing = waitQueue.get(i);
                if (existing.getOwner().equals(lockInfo.getOwner())
                    && existing.getConnectionId().equals(lockInfo.getConnectionId())) {
                    existing.setWaitDeadline(deadline);
                    return i;
                }
            }
            WaitEntry entry =
                new WaitEntry(lockInfo.getOwner(), lockInfo.getConnectionId(), now, deadline);
            waitQueue.add(entry);
            return waitQueue.size() - 1;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Peek at the first non-expired waiter without removing it from the queue.
     * Expired entries encountered are discarded.
     *
     * @return first non-expired waiter, or null if queue is empty
     */
    public WaitEntry peekFirstWaiter() {
        lock.lock();
        try {
            return peekFirstWaiterUnderLock();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Check if there are any non-expired waiters in the queue.
     *
     * <p>Note: this method discards expired entries from the head of the queue
     * as a side effect of the peek operation.
     *
     * @return true if at least one non-expired waiter exists
     */
    public boolean hasWaiters() {
        return peekFirstWaiter() != null;
    }
    
    /**
     * Remove and return the first waiter from the queue.
     *
     * @return first waiter, or null if queue is empty
     */
    public WaitEntry pollFirstWaiter() {
        lock.lock();
        try {
            while (!waitQueue.isEmpty()) {
                WaitEntry entry = waitQueue.poll();
                if (!entry.isExpired()) {
                    return entry;
                }
            }
            return null;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Atomically acquire the lock only if the requester is still the queue head.
     *
     * <p>This keeps queue-head verification, lock acquisition, and waiter removal in
     * one critical section. Otherwise a concurrent cancel or cleanup could change the
     * queue between verification and removal.
     *
     * @param lockInfo retry request from a queued waiter
     * @return structured lock result
     */
    public LockResult tryLockAsQueueHead(LockInfo lockInfo) {
        lock.lock();
        try {
            if (lockInfo == null) {
                return LockResult.fail("LockInfo cannot be null");
            }
            WaitEntry head = peekFirstWaiterUnderLock();
            if (!isSameWaiter(head, lockInfo)) {
                return LockResult.fail("Only queue head waiter can retry");
            }
            
            autoExpire();
            Boolean acquired = doTryLock(lockInfo);
            if (acquired) {
                waitQueue.poll();
                return LockResult.success(reentrantCount);
            }
            
            if (lockInfo.getWaitTime() > 0) {
                return LockResult.waiting(0);
            }
            return LockResult.fail("Lock is held by another owner");
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Remove the first waiter entry matching the given owner.
     *
     * @param owner the owner to remove
     * @return true if an entry was removed
     */
    public boolean removeStaleWaiter(String owner) {
        lock.lock();
        try {
            Iterator<WaitEntry> iterator = waitQueue.iterator();
            while (iterator.hasNext()) {
                WaitEntry entry = iterator.next();
                if (entry.getOwner().equals(owner)) {
                    iterator.remove();
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Remove the first waiter entry matching the given owner and connection.
     *
     * @param owner the owner to remove
     * @param connectionId the connection ID to remove
     * @return true if an entry was removed
     */
    public boolean removeWaiter(String owner, String connectionId) {
        lock.lock();
        try {
            Iterator<WaitEntry> iterator = waitQueue.iterator();
            while (iterator.hasNext()) {
                WaitEntry entry = iterator.next();
                if (Objects.equals(entry.getOwner(), owner)
                    && Objects.equals(entry.getConnectionId(), connectionId)) {
                    iterator.remove();
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
    
    private WaitEntry peekFirstWaiterUnderLock() {
        while (!waitQueue.isEmpty()) {
            WaitEntry entry = waitQueue.peek();
            if (!entry.isExpired()) {
                return entry;
            }
            waitQueue.poll();
        }
        return null;
    }
    
    private boolean isSameWaiter(WaitEntry waiter, LockInfo lockInfo) {
        return waiter != null && Objects.equals(waiter.getOwner(), lockInfo.getOwner())
            && Objects.equals(waiter.getConnectionId(), lockInfo.getConnectionId());
    }
    
    /**
     * Clear all waiters from the queue.
     */
    public void clearWaiters() {
        lock.lock();
        try {
            waitQueue.clear();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Remove and return all waiters from the queue.
     *
     * @return list of all waiters (may be empty)
     */
    public List<WaitEntry> drainAllWaiters() {
        lock.lock();
        try {
            List<WaitEntry> result = new ArrayList<>(waitQueue);
            waitQueue.clear();
            return result;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public Boolean autoExpire() {
        lock.lock();
        try {
            if (expiredTimestamp > 0 && System.currentTimeMillis() > expiredTimestamp) {
                owner = null;
                reentrantCount = 0;
                expiredTimestamp = 0;
                connectionId = null;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public Boolean isClear() {
        lock.lock();
        try {
            return owner == null;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public Boolean renew(LockInfo lockInfo) {
        lock.lock();
        try {
            if (lockInfo == null || lockInfo.getOwner() == null) {
                return false;
            }
            if (!lockInfo.getOwner().equals(owner)) {
                return false;
            }
            expiredTimestamp = lockInfo.getEndTime();
            return true;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public Boolean forceRelease() {
        lock.lock();
        try {
            if (owner == null) {
                return false;
            }
            owner = null;
            reentrantCount = 0;
            expiredTimestamp = 0;
            connectionId = null;
            return true;
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Subclass hook for lock acquisition logic. Called after common checks.
     *
     * @param lockInfo lock request info
     * @return true if acquired
     */
    protected abstract Boolean doTryLock(LockInfo lockInfo);
    
    /**
     * Subclass hook for unlock logic. Called after owner verification.
     *
     * @param lockInfo lock info with owner
     * @return true if released
     */
    protected abstract Boolean doUnLock(LockInfo lockInfo);
    
    @Override
    public Boolean tryLock(LockInfo lockInfo) {
        lock.lock();
        try {
            if (lockInfo == null) {
                return false;
            }
            autoExpire();
            return doTryLock(lockInfo);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Unlock the lock with owner verification.
     *
     * <p>If lockInfo.getOwner() is null, owner verification is bypassed.
     * This is intentional for system-level releases (e.g., expire scanner,
     * connection cleanup) where the original owner may not be known.
     *
     * @param lockInfo lock info with owner (may be null for system releases)
     * @return true if unlocked successfully, false if owner mismatch or lockInfo is null
     */
    @Override
    public Boolean unLock(LockInfo lockInfo) {
        lock.lock();
        try {
            if (lockInfo == null) {
                return false;
            }
            if (lockInfo.getOwner() != null && !lockInfo.getOwner().equals(owner)) {
                return false;
            }
            return doUnLock(lockInfo);
        } finally {
            lock.unlock();
        }
    }
}
