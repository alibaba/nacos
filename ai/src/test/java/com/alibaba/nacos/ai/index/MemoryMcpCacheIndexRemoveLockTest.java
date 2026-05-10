/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.index;

import com.alibaba.nacos.ai.config.McpCacheIndexProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the "missing writeLock on removeIndex" bug.
 *
 * <p>Before the fix, both {@code removeIndex(String, String)} and
 * {@code removeIndex(String)} mutated three shared structures
 * ({@code nameKeyToId}, {@code idToEntry} and the LRU doubly-linked list)
 * without holding any lock, while sibling mutators {@code updateIndex} and
 * {@code clear} correctly acquired the {@code writeLock}.
 * Under concurrency this allowed double-removal from the LRU list,
 * dangling-node reinsertion via {@code moveToHead}, NPEs and memory leaks.
 *
 * <p>This test verifies the externally-visible behavioural contract: a
 * concurrent stress scenario that mixes {@code updateIndex} /
 * {@code getMcpServerById} / {@code removeIndex} must finish cleanly, leave
 * the cache in a consistent and usable state and cause no leaked exceptions.
 * Intentionally black-box: does not reflect on internal fields so it remains
 * valid if the internal locking primitive is later refactored.
 *
 * @author nacos
 */
class MemoryMcpCacheIndexRemoveLockTest {
    
    private MemoryMcpCacheIndex cache;
    
    private McpCacheIndexProperties props;
    
    @BeforeEach
    void setUp() {
        props = new McpCacheIndexProperties();
        props.setMaxSize(1000);
        props.setExpireTimeSeconds(60);
        // Disable background cleanup so the scheduler cannot race with the test.
        props.setCleanupIntervalSeconds(3600);
        cache = new MemoryMcpCacheIndex(props);
    }
    
    @AfterEach
    void tearDown() {
        cache.shutdown();
    }
    
    @Test
    void testConcurrentUpdateGetRemoveStaysConsistent() throws Exception {
        int preload = 200;
        for (int i = 0; i < preload; i++) {
            cache.updateIndex("ns", "name-" + i, "id-" + i);
        }
        
        int threads = 16;
        int opsPerThread = 500;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger leakedErrors = new AtomicInteger(0);
        
        for (int t = 0; t < threads; t++) {
            final int threadIdx = t;
            pool.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                    for (int i = 0; i < opsPerThread; i++) {
                        int k = (threadIdx * opsPerThread + i) % preload;
                        switch (i % 4) {
                            case 0:
                                cache.updateIndex("ns", "name-" + k, "id-" + k);
                                break;
                            case 1:
                                cache.getMcpServerById("id-" + k);
                                break;
                            case 2:
                                cache.removeIndex("ns", "name-" + k);
                                break;
                            default:
                                cache.removeIndex("id-" + k);
                                break;
                        }
                    }
                } catch (Throwable ex) {
                    // Any leaked NPE / IllegalState here means the cache guards are broken.
                    leakedErrors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        boolean finished = latch.await(30, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "Pool must terminate in time");
        assertTrue(finished, "All worker threads must finish in time");
        assertEquals(0, leakedErrors.get(),
            "Concurrent updateIndex/getMcpServerById/removeIndex must not leak exceptions");
        
        // Cache must still be usable after the storm.
        cache.updateIndex("ns", "final-name", "final-id");
        assertEquals("final-id", cache.getMcpId("ns", "final-name"),
            "Cache must remain functional after concurrent update/remove stress");
    }
}
