/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.remote.request;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestTest {
    
    @BeforeEach
    void setUp() throws Exception {
    }
    
    @Test
    void testHeader() {
        MockRequest request = new MockRequest();
        assertTrue(request.getHeaders().isEmpty());
        assertNull(request.getHeader("clientIp"));
        assertEquals("1.1.1.1", request.getHeader("clientIp", "1.1.1.1"));
        
        request.putHeader("clientIp", "2.2.2.2");
        assertEquals(1, request.getHeaders().size());
        assertEquals("2.2.2.2", request.getHeader("clientIp"));
        assertEquals("2.2.2.2", request.getHeader("clientIp", "1.1.1.1"));
        
        request.putAllHeader(Collections.singletonMap("connectionId", "aaa"));
        assertEquals(2, request.getHeaders().size());
        request.putAllHeader(null);
        assertEquals(2, request.getHeaders().size());
        
        request.clearHeaders();
        assertTrue(request.getHeaders().isEmpty());
    }
    
    private static class MockRequest extends Request {
        
        @Override
        public String getModule() {
            return "mock";
        }
    }
}