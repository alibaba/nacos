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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.spy;

class SegmentTest {
    
    @TempDir
    Path directory;
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void readsIntoCallerBufferWithShortReadsAndPreservesItsEnvelope(boolean direct)
        throws Exception {
        FileChannel channel = mock(FileChannel.class);
        when(channel.read(any(ByteBuffer.class), anyLong())).thenAnswer(invocation -> {
            ByteBuffer target = invocation.getArgument(0);
            long offset = invocation.getArgument(1);
            int count = Math.min(3, target.remaining());
            for (int i = 0; i < count; i++) {
                target.put((byte) (offset + i));
            }
            return count;
        });
        try (Segment segment = new Segment(Path.of("read-into.seg"),
            new Segment.SegmentHeader(UUID.randomUUID(), 1), channel, 100, 100)) {
            ByteBuffer target = direct ? ByteBuffer.allocateDirect(20) : ByteBuffer.allocate(20);
            target.order(java.nio.ByteOrder.LITTLE_ENDIAN).position(5).limit(17);
            segment.readInto(40, target, 52);
            assertEquals(17, target.position());
            assertEquals(17, target.limit());
            assertEquals(java.nio.ByteOrder.LITTLE_ENDIAN, target.order());
            for (int i = 0; i < 12; i++) {
                assertEquals((byte) (40 + i), target.get(5 + i));
            }
            verify(channel, times(4)).read(any(ByteBuffer.class), anyLong());
            verify(channel, times(0)).force(true);
        }
    }
    
    @Test
    void rejectsInvalidReadIntoRequestsBeforeIo() throws Exception {
        FileChannel channel = mock(FileChannel.class);
        try (Segment segment = new Segment(Path.of("read-into.seg"),
            new Segment.SegmentHeader(UUID.randomUUID(), 1), channel, 100, Long.MAX_VALUE)) {
            ByteBuffer target = ByteBuffer.allocate(20).position(3).limit(13);
            assertThrows(IOException.class, () -> segment.readInto(39, target, 100));
            assertThrows(IOException.class, () -> segment.readInto(95, target, 100));
            assertThrows(IOException.class, () -> segment.readInto(40, target, 101));
            assertThrows(IOException.class, () -> segment.readInto(Long.MAX_VALUE, target, 100));
            assertThrows(IOException.class, () -> segment.readInto(40,
                target.asReadOnlyBuffer(), 100));
            assertThrows(IOException.class, () -> segment.readInto(40, null, 100));
            assertEquals(3, target.position());
            assertEquals(13, target.limit());
            verify(channel, times(0)).read(any(ByteBuffer.class), anyLong());
        }
    }
    
    @Test
    void readIntoReportsPartialProgressOnIoFailure() throws Exception {
        FileChannel channel = mock(FileChannel.class);
        AtomicInteger calls = new AtomicInteger();
        when(channel.read(any(ByteBuffer.class), anyLong())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() > 1) {
                throw new IOException("injected partial read");
            }
            ByteBuffer target = invocation.getArgument(0);
            target.put(new byte[3]);
            return 3;
        });
        try (Segment segment = new Segment(Path.of("read-into.seg"),
            new Segment.SegmentHeader(UUID.randomUUID(), 1), channel, 100, 100)) {
            ByteBuffer target = ByteBuffer.allocate(20).position(2).limit(12);
            assertThrows(IOException.class, () -> segment.readInto(40, target, 100));
            assertEquals(5, target.position());
            assertEquals(12, target.limit());
        }
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void combinesRecordsIntoOneWriteAndPreservesInputPositions(boolean direct) throws Exception {
        FileChannel channel = mock(FileChannel.class);
        AtomicInteger calls = new AtomicInteger();
        List<ByteBuffer> records = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            records.add(ByteBuffer.wrap(new byte[24]).asReadOnlyBuffer());
        }
        when(channel.write(any(ByteBuffer.class), anyLong())).thenAnswer(invocation -> {
            ByteBuffer buffer = invocation.getArgument(0);
            assertEquals(direct, buffer.isDirect());
            int length = buffer.remaining();
            assertEquals(128 * 24, length);
            assertEquals(40L, (long) invocation.getArgument(1));
            buffer.position(buffer.limit());
            calls.incrementAndGet();
            return length;
        });
        try (Segment segment = new Segment(Path.of("combined.seg"),
            new Segment.SegmentHeader(UUID.randomUUID(), 1), channel, 40, 4096)) {
            assertEquals(40, segment.appendBatch(records, 0, records.size(),
                direct ? ByteBuffer.allocateDirect(4096) : ByteBuffer.allocate(4096)));
            assertEquals(40 + 128 * 24, segment.getLogicalEnd());
            assertEquals(1, calls.get());
            for (ByteBuffer record : records) {
                assertEquals(0, record.position());
                assertEquals(24, record.limit());
            }
        }
    }
    
    @ParameterizedTest
    @CsvSource({"24,false", "24,true", "48,false", "48,true", "64,false", "64,true",
        "262144,false", "262144,true", "1048576,true"})
    void mergedWritesMatchSingleRecordsIncludingLargeRecords(int capacity, boolean direct)
        throws Exception {
        List<ByteBuffer> records = new ArrayList<>();
        records.add(null);
        for (int size : new int[] {24, 24, 25, 64, 90, 32}) {
            byte[] bytes = new byte[size + 5];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (size + i);
            }
            ByteBuffer record = ByteBuffer.wrap(bytes);
            record.position(3).limit(3 + size);
            records.add(record.asReadOnlyBuffer());
        }
        UUID store = UUID.randomUUID();
        try (Segment single = Segment.create(directory, store, 1);
            Segment batch = Segment.create(directory, store, 2)) {
            for (int i = 1; i < records.size(); i++) {
                single.append(records.get(i).duplicate());
            }
            batch.appendBatch(records, 1, records.size(),
                direct ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity));
            assertEquals(single.getLogicalEnd(), batch.getLogicalEnd());
            byte[] expected = Files.readAllBytes(single.getPath());
            byte[] actual = Files.readAllBytes(batch.getPath());
            assertArrayEquals(Arrays.copyOfRange(expected, 40, expected.length),
                Arrays.copyOfRange(actual, 40, actual.length));
            for (int i = 1; i < records.size(); i++) {
                assertEquals(3, records.get(i).position());
            }
        }
    }
    
    @Test
    void rejectedBatchDoesNotWriteOrInvalidateSuccessfulSync() throws Exception {
        FileChannel channel = mock(FileChannel.class);
        ByteBuffer record = ByteBuffer.allocate(24);
        List<ByteBuffer> records = List.of(record, record);
        try (Segment segment = new Segment(Path.of("invalid-batch.seg"),
            new Segment.SegmentHeader(UUID.randomUUID(), 1), channel, 40, 80)) {
            segment.force();
            assertThrows(IOException.class,
                () -> segment.appendBatch(null, 0, 1, ByteBuffer.allocate(24)));
            assertThrows(IOException.class,
                () -> segment.appendBatch(records, -1, 1, ByteBuffer.allocate(24)));
            assertThrows(IOException.class,
                () -> segment.appendBatch(records, 0, 0, ByteBuffer.allocate(24)));
            assertThrows(IOException.class,
                () -> segment.appendBatch(records, 0, 3, ByteBuffer.allocate(24)));
            assertThrows(IOException.class,
                () -> segment.appendBatch(records, 0, 1, null));
            assertThrows(IOException.class,
                () -> segment.appendBatch(records, 0, 1, ByteBuffer.allocate(23)));
            assertThrows(IOException.class, () -> segment.appendBatch(records, 0, 1,
                ByteBuffer.allocate(24).asReadOnlyBuffer()));
            assertThrows(IOException.class, () -> segment.appendBatch(
                Arrays.asList(record, null), 0, 2, ByteBuffer.allocate(24)));
            assertThrows(IOException.class, () -> segment.appendBatch(
                List.of(record, ByteBuffer.allocate(23)), 0, 2, ByteBuffer.allocate(24)));
            assertThrows(IOException.class,
                () -> segment.appendBatch(records, 0, 2, ByteBuffer.allocate(48)));
            assertThrows(IOException.class, () -> segment.append(ByteBuffer.allocate(41)));
            assertThrows(IOException.class, () -> segment.truncate(39));
            segment.syncIfRequired();
            verify(channel, times(1)).force(true);
            verify(channel, times(0)).write(any(ByteBuffer.class), anyLong());
            assertEquals(40, segment.getLogicalEnd());
        }
        try (Segment segment = new Segment(Path.of("overflow-batch.seg"),
            new Segment.SegmentHeader(UUID.randomUUID(), 1), channel,
            Long.MAX_VALUE - 24, Long.MAX_VALUE)) {
            assertThrows(IOException.class,
                () -> segment.appendBatch(records, 0, 2, ByteBuffer.allocate(48)));
        }
    }
    
    @Test
    void syncTracksMutationsAndExplicitForceIsUnconditional() throws Exception {
        FileChannel channel = mock(FileChannel.class);
        when(channel.write(any(ByteBuffer.class), anyLong())).thenAnswer(invocation -> {
            ByteBuffer source = invocation.getArgument(0);
            int size = source.remaining();
            source.position(source.limit());
            return size;
        });
        try (Segment segment = new Segment(Path.of("sync.seg"),
            new Segment.SegmentHeader(UUID.randomUUID(), 1), channel, 40, 4096)) {
            segment.syncIfRequired();
            segment.syncIfRequired();
            verify(channel, times(1)).force(true);
            segment.append(ByteBuffer.allocate(24));
            segment.syncIfRequired();
            segment.syncIfRequired();
            verify(channel, times(2)).force(true);
            segment.appendBatch(List.of(ByteBuffer.allocate(24), ByteBuffer.allocate(24)),
                0, 2, ByteBuffer.allocate(48));
            segment.syncIfRequired();
            verify(channel, times(3)).force(true);
            segment.truncate(64);
            segment.syncIfRequired();
            verify(channel, times(4)).force(true);
            segment.truncateToLogicalEnd();
            segment.syncIfRequired();
            segment.force();
            segment.force();
            verify(channel, times(7)).force(true);
        }
        verify(channel, times(7)).force(true);
    }
    
    @Test
    void createdAndReopenedSegmentsRequireTheirOwnSyncConfirmation() throws Exception {
        UUID store = UUID.randomUUID();
        Path path;
        try (Segment segment = spy(Segment.create(directory, store, 1))) {
            segment.syncIfRequired();
            verify(segment, times(1)).force();
            segment.append(ByteBuffer.allocate(24));
            segment.force();
            path = segment.getPath();
        }
        try (Segment segment = spy(Segment.open(path, 64))) {
            segment.syncIfRequired();
            segment.syncIfRequired();
            verify(segment, times(1)).force();
        }
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"batch", "append", "truncate", "tail", "force"})
    void failedMutationOrForceRetainsPendingSync(String operation) throws Exception {
        FileChannel channel = mock(FileChannel.class);
        AtomicInteger writes = new AtomicInteger();
        when(channel.write(any(ByteBuffer.class), anyLong())).thenAnswer(invocation -> {
            if (writes.incrementAndGet() == 1) {
                ByteBuffer source = invocation.getArgument(0);
                source.position(source.position() + 5);
                return 5;
            }
            throw new IOException("partial write");
        });
        when(channel.truncate(anyLong())).thenThrow(new IOException("truncate"));
        AtomicInteger forces = new AtomicInteger();
        doAnswer(invocation -> {
            if (forces.incrementAndGet() == 2) {
                throw new IOException("force");
            }
            return null;
        }).when(channel).force(true);
        try (Segment segment = new Segment(Path.of("failed-sync.seg"),
            new Segment.SegmentHeader(UUID.randomUUID(), 1), channel, 40, 4096)) {
            segment.force();
            switch (operation) {
                case "batch":
                    assertThrows(IOException.class, () -> segment.appendBatch(
                        List.of(ByteBuffer.allocate(24), ByteBuffer.allocate(24)),
                        0, 2, ByteBuffer.allocate(48)));
                    break;
                case "append":
                    assertThrows(IOException.class, () -> segment.append(ByteBuffer.allocate(24)));
                    break;
                case "truncate":
                    assertThrows(IOException.class, () -> segment.truncate(40));
                    break;
                case "tail":
                    assertThrows(IOException.class, segment::truncateToLogicalEnd);
                    break;
                default:
                    assertThrows(IOException.class, segment::force);
            }
            if (!"force".equals(operation)) {
                assertThrows(IOException.class, segment::syncIfRequired);
            }
            segment.syncIfRequired();
            segment.syncIfRequired();
            verify(channel, times(3)).force(true);
            assertEquals(40, segment.getLogicalEnd());
        }
    }
    
    @Test
    void batchLoopsOnShortWritesAndRetainsEndOnZeroProgress() throws Exception {
        FileChannel channel = mock(FileChannel.class);
        byte[] bytes = new byte[48];
        when(channel.write(any(ByteBuffer.class), anyLong())).thenAnswer(invocation -> {
            ByteBuffer source = invocation.getArgument(0);
            int offset = Math.toIntExact((long) invocation.getArgument(1) - 40);
            int length = Math.min(7, source.remaining());
            source.get(bytes, offset, length);
            return length;
        });
        byte[] record = new byte[24];
        Arrays.fill(record, (byte) 7);
        List<ByteBuffer> records = List.of(ByteBuffer.wrap(record), ByteBuffer.wrap(record));
        try (Segment segment = new Segment(Path.of("short-batch.seg"),
            new Segment.SegmentHeader(UUID.randomUUID(), 1), channel, 40, 4096)) {
            segment.appendBatch(records, 0, 2, ByteBuffer.allocate(48));
            byte[] expected = new byte[48];
            Arrays.fill(expected, (byte) 7);
            assertArrayEquals(expected, bytes);
            when(channel.write(any(ByteBuffer.class), anyLong())).thenReturn(0);
            assertThrows(IOException.class,
                () -> segment.appendBatch(records, 0, 2, ByteBuffer.allocate(48)));
            assertEquals(88, segment.getLogicalEnd());
        }
    }
    
    @Test
    void neverReplacesAnExistingSegment() throws Exception {
        UUID id = UUID.randomUUID();
        try (Segment original = Segment.create(directory, id, 7)) {
            byte[] before = Files.readAllBytes(original.getPath());
            assertThrows(IOException.class, () -> Segment.create(directory, id, 7));
            assertArrayEquals(before,
                Files.readAllBytes(original.getPath()));
        }
    }
    
    @Test
    void writesACompactHeaderAndTracksLogicalEnd() throws Exception {
        UUID storeId = UUID.randomUUID();
        assertEquals(40, FileLogFormat.SEGMENT_HEADER_SIZE);
        Path path;
        try (Segment segment = Segment.create(directory, storeId, 7)) {
            path = segment.getPath();
            assertEquals(FileLogFormat.SEGMENT_HEADER_SIZE, segment.getLogicalEnd());
            long offset = segment.append(ByteBuffer.allocate(FileLogFormat.RECORD_OVERHEAD));
            assertEquals(FileLogFormat.SEGMENT_HEADER_SIZE, offset);
            assertEquals(FileLogFormat.SEGMENT_HEADER_SIZE + FileLogFormat.RECORD_OVERHEAD,
                segment.getLogicalEnd());
        }
        
        assertEquals(FileLogFormat.SEGMENT_HEADER_SIZE + FileLogFormat.RECORD_OVERHEAD,
            Files.size(path));
        byte[] bytes = Files.readAllBytes(path);
        assertEquals(40, ByteBuffer.wrap(bytes).getShort(6));
        assertEquals(FileLogFormat.crc64(bytes, 0, 32), ByteBuffer.wrap(bytes).getLong(32));
        try (Segment reopened = Segment.open(path, Files.size(path))) {
            assertEquals(storeId, reopened.getHeader().getStoreId());
            assertEquals(7, reopened.getHeader().getSegmentId());
        }
    }
    
    @Test
    void truncatesAPhysicalTailToThePublishedEnd() throws Exception {
        Path path;
        long publishedEnd;
        try (Segment segment = Segment.create(directory, UUID.randomUUID(), 1)) {
            segment.append(ByteBuffer.allocate(FileLogFormat.RECORD_OVERHEAD));
            publishedEnd = segment.getLogicalEnd();
            segment.append(ByteBuffer.allocate(FileLogFormat.RECORD_OVERHEAD));
            path = segment.getPath();
        }
        
        try (Segment segment = Segment.open(path, publishedEnd)) {
            segment.truncateToLogicalEnd();
        }
        assertEquals(publishedEnd, Files.size(path));
    }
    
    @Test
    void loopsOnPositiveShortWritesAndRejectsZeroProgress() throws Exception {
        byte[] record = new byte[FileLogFormat.RECORD_OVERHEAD];
        for (int i = 0; i < record.length; i++) {
            record[i] = (byte) (i + 1);
        }
        byte[] writtenBytes = new byte[record.length];
        List<Long> offsets = new ArrayList<>();
        FileChannel shortChannel = mock(FileChannel.class);
        when(shortChannel.write(any(ByteBuffer.class), anyLong())).thenAnswer(invocation -> {
            ByteBuffer source = invocation.getArgument(0);
            long offset = invocation.getArgument(1);
            offsets.add(offset);
            int written = Math.min(3, source.remaining());
            source.get(writtenBytes,
                Math.toIntExact(offset - FileLogFormat.SEGMENT_HEADER_SIZE), written);
            return written;
        });
        Segment.SegmentHeader header = new Segment.SegmentHeader(UUID.randomUUID(), 1);
        try (Segment segment = new Segment(Path.of("short.seg"), header, shortChannel,
            FileLogFormat.SEGMENT_HEADER_SIZE, 1024)) {
            assertEquals(FileLogFormat.SEGMENT_HEADER_SIZE,
                segment.append(ByteBuffer.wrap(record)));
            assertEquals(FileLogFormat.SEGMENT_HEADER_SIZE + record.length,
                segment.getLogicalEnd());
        }
        assertEquals(List.of(40L, 43L, 46L, 49L, 52L, 55L, 58L, 61L), offsets);
        assertArrayEquals(record, writtenBytes);
        
        FileChannel stalledChannel = mock(FileChannel.class);
        when(stalledChannel.write(any(ByteBuffer.class), anyLong())).thenReturn(0);
        try (Segment segment = new Segment(Path.of("stalled.seg"), header, stalledChannel,
            FileLogFormat.SEGMENT_HEADER_SIZE, 1024)) {
            assertThrows(IOException.class,
                () -> segment.append(ByteBuffer.allocate(FileLogFormat.RECORD_OVERHEAD)));
        }
    }
    
    @Test
    void loopsOnPositiveShortReadsAndRejectsEofOrZeroProgress() throws Exception {
        Segment.SegmentHeader header = new Segment.SegmentHeader(UUID.randomUUID(), 1);
        byte[] expected = new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
        List<Long> offsets = new ArrayList<>();
        FileChannel shortChannel = mock(FileChannel.class);
        when(shortChannel.read(any(ByteBuffer.class), anyLong())).thenAnswer(invocation -> {
            ByteBuffer target = invocation.getArgument(0);
            long offset = invocation.getArgument(1);
            offsets.add(offset);
            int read = Math.min(3, target.remaining());
            target.put(expected,
                Math.toIntExact(offset - FileLogFormat.SEGMENT_HEADER_SIZE), read);
            return read;
        });
        try (Segment segment = new Segment(Path.of("short.seg"), header, shortChannel,
            FileLogFormat.SEGMENT_HEADER_SIZE + 12, 1024)) {
            ByteBuffer result = segment.read(FileLogFormat.SEGMENT_HEADER_SIZE, 12,
                FileLogFormat.SEGMENT_HEADER_SIZE + 12);
            byte[] actual = new byte[result.remaining()];
            result.get(actual);
            assertArrayEquals(expected, actual);
        }
        assertEquals(List.of(40L, 43L, 46L, 49L), offsets);
        
        FileChannel eofChannel = mock(FileChannel.class);
        when(eofChannel.read(any(ByteBuffer.class), anyLong())).thenReturn(-1);
        try (Segment segment = new Segment(Path.of("eof.seg"), header, eofChannel,
            FileLogFormat.SEGMENT_HEADER_SIZE + 12, 1024)) {
            assertThrows(IOException.class,
                () -> segment.read(FileLogFormat.SEGMENT_HEADER_SIZE, 12,
                    FileLogFormat.SEGMENT_HEADER_SIZE + 12));
        }
        
        FileChannel stalledChannel = mock(FileChannel.class);
        when(stalledChannel.read(any(ByteBuffer.class), anyLong())).thenReturn(0);
        try (Segment segment = new Segment(Path.of("stalled.seg"), header, stalledChannel,
            FileLogFormat.SEGMENT_HEADER_SIZE + 12, 1024)) {
            assertThrows(IOException.class,
                () -> segment.read(FileLogFormat.SEGMENT_HEADER_SIZE, 12,
                    FileLogFormat.SEGMENT_HEADER_SIZE + 12));
        }
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"truncated", "magic", "version", "headerSize", "storeId", "segmentId",
        "checksum"})
    void rejectsIllegalSegmentHeaders(String kind) throws Exception {
        Path path;
        try (Segment segment = Segment.create(directory, UUID.randomUUID(), 1)) {
            path = segment.getPath();
        }
        byte[] bytes = Files.readAllBytes(path);
        switch (kind) {
            case "truncated":
                bytes = java.util.Arrays.copyOf(bytes, bytes.length - 1);
                break;
            case "magic":
                ByteBuffer.wrap(bytes).putInt(0, 0);
                break;
            case "version":
                ByteBuffer.wrap(bytes).putShort(4, (short) 99);
                break;
            case "headerSize":
                ByteBuffer.wrap(bytes).putShort(6, (short) 48);
                break;
            case "storeId":
                bytes[8] ^= 1;
                break;
            case "segmentId":
                ByteBuffer.wrap(bytes).putLong(24, 0);
                break;
            default:
                bytes[32] ^= 1;
        }
        if (kind.equals("magic") || kind.equals("version") || kind.equals("headerSize")
            || kind.equals("segmentId")) {
            ByteBuffer.wrap(bytes).putLong(32, FileLogFormat.crc64(bytes, 0, 32));
        }
        Files.write(path, bytes);
        assertThrows(IOException.class,
            () -> Segment.open(path, FileLogFormat.SEGMENT_HEADER_SIZE));
    }
    
    @Test
    void truncatesLogicalAndPhysicalEndBeforeAppendingReplacement() throws Exception {
        try (Segment segment = Segment.create(directory, UUID.randomUUID(), 1)) {
            segment.append(ByteBuffer.allocate(FileLogFormat.RECORD_OVERHEAD));
            long retainedEnd = segment.getLogicalEnd();
            segment.append(ByteBuffer.allocate(FileLogFormat.RECORD_OVERHEAD));
            segment.truncate(retainedEnd);
            assertEquals(retainedEnd, segment.getLogicalEnd());
            assertEquals(retainedEnd, Files.size(segment.getPath()));
            assertEquals(retainedEnd,
                segment.append(ByteBuffer.allocate(FileLogFormat.RECORD_OVERHEAD)));
            assertThrows(IOException.class,
                () -> segment.truncate(FileLogFormat.SEGMENT_HEADER_SIZE - 1));
        }
    }
    
    @Test
    void reportsReadBoundaryOverflow() throws Exception {
        Segment.SegmentHeader header = new Segment.SegmentHeader(UUID.randomUUID(), 1);
        FileChannel channel = mock(FileChannel.class);
        Path path = Path.of("overflow.seg");
        try (Segment segment = new Segment(path, header, channel,
            FileLogFormat.SEGMENT_HEADER_SIZE, 1024)) {
            IOException error = assertThrows(IOException.class,
                () -> segment.read(Long.MAX_VALUE, 1, FileLogFormat.SEGMENT_HEADER_SIZE));
            assertTrue(error.getMessage().contains("Segment read boundary overflow"));
            assertTrue(error.getMessage().contains(path.toString()));
            assertTrue(error.getMessage().contains(Long.toString(Long.MAX_VALUE)));
        }
    }
    
    @Test
    void propagatesForceAndTruncateFailures() throws Exception {
        Segment.SegmentHeader header = new Segment.SegmentHeader(UUID.randomUUID(), 1);
        FileChannel channel = mock(FileChannel.class);
        doThrow(new IOException("force")).when(channel).force(true);
        when(channel.truncate(anyLong())).thenThrow(new IOException("truncate"));
        try (Segment segment = new Segment(Path.of("failed.seg"), header, channel,
            FileLogFormat.SEGMENT_HEADER_SIZE, 1024)) {
            assertThrows(IOException.class, segment::force);
            assertThrows(IOException.class, segment::truncateToLogicalEnd);
        }
    }
}
