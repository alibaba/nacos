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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.core.remote.thirdparty.clhm.ConcurrentLinkedHashMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.awaitility.Awaitility.await;

class ConcurrentLinkedHashMapTest {
    
    @Test
    void testCapacityAndAccessOrderEviction() {
        List<String> evicted = new ArrayList<>();
        ConcurrentLinkedHashMap<String, String> map =
            new ConcurrentLinkedHashMap.Builder<String, String>()
                .maximumWeightedCapacity(2)
                .listener((key, value) -> evicted.add(key)).build();
        map.put("first", "one");
        map.put("second", "two");
        assertEquals("one", map.get("first"));
        map.put("third", "three");
        
        assertEquals(2, map.size());
        assertEquals(List.of("second"), evicted);
        assertNull(map.get("second"));
        assertEquals(List.of("first", "third"), new ArrayList<>(map.ascendingKeySet()));
    }
    
    @Test
    void testWeightedCapacityAndResize() {
        ConcurrentLinkedHashMap<String, String> map =
            new ConcurrentLinkedHashMap.Builder<String, String>()
                .maximumWeightedCapacity(5).weigher(String::length).build();
        map.put("first", "123");
        map.put("second", "456");
        assertFalse(map.containsKey("first"));
        assertEquals(1, map.size());
        map.setCapacity(2);
        assertTrue(map.isEmpty());
    }
    
    @Test
    void testExplicitRemovalAndClearDoNotNotifyEvictionListener() {
        AtomicInteger evictions = new AtomicInteger();
        ConcurrentLinkedHashMap<String, String> map =
            new ConcurrentLinkedHashMap.Builder<String, String>()
                .maximumWeightedCapacity(2)
                .listener((key, value) -> evictions.incrementAndGet()).build();
        map.put("first", "one");
        map.put("second", "two");
        assertFalse(map.remove("first", "other"));
        assertEquals("one", map.get("first"));
        assertTrue(map.remove("first", "one"));
        assertEquals("two", map.remove("second"));
        map.put("third", "three");
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(0, evictions.get());
    }
    
    @Test
    void testPutIfAbsentAndConditionalReplace() {
        ConcurrentLinkedHashMap<String, String> map =
            new ConcurrentLinkedHashMap.Builder<String, String>()
                .maximumWeightedCapacity(1).build();
        assertNull(map.putIfAbsent("key", "first"));
        assertEquals("first", map.putIfAbsent("key", "second"));
        assertFalse(map.replace("key", "wrong", "second"));
        assertTrue(map.replace("key", "first", "second"));
        assertEquals("second", map.get("key"));
    }
    
    @Test
    void testZeroCapacityImmediatelyEvictsInsertedEntry() {
        List<String> evicted = new ArrayList<>();
        ConcurrentLinkedHashMap<String, String> map =
            new ConcurrentLinkedHashMap.Builder<String, String>()
                .maximumWeightedCapacity(0)
                .listener((key, value) -> evicted.add(value)).build();
        map.putIfAbsent("key", "value");
        assertTrue(map.isEmpty());
        assertEquals(List.of("value"), evicted);
    }
    
    @Test
    void testInvalidBuilderAndNullEntries() {
        assertThrows(IllegalStateException.class,
            () -> new ConcurrentLinkedHashMap.Builder<>().build());
        assertThrows(IllegalArgumentException.class,
            () -> new ConcurrentLinkedHashMap.Builder<>().maximumWeightedCapacity(-1));
        ConcurrentLinkedHashMap<String, String> map =
            new ConcurrentLinkedHashMap.Builder<String, String>()
                .maximumWeightedCapacity(1).build();
        assertThrows(NullPointerException.class, () -> map.put(null, "value"));
        assertThrows(NullPointerException.class, () -> map.put("key", null));
    }
    
    @Test
    void testConcurrentPutIfAbsentHasOneWinner() throws Exception {
        ConcurrentLinkedHashMap<String, Object> map =
            new ConcurrentLinkedHashMap.Builder<String, Object>()
                .maximumWeightedCapacity(1).build();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        AtomicInteger winners = new AtomicInteger();
        try {
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    Object value = new Object();
                    Object previous = map.putIfAbsent("key", value);
                    if (previous == null) {
                        winners.incrementAndGet();
                        return value;
                    }
                    return previous;
                }));
            }
            start.countDown();
            for (Future<Object> result : results) {
                assertSame(result.get(10, TimeUnit.SECONDS), map.get("key"));
            }
            assertEquals(1, winners.get());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
    
    @Test
    void testConcurrentReadsWritesRemovalsAndEvictionsAccountForEveryEntry() throws Exception {
        Map<Integer, AtomicInteger> completed = new ConcurrentHashMap<>();
        ConcurrentLinkedHashMap<Integer, Integer> map =
            new ConcurrentLinkedHashMap.Builder<Integer, Integer>()
                .maximumWeightedCapacity(32)
                .listener((key, value) -> {
                    assertEquals(key, value);
                    completed.computeIfAbsent(key, ignored -> new AtomicInteger())
                        .incrementAndGet();
                }).build();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        int entriesPerThread = 500;
        try {
            for (int thread = 0; thread < 8; thread++) {
                int offset = thread * entriesPerThread;
                results.add(executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    for (int i = 0; i < entriesPerThread; i++) {
                        int key = offset + i;
                        assertNull(map.putIfAbsent(key, key));
                        Integer value = map.get(key);
                        if (value != null) {
                            assertEquals(key, value.intValue());
                        }
                        if (i % 3 == 0 && map.remove(key, key)) {
                            completed.computeIfAbsent(key, ignored -> new AtomicInteger())
                                .incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(20, TimeUnit.SECONDS);
            }
            // Upstream applies the eviction policy in batches. Exercise its public
            // capacity update until pending operations have been processed.
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                map.setCapacity(0);
                assertTrue(map.isEmpty());
                assertEquals(8 * entriesPerThread, completed.size());
            });
            completed.values().forEach(count -> assertEquals(1, count.get()));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
