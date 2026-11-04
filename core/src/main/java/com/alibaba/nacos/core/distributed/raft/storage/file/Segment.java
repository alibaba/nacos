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
import java.util.Objects;
import java.util.UUID;

final class Segment implements AutoCloseable {
    
    static final long MAX_SEGMENT_BYTES = 128L * 1024 * 1024;
    
    private final Path path;
    
    private final SegmentHeader header;
    
    private final FileChannel channel;
    
    private final long maxBytes;
    
    private long logicalEnd;
    
    Segment(Path path, SegmentHeader header, FileChannel channel, long logicalEnd, long maxBytes)
        throws IOException {
        this.path = Objects.requireNonNull(path, "path");
        this.header = Objects.requireNonNull(header, "header");
        this.channel = Objects.requireNonNull(channel, "channel");
        validateHeader(header);
        if (maxBytes < FileLogFormat.SEGMENT_HEADER_SIZE
            || logicalEnd < FileLogFormat.SEGMENT_HEADER_SIZE
            || logicalEnd > maxBytes) {
            throw new IOException("Invalid logical segment boundary: " + path);
        }
        this.logicalEnd = logicalEnd;
        this.maxBytes = maxBytes;
    }
    
    static Segment create(Path directory, UUID storeId, long segmentId)
        throws IOException {
        SegmentHeader header = new SegmentHeader(storeId, segmentId);
        validateHeader(header);
        Path path = directory.resolve(FileLogFormat.segmentName(segmentId));
        FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW,
            StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try {
            writeFully(channel, encodeHeader(header), 0, path);
            return new Segment(path, header, channel, FileLogFormat.SEGMENT_HEADER_SIZE,
                MAX_SEGMENT_BYTES);
        } catch (IOException | RuntimeException error) {
            closeAfterFailure(channel, error);
            throw error;
        }
    }
    
    static Segment open(Path path, long logicalEnd) throws IOException {
        if (!java.nio.file.Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Not a regular segment file: " + path);
        }
        FileChannel channel =
            FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
        try {
            SegmentHeader header = readHeader(channel, path);
            long size = channel.size();
            if (size < logicalEnd || size > MAX_SEGMENT_BYTES) {
                throw new IOException("Invalid physical segment size: " + path);
            }
            return new Segment(path, header, channel, logicalEnd, MAX_SEGMENT_BYTES);
        } catch (IOException | RuntimeException e) {
            closeAfterFailure(channel, e);
            throw e;
        }
    }
    
    private static ByteBuffer encodeHeader(SegmentHeader header) {
        ByteBuffer buffer = ByteBuffer.allocate(FileLogFormat.SEGMENT_HEADER_SIZE)
            .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(FileLogFormat.SEGMENT_MAGIC);
        buffer.putShort(FileLogFormat.VERSION);
        buffer.putShort((short) FileLogFormat.SEGMENT_HEADER_SIZE);
        buffer.putLong(header.getStoreId().getMostSignificantBits());
        buffer.putLong(header.getStoreId().getLeastSignificantBits());
        buffer.putLong(header.getSegmentId());
        buffer.putLong(
            FileLogFormat.crc64(buffer.array(), 0, FileLogFormat.SEGMENT_HEADER_SIZE - Long.BYTES));
        return buffer.flip();
    }
    
    private static SegmentHeader readHeader(FileChannel channel, Path path) throws IOException {
        if (channel.size() < FileLogFormat.SEGMENT_HEADER_SIZE) {
            throw new CorruptHeaderException("Truncated segment header: " + path);
        }
        ByteBuffer buffer = ByteBuffer.allocate(FileLogFormat.SEGMENT_HEADER_SIZE)
            .order(ByteOrder.BIG_ENDIAN);
        readFully(channel, buffer, 0, path);
        buffer.flip();
        if (buffer.getInt() != FileLogFormat.SEGMENT_MAGIC) {
            throw new CorruptHeaderException("Invalid segment magic: " + path);
        }
        if (buffer.getShort() != FileLogFormat.VERSION) {
            throw new IOException("Unsupported segment version: " + path);
        }
        if (Short.toUnsignedInt(buffer.getShort()) != FileLogFormat.SEGMENT_HEADER_SIZE) {
            throw new CorruptHeaderException("Invalid segment header size: " + path);
        }
        SegmentHeader header =
            new SegmentHeader(new UUID(buffer.getLong(), buffer.getLong()), buffer.getLong());
        long checksum = buffer.getLong();
        if (checksum != FileLogFormat.crc64(buffer.array(), 0,
            FileLogFormat.SEGMENT_HEADER_SIZE - Long.BYTES)) {
            throw new CorruptHeaderException("Segment header checksum mismatch: " + path);
        }
        validateHeader(header);
        return header;
    }
    
    private static void validateHeader(SegmentHeader header) throws IOException {
        if (header.getStoreId() == null || header.getSegmentId() <= 0) {
            throw new IOException("Invalid segment identity");
        }
    }
    
    long append(ByteBuffer record) throws IOException {
        int length = record.remaining();
        if (length < FileLogFormat.RECORD_OVERHEAD) {
            throw new IOException("Record is too small for segment: " + path);
        }
        final long following;
        try {
            following = Math.addExact(logicalEnd, length);
        } catch (ArithmeticException error) {
            throw new IOException("Segment append boundary overflow: " + path, error);
        }
        if (following > maxBytes) {
            throw new IOException("Record does not fit segment: " + path);
        }
        long offset = logicalEnd;
        writeFully(channel, record, offset, path);
        logicalEnd = following;
        return offset;
    }
    
    ByteBuffer read(long offset, int length, long publishedEnd) throws IOException {
        if (offset < FileLogFormat.SEGMENT_HEADER_SIZE || length < 0
            || publishedEnd > logicalEnd) {
            throw new IOException("Invalid segment read at " + path + " offset " + offset);
        }
        final long end;
        try {
            end = Math.addExact(offset, length);
        } catch (ArithmeticException e) {
            throw new IOException(
                "Segment read boundary overflow in " + path + " at offset " + offset, e);
        }
        if (end > publishedEnd) {
            throw new IOException("Invalid segment read at " + path + " offset " + offset);
        }
        ByteBuffer result = ByteBuffer.allocate(length).order(ByteOrder.BIG_ENDIAN);
        readFully(channel, result, offset, path);
        return result.flip();
    }
    
    Path getPath() {
        return path;
    }
    
    SegmentHeader getHeader() {
        return header;
    }
    
    long getLogicalEnd() {
        return logicalEnd;
    }
    
    long getPhysicalSize() throws IOException {
        return channel.size();
    }
    
    void truncateToLogicalEnd() throws IOException {
        channel.truncate(logicalEnd);
    }
    
    void truncate(long end) throws IOException {
        if (end < FileLogFormat.SEGMENT_HEADER_SIZE || end > logicalEnd) {
            throw new IOException("Invalid segment truncation boundary: " + end);
        }
        channel.truncate(end);
        logicalEnd = end;
    }
    
    void force() throws IOException {
        channel.force(true);
    }
    
    @Override
    public void close() throws IOException {
        channel.close();
    }
    
    private static void readFully(FileChannel channel, ByteBuffer target, long offset, Path path)
        throws IOException {
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
    
    private static void writeFully(FileChannel channel, ByteBuffer source, long offset, Path path)
        throws IOException {
        long position = offset;
        while (source.hasRemaining()) {
            int written = channel.write(source, position);
            if (written == 0) {
                throw new IOException("No write progress in " + path + " at offset " + position);
            }
            position = Math.addExact(position, written);
        }
    }
    
    private static void closeAfterFailure(FileChannel channel, Exception failure) {
        try {
            channel.close();
        } catch (IOException closeError) {
            failure.addSuppressed(closeError);
        }
    }
    
    /** A malformed header does not establish ownership of an unpublished file. */
    static final class CorruptHeaderException extends IOException {
        
        private static final long serialVersionUID = 1L;
        
        CorruptHeaderException(String message) {
            super(message);
        }
    }
    
    static final class SegmentHeader {
        
        private final UUID storeId;
        
        private final long segmentId;
        
        SegmentHeader(UUID storeId, long segmentId) {
            this.storeId = storeId;
            this.segmentId = segmentId;
        }
        
        UUID getStoreId() {
            return storeId;
        }
        
        long getSegmentId() {
            return segmentId;
        }
        
    }
}
