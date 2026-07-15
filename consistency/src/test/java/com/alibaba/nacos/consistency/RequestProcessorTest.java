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

package com.alibaba.nacos.consistency;

import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RequestProcessorTest {
    
    private static final String TEST_GROUP = "test-group";
    
    @Test
    void testOnError() {
        ConcreteRequestProcessor processor = new ConcreteRequestProcessor();
        processor.onError(new RuntimeException("test error"));
        // Default empty impl - should not throw
    }
    
    @Test
    void testGroup() {
        ConcreteRequestProcessor processor = new ConcreteRequestProcessor();
        assertEquals(TEST_GROUP, processor.group());
    }
    
    @Test
    void testOnRequest() {
        ConcreteRequestProcessor processor = new ConcreteRequestProcessor();
        Response response = processor.onRequest(ReadRequest.getDefaultInstance());
        assertNotNull(response);
    }
    
    @Test
    void testOnApply() {
        ConcreteRequestProcessor processor = new ConcreteRequestProcessor();
        Response response = processor.onApply(WriteRequest.getDefaultInstance());
        assertNotNull(response);
    }
    
    private static class ConcreteRequestProcessor extends RequestProcessor {
        
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
