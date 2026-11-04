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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.nacos.core.utils.Loggers;
import com.alipay.sofa.jraft.conf.ConfigurationManager;
import com.alipay.sofa.jraft.entity.EnumOutter.EntryType;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.LogId;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.codec.v2.LogEntryV2CodecFactory;
import com.alipay.sofa.jraft.option.LogStorageOptions;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.util.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;

class FileLogStorageRecoveryTest {
    
    @TempDir
    Path directory;
    
    private final List<FileLogStorage> stores = new ArrayList<>();
    
    @AfterEach
    void closeStores() {
        stores.forEach(FileLogStorage::shutdown);
    }
    
    @Test
    void recoversPersistedLogsThroughAncestorLinkAndRealPath() throws Exception {
        Path realHome = Files.createDirectory(directory.resolve("real-home"));
        Path realLog = Files.createDirectories(realHome.resolve("data/group/log"));
        Path homeLink = directoryLink(directory.resolve("home-link"), realHome);
        FileLogStorage original = store(realLog);
        assertTrue(original.init(context("group")));
        assertTrue(original.appendEntry(entry(1)));
        assertTrue(original.appendEntry(entry(2)));
        java.util.UUID storeId = original.getManifest().getStoreId();
        original.shutdown();
        
        FileLogStorage linked = store(homeLink.resolve("data/group/log"));
        assertTrue(linked.init(context("group")));
        assertEquals(storeId, linked.getManifest().getStoreId());
        assertEquals(entry(1), linked.getEntry(1));
        assertEquals(entry(2), linked.getEntry(2));
        assertTrue(linked.appendEntry(entry(3)));
        linked.shutdown();
        
        FileLogStorage real = store(realLog);
        assertTrue(real.init(context("group")));
        assertEquals(storeId, real.getManifest().getStoreId());
        for (int index = 1; index <= 3; index++) {
            assertEquals(entry(index), real.getEntry(index));
        }
        assertTrue(real.appendEntry(entry(4)));
        real.shutdown();
        assertTrue(linked.init(context("group")));
        assertEquals(storeId, linked.getManifest().getStoreId());
        assertEquals(1, linked.getFirstLogIndex());
        assertEquals(4, linked.getLastLogIndex());
        for (int index = 1; index <= 4; index++) {
            assertEquals(entry(index), linked.getEntry(index));
        }
        assertTrue(Files.isSymbolicLink(homeLink));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"dangling", "danglingAncestor", "cycle", "cycleAncestor",
        "file", "fileAncestor"})
    void rejectsInvalidConfigurationLinksWithoutCreatingTargetsAndRetriesAfterRepair(String kind)
        throws Exception {
        Path link = directory.resolve("entry");
        Path missing = directory.resolve("missing-target");
        Path regularFile = Files.write(directory.resolve("regular-file"), new byte[] {42});
        Path target = kind.startsWith("dangling") ? missing
            : kind.startsWith("cycle") ? link.getFileName() : regularFile;
        directoryLink(link, target);
        Path configured = kind.endsWith("Ancestor") ? link.resolve("group/log") : link;
        FileLogStorage store = store(configured);
        assertFalse(store.init(context("group")));
        assertFalse(Files.exists(missing));
        assertArrayEquals(new byte[] {42}, Files.readAllBytes(regularFile));
        assertTrue(Files.isSymbolicLink(link));
        assertFalse(store.appendEntry(entry(1)));
        assertEquals(0, store.getFirstLogIndex());
        Files.delete(link);
        Path repaired = Files.createDirectory(directory.resolve("repaired"));
        directoryLink(link, repaired);
        assertTrue(store.init(context("group")));
        assertTrue(store.appendEntry(entry(1)));
        store.shutdown();
        assertTrue(store.init(context("group")));
        assertEquals(entry(1), store.getEntry(1));
        Path actual = kind.endsWith("Ancestor") ? repaired.resolve("group/log") : repaired;
        assertTrue(Files.isRegularFile(actual.resolve("file-log/manifest")));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"file-log", "segments", "index"})
    void rejectsManagedDirectoryLinksThroughAllowedEntryAndPreservesExternalContents(String name)
        throws Exception {
        Path real = Files.createDirectory(directory.resolve("real-log"));
        Path entry = directoryLink(directory.resolve("entry"), real);
        Path external = Files.createDirectory(directory.resolve("external"));
        Path sentinel = Files.write(external.resolve("sentinel"), new byte[] {42});
        Path managed = "file-log".equals(name) ? real.resolve(name)
            : Files.createDirectory(real.resolve("file-log")).resolve(name);
        directoryLink(managed, external);
        FileLogStorage store = store(entry);
        assertFalse(store.init(context("group")));
        assertFalse(store.appendEntry(entry(1)));
        assertTrue(Files.isSymbolicLink(managed));
        assertArrayEquals(new byte[] {42}, Files.readAllBytes(sentinel));
        try (java.util.stream.Stream<Path> contents = Files.list(external)) {
            assertEquals(List.of(sentinel), contents.toList());
        }
        Files.delete(managed);
        assertTrue(store.init(context("group")));
        assertTrue(store.appendEntry(entry(1)));
        store.shutdown();
        assertTrue(store.init(context("group")));
        assertEquals(entry(1), store.getEntry(1));
        assertArrayEquals(new byte[] {42}, Files.readAllBytes(sentinel));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"LOCK", "manifest", "segment", "index", "manifest.tmp"})
    void rejectsManagedFileLinksAndLeavesExternalBytesUntouched(String name) throws Exception {
        Path real = Files.createDirectory(directory.resolve("real-log"));
        Path link = directoryLink(directory.resolve("entry"), real);
        Path root = real.resolve("file-log");
        FileLogStorage store = store(link);
        boolean published = !"manifest.tmp".equals(name);
        java.util.UUID storeId = null;
        if (published) {
            assertTrue(store.init(context("group")));
            assertTrue(store.appendEntry(entry(1)));
            storeId = store.getManifest().getStoreId();
            store.shutdown();
        } else {
            Files.createDirectory(root);
        }
        Path managed;
        if ("segment".equals(name)) {
            managed = root.resolve("segments").resolve(FileLogFormat.segmentName(1));
        } else if ("index".equals(name)) {
            managed = root.resolve("index").resolve(FileLogFormat.indexName(1));
        } else {
            managed = root.resolve(name);
        }
        Path external = directory.resolve("external-file");
        if (published) {
            Files.move(managed, external);
        } else {
            Files.write(external, new byte[] {42});
        }
        byte[] bytes = Files.readAllBytes(external);
        directoryLink(managed, external);
        assertFalse(store.init(context("group")));
        assertFalse(store.appendEntry(entry(2)));
        assertTrue(Files.isSymbolicLink(managed));
        assertArrayEquals(bytes, Files.readAllBytes(external));
        Files.delete(managed);
        if (published) {
            Files.move(external, managed);
        }
        assertTrue(store.init(context("group")));
        if (published) {
            assertEquals(storeId, store.getManifest().getStoreId());
            assertEquals(entry(1), store.getEntry(1));
        }
        assertTrue(store.appendEntry(entry(published ? 2 : 1)));
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void initializationFailureLogsConfiguredAndResolvedPathsWithOriginalException(boolean resolved)
        throws Exception {
        Path real = Files.createDirectory(directory.resolve("real-log"));
        Path link = directoryLink(directory.resolve("entry"), real);
        Path configured = resolved ? link : directoryLink(directory.resolve("dangling"),
            directory.resolve("missing"));
        Path actualRoot = real.toRealPath().resolve("file-log");
        if (resolved) {
            Files.createDirectory(actualRoot);
            Files.write(actualRoot.resolve("segments"), new byte[] {42});
        }
        Logger logger = (Logger) Loggers.RAFT;
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            logger.setLevel(Level.ERROR);
            assertFalse(store(configured).init(context("group")));
            assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.ERROR
                && event.getFormattedMessage().contains("configuredLogUri=" + configured)
                && event.getFormattedMessage().contains(
                    "resolvedRoot=" + (resolved ? actualRoot : null))
                && event.getThrowableProxy() != null),
                "Initialization diagnostics must retain both locations and the original failure");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }
    
    @Test
    void encodesAndValidatesTheCompactManifest() throws Exception {
        FileLogStorage store = store();
        Manifest manifest =
            new Manifest(FileLogFormat.VERSION, java.util.UUID.randomUUID(), "group", 10, 11,
                List.of(new Manifest.SegmentMeta(1, 10, 11, 48, 200)));
        ByteBuffer encoded = store.encodeManifest(manifest);
        
        assertEquals(FileLogFormat.MANIFEST_MAGIC, encoded.getInt(0));
        assertEquals(FileLogFormat.MANIFEST_HEADER_SIZE, encoded.getShort(6));
        assertEquals(52 + "group".getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 40 + 8,
            encoded.remaining());
        assertEquals(manifest, store.decodeManifest(encoded));
        
        byte[] corrupt = encoded.array().clone();
        corrupt[corrupt.length - 1] ^= 1;
        assertThrows(java.io.IOException.class,
            () -> store.decodeManifest(ByteBuffer.wrap(corrupt)));
    }
    
    @Test
    void keepsStoreOpenAfterManifestValidationFailure() throws Exception {
        FileLogStorage store = store();
        assertTrue(store.init(context("group")));
        Manifest current = store.getManifest();
        Manifest wrongStore = Manifest.empty(java.util.UUID.randomUUID(), "group", 1);
        
        assertThrows(java.io.IOException.class, () -> store.publishManifest(wrongStore));
        assertEquals(current, store.getManifest());
        store.shutdown();
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"é", "🚀"})
    void preservesExactUtf8GroupIdentityAtTheByteLimit(String character) throws Exception {
        int width = character.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        String group = character.repeat(1024 / width);
        FileLogStorage creator = store();
        assertTrue(creator.init(context(group)));
        Manifest manifest = creator.getManifest();
        assertEquals(group, manifest.getGroupId());
        assertEquals(manifest, creator.decodeManifest(creator.encodeManifest(manifest)));
        assertTrue(creator.appendEntry(entry(1)));
        creator.shutdown();
        FileLogStorage reopened = store();
        assertTrue(reopened.init(context(group)));
        assertEquals(group, reopened.getManifest().getGroupId());
        assertEquals(entry(1), reopened.getEntry(1));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"highSurrogate", "lowSurrogate", "asciiTooLong", "utf8TooLong"})
    void rejectsInvalidGroupIdentityBeforeCreatingFiles(String kind) {
        String group;
        switch (kind) {
            case "highSurrogate":
                group = "group" + Character.MIN_HIGH_SURROGATE;
                break;
            case "lowSurrogate":
                group = "group" + Character.MIN_LOW_SURROGATE;
                break;
            case "asciiTooLong":
                group = "a".repeat(1025);
                break;
            default:
                group = "é".repeat(513);
        }
        FileLogStorage storage = store();
        Manifest invalid = Manifest.empty(java.util.UUID.randomUUID(), group, 1);
        assertThrows(IOException.class, () -> storage.encodeManifest(invalid));
        assertFalse(storage.init(context(group)));
        assertFalse(Files.exists(root()));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {".seg", ".idx"})
    void recognizesOnlyCanonicalAsciiFileNames(String suffix) throws Exception {
        assertEquals(Long.valueOf(1), FileLogFormat.parseFileId("00000000000000000001" + suffix,
            suffix));
        assertEquals(Long.valueOf(Long.MAX_VALUE),
            FileLogFormat.parseFileId("09223372036854775807" + suffix, suffix));
        for (String name : List.of("1" + suffix, "000000000000000000001" + suffix,
            "0000000000000000000١" + suffix, "00000000000000000001" + suffix + ".tmp",
            "+0000000000000000001" + suffix)) {
            assertNull(FileLogFormat.parseFileId(name, suffix));
        }
        assertThrows(IOException.class,
            () -> FileLogFormat.parseFileId("09223372036854775808" + suffix, suffix));
    }
    
    @Test
    void preservesUnrecognizedUnicodeSegmentAndIndexNames() throws Exception {
        writeFixture(1, 1, 0);
        Path segment = root().resolve("segments/0000000000000000000٢.seg");
        Path index = root().resolve("index/0000000000000000000٢.idx");
        byte[] bytes = new byte[] {7, 8, 9};
        Files.write(segment, bytes);
        Files.write(index, bytes);
        FileLogStorage reopened = store();
        assertTrue(reopened.init(context("group")));
        assertArrayEquals(bytes, Files.readAllBytes(segment));
        assertArrayEquals(bytes, Files.readAllBytes(index));
        assertTrue(reopened.reset(10));
        assertTrue(reopened.appendEntry(entry(10)));
        assertEquals(2, reopened.getManifest().getSegments().get(0).getSegmentId());
    }
    
    @Test
    void rebuildsMinimalIndexAndTruncatesDirtyTail() throws Exception {
        Manifest published = writeFixture(100, 3, 2);
        Path segmentPath = root().resolve("segments").resolve(FileLogFormat.segmentName(1));
        long physicalSize = Files.size(segmentPath);
        assertTrue(physicalSize > published.getSegments().get(0).getPublishedEndOffset());
        
        FileLogStorage reopened = store();
        assertTrue(reopened.init(context("group")));
        assertEquals(published, reopened.getManifest());
        assertEquals(published.getSegments().get(0).getPublishedEndOffset(),
            Files.size(segmentPath));
        assertEquals(3L * FileLogFormat.INDEX_ENTRY_SIZE,
            Files.size(root().resolve("index").resolve(FileLogFormat.indexName(1))));
        reopened.shutdown();
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void validatesPublishedAndOrphanSegmentsBeforeRecoveryBarrier(boolean failBarrier)
        throws Exception {
        Manifest published = writeFixture(100, 2, 1);
        Path segmentPath = root().resolve("segments").resolve(FileLogFormat.segmentName(1));
        Path orphan = createOrphan(published);
        byte[] segmentBytes = Files.readAllBytes(segmentPath);
        byte[] orphanBytes = Files.readAllBytes(orphan);
        byte[] manifestBytes = Files.readAllBytes(root().resolve("manifest"));
        Path indexPath = root().resolve("index").resolve(FileLogFormat.indexName(1));
        byte[] indexBytes = new byte[] {9, 8, 7};
        Files.write(indexPath, indexBytes);
        AtomicInteger scannedEntries = new AtomicInteger();
        AtomicInteger rootForces = new AtomicInteger();
        AtomicBoolean orphanValidated = new AtomicBoolean();
        AtomicBoolean barrierReached = new AtomicBoolean();
        FileLogStorage reopened = store();
        try (MockedStatic<FileLogStorage> directoryForces =
            mockStatic(FileLogStorage.class, CALLS_REAL_METHODS);
            MockedStatic<Index> indexes = mockStatic(Index.class, CALLS_REAL_METHODS);
            MockedStatic<Segment> segments =
                mockStatic(Segment.class, CALLS_REAL_METHODS)) {
            directoryForces.when(() -> FileLogStorage.forceDirectory(any(Path.class)))
                .thenAnswer(invocation -> {
                    Path path = invocation.getArgument(0);
                    if (path.equals(root()) && rootForces.incrementAndGet() == 2) {
                        assertEquals(2, scannedEntries.get());
                        assertTrue(orphanValidated.get());
                        assertArrayEquals(segmentBytes, Files.readAllBytes(segmentPath));
                        assertArrayEquals(orphanBytes, Files.readAllBytes(orphan));
                        assertEquals(2L * FileLogFormat.INDEX_ENTRY_SIZE, Files.size(indexPath));
                        barrierReached.set(true);
                        if (failBarrier) {
                            throw new IOException("recovery directory force failed");
                        }
                    }
                    return invocation.callRealMethod();
                });
            indexes.when(() -> Index.create(any(Path.class), anyLong()))
                .thenAnswer(invocation -> {
                    assertEquals(indexPath, invocation.getArgument(0));
                    Index index = spy((Index) invocation.callRealMethod());
                    doAnswer(append -> {
                        Object result = append.callRealMethod();
                        scannedEntries.incrementAndGet();
                        return result;
                    }).when(index).append(anyLong(), anyLong(), anyInt());
                    return index;
                });
            segments.when(() -> Segment.open(any(Path.class), anyLong()))
                .thenAnswer(invocation -> {
                    Segment segment = spy((Segment) invocation.callRealMethod());
                    if (orphan.equals(invocation.getArgument(0))) {
                        doAnswer(header -> {
                            Object result = header.callRealMethod();
                            orphanValidated.set(true);
                            return result;
                        }).when(segment).getHeader();
                    }
                    return segment;
                });
            assertEquals(!failBarrier, reopened.init(context("group")));
        }
        assertTrue(barrierReached.get());
        assertArrayEquals(manifestBytes, Files.readAllBytes(root().resolve("manifest")));
        if (failBarrier) {
            assertArrayEquals(segmentBytes, Files.readAllBytes(segmentPath));
            assertArrayEquals(orphanBytes, Files.readAllBytes(orphan));
            assertEquals(2L * FileLogFormat.INDEX_ENTRY_SIZE, Files.size(indexPath));
        } else {
            assertEquals(published.getSegments().get(0).getPublishedEndOffset(),
                Files.size(segmentPath));
            assertFalse(Files.exists(orphan));
        }
    }
    
    @Test
    void rejectsCorruptionBeforeRecoveryBarrierWithoutChangingAuthoritativeFiles()
        throws Exception {
        Manifest published = writeFixture(100, 2, 1);
        Path segmentPath = root().resolve("segments").resolve(FileLogFormat.segmentName(1));
        Path orphan = createOrphan(published);
        byte[] corrupt = Files.readAllBytes(segmentPath);
        int offset = (int) published.getSegments().get(0).getPublishedEndOffset() - 1;
        corrupt[offset] ^= 1;
        Files.write(segmentPath, corrupt);
        byte[] segmentBytes = Files.readAllBytes(segmentPath);
        byte[] orphanBytes = Files.readAllBytes(orphan);
        byte[] manifestBytes = Files.readAllBytes(root().resolve("manifest"));
        AtomicInteger rootForces = new AtomicInteger();
        try (MockedStatic<FileLogStorage> directoryForces =
            mockStatic(FileLogStorage.class, CALLS_REAL_METHODS)) {
            directoryForces.when(() -> FileLogStorage.forceDirectory(any(Path.class)))
                .thenAnswer(invocation -> {
                    if (root().equals(invocation.getArgument(0))) {
                        rootForces.incrementAndGet();
                    }
                    return invocation.callRealMethod();
                });
            assertFalse(store().init(context("group")));
        }
        assertEquals(1, rootForces.get());
        assertArrayEquals(segmentBytes, Files.readAllBytes(segmentPath));
        assertArrayEquals(orphanBytes, Files.readAllBytes(orphan));
        assertArrayEquals(manifestBytes, Files.readAllBytes(root().resolve("manifest")));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"truncate", "force"})
    void recoveryTailIoFailureFailsInitializationBeforeOpeningWrites(String operation)
        throws Exception {
        Manifest published = writeFixture(100, 2, 1);
        Path segmentPath = root().resolve("segments").resolve(FileLogFormat.segmentName(1));
        Path orphan = createOrphan(published);
        byte[] bytes = Files.readAllBytes(segmentPath);
        byte[] manifestBytes = Files.readAllBytes(root().resolve("manifest"));
        Path indexPath = root().resolve("index").resolve(FileLogFormat.indexName(1));
        byte[] oldIndex = new byte[] {9, 8, 7};
        Files.write(indexPath, oldIndex);
        FileLogStorage reopened = store();
        try (MockedStatic<Segment> segments =
            mockStatic(Segment.class, CALLS_REAL_METHODS)) {
            segments.when(() -> Segment.open(any(Path.class), anyLong()))
                .thenAnswer(invocation -> {
                    Segment segment = spy((Segment) invocation.callRealMethod());
                    if (segmentPath.equals(invocation.getArgument(0))) {
                        if ("truncate".equals(operation)) {
                            doThrow(new IOException("tail truncate failed")).when(segment)
                                .truncateToLogicalEnd();
                        } else {
                            doThrow(new IOException("tail force failed")).when(segment).force();
                        }
                    }
                    return segment;
                });
            assertFalse(reopened.init(context("group")));
        }
        byte[] after = Files.readAllBytes(segmentPath);
        int publishedEnd = (int) published.getSegments().get(0).getPublishedEndOffset();
        assertArrayEquals(java.util.Arrays.copyOf(bytes, publishedEnd),
            java.util.Arrays.copyOf(after, publishedEnd));
        if ("truncate".equals(operation)) {
            assertArrayEquals(bytes, after);
        }
        assertArrayEquals(manifestBytes, Files.readAllBytes(root().resolve("manifest")));
        assertEquals(2L * FileLogFormat.INDEX_ENTRY_SIZE, Files.size(indexPath));
        assertTrue(Files.exists(orphan));
        assertEquals(0, reopened.getFirstLogIndex());
        assertTrue(reopened.init(context("group")));
    }
    
    @Test
    void rejectsStableCorruptionWithoutTruncatingTheFile() throws Exception {
        Manifest published = writeFixture(1, 2, 1);
        Path segmentPath = root().resolve("segments").resolve(FileLogFormat.segmentName(1));
        byte[] bytes = Files.readAllBytes(segmentPath);
        bytes[FileLogFormat.SEGMENT_HEADER_SIZE + FileLogFormat.RECORD_HEADER_SIZE] ^= 1;
        Files.write(segmentPath, bytes);
        long sizeBefore = Files.size(segmentPath);
        
        assertFalse(store().init(context("group")));
        assertEquals(sizeBefore, Files.size(segmentPath));
        assertTrue(sizeBefore > published.getSegments().get(0).getPublishedEndOffset());
    }
    
    @Test
    void rejectsMissingManifestWithDataButAcceptsAStaleTemporaryManifest()
        throws Exception {
        Files.createDirectories(root().resolve("segments"));
        Files.createDirectories(root().resolve("index"));
        Files.write(root().resolve("segments").resolve("unknown.seg"), new byte[] {1});
        assertFalse(store().init(context("group")));
        
        Path clean = directory.resolve("clean");
        Files.createDirectories(clean.resolve("file-log"));
        Files.write(clean.resolve("file-log/manifest.tmp"), new byte[] {1, 2, 3});
        FileLogStorage cleanStore = new FileLogStorage(clean, new RaftOptions());
        stores.add(cleanStore);
        assertTrue(cleanStore.init(context("group")));
        cleanStore.shutdown();
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void retainsOrphanHighWaterBeforeCleanup(boolean failCleanup) throws Exception {
        Manifest published = writeFixture(1, 1, 0);
        Path orphan;
        try (Segment segment =
            Segment.create(root().resolve("segments"), published.getStoreId(), 700)) {
            orphan = segment.getPath();
        }
        byte[] orphanBytes = Files.readAllBytes(orphan);
        FileLogStorage reopened = store();
        try (MockedStatic<Files> files = mockStatic(Files.class, invocation -> {
            if (failCleanup && "deleteIfExists".equals(invocation.getMethod().getName())
                && orphan.equals(invocation.getArgument(0))) {
                throw new IOException("orphan cleanup failed");
            }
            return invocation.callRealMethod();
        })) {
            assertTrue(reopened.init(context("group")));
            assertEquals(failCleanup, Files.exists(orphan));
            if (failCleanup) {
                assertArrayEquals(orphanBytes, Files.readAllBytes(orphan));
            }
            assertTrue(reopened.reset(10));
            assertTrue(reopened.getManifest().getSegments().isEmpty());
            assertTrue(reopened.appendEntry(entry(10)));
            assertEquals(701, reopened.getManifest().getSegments().get(0).getSegmentId());
        } finally {
            reopened.shutdown();
        }
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"maximum", "overflow"})
    void rejectsExhaustedFileIdsBeforeCleanupOrOverwrite(String kind) throws Exception {
        Manifest published = writeFixture(1, 1, 1);
        Path high;
        if ("maximum".equals(kind)) {
            try (Segment segment = Segment.create(root().resolve("segments"),
                published.getStoreId(), Long.MAX_VALUE)) {
                high = segment.getPath();
            }
        } else {
            high = root().resolve("segments/09223372036854775808.seg");
            Files.write(high, new byte[] {7, 8, 9});
        }
        byte[] highBytes = Files.readAllBytes(high);
        Path publishedPath = root().resolve("segments").resolve(FileLogFormat.segmentName(1));
        byte[] publishedBytes = Files.readAllBytes(publishedPath);
        byte[] manifestBytes = Files.readAllBytes(root().resolve("manifest"));
        FileLogStorage reopened = store();
        assertFalse(reopened.init(context("group")));
        assertArrayEquals(highBytes, Files.readAllBytes(high));
        assertArrayEquals(publishedBytes, Files.readAllBytes(publishedPath));
        assertArrayEquals(manifestBytes, Files.readAllBytes(root().resolve("manifest")));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"missingPeers", "emptyPeers", "oldLearnersWithoutOldPeers"})
    void rejectsPublishedInvalidConfigurationBeforeTailAndOrphanCleanup(String kind)
        throws Exception {
        Manifest original = writeFixture(1, 1, 0);
        LogEntry invalid = new LogEntry(EntryType.ENTRY_TYPE_CONFIGURATION);
        invalid.setId(new LogId(1, 2));
        if ("emptyPeers".equals(kind)) {
            invalid.setPeers(List.of());
        } else if ("oldLearnersWithoutOldPeers".equals(kind)) {
            invalid.setPeers(List.of(new PeerId("127.0.0.1", 8081)));
            invalid.setOldLearners(List.of(new PeerId("127.0.0.1", 8082)));
        }
        ByteBuffer record = rawRecord(invalid);
        Path segmentPath = root().resolve("segments").resolve(FileLogFormat.segmentName(1));
        byte[] header = java.util.Arrays.copyOf(Files.readAllBytes(segmentPath),
            FileLogFormat.SEGMENT_HEADER_SIZE);
        int end = header.length + record.remaining();
        ByteBuffer tail = new LogRecordCodec(LogEntryV2CodecFactory.getInstance()).encode(entry(2));
        ByteBuffer bytes = ByteBuffer.allocate(end + tail.remaining());
        bytes.put(header).put(record).put(tail);
        Files.write(segmentPath, bytes.array());
        Manifest published = new Manifest(FileLogFormat.VERSION, original.getStoreId(), "group",
            1, 1, List.of(new Manifest.SegmentMeta(1, 1, 1, header.length, end)));
        byte[] manifestBytes = store().encodeManifest(published).array();
        Files.write(root().resolve("manifest"), manifestBytes);
        Path orphan = createOrphan(published);
        byte[] orphanBytes = Files.readAllBytes(orphan);
        assertFalse(store().init(context("group")));
        assertArrayEquals(bytes.array(), Files.readAllBytes(segmentPath));
        assertArrayEquals(orphanBytes, Files.readAllBytes(orphan));
        assertArrayEquals(manifestBytes, Files.readAllBytes(root().resolve("manifest")));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"empty", "partial", "magic", "checksum"})
    void preservesUnknownSegmentsAcrossRecoveryAndAllocation(String corruption) throws Exception {
        Manifest published = writeFixture(100, 2, 1);
        Path orphan = createOrphan(published);
        byte[] bytes = Files.readAllBytes(orphan);
        if ("empty".equals(corruption)) {
            bytes = new byte[0];
        } else if ("partial".equals(corruption)) {
            bytes = java.util.Arrays.copyOf(bytes, 13);
        } else {
            bytes["magic".equals(corruption) ? 0 : 39] ^= 1;
        }
        Files.write(orphan, bytes);
        byte[] manifestBytes = Files.readAllBytes(root().resolve("manifest"));
        Logger logger = (Logger) Loggers.RAFT;
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            logger.setLevel(Level.WARN);
            for (int attempt = 0; attempt < 2; attempt++) {
                appender.list.clear();
                FileLogStorage recovered = store();
                assertTrue(recovered.init(context("group")));
                assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN
                    && event.getFormattedMessage().contains("unknown ownership")
                    && event.getFormattedMessage().contains(orphan.toString())),
                    "Recovery must warn about the preserved segment: " + orphan);
                assertArrayEquals(manifestBytes, Files.readAllBytes(root().resolve("manifest")));
                assertArrayEquals(bytes, Files.readAllBytes(orphan));
                assertEquals(entry(100), recovered.getEntry(100));
                assertEquals(entry(101), recovered.getEntry(101));
                recovered.shutdown();
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
        FileLogStorage recovered = store();
        assertTrue(recovered.init(context("group")));
        long orphanId = FileLogFormat.parseFileId(orphan.getFileName().toString(), ".seg");
        assertTrue(recovered.reset(10));
        assertTrue(recovered.appendEntry(entry(10)));
        assertTrue(recovered.getManifest().getSegments().get(0).getSegmentId() > orphanId);
        recovered.shutdown();
        FileLogStorage reopened = store();
        assertTrue(reopened.init(context("group")));
        assertEquals(entry(10), reopened.getEntry(10));
        assertArrayEquals(bytes, Files.readAllBytes(orphan));
        assertTrue(reopened.appendEntry(entry(11)));
    }
    
    @Test
    void actualOrphanIoFailureStillRejectsRecovery() throws Exception {
        Manifest published = writeFixture(100, 2, 1);
        Path orphan = createOrphan(published);
        byte[] manifestBytes = Files.readAllBytes(root().resolve("manifest"));
        byte[] orphanBytes = Files.readAllBytes(orphan);
        try (MockedStatic<Segment> segments = mockStatic(Segment.class, CALLS_REAL_METHODS)) {
            segments.when(() -> Segment.open(orphan, FileLogFormat.SEGMENT_HEADER_SIZE))
                .thenThrow(new IOException("Injected OS read failure"));
            assertFalse(store().init(context("group")));
        }
        assertArrayEquals(manifestBytes, Files.readAllBytes(root().resolve("manifest")));
        assertArrayEquals(orphanBytes, Files.readAllBytes(orphan));
    }
    
    @Test
    void unknownSegmentIdExhaustionDoesNotOverwriteFiles() throws Exception {
        writeFixture(100, 2, 0);
        Path orphan = root().resolve("segments").resolve(FileLogFormat.segmentName(Long.MAX_VALUE));
        Files.createFile(orphan);
        assertFalse(store().init(context("group")));
        assertEquals(0, Files.size(orphan));
    }
    
    @Test
    void unreferencedDirectoryCannotBeMistakenForAnIncompleteHeader() throws Exception {
        writeFixture(100, 2, 0);
        Path orphan = root().resolve("segments").resolve(FileLogFormat.segmentName(2));
        Files.createDirectory(orphan);
        assertFalse(store().init(context("group")));
        assertTrue(Files.isDirectory(orphan));
    }
    
    @Test
    void unreferencedSymbolicLinkCannotBeMistakenForAnIncompleteHeader() throws Exception {
        writeFixture(100, 2, 0);
        Path target = directory.resolve("foreign");
        Files.write(target, new byte[] {42});
        Path orphan = root().resolve("segments").resolve(FileLogFormat.segmentName(2));
        directoryLink(orphan, target);
        assertFalse(store().init(context("group")));
        assertArrayEquals(new byte[] {42}, Files.readAllBytes(target));
        assertTrue(Files.isSymbolicLink(orphan));
    }
    
    @Test
    void validUnpublishedHeaderMustMatchItsFilename() throws Exception {
        Manifest published = writeFixture(100, 2, 0);
        Path orphan = createOrphan(published);
        Path wrongName = orphan.resolveSibling(FileLogFormat.segmentName(3));
        Files.move(orphan, wrongName);
        assertFalse(store().init(context("group")));
        assertTrue(Files.exists(wrongName));
    }
    
    @Test
    void rejectsUnsupportedUnpublishedVersion() throws Exception {
        Manifest published = writeFixture(100, 2, 1);
        Path orphan = createOrphan(published);
        byte[] bytes = Files.readAllBytes(orphan);
        ByteBuffer.wrap(bytes).putShort(4, (short) (FileLogFormat.VERSION + 1));
        Files.write(orphan, bytes);
        assertFalse(store().init(context("group")));
        assertArrayEquals(bytes, Files.readAllBytes(orphan));
    }
    
    @Test
    void rejectsAnUnreferencedSegmentOwnedByAnotherStore() throws Exception {
        FileLogStorage creator = store();
        assertTrue(creator.init(context("group")));
        creator.shutdown();
        try (Segment ignored = Segment.create(root().resolve("segments"),
            java.util.UUID.randomUUID(), 1)) {
            // The valid header proves that this segment does not belong to the current store.
        }
        assertFalse(store().init(context("group")));
    }
    
    @Test
    void rejectsWrongGroupAndMissingPublishedSegment() throws Exception {
        FileLogStorage creator = store();
        assertTrue(creator.init(context("group")));
        creator.shutdown();
        
        assertFalse(store().init(context("other-group")));
        
        FileLogStorage publisher = store();
        assertTrue(publisher.init(context("group")));
        Manifest empty = publisher.getManifest();
        Manifest missing =
            new Manifest(FileLogFormat.VERSION, empty.getStoreId(), empty.getGroupId(), 10, 10,
                List.of(new Manifest.SegmentMeta(1, 10, 10,
                    FileLogFormat.SEGMENT_HEADER_SIZE, FileLogFormat.SEGMENT_HEADER_SIZE + 24)));
        publisher.publishManifest(missing);
        publisher.shutdown();
        assertFalse(store().init(context("group")));
    }
    
    @Test
    void recoversAContinuousRangeAcrossSegments() throws Exception {
        FileLogStorage creator = new FileLogStorage(directory, new RaftOptions(), 80);
        stores.add(creator);
        assertTrue(creator.init(context("group")));
        assertTrue(creator.truncatePrefix(50));
        assertEquals(2, creator.appendEntries(List.of(entry(50), entry(51))));
        Manifest published = creator.getManifest();
        assertEquals(2, published.getSegments().size());
        ByteBuffer encoded = creator.encodeManifest(published);
        assertEquals(52 + 5 + 2 * 40 + 8, encoded.remaining());
        assertEquals(published, creator.decodeManifest(encoded));
        creator.shutdown();
        
        FileLogStorage reopened = store();
        try {
            assertTrue(reopened.init(context("group")));
            assertEquals(entry(50), reopened.getEntry(50));
            assertEquals(entry(51), reopened.getEntry(51));
            for (Manifest.SegmentMeta segment : published.getSegments()) {
                assertEquals(FileLogFormat.INDEX_ENTRY_SIZE,
                    Files.size(root().resolve("index")
                        .resolve(FileLogFormat.indexName(segment.getSegmentId()))));
            }
        } finally {
            reopened.shutdown();
        }
    }
    
    @ParameterizedTest
    @ValueSource(
        strings = {"magic", "version", "headerSize", "descriptorSize", "checksum", "truncated"})
    void rejectsIllegalManifestFormats(String kind) throws Exception {
        FileLogStorage store = store();
        Manifest manifest = new Manifest(FileLogFormat.VERSION, java.util.UUID.randomUUID(),
            "group", 1, 1, List.of(new Manifest.SegmentMeta(1, 1, 1, 40, 80)));
        byte[] bytes = store.encodeManifest(manifest).array();
        switch (kind) {
            case "magic":
                ByteBuffer.wrap(bytes).putInt(0, 0);
                break;
            case "version":
                ByteBuffer.wrap(bytes).putShort(4, (short) 99);
                break;
            case "headerSize":
                ByteBuffer.wrap(bytes).putShort(6, (short) 48);
                break;
            case "descriptorSize":
                bytes = java.util.Arrays.copyOf(bytes, bytes.length + 8);
                ByteBuffer.wrap(bytes).putInt(8, bytes.length);
                break;
            case "truncated":
                bytes = java.util.Arrays.copyOf(bytes, bytes.length - 1);
                break;
            default:
                bytes[bytes.length - 1] ^= 1;
        }
        if (!kind.equals("checksum") && !kind.equals("truncated")) {
            ByteBuffer.wrap(bytes).putLong(bytes.length - 8,
                FileLogFormat.crc64(bytes, 0, bytes.length - 8));
        }
        ByteBuffer encoded = ByteBuffer.wrap(bytes);
        assertThrows(java.io.IOException.class, () -> store.decodeManifest(encoded));
    }
    
    @Test
    void rejectsInvalidCompactDescriptorRanges() {
        for (Manifest.SegmentMeta metadata : List.of(
            new Manifest.SegmentMeta(0, 100, 100, 40, 80),
            new Manifest.SegmentMeta(1, 101, 101, 40, 80),
            new Manifest.SegmentMeta(1, 100, 99, 40, 80),
            new Manifest.SegmentMeta(1, 100, 100, 39, 80),
            new Manifest.SegmentMeta(1, 100, 100, 40, 40),
            new Manifest.SegmentMeta(1, 100, 100, 40, Segment.MAX_SEGMENT_BYTES + 1),
            new Manifest.SegmentMeta(1, 100, Long.MAX_VALUE, 40, 80))) {
            Manifest invalid = new Manifest(FileLogFormat.VERSION, java.util.UUID.randomUUID(),
                "group", 100, metadata.getLastIndex(), List.of(metadata));
            assertThrows(java.io.IOException.class, () -> store().encodeManifest(invalid));
        }
    }
    
    @Test
    void rejectsLogGapWithoutChangingManifestOrSegment() throws Exception {
        Manifest original = writeFixture(100, 2, 1);
        Path path = root().resolve("segments").resolve(FileLogFormat.segmentName(1));
        byte[] bytes = Files.readAllBytes(path);
        LogRecordCodec codec = new LogRecordCodec(LogEntryV2CodecFactory.getInstance());
        LogEntry wrong = new LogEntry(EntryType.ENTRY_TYPE_DATA);
        wrong.setId(new LogId(101, 2));
        wrong.setData(ByteBuffer.wrap(new byte[] {100}));
        ByteBuffer record = codec.encode(wrong);
        int originalSize = codec.recordSize(ByteBuffer.wrap(bytes,
            FileLogFormat.SEGMENT_HEADER_SIZE, FileLogFormat.RECORD_HEADER_SIZE).slice());
        assertEquals(originalSize, record.remaining());
        record.get(bytes, FileLogFormat.SEGMENT_HEADER_SIZE, record.remaining());
        Files.write(path, bytes);
        byte[] manifest = Files.readAllBytes(root().resolve("manifest"));
        Path index = root().resolve("index").resolve(FileLogFormat.indexName(1));
        byte[] oldIndex = new byte[] {9, 8, 7};
        Files.write(index, oldIndex);
        assertFalse(store().init(context("group")));
        assertArrayEquals(bytes, Files.readAllBytes(path));
        assertArrayEquals(manifest, Files.readAllBytes(root().resolve("manifest")));
        assertEquals(0, Files.size(index));
        assertTrue(bytes.length > original.getSegments().get(0).getPublishedEndOffset());
    }
    
    @Test
    void recoversRetainedRangeAfterSkippedPrefix() throws Exception {
        Manifest original = writeFixture(100, 3, 1);
        Path path = root().resolve("segments").resolve(FileLogFormat.segmentName(1));
        byte[] bytes = Files.readAllBytes(path);
        LogRecordCodec codec = new LogRecordCodec(LogEntryV2CodecFactory.getInstance());
        int firstSize = codec.recordSize(ByteBuffer.wrap(bytes,
            FileLogFormat.SEGMENT_HEADER_SIZE, FileLogFormat.RECORD_HEADER_SIZE).slice());
        long firstOffset = FileLogFormat.SEGMENT_HEADER_SIZE + firstSize;
        long end = original.getSegments().get(0).getPublishedEndOffset();
        FileLogStorage publisher = store();
        assertTrue(publisher.init(context("group")));
        Manifest retained = new Manifest(FileLogFormat.VERSION, original.getStoreId(), "group",
            101, 102, List.of(new Manifest.SegmentMeta(1, 101, 102, firstOffset, end)));
        Path indexPath = root().resolve("index").resolve(FileLogFormat.indexName(1));
        try (MockedStatic<Index> indexes = mockStatic(Index.class, CALLS_REAL_METHODS)) {
            assertTrue(publisher.truncatePrefix(101));
            indexes.verify(() -> Index.create(indexPath, 101));
        }
        assertEquals(retained, publisher.getManifest());
        publisher.shutdown();
        // An obsolete prefix is outside the published range and must not be decoded.
        bytes[FileLogFormat.SEGMENT_HEADER_SIZE] ^= 1;
        Files.write(path, bytes);
        Files.write(indexPath, new byte[] {1});
        FileLogStorage reopened = store();
        try {
            assertTrue(reopened.init(context("group")));
            assertEquals(retained, reopened.getManifest());
            assertEquals(end, Files.size(path));
            assertEquals(24, Files.size(indexPath));
            try (Index index = Index.open(indexPath, 101, 102)) {
                assertEquals(firstOffset, index.read(101, firstOffset, end).getOffset());
            }
        } finally {
            reopened.shutdown();
        }
    }
    
    @Test
    void rejectsIdentityMismatchWithoutChangingPublishedFiles() throws Exception {
        writeFixture(100, 2, 1);
        Path path = root().resolve("segments").resolve(FileLogFormat.segmentName(1));
        Path manifestPath = root().resolve("manifest");
        byte[] manifest = Files.readAllBytes(manifestPath);
        byte[] original = Files.readAllBytes(path);
        for (int offset : new int[] {8, 24}) {
            byte[] changed = original.clone();
            changed[offset] ^= 1;
            ByteBuffer.wrap(changed).putLong(FileLogFormat.SEGMENT_HEADER_SIZE - 8,
                FileLogFormat.crc64(changed, 0, FileLogFormat.SEGMENT_HEADER_SIZE - 8));
            Files.write(path, changed);
            assertFalse(store().init(context("group")));
            assertArrayEquals(changed, Files.readAllBytes(path));
            assertArrayEquals(manifest, Files.readAllBytes(manifestPath));
        }
    }
    
    private Manifest writeFixture(long firstIndex, int publishedCount, int tailCount)
        throws Exception {
        FileLogStorage creator = store();
        Manifest manifest;
        try {
            assertTrue(creator.init(context("group")));
            assertTrue(creator.truncatePrefix(firstIndex));
            for (int i = 0; i < publishedCount; i++) {
                assertTrue(creator.appendEntry(entry(firstIndex + i)));
            }
            manifest = creator.getManifest();
        } finally {
            creator.shutdown();
        }
        Manifest.SegmentMeta last = manifest.getSegments().get(manifest.getSegments().size() - 1);
        Path segmentPath =
            root().resolve("segments").resolve(FileLogFormat.segmentName(last.getSegmentId()));
        LogRecordCodec codec = new LogRecordCodec(LogEntryV2CodecFactory.getInstance());
        try (Segment segment = Segment.open(segmentPath, last.getPublishedEndOffset())) {
            for (int i = 0; i < tailCount; i++) {
                segment.append(codec.encode(entry(firstIndex + publishedCount + i)));
            }
            segment.force();
        }
        return manifest;
    }
    
    private Path createOrphan(Manifest manifest) throws IOException {
        try (Segment segment =
            Segment.create(root().resolve("segments"), manifest.getStoreId(), 2)) {
            segment.force();
            return segment.getPath();
        }
    }
    
    private static LogEntry entry(long index) {
        LogEntry entry = new LogEntry(EntryType.ENTRY_TYPE_DATA);
        entry.setId(new LogId(index, 2));
        entry.setData(ByteBuffer.wrap(new byte[] {(byte) index}));
        return entry;
    }
    
    private static ByteBuffer rawRecord(LogEntry entry) {
        byte[] payload = LogEntryV2CodecFactory.getInstance().encoder().encode(entry);
        ByteBuffer record = ByteBuffer.allocate(FileLogFormat.RECORD_OVERHEAD + payload.length);
        record.putInt(FileLogFormat.RECORD_MAGIC).putShort(FileLogFormat.VERSION)
            .putShort((short) FileLogFormat.RECORD_HEADER_SIZE).putInt(payload.length);
        record.putInt(FileLogFormat.crc32(record.array(), 0, 12));
        record.put(payload).putLong(FileLogFormat.crc64(payload, 0, payload.length));
        return record.flip();
    }
    
    private FileLogStorage store() {
        return store(directory);
    }
    
    private FileLogStorage store(Path logUri) {
        FileLogStorage store = new FileLogStorage(logUri, new RaftOptions());
        stores.add(store);
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
    
    private Path root() {
        return directory.resolve("file-log");
    }
    
    private static LogStorageOptions context(String group) {
        LogStorageOptions result = new LogStorageOptions();
        result.setGroupId(group);
        result.setConfigurationManager(new ConfigurationManager());
        result.setLogEntryCodecFactory(LogEntryV2CodecFactory.getInstance());
        return result;
    }
}
