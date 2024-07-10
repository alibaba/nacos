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

package com.alibaba.nacos.core.context;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestContextTest {
    
    long requestTimestamp;
    
    RequestContext requestContext;
    
    @BeforeEach
    void setUp() {
        requestTimestamp = System.currentTimeMillis();
        requestContext = new RequestContext(requestTimestamp);
    }
    
    @Test
    public void testGetRequestId() {
        String requestId = requestContext.getRequestId();
        assertNotNull(requestId);
        assertNotNull(UUID.fromString(requestId));
        requestContext.setRequestId("testRequestId");
        assertEquals("testRequestId", requestContext.getRequestId());
    }
    
    @Test
    public void testGetRequestTimestamp() {
        assertEquals(requestTimestamp, requestContext.getRequestTimestamp());
    }
    
    @Test
    public void testSetExtensionContext() {
        assertNull(requestContext.getExtensionContext("testKey"));
        requestContext.addExtensionContext("testKey", "testValue");
        assertEquals("testValue", requestContext.getExtensionContext("testKey"));
    }
}