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

package com.alibaba.nacos.api.utils.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosJsonSubtypeTest {
    
    @Test
    void testCreateSubtype() {
        NacosJsonSubtype subtype = new NacosJsonSubtype(Number.class, Integer.class, "integer");
        
        assertSame(Number.class, subtype.getBaseType());
        assertSame(Integer.class, subtype.getSubtype());
        assertEquals("integer", subtype.getTypeName());
    }
    
    @Test
    void testCreateSubtypeWithNullBaseType() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new NacosJsonSubtype(null, Integer.class, "integer"));
        
        assertEquals("baseType must not be null", exception.getMessage());
    }
    
    @Test
    void testCreateSubtypeWithNullSubtype() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new NacosJsonSubtype(Number.class, null, "integer"));
        
        assertEquals("subtype must not be null", exception.getMessage());
    }
    
    @Test
    void testCreateSubtypeWithNullTypeName() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new NacosJsonSubtype(Number.class, Integer.class, null));
        
        assertEquals("typeName must not be empty", exception.getMessage());
    }
    
    @Test
    void testCreateSubtypeWithEmptyTypeName() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new NacosJsonSubtype(Number.class, Integer.class, ""));
        
        assertEquals("typeName must not be empty", exception.getMessage());
    }
    
    @Test
    void testEqualsAndHashCode() {
        NacosJsonSubtype subtype = new NacosJsonSubtype(Number.class, Integer.class, "integer");
        NacosJsonSubtype sameSubtype = new NacosJsonSubtype(Number.class, Integer.class, "integer");
        
        assertTrue(subtype.equals(subtype));
        assertEquals(subtype, sameSubtype);
        assertEquals(subtype.hashCode(), sameSubtype.hashCode());
        assertFalse(subtype.equals("integer"));
        assertNotEquals(subtype, new NacosJsonSubtype(Object.class, Integer.class, "integer"));
        assertNotEquals(subtype, new NacosJsonSubtype(Number.class, Long.class, "integer"));
        assertNotEquals(subtype, new NacosJsonSubtype(Number.class, Integer.class, "long"));
    }
}
