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
        
        assertEntry(data, codec.decode(codec.encode(data)));
        assertEntry(noOp, codec.decode(codec.encode(noOp)));
        LogEntry decodedConfiguration = codec.decode(codec.encode(configuration));
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
        assertThrows(IOException.class, () -> codec.decode(ByteBuffer.wrap(payloadCorruption)));
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
        
        IOException failure = assertThrows(IOException.class, () -> bounded.decode(record));
        
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
        
        assertEntry(expected, bounded.decode(record.duplicate()));
        ByteBuffer truncated = record.duplicate();
        truncated.limit(truncated.limit() - 1);
        IOException failure = assertThrows(IOException.class, () -> bounded.decode(truncated));
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
        assertThrows(IOException.class, () -> codec.decode(record));
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
