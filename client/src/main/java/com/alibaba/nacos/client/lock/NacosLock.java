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

package com.alibaba.nacos.client.lock;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.lock.common.LockConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.model.LockResult;
import com.alibaba.nacos.client.lock.exception.NacosLockException;
import com.alibaba.nacos.client.lock.remote.grpc.LockGrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * JUC Lock implementation backed by Nacos distributed lock service.
 *
 * <p>This lock uses a {@link ThreadLocal} to track the per-thread reentrant count.
 * Only the thread that acquired the lock can release it. Cross-thread lock
 * handoff (e.g. in async/callback scenarios) is not supported.
 *
 * <p>{@link Condition} is not supported. {@link #newCondition()} throws
 * {@link UnsupportedOperationException}.
 *
 * <p><strong>Important:</strong> {@code NacosLock} and {@link LockGrpcClient#lock(LockInstance)}
 * are mutually exclusive APIs for the same lock key. Do not mix them on the same key,
 * as the watchdog registration state may become inconsistent.
 *
 * @author DHX
 * @date 2026/05/29
 */
public class NacosLock implements Lock {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(NacosLock.class);
    
    private static final long DEFAULT_LEASE_TIME_MS = 30000L;
    
    /**
     * Timeout for each waitForNotification poll in the lock acquisition loop.
     *
     * <p>When waiting for a lock, the client polls waitForNotification with this timeout.
     * If no notification arrives within this window, the client re-sends the ACQUIRE request.
     * This is independent of the lease time ({@link #DEFAULT_LEASE_TIME_MS}) and the server
     * wait queue timeout ({@link #DEFAULT_SERVER_WAIT_TIME_MS}).
     */
    private static final long NOTIFICATION_POLL_TIMEOUT_MS = 60000L;
    
    /**
     * Server-side wait queue timeout for indefinite lock acquisition ({@link #lock()}).
     *
     * <p>This tells the server how long to keep the client in its wait queue. When this
     * expires, the server removes the client and sends a TIMEOUT notification, so the
     * client must re-register. A large value (5 minutes) minimizes re-registration
     * overhead and reduces the race window where a lock release notification could be
     * missed between eviction and re-registration.
     *
     * <p>This is NOT the lease time. Lease time is controlled by {@link #DEFAULT_LEASE_TIME_MS}
     * and the watchdog renewal mechanism.
     */
    private static final long DEFAULT_SERVER_WAIT_TIME_MS = 300000L;
    
    private final String key;
    
    private final String lockType;
    
    private final LockGrpcClient grpcClient;
    
    private final NacosLockWatchdog watchdog;
    
    private final String clientId;
    
    private final ThreadLocal<Integer> localReentrantCount = ThreadLocal.withInitial(() -> 0);
    
    /**
     * Guard flag to prevent recursive unlock() calls from the same thread.
     *
     * <p>ThreadLocal guarantees per-thread isolation so concurrent access from different
     * threads is impossible. However, if unlock() triggers a callback or signal handler
     * that recursively calls unlock() on the same thread, the read-modify-write on
     * {@code localReentrantCount} could be corrupted. This flag detects and rejects
     * such recursive calls.
     */
    private final ThreadLocal<Boolean> inUnlock = ThreadLocal.withInitial(() -> Boolean.FALSE);
    
    public NacosLock(String key, String lockType, LockGrpcClient grpcClient,
        NacosLockWatchdog watchdog,
        String clientId) {
        this.key = key;
        this.lockType = lockType;
        this.grpcClient = grpcClient;
        this.watchdog = watchdog;
        this.clientId = clientId;
    }
    
    private String currentOwner() {
        return clientId + ":" + Thread.currentThread().getId();
    }
    
    private LockInstance buildInstance(long expiredTime) {
        LockInstance instance = new LockInstance();
        instance.setKey(key);
        instance.setLockType(lockType);
        instance.setOwner(currentOwner());
        instance.setExpiredTime(expiredTime);
        return instance;
    }
    
    /**
     * Guard against non-reentrant lock reentry on the same thread.
     *
     * <p>For {@code NON_REENTRANT} locks, if the current thread already holds the lock
     * ({@code localReentrantCount > 0}), a second acquisition attempt is immediately
     * rejected with {@link IllegalMonitorStateException}. Without this client-side check,
     * the request would be sent to the server, which rejects it and places the thread in
     * the wait queue, causing self-deadlock (the thread waits for itself to release the lock).
     */
    private void checkReentrantGuard() {
        if (LockConstants.NON_REENTRANT_LOCK_TYPE.equals(lockType)
            && localReentrantCount.get() > 0) {
            throw new IllegalMonitorStateException(
                "Non-reentrant lock does not allow reentry on the same thread, key=" + key);
        }
    }
    
    @Override
    public void lock() {
        checkReentrantGuard();
        boolean firstAttempt = true;
        while (true) {
            try {
                LockInstance instance = buildInstance(-1);
                instance.setWaitTime(DEFAULT_SERVER_WAIT_TIME_MS);
                if (!firstAttempt) {
                    instance.setWaiterRetry(true);
                }
                grpcClient.registerForNotification(key, currentOwner());
                LockResult result = grpcClient.lockWithResult(instance);
                if (result.isSuccess()) {
                    grpcClient.cancelWait(key, currentOwner());
                    localReentrantCount.set(localReentrantCount.get() + 1);
                    if (result.getReentrantCount() == 1) {
                        watchdog.register(key, grpcClient, instance);
                    }
                    return;
                }
                firstAttempt = false;
                grpcClient.waitForNotification(key, currentOwner(), NOTIFICATION_POLL_TIMEOUT_MS);
            } catch (InterruptedException e) {
                // Clear interrupt flag so gRPC cancel request doesn't throw,
                // then restore it after the synchronous server-side cleanup.
                grpcClient.cancelWait(key, lockType, currentOwner());
                Thread.currentThread().interrupt();
                localReentrantCount.remove();
                throw new NacosLockException("Lock interrupted", e);
            } catch (NacosException e) {
                LOGGER.error("Failed to acquire lock, key={}", key, e);
                grpcClient.cancelWait(key, lockType, currentOwner());
                localReentrantCount.remove();
                throw new NacosLockException("Failed to acquire lock: " + key, e);
            }
        }
    }
    
    @Override
    public void lockInterruptibly() throws InterruptedException {
        checkReentrantGuard();
        boolean firstAttempt = true;
        while (true) {
            if (Thread.interrupted()) {
                if (!firstAttempt) {
                    grpcClient.cancelWait(key, lockType, currentOwner());
                }
                throw new InterruptedException();
            }
            try {
                LockInstance instance = buildInstance(-1);
                instance.setWaitTime(DEFAULT_SERVER_WAIT_TIME_MS);
                if (!firstAttempt) {
                    instance.setWaiterRetry(true);
                }
                grpcClient.registerForNotification(key, currentOwner());
                LockResult result = grpcClient.lockWithResult(instance);
                if (result.isSuccess()) {
                    grpcClient.cancelWait(key, currentOwner());
                    localReentrantCount.set(localReentrantCount.get() + 1);
                    if (result.getReentrantCount() == 1) {
                        watchdog.register(key, grpcClient, instance);
                    }
                    return;
                }
                firstAttempt = false;
                grpcClient.waitForNotification(key, currentOwner(), NOTIFICATION_POLL_TIMEOUT_MS);
            } catch (InterruptedException e) {
                grpcClient.cancelWait(key, lockType, currentOwner());
                localReentrantCount.remove();
                throw e;
            } catch (NacosException e) {
                LOGGER.error("Failed to acquire lock, key={}", key, e);
                grpcClient.cancelWait(key, lockType, currentOwner());
                localReentrantCount.remove();
                throw new NacosLockException("Failed to acquire lock: " + key, e);
            }
        }
    }
    
    @Override
    public boolean tryLock() {
        checkReentrantGuard();
        try {
            LockInstance instance = buildInstance(-1);
            LockResult result = grpcClient.lockWithResult(instance);
            if (result.isSuccess()) {
                localReentrantCount.set(localReentrantCount.get() + 1);
                if (result.getReentrantCount() == 1) {
                    watchdog.register(key, grpcClient, instance);
                }
                return true;
            }
            return false;
        } catch (NacosException e) {
            LOGGER.error("Failed to try lock, key={}", key, e);
            return false;
        }
    }
    
    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        checkReentrantGuard();
        long deadline = System.currentTimeMillis() + unit.toMillis(time);
        boolean firstAttempt = true;
        while (true) {
            if (Thread.interrupted()) {
                if (!firstAttempt) {
                    grpcClient.cancelWait(key, lockType, currentOwner());
                }
                throw new InterruptedException();
            }
            LockInstance instance = buildInstance(-1);
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                grpcClient.cancelWait(key, lockType, currentOwner());
                return false;
            }
            instance.setWaitTime(remaining);
            if (!firstAttempt) {
                instance.setWaiterRetry(true);
            }
            try {
                grpcClient.registerForNotification(key, currentOwner());
                LockResult result = grpcClient.lockWithResult(instance);
                if (result.isSuccess()) {
                    grpcClient.cancelWait(key, currentOwner());
                    localReentrantCount.set(localReentrantCount.get() + 1);
                    if (result.getReentrantCount() == 1) {
                        watchdog.register(key, grpcClient, instance);
                    }
                    return true;
                }
                firstAttempt = false;
                grpcClient.waitForNotification(key, currentOwner(), remaining);
            } catch (InterruptedException e) {
                grpcClient.cancelWait(key, lockType, currentOwner());
                localReentrantCount.remove();
                throw e;
            } catch (NacosException e) {
                grpcClient.cancelWait(key, lockType, currentOwner());
                localReentrantCount.remove();
                LOGGER.error("Failed to try lock with timeout, key={}", key, e);
                return false;
            }
        }
    }
    
    @Override
    public void unlock() {
        if (inUnlock.get()) {
            throw new IllegalMonitorStateException("Recursive unlock() detected for key=" + key);
        }
        inUnlock.set(Boolean.TRUE);
        boolean removed = false;
        try {
            int count = localReentrantCount.get();
            if (count <= 0) {
                throw new IllegalMonitorStateException("Current thread does not hold the lock");
            }
            try {
                LockInstance instance = buildInstance(0);
                LockResult result = grpcClient.unLockWithResult(instance);
                if (result.isSuccess()) {
                    localReentrantCount.set(count - 1);
                    if (result.getReentrantCount() == 0) {
                        watchdog.unregister(key);
                        localReentrantCount.remove();
                        removed = true;
                    }
                } else {
                    localReentrantCount.set(0);
                    watchdog.unregister(key);
                    localReentrantCount.remove();
                    removed = true;
                    throw new IllegalMonitorStateException(
                        "Unlock rejected by server, key=" + key + ", msg="
                            + result.getErrorMessage());
                }
            } catch (NacosException e) {
                // Server may have already released the lock — clear client state
                // to prevent the lock from becoming permanently unusable.
                localReentrantCount.set(0);
                watchdog.unregister(key);
                localReentrantCount.remove();
                removed = true;
                LOGGER.error("Failed to unlock, key={}", key, e);
                throw new IllegalStateException("Failed to unlock: " + key, e);
            }
        } finally {
            inUnlock.remove();
            if (!removed && localReentrantCount.get() <= 0) {
                localReentrantCount.remove();
            }
        }
    }
    
    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException(
            "Condition not supported in Nacos distributed lock");
    }
    
    public String getKey() {
        return key;
    }
    
    public String getLockType() {
        return lockType;
    }
}
