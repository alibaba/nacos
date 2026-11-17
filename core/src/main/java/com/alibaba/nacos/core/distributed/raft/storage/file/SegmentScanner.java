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

/** Sequential, bounded reads of one segment's published records. */
final class SegmentScanner {
    
    private final Segment segment;
    
    private final long publishedEnd;
    
    private final int bufferBytes;
    
    private ByteBuffer buffer;
    
    private long nextRecordOffset;
    
    private long nextReadOffset;
    
    SegmentScanner(Segment segment, long firstRecordOffset, long publishedEnd, int bufferBytes)
        throws IOException {
        if (segment == null || firstRecordOffset < FileLogFormat.SEGMENT_HEADER_SIZE
            || publishedEnd < firstRecordOffset || publishedEnd > segment.getLogicalEnd()
            || bufferBytes < FileLogFormat.RECORD_HEADER_SIZE) {
            throw new IOException("Invalid segment scan range or buffer size");
        }
        this.segment = segment;
        this.publishedEnd = publishedEnd;
        this.bufferBytes = bufferBytes;
        this.nextRecordOffset = firstRecordOffset;
        this.nextReadOffset = firstRecordOffset;
    }
    
    boolean hasNext() {
        return nextRecordOffset < publishedEnd;
    }
    
    /**
     * Returns one record, to be decoded before the next call can overwrite its bytes.
     * The caller must abandon this scanner after any scan or decoding failure.
     *
     * @param codec validates the header before record-sized allocation
     * @return a bounded record view, or a dedicated buffer for an oversized record
     * @throws IOException if exhausted, malformed, or unable to read a published record
     */
    ByteBuffer nextRecord(LogRecordCodec codec) throws IOException {
        if (!hasNext()) {
            throw new IOException("No published segment record remains");
        }
        if (publishedEnd - nextRecordOffset < FileLogFormat.RECORD_HEADER_SIZE) {
            throw new IOException("Truncated published record header in " + segment.getPath());
        }
        if (buffer == null) {
            buffer = ByteBuffer.allocate((int) Math.min(bufferBytes,
                publishedEnd - nextRecordOffset));
            buffer.limit(0);
        }
        ensureAvailable(FileLogFormat.RECORD_HEADER_SIZE);
        int size = codec.recordSize(buffer);
        final long recordEnd;
        try {
            recordEnd = Math.addExact(nextRecordOffset, size);
        } catch (ArithmeticException e) {
            throw new IOException("Published record boundary overflow in " + segment.getPath(), e);
        }
        if (recordEnd > publishedEnd) {
            throw new IOException("Published record is incomplete in " + segment.getPath()
                + " at offset " + nextRecordOffset);
        }
        final ByteBuffer record;
        if (size > buffer.capacity()) {
            record = ByteBuffer.allocate(size);
            record.put(buffer);
            segment.readInto(nextReadOffset, record, publishedEnd);
            nextReadOffset = recordEnd;
            buffer.clear().limit(0);
            record.flip();
        } else {
            ensureAvailable(size);
            record = buffer.slice(buffer.position(), size);
            buffer.position(buffer.position() + size);
        }
        nextRecordOffset = recordEnd;
        return record;
    }
    
    private void ensureAvailable(int required) throws IOException {
        if (buffer.remaining() >= required) {
            return;
        }
        buffer.compact();
        int readBytes = (int) Math.min(buffer.remaining(), publishedEnd - nextReadOffset);
        buffer.limit(buffer.position() + readBytes);
        segment.readInto(nextReadOffset, buffer, publishedEnd);
        nextReadOffset += readBytes;
        buffer.flip();
        if (buffer.remaining() < required) {
            throw new IOException("Truncated published record in " + segment.getPath());
        }
    }
}
