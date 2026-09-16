/*
 *  Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.alibaba.nacos.consistency;

import com.alibaba.nacos.consistency.entity.GetRequest;
import com.alibaba.nacos.consistency.entity.Log;
import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.exception.ConsistencyException;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtoMessageUtilTest {
    
    @Test
    void testConstructor() {
        new ProtoMessageUtil();
    }
    
    @Test
    void testParseInvalidBytes() {
        assertThrows(ConsistencyException.class,
            () -> ProtoMessageUtil.parse(new byte[] {1, 2, 3}));
    }
    
    @ParameterizedTest
    @NullAndEmptySource
    void testParseNullOrEmptyBytes(byte[] bytes) {
        assertThrows(ConsistencyException.class, () -> ProtoMessageUtil.parse(bytes));
    }
    
    @Test
    void testParseTruncatedRequestTypePrefix() {
        assertThrows(ConsistencyException.class,
            () -> ProtoMessageUtil.parse(new byte[] {ProtoMessageUtil.REQUEST_TYPE_FIELD_TAG}));
    }
    
    @ParameterizedTest
    @ValueSource(ints = {0, 3, 127, 255})
    void testParseUnknownRequestType(int requestType) {
        byte[] bytes = withRequestType(requestType,
            WriteRequest.newBuilder().setGroup("test").setOperation("write").build());
        assertThrows(ConsistencyException.class, () -> ProtoMessageUtil.parse(bytes));
    }
    
    @ParameterizedTest
    @ValueSource(ints = {ProtoMessageUtil.REQUEST_TYPE_READ, ProtoMessageUtil.REQUEST_TYPE_WRITE})
    void testParseCorruptRequestPreservesCause(int requestType) {
        byte[] bytes = new byte[] {ProtoMessageUtil.REQUEST_TYPE_FIELD_TAG,
            (byte) requestType, (byte) 0x80};
        ConsistencyException exception =
            assertThrows(ConsistencyException.class, () -> ProtoMessageUtil.parse(bytes));
        assertInstanceOf(InvalidProtocolBufferException.class, exception.getCause());
    }
    
    @Test
    void testParseReadRequestWithRequestTypeField() {
        ReadRequest expected = ReadRequest.newBuilder().setGroup("test")
            .setData(ByteString.copyFromUtf8("data")).putExtendInfo("k", "v").build();
        
        ReadRequest actual = assertInstanceOf(ReadRequest.class,
            ProtoMessageUtil.parse(withRequestType(ProtoMessageUtil.REQUEST_TYPE_READ, expected)));
        assertEquals(expected.getGroup(), actual.getGroup());
        assertEquals(expected.getData(), actual.getData());
        assertEquals(expected.getExtendInfoMap(), actual.getExtendInfoMap());
    }
    
    @Test
    void testParseWriteRequestWithRequestTypeField() {
        WriteRequest expected = WriteRequest.newBuilder().setGroup("test").setKey("key")
            .setData(ByteString.copyFromUtf8("data")).setType("type").setOperation("write")
            .putExtendInfo("k", "v").build();
        
        WriteRequest actual = assertInstanceOf(WriteRequest.class,
            ProtoMessageUtil.parse(withRequestType(ProtoMessageUtil.REQUEST_TYPE_WRITE, expected)));
        assertEquals(expected.getGroup(), actual.getGroup());
        assertEquals(expected.getKey(), actual.getKey());
        assertEquals(expected.getData(), actual.getData());
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getOperation(), actual.getOperation());
        assertEquals(expected.getExtendInfoMap(), actual.getExtendInfoMap());
    }
    
    @Test
    void testParseEmptyReadRequestWithRequestTypeField() {
        assertInstanceOf(ReadRequest.class, ProtoMessageUtil.parse(
            withRequestType(ProtoMessageUtil.REQUEST_TYPE_READ, ReadRequest.getDefaultInstance())));
    }
    
    @Test
    void testParseEmptyWriteRequestWithRequestTypeField() {
        assertInstanceOf(WriteRequest.class, ProtoMessageUtil.parse(
            withRequestType(ProtoMessageUtil.REQUEST_TYPE_WRITE,
                WriteRequest.getDefaultInstance())));
    }
    
    @ParameterizedTest
    @MethodSource("untaggedRequests")
    void testParseUntaggedRequest(Message request) {
        assertThrows(ConsistencyException.class,
            () -> ProtoMessageUtil.parse(request.toByteArray()));
    }
    
    private static Stream<Message> untaggedRequests() {
        return Stream.of(GetRequest.newBuilder().setGroup("test").build(),
            Log.newBuilder().setGroup("test").setOperation("write").build(),
            ReadRequest.newBuilder().setGroup("test").build(),
            WriteRequest.newBuilder().setGroup("test").setOperation("write").build());
    }
    
    private byte[] withRequestType(int requestType, Message request) {
        byte[] body = request.toByteArray();
        return ByteBuffer.allocate(body.length + 2)
            .put((byte) ProtoMessageUtil.REQUEST_TYPE_FIELD_TAG)
            .put((byte) requestType).put(body).array();
    }
}
