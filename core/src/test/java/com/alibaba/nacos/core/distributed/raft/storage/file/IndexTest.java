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
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class IndexTest {
    
    @TempDir
    Path directory;
    
    @Test
    void batchMatchesIndividualSlotsAcrossBufferBoundaries() throws Exception {
        int count = 10000;
        int[] sizes = new int[count + 2];
        Path batchPath = directory.resolve("batch.idx");
        Path singlePath = directory.resolve("single.idx");
        long end = 48;
        try (Index batch = Index.create(batchPath, 101);
            Index single = Index.create(singlePath, 101)) {
            for (int i = 1; i <= count; i++) {
                sizes[i] = 24 + i % 71;
                single.append(100L + i, end, sizes[i]);
                end += sizes[i];
            }
            batch.appendBatch(101, 48, sizes, 1, count);
            assertArrayEquals(Files.readAllBytes(singlePath), Files.readAllBytes(batchPath));
            assertEquals(end - sizes[count], batch.read(100L + count, 48, end).getOffset());
            batch.append(101L + count, end, 24);
            assertEquals(end, batch.read(101L + count, 48, end + 24).getOffset());
        }
    }
    
    @Test
    void combinesSmallSlotsIntoOneBoundedWrite() throws Exception {
        FileChannel channel = mock(FileChannel.class);
        AtomicInteger calls = new AtomicInteger();
        int[] sizes = new int[128];
        java.util.Arrays.fill(sizes, 24);
        when(channel.write(any(ByteBuffer.class), anyLong())).thenAnswer(invocation -> {
            ByteBuffer buffer = invocation.getArgument(0);
            assertEquals(0L, (long) invocation.getArgument(1));
            assertEquals(128 * 12, buffer.remaining());
            int length = buffer.remaining();
            for (int i = 0; i < 128; i++) {
                assertEquals(40L + i * 24L, buffer.getLong());
                assertEquals(24, buffer.getInt());
            }
            calls.incrementAndGet();
            return length;
        });
        try (Index index = new Index(Path.of("combined.idx"), channel, 1, 1)) {
            index.appendBatch(1, 40, sizes, 0, sizes.length);
            assertEquals(1, calls.get());
        }
    }
    
    @Test
    void rejectsInvalidBatchBeforeAnyFileIo() throws Exception {
        FileChannel channel = mock(FileChannel.class);
        try (Index index = new Index(Path.of("invalid.idx"), channel, 1, 1)) {
            assertThrows(IOException.class, () -> index.appendBatch(1, 40, null, 0, 1));
            assertThrows(IOException.class, () -> index.appendBatch(1, 40, new int[] {24}, 0, 0));
            assertThrows(IOException.class, () -> index.appendBatch(1, 40, new int[] {24}, -1, 1));
            assertThrows(IOException.class,
                () -> index.appendBatch(1, 40, new int[] {24}, 1, Integer.MAX_VALUE));
            assertThrows(IOException.class, () -> index.appendBatch(2, 40, new int[] {24}, 0, 1));
            assertThrows(IOException.class, () -> index.appendBatch(1, 39, new int[] {24}, 0, 1));
            assertThrows(IOException.class,
                () -> index.appendBatch(1, 40, new int[] {24, 23}, 0, 2));
            assertThrows(IOException.class,
                () -> index.appendBatch(1, 40, new int[] {24, Integer.MAX_VALUE}, 0, 2));
            assertThrows(IOException.class,
                () -> index.appendBatch(1, Long.MAX_VALUE - 24, new int[] {24, 24}, 0, 2));
            verifyNoInteractions(channel);
        }
        FileChannel overflow = mock(FileChannel.class);
        try (Index index = new Index(Path.of("overflow-batch.idx"), overflow,
            Long.MAX_VALUE, Long.MAX_VALUE)) {
            assertThrows(IOException.class,
                () -> index.appendBatch(Long.MAX_VALUE, 40, new int[] {24}, 0, 1));
            verifyNoInteractions(overflow);
        }
        FileChannel slots = mock(FileChannel.class);
        try (Index index = new Index(Path.of("overflow-slots.idx"), slots, 1, Long.MAX_VALUE - 1)) {
            assertThrows(IOException.class,
                () -> index.appendBatch(Long.MAX_VALUE - 1, 40, new int[] {24}, 0, 1));
            verifyNoInteractions(slots);
        }
    }
    
    @Test
    void batchLoopsOnPositiveShortWrites() throws Exception {
        FileChannel channel = mock(FileChannel.class);
        byte[] bytes = new byte[36];
        when(channel.write(any(ByteBuffer.class), anyLong())).thenAnswer(invocation -> {
            ByteBuffer source = invocation.getArgument(0);
            int offset = Math.toIntExact(invocation.getArgument(1));
            int length = Math.min(5, source.remaining());
            source.get(bytes, offset, length);
            return length;
        });
        try (Index index = new Index(Path.of("short-batch.idx"), channel, 10, 10)) {
            index.appendBatch(10, 40, new int[] {24, 25, 26}, 0, 3);
        }
        assertArrayEquals(ByteBuffer.allocate(36).putLong(40).putInt(24)
            .putLong(64).putInt(25).putLong(89).putInt(26).array(), bytes);
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void partialBatchFailureDoesNotAdvanceNextIndex(boolean zeroProgress) throws Exception {
        FileChannel channel = mock(FileChannel.class);
        AtomicInteger calls = new AtomicInteger();
        when(channel.write(any(ByteBuffer.class), anyLong())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                ByteBuffer source = invocation.getArgument(0);
                source.position(source.position() + 5);
                return 5;
            }
            if (zeroProgress) {
                return 0;
            }
            throw new IOException("partial index batch");
        });
        try (Index index = new Index(Path.of("failed-batch.idx"), channel, 10, 10)) {
            assertThrows(IOException.class,
                () -> index.appendBatch(10, 40, new int[] {24, 24}, 0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> index.read(10, 40, 88));
        }
    }
    
    @Test
    void readsOnlySuccessfullyAppendedSlots() throws Exception {
        try (Index index = Index.create(directory.resolve("active.idx"), 100)) {
            assertThrows(IndexOutOfBoundsException.class, () -> index.read(100, 40, 64));
            index.append(100, 48, 24);
            assertEquals(48, index.read(100, 48, 72).getOffset());
            assertThrows(IndexOutOfBoundsException.class, () -> index.read(101, 48, 72));
        }
    }
    
    @Test
    void storesOnlyOffsetAndRecordSize() throws Exception {
        Path path = directory.resolve("segment.idx");
        try (Index index = Index.create(path, 100)) {
            index.append(100, 48, 30);
            index.append(101, 78, 40);
        }
        
        assertEquals(2L * FileLogFormat.INDEX_ENTRY_SIZE, Files.size(path));
        try (Index index = Index.open(path, 100, 101)) {
            Index.Entry first = index.read(100, 48, 118);
            Index.Entry second = index.read(101, 48, 118);
            assertEquals(48, first.getOffset());
            assertEquals(30, first.getRecordSize());
            assertEquals(78, second.getOffset());
            assertEquals(40, second.getRecordSize());
            assertThrows(IndexOutOfBoundsException.class, () -> index.read(102, 48, 118));
        }
    }
    
    @Test
    void writableIndexTruncatesSlotsAndAcceptsReplacementAfterReopen() throws Exception {
        Path path = directory.resolve("writable.idx");
        try (Index index = Index.create(path, 100)) {
            index.append(100, 40, 24);
            index.append(101, 64, 24);
            index.append(102, 88, 24);
        }
        try (Index index = Index.openWritable(path, 100, 102)) {
            index.truncateSuffix(100);
            assertEquals(FileLogFormat.INDEX_ENTRY_SIZE, Files.size(path));
            assertThrows(IndexOutOfBoundsException.class, () -> index.read(101, 40, 112));
            index.append(101, 64, 32);
            assertEquals(32, index.read(101, 40, 96).getRecordSize());
        }
        try (Index index = Index.open(path, 100, 101)) {
            assertEquals(64, index.read(101, 40, 96).getOffset());
        }
    }
    
    @Test
    void rejectsAnIndexWithTheWrongDerivedSize() throws Exception {
        Path path = directory.resolve("segment.idx");
        Files.write(path, new byte[FileLogFormat.INDEX_ENTRY_SIZE - 1]);
        assertThrows(IOException.class, () -> Index.open(path, 10, 10));
    }
    
    @Test
    void loopsOnShortWritesAndRejectsNoProgress() throws Exception {
        FileChannel channel = mock(FileChannel.class);
        byte[] writtenBytes = new byte[FileLogFormat.INDEX_ENTRY_SIZE];
        List<Long> offsets = new ArrayList<>();
        when(channel.write(any(ByteBuffer.class), anyLong())).thenAnswer(invocation -> {
            ByteBuffer source = invocation.getArgument(0);
            long offset = invocation.getArgument(1);
            offsets.add(offset);
            int written = Math.min(2, source.remaining());
            source.get(writtenBytes, Math.toIntExact(offset), written);
            return written;
        });
        try (Index index = new Index(Path.of("short.idx"), channel, 10, 10)) {
            index.append(10, 48, 24);
        }
        assertEquals(List.of(0L, 2L, 4L, 6L, 8L, 10L), offsets);
        assertArrayEquals(ByteBuffer.allocate(FileLogFormat.INDEX_ENTRY_SIZE)
            .putLong(48).putInt(24).array(), writtenBytes);
        
        FileChannel stalled = mock(FileChannel.class);
        when(stalled.write(any(ByteBuffer.class), anyLong())).thenReturn(0);
        try (Index index = new Index(Path.of("stalled.idx"), stalled, 10, 10)) {
            assertThrows(IOException.class, () -> index.append(10, 48, 24));
        }
    }
    
    @Test
    void rejectsIndexOverflowBeforeWriting() throws Exception {
        Path path = directory.resolve("overflow.idx");
        try (Index index = Index.create(path, Long.MAX_VALUE)) {
            IOException error = assertThrows(IOException.class,
                () -> index.append(Long.MAX_VALUE, FileLogFormat.SEGMENT_HEADER_SIZE,
                    FileLogFormat.RECORD_OVERHEAD));
            assertTrue(error.getMessage().contains("Index range overflow"));
        }
        assertEquals(0, Files.size(path));
    }
    
    @Test
    void reportsOverflowInPersistedEntryRange() throws Exception {
        Path path = directory.resolve("corrupt.idx");
        ByteBuffer entry = ByteBuffer.allocate(FileLogFormat.INDEX_ENTRY_SIZE)
            .order(ByteOrder.BIG_ENDIAN);
        entry.putLong(Long.MAX_VALUE).putInt(FileLogFormat.RECORD_OVERHEAD);
        Files.write(path, entry.array());
        
        try (Index index = Index.open(path, 1, 1)) {
            IOException error = assertThrows(IOException.class,
                () -> index.read(1, FileLogFormat.SEGMENT_HEADER_SIZE, Long.MAX_VALUE));
            assertTrue(error.getMessage().contains("Index entry range overflow"));
            assertTrue(error.getMessage().contains("index 1"));
        }
    }
}
