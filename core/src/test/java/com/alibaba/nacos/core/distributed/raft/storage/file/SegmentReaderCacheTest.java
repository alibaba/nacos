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

package com.alibaba.nacos.core.distributed.raft.storage.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SegmentReaderCacheTest {
    
    @TempDir
    Path directory;
    
    private final UUID storeId = UUID.randomUUID();
    
    private static Manifest.SegmentMeta metadata(long id) {
        return new Manifest.SegmentMeta(id, 1, 5, 40, 200);
    }
    
    private static SegmentReader reader(Manifest.SegmentMeta metadata) {
        return new SegmentReader(metadata, mock(Segment.class), mock(Index.class));
    }
    
    private SegmentReader borrow(SegmentReaderCache cache, Manifest.SegmentMeta metadata)
        throws IOException {
        return cache.borrow(directory, storeId, metadata);
    }
    
    private static MockedStatic<SegmentReader> mockOpen(Answer<SegmentReader> answer) {
        MockedStatic<SegmentReader> opened = mockStatic(SegmentReader.class);
        opened.when(() -> SegmentReader.open(any(Path.class), any(UUID.class),
            any(Manifest.SegmentMeta.class))).thenAnswer(answer);
        return opened;
    }
    
    @Test
    void reusesReadersAndClosesOnlyAfterLastBorrowerReleases() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        SegmentReader first;
        try (var opened = mockOpen(invocation -> {
            opens.incrementAndGet();
            return reader(invocation.getArgument(2));
        }); SegmentReaderCache cache = new SegmentReaderCache(2)) {
            first = borrow(cache, metadata(1));
            SegmentReader second = borrow(cache, metadata(1));
            assertSame(first, second);
            cache.invalidate(1);
            verify(first.getSegment(), never()).close();
            cache.release(first);
            verify(first.getIndex(), never()).close();
            cache.release(second);
            verify(first.getSegment()).close();
            verify(first.getIndex()).close();
            assertEquals(1, opens.get());
            assertThrows(IllegalStateException.class, () -> cache.release(first));
        }
        verify(first.getSegment(), times(1)).close();
    }
    
    @Test
    void evictsLeastRecentlyUsedReader() throws Exception {
        try (var opened = mockOpen(invocation -> reader(invocation.getArgument(2)));
            SegmentReaderCache cache = new SegmentReaderCache(2)) {
            SegmentReader one = borrow(cache, metadata(1));
            cache.release(one);
            SegmentReader two = borrow(cache, metadata(2));
            cache.release(two);
            assertSame(one, borrow(cache, metadata(1)));
            cache.release(one);
            SegmentReader three = borrow(cache, metadata(3));
            cache.release(three);
            verify(two.getSegment()).close();
            verify(one.getSegment(), never()).close();
            verify(three.getIndex(), never()).close();
        }
    }
    
    @Test
    void retiredReaderDoesNotRemoveReplacementWithSameId() throws Exception {
        try (var opened = mockOpen(invocation -> reader(invocation.getArgument(2)));
            SegmentReaderCache cache = new SegmentReaderCache(1)) {
            SegmentReader old = borrow(cache, metadata(1));
            SegmentReader other = borrow(cache, metadata(2));
            SegmentReader replacement = borrow(cache, metadata(1));
            assertNotSame(old, replacement);
            cache.discard(old);
            cache.release(old);
            cache.discard(old);
            cache.discard(old);
            verify(old.getSegment(), times(1)).close();
            verify(old.getIndex(), times(1)).close();
            assertSame(replacement, borrow(cache, metadata(1)));
            cache.release(replacement);
            cache.release(replacement);
            cache.release(other);
            verify(other.getIndex()).close();
            verify(replacement.getIndex(), never()).close();
        }
    }
    
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void replacesReaderWhenAnyRangeFieldChanges(int field) throws Exception {
        long[] values = {1, 5, 40, 200};
        values[field]++;
        Manifest.SegmentMeta changed = new Manifest.SegmentMeta(1, values[0], values[1],
            values[2], values[3]);
        try (var opened = mockOpen(invocation -> reader(invocation.getArgument(2)));
            SegmentReaderCache cache = new SegmentReaderCache(1)) {
            SegmentReader old = borrow(cache, metadata(1));
            cache.release(old);
            SegmentReader next = borrow(cache, changed);
            assertNotSame(old, next);
            assertEquals(changed, next.getMetadata());
            verify(old.getIndex()).close();
            cache.release(next);
        }
    }
    
    @Test
    void invalidateAllDoesNotDisableFutureBorrows() throws Exception {
        try (var opened = mockOpen(invocation -> reader(invocation.getArgument(2)));
            SegmentReaderCache cache = new SegmentReaderCache(2)) {
            SegmentReader one = borrow(cache, metadata(1));
            SegmentReader two = borrow(cache, metadata(2));
            cache.release(one);
            cache.invalidateAll();
            verify(one.getIndex()).close();
            verify(two.getIndex(), never()).close();
            SegmentReader next = borrow(cache, metadata(1));
            assertNotSame(one, next);
            cache.release(next);
            cache.release(two);
            verify(two.getIndex()).close();
        }
    }
    
    @Test
    void failedOpenDoesNotPoisonCache() throws Exception {
        AtomicInteger opens = new AtomicInteger();
        try (var opened = mockOpen(invocation -> {
            if (opens.incrementAndGet() == 1) {
                throw new IOException("open");
            }
            return reader(invocation.getArgument(2));
        }); SegmentReaderCache cache = new SegmentReaderCache(1)) {
            assertThrows(IOException.class, () -> borrow(cache, metadata(1)));
            SegmentReader reader = borrow(cache, metadata(1));
            cache.release(reader);
            assertEquals(2, opens.get());
        }
        assertThrows(IllegalArgumentException.class,
            () -> new SegmentReaderCache(0));
    }
    
    @Test
    void closeFailureStillAttemptsBothResourcesAndAllEntries() throws Exception {
        SegmentReader one = reader(metadata(1));
        SegmentReader two = reader(metadata(2));
        doThrow(new IOException("index")).when(one.getIndex()).close();
        doThrow(new IOException("segment")).when(one.getSegment()).close();
        try (var opened = mockOpen(
            invocation -> invocation.<Manifest.SegmentMeta>getArgument(2).getSegmentId() == 1 ? one
                : two)) {
            SegmentReaderCache cache = new SegmentReaderCache(2);
            cache.release(borrow(cache, metadata(1)));
            cache.release(borrow(cache, metadata(2)));
            IOException failure = assertThrows(IOException.class, cache::close);
            assertEquals("index", failure.getMessage());
            assertEquals("segment", failure.getSuppressed()[0].getMessage());
            verify(one.getSegment()).close();
            verify(two.getIndex()).close();
            verify(two.getSegment()).close();
            cache.discard(one);
            cache.close();
            verify(one.getIndex(), times(1)).close();
        }
    }
    
    @Test
    void failedEvictionDoesNotLeakSelectedReference() throws Exception {
        SegmentReader bad = reader(metadata(1));
        doThrow(new IOException("close")).when(bad.getIndex()).close();
        SegmentReader good = reader(metadata(2));
        try (
            var opened = mockOpen(
                invocation -> invocation.<Manifest.SegmentMeta>getArgument(2).getSegmentId() == 1
                    ? bad : good);
            SegmentReaderCache cache = new SegmentReaderCache(1)) {
            cache.release(borrow(cache, metadata(1)));
            assertThrows(IOException.class, () -> borrow(cache, metadata(2)));
            assertSame(good, borrow(cache, metadata(2)));
            cache.release(good);
        }
        verify(good.getSegment()).close();
    }
    
    @Test
    void clearingCacheDefersBorrowedResources() throws Exception {
        try (var opened = mockOpen(invocation -> reader(invocation.getArgument(2)))) {
            SegmentReaderCache cache = new SegmentReaderCache(1);
            SegmentReader reader = borrow(cache, metadata(1));
            cache.close();
            cache.close();
            cache.discard(reader);
            verify(reader.getIndex(), never()).close();
            cache.release(reader);
            cache.discard(reader);
            cache.close();
            verify(reader.getIndex(), times(1)).close();
            verify(reader.getSegment(), times(1)).close();
        }
    }
    
    @Test
    void concurrentMissesKeepOnePairAndCloseDuplicate() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier opened = new CyclicBarrier(2);
        List<SegmentReader> pairs = java.util.Collections.synchronizedList(new ArrayList<>());
        try (SegmentReaderCache cache = new SegmentReaderCache(1)) {
            java.util.concurrent.Callable<SegmentReader> miss = () -> {
                try (var opening = mockOpen(invocation -> {
                    SegmentReader pair = reader(invocation.getArgument(2));
                    pairs.add(pair);
                    opened.await(5, TimeUnit.SECONDS);
                    return pair;
                })) {
                    return borrow(cache, metadata(1));
                }
            };
            Future<SegmentReader> a = executor.submit(miss);
            Future<SegmentReader> b = executor.submit(miss);
            SegmentReader chosen = a.get(5, TimeUnit.SECONDS);
            assertSame(chosen, b.get(5, TimeUnit.SECONDS));
            assertEquals(2, pairs.size());
            SegmentReader loser = pairs.get(0) == chosen ? pairs.get(1) : pairs.get(0);
            verify(loser.getIndex()).close();
            verify(chosen.getIndex(), never()).close();
            cache.release(chosen);
            cache.release(chosen);
        } finally {
            executor.shutdownNow();
        }
        for (SegmentReader pair : pairs) {
            verify(pair.getIndex(), times(1)).close();
            verify(pair.getSegment(), times(1)).close();
        }
    }
    
    @Test
    void slowOpenDoesNotBlockHits() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch opening = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        SegmentReader slow = reader(metadata(2));
        try (var opened = mockOpen(invocation -> reader(invocation.getArgument(2)));
            SegmentReaderCache cache = new SegmentReaderCache(2)) {
            try {
                SegmentReader hot = borrow(cache, metadata(1));
                cache.release(hot);
                Future<SegmentReader> pending = executor.submit(() -> {
                    try (var miss = mockOpen(invocation -> {
                        opening.countDown();
                        assertTrue(finish.await(5, TimeUnit.SECONDS));
                        return slow;
                    })) {
                        return borrow(cache, metadata(2));
                    }
                });
                assertTrue(opening.await(5, TimeUnit.SECONDS));
                Future<SegmentReader> hit = executor.submit(() -> borrow(cache, metadata(1)));
                assertSame(hot, hit.get(5, TimeUnit.SECONDS));
                cache.release(hot);
                finish.countDown();
                assertSame(slow, pending.get(5, TimeUnit.SECONDS));
                cache.release(slow);
            } finally {
                finish.countDown();
                executor.shutdownNow();
            }
        }
        verify(slow.getIndex()).close();
        verify(slow.getSegment()).close();
    }
    
    @Test
    void duplicateCloseFailureReleasesWinningReaderReference() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch opening = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        SegmentReader loser = reader(metadata(1));
        SegmentReader winner = reader(metadata(1));
        doThrow(new IOException("duplicate close")).when(loser.getIndex()).close();
        try (var opened = mockOpen(invocation -> winner);
            SegmentReaderCache cache = new SegmentReaderCache(1)) {
            try {
                Future<SegmentReader> pending = executor.submit(() -> {
                    try (var miss = mockOpen(invocation -> {
                        opening.countDown();
                        assertTrue(finish.await(5, TimeUnit.SECONDS));
                        return loser;
                    })) {
                        return borrow(cache, metadata(1));
                    }
                });
                assertTrue(opening.await(5, TimeUnit.SECONDS));
                assertSame(winner, borrow(cache, metadata(1)));
                cache.release(winner);
                finish.countDown();
                ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> pending.get(5, TimeUnit.SECONDS));
                assertEquals("duplicate close", failure.getCause().getMessage());
                assertSame(winner, borrow(cache, metadata(1)));
                cache.release(winner);
            } finally {
                finish.countDown();
                executor.shutdownNow();
            }
        }
        verify(winner.getIndex()).close();
        verify(loser.getSegment()).close();
    }
    
    @Test
    void slowEvictionCloseDoesNotHoldCacheMonitor() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch closing = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        SegmentReader cold = reader(metadata(1));
        org.mockito.Mockito.doAnswer(invocation -> {
            closing.countDown();
            assertTrue(finish.await(5, TimeUnit.SECONDS));
            return null;
        }).when(cold.getIndex()).close();
        try (
            var opened = mockOpen(
                invocation -> invocation.<Manifest.SegmentMeta>getArgument(2).getSegmentId() == 1
                    ? cold : reader(invocation.getArgument(2)));
            SegmentReaderCache cache = new SegmentReaderCache(2)) {
            try {
                cache.release(borrow(cache, metadata(1)));
                SegmentReader hot = borrow(cache, metadata(2));
                cache.release(hot);
                Future<SegmentReader> miss = executor.submit(() -> {
                    try (var opening = mockOpen(invocation -> reader(invocation.getArgument(2)))) {
                        return borrow(cache, metadata(3));
                    }
                });
                assertTrue(closing.await(5, TimeUnit.SECONDS));
                Future<SegmentReader> hit = executor.submit(() -> borrow(cache, metadata(2)));
                assertSame(hot, hit.get(5, TimeUnit.SECONDS));
                cache.release(hot);
                finish.countDown();
                cache.release(miss.get(5, TimeUnit.SECONDS));
            } finally {
                finish.countDown();
                executor.shutdownNow();
            }
        }
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void openingFailureClosesSegmentAndNeverPublishesPartialPair(boolean identity)
        throws Exception {
        UUID storeId = UUID.randomUUID();
        Segment segment = mock(Segment.class);
        when(segment.getHeader()).thenReturn(new Segment.SegmentHeader(
            identity ? UUID.randomUUID() : storeId, 1));
        IOException failure = new IOException("index open");
        doThrow(new IOException("segment close")).when(segment).close();
        try (var segments = mockStatic(Segment.class, CALLS_REAL_METHODS);
            var indexes = mockStatic(Index.class, CALLS_REAL_METHODS)) {
            segments.when(() -> Segment.open(any(Path.class), anyLong())).thenReturn(segment);
            indexes.when(() -> Index.open(any(Path.class), anyLong(), anyLong()))
                .thenThrow(failure);
            indexes.clearInvocations();
            IOException caught = assertThrows(IOException.class,
                () -> SegmentReader.open(directory, storeId, metadata(1)));
            assertEquals(1, caught.getSuppressed().length);
            if (identity) {
                indexes.verifyNoInteractions();
            } else {
                assertSame(failure, caught);
            }
            verify(segment).close();
        }
    }
}
