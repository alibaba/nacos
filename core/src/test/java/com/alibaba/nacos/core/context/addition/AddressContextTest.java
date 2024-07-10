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

package com.alibaba.nacos.core.context.addition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AddressContextTest {
    
    AddressContext addressContext;
    
    @BeforeEach
    void setUp() {
        addressContext = new AddressContext();
    }
    
    @Test
    void testSetSourceIp() {
        assertNull(addressContext.getSourceIp());
        addressContext.setSourceIp("127.0.0.1");
        assertEquals("127.0.0.1", addressContext.getSourceIp());
    }
    
    @Test
    void testSetSourcePort() {
        assertEquals(0, addressContext.getSourcePort());
        addressContext.setSourcePort(8080);
        assertEquals(8080, addressContext.getSourcePort());
    }
    
    @Test
    void testSetRemoteIp() {
        assertNull(addressContext.getRemoteIp());
        addressContext.setRemoteIp("127.0.0.1");
        assertEquals("127.0.0.1", addressContext.getRemoteIp());
    }
    
    @Test
    void testSetRemotePort() {
        assertEquals(0, addressContext.getRemotePort());
        addressContext.setRemotePort(8080);
        assertEquals(8080, addressContext.getRemotePort());
    }
    
    @Test
    void testSetHost() {
        assertNull(addressContext.getHost());
        addressContext.setHost("127.0.0.1");
        assertEquals("127.0.0.1", addressContext.getHost());
    }
}