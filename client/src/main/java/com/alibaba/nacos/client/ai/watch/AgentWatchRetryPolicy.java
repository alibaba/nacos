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

package com.alibaba.nacos.client.ai.watch;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Bounded retry delay policy for pending and dirty Agent Watch refreshes.
 *
 * @author Nacos
 */
interface AgentWatchRetryPolicy {
    
    /**
     * Calculate the next retry delay.
     *
     * @param failureCount consecutive failure count, starting at one
     * @param watchKey canonical Watch key
     * @return delay in milliseconds
     */
    long nextDelayMillis(int failureCount, String watchKey);
    
    /**
     * Exponential retry with bounded random jitter.
     */
    final class Jittered implements AgentWatchRetryPolicy {
        
        private final long minimumDelayMillis;
        
        private final long maximumDelayMillis;
        
        Jittered(long minimumDelayMillis, long maximumDelayMillis) {
            if (minimumDelayMillis < 1 || maximumDelayMillis < minimumDelayMillis) {
                throw new IllegalArgumentException("Invalid Agent Watch retry delay bounds");
            }
            this.minimumDelayMillis = minimumDelayMillis;
            this.maximumDelayMillis = maximumDelayMillis;
        }
        
        @Override
        public long nextDelayMillis(int failureCount, String watchKey) {
            if (minimumDelayMillis == maximumDelayMillis) {
                return minimumDelayMillis;
            }
            int exponent = Math.min(Math.max(0, failureCount - 1), 20);
            long exponential;
            if (minimumDelayMillis > maximumDelayMillis >> exponent) {
                exponential = maximumDelayMillis;
            } else {
                exponential = Math.min(maximumDelayMillis, minimumDelayMillis << exponent);
            }
            long lower = Math.max(1L, exponential * 4L / 5L);
            long upper = Math.max(lower, Math.min(maximumDelayMillis, exponential * 6L / 5L));
            return lower == upper ? lower
                : ThreadLocalRandom.current().nextLong(lower, upper + 1L);
        }
    }
}
