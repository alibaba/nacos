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

package com.alibaba.nacos.ai.service.a2a.migration;

import com.alibaba.nacos.ai.service.a2a.migration.A2aMigrationControlStore.VersionedValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Temporary compatibility support for migrating Nacos 3.0-3.2 A2A data.
 *
 * <p>TODO(remove in 4.0): remove after the historical A2A migration window closes.</p>
 *
 * @author Nacos
 */
public final class A2aMigrationLease implements AutoCloseable {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(A2aMigrationLease.class);
    
    private final A2aMigrationControlStore controlStore;
    
    private final String owner;
    
    private final long leaseDurationMillis;
    
    private final LongSupplier clock;
    
    private final AtomicBoolean owned = new AtomicBoolean(true);
    
    private volatile String currentMd5;
    
    A2aMigrationLease(A2aMigrationControlStore controlStore, String owner,
        long leaseDurationMillis, LongSupplier clock, String currentMd5) {
        this.controlStore = controlStore;
        this.owner = owner;
        this.leaseDurationMillis = leaseDurationMillis;
        this.clock = clock;
        this.currentMd5 = currentMd5;
    }
    
    /**
     * Renew the lease using Config compare-and-set.
     *
     * @return {@code true} when this handle still owns the durable lease
     */
    public synchronized boolean renew() {
        if (!owned.get()) {
            return false;
        }
        long now = clock.getAsLong();
        A2aMigrationLeaseRecord replacement = A2aMigrationLeaseRecord.of(owner,
            now + leaseDurationMillis);
        try {
            controlStore.compareAndSetLease(replacement, currentMd5);
        } catch (Exception e) {
            LOGGER.warn("A2A migration lease renewal result is uncertain; rereading lease", e);
        }
        VersionedValue<A2aMigrationLeaseRecord> observed = readLease();
        if (!ownsObservedLease(observed, now)) {
            owned.set(false);
            LOGGER.warn("A2A migration reconciliation lease was lost by owner {}", owner);
            return false;
        }
        currentMd5 = observed.getMd5();
        return true;
    }
    
    /**
     * Fail the caller immediately after lease loss.
     */
    public void assertOwned() {
        if (!owned.get()) {
            throw new IllegalStateException("A2A migration reconciliation lease was lost");
        }
    }
    
    /**
     * Return whether this handle still owns the lease according to its latest CAS/read.
     *
     * @return local ownership state
     */
    public boolean isOwned() {
        return owned.get();
    }
    
    @Override
    public synchronized void close() {
        if (!owned.compareAndSet(true, false)) {
            return;
        }
        try {
            controlStore.compareAndSetLease(A2aMigrationLeaseRecord.of(owner, 0), currentMd5);
        } catch (Exception e) {
            LOGGER.warn("Failed to release A2A migration reconciliation lease", e);
        }
    }
    
    private VersionedValue<A2aMigrationLeaseRecord> readLease() {
        try {
            return controlStore.readLease();
        } catch (Exception e) {
            LOGGER.warn("Failed to verify A2A migration reconciliation lease", e);
            return null;
        }
    }
    
    private boolean ownsObservedLease(VersionedValue<A2aMigrationLeaseRecord> lease, long now) {
        return lease != null && lease.getValue().isValid()
            && owner.equals(lease.getValue().getOwner())
            && lease.getValue().getExpiresAt() > now;
    }
}
