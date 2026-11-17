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

import com.alipay.sofa.jraft.entity.EnumOutter.EntryType;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.LogId;
import com.alipay.sofa.jraft.entity.codec.v2.LogEntryV2CodecFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SegmentScannerTest {
    
    private final LogRecordCodec codec = new LogRecordCodec(LogEntryV2CodecFactory.getInstance());
    
    private final List<long[]> reads = new ArrayList<>();
    
    @ParameterizedTest
    @ValueSource(ints = {16, 24, 32, 64, 256, 262144})
    void scansMixedRecordSizesAndPreservesPublishedBoundaries(int capacity) throws Exception {
        List<ByteBuffer> records = List.of(record(1, 3), record(2, 400), record(3, 0),
            record(4, 1024 * 1024), record(5, 17));
        byte[] bytes = concatenate(records);
        try (Segment segment = source(bytes, bytes.length, Integer.MAX_VALUE)) {
            SegmentScanner scanner = new SegmentScanner(segment, 40, 40L + bytes.length, capacity);
            for (int i = 0; i < records.size(); i++) {
                assertTrue(scanner.hasNext());
                ByteBuffer actual = scanner.nextRecord(codec);
                assertEquals(0, actual.position());
                assertEquals(records.get(i).remaining(), actual.capacity());
                assertArrayEquals(records.get(i).array(), copy(actual));
                assertEquals(i + 1, codec.decode(actual, actual.remaining()).getId().getIndex());
            }
            assertFalse(scanner.hasNext());
            assertThrows(IOException.class, () -> scanner.nextRecord(codec));
        }
        long following = 40;
        for (long[] read : reads) {
            assertEquals(following, read[0]);
            following += read[1];
        }
        assertEquals(40L + bytes.length, following);
    }
    
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 15})
    void handlesExactlyFullBufferAndSplitHeaders(int headerBytes) throws Exception {
        ByteBuffer first = record(1, 11);
        byte[] bytes = concatenate(List.of(first, record(2, 15), record(3, 23)));
        try (Segment segment = source(bytes, bytes.length, Integer.MAX_VALUE)) {
            SegmentScanner scanner = new SegmentScanner(segment, 40, 40L + bytes.length,
                first.remaining() + headerBytes);
            for (int i = 1; i <= 3; i++) {
                ByteBuffer next = scanner.nextRecord(codec);
                assertEquals(i, codec.decode(next, next.remaining()).getId().getIndex());
            }
            assertFalse(scanner.hasNext());
        }
    }
    
    @Test
    void combinesManyRecordsIntoOneReadWithoutOwningTheSegment() throws Exception {
        List<ByteBuffer> records = new ArrayList<>();
        for (int i = 1; i <= 128; i++) {
            records.add(record(i, 3));
        }
        byte[] bytes = concatenate(records);
        try (Segment segment = source(bytes, bytes.length, Integer.MAX_VALUE)) {
            SegmentScanner scanner = new SegmentScanner(segment, 40, 40L + bytes.length, 262144);
            for (int i = 1; i <= 128; i++) {
                ByteBuffer next = scanner.nextRecord(codec);
                assertEquals(i, codec.decode(next, next.remaining()).getId().getIndex());
            }
            assertEquals(1, reads.size());
            assertEquals(bytes[0], segment.read(40, 1, segment.getLogicalEnd()).get());
        }
    }
    
    @Test
    void skipsRetiredPrefixAndUnpublishedTailWithoutInspectingThem() throws Exception {
        ByteBuffer kept = record(9, 19);
        byte[] bytes = new byte[13 + kept.remaining() + 27];
        System.arraycopy(kept.array(), 0, bytes, 13, kept.remaining());
        long end = 53L + kept.remaining();
        try (Segment segment = source(bytes, bytes.length, Integer.MAX_VALUE)) {
            SegmentScanner scanner = new SegmentScanner(segment, 53, end, 262144);
            ByteBuffer next = scanner.nextRecord(codec);
            assertEquals(9, codec.decode(next, next.remaining()).getId().getIndex());
            assertFalse(scanner.hasNext());
            assertEquals(1, reads.size());
            assertEquals(53, reads.get(0)[0]);
            assertEquals(kept.remaining(), reads.get(0)[1]);
        }
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"checksum", "magic", "version", "headerSize", "negative",
        "oversized", "boundary"})
    void rejectsMalformedHeadersBeforeReadingARecordSizedBuffer(String kind) throws Exception {
        byte[] bytes = record(1, 32).array().clone();
        ByteBuffer header = ByteBuffer.wrap(bytes);
        switch (kind) {
            case "checksum":
                bytes[12] ^= 1;
                break;
            case "magic":
                header.putInt(0, 0);
                break;
            case "version":
                header.putShort(4, (short) 2);
                break;
            case "headerSize":
                header.putShort(6, (short) 15);
                break;
            case "negative":
                header.putInt(8, -1);
                break;
            case "oversized":
                header.putInt(8, Integer.MAX_VALUE);
                break;
            case "boundary":
                header.putInt(8, bytes.length);
                break;
            default:
                throw new AssertionError(kind);
        }
        if (!"checksum".equals(kind)) {
            header.putInt(12, FileLogFormat.crc32(bytes, 0, 12));
        }
        try (Segment segment = source(bytes, bytes.length, Integer.MAX_VALUE)) {
            SegmentScanner scanner = new SegmentScanner(segment, 40, 40L + bytes.length, 16);
            assertThrows(IOException.class, () -> scanner.nextRecord(codec));
            assertEquals(1, reads.size());
            assertEquals(16, reads.get(0)[1]);
        }
    }
    
    @Test
    void leavesPayloadValidationToTheExistingCodec() throws Exception {
        byte[] bytes = record(1, 19).array().clone();
        bytes[FileLogFormat.RECORD_HEADER_SIZE] ^= 1;
        try (Segment segment = source(bytes, bytes.length, Integer.MAX_VALUE)) {
            SegmentScanner scanner = new SegmentScanner(segment, 40, 40L + bytes.length, 262144);
            ByteBuffer next = scanner.nextRecord(codec);
            assertThrows(IOException.class, () -> codec.decode(next, next.remaining()));
            assertEquals(0, next.position());
        }
    }
    
    @ParameterizedTest
    @ValueSource(ints = {1, 7, 15})
    void rejectsPartialPublishedHeaders(int length) throws Exception {
        try (Segment segment = source(new byte[length], length, Integer.MAX_VALUE)) {
            SegmentScanner scanner = new SegmentScanner(segment, 40, 40L + length, 64);
            assertThrows(IOException.class, () -> scanner.nextRecord(codec));
            assertTrue(reads.isEmpty());
        }
    }
    
    @Test
    void loopsOnShortReadsAndRejectsPrematurePhysicalEof() throws Exception {
        byte[] bytes = record(1, 100).array();
        try (Segment segment = source(bytes, bytes.length, 7)) {
            SegmentScanner scanner = new SegmentScanner(segment, 40, 40L + bytes.length, 64);
            ByteBuffer next = scanner.nextRecord(codec);
            assertEquals(1, codec.decode(next, next.remaining()).getId().getIndex());
            assertTrue(reads.size() > 2);
        }
        try (Segment segment = source(bytes, bytes.length - 1, 7)) {
            SegmentScanner scanner = new SegmentScanner(segment, 40, 40L + bytes.length, 64);
            assertThrows(IOException.class, () -> scanner.nextRecord(codec));
        }
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void propagatesZeroProgressAndIoFailure(boolean ioFailure) throws Exception {
        FileChannel channel = mock(FileChannel.class);
        when(channel.read(any(ByteBuffer.class), anyLong())).thenAnswer(invocation -> {
            if (ioFailure) {
                throw new IOException("injected read failure");
            }
            return 0;
        });
        try (Segment segment = new Segment(Path.of("broken.seg"),
            new Segment.SegmentHeader(UUID.randomUUID(), 1), channel, 200, 200)) {
            SegmentScanner scanner = new SegmentScanner(segment, 40, 200, 64);
            assertThrows(IOException.class, () -> scanner.nextRecord(codec));
            verify(channel, times(1)).read(any(ByteBuffer.class), anyLong());
        }
    }
    
    @Test
    void validatesRangesAndAllowsAnEmptyScanWithoutIo() throws Exception {
        try (Segment segment = source(new byte[20], 20, Integer.MAX_VALUE)) {
            assertThrows(IOException.class, () -> new SegmentScanner(segment, 39, 60, 64));
            assertThrows(IOException.class, () -> new SegmentScanner(segment, 50, 49, 64));
            assertThrows(IOException.class, () -> new SegmentScanner(segment, 40, 61, 64));
            assertThrows(IOException.class, () -> new SegmentScanner(segment, 40, 60, 15));
            assertThrows(IOException.class, () -> new SegmentScanner(null, 40, 60, 64));
            SegmentScanner empty = new SegmentScanner(segment, 60, 60, 64);
            assertFalse(empty.hasNext());
            assertThrows(IOException.class, () -> empty.nextRecord(codec));
            assertTrue(reads.isEmpty());
        }
    }
    
    @Test
    void rejectsRecordEndOverflowBeforeReadingTheRemainder() throws Exception {
        byte[] bytes = record(1, 50).array();
        FileChannel channel = mock(FileChannel.class);
        when(channel.read(any(ByteBuffer.class), anyLong())).thenAnswer(invocation -> {
            ByteBuffer target = invocation.getArgument(0);
            int count = target.remaining();
            target.put(bytes, 0, count);
            return count;
        });
        try (Segment segment = new Segment(Path.of("overflow.seg"),
            new Segment.SegmentHeader(UUID.randomUUID(), 1), channel, Long.MAX_VALUE,
            Long.MAX_VALUE)) {
            SegmentScanner scanner =
                new SegmentScanner(segment, Long.MAX_VALUE - 32, Long.MAX_VALUE, 16);
            assertThrows(IOException.class, () -> scanner.nextRecord(codec));
            verify(channel, times(1)).read(any(ByteBuffer.class), anyLong());
        }
    }
    
    private Segment source(byte[] bytes, int physicalBytes, int chunk) throws IOException {
        FileChannel channel = mock(FileChannel.class);
        when(channel.read(any(ByteBuffer.class), anyLong())).thenAnswer(invocation -> {
            ByteBuffer target = invocation.getArgument(0);
            long offset = invocation.getArgument(1);
            int position = Math.toIntExact(offset - 40);
            int count = Math.min(Math.min(target.remaining(), chunk), physicalBytes - position);
            if (count <= 0) {
                return -1;
            }
            reads.add(new long[] {offset, count});
            target.put(bytes, position, count);
            return count;
        });
        return new Segment(Path.of("memory.seg"), new Segment.SegmentHeader(UUID.randomUUID(), 1),
            channel, 40L + bytes.length, 40L + bytes.length);
    }
    
    private ByteBuffer record(long index, int payload) throws IOException {
        LogEntry entry = new LogEntry(EntryType.ENTRY_TYPE_DATA);
        entry.setId(new LogId(index, 7));
        entry.setData(ByteBuffer.wrap(new byte[payload]));
        return codec.encode(entry);
    }
    
    private static byte[] concatenate(List<ByteBuffer> records) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (ByteBuffer record : records) {
            bytes.write(copy(record));
        }
        return bytes.toByteArray();
    }
    
    private static byte[] copy(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        return bytes;
    }
}
