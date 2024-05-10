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

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.common.utils.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;

import static com.alibaba.nacos.common.utils.StringUtils.defaultIfEmpty;
import static com.alibaba.nacos.common.utils.StringUtils.isNotBlank;
import static com.alibaba.nacos.common.utils.StringUtils.isNotEmpty;
import static com.alibaba.nacos.common.utils.StringUtils.join;
import static com.alibaba.nacos.common.utils.StringUtils.substringBetween;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringUtilsTest {

    @Test
    void testisNotBlank() {
        assertTrue(isNotBlank("foo"));
        
        assertFalse(isNotBlank(" "));
        assertFalse(isNotBlank(null));
    }

    @Test
    void testIsNotEmpty() {
        assertFalse(isNotEmpty(""));
        
        assertTrue(isNotEmpty("foo"));
    }

    @Test
    void testDefaultIfEmpty() {
        assertEquals("foo", defaultIfEmpty("", "foo"));
        assertEquals("bar", defaultIfEmpty("bar", "foo"));
    }

    @Test
    void testEquals() {
        assertTrue(StringUtils.equals("foo", "foo"));
        
        assertFalse(StringUtils.equals("bar", "foo"));
        assertFalse(StringUtils.equals(" ", "foo"));
        assertFalse(StringUtils.equals("foo", null));
    }

    @Test
    void testSubstringBetween() {
        assertNull(substringBetween(null, null, null));
        assertNull(substringBetween("", "foo", ""));
        assertNull(substringBetween("foo", "bar", "baz"));
        
        assertEquals("", substringBetween("foo", "foo", ""));
    }

    @Test
    void testJoin() {
        assertNull(join(null, ""));
        
        Collection collection = new ArrayList();
        collection.add("foo");
        collection.add("bar");
        assertEquals("foo,bar", join(collection, ","));
    }
}
