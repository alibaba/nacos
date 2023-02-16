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
import com.google.protobuf.ByteString;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;

public class ProtoMessageUtilTest {
    
    @Test
    public void testProto() throws Exception {
        WriteRequest request = WriteRequest.newBuilder().setKey("test-proto-new").build();
        
        byte[] bytes = request.toByteArray();
        Log log = Log.parseFrom(bytes);
        assertEquals(request.getKey(), log.getKey());
    }
    
    @Test
    public void testParseReadRequestWithRequestTypeField() {
        String group = "test";
        ByteString data = ByteString.copyFrom("data".getBytes());
        ReadRequest testCase = ReadRequest.newBuilder().setGroup(group).setData(data).build();
        
        byte[] requestTypeFieldBytes = new byte[2];
        requestTypeFieldBytes[0] = ProtoMessageUtil.REQUEST_TYPE_FIELD_TAG;
        requestTypeFieldBytes[1] = ProtoMessageUtil.REQUEST_TYPE_READ;
        
        byte[] dataBytes = testCase.toByteArray();
        ByteBuffer byteBuffer = (ByteBuffer) ByteBuffer.allocate(requestTypeFieldBytes.length + dataBytes.length)
                .put(requestTypeFieldBytes).put(dataBytes).position(0);
        
        Object actual = ProtoMessageUtil.parse(byteBuffer.array());
        assertEquals(ReadRequest.class, testCase.getClass());
        assertEquals(group, ((ReadRequest) actual).getGroup());
        assertEquals(data, ((ReadRequest) actual).getData());
    }
    
    @Test
    public void testParseWriteRequestWithRequestTypeField() {
        String group = "test";
        ByteString data = ByteString.copyFrom("data".getBytes());
        WriteRequest testCase = WriteRequest.newBuilder().setGroup(group).setData(data).build();
        
        byte[] requestTypeFieldBytes = new byte[2];
        requestTypeFieldBytes[0] = ProtoMessageUtil.REQUEST_TYPE_FIELD_TAG;
        requestTypeFieldBytes[1] = ProtoMessageUtil.REQUEST_TYPE_WRITE;
        
        byte[] dataBytes = testCase.toByteArray();
        ByteBuffer byteBuffer = (ByteBuffer) ByteBuffer.allocate(requestTypeFieldBytes.length + dataBytes.length)
                .put(requestTypeFieldBytes).put(dataBytes).position(0);
        
        Object actual = ProtoMessageUtil.parse(byteBuffer.array());
        assertEquals(WriteRequest.class, testCase.getClass());
        assertEquals(group, ((WriteRequest) actual).getGroup());
        assertEquals(data, ((WriteRequest) actual).getData());
    }
    
    @Test
    public void testParseReadRequest() {
        String group = "test";
        ByteString data = ByteString.copyFrom("data".getBytes());
        ReadRequest testCase = ReadRequest.newBuilder().setGroup(group).setData(data).build();
        Object actual = ProtoMessageUtil.parse(testCase.toByteArray());
        assertEquals(ReadRequest.class, testCase.getClass());
        assertEquals(group, ((ReadRequest) actual).getGroup());
        assertEquals(data, ((ReadRequest) actual).getData());
    }
    
    @Test
    public void testParseWriteRequest() {
        String group = "test";
        ByteString data = ByteString.copyFrom("data".getBytes());
        WriteRequest testCase = WriteRequest.newBuilder().setGroup(group).setData(data).build();
        Object actual = ProtoMessageUtil.parse(testCase.toByteArray());
        assertEquals(WriteRequest.class, testCase.getClass());
        assertEquals(group, ((WriteRequest) actual).getGroup());
        assertEquals(data, ((WriteRequest) actual).getData());
    }
    
    @Test
    public void testConvertToReadRequest() {
        ByteString data = ByteString.copyFrom("data".getBytes());
        String group = "test";
        
        GetRequest getRequest = GetRequest.newBuilder().setGroup(group).setData(data).putExtendInfo("k", "v").build();
        ReadRequest readRequest = ProtoMessageUtil.convertToReadRequest(getRequest);
        
        assertEquals(group, readRequest.getGroup());
        
        assertEquals(data, readRequest.getData());
        
        assertEquals(1, readRequest.getExtendInfoCount());
    }
    
    @Test
    public void testConvertToWriteRequest() {
        ByteString data = ByteString.copyFrom("data".getBytes());
        Log log = Log.newBuilder().setKey("key").setGroup("group").setData(data).setOperation("o")
                .putExtendInfo("k", "v").build();
        WriteRequest writeRequest = ProtoMessageUtil.convertToWriteRequest(log);
        
        assertEquals(1, writeRequest.getExtendInfoCount());
        
        assertEquals(data, writeRequest.getData());
        
        assertEquals("key", writeRequest.getKey());
        
        assertEquals("group", writeRequest.getGroup());
        
        assertEquals("o", writeRequest.getOperation());
    }
}
