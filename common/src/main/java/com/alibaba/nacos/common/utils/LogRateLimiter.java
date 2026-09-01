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

package com.alibaba.nacos.common.utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe monotonic admission gate for repetitive diagnostic logs.
 *
 * @author Nacos
 */
public final class LogRateLimiter {
    
    private static final long NEVER_ACQUIRED = Long.MIN_VALUE;
    
    private final long intervalNanos;
    
    private final AtomicLong nextAllowedNanos = new AtomicLong(NEVER_ACQUIRED);
    
    /**
     * Create one limiter.
     *
     * @param intervalMillis minimum interval between admitted logs
     */
    public LogRateLimiter(long intervalMillis) {
        if (intervalMillis <= 0L) {
            throw new IllegalArgumentException("Log interval must be positive");
        }
        intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMillis);
    }
    
    /**
     * Attempt to admit the current log event.
     *
     * @return whether the caller should write the log
     */
    public boolean tryAcquire() {
        return tryAcquireAt(System.nanoTime());
    }
    
    boolean tryAcquireAt(long nowNanos) {
        long next = nextAllowedNanos.get();
        if (next != NEVER_ACQUIRED && nowNanos < next) {
            return false;
        }
        long candidate = nowNanos > Long.MAX_VALUE - intervalNanos
            ? Long.MAX_VALUE : nowNanos + intervalNanos;
        return nextAllowedNanos.compareAndSet(next, candidate);
    }
}
