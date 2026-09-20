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

package com.alibaba.nacos.client.ai.remote.capability;

import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiCapabilityCacheTest {
    
    private final Map<String, String> identity = Collections.singletonMap("Authorization", "one");
    
    @Test
    void shouldExpireAndSeparateTargetContextSchemeAndIdentity() throws Exception {
        AtomicLong clock = new AtomicLong();
        AiCapabilityCache cache = new AiCapabilityCache(clock::get, 100);
        AtomicInteger loads = new AtomicInteger();
        AiCapabilityCache.Loader loader = () -> {
            loads.incrementAndGet();
            return AiCapabilitySnapshot.unknown();
        };
        cache.get("http://one/nacos", identity, loader);
        cache.get("http://one/nacos", identity, loader);
        assertEquals(1, loads.get());
        cache.get("http://two/nacos", identity, loader);
        cache.get("http://one/custom", identity, loader);
        cache.get("https://one/nacos", identity, loader);
        cache.get("http://one/nacos", Collections.singletonMap("Authorization", "two"), loader);
        assertEquals(5, loads.get());
        clock.set(100);
        cache.get("http://one/nacos", identity, loader);
        assertEquals(6, loads.get());
        cache.invalidate();
        cache.get("http://one/nacos", identity, loader);
        assertEquals(7, loads.get());
        cache.close();
        assertThrows(NacosException.class, () -> cache.get("http://one/nacos", identity, loader));
        assertEquals(7, loads.get());
    }
    
    @Test
    void shouldNotCacheFailures() throws Exception {
        AiCapabilityCache cache = new AiCapabilityCache();
        NacosException denied = new NacosException(403, "denied");
        assertSame(denied, assertThrows(NacosException.class,
            () -> cache.get("target", identity, () -> {
                throw denied;
            })));
        assertSame(AiCapabilitySnapshot.unknown(),
            cache.get("target", identity, AiCapabilitySnapshot::unknown));
    }
    
    @Test
    void shouldEvictCompletedEntriesAtBound() throws Exception {
        AiCapabilityCache cache = new AiCapabilityCache();
        for (int i = 0; i < 65; i++) {
            cache.get("target" + i, identity, AiCapabilitySnapshot::unknown);
        }
        AtomicInteger reload = new AtomicInteger();
        cache.get("target0", identity, () -> {
            reload.incrementAndGet();
            return AiCapabilitySnapshot.unknown();
        });
        assertEquals(1, reload.get());
    }
    
    @Test
    void shouldCoalesceConcurrentRequests() throws Exception {
        AiCapabilityCache cache = new AiCapabilityCache();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        try {
            Future<AiCapabilitySnapshot> first = executor.submit(() -> cache.get("target", identity,
                () -> {
                    loads.incrementAndGet();
                    started.countDown();
                    await(release);
                    return AiCapabilitySnapshot.unknown();
                }));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Future<AiCapabilitySnapshot> second =
                executor.submit(() -> cache.get("target", identity,
                    () -> {
                        loads.incrementAndGet();
                        return AiCapabilitySnapshot.unknown();
                    }));
            release.countDown();
            assertSame(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(1, loads.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
    
    @Test
    void shouldRejectStaleCompletionAfterInvalidation() throws Exception {
        AiCapabilityCache cache = new AiCapabilityCache();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<Integer> pending = executor.submit(() -> {
                NacosException error = assertThrows(NacosException.class,
                    () -> cache.get("target", identity, () -> {
                        started.countDown();
                        await(release);
                        return AiCapabilitySnapshot.unknown();
                    }));
                return error.getErrCode();
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            cache.invalidate();
            cache.get("target", identity, AiCapabilitySnapshot::unknown);
            release.countDown();
            assertEquals(NacosException.CLIENT_DISCONNECT, pending.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
    
    @Test
    void shouldRejectGrowthWhenAllSlotsAreLoadingAndReuseAfterCompletion() throws Exception {
        AiCapabilityCache cache = new AiCapabilityCache();
        ExecutorService executor = Executors.newFixedThreadPool(64);
        CountDownLatch started = new CountDownLatch(64);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<AiCapabilitySnapshot>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 64; i++) {
                String target = "target" + i;
                results.add(executor.submit(() -> cache.get(target, identity, () -> {
                    started.countDown();
                    await(release);
                    return AiCapabilitySnapshot.unknown();
                })));
            }
            assertTrue(started.await(5, TimeUnit.SECONDS));
            NacosException error = assertThrows(NacosException.class,
                () -> cache.get("overflow", identity, AiCapabilitySnapshot::unknown));
            assertEquals(NacosException.OVER_THRESHOLD, error.getErrCode());
            release.countDown();
            for (Future<AiCapabilitySnapshot> result : results) {
                assertSame(AiCapabilitySnapshot.unknown(), result.get(5, TimeUnit.SECONDS));
            }
            assertSame(AiCapabilitySnapshot.unknown(),
                cache.get("overflow", identity, AiCapabilitySnapshot::unknown));
        } finally {
            release.countDown();
            executor.shutdownNow();
            cache.close();
        }
    }
    
    @Test
    void shouldPreserveInterruptedWaitWithoutCancellingTheOwner() throws Exception {
        AiCapabilityCache cache = new AiCapabilityCache();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<AiCapabilitySnapshot> owner = executor.submit(() -> cache.get("target", identity,
                () -> {
                    started.countDown();
                    await(release);
                    return AiCapabilitySnapshot.unknown();
                }));
            assertTrue(started.await(5, TimeUnit.SECONDS));
            Thread.currentThread().interrupt();
            try {
                assertThrows(NacosException.class,
                    () -> cache.get("target", identity, AiCapabilitySnapshot::unknown));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
            release.countDown();
            assertSame(AiCapabilitySnapshot.unknown(), owner.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
    
    private void await(CountDownLatch latch) throws NacosException {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NacosException(NacosException.SERVER_ERROR, e);
        }
    }
}
