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

package com.alibaba.nacos.config.server.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigInfoExTest {
    
    @Test
    void testDefaultConstructor() {
        ConfigInfoEx ex = new ConfigInfoEx();
        assertEquals(0, ex.getStatus());
        assertNull(ex.getMessage());
    }
    
    @Test
    void testConstructorWithDataIdGroupContent() {
        ConfigInfoEx ex = new ConfigInfoEx("dataId", "group", "content");
        assertEquals("dataId", ex.getDataId());
        assertEquals("group", ex.getGroup());
        assertEquals("content", ex.getContent());
    }
    
    @Test
    void testConstructorWithStatusAndMessage() {
        ConfigInfoEx ex = new ConfigInfoEx("dataId", "group", "content", 200, "ok");
        assertEquals(200, ex.getStatus());
        assertEquals("ok", ex.getMessage());
    }
    
    @Test
    void testSettersAndGetters() {
        ConfigInfoEx ex = new ConfigInfoEx();
        ex.setStatus(500);
        ex.setMessage("error");
        assertEquals(500, ex.getStatus());
        assertEquals("error", ex.getMessage());
    }
    
    @Test
    void testHashCode() {
        ConfigInfoEx a = new ConfigInfoEx("dataId", "group", "content");
        ConfigInfoEx b = new ConfigInfoEx("dataId", "group", "content");
        assertEquals(a.hashCode(), b.hashCode());
    }
    
    @Test
    void testEquals() {
        ConfigInfoEx a = new ConfigInfoEx("dataId", "group", "content");
        ConfigInfoEx b = new ConfigInfoEx("dataId", "group", "content");
        assertEquals(a, b);
    }
    
    @Test
    void testToString() {
        ConfigInfoEx ex = new ConfigInfoEx("dataId", "group", "content", 200, "ok");
        String str = ex.toString();
        assertTrue(str.contains("200"));
        assertTrue(str.contains("ok"));
        assertTrue(str.contains("dataId"));
    }
}
