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
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.codec.v2.LogEntryV2CodecFactory;
import com.alipay.sofa.jraft.option.LogStorageOptions;
import com.alipay.sofa.jraft.option.RaftOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FileLogIndexRebuildTest {
    
    @TempDir
    Path directory;
    
    @ParameterizedTest
    @CsvSource({"1,false", "4095,false", "4096,false", "4097,false", "8193,false",
        "1,true", "4095,true", "4096,true", "4097,true", "8193,true"})
    void rebuildsByteIdenticalIndexesInBoundedWrites(int count, boolean prefix) throws Exception {
        FileLogStorage store = create(Segment.MAX_SEGMENT_BYTES);
        try {
            assertTrue(store.init(context(new ConfigurationManager())));
            seed(store, count + (prefix ? 3 : 0));
            Map<Long, byte[]> expected = indexBytes(store.getManifest());
            if (!prefix) {
                store.shutdown();
            }
            List<FileChannel> channels = new ArrayList<>();
            try (var indexes = mockStatic(Index.class, invocation -> {
                if (!"create".equals(invocation.getMethod().getName())) {
                    return invocation.callRealMethod();
                }
                Path path = invocation.getArgument(0);
                long first = invocation.getArgument(1);
                FileChannel real = FileChannel.open(path, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.READ,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                FileChannel channel = mock(FileChannel.class, delegatesTo(real));
                channels.add(channel);
                return new Index(path, channel, first, first);
            })) {
                assertTrue(prefix ? store.truncatePrefix(4)
                    : store.init(context(new ConfigurationManager())));
                assertEquals(1, channels.size());
                verify(channels.get(0), times((count + 4095) / 4096))
                    .write(any(ByteBuffer.class), anyLong());
                verify(channels.get(0), times(0)).force(true);
                for (Manifest.SegmentMeta segment : store.getManifest().getSegments()) {
                    byte[] bytes = expected.get(segment.getSegmentId());
                    if (prefix) {
                        bytes = Arrays.copyOfRange(bytes, 3 * FileLogFormat.INDEX_ENTRY_SIZE,
                            bytes.length);
                    }
                    assertArrayEquals(bytes, Files.readAllBytes(indexPath(segment.getSegmentId())));
                }
                assertNotNull(store.getEntry(prefix ? 4 : 1));
                assertNotNull(store.getEntry(count + (prefix ? 3 : 0)));
            }
            store.shutdown();
            assertTrue(store.init(context(new ConfigurationManager())));
            assertTrue(store.appendEntry(entry(count + (prefix ? 4 : 1))));
        } finally {
            store.shutdown();
        }
    }
    
    @Test
    void rebuildsEverySegmentAndRestoresConfigurationHistory() throws Exception {
        FileLogStorage store = create(4096);
        try {
            ConfigurationManager recovered = new ConfigurationManager();
            assertTrue(store.init(context(new ConfigurationManager())));
            seed(store, 500);
            Manifest manifest = store.getManifest();
            assertTrue(manifest.getSegments().size() > 1);
            Map<Long, byte[]> before = indexBytes(manifest);
            store.shutdown();
            assertTrue(store.init(context(recovered)));
            for (Manifest.SegmentMeta segment : manifest.getSegments()) {
                assertArrayEquals(before.get(segment.getSegmentId()),
                    Files.readAllBytes(indexPath(segment.getSegmentId())));
            }
            assertEquals(1, recovered.getLastConfiguration().getId().getIndex());
            assertTrue(store.truncatePrefix(25));
            assertNotNull(store.getEntry(25));
            store.shutdown();
            assertTrue(store.init(context(new ConfigurationManager())));
            assertEquals(25, store.getFirstLogIndex());
            assertTrue(store.appendEntry(entry(501)));
        } finally {
            store.shutdown();
        }
    }
    
    @ParameterizedTest
    @CsvSource({"recover,full", "recover,tailBatch", "recover,tailSingle",
        "prefix,full", "prefix,tailBatch", "prefix,tailSingle"})
    void failedBatchRebuildCanBeRepeatedWithoutPublishingConfigurations(String operation,
        String point) throws Exception {
        boolean prefix = "prefix".equals(operation);
        int count = "tailSingle".equals(point) ? 4097 : 4098;
        FileLogStorage store = create(Segment.MAX_SEGMENT_BYTES);
        try {
            assertTrue(store.init(context(new ConfigurationManager())));
            seed(store, count + (prefix ? 3 : 0));
            byte[] manifest = Files.readAllBytes(root().resolve("manifest"));
            Map<Long, byte[]> segments = new HashMap<>();
            for (Manifest.SegmentMeta segment : store.getManifest().getSegments()) {
                segments.put(segment.getSegmentId(),
                    Files.readAllBytes(segmentPath(segment.getSegmentId())));
            }
            if (!prefix) {
                store.shutdown();
            }
            ConfigurationManager manager = new ConfigurationManager();
            try (var indexes = mockStatic(Index.class, invocation -> {
                Object result = invocation.callRealMethod();
                if (!"create".equals(invocation.getMethod().getName())) {
                    return result;
                }
                Index index = spy((Index) result);
                doAnswer(write -> {
                    long first = write.getArgument(0);
                    int[] sizes = write.getArgument(2);
                    int from = write.getArgument(3);
                    int entries = write.getArgument(4);
                    if (("full".equals(point) && entries == 4096)
                        || ("tailBatch".equals(point) && entries == 2)) {
                        index.append(first, write.getArgument(1), sizes[from]);
                        throw new IOException("Partial rebuild batch");
                    }
                    return write.callRealMethod();
                }).when(index).appendBatch(anyLong(), anyLong(), any(int[].class), anyInt(),
                    anyInt());
                if ("tailSingle".equals(point)) {
                    doAnswer(write -> {
                        write.callRealMethod();
                        throw new IOException("Tail rebuild write failed");
                    }).when(index).append(anyLong(), anyLong(), anyInt());
                }
                return index;
            })) {
                assertFalse(prefix ? store.truncatePrefix(4) : store.init(context(manager)));
                assertEquals(0, manager.getLastConfiguration().getId().getIndex());
                for (Map.Entry<Long, byte[]> segment : segments.entrySet()) {
                    assertArrayEquals(segment.getValue(),
                        Files.readAllBytes(segmentPath(segment.getKey())));
                }
                if (!prefix) {
                    assertArrayEquals(manifest, Files.readAllBytes(root().resolve("manifest")));
                } else {
                    assertFalse(store.appendEntry(entry(count + 4)));
                }
            }
            store.shutdown();
            assertTrue(store.init(context(new ConfigurationManager())));
            assertEquals(prefix ? 4 : 1, store.getFirstLogIndex());
            assertNotNull(store.getEntry(count + (prefix ? 3 : 0)));
            assertTrue(store.appendEntry(entry(count + (prefix ? 4 : 1))));
        } finally {
            store.shutdown();
        }
    }
    
    @Test
    void lateCorruptionPreventsRecoveryBarrierAndConfigurationInstallation() throws Exception {
        FileLogStorage store = create(Segment.MAX_SEGMENT_BYTES);
        try {
            assertTrue(store.init(context(new ConfigurationManager())));
            seed(store, 8193);
            Manifest.SegmentMeta segment = store.getManifest().getSegments().get(0);
            Path path = segmentPath(segment.getSegmentId());
            byte[] valid = Files.readAllBytes(path);
            store.shutdown();
            byte[] corrupt = Arrays.copyOf(valid, valid.length + 24);
            corrupt[valid.length - 1] ^= 1;
            Files.write(path, corrupt);
            ConfigurationManager manager = new ConfigurationManager();
            assertFalse(store.init(context(manager)));
            assertEquals(0, manager.getLastConfiguration().getId().getIndex());
            assertArrayEquals(corrupt, Files.readAllBytes(path));
            assertEquals(8192L * FileLogFormat.INDEX_ENTRY_SIZE,
                Files.size(indexPath(segment.getSegmentId())));
            Files.write(path, valid);
            assertTrue(store.init(context(new ConfigurationManager())));
            assertTrue(store.appendEntry(entry(8194)));
        } finally {
            store.shutdown();
        }
    }
    
    private FileLogStorage create(long maxBytes) {
        return new FileLogStorage(directory, new RaftOptions(), maxBytes);
    }
    
    @Test
    void scansLargeAndSmallRecordsAcrossSegmentsAndAfterPrefixTruncation() throws Exception {
        FileLogStorage store = create(1024 * 1024);
        try {
            assertTrue(store.init(context(new ConfigurationManager())));
            seed(store, 1);
            List<LogEntry> entries = new ArrayList<>();
            for (int i = 2; i <= 19; i++) {
                LogEntry value = entry(i);
                if (i % 3 == 0) {
                    value.setData(ByteBuffer.wrap(new byte[300 * 1024]));
                }
                entries.add(value);
            }
            assertEquals(entries.size(), store.appendEntries(entries));
            assertTrue(store.getManifest().getSegments().size() > 1);
            Map<Long, byte[]> expected = indexBytes(store.getManifest());
            store.shutdown();
            ConfigurationManager manager = new ConfigurationManager();
            assertTrue(store.init(context(manager)));
            assertEquals(1, manager.getLastConfiguration().getId().getIndex());
            for (Manifest.SegmentMeta segment : store.getManifest().getSegments()) {
                assertArrayEquals(expected.get(segment.getSegmentId()),
                    Files.readAllBytes(indexPath(segment.getSegmentId())));
            }
            for (LogEntry value : entries) {
                assertEquals(value, store.getEntry(value.getId().getIndex()));
            }
            assertTrue(store.truncatePrefix(5));
            assertTrue(store.getManifest().getSegments().get(0).getFirstRecordOffset() > 40);
            expected = indexBytes(store.getManifest());
            store.shutdown();
            assertTrue(store.init(context(new ConfigurationManager())));
            assertEquals(5, store.getFirstLogIndex());
            for (Manifest.SegmentMeta segment : store.getManifest().getSegments()) {
                assertArrayEquals(expected.get(segment.getSegmentId()),
                    Files.readAllBytes(indexPath(segment.getSegmentId())));
            }
            assertNotNull(store.getEntry(6));
            assertTrue(store.appendEntry(entry(20)));
        } finally {
            store.shutdown();
        }
    }
    
    @Test
    void failedPrefetchPreservesPublishedFilesAndAllowsACompleteRetry() throws Exception {
        FileLogStorage store = create(Segment.MAX_SEGMENT_BYTES);
        try {
            assertTrue(store.init(context(new ConfigurationManager())));
            seed(store, 1);
            LogEntry large = entry(2);
            large.setData(ByteBuffer.wrap(new byte[1024 * 1024]));
            assertTrue(store.appendEntry(large));
            byte[] manifest = Files.readAllBytes(root().resolve("manifest"));
            byte[] bytes = Files.readAllBytes(segmentPath(1));
            store.shutdown();
            ConfigurationManager manager = new ConfigurationManager();
            java.util.concurrent.atomic.AtomicInteger reads =
                new java.util.concurrent.atomic.AtomicInteger();
            try (var segments = mockStatic(Segment.class, invocation -> {
                Object result = invocation.callRealMethod();
                if (!"open".equals(invocation.getMethod().getName())) {
                    return result;
                }
                Segment segment = spy((Segment) result);
                doAnswer(read -> {
                    if (reads.incrementAndGet() == 2) {
                        throw new IOException("injected large-record read failure");
                    }
                    return read.callRealMethod();
                }).when(segment).readInto(anyLong(), any(ByteBuffer.class), anyLong());
                return segment;
            })) {
                assertFalse(store.init(context(manager)));
                assertEquals(2, reads.get());
                assertEquals(0, manager.getLastConfiguration().getId().getIndex());
                assertFalse(store.appendEntry(entry(3)));
                assertArrayEquals(manifest, Files.readAllBytes(root().resolve("manifest")));
                assertArrayEquals(bytes, Files.readAllBytes(segmentPath(1)));
            }
            store.shutdown();
            assertTrue(store.init(context(manager)));
            assertEquals(1, manager.getLastConfiguration().getId().getIndex());
            assertEquals(large, store.getEntry(2));
            assertTrue(store.appendEntry(entry(3)));
        } finally {
            store.shutdown();
        }
    }
    
    private static void seed(FileLogStorage store, int count) {
        List<LogEntry> entries = new ArrayList<>(count);
        LogEntry configuration = new LogEntry(EntryType.ENTRY_TYPE_CONFIGURATION);
        configuration.setId(new LogId(1, 2));
        configuration.setPeers(List.of(new PeerId("127.0.0.1", 7848)));
        entries.add(configuration);
        for (int i = 2; i <= count; i++) {
            entries.add(entry(i));
        }
        assertEquals(count, store.appendEntries(entries));
    }
    
    private static LogEntry entry(long index) {
        LogEntry entry = new LogEntry(EntryType.ENTRY_TYPE_DATA);
        entry.setId(new LogId(index, 2));
        entry.setData(ByteBuffer.wrap(new byte[(int) (index % 31 + 1)]));
        return entry;
    }
    
    private Map<Long, byte[]> indexBytes(Manifest manifest) throws IOException {
        Map<Long, byte[]> result = new HashMap<>();
        for (Manifest.SegmentMeta segment : manifest.getSegments()) {
            result.put(segment.getSegmentId(),
                Files.readAllBytes(indexPath(segment.getSegmentId())));
        }
        return result;
    }
    
    private Path root() {
        return directory.resolve("file-log");
    }
    
    private Path indexPath(long id) {
        return root().resolve("index").resolve(FileLogFormat.indexName(id));
    }
    
    private Path segmentPath(long id) {
        return root().resolve("segments").resolve(FileLogFormat.segmentName(id));
    }
    
    private static LogStorageOptions context(ConfigurationManager manager) {
        LogStorageOptions options = new LogStorageOptions();
        options.setGroupId("group");
        options.setConfigurationManager(manager);
        options.setLogEntryCodecFactory(LogEntryV2CodecFactory.getInstance());
        return options;
    }
}
