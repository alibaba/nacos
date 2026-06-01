/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.consistency.cp;

import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import com.alibaba.nacos.consistency.snapshot.SnapshotOperation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
class RequestProcessor4CPTest {
    
    private static final String TEST_GROUP = "test-cp-group";
    
    @Test
    void testLoadSnapshotOperate() {
        ConcreteRequestProcessor4CP processor = new ConcreteRequestProcessor4CP();
        List<SnapshotOperation> operations = processor.loadSnapshotOperate();
        assertNotNull(operations);
        assertTrue(operations.isEmpty());
    }
    
    @Test
    void testOnError() {
        ConcreteRequestProcessor4CP processor = new ConcreteRequestProcessor4CP();
        processor.onError(new RuntimeException("test error"));
        // Inherited default onError - should not throw
    }
    
    @Test
    void testOnRequest() {
        ConcreteRequestProcessor4CP processor = new ConcreteRequestProcessor4CP();
        Response response = processor.onRequest(ReadRequest.getDefaultInstance());
        assertNotNull(response);
    }
    
    @Test
    void testOnApply() {
        ConcreteRequestProcessor4CP processor = new ConcreteRequestProcessor4CP();
        Response response = processor.onApply(WriteRequest.getDefaultInstance());
        assertNotNull(response);
    }
    
    @Test
    void testGroup() {
        ConcreteRequestProcessor4CP processor = new ConcreteRequestProcessor4CP();
        assertEquals(TEST_GROUP, processor.group());
    }
    
    private static class ConcreteRequestProcessor4CP extends RequestProcessor4CP {
        
        @Override
        public Response onRequest(ReadRequest request) {
            return Response.getDefaultInstance();
        }
        
        @Override
        public Response onApply(WriteRequest log) {
            return Response.getDefaultInstance();
        }
        
        @Override
        public String group() {
            return TEST_GROUP;
        }
    }
}
