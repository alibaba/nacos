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
import com.alipay.sofa.jraft.conf.ConfigurationEntry;
import com.alipay.sofa.jraft.entity.EnumOutter.EntryType;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.LogId;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.codec.LogEntryCodecFactory;
import com.alipay.sofa.jraft.entity.codec.LogEntryEncoder;
import com.alipay.sofa.jraft.entity.codec.v2.LogEntryV2CodecFactory;
import com.alipay.sofa.jraft.option.LogStorageOptions;
import com.alipay.sofa.jraft.option.RaftOptions;
import com.alipay.sofa.jraft.util.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class FileLogStorageTest {
    
    @TempDir
    Path directory;
    
    private final List<FileLogStorage> stores = new ArrayList<>();
    
    @AfterEach
    void closeStores() {
        stores.forEach(FileLogStorage::shutdown);
    }
    
    @Test
    void skipsDirectoryChannelOnWindows() throws Exception {
        try (MockedStatic<Platform> platform = mockStatic(Platform.class);
            MockedStatic<FileChannel> channels = mockStatic(FileChannel.class)) {
            platform.when(Platform::isWindows).thenReturn(true);
            FileLogStorage.forceDirectory(directory);
            channels.verifyNoInteractions();
        }
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsInvalidDirectoryBeforeOpeningChannel(boolean windows) throws Exception {
        Path missing = directory.resolve("missing");
        Path regularFile = Files.write(directory.resolve("regular"), new byte[] {1});
        try (MockedStatic<Platform> platform = mockStatic(Platform.class);
            MockedStatic<FileChannel> channels = mockStatic(FileChannel.class)) {
            platform.when(Platform::isWindows).thenReturn(windows);
            assertThrows(IOException.class, () -> FileLogStorage.forceDirectory(missing));
            assertThrows(IOException.class, () -> FileLogStorage.forceDirectory(regularFile));
            channels.verifyNoInteractions();
        }
    }
    
    @Test
    void forcesDirectoryChannelOutsideWindows() throws Exception {
        FileChannel channel = mock(FileChannel.class);
        try (MockedStatic<Platform> platform = mockStatic(Platform.class);
            MockedStatic<FileChannel> channels = mockStatic(FileChannel.class)) {
            platform.when(Platform::isWindows).thenReturn(false);
            channels.when(() -> FileChannel.open(directory, StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS)).thenReturn(channel);
            FileLogStorage.forceDirectory(directory);
            channels.verify(() -> FileChannel.open(directory, StandardOpenOption.READ,
                LinkOption.NOFOLLOW_LINKS));
            verify(channel).force(true);
            verify(channel).close();
        }
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"open", "force", "close"})
    void propagatesDirectoryChannelFailuresOutsideWindows(String operation) throws Exception {
        IOException failure = new IOException("Directory " + operation + " failed");
        FileChannel channel = mock(FileChannel.class);
        try (MockedStatic<Platform> platform = mockStatic(Platform.class);
            MockedStatic<FileChannel> channels = mockStatic(FileChannel.class)) {
            platform.when(Platform::isWindows).thenReturn(false);
            if ("open".equals(operation)) {
                channels.when(() -> FileChannel.open(directory, StandardOpenOption.READ,
                    LinkOption.NOFOLLOW_LINKS)).thenThrow(failure);
            } else {
                channels.when(() -> FileChannel.open(directory, StandardOpenOption.READ,
                    LinkOption.NOFOLLOW_LINKS)).thenReturn(channel);
                if ("force".equals(operation)) {
                    doThrow(failure).when(channel).force(true);
                } else {
                    doThrow(failure).when(channel).close();
                }
            }
            assertSame(failure,
                assertThrows(IOException.class, () -> FileLogStorage.forceDirectory(directory)));
            if (!"open".equals(operation)) {
                verify(channel).close();
            }
        }
    }
    
    @ParameterizedTest
    @CsvSource({"true, segment", "false, segment", "true, manifest", "false, manifest"})
    void fileForceFailureDisablesWritesOnEveryPlatform(boolean windows, String file)
        throws Exception {
        FileLogStorage store = new FileLogStorage(directory, new RaftOptions(),
            "segment".equals(file) ? 160 : 4096);
        stores.add(store);
        assertTrue(store.init(context(new ConfigurationManager())));
        assertEquals(3, store.appendEntries(List.of(entry(1), entry(2), entry(3))));
        byte[] published = Files.readAllBytes(root().resolve("manifest"));
        try (MockedStatic<Platform> platform = mockStatic(Platform.class);
            MockedStatic<Segment> segments = mockStatic(Segment.class, CALLS_REAL_METHODS);
            MockedStatic<FileChannel> channels =
                mockStatic(FileChannel.class, CALLS_REAL_METHODS)) {
            platform.when(Platform::isWindows).thenReturn(windows);
            if ("segment".equals(file)) {
                segments.when(() -> Segment.create(any(Path.class), any(UUID.class), anyLong()))
                    .thenAnswer(invocation -> {
                        Segment segment = spy((Segment) invocation.callRealMethod());
                        doThrow(new IOException("Segment force failed")).when(segment).force();
                        return segment;
                    });
            } else {
                channels.when(() -> FileChannel.open(root().resolve("manifest.tmp"),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))
                    .thenAnswer(invocation -> {
                        FileChannel channel = spy((FileChannel) invocation.callRealMethod());
                        doThrow(new IOException("Manifest force failed")).when(channel).force(true);
                        return channel;
                    });
            }
            assertFalse(store.appendEntry(entry(4)));
        }
        assertArrayEquals(published, Files.readAllBytes(root().resolve("manifest")));
        assertRange(store, 1, 3);
        assertMutationDisabled(store);
        store.shutdown();
        assertTrue(store.init(context(new ConfigurationManager())));
        assertRange(store, 1, 3);
        assertTrue(store.appendEntry(entry(4)));
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsAsyncBeforeCreatingFiles(boolean syncMeta) {
        RaftOptions options = new RaftOptions();
        options.setSync(false);
        options.setSyncMeta(syncMeta);
        assertFalse(
            new FileLogStorage(directory, options).init(context(new ConfigurationManager())));
        assertFalse(Files.exists(root()));
    }
    
    @ParameterizedTest
    @CsvSource({"-1, false", "0, false", "1, false", "2, false",
        "-1, true", "0, true", "1, true", "2, true"})
    void persistsNewDirectoryAncestorsBeforePublishingOrOpeningWrites(int failedParent,
        boolean linked)
        throws Exception {
        Path base = directory.toRealPath();
        Path configuredBase = base;
        if (linked) {
            base = Files.createDirectory(base.resolve("real-base"));
            configuredBase = directoryLink(directory.resolve("entry"), base);
        }
        Path logUri = configuredBase.resolve("new-parent/group/log");
        Path logRoot = base.resolve("new-parent/group/log/file-log");
        List<Path> ancestors = new ArrayList<>();
        for (Path path = logRoot; path != null; path = path.getParent()) {
            ancestors.add(path);
        }
        Path failure = failedParent < 0 ? null : ancestors.get(failedParent + 1);
        List<Path> forced = new ArrayList<>();
        FileLogStorage store = new FileLogStorage(logUri, new RaftOptions());
        stores.add(store);
        try (MockedStatic<FileLogStorage> directoryForces =
            mockStatic(FileLogStorage.class, CALLS_REAL_METHODS)) {
            directoryForces.when(() -> FileLogStorage.forceDirectory(any(Path.class)))
                .thenAnswer(invocation -> {
                    Path path = invocation.getArgument(0);
                    forced.add(path);
                    if (failure != null && path.equals(failure)) {
                        throw new IOException("New directory parent force failed");
                    }
                    return invocation.callRealMethod();
                });
            if (failure != null) {
                assertFalse(store.init(context(new ConfigurationManager())));
                assertFalse(Files.exists(logRoot.resolve("manifest")));
                assertFalse(store.appendEntry(entry(1)));
                assertEquals(0, store.getFirstLogIndex());
                try (FileChannel channel = FileChannel.open(logRoot.resolve("LOCK"),
                    StandardOpenOption.WRITE); FileLock released = channel.tryLock()) {
                    assertNotNull(released, "Failed initialization must release the real lock");
                }
                // Created directories remain visible. Retry must still persist their ancestors.
                forced.clear();
                directoryForces.when(() -> FileLogStorage.forceDirectory(any(Path.class)))
                    .thenAnswer(invocation -> {
                        forced.add(invocation.getArgument(0));
                        return invocation.callRealMethod();
                    });
            }
            assertTrue(store.init(context(new ConfigurationManager())));
            List<Path> expected = new ArrayList<>();
            expected.add(logRoot.resolve("segments"));
            expected.add(logRoot.resolve("index"));
            expected.addAll(ancestors);
            assertTrue(forced.size() >= expected.size(), "All directory ancestors must be forced");
            assertEquals(expected, forced.subList(0, expected.size()));
            assertTrue(store.appendEntry(entry(1)));
            store.shutdown();
            assertTrue(store.init(context(new ConfigurationManager())));
            assertEquals(entry(1), store.getEntry(1));
        }
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"ancestor", "direct", "relative", "chain", "parent",
        "relativeLogUri", "missingParent"})
    void resolvesConfiguredDirectoryLinksBeforeCreatingManagedStorage(String kind)
        throws Exception {
        Path real = Files.createDirectories(directory.resolve("targets/release/current"));
        Path target = real;
        if ("relative".equals(kind)) {
            target = directory.relativize(real);
        } else if ("chain".equals(kind)) {
            target = directoryLink(directory.resolve("hop"), real).getFileName();
        }
        Path link = directoryLink(directory.resolve("entry"), target);
        Path configured = link.resolve("new/group/log");
        Path expected = real.resolve("new/group/log");
        if ("direct".equals(kind)) {
            configured = link;
            expected = real;
        } else if ("parent".equals(kind)) {
            configured = link.resolve("../new/group/log");
            expected = real.getParent().resolve("new/group/log");
        } else if ("missingParent".equals(kind)) {
            configured = link.resolve("created/../new/group/log");
        } else if ("relativeLogUri".equals(kind)) {
            configured = Path.of("").toAbsolutePath().relativize(configured);
        }
        FileLogStorage store = create(configured);
        assertFalse(Files.exists(expected.resolve("file-log")),
            "Construction must not create storage directories");
        assertTrue(store.init(context(new ConfigurationManager())));
        assertTrue(store.appendEntry(entry(1)));
        Path actualRoot = expected.toRealPath().resolve("file-log");
        assertTrue(Files.isRegularFile(actualRoot.resolve("manifest")));
        Manifest persisted = store.decodeManifest(
            ByteBuffer.wrap(Files.readAllBytes(actualRoot.resolve("manifest"))));
        assertEquals(store.getManifest().getStoreId(), persisted.getStoreId());
        store.shutdown();
        assertTrue(store.init(context(new ConfigurationManager())));
        assertEquals(entry(1), store.getEntry(1));
        assertTrue(Files.isSymbolicLink(link));
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realDirectoryAndAliasesShareIdentityAndExclusiveLock(boolean aliasFirst)
        throws Exception {
        Path real = Files.createDirectory(directory.resolve("real-log"));
        Path firstAlias = directoryLink(directory.resolve("first-alias"), real);
        Path secondAlias = directoryLink(directory.resolve("second-alias"), real);
        FileLogStorage first = create(aliasFirst ? firstAlias : real);
        FileLogStorage second = create(aliasFirst ? real : firstAlias);
        FileLogStorage third = create(secondAlias);
        assertTrue(first.init(context(new ConfigurationManager())));
        UUID storeId = first.getManifest().getStoreId();
        assertTrue(first.appendEntry(entry(1)));
        assertFalse(second.init(context(new ConfigurationManager())));
        assertFalse(third.init(context(new ConfigurationManager())));
        assertTrue(first.appendEntry(entry(2)));
        first.shutdown();
        assertTrue(second.init(context(new ConfigurationManager())));
        assertEquals(storeId, second.getManifest().getStoreId());
        assertEquals(entry(1), second.getEntry(1));
        assertEquals(entry(2), second.getEntry(2));
        assertFalse(third.init(context(new ConfigurationManager())));
        second.shutdown();
        assertTrue(third.init(context(new ConfigurationManager())));
        assertEquals(storeId, third.getManifest().getStoreId());
        assertEquals(entry(2), third.getEntry(2));
    }
    
    @Test
    void pinsAllOperationsAndLockUntilShutdownThenResolvesEntryAgain() throws Exception {
        Path first = Files.createDirectory(directory.resolve("first"));
        Path second = Files.createDirectory(directory.resolve("second"));
        Path link = directoryLink(directory.resolve("entry"), first);
        FileLogStorage store = create(link);
        assertTrue(store.init(context(new ConfigurationManager())));
        UUID firstId = store.getManifest().getStoreId();
        assertTrue(store.appendEntry(entry(1)));
        Files.delete(link);
        directoryLink(link, second);
        assertTrue(store.init(context(new ConfigurationManager())));
        assertEquals(firstId, store.getManifest().getStoreId());
        assertEquals(entry(1), store.getEntry(1));
        assertTrue(store.appendEntry(entry(2)));
        assertTrue(store.truncateSuffix(1));
        assertTrue(store.appendEntry(entry(2)));
        assertTrue(store.truncatePrefix(2));
        assertTrue(store.reset(10));
        assertTrue(store.appendEntry(entry(10)));
        assertRange(store, 10, 10);
        
        FileLogStorage other = create(second);
        assertTrue(other.init(context(new ConfigurationManager())));
        UUID secondId = other.getManifest().getStoreId();
        assertFalse(firstId.equals(secondId));
        assertTrue(other.appendEntry(entry(1)));
        other.shutdown();
        FileLogStorage probe = create(first);
        assertFalse(probe.init(context(new ConfigurationManager())));
        store.shutdown();
        assertTrue(store.init(context(new ConfigurationManager())));
        assertEquals(secondId, store.getManifest().getStoreId());
        assertEquals(entry(1), store.getEntry(1));
        assertTrue(store.appendEntry(entry(2)));
        assertTrue(probe.init(context(new ConfigurationManager())));
        assertEquals(firstId, probe.getManifest().getStoreId());
        assertRange(probe, 10, 10);
        assertEquals(entry(10), probe.getEntry(10));
        assertNull(probe.getEntry(1));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"attributes", "create"})
    void propagatesPathAccessErrorsAndCanRetryInitialization(String operation) throws Exception {
        Path real = Files.createDirectory(directory.resolve("real"));
        Path link = directoryLink(directory.resolve("entry"), real);
        Path configured = link.resolve("new-log");
        Path candidate = real.toRealPath().resolve("new-log");
        FileLogStorage store = create(configured);
        AtomicInteger injected = new AtomicInteger();
        try (MockedStatic<Files> files = mockStatic(Files.class, invocation -> {
            String method = invocation.getMethod().getName();
            Path path = invocation.getArgument(0);
            boolean attributes = "attributes".equals(operation)
                && "readAttributes".equals(method) && configured.toAbsolutePath().equals(path);
            boolean create = "create".equals(operation)
                && "createDirectory".equals(method) && candidate.equals(path);
            if (attributes || create) {
                injected.incrementAndGet();
                throw new AccessDeniedException(path.toString());
            }
            return invocation.callRealMethod();
        })) {
            assertFalse(store.init(context(new ConfigurationManager())));
            assertEquals(1, injected.get(), "The requested I/O failure must be exercised");
            assertFalse(Files.exists(candidate));
            assertFalse(store.appendEntry(entry(1)));
            assertEquals(0, store.getFirstLogIndex());
        }
        assertTrue(store.init(context(new ConfigurationManager())));
        assertTrue(store.appendEntry(entry(1)));
        store.shutdown();
        assertTrue(store.init(context(new ConfigurationManager())));
        assertEquals(entry(1), store.getEntry(1));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"directory", "link", "file", "dangling", "cycle"})
    void validatesObjectCreatedByAnotherInitializer(String kind) throws Exception {
        Path real = Files.createDirectory(directory.resolve("real"));
        Path link = directoryLink(directory.resolve("entry"), real);
        Path candidate = real.toRealPath().resolve("new-log");
        Path target = Files.createDirectory(directory.resolve("target"));
        Path missing = directory.resolve("missing");
        FileLogStorage store = create(link.resolve("new-log"));
        AtomicInteger raced = new AtomicInteger();
        try (MockedStatic<Files> files = mockStatic(Files.class, invocation -> {
            if ("createDirectory".equals(invocation.getMethod().getName())
                && candidate.equals(invocation.getArgument(0))) {
                raced.incrementAndGet();
                if ("directory".equals(kind)) {
                    invocation.callRealMethod();
                } else if ("file".equals(kind)) {
                    Files.write(candidate, new byte[] {42});
                } else {
                    Path destination = "link".equals(kind) ? target
                        : "dangling".equals(kind) ? missing : candidate.getFileName();
                    directoryLink(candidate, destination);
                }
                throw new FileAlreadyExistsException(candidate.toString());
            }
            return invocation.callRealMethod();
        })) {
            boolean valid = "directory".equals(kind) || "link".equals(kind);
            assertEquals(valid, store.init(context(new ConfigurationManager())));
            assertEquals(1, raced.get(), "Creation must encounter the concurrent object");
            if (valid) {
                assertTrue(store.appendEntry(entry(1)));
            } else {
                assertFalse(store.appendEntry(entry(1)));
                assertFalse(Files.exists(missing));
                if ("file".equals(kind)) {
                    assertArrayEquals(new byte[] {42}, Files.readAllBytes(candidate));
                }
            }
        }
        store.shutdown();
        if (!"directory".equals(kind) && !"link".equals(kind)) {
            Files.delete(candidate);
        }
        assertTrue(store.init(context(new ConfigurationManager())));
        if ("directory".equals(kind) || "link".equals(kind)) {
            assertEquals(entry(1), store.getEntry(1));
        } else {
            assertTrue(store.appendEntry(entry(1)));
        }
    }
    
    @Test
    void ownsDirectoryUntilShutdownAndCanReinitialize() {
        FileLogStorage store = open();
        assertTrue(store.init(context(new ConfigurationManager())));
        FileLogStorage duplicate = create();
        assertFalse(duplicate.init(context(new ConfigurationManager())));
        store.shutdown();
        store.shutdown();
        assertTrue(store.init(context(new ConfigurationManager())));
        assertTrue(Files.exists(root().resolve("LOCK")));
    }
    
    @Test
    void clearsFailedInitializationBeforeRetrying() throws Exception {
        FileLogStorage store = create();
        assertFalse(store.init(context(null)));
        Files.createDirectories(root());
        Files.write(root().resolve("segments"), new byte[] {1});
        assertFalse(store.init(context(new ConfigurationManager())));
        assertTrue(Files.isRegularFile(root().resolve("LOCK")));
        assertFalse(Files.exists(root().resolve("index")));
        Files.delete(root().resolve("segments"));
        assertTrue(store.init(context(new ConfigurationManager())));
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void closedStoreRejectsOperations(boolean initialized) {
        FileLogStorage store = create();
        if (initialized) {
            assertTrue(store.init(context(new ConfigurationManager())));
            store.shutdown();
        }
        assertEquals(0, store.getFirstLogIndex());
        assertEquals(0, store.getLastLogIndex());
        assertNull(store.getEntry(1));
        assertEquals(0, store.getTerm(1));
        assertFalse(store.appendEntry(entry(1)));
        assertEquals(0, store.appendEntries(List.of(entry(1))));
        assertFalse(store.truncatePrefix(1));
        assertFalse(store.truncateSuffix(1));
        assertFalse(store.reset(1));
    }
    
    @Test
    void appendsContiguousEntriesAndReadsTheirTerms() {
        FileLogStorage store = open();
        assertRange(store, 1, 0);
        assertTrue(store.appendEntry(entry(1)));
        assertEquals(2, store.appendEntries(List.of(entry(2), entry(3))));
        assertRange(store, 1, 3);
        for (long index = 1; index <= 3; index++) {
            assertEquals(entry(index), store.getEntry(index));
            assertEquals(2, store.getTerm(index));
        }
        assertNull(store.getEntry(0));
        assertNull(store.getEntry(4));
        store.shutdown();
        assertTrue(store.init(context(new ConfigurationManager())));
        assertRange(store, 1, 3);
        assertEquals(entry(3), store.getEntry(3));
    }
    
    @ParameterizedTest
    @ValueSource(
        strings = {"null", "empty", "gap", "duplicate", "outOfOrder", "nullEntry", "term", "type",
            "missingPeers", "emptyPeers", "nullPeer", "nullLearner", "nullOldPeer",
            "nullOldLearner", "orphanOldLearners", "overlap", "oldOverlap"})
    void rejectsWholeInvalidBatchWithoutMutation(String kind) throws Exception {
        FileLogStorage store = open();
        assertTrue(store.appendEntry(entry(1)));
        List<LogEntry> batch;
        switch (kind) {
            case "null":
                batch = null;
                break;
            case "empty":
                batch = List.of();
                break;
            case "gap":
                batch = List.of(entry(2), entry(4));
                break;
            case "duplicate":
                batch = List.of(entry(2), entry(2));
                break;
            case "outOfOrder":
                batch = List.of(entry(3), entry(2));
                break;
            case "nullEntry":
                batch = Arrays.asList(entry(2), null);
                break;
            case "term":
                LogEntry negative = entry(3);
                negative.setId(new LogId(3, -1));
                batch = List.of(entry(2), negative);
                break;
            case "type":
                LogEntry unknown = entry(3);
                unknown.setType(EntryType.ENTRY_TYPE_UNKNOWN);
                batch = List.of(entry(2), unknown);
                break;
            default:
                LogEntry configuration = entry(3);
                configuration.setType(EntryType.ENTRY_TYPE_CONFIGURATION);
                configuration.setData(null);
                PeerId peer = new PeerId("127.0.0.1", 7848);
                configuration.setPeers(List.of(peer));
                switch (kind) {
                    case "missingPeers":
                        configuration.setPeers(null);
                        break;
                    case "emptyPeers":
                        configuration.setPeers(List.of());
                        break;
                    case "nullPeer":
                        configuration.setPeers(Arrays.asList(peer, null));
                        break;
                    case "nullLearner":
                        configuration.setLearners(Arrays.asList((PeerId) null));
                        break;
                    case "nullOldPeer":
                        configuration.setOldPeers(Arrays.asList(peer, null));
                        break;
                    case "nullOldLearner":
                        configuration.setOldPeers(List.of(peer));
                        configuration.setOldLearners(Arrays.asList((PeerId) null));
                        break;
                    case "orphanOldLearners":
                        configuration.setOldLearners(List.of(peer));
                        break;
                    case "overlap":
                        configuration.setLearners(List.of(peer));
                        break;
                    default:
                        configuration.setOldPeers(List.of(peer));
                        configuration.setOldLearners(List.of(peer));
                }
                batch = List.of(entry(2), configuration);
        }
        byte[] before = Files.readAllBytes(root().resolve("manifest"));
        assertEquals(0, store.appendEntries(batch));
        assertRange(store, 1, 1);
        assertArrayEquals(before, Files.readAllBytes(root().resolve("manifest")));
        assertNull(store.getEntry(2));
        assertTrue(store.appendEntry(entry(2)));
    }
    
    @Test
    void encodingFailureDoesNotPublishEarlierBatchEntries() {
        LogEntryCodecFactory factory = mock(LogEntryCodecFactory.class);
        LogEntryEncoder encoder = mock(LogEntryEncoder.class);
        when(factory.encoder()).thenReturn(encoder);
        when(factory.decoder()).thenReturn(LogEntryV2CodecFactory.getInstance().decoder());
        when(encoder.encode(any(LogEntry.class))).thenAnswer(invocation -> {
            LogEntry entry = invocation.getArgument(0);
            if (entry.getId().getIndex() == 2) {
                throw new IllegalArgumentException("encoding failure");
            }
            return LogEntryV2CodecFactory.getInstance().encoder().encode(entry);
        });
        LogStorageOptions options = context(new ConfigurationManager());
        options.setLogEntryCodecFactory(factory);
        FileLogStorage store = create();
        assertTrue(store.init(options));
        assertEquals(0, store.appendEntries(List.of(entry(1), entry(2))));
        assertRange(store, 1, 0);
        assertTrue(store.appendEntry(entry(1)));
    }
    
    @Test
    void truncatesWithinAndAcrossSegmentsThenAppendsAfterReopen() {
        FileLogStorage store = new FileLogStorage(directory, new RaftOptions(), 160);
        stores.add(store);
        assertTrue(store.init(context(new ConfigurationManager())));
        List<LogEntry> batch = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            batch.add(entry(i));
        }
        assertEquals(batch.size(), store.appendEntries(batch));
        assertTrue(store.getManifest().getSegments().size() > 2);
        assertTrue(store.truncatePrefix(4));
        assertTrue(store.truncateSuffix(8));
        assertRange(store, 4, 8);
        assertNull(store.getEntry(3));
        assertNull(store.getEntry(9));
        assertEquals(entry(4), store.getEntry(4));
        assertEquals(entry(8), store.getEntry(8));
        store.shutdown();
        assertTrue(store.init(context(new ConfigurationManager())));
        assertRange(store, 4, 8);
        assertTrue(store.appendEntry(entry(9)));
        assertEquals(entry(9), store.getEntry(9));
    }
    
    @ParameterizedTest
    @CsvSource({"prefix, 99, 101, 200", "prefix, 101, 101, 200",
        "prefix, 150, 150, 200", "prefix, 200, 200, 200", "prefix, 201, 201, 200",
        "prefix, 250, 250, 249", "suffix, 100, 101, 100", "suffix, 99, 101, 100",
        "suffix, 101, 101, 101", "suffix, 150, 101, 150", "suffix, 200, 101, 200",
        "suffix, 250, 101, 200"})
    void truncationRangeTableSurvivesRestart(String operation, long boundary, long first,
        long last) {
        FileLogStorage store = open();
        assertTrue(store.reset(101));
        List<LogEntry> batch = new ArrayList<>();
        for (long index = 101; index <= 200; index++) {
            batch.add(entry(index));
        }
        assertEquals(100, store.appendEntries(batch));
        assertTrue("prefix".equals(operation) ? store.truncatePrefix(boundary)
            : store.truncateSuffix(boundary));
        assertRange(store, first, last);
        store.shutdown();
        assertTrue(store.init(context(new ConfigurationManager())));
        assertRange(store, first, last);
        for (long index = first; index <= last; index++) {
            assertEquals(entry(index), store.getEntry(index));
        }
        assertNull(store.getEntry(first - 1));
        assertNull(store.getEntry(last + 1));
        assertTrue(store.appendEntry(entry(last + 1)));
    }
    
    @Test
    void rejectsInvalidTruncationAndResetWithoutChangingRange() {
        FileLogStorage store = open();
        assertTrue(store.appendEntry(entry(1)));
        assertFalse(store.truncatePrefix(0));
        assertFalse(store.truncatePrefix(-1));
        assertFalse(store.truncateSuffix(-1));
        assertFalse(store.reset(0));
        assertFalse(store.reset(-1));
        assertRange(store, 1, 1);
        assertEquals(entry(1), store.getEntry(1));
    }
    
    @ParameterizedTest
    @ValueSource(longs = {2, 20, Long.MAX_VALUE - 1, Long.MAX_VALUE})
    void resetPublishesAnEmptyRangeWithoutCreatingSegments(long nextIndex) throws Exception {
        FileLogStorage store = open();
        assertEquals(3, store.appendEntries(List.of(entry(1), entry(2), entry(3))));
        try (MockedStatic<Segment> segments =
            mockStatic(Segment.class, CALLS_REAL_METHODS)) {
            segments.when(() -> Segment.create(any(Path.class), any(UUID.class), anyLong()))
                .thenThrow(new IOException("reset must not create a segment"));
            assertTrue(store.reset(nextIndex));
        }
        assertRange(store, nextIndex, nextIndex - 1);
        assertTrue(store.getManifest().getSegments().isEmpty());
        assertNull(store.getEntry(nextIndex));
        assertEquals(0, store.getTerm(nextIndex));
        assertEquals(nextIndex != Long.MAX_VALUE, store.appendEntry(entry(nextIndex)));
        if (nextIndex != Long.MAX_VALUE) {
            assertEquals(entry(nextIndex), store.getEntry(nextIndex));
        }
        assertTrue(store.reset(nextIndex));
        store.shutdown();
        assertTrue(store.init(context(new ConfigurationManager())));
        assertRange(store, nextIndex, nextIndex - 1);
        assertTrue(store.getManifest().getSegments().isEmpty());
        assertNull(store.getEntry(nextIndex));
        if (nextIndex == Long.MAX_VALUE - 1) {
            byte[] before = Files.readAllBytes(root().resolve("manifest"));
            assertEquals(0, store.appendEntries(List.of(entry(nextIndex), entry(Long.MAX_VALUE))));
            assertRange(store, nextIndex, nextIndex - 1);
            assertTrue(store.getManifest().getSegments().isEmpty());
            assertArrayEquals(before, Files.readAllBytes(root().resolve("manifest")));
            assertNull(store.getEntry(nextIndex));
            try (Stream<Path> segments = Files.list(root().resolve("segments"))) {
                assertEquals(0, segments.count());
            }
        }
        assertEquals(nextIndex != Long.MAX_VALUE, store.appendEntry(entry(nextIndex)));
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recoversConfigurationHistoryWithoutReplacingSnapshot(boolean reset) {
        FileLogStorage store = create();
        ConfigurationManager manager = new ConfigurationManager();
        assertTrue(store.init(context(manager)));
        LogEntry configuration = entry(1);
        configuration.setType(EntryType.ENTRY_TYPE_CONFIGURATION);
        configuration.setData(null);
        configuration.setPeers(List.of(new PeerId("127.0.0.1", 7848)));
        configuration.setOldPeers(List.of(new PeerId("127.0.0.1", 7849)));
        configuration.setLearners(List.of(new PeerId("127.0.0.1", 7850)));
        configuration.setOldLearners(List.of(new PeerId("127.0.0.1", 7851)));
        assertTrue(store.appendEntry(configuration));
        assertEquals(0, manager.getLastConfiguration().getId().getIndex());
        if (reset) {
            assertTrue(store.reset(1));
            assertNull(store.getEntry(1));
        }
        store.shutdown();
        ConfigurationManager recovered = new ConfigurationManager();
        ConfigurationEntry snapshot = new ConfigurationEntry();
        snapshot.setId(new LogId(0, 1));
        recovered.setSnapshot(snapshot);
        assertTrue(store.init(context(recovered)));
        assertSame(snapshot, recovered.getSnapshot());
        if (reset) {
            assertSame(snapshot, recovered.getLastConfiguration());
            return;
        }
        assertEquals(configuration.getId(), recovered.getLastConfiguration().getId());
        assertEquals(configuration.getPeers(),
            recovered.getLastConfiguration().getConf().listPeers());
        assertEquals(configuration.getLearners(),
            recovered.getLastConfiguration().getConf().listLearners());
        assertEquals(configuration.getOldPeers(),
            recovered.getLastConfiguration().getOldConf().listPeers());
        assertEquals(configuration.getOldLearners(),
            recovered.getLastConfiguration().getOldConf().listLearners());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"append", "prefix", "suffix", "reset", "cleanup", "write", "force"})
    void mutationFailureDisablesWritesWhileCleanupIsBestEffort(String operation)
        throws Exception {
        FileLogStorage store = open();
        assertEquals(3, store.appendEntries(List.of(entry(1), entry(2), entry(3))));
        boolean published = "cleanup".equals(operation);
        if (!published && !"write".equals(operation) && !"force".equals(operation)) {
            Files.createDirectory(root().resolve("manifest.tmp"));
        }
        switch (operation) {
            case "append":
                assertFalse(store.appendEntry(entry(4)));
                break;
            case "prefix":
                assertFalse(store.truncatePrefix(2));
                break;
            case "suffix":
                assertFalse(store.truncateSuffix(2));
                break;
            case "cleanup":
                Path oldSegment = root().resolve("segments").resolve(FileLogFormat.segmentName(1));
                try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
                    files.when(() -> Files.deleteIfExists(oldSegment))
                        .thenThrow(new IOException("old segment cleanup failed"));
                    assertTrue(store.reset(10));
                }
                break;
            case "write":
            case "force":
                try (MockedStatic<Segment> segments =
                    mockStatic(Segment.class, CALLS_REAL_METHODS)) {
                    segments
                        .when(() -> Segment.create(any(Path.class), any(UUID.class), anyLong()))
                        .thenAnswer(invocation -> {
                            Segment segment = spy((Segment) invocation.callRealMethod());
                            if ("force".equals(operation)) {
                                doThrow(new IOException("segment force failed")).when(segment)
                                    .force();
                            } else {
                                doThrow(new IOException("segment write failed")).when(segment)
                                    .append(any(ByteBuffer.class));
                            }
                            return segment;
                        });
                    assertFalse(store.appendEntry(entry(4)));
                }
                break;
            default:
                assertFalse(store.reset(10));
        }
        assertRange(store, published ? 10 : 1, published ? 9 : 3);
        if (published) {
            assertNull(store.getEntry(10));
        } else {
            assertEquals(entry(3), store.getEntry(3));
        }
        Files.deleteIfExists(root().resolve("manifest.tmp"));
        if (published) {
            assertTrue(store.appendEntry(entry(10)));
        } else {
            assertFalse(store.init(context(new ConfigurationManager())));
            assertFalse(create().init(context(new ConfigurationManager())));
            assertFalse(store.appendEntry(entry(4)));
            assertFalse(store.truncatePrefix(2));
            assertFalse(store.truncateSuffix(1));
            assertFalse(store.reset(10));
        }
        store.shutdown();
        assertTrue(store.init(context(new ConfigurationManager())));
        assertRange(store, published ? 10 : 1, published ? 10 : 3);
        assertTrue(store.appendEntry(entry(published ? 11 : 4)));
    }
    
    @ParameterizedTest
    @CsvSource({"prefix, otherRecord", "prefix, size", "prefix, payload",
        "suffix, otherRecord", "suffix, size", "suffix, payload"})
    void corruptBoundaryCannotPublishTruncation(String operation, String corruption)
        throws Exception {
        FileLogStorage store = new FileLogStorage(directory, new RaftOptions(), 4096);
        stores.add(store);
        assertTrue(store.init(context(new ConfigurationManager())));
        assertEquals(3, store.appendEntries(List.of(entry(1), entry(2), entry(3))));
        Manifest previous = store.getManifest();
        long segmentId = previous.getSegments().get(0).getSegmentId();
        Path segment = root().resolve("segments").resolve(FileLogFormat.segmentName(segmentId));
        Path index = root().resolve("index").resolve(FileLogFormat.indexName(segmentId));
        byte[] originalSegment = Files.readAllBytes(segment);
        byte[] originalIndex = Files.readAllBytes(index);
        byte[] corruptIndex = originalIndex.clone();
        ByteBuffer slots = ByteBuffer.wrap(corruptIndex);
        int slot = FileLogFormat.INDEX_ENTRY_SIZE;
        long offset = slots.getLong(slot);
        int size = slots.getInt(slot + Long.BYTES);
        if ("otherRecord".equals(corruption)) {
            System.arraycopy(corruptIndex, 0, corruptIndex, slot, slot);
            Files.write(index, corruptIndex);
        } else if ("size".equals(corruption)) {
            slots.putInt(slot + Long.BYTES, size + 1);
            Files.write(index, corruptIndex);
        } else {
            byte[] damaged = originalSegment.clone();
            damaged[(int) offset + size - 1] ^= 1;
            Files.write(segment, damaged);
        }
        byte[] manifestBefore = Files.readAllBytes(root().resolve("manifest"));
        byte[] segmentBefore = Files.readAllBytes(segment);
        byte[] indexBefore = Files.readAllBytes(index);
        assertNull(store.getEntry(2));
        assertFalse("prefix".equals(operation) ? store.truncatePrefix(2)
            : store.truncateSuffix(2));
        assertSame(previous, store.getManifest());
        assertRange(store, 1, 3);
        assertArrayEquals(manifestBefore, Files.readAllBytes(root().resolve("manifest")));
        assertArrayEquals(segmentBefore, Files.readAllBytes(segment));
        assertArrayEquals(indexBefore, Files.readAllBytes(index));
        assertMutationDisabled(store);
        store.shutdown();
        if ("payload".equals(corruption)) {
            assertFalse(store.init(context(new ConfigurationManager())));
            assertArrayEquals(segmentBefore, Files.readAllBytes(segment));
            Files.write(segment, originalSegment);
        }
        assertTrue(store.init(context(new ConfigurationManager())));
        assertRange(store, 1, 3);
        assertEquals(entry(2), store.getEntry(2));
        assertTrue(store.appendEntry(entry(4)));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"prefixIndex", "segmentTruncate", "segmentForce", "indexTruncate",
        "activeReopen"})
    void requiredResourceFailureRetainsPublishedTruncation(String failure) throws Exception {
        FileLogStorage store = new FileLogStorage(directory, new RaftOptions(), 4096);
        stores.add(store);
        assertTrue(store.init(context(new ConfigurationManager())));
        assertEquals(3, store.appendEntries(List.of(entry(1), entry(2), entry(3))));
        Manifest previous = store.getManifest();
        boolean prefix = "prefixIndex".equals(failure) || "activeReopen".equals(failure);
        try (MockedStatic<Index> indexes = mockStatic(Index.class, CALLS_REAL_METHODS);
            MockedStatic<Segment> segments = mockStatic(Segment.class, CALLS_REAL_METHODS)) {
            indexes.when(() -> Index.create(any(Path.class), anyLong()))
                .thenAnswer(invocation -> {
                    if ("prefixIndex".equals(failure) && store.getManifest() != previous) {
                        throw new IOException("Published prefix index rebuild failed");
                    }
                    return invocation.callRealMethod();
                });
            indexes.when(() -> Index.openWritable(any(Path.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    if ("activeReopen".equals(failure) && store.getManifest() != previous) {
                        throw new IOException("Published range active reopen failed");
                    }
                    Index index = spy((Index) invocation.callRealMethod());
                    if ("indexTruncate".equals(failure)) {
                        doThrow(new IOException("Published suffix index truncation failed"))
                            .when(index).truncateSuffix(anyLong());
                    }
                    return index;
                });
            segments.when(() -> Segment.open(any(Path.class), anyLong()))
                .thenAnswer(invocation -> {
                    Segment segment = spy((Segment) invocation.callRealMethod());
                    if (store.getManifest() != previous) {
                        if ("segmentTruncate".equals(failure)) {
                            doThrow(new IOException("Published suffix segment truncation failed"))
                                .when(segment).truncate(anyLong());
                        } else if ("segmentForce".equals(failure)) {
                            doThrow(new IOException("Published suffix segment force failed"))
                                .when(segment).force();
                        }
                    }
                    return segment;
                });
            assertFalse(prefix ? store.truncatePrefix(2) : store.truncateSuffix(2));
        }
        long first = prefix ? 2 : 1;
        long last = prefix ? 3 : 2;
        assertRange(store, first, last);
        byte[] published = Files.readAllBytes(root().resolve("manifest"));
        Manifest persisted = store.decodeManifest(ByteBuffer.wrap(published));
        assertEquals(first, persisted.getFirstLogIndex());
        assertEquals(last, persisted.getLastLogIndex());
        assertMutationDisabled(store);
        assertRange(store, first, last);
        assertArrayEquals(published, Files.readAllBytes(root().resolve("manifest")));
        store.shutdown();
        assertTrue(store.init(context(new ConfigurationManager())));
        assertRange(store, first, last);
        for (long index = first; index <= last; index++) {
            assertEquals(entry(index), store.getEntry(index));
        }
        assertNull(store.getEntry(first - 1));
        assertNull(store.getEntry(last + 1));
        assertTrue(store.appendEntry(entry(last + 1)));
    }
    
    private void assertMutationDisabled(FileLogStorage store) {
        assertFalse(store.init(context(new ConfigurationManager())));
        assertFalse(create().init(context(new ConfigurationManager())));
        assertFalse(store.appendEntry(entry(store.getLastLogIndex() + 1)));
        assertEquals(0, store.appendEntries(List.of(entry(store.getLastLogIndex() + 1))));
        assertFalse(store.truncatePrefix(2));
        assertFalse(store.truncateSuffix(1));
        assertFalse(store.reset(10));
    }
    
    @Test
    void segmentCreationFailureDisablesWritesUntilReinitialization() throws Exception {
        FileLogStorage store = open();
        Path obstruction = root().resolve("segments").resolve(FileLogFormat.segmentName(1));
        Files.createDirectory(obstruction);
        assertFalse(store.appendEntry(entry(1)));
        assertRange(store, 1, 0);
        assertFalse(create().init(context(new ConfigurationManager())));
        Files.delete(obstruction);
        assertFalse(store.appendEntry(entry(1)));
        assertFalse(store.init(context(new ConfigurationManager())));
        store.shutdown();
        assertTrue(store.init(context(new ConfigurationManager())));
        assertTrue(store.appendEntry(entry(1)));
    }
    
    @Test
    void manifestDirectoryForceFailureRetainsLockUntilReopenRecoversMovedManifest() {
        FileLogStorage store = open();
        assertEquals(3, store.appendEntries(List.of(entry(1), entry(2), entry(3))));
        Manifest previous = store.getManifest();
        try (MockedStatic<FileLogStorage> directoryForces =
            mockStatic(FileLogStorage.class, CALLS_REAL_METHODS)) {
            directoryForces.when(() -> FileLogStorage.forceDirectory(any(Path.class)))
                .thenAnswer(invocation -> {
                    if (root().equals(invocation.getArgument(0))) {
                        throw new IOException("manifest directory force failed after replacement");
                    }
                    return invocation.callRealMethod();
                });
            assertFalse(store.appendEntry(entry(4)));
            assertSame(previous, store.getManifest());
            assertRange(store, 1, 3);
            assertEquals(entry(3), store.getEntry(3));
            assertNull(store.getEntry(4));
            assertFalse(store.init(context(new ConfigurationManager())));
            assertFalse(create().init(context(new ConfigurationManager())));
            assertFalse(store.appendEntry(entry(4)));
            assertFalse(store.truncatePrefix(2));
            assertFalse(store.truncateSuffix(1));
            assertFalse(store.reset(10));
            assertSame(previous, store.getManifest());
        }
        store.shutdown();
        assertTrue(store.init(context(new ConfigurationManager())));
        assertRange(store, 1, 4);
        assertEquals(entry(4), store.getEntry(4));
        assertTrue(store.appendEntry(entry(5)));
    }
    
    @Test
    void validatesEntireStableRangeBeforeRecoveringConfigurations() throws Exception {
        FileLogStorage store = open();
        LogEntry configuration = entry(1);
        configuration.setType(EntryType.ENTRY_TYPE_CONFIGURATION);
        configuration.setData(null);
        configuration.setPeers(List.of(new PeerId("127.0.0.1", 7848)));
        assertTrue(store.appendEntry(configuration));
        assertTrue(store.appendEntry(entry(2)));
        Manifest manifest = store.getManifest();
        Manifest.SegmentMeta last = manifest.getSegments().get(manifest.getSegments().size() - 1);
        Path segment =
            root().resolve("segments").resolve(FileLogFormat.segmentName(last.getSegmentId()));
        store.shutdown();
        byte[] bytes = Files.readAllBytes(segment);
        bytes[(int) last.getPublishedEndOffset() - 1] ^= 1;
        Files.write(segment, bytes);
        ConfigurationManager manager = new ConfigurationManager();
        assertFalse(store.init(context(manager)));
        assertEquals(0, manager.getLastConfiguration().getId().getIndex());
        assertArrayEquals(bytes, Files.readAllBytes(segment));
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"append", "prefix", "suffix", "close"})
    void concurrentReadersObserveOnlyCompleteEntries(String operation) throws Exception {
        FileLogStorage store = open();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CyclicBarrier phase = new CyclicBarrier(3);
        try {
            Future<?> writer = executor.submit(() -> {
                for (int i = 1; i <= 40; i++) {
                    phase.await(10, TimeUnit.SECONDS);
                    assertTrue(store.appendEntry(entry(i)));
                    if (i % 8 == 0) {
                        if ("prefix".equals(operation)) {
                            assertTrue(store.truncatePrefix(i - 2));
                        } else if ("suffix".equals(operation)) {
                            assertTrue(store.truncateSuffix(i - 1));
                            assertTrue(store.appendEntry(entry(i)));
                        }
                    }
                    if ("close".equals(operation) && i == 40) {
                        store.shutdown();
                    }
                    phase.await(10, TimeUnit.SECONDS);
                }
                return null;
            });
            List<Future<Integer>> readers = new ArrayList<>();
            for (int reader = 0; reader < 2; reader++) {
                readers.add(executor.submit(() -> {
                    int observedEntries = 0;
                    for (int i = 1; i <= 40; i++) {
                        // Release reads and mutations together, then inspect the completed round.
                        phase.await(10, TimeUnit.SECONDS);
                        for (int attempt = 0; attempt < 5; attempt++) {
                            long last = store.getLastLogIndex();
                            if (last > 0) {
                                LogEntry observed = store.getEntry(last);
                                if ("append".equals(operation)) {
                                    assertNotNull(observed,
                                        "An append cannot remove a published entry: " + last);
                                }
                                // Truncation or shutdown may occur between the two API calls.
                                if (observed != null) {
                                    assertEquals(entry(last), observed);
                                }
                            }
                            // The previous entry survives this round's prefix/suffix truncation.
                            // Closing in the last round is the only exception to that guarantee.
                            if (i > 1 && !("close".equals(operation) && i == 40)) {
                                LogEntry retained = store.getEntry(i - 1);
                                assertNotNull(retained,
                                    "Retained entry disappeared during " + operation + ": "
                                        + (i - 1));
                                assertEquals(entry(i - 1), retained);
                                observedEntries++;
                            }
                        }
                        phase.await(10, TimeUnit.SECONDS);
                        // The next mutation waits until both readers enter the next round.
                        if ("close".equals(operation) && i == 40) {
                            assertEquals(0, store.getLastLogIndex());
                            assertNull(store.getEntry(i));
                        } else {
                            assertEquals(entry(i), store.getEntry(i));
                        }
                    }
                    return observedEntries;
                }));
            }
            for (Future<Integer> reader : readers) {
                assertEquals(5 * ("close".equals(operation) ? 38 : 39),
                    reader.get(30, TimeUnit.SECONDS).intValue());
            }
            writer.get(30, TimeUnit.SECONDS);
            if ("close".equals(operation)) {
                assertTrue(store.init(context(new ConfigurationManager())));
            }
            assertRange(store, "prefix".equals(operation) ? 38 : 1, 40);
            assertEquals(entry(40), store.getEntry(40));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
    
    private FileLogStorage create() {
        return create(directory);
    }
    
    private FileLogStorage create(Path logUri) {
        FileLogStorage store = new FileLogStorage(logUri, new RaftOptions(), 160);
        stores.add(store);
        return store;
    }
    
    private FileLogStorage open() {
        FileLogStorage store = create();
        assertTrue(store.init(context(new ConfigurationManager())));
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
    
    private static LogEntry entry(long index) {
        LogEntry entry = new LogEntry(EntryType.ENTRY_TYPE_DATA);
        entry.setId(new LogId(index, 2));
        entry.setData(ByteBuffer.wrap(new byte[] {(byte) index}));
        return entry;
    }
    
    private static void assertRange(FileLogStorage store, long first, long last) {
        assertEquals(first, store.getFirstLogIndex());
        assertEquals(last, store.getLastLogIndex());
    }
    
    private static LogStorageOptions context(ConfigurationManager manager) {
        LogStorageOptions options = new LogStorageOptions();
        options.setGroupId("group");
        options.setConfigurationManager(manager);
        options.setLogEntryCodecFactory(LogEntryV2CodecFactory.getInstance());
        return options;
    }
}
