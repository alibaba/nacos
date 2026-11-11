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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SegmentTest {
    
    @TempDir
    Path directory;
    
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
