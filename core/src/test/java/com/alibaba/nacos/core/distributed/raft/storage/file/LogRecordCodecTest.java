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
import com.alipay.sofa.jraft.entity.PeerId;
import com.alipay.sofa.jraft.entity.codec.LogEntryCodecFactory;
import com.alipay.sofa.jraft.entity.codec.LogEntryDecoder;
import com.alipay.sofa.jraft.entity.codec.v2.LogEntryV2CodecFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LogRecordCodecTest {
    
    private final LogRecordCodec codec = new LogRecordCodec(LogEntryV2CodecFactory.getInstance());
    
    @ParameterizedTest
    @ValueSource(strings = {"heap", "readonly", "direct", "slice", "sliceReadonly",
        "directReadonly"})
    void decodesBufferViewsWithoutChangingTheirEnvelope(String kind) throws Exception {
        LogEntry expected = entry(19, 7, EntryType.ENTRY_TYPE_DATA);
        expected.setData(ByteBuffer.wrap(new byte[] {1, 5, 9}));
        ByteBuffer encoded = codec.encode(expected);
        int size = encoded.remaining();
        ByteBuffer input = view(encoded, kind);
        int start = input.position();
        int limit = input.limit();
        assertEquals(size, codec.recordSize(input));
        assertEquals(start, input.position());
        assertEquals(limit, input.limit());
        assertEquals(ByteOrder.LITTLE_ENDIAN, input.order());
        assertEntry(expected, codec.decode(input, size));
        assertEquals(start + size, input.position());
        assertEquals(limit, input.limit());
        assertEquals(ByteOrder.LITTLE_ENDIAN, input.order());
    }
    
    @ParameterizedTest
    @ValueSource(ints = {-1, 1, Integer.MAX_VALUE})
    void rejectsIndexSizeMismatchBeforeCallingPayloadDecoder(int difference) throws Exception {
        LogEntryCodecFactory factory = mock(LogEntryCodecFactory.class);
        LogEntryDecoder decoder = mock(LogEntryDecoder.class);
        when(factory.encoder()).thenReturn(LogEntryV2CodecFactory.getInstance().encoder());
        when(factory.decoder()).thenReturn(decoder);
        LogRecordCodec checked = new LogRecordCodec(factory);
        ByteBuffer record = view(codec.encode(entry(1, 1, EntryType.ENTRY_TYPE_NO_OP)), "heap");
        int start = record.position();
        int expected =
            difference == Integer.MAX_VALUE ? difference : record.remaining() + difference;
        assertThrows(IOException.class, () -> checked.decode(record, expected));
        assertEquals(start, record.position());
        verifyNoInteractions(decoder);
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"header", "payload", "truncated", "headerLimit"})
    void rejectedRecordsPreserveInputState(String failure) throws Exception {
        LogEntry expected = entry(1, 1, EntryType.ENTRY_TYPE_DATA);
        expected.setData(ByteBuffer.wrap(new byte[] {8, 3, 2}));
        ByteBuffer record = view(codec.encode(expected), "directReadonly");
        byte[] bytes = new byte[record.remaining()];
        record.duplicate().get(bytes);
        if ("header".equals(failure)) {
            bytes[8] ^= 1;
        } else if ("payload".equals(failure)) {
            bytes[FileLogFormat.RECORD_HEADER_SIZE] ^= 1;
        }
        ByteBuffer input = view(ByteBuffer.wrap(bytes), "directReadonly");
        if ("truncated".equals(failure)) {
            input.limit(input.limit() - 1);
        } else if ("headerLimit".equals(failure)) {
            input.limit(input.position() + FileLogFormat.RECORD_HEADER_SIZE - 1);
        }
        int start = input.position();
        int limit = input.limit();
        assertThrows(IOException.class, () -> codec.decode(input, bytes.length));
        assertEquals(start, input.position());
        assertEquals(limit, input.limit());
        assertEquals(ByteOrder.LITTLE_ENDIAN, input.order());
    }
    
    @Test
    void advancesByOnlyOneRecordWhenMoreBytesFollow() throws Exception {
        LogEntry expected = entry(5, 2, EntryType.ENTRY_TYPE_NO_OP);
        ByteBuffer record = codec.encode(expected);
        int size = record.remaining();
        ByteBuffer input = ByteBuffer.allocate(size * 2 + 7);
        input.position(7).put(record.duplicate()).put(record.duplicate()).flip().position(7);
        assertEntry(expected, codec.decode(input, size));
        assertEquals(7 + size, input.position());
        assertEntry(expected, codec.decode(input, size));
        assertEquals(input.limit(), input.position());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"throws", "null", "identity"})
    void payloadDecoderFailureDoesNotConsumeRecord(String failure) throws Exception {
        LogEntryCodecFactory factory = mock(LogEntryCodecFactory.class);
        LogEntryDecoder decoder = mock(LogEntryDecoder.class);
        when(factory.encoder()).thenReturn(LogEntryV2CodecFactory.getInstance().encoder());
        when(factory.decoder()).thenReturn(decoder);
        if ("throws".equals(failure)) {
            when(decoder.decode(org.mockito.ArgumentMatchers.any(byte[].class)))
                .thenThrow(new IllegalArgumentException("decoder"));
        } else if ("identity".equals(failure)) {
            when(decoder.decode(org.mockito.ArgumentMatchers.any(byte[].class)))
                .thenReturn(entry(0, 1, EntryType.ENTRY_TYPE_DATA));
        }
        LogRecordCodec checked = new LogRecordCodec(factory);
        ByteBuffer input = view(codec.encode(entry(1, 1, EntryType.ENTRY_TYPE_NO_OP)), "heap");
        int start = input.position();
        int length = input.remaining();
        assertThrows(IOException.class, () -> checked.decode(input, length));
        assertEquals(start, input.position());
    }
    
    private static ByteBuffer view(ByteBuffer encoded, String kind) {
        int size = encoded.remaining();
        ByteBuffer buffer = kind.startsWith("direct")
            ? ByteBuffer.allocateDirect(size + 23) : ByteBuffer.allocate(size + 23);
        buffer.position(12).put(encoded.duplicate());
        buffer.limit(12 + size).position(5);
        if (kind.startsWith("slice")) {
            buffer = buffer.slice();
            buffer.position(7);
        } else {
            buffer.position(12);
        }
        if ("readonly".equals(kind) || kind.endsWith("Readonly")) {
            buffer = buffer.asReadOnlyBuffer();
        }
        return buffer.order(ByteOrder.LITTLE_ENDIAN);
    }
    
    @Test
    void roundTripsAllPersistedEntryTypes() throws Exception {
        LogEntry data = entry(1, 2, EntryType.ENTRY_TYPE_DATA);
        data.setData(ByteBuffer.wrap(new byte[] {1, 2, 3}));
        LogEntry noOp = entry(2, 2, EntryType.ENTRY_TYPE_NO_OP);
        LogEntry configuration = entry(3, 2, EntryType.ENTRY_TYPE_CONFIGURATION);
        configuration.setPeers(List.of(new PeerId("127.0.0.1", 7848)));
        configuration.setOldPeers(List.of(new PeerId("127.0.0.1", 7849)));
        configuration.setLearners(List.of(new PeerId("127.0.0.1", 7850)));
        configuration.setOldLearners(List.of(new PeerId("127.0.0.1", 7851)));
        
        ByteBuffer dataRecord = codec.encode(data);
        assertEntry(data, codec.decode(dataRecord, dataRecord.remaining()));
        ByteBuffer noOpRecord = codec.encode(noOp);
        assertEntry(noOp, codec.decode(noOpRecord, noOpRecord.remaining()));
        ByteBuffer configurationRecord = codec.encode(configuration);
        LogEntry decodedConfiguration =
            codec.decode(configurationRecord, configurationRecord.remaining());
        assertEntry(configuration, decodedConfiguration);
        assertEquals(configuration.getPeers(), decodedConfiguration.getPeers());
        assertEquals(configuration.getOldPeers(), decodedConfiguration.getOldPeers());
        assertEquals(configuration.getLearners(), decodedConfiguration.getLearners());
        assertEquals(configuration.getOldLearners(), decodedConfiguration.getOldLearners());
    }
    
    @Test
    void storesOnlyLengthInTheRecordEnvelope() throws Exception {
        ByteBuffer encoded = codec.encode(entry(9, 4, EntryType.ENTRY_TYPE_NO_OP));
        
        assertEquals(FileLogFormat.RECORD_MAGIC, encoded.getInt(0));
        assertEquals(FileLogFormat.VERSION, encoded.getShort(4));
        assertEquals(FileLogFormat.RECORD_HEADER_SIZE, encoded.getShort(6));
        int payloadLength = encoded.getInt(8);
        assertEquals(FileLogFormat.RECORD_OVERHEAD + payloadLength, encoded.remaining());
        assertEquals(encoded.remaining(), codec.recordSize(encoded));
    }
    
    @Test
    void rejectsHeaderAndPayloadCorruption() throws Exception {
        ByteBuffer encoded = codec.encode(entry(1, 1, EntryType.ENTRY_TYPE_NO_OP));
        byte[] headerCorruption = encoded.array().clone();
        headerCorruption[8] ^= 1;
        assertThrows(IOException.class, () -> codec.recordSize(ByteBuffer.wrap(headerCorruption)));
        
        byte[] payloadCorruption = encoded.array().clone();
        payloadCorruption[FileLogFormat.RECORD_HEADER_SIZE] ^= 1;
        assertThrows(IOException.class,
            () -> codec.decode(ByteBuffer.wrap(payloadCorruption), payloadCorruption.length));
    }
    
    @Test
    void rejectsOversizedHeadersAndEncodedRecords() throws Exception {
        LogRecordCodec bounded = new LogRecordCodec(LogEntryV2CodecFactory.getInstance(), 64);
        ByteBuffer header =
            ByteBuffer.allocate(FileLogFormat.RECORD_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        header.putInt(FileLogFormat.RECORD_MAGIC).putShort(FileLogFormat.VERSION)
            .putShort((short) FileLogFormat.RECORD_HEADER_SIZE).putInt(Integer.MAX_VALUE);
        header.putInt(FileLogFormat.crc32(header.array(), 0, 12)).flip();
        
        assertThrows(IOException.class, () -> bounded.recordSize(header));
        LogEntry large = entry(1, 1, EntryType.ENTRY_TYPE_DATA);
        large.setData(ByteBuffer.wrap(new byte[128]));
        assertThrows(IOException.class, () -> bounded.encode(large));
    }
    
    @ParameterizedTest
    @CsvSource({"-1, false", "41, true", "2147483647, false"})
    void rejectsInvalidLengthsBeforePayloadDecoding(int payloadLength, boolean complete)
        throws Exception {
        LogEntryCodecFactory factory = mock(LogEntryCodecFactory.class);
        LogEntryDecoder decoder = mock(LogEntryDecoder.class);
        when(factory.encoder()).thenReturn(LogEntryV2CodecFactory.getInstance().encoder());
        when(factory.decoder()).thenReturn(decoder);
        LogRecordCodec bounded = new LogRecordCodec(factory, 64);
        // The 41-byte payload is only one byte beyond the total 64-byte record limit.
        // Supply its full envelope and checksum so a missing bound cannot pass as truncation.
        int size = complete ? FileLogFormat.RECORD_OVERHEAD + payloadLength
            : FileLogFormat.RECORD_HEADER_SIZE;
        ByteBuffer record = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        record.putInt(FileLogFormat.RECORD_MAGIC).putShort(FileLogFormat.VERSION)
            .putShort((short) FileLogFormat.RECORD_HEADER_SIZE).putInt(payloadLength);
        record.putInt(FileLogFormat.crc32(record.array(), 0, 12));
        if (complete) {
            byte[] payload = new byte[payloadLength];
            record.put(payload).putLong(FileLogFormat.crc64(payload, 0, payload.length));
        }
        record.flip();
        
        IOException failure =
            assertThrows(IOException.class, () -> bounded.decode(record, record.remaining()));
        
        assertTrue(failure.getMessage().contains("length exceeds bounds"));
        assertEquals(0, record.position());
        verifyNoInteractions(decoder);
    }
    
    @Test
    void decodesExactRecordLimitAndRejectsTruncatedPayload() throws Exception {
        LogEntry expected = entry(1, 1, EntryType.ENTRY_TYPE_DATA);
        expected.setData(ByteBuffer.wrap(new byte[32]));
        ByteBuffer record = codec.encode(expected);
        LogRecordCodec bounded = new LogRecordCodec(LogEntryV2CodecFactory.getInstance(),
            record.remaining());
        
        assertEntry(expected, bounded.decode(record.duplicate(), record.remaining()));
        ByteBuffer truncated = record.duplicate();
        truncated.limit(truncated.limit() - 1);
        IOException failure =
            assertThrows(IOException.class, () -> bounded.decode(truncated, record.remaining()));
        assertTrue(failure.getMessage().contains("Truncated file log record"));
        assertEquals(0, truncated.position());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"missingPeers", "emptyPeers", "orphanOldLearners", "overlap"})
    void rejectsUnrecoverableConfigurationsDuringEncodingAndDecoding(String kind) throws Exception {
        LogEntry configuration = entry(1, 1, EntryType.ENTRY_TYPE_CONFIGURATION);
        PeerId peer = new PeerId("127.0.0.1", 7848);
        configuration.setPeers(List.of(peer));
        switch (kind) {
            case "missingPeers":
                configuration.setPeers(null);
                break;
            case "emptyPeers":
                configuration.setPeers(List.of());
                break;
            case "orphanOldLearners":
                configuration.setOldLearners(List.of(new PeerId("127.0.0.1", 7849)));
                break;
            case "overlap":
                configuration.setLearners(List.of(peer));
                break;
            default:
                throw new AssertionError(kind);
        }
        assertThrows(IOException.class, () -> codec.encode(configuration));
        // Build a checksummed record bypassing the file codec's input validation.
        byte[] payload = LogEntryV2CodecFactory.getInstance().encoder().encode(configuration);
        ByteBuffer record = ByteBuffer.allocate(FileLogFormat.RECORD_OVERHEAD + payload.length)
            .order(ByteOrder.BIG_ENDIAN);
        record.putInt(FileLogFormat.RECORD_MAGIC).putShort(FileLogFormat.VERSION)
            .putShort((short) FileLogFormat.RECORD_HEADER_SIZE).putInt(payload.length);
        record.putInt(FileLogFormat.crc32(record.array(), 0, 12)).put(payload)
            .putLong(FileLogFormat.crc64(payload, 0, payload.length)).flip();
        assertThrows(IOException.class, () -> codec.decode(record, record.remaining()));
    }
    
    private static LogEntry entry(long index, long term, EntryType type) {
        LogEntry result = new LogEntry(type);
        result.setId(new LogId(index, term));
        return result;
    }
    
    private static void assertEntry(LogEntry expected, LogEntry actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getData(), actual.getData());
    }
}
