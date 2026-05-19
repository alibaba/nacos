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
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigKeyTest {
    
    @Test
    void testSettersAndGetters() {
        ConfigKey key = new ConfigKey();
        key.setAppName("app");
        key.setDataId("dataId");
        key.setGroup("group");
        assertEquals("app", key.getAppName());
        assertEquals("dataId", key.getDataId());
        assertEquals("group", key.getGroup());
    }
    
    @Test
    void testDefaults() {
        ConfigKey key = new ConfigKey();
        assertNull(key.getAppName());
        assertNull(key.getDataId());
        assertNull(key.getGroup());
    }
    
    @Test
    void testEqualsSame() {
        ConfigKey a = new ConfigKey();
        a.setDataId("d");
        a.setGroup("g");
        a.setAppName("a");
        ConfigKey b = new ConfigKey();
        b.setDataId("d");
        b.setGroup("g");
        b.setAppName("a");
        assertEquals(a, b);
    }
    
    @Test
    void testEqualsSelf() {
        ConfigKey a = new ConfigKey();
        assertEquals(a, a);
    }
    
    @Test
    void testNotEqualsNull() {
        assertFalse(new ConfigKey().equals(null));
    }
    
    @Test
    void testNotEqualsDifferentClass() {
        assertFalse(new ConfigKey().equals("str"));
    }
    
    @Test
    void testNotEqualsDifferent() {
        ConfigKey a = new ConfigKey();
        a.setDataId("d1");
        ConfigKey b = new ConfigKey();
        b.setDataId("d2");
        assertNotEquals(a, b);
    }
    
    @Test
    void testHashCode() {
        ConfigKey a = new ConfigKey();
        a.setDataId("d");
        ConfigKey b = new ConfigKey();
        b.setDataId("d");
        assertEquals(a.hashCode(), b.hashCode());
    }
}
