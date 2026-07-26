/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.core.v2.client;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClientAttributesTest {
    
    @Test
    void testAccessorsAndDefaultValue() {
        ClientAttributes attributes = new ClientAttributes();
        Map<String, Object> values = new HashMap<>();
        values.put("version", "1.0.0");
        attributes.setClientAttributes(values);
        attributes.addClientAttribute("app", "nacos");
        
        assertSame(values, attributes.getClientAttributes());
        assertEquals("1.0.0", attributes.getClientAttribute("version"));
        assertEquals("nacos", attributes.getClientAttribute("app", "default"));
        assertEquals("default", attributes.getClientAttribute("missing", "default"));
    }
    
    @Test
    void testGetClientAttributeReturnsNullWhenMapUnavailable() {
        ClientAttributes attributes = new ClientAttributes();
        attributes.setClientAttributes(null);
        
        assertNull(attributes.getClientAttribute("missing"));
    }
    
    @Test
    void testSetClientAttributesSupportsImmutableMap() {
        ClientAttributes attributes = new ClientAttributes();
        attributes.setClientAttributes(Collections.singletonMap("key", "value"));
        
        assertEquals("value", attributes.getClientAttribute("key"));
    }
}
