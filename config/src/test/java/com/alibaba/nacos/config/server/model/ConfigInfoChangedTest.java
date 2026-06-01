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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigInfoChangedTest {
    
    @Test
    void testDefaultConstructor() {
        ConfigInfoChanged changed = new ConfigInfoChanged();
        assertNull(changed.getDataId());
        assertNull(changed.getGroup());
        assertNull(changed.getTenant());
    }
    
    @Test
    void testParameterizedConstructor() {
        ConfigInfoChanged changed = new ConfigInfoChanged("dataId", "group", "tenant");
        assertEquals("dataId", changed.getDataId());
        assertEquals("group", changed.getGroup());
        assertEquals("tenant", changed.getTenant());
    }
    
    @Test
    void testSettersAndGetters() {
        ConfigInfoChanged changed = new ConfigInfoChanged();
        changed.setDataId("d");
        changed.setGroup("g");
        changed.setTenant("t");
        assertEquals("d", changed.getDataId());
        assertEquals("g", changed.getGroup());
        assertEquals("t", changed.getTenant());
    }
    
    @Test
    void testEqualsSameInstance() {
        ConfigInfoChanged a = new ConfigInfoChanged("d", "g", "t");
        assertEquals(a, a);
    }
    
    @Test
    void testEqualsNull() {
        ConfigInfoChanged a = new ConfigInfoChanged("d", "g", "t");
        assertFalse(a.equals(null));
        assertNotEquals(null, a);
    }
    
    @Test
    void testEqualsDifferentClass() {
        ConfigInfoChanged a = new ConfigInfoChanged("d", "g", "t");
        assertFalse(a.equals("str"));
        assertNotEquals("str", a);
    }
    
    @Test
    void testEqualsSameValues() {
        ConfigInfoChanged a = new ConfigInfoChanged("d", "g", "t");
        ConfigInfoChanged b = new ConfigInfoChanged("d", "g", "t");
        assertEquals(a, b);
    }
    
    @Test
    void testEqualsDataIdNullVsNonNull() {
        ConfigInfoChanged a = new ConfigInfoChanged();
        a.setGroup("g");
        ConfigInfoChanged b = new ConfigInfoChanged("d", "g", "t");
        assertNotEquals(a, b);
    }
    
    @Test
    void testEqualsDataIdDifferent() {
        ConfigInfoChanged a = new ConfigInfoChanged("d1", "g", "t");
        ConfigInfoChanged b = new ConfigInfoChanged("d2", "g", "t");
        assertNotEquals(a, b);
    }
    
    @Test
    void testEqualsGroupNullVsNonNull() {
        ConfigInfoChanged a = new ConfigInfoChanged();
        a.setDataId("d");
        ConfigInfoChanged b = new ConfigInfoChanged("d", "g", "t");
        assertNotEquals(a, b);
    }
    
    @Test
    void testEqualsGroupDifferent() {
        ConfigInfoChanged a = new ConfigInfoChanged("d", "g1", "t");
        ConfigInfoChanged b = new ConfigInfoChanged("d", "g2", "t");
        assertNotEquals(a, b);
    }
    
    @Test
    void testEqualsBothNull() {
        ConfigInfoChanged a = new ConfigInfoChanged();
        ConfigInfoChanged b = new ConfigInfoChanged();
        assertEquals(a, b);
    }
    
    @Test
    void testHashCode() {
        ConfigInfoChanged a = new ConfigInfoChanged("d", "g", "t");
        ConfigInfoChanged b = new ConfigInfoChanged("d", "g", "t");
        assertEquals(a.hashCode(), b.hashCode());
    }
    
    @Test
    void testToString() {
        ConfigInfoChanged changed = new ConfigInfoChanged("d", "g", "t");
        String str = changed.toString();
        assertTrue(str.contains("d"));
        assertTrue(str.contains("g"));
    }
}
