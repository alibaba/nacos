/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.dns;

import org.junit.jupiter.api.Test;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link NacosDnsQueryHandler}.
 *
 * @author Nacos
 */
class NacosDnsQueryHandlerTest {
    
    @Test
    void testHandleQueryNonAQueryType() {
        NacosDnsProperties properties = new NacosDnsProperties();
        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(null, properties);
        
        // Create a MX query
        Name queryName = Name.fromConstantString("test.nacos.");
        Record question = Record.newRecord(queryName, Type.MX, DClass.IN);
        Message query = Message.newQuery(question);
        
        Message response = handler.handleQuery(query);
        assertNotNull(response);
        // Should return NOTIMP for non-A queries
        assertEquals(org.xbill.DNS.Rcode.NOTIMP, response.getHeader().getRcode());
    }
    
    @Test
    void testHandleQueryDomainSuffixMismatch() {
        NacosDnsProperties properties = new NacosDnsProperties();
        properties.setDomainSuffix("nacos");
        NacosDnsQueryHandler handler = new NacosDnsQueryHandler(null, properties);
        
        // Query a domain that doesn't match suffix
        Name queryName = Name.fromConstantString("test.example.com.");
        Record question = Record.newRecord(queryName, Type.A, DClass.IN);
        Message query = Message.newQuery(question);
        
        Message response = handler.handleQuery(query);
        assertNotNull(response);
        // Should return NXDOMAIN
        assertEquals(org.xbill.DNS.Rcode.NXDOMAIN, response.getHeader().getRcode());
    }
    
    @Test
    void testPropertiesDefaultValues() {
        NacosDnsProperties properties = new NacosDnsProperties();
        
        assertEquals(5353, properties.getPort());
        assertEquals("nacos", properties.getDomainSuffix());
        assertEquals("DEFAULT_GROUP", properties.getDefaultGroup());
        assertEquals(60, properties.getTtl());
        assertEquals(false, properties.isEnabled());
    }
}
