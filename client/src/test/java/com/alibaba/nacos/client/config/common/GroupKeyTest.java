/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.config.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GroupKeyTest {
    
    @Test
    void testGetKey() {
        assertEquals("1+foo", GroupKey.getKey("1", "foo"));
        assertEquals("1+foo+bar", GroupKey.getKey("1", "foo", "bar"));
        assertEquals("1+f%2Boo+b%25ar", GroupKey.getKey("1", "f+oo", "b%ar"));
    }
    
    @Test
    void testGetKeyTenant() {
        assertEquals("1+foo+bar", GroupKey.getKeyTenant("1", "foo", "bar"));
    }
    
    @Test
    void testParseKey() {
        assertArrayEquals(new String[] {"a", "f+oo", null}, GroupKey.parseKey("a+f%2Boo"));
        assertArrayEquals(new String[] {"b", "f%oo", null}, GroupKey.parseKey("b+f%25oo"));
        assertArrayEquals(new String[] {"a", "b", "c"}, GroupKey.parseKey("a+b+c"));
    }
    
    @Test
    void testParseKeyIllegalArgumentException1() {
        assertThrows(IllegalArgumentException.class, () -> {
            GroupKey.parseKey("");
        });
    }
    
    @Test
    void testParseKeyIllegalArgumentException2() {
        assertThrows(IllegalArgumentException.class, () -> {
            GroupKey.parseKey("f%oo");
        });
    }
    
    @Test
    void testParseKeyIllegalArgumentException3() {
        assertThrows(IllegalArgumentException.class, () -> {
            GroupKey.parseKey("f+o+o+bar");
        });
    }
    
    @Test
    void testParseKeyIllegalArgumentException4() {
        assertThrows(IllegalArgumentException.class, () -> {
            GroupKey.parseKey("f++bar");
        });
    }
    
    @Test
    void testGetKeyDatIdParam() {
        assertThrows(IllegalArgumentException.class, () -> {
            GroupKey.getKey("", "a");
        });
    }
    
    @Test
    void testGetKeyGroupParam() {
        assertThrows(IllegalArgumentException.class, () -> {
            GroupKey.getKey("a", "");
        });
    }
}
