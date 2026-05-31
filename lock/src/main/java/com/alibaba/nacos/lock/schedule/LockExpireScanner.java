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

package com.alibaba.nacos.lock.schedule;

import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.remote.request.LockNotificationRequest;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.lock.LockManager;
import com.alibaba.nacos.lock.core.reentrant.AbstractAtomicLock;
import com.alibaba.nacos.lock.core.reentrant.AtomicLockService;
import com.alibaba.nacos.lock.model.LockKey;
import com.alibaba.nacos.lock.model.WaitEntry;
import com.alibaba.nacos.lock.service.LockOperationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Periodically scans for expired locks and notifies waiting clients.
 *
 * <p>When a lock's watchdog timeout expires (client crashed), the lock
 * is released via Raft consensus and waiting clients are notified.
 * Expired wait queue entries are also cleaned up.
 *
 * @author DHX
 * @date 2026/05/29
 */
@Component
public class LockExpireScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(LockExpireScanner.class);

    @Autowired
    private LockManager lockManager;

    @Autowired
    private LockOperationService lockOperationService;

    @Autowired
    private RpcPushService rpcPushService;

    /**
     * Scan for expired locks and clean up wait queue entries periodically.
     */
    @Scheduled(fixedDelayString = "${nacos.lock.expire.scan.interval:1000}")
    public void scanExpiredLocks() {
        try {
            doScan();
        } catch (Exception e) {
            LOGGER.error("Lock: error during expire scan", e);
        }
    }

    private void doScan() {
        for (Map.Entry<LockKey, AtomicLockService> entry : new java.util.HashMap<>(lockManager.showLocks()).entrySet()) {
            LockKey lockKey = entry.getKey();
            AtomicLockService lockService = entry.getValue();

            if (lockService instanceof AbstractAtomicLock) {
                AbstractAtomicLock atomicLock = (AbstractAtomicLock) lockService;

                if (atomicLock.getExpiredTimestamp() > 0
                        && System.currentTimeMillis() > atomicLock.getExpiredTimestamp()) {
                    LOGGER.info("Lock: lock expired, key={}, type={}, releasing via Raft",
                            lockKey.getKey(), lockKey.getLockType());
                    expireLockViaRaft(lockKey, atomicLock);
                    continue;
                }

                cleanExpiredWaitEntries(lockKey, atomicLock);
            }
        }
    }

    private void expireLockViaRaft(LockKey lockKey, AbstractAtomicLock atomicLock) {
        String owner = atomicLock.getOwner();
        if (owner == null) {
            return;
        }
        LockInstance instance = new LockInstance();
        instance.setKey(lockKey.getKey());
        instance.setLockType(lockKey.getLockType());
        instance.setOwner(owner);
        try {
            lockOperationService.expire(instance);
        } catch (Exception e) {
            LOGGER.warn("Lock: failed to expire lock via Raft, key={}", lockKey, e);
        }
    }

    private void cleanExpiredWaitEntries(LockKey lockKey, AbstractAtomicLock atomicLock) {
        List<WaitEntry> expiredWaiters = atomicLock.removeExpiredWaiters();
        for (WaitEntry entry : expiredWaiters) {
            LockNotificationRequest timeout = LockNotificationRequest.timeout(
                    lockKey.getKey(), lockKey.getLockType(), entry.getOwner());
            try {
                rpcPushService.pushWithoutAck(entry.getConnectionId(), timeout);
            } catch (Exception e) {
                LOGGER.warn("Lock: failed to notify expired waiter, connectionId={}", entry.getConnectionId(), e);
            }
        }
    }
}
