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

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogRateLimiterTest {
    
    @Test
    void admitsOnlyOneLogPerIntervalAndHandlesOverflow() {
        assertThrows(IllegalArgumentException.class, () -> new LogRateLimiter(0L));
        assertTrue(new LogRateLimiter(1L).tryAcquire());
        LogRateLimiter limiter = new LogRateLimiter(1L);
        long interval = TimeUnit.MILLISECONDS.toNanos(1L);
        
        assertTrue(limiter.tryAcquireAt(100L));
        assertFalse(limiter.tryAcquireAt(100L + interval - 1L));
        assertTrue(limiter.tryAcquireAt(100L + interval));
        
        LogRateLimiter overflow = new LogRateLimiter(1L);
        assertTrue(overflow.tryAcquireAt(Long.MAX_VALUE - 1L));
        assertFalse(overflow.tryAcquireAt(Long.MAX_VALUE - 1L));
    }
}
