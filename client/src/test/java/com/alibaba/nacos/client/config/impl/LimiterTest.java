/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.config.impl;

import com.google.common.cache.Cache;
import com.google.common.util.concurrent.RateLimiter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimiterTest {
    
    @Test
    void testIsLimit() {
        String keyId = "a";
        //For initiating.
        assertFalse(Limiter.isLimit(keyId));
        long start = System.currentTimeMillis();
        for (int j = 0; j < 5; j++) {
            assertFalse(Limiter.isLimit(keyId));
        }
        long elapse = System.currentTimeMillis() - start;
        // assert  < limit 5qps
        assertTrue(elapse > 980);
    }
    
    @Test
    void testIsLimitConstructor() {
        // class loading; ensure default ctor is exercised for coverage.
        assertNotNull(new Limiter());
    }
    
    @Test
    void testIsLimitTriggered() throws Exception {
        // Replace cache with a low-rate limiter so a rapid burst triggers the limited path
        Field cacheField = Limiter.class.getDeclaredField("CACHE");
        cacheField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<String, RateLimiter> cache = (Cache<String, RateLimiter>) cacheField.get(null);
        cache.invalidateAll();
        // Use very small rate so subsequent acquires get limited
        RateLimiter slowLimiter = RateLimiter.create(0.0001);
        cache.put("limited-key", slowLimiter);
        // Drain initial permit; subsequent call should be limited
        slowLimiter.tryAcquire();
        assertTrue(Limiter.isLimit("limited-key"));
    }
    
}
