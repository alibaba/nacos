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

package com.alibaba.nacos.consistency.ap;

import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("checkstyle:AbbreviationAsWordInName")
class RequestProcessor4APTest {
    
    private static final String TEST_GROUP = "test-ap-group";
    
    @Test
    void testConcreteSubclassWorks() {
        ConcreteRequestProcessor4AP processor = new ConcreteRequestProcessor4AP();
        assertNotNull(processor);
    }
    
    @Test
    void testOnRequest() {
        ConcreteRequestProcessor4AP processor = new ConcreteRequestProcessor4AP();
        Response response = processor.onRequest(ReadRequest.getDefaultInstance());
        assertNotNull(response);
    }
    
    @Test
    void testOnApply() {
        ConcreteRequestProcessor4AP processor = new ConcreteRequestProcessor4AP();
        Response response = processor.onApply(WriteRequest.getDefaultInstance());
        assertNotNull(response);
    }
    
    @Test
    void testGroup() {
        ConcreteRequestProcessor4AP processor = new ConcreteRequestProcessor4AP();
        assertEquals(TEST_GROUP, processor.group());
    }
    
    private static class ConcreteRequestProcessor4AP extends RequestProcessor4AP {
        
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
