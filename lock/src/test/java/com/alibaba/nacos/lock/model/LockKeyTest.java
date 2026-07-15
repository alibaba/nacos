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

package com.alibaba.nacos.lock.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Unit tests for LockKey.
 *
 * @author DHX
 * @date 2026/06/01
 */
class LockKeyTest {
    
    @Test
    void testEqualsSameValues() {
        LockKey key1 = new LockKey("type-a", "key-1");
        LockKey key2 = new LockKey("type-a", "key-1");
        assertEquals(key1, key2);
    }
    
    @Test
    void testEqualsSelf() {
        LockKey key = new LockKey("type-a", "key-1");
        assertEquals(key, key);
    }
    
    @Test
    void testEqualsNull() {
        LockKey key = new LockKey("type-a", "key-1");
        assertNotEquals(null, key);
    }
    
    @Test
    void testNotEqualsDifferentType() {
        LockKey key1 = new LockKey("type-a", "key-1");
        LockKey key2 = new LockKey("type-b", "key-1");
        assertNotEquals(key1, key2);
    }
    
    @Test
    void testNotEqualsDifferentKey() {
        LockKey key1 = new LockKey("type-a", "key-1");
        LockKey key2 = new LockKey("type-a", "key-2");
        assertNotEquals(key1, key2);
    }
    
    @Test
    void testHashCodeConsistent() {
        LockKey key1 = new LockKey("type-a", "key-1");
        LockKey key2 = new LockKey("type-a", "key-1");
        assertEquals(key1.hashCode(), key2.hashCode());
    }
    
    @Test
    void testHashCodeDifferent() {
        LockKey key1 = new LockKey("type-a", "key-1");
        LockKey key2 = new LockKey("type-a", "key-2");
        assertNotEquals(key1.hashCode(), key2.hashCode());
    }
    
    @Test
    void testToString() {
        LockKey key = new LockKey("type-a", "key-1");
        assertEquals("type-a:key-1", key.toString());
    }
    
    @Test
    void testGettersAndSetters() {
        LockKey key = new LockKey("type-a", "key-1");
        assertEquals("type-a", key.getLockType());
        assertEquals("key-1", key.getKey());
        
        key.setLockType("type-b");
        key.setKey("key-2");
        assertEquals("type-b", key.getLockType());
        assertEquals("key-2", key.getKey());
    }
}
