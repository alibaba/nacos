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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

final class Index implements AutoCloseable {
    
    private final Path path;
    
    private final FileChannel channel;
    
    private final long baseIndex;
    
    private long nextIndex;
    
    Index(Path path, FileChannel channel, long baseIndex, long nextIndex) {
        this.path = path;
        this.channel = channel;
        this.baseIndex = baseIndex;
        this.nextIndex = nextIndex;
    }
    
    static Index create(Path path, long baseIndex) throws IOException {
        if (baseIndex <= 0) {
            throw new IOException("Invalid index base: " + baseIndex);
        }
        FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.READ,
            StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        return new Index(path, channel, baseIndex, baseIndex);
    }
    
    static Index open(Path path, long baseIndex, long lastIndex) throws IOException {
        return openValidated(path, baseIndex, lastIndex, false);
    }
    
    static Index openWritable(Path path, long baseIndex, long lastIndex) throws IOException {
        return openValidated(path, baseIndex, lastIndex, true);
    }
    
    private static Index openValidated(Path path, long baseIndex, long lastIndex,
        boolean writable)
        throws IOException {
        if (baseIndex <= 0 || lastIndex < baseIndex) {
            throw new IOException("Invalid index range: " + baseIndex + ".." + lastIndex);
        }
        final long nextIndex;
        try {
            nextIndex = Math.addExact(lastIndex, 1);
        } catch (ArithmeticException e) {
            throw new IOException("Index range overflow: " + baseIndex + ".." + lastIndex, e);
        }
        FileChannel channel = writable
            ? FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS)
            : FileChannel.open(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try {
            long expectedSize = position(baseIndex, nextIndex);
            if (channel.size() != expectedSize) {
                throw new IOException("Index size mismatch: " + path);
            }
            return new Index(path, channel, baseIndex, nextIndex);
        } catch (IOException | RuntimeException e) {
            closeAfterFailure(channel, e);
            throw e;
        }
    }
    
    void append(long index, long offset, int recordSize) throws IOException {
        if (index != nextIndex || offset < FileLogFormat.SEGMENT_HEADER_SIZE
            || recordSize < FileLogFormat.RECORD_OVERHEAD) {
            throw new IOException("Invalid index entry for " + path + " at index " + index);
        }
        try {
            Math.addExact(offset, recordSize);
        } catch (ArithmeticException e) {
            throw new IOException(
                "Index entry range overflow in " + path + " at index " + index, e);
        }
        final long followingIndex;
        try {
            followingIndex = Math.addExact(nextIndex, 1);
        } catch (ArithmeticException e) {
            throw new IOException("Index range overflow in " + path + " at index " + index, e);
        }
        long entryPosition = position(baseIndex, index);
        position(baseIndex, followingIndex);
        ByteBuffer entry = ByteBuffer.allocate(FileLogFormat.INDEX_ENTRY_SIZE)
            .order(ByteOrder.BIG_ENDIAN);
        entry.putLong(offset).putInt(recordSize).flip();
        writeFully(entry, entryPosition);
        nextIndex = followingIndex;
    }
    
    Entry read(long index, long firstOffset, long publishedEnd) throws IOException {
        if (index < baseIndex || index >= nextIndex) {
            throw new IndexOutOfBoundsException("Index outside retained range: " + index);
        }
        ByteBuffer entry = ByteBuffer.allocate(FileLogFormat.INDEX_ENTRY_SIZE)
            .order(ByteOrder.BIG_ENDIAN);
        readFully(entry, position(baseIndex, index));
        entry.flip();
        Entry result = new Entry(entry.getLong(), entry.getInt());
        if (result.getOffset() < firstOffset
            || result.getRecordSize() < FileLogFormat.RECORD_OVERHEAD
            || result.getRecordSize() > LogRecordCodec.MAX_RECORD_BYTES) {
            throw new IOException("Invalid index entry in " + path + " at index " + index);
        }
        final long recordEnd;
        try {
            recordEnd = Math.addExact(result.getOffset(), result.getRecordSize());
        } catch (ArithmeticException e) {
            throw new IOException(
                "Index entry range overflow in " + path + " at index " + index, e);
        }
        if (recordEnd > publishedEnd) {
            throw new IOException("Invalid index entry in " + path + " at index " + index);
        }
        return result;
    }
    
    void truncateSuffix(long lastIndexKept) throws IOException {
        if (lastIndexKept < Math.subtractExact(baseIndex, 1) || lastIndexKept >= nextIndex) {
            throw new IOException("Invalid index truncation boundary: " + lastIndexKept);
        }
        long following = Math.addExact(lastIndexKept, 1);
        channel.truncate(position(baseIndex, following));
        nextIndex = following;
    }
    
    private static long position(long baseIndex, long index) throws IOException {
        if (baseIndex <= 0 || index < baseIndex) {
            throw new IOException("Invalid index slot");
        }
        try {
            return Math.multiplyExact(Math.subtractExact(index, baseIndex),
                (long) FileLogFormat.INDEX_ENTRY_SIZE);
        } catch (ArithmeticException e) {
            throw new IOException("Index position overflow", e);
        }
    }
    
    private void readFully(ByteBuffer target, long offset) throws IOException {
        long position = offset;
        while (target.hasRemaining()) {
            int read = channel.read(target, position);
            if (read < 0) {
                throw new IOException("Unexpected EOF in " + path + " at offset " + position);
            }
            if (read == 0) {
                throw new IOException("No read progress in " + path + " at offset " + position);
            }
            position = Math.addExact(position, read);
        }
    }
    
    private void writeFully(ByteBuffer source, long offset) throws IOException {
        long position = offset;
        while (source.hasRemaining()) {
            int written = channel.write(source, position);
            if (written == 0) {
                throw new IOException("No write progress in " + path + " at offset " + position);
            }
            position = Math.addExact(position, written);
        }
    }
    
    @Override
    public void close() throws IOException {
        channel.close();
    }
    
    private static void closeAfterFailure(FileChannel channel, Exception failure) {
        try {
            channel.close();
        } catch (IOException closeError) {
            failure.addSuppressed(closeError);
        }
    }
    
    static final class Entry {
        
        private final long offset;
        
        private final int recordSize;
        
        Entry(long offset, int recordSize) {
            this.offset = offset;
            this.recordSize = recordSize;
        }
        
        long getOffset() {
            return offset;
        }
        
        int getRecordSize() {
            return recordSize;
        }
    }
}
