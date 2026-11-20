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

import com.alipay.sofa.jraft.conf.ConfigurationManager;
import com.alipay.sofa.jraft.entity.EnumOutter.EntryType;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.LogId;
import com.alipay.sofa.jraft.entity.codec.v2.LogEntryV2CodecFactory;
import com.alipay.sofa.jraft.option.LogStorageOptions;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.util.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FileLogReaderCacheTest {
    
    @TempDir
    Path temporaryDirectory;
    
    private Path directory;
    
    private Path realDirectory;
    
    private final List<FileLogStorage> stores = new ArrayList<>();
    
    @BeforeEach
    void configureTemporaryDirectory() throws IOException {
        configureDirectory(temporaryDirectory);
    }
    
    @AfterEach
    void shutdown() {
        stores.forEach(FileLogStorage::shutdown);
    }
    
    @Test
    void shutdownWaitsForOpeningReadingAndReleasingCachedReader() throws Exception {
        FileLogStorage store = seed();
        Field field = FileLogStorage.class.getDeclaredField("readWriteLock");
        field.setAccessible(true);
        ReentrantReadWriteLock lock = (ReentrantReadWriteLock) field.get(store);
        CountDownLatch opening = new CountDownLatch(1);
        CountDownLatch finishOpen = new CountDownLatch(1);
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch finishRead = new CountDownLatch(1);
        AtomicReference<SegmentReader> opened = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Thread shutdown = new Thread(store::shutdown, "reader-cache-shutdown");
        try {
            Future<LogEntry> result = executor.submit(() -> {
                try (var readers = mockStatic(SegmentReader.class, invocation -> {
                    SegmentReader original = (SegmentReader) invocation.callRealMethod();
                    Segment segment = spy(original.getSegment());
                    Index index = spy(original.getIndex());
                    SegmentReader reader =
                        new SegmentReader(original.getMetadata(), segment, index);
                    doAnswer(read -> {
                        reading.countDown();
                        assertTrue(finishRead.await(5, TimeUnit.SECONDS));
                        return read.callRealMethod();
                    }).when(segment).read(anyLong(), anyInt(), anyLong());
                    opened.set(reader);
                    opening.countDown();
                    assertTrue(finishOpen.await(5, TimeUnit.SECONDS));
                    return reader;
                })) {
                    return store.getEntry(1);
                }
            });
            assertTrue(opening.await(5, TimeUnit.SECONDS));
            shutdown.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!lock.hasQueuedThread(shutdown) && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            assertTrue(lock.hasQueuedThread(shutdown));
            verify(opened.get().getIndex(), never()).close();
            finishOpen.countDown();
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            assertTrue(lock.hasQueuedThread(shutdown));
            verify(opened.get().getSegment(), never()).close();
            finishRead.countDown();
            assertEquals(entry(1), result.get(5, TimeUnit.SECONDS));
            shutdown.join(5000);
            assertFalse(shutdown.isAlive());
            verify(opened.get().getIndex(), times(1)).close();
            verify(opened.get().getSegment(), times(1)).close();
            assertNull(store.getEntry(1));
        } finally {
            finishOpen.countDown();
            finishRead.countDown();
            executor.shutdownNow();
            shutdown.join(5000);
        }
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cacheHitsReadAndDecodeWithoutReopeningEitherFile(boolean linkedDirectory)
        throws Exception {
        if (linkedDirectory) {
            useLinkedTemporaryDirectory();
        }
        FileLogStorage store = seed();
        Manifest.SegmentMeta first = store.getManifest().getSegments().get(0);
        List<Segment> openedSegments = new ArrayList<>();
        List<Index> openedIndexes = new ArrayList<>();
        try (var segments = mockStatic(Segment.class, invocation -> {
            Object result = invocation.callRealMethod();
            if ("open".equals(invocation.getMethod().getName())) {
                Segment segment = spy((Segment) result);
                openedSegments.add(segment);
                return segment;
            }
            return result;
        }); var indexes = mockStatic(Index.class, invocation -> {
            Object result = invocation.callRealMethod();
            if ("open".equals(invocation.getMethod().getName())) {
                Index index = spy((Index) result);
                openedIndexes.add(index);
                return index;
            }
            return result;
        })) {
            for (int i = 0; i < 4; i++) {
                assertEquals(entry(1), store.getEntry(1));
                assertEquals(7, store.getTerm(1));
                assertEquals(entry(12), store.getEntry(12));
            }
            segments.verify(() -> Segment.open(segmentPath(first.getSegmentId()),
                first.getPublishedEndOffset()), times(1));
            indexes.verify(() -> Index.open(indexPath(first.getSegmentId()), first.getFirstIndex(),
                first.getLastIndex()), times(1));
            assertEquals(1, openedSegments.size());
            assertEquals(1, openedIndexes.size());
            verify(openedSegments.get(0), times(8)).read(anyLong(),
                org.mockito.ArgumentMatchers.anyInt(), anyLong());
            verify(openedIndexes.get(0), times(8)).read(anyLong(), anyLong(), anyLong());
            verify(openedSegments.get(0), never()).close();
            store.shutdown();
            verify(openedSegments.get(0)).close();
            verify(openedIndexes.get(0)).close();
        }
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"prefixInside", "prefixAtSegment", "suffixInside", "suffixAtSegment",
        "reset", "shutdown"})
    void warmedCacheCannotSurviveAffectedRangeChanges(String operation) throws Exception {
        FileLogStorage store = seed();
        List<Segment> cached = new ArrayList<>();
        try (var segments = mockStatic(Segment.class, invocation -> {
            Object result = invocation.callRealMethod();
            if ("open".equals(invocation.getMethod().getName())) {
                Segment segment = spy((Segment) result);
                cached.add(segment);
                return segment;
            }
            return result;
        })) {
            for (int i = 1; i <= 12; i++) {
                assertEquals(entry(i), store.getEntry(i));
            }
            List<Segment> before = new ArrayList<>(cached);
            assertTrue(before.size() > 1);
            Manifest previous = store.getManifest();
            long secondFirst = previous.getSegments().get(1).getFirstIndex();
            long firstLast = previous.getSegments().get(0).getLastIndex();
            long firstKept = 1;
            long lastKept = 12;
            if (operation.startsWith("prefix")) {
                firstKept = "prefixInside".equals(operation) ? 2 : secondFirst;
                assertTrue(store.truncatePrefix(firstKept));
            } else if (operation.startsWith("suffix")) {
                lastKept = "suffixInside".equals(operation) ? 1 : firstLast;
                assertTrue(store.truncateSuffix(lastKept));
            } else if ("reset".equals(operation)) {
                assertTrue(store.reset(100));
                firstKept = 100;
                lastKept = 99;
            } else {
                store.shutdown();
                assertTrue(store.init(context()));
            }
            if (operation.startsWith("prefix")) {
                verify(before.get(0)).close();
                if ("prefixAtSegment".equals(operation)) {
                    verify(before.get(1)).close();
                }
            } else {
                for (Segment segment : before) {
                    verify(segment).close();
                }
            }
            assertEquals(firstKept, store.getFirstLogIndex());
            assertEquals(lastKept, store.getLastLogIndex());
            assertNull(store.getEntry(firstKept - 1));
            assertNull(store.getEntry(lastKept + 1));
            for (long i = firstKept; i <= lastKept; i++) {
                assertEquals(entry(i), store.getEntry(i));
            }
            assertTrue(store.appendEntry(entry(lastKept + 1)));
            store.shutdown();
            assertTrue(store.init(context()));
            assertEquals(entry(lastKept + 1), store.getEntry(lastKept + 1));
        }
    }
    
    @Test
    void prefixRebuildClosesCachedIndexBeforeTruncatingItsFile() throws Exception {
        FileLogStorage store = seed();
        Manifest.SegmentMeta second = store.getManifest().getSegments().get(1);
        AtomicBoolean closed = new AtomicBoolean();
        try (var indexes = mockStatic(Index.class, invocation -> {
            if ("create".equals(invocation.getMethod().getName())) {
                assertTrue(closed.get(), "Cached index must close before rebuild");
            }
            Object result = invocation.callRealMethod();
            if ("open".equals(invocation.getMethod().getName())) {
                Index index = spy((Index) result);
                doAnswer(close -> {
                    closed.set(true);
                    return close.callRealMethod();
                }).when(index).close();
                return index;
            }
            return result;
        })) {
            assertNotNull(store.getEntry(second.getFirstIndex()));
            assertTrue(store.truncatePrefix(second.getFirstIndex()));
            assertTrue(closed.get());
            assertEquals(entry(second.getFirstIndex()), store.getEntry(second.getFirstIndex()));
        }
    }
    
    @Test
    void directManifestPublicationInvalidatesNewActiveAndRemovedReaders() throws Exception {
        FileLogStorage store = seed();
        Manifest previous = store.getManifest();
        Manifest.SegmentMeta first = previous.getSegments().get(0);
        List<Segment> cached = new ArrayList<>();
        try (var segments = mockStatic(Segment.class, invocation -> {
            Object result = invocation.callRealMethod();
            if ("open".equals(invocation.getMethod().getName())) {
                Segment segment = spy((Segment) result);
                cached.add(segment);
                return segment;
            }
            return result;
        })) {
            assertNotNull(store.getEntry(first.getFirstIndex()));
            assertNotNull(store.getEntry(previous.getSegments().get(1).getFirstIndex()));
            store.publishManifest(new Manifest(FileLogFormat.VERSION, previous.getStoreId(),
                previous.getGroupId(), first.getFirstIndex(), first.getLastIndex(),
                List.of(first)));
            for (Segment segment : cached) {
                verify(segment).close();
            }
            assertNull(store.getEntry(first.getLastIndex() + 1));
        }
    }
    
    @Test
    void readFailureDiscardsReaderAndLeavesWritesEnabled() throws Exception {
        FileLogStorage store = seed();
        AtomicBoolean fail = new AtomicBoolean();
        List<Segment> cached = new ArrayList<>();
        try (var segments = mockStatic(Segment.class, invocation -> {
            Object result = invocation.callRealMethod();
            if ("open".equals(invocation.getMethod().getName())) {
                Segment segment = spy((Segment) result);
                doAnswer(read -> {
                    if (fail.getAndSet(false)) {
                        throw new IOException("cached read failure");
                    }
                    return read.callRealMethod();
                }).when(segment).read(anyLong(), org.mockito.ArgumentMatchers.anyInt(), anyLong());
                cached.add(segment);
                return segment;
            }
            return result;
        })) {
            assertNotNull(store.getEntry(1));
            fail.set(true);
            assertNull(store.getEntry(1));
            verify(cached.get(0)).close();
            assertEquals(entry(1), store.getEntry(1));
            assertEquals(2, cached.size());
            assertTrue(store.appendEntry(entry(13)));
        }
    }
    
    @Test
    void cacheHitsStillValidateIndexAndRecordBytes() throws Exception {
        FileLogStorage store = seed();
        Manifest.SegmentMeta first = store.getManifest().getSegments().get(0);
        Path indexPath = indexPath(first.getSegmentId());
        Path segmentPath = segmentPath(first.getSegmentId());
        byte[] index = Files.readAllBytes(indexPath);
        byte[] segment = Files.readAllBytes(segmentPath);
        assertEquals(entry(1), store.getEntry(1));
        byte[] badIndex = index.clone();
        ByteBuffer slots = ByteBuffer.wrap(badIndex);
        slots.putInt(Long.BYTES, slots.getInt(Long.BYTES) + 1);
        Files.write(indexPath, badIndex);
        assertNull(store.getEntry(1));
        Files.write(indexPath, index);
        assertEquals(entry(1), store.getEntry(1));
        byte[] badRecord = segment.clone();
        badRecord[40 + ByteBuffer.wrap(index).getInt(Long.BYTES) - 1] ^= 1;
        Files.write(segmentPath, badRecord);
        assertNull(store.getEntry(1));
        Files.write(segmentPath, segment);
        assertEquals(entry(1), store.getEntry(1));
        assertTrue(store.appendEntry(entry(13)));
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mutationFailureClearsCacheButPublishedReadsCanBorrowAgain(boolean linkedDirectory)
        throws Exception {
        if (linkedDirectory) {
            useLinkedTemporaryDirectory();
        }
        FileLogStorage store = seed();
        AtomicInteger failures = new AtomicInteger();
        List<Segment> cached = new ArrayList<>();
        try (var segments = mockStatic(Segment.class, invocation -> {
            Object result = invocation.callRealMethod();
            if ("open".equals(invocation.getMethod().getName())) {
                Segment segment = spy((Segment) result);
                cached.add(segment);
                return segment;
            }
            return result;
        })) {
            assertNotNull(store.getEntry(1));
            Segment old = cached.get(0);
            try (var forces = mockStatic(FileLogStorage.class, invocation -> {
                if ("forceDirectory".equals(invocation.getMethod().getName())
                    && root().equals(invocation.getArgument(0))) {
                    failures.incrementAndGet();
                    throw new IOException("published root force failure");
                }
                return invocation.callRealMethod();
            })) {
                assertFalse(store.appendEntry(entry(13)));
                assertEquals(1, failures.get(), "The published Manifest directory must be forced");
            }
            verify(old).close();
            assertFalse(store.appendEntry(entry(14)));
            assertEquals(entry(1), store.getEntry(1));
            assertTrue(cached.size() >= 2);
            FileLogStorage competing = new FileLogStorage(directory, new RaftOptions(), 1600);
            stores.add(competing);
            assertFalse(competing.init(context()));
            store.shutdown();
            assertTrue(competing.init(context()));
            assertEquals(entry(13), competing.getEntry(13));
        }
    }
    
    @Test
    void realCacheHasSixteenResidentPairsAndReopensEvictedSegments() throws Exception {
        FileLogStorage store = new FileLogStorage(directory, new RaftOptions(), 1100);
        stores.add(store);
        assertTrue(store.init(context()));
        List<LogEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 40; i++) {
            entries.add(entry(i));
        }
        assertEquals(40, store.appendEntries(entries));
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        try (var segments = mockStatic(Segment.class, invocation -> {
            Object result = invocation.callRealMethod();
            if ("open".equals(invocation.getMethod().getName())) {
                opens.incrementAndGet();
                Segment segment = spy((Segment) result);
                doAnswer(close -> {
                    closes.incrementAndGet();
                    return close.callRealMethod();
                }).when(segment).close();
                return segment;
            }
            return result;
        })) {
            for (int i = 1; i <= 20; i++) {
                assertEquals(entry(i), store.getEntry(i));
            }
            assertEquals(20, opens.get());
            assertEquals(4, closes.get());
            assertEquals(entry(1), store.getEntry(1));
            assertEquals(21, opens.get());
            assertEquals(5, closes.get());
            store.shutdown();
            assertEquals(opens.get(), closes.get());
        }
    }
    
    @Test
    void cacheRemainsPinnedUntilShutdownAndReinitUsesNewRealRoot() throws Exception {
        FileLogStorage original = seed();
        UUID originalId = original.getManifest().getStoreId();
        original.shutdown();
        Path alias = directory.resolve("alias");
        Path other = Files.createDirectory(directory.resolve("other"));
        try {
            Files.createSymbolicLink(alias, directory);
        } catch (UnsupportedOperationException | IOException error) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Directory links unavailable");
        }
        FileLogStorage linked = new FileLogStorage(alias, new RaftOptions(), 1600);
        stores.add(linked);
        assertTrue(linked.init(context()));
        assertEquals(entry(1), linked.getEntry(1));
        Files.delete(alias);
        Files.createSymbolicLink(alias, other);
        assertEquals(entry(1), linked.getEntry(1));
        assertEquals(originalId, linked.getManifest().getStoreId());
        linked.shutdown();
        assertTrue(linked.init(context()));
        assertNull(linked.getEntry(1));
        org.junit.jupiter.api.Assertions.assertNotEquals(originalId,
            linked.getManifest().getStoreId());
        assertTrue(linked.appendEntry(entry(1)));
    }
    
    private FileLogStorage seed() {
        FileLogStorage store = new FileLogStorage(directory, new RaftOptions(), 1600);
        stores.add(store);
        assertTrue(store.init(context()));
        List<LogEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            entries.add(entry(i));
        }
        assertEquals(12, store.appendEntries(entries));
        return store;
    }
    
    private static Path directoryLink(Path link, Path target) throws IOException {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (FileSystemException | UnsupportedOperationException e) {
            assumeTrue(!Platform.isWindows(), "Windows cannot create symbolic links: " + e);
            throw e;
        }
    }
    
    private void configureDirectory(Path configuredDirectory) throws IOException {
        directory = configuredDirectory;
        realDirectory = configuredDirectory.toRealPath();
    }
    
    private void useLinkedTemporaryDirectory() throws IOException {
        Path target = Files.createDirectory(temporaryDirectory.resolve("real-directory"));
        Path link = directoryLink(temporaryDirectory.resolve("temp-entry"), target);
        configureDirectory(Files.createDirectory(link.resolve("storage")));
        assertNotEquals(directory.toAbsolutePath(), realDirectory);
        assertTrue(Files.isSameFile(directory, realDirectory));
    }
    
    private Path root() {
        return realDirectory.resolve("file-log");
    }
    
    private Path segmentPath(long id) {
        return root().resolve("segments").resolve(FileLogFormat.segmentName(id));
    }
    
    private Path indexPath(long id) {
        return root().resolve("index").resolve(FileLogFormat.indexName(id));
    }
    
    private static LogStorageOptions context() {
        LogStorageOptions options = new LogStorageOptions();
        options.setGroupId("reader-cache-test");
        options.setConfigurationManager(new ConfigurationManager());
        options.setLogEntryCodecFactory(LogEntryV2CodecFactory.getInstance());
        return options;
    }
    
    private static LogEntry entry(long index) {
        LogEntry entry = new LogEntry(EntryType.ENTRY_TYPE_DATA);
        entry.setId(new LogId(index, 7));
        byte[] bytes = new byte[512];
        java.util.Arrays.fill(bytes, (byte) index);
        entry.setData(ByteBuffer.wrap(bytes));
        return entry;
    }
}
