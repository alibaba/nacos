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

import com.alipay.sofa.jraft.conf.Configuration;
import com.alipay.sofa.jraft.conf.ConfigurationEntry;
import com.alipay.sofa.jraft.entity.EnumOutter.EntryType;
import com.alipay.sofa.jraft.entity.LogEntry;
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.codec.LogEntryCodecFactory;
import com.alipay.sofa.jraft.entity.codec.LogEntryDecoder;
import com.alipay.sofa.jraft.entity.codec.LogEntryEncoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Objects;

final class LogRecordCodec {
    
    static final int MAX_RECORD_BYTES = 64 * 1024 * 1024;
    
    private final LogEntryEncoder encoder;
    
    private final LogEntryDecoder decoder;
    
    private final int maxRecordBytes;
    
    LogRecordCodec(LogEntryCodecFactory codecFactory) {
        this(codecFactory, MAX_RECORD_BYTES);
    }
    
    LogRecordCodec(LogEntryCodecFactory codecFactory, int maxRecordBytes) {
        Objects.requireNonNull(codecFactory, "codecFactory");
        this.encoder = Objects.requireNonNull(codecFactory.encoder(), "encoder");
        this.decoder = Objects.requireNonNull(codecFactory.decoder(), "decoder");
        if (maxRecordBytes < FileLogFormat.RECORD_OVERHEAD) {
            throw new IllegalArgumentException("maxRecordBytes is too small");
        }
        this.maxRecordBytes = maxRecordBytes;
    }
    
    ByteBuffer encode(LogEntry entry) throws IOException {
        validateEntry(entry);
        final byte[] payload;
        try {
            payload = encoder.encode(entry);
        } catch (RuntimeException e) {
            throw new IOException("Failed to encode Raft log entry", e);
        }
        if (payload == null) {
            throw new IOException("Raft log entry encoder returned null");
        }
        int size = checkedRecordSize(payload.length);
        ByteBuffer record = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        record.putInt(FileLogFormat.RECORD_MAGIC);
        record.putShort(FileLogFormat.VERSION);
        record.putShort((short) FileLogFormat.RECORD_HEADER_SIZE);
        record.putInt(payload.length);
        record.putInt(FileLogFormat.crc32(record.array(), 0, 12));
        record.put(payload);
        record.putLong(FileLogFormat.crc64(payload, 0, payload.length));
        return record.flip();
    }
    
    LogEntry decode(ByteBuffer source) throws IOException {
        int size = recordSize(source);
        if (source.remaining() < size) {
            throw new IOException("Truncated file log record");
        }
        int start = source.position();
        int payloadLength = Math.subtractExact(size, FileLogFormat.RECORD_OVERHEAD);
        byte[] payload = new byte[payloadLength];
        ByteBuffer record = source.duplicate().order(ByteOrder.BIG_ENDIAN);
        record.position(Math.addExact(start, FileLogFormat.RECORD_HEADER_SIZE));
        record.get(payload);
        long expectedChecksum = record.getLong();
        if (FileLogFormat.crc64(payload, 0, payload.length) != expectedChecksum) {
            throw new IOException("File log record payload checksum mismatch");
        }
        final LogEntry decoded;
        try {
            decoded = decoder.decode(payload);
        } catch (RuntimeException e) {
            throw new IOException("Failed to decode Raft log entry", e);
        }
        validateEntry(decoded);
        source.position(Math.addExact(start, size));
        return decoded;
    }
    
    int recordSize(ByteBuffer source) throws IOException {
        ByteBuffer header = source.duplicate().order(ByteOrder.BIG_ENDIAN);
        if (header.remaining() < FileLogFormat.RECORD_HEADER_SIZE) {
            throw new IOException("Truncated file log record header");
        }
        byte[] bytes = new byte[FileLogFormat.RECORD_HEADER_SIZE];
        header.get(bytes);
        ByteBuffer fields = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        int magic = fields.getInt();
        short version = fields.getShort();
        int headerSize = Short.toUnsignedInt(fields.getShort());
        int payloadLength = fields.getInt();
        int checksum = fields.getInt();
        if (checksum != FileLogFormat.crc32(bytes, 0, 12)) {
            throw new IOException("File log record header checksum mismatch");
        }
        if (magic != FileLogFormat.RECORD_MAGIC || version != FileLogFormat.VERSION
            || headerSize != FileLogFormat.RECORD_HEADER_SIZE) {
            throw new IOException("Unsupported file log record header");
        }
        return checkedRecordSize(payloadLength);
    }
    
    private int checkedRecordSize(int payloadLength) throws IOException {
        if (payloadLength < 0 || payloadLength > maxRecordBytes - FileLogFormat.RECORD_OVERHEAD) {
            throw new IOException("File log record payload length exceeds bounds");
        }
        return Math.addExact(FileLogFormat.RECORD_OVERHEAD, payloadLength);
    }
    
    private static void validateEntry(LogEntry entry) throws IOException {
        if (entry == null || entry.getId() == null || entry.getId().getIndex() <= 0
            || entry.getId().getTerm() < 0) {
            throw new IOException("Invalid Raft log entry identity");
        }
        EntryType type = entry.getType();
        if (type == null || type == EntryType.ENTRY_TYPE_UNKNOWN) {
            throw new IOException("Unknown Raft log entry type");
        }
        if (type == EntryType.ENTRY_TYPE_CONFIGURATION) {
            validateConfiguration(entry);
        }
    }
    
    private static void validateConfiguration(LogEntry entry) throws IOException {
        if (entry.getPeers() == null || entry.getPeers().isEmpty()) {
            throw new IOException("Configuration log requires peers");
        }
        validateMembers(entry.getPeers());
        validateMembers(entry.getLearners());
        validateMembers(entry.getOldPeers());
        validateMembers(entry.getOldLearners());
        boolean hasOldPeers = entry.getOldPeers() != null && !entry.getOldPeers().isEmpty();
        if (!hasOldPeers && entry.getOldLearners() != null && !entry.getOldLearners().isEmpty()) {
            throw new IOException("Old learners require old peers in configuration log");
        }
        Configuration current = new Configuration(entry.getPeers(), entry.getLearners());
        Configuration old = hasOldPeers
            ? new Configuration(entry.getOldPeers(), entry.getOldLearners()) : new Configuration();
        if (!new ConfigurationEntry(entry.getId(), current, old).isValid()) {
            throw new IOException("Invalid configuration log members");
        }
    }
    
    private static void validateMembers(List<PeerId> members) throws IOException {
        if (members != null && members.stream().anyMatch(Objects::isNull)) {
            throw new IOException("Null member in configuration log");
        }
    }
}
