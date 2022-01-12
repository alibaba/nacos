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
import org.junit.Assert;
import org.junit.Test;

public class ProtoMessageUtilTest {
    
    @Test
    public void testProto() throws Exception {
        WriteRequest request = WriteRequest.newBuilder()
                .setKey("test-proto-new")
                .build();
        
        byte[] bytes = request.toByteArray();
        Log log = Log.parseFrom(bytes);
        Assert.assertEquals(request.getKey(), log.getKey());
    }
    
    @Test
    public void testConvertToReadRequest() {
        ByteString data = ByteString.copyFrom("data".getBytes());
        String group = "test";
        
        GetRequest getRequest = GetRequest.newBuilder()
                .setGroup(group)
                .setData(data)
                .putExtendInfo("k", "v")
                .build();
        ReadRequest readRequest = ProtoMessageUtil.convertToReadRequest(getRequest);
        
        Assert.assertEquals(group, readRequest.getGroup());
        
        Assert.assertEquals(data, readRequest.getData());
        
        Assert.assertEquals(1, readRequest.getExtendInfoCount());
    }
    
    @Test
    public void testConvertToWriteRequest() {
        ByteString data = ByteString.copyFrom("data".getBytes());
        Log log = Log.newBuilder()
                .setKey("key")
                .setGroup("group")
                .setData(data)
                .setOperation("o")
                .putExtendInfo("k", "v")
                .build();
        WriteRequest writeRequest = ProtoMessageUtil.convertToWriteRequest(log);
        
        Assert.assertEquals(1, writeRequest.getExtendInfoCount());
        
        Assert.assertEquals(data, writeRequest.getData());
        
        Assert.assertEquals("key", writeRequest.getKey());
        
        Assert.assertEquals("group", writeRequest.getGroup());
        
        Assert.assertEquals("o", writeRequest.getOperation());
    }
}