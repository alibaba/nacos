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

package com.alibaba.nacos.ai.service.search;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiResourceRelevanceFilterTest {
    
    static AiResourceRelevanceConfiguration configuration(Map<String, String> overrides) {
        Map<String, String> settings = new HashMap<>();
        settings.put("nacos.ai.ard.search.enhancement.enabled", "true");
        for (Map.Entry<String, String> entry : Map
            .of("endpoint", "http://localhost:9000/v1/systemone",
                "api-key", "test-key", "model", "test-model")
            .entrySet()) {
            settings.put(AiResourceRelevanceConfiguration.PREFIX + entry.getKey(),
                entry.getValue());
        }
        overrides.forEach(
            (key, value) -> settings.put(AiResourceRelevanceConfiguration.PREFIX + key, value));
        return new AiResourceRelevanceConfiguration(settings::get);
    }
    
    static AiResourceRelevanceProvider provider() {
        AiResourceRelevanceProvider provider = mock(AiResourceRelevanceProvider.class);
        when(provider.name()).thenReturn("jev");
        when(provider.maxBatchSize()).thenReturn(60);
        when(provider.maxInputBytes()).thenReturn(65536);
        when(provider.contractVersion()).thenReturn("test-v1");
        return provider;
    }
    
    static List<Map<String, String>> candidates(int count) {
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(Map.of("id", "candidate_" + i, "description", "declared capability"));
        }
        return result;
    }
    
    @Test
    void sequentialBatchesReuseSingleNodePermit() throws Exception {
        AiResourceRelevanceProvider provider = provider();
        when(provider.evaluateBatch(any(), any(), any(), anyLong())).thenAnswer(invocation -> {
            List<Map<String, String>> batch = invocation.getArgument(1);
            return Map.of(batch.get(0).get("id"), 0.8);
        });
        AiResourceRelevanceFilter filter = new AiResourceRelevanceFilter(
            configuration(Map.of("batch-size", "1", "max-concurrency", "1")), List.of(provider));
        try {
            assertEquals(60, filter.evaluate("public", "query", candidates(60)).getScores().size());
        } finally {
            filter.close();
        }
    }
    
    @Test
    void defaultsThresholdAndHonorsExplicitOverride() {
        assertEquals(0.5, configuration(Map.of()).threshold);
        assertEquals(0.1, configuration(Map.of("threshold", "0.1")).threshold);
        assertEquals(configuration(Map.of("threshold", "0.50")).fingerprint("test-v1"),
            configuration(Map.of()).fingerprint("test-v1"));
    }
    
    @Test
    void validatesConfigurationAndProviderRegistry() {
        assertFalse(new AiResourceRelevanceConfiguration(key -> null).enabled);
        assertThrows(IllegalArgumentException.class,
            () -> configuration(Map.of("threshold", "NaN")));
        assertThrows(IllegalArgumentException.class,
            () -> configuration(Map.of("threshold", "1.1")));
        assertThrows(IllegalArgumentException.class,
            () -> configuration(Map.of("batch-size", "0")));
        assertThrows(IllegalArgumentException.class,
            () -> configuration(Map.of("batch-concurrency", "9")));
        assertThrows(IllegalArgumentException.class, () -> configuration(Map.of("api-key", "")));
        AiResourceRelevanceProvider provider = provider();
        assertThrows(IllegalArgumentException.class,
            () -> new AiResourceRelevanceFilter(configuration(Map.of()),
                List.of(provider, provider)));
        assertThrows(IllegalArgumentException.class,
            () -> new AiResourceRelevanceFilter(configuration(Map.of("provider", "unknown")),
                List.of(provider)));
    }
    
    @Test
    void runsBatchesConcurrentlyAndKeepsThresholdBoundary() throws Exception {
        AiResourceRelevanceProvider provider = provider();
        CountDownLatch started = new CountDownLatch(3);
        AtomicInteger calls = new AtomicInteger();
        when(provider.evaluateBatch(any(), any(), any(), anyLong())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            started.countDown();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            List<Map<String, String>> batch = invocation.getArgument(1);
            assertEquals(20, batch.size());
            Map<String, Double> scores = new HashMap<>();
            batch.forEach(item -> scores.put(item.get("id"), 0.5));
            return scores;
        });
        AiResourceRelevanceFilter filter = new AiResourceRelevanceFilter(
            configuration(Map.of("batch-size", "20", "batch-concurrency", "3")), List.of(provider));
        try {
            AiResourceRelevanceFilter.Evaluation result =
                filter.evaluate("public", "query", candidates(60));
            assertEquals("applied", result.getStatus());
            assertEquals(60, result.getScores().size());
            assertEquals(3, calls.get());
            assertTrue(filter.keep(0.5));
            assertTrue(filter.keep(null));
            assertFalse(filter.keep(0.499));
        } finally {
            filter.close();
        }
    }
    
    @Test
    void discardsSuccessfulBatchWhenAnotherFailsAndRejectsInvalidIds() throws Exception {
        AiResourceRelevanceProvider provider = provider();
        when(provider.evaluateBatch(any(), any(), any(), anyLong())).thenAnswer(invocation -> {
            List<Map<String, String>> batch = invocation.getArgument(1);
            String id = batch.get(0).get("id");
            if (id.equals("candidate_1")) {
                throw new IllegalStateException("failure");
            }
            return Map.of(id, 0.9);
        });
        AiResourceRelevanceFilter filter = new AiResourceRelevanceFilter(
            configuration(Map.of("batch-size", "1", "batch-concurrency", "2")), List.of(provider));
        try {
            assertEquals(Map.of(), filter.evaluate("public", "query", candidates(2)).getScores());
            org.mockito.Mockito.doReturn(Map.of("other", 0.9)).when(provider)
                .evaluateBatch(any(), any(), any(), anyLong());
            assertEquals("bypassed", filter.evaluate("public", "query", candidates(1)).getStatus());
        } finally {
            filter.close();
        }
    }
    
    @Test
    void deadlineCancelsCallButDoesNotReleaseOccupiedNodeCapacityPrematurely() throws Exception {
        AiResourceRelevanceProvider provider = provider();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(provider.evaluateBatch(any(), any(), any(), anyLong())).thenAnswer(invocation -> {
            calls.incrementAndGet();
            while (release.getCount() != 0) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    interrupted.countDown();
                }
            }
            return Map.of("candidate_0", 1.0);
        });
        AiResourceRelevanceFilter filter = new AiResourceRelevanceFilter(
            configuration(Map.of("timeout-ms", "100", "max-concurrency", "1")), List.of(provider));
        try {
            assertEquals("bypassed", filter.evaluate("public", "query", candidates(1)).getStatus());
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            assertEquals("bypassed", filter.evaluate("public", "query", candidates(1)).getStatus());
            assertEquals(1, calls.get());
        } finally {
            release.countDown();
            filter.close();
        }
    }
    
    @Test
    void retainsOversizedCandidatesWithoutSendingThem() throws Exception {
        AiResourceRelevanceProvider provider = provider();
        when(provider.evaluateBatch(any(), any(), any(), anyLong()))
            .thenReturn(Map.of("candidate_0", 0.9));
        AiResourceRelevanceFilter filter =
            new AiResourceRelevanceFilter(configuration(Map.of()), List.of(provider));
        try {
            List<Map<String, String>> inputs = candidates(1);
            inputs.add(Map.of("id", "candidate_1", "description", "x".repeat(70000)));
            assertEquals("partial", filter.evaluate("public", "query", inputs).getStatus());
            assertEquals("bypassed",
                filter.evaluate("public", "x".repeat(70000), inputs).getStatus());
        } finally {
            filter.close();
        }
    }
}
