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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ConfigAdvanceInfoTest {
    
    @Test
    void testSettersAndGetters() {
        ConfigAdvanceInfo info = new ConfigAdvanceInfo();
        info.setCreateTime(100L);
        info.setModifyTime(200L);
        info.setCreateUser("user");
        info.setCreateIp("1.1.1.1");
        info.setDesc("desc");
        info.setUse("use");
        info.setEffect("effect");
        info.setType("type");
        info.setSchema("schema");
        info.setConfigTags("tags");
        
        assertEquals(100L, info.getCreateTime());
        assertEquals(200L, info.getModifyTime());
        assertEquals("user", info.getCreateUser());
        assertEquals("1.1.1.1", info.getCreateIp());
        assertEquals("desc", info.getDesc());
        assertEquals("use", info.getUse());
        assertEquals("effect", info.getEffect());
        assertEquals("type", info.getType());
        assertEquals("schema", info.getSchema());
        assertEquals("tags", info.getConfigTags());
    }
    
    @Test
    void testEquals() {
        ConfigAdvanceInfo a = new ConfigAdvanceInfo();
        a.setCreateUser("u");
        a.setDesc("d");
        ConfigAdvanceInfo b = new ConfigAdvanceInfo();
        b.setCreateUser("u");
        b.setDesc("d");
        assertEquals(a, b);
    }
    
    @Test
    void testEqualsSelf() {
        ConfigAdvanceInfo a = new ConfigAdvanceInfo();
        assertEquals(a, a);
    }
    
    @Test
    void testNotEqualsNull() {
        assertFalse(new ConfigAdvanceInfo().equals(null));
    }
    
    @Test
    void testNotEqualsDifferentClass() {
        assertFalse(new ConfigAdvanceInfo().equals("str"));
    }
    
    @Test
    void testNotEqualsDifferent() {
        ConfigAdvanceInfo a = new ConfigAdvanceInfo();
        a.setDesc("d1");
        ConfigAdvanceInfo b = new ConfigAdvanceInfo();
        b.setDesc("d2");
        assertNotEquals(a, b);
    }
    
    @Test
    void testHashCode() {
        ConfigAdvanceInfo a = new ConfigAdvanceInfo();
        a.setDesc("d");
        ConfigAdvanceInfo b = new ConfigAdvanceInfo();
        b.setDesc("d");
        assertEquals(a.hashCode(), b.hashCode());
    }
}
