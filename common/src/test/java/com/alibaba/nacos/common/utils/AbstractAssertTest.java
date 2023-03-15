/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.utils;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertThrows;

public class AbstractAssertTest {
    
    @Test
    public void testState() {
        assertThrows("error message", IllegalStateException.class, () -> AbstractAssert.state(false, "error message"));
        assertThrows("error", IllegalStateException.class, () -> AbstractAssert.state(false, () -> "error"));
    }
    
    @Test
    public void testIsTrue() {
        assertThrows("error", IllegalArgumentException.class, () -> AbstractAssert.isTrue(false, "error"));
        assertThrows("error", IllegalArgumentException.class, () -> AbstractAssert.isTrue(false, () -> "error"));
    }
    
    @Test
    public void testIsNull() {
        assertThrows("error", IllegalArgumentException.class, () -> AbstractAssert.isNull(new Object(), "error"));
        assertThrows("error", IllegalArgumentException.class, () -> AbstractAssert.isNull(new Object(), () -> "error"));
    }
    
    @Test
    public void testNotNull() {
        assertThrows("error", IllegalArgumentException.class, () -> AbstractAssert.notNull(null, "error"));
        assertThrows("error", IllegalArgumentException.class, () -> AbstractAssert.notNull(null, () -> "error"));
    }
    
    @Test
    public void testHasLength() {
        assertThrows("error", IllegalArgumentException.class, () -> AbstractAssert.hasLength(null, "error"));
        assertThrows("error", IllegalArgumentException.class, () -> AbstractAssert.hasLength(null, () -> "error"));
    }
    
    @Test
    public void testHasText() {
        assertThrows("error", IllegalArgumentException.class, () -> AbstractAssert.hasText(null, "error"));
        assertThrows("error", IllegalArgumentException.class, () -> AbstractAssert.hasText(null, () -> "error"));
    }
    
    @Test
    public void testDoesNotContain() {
        assertThrows(IllegalArgumentException.class, () -> AbstractAssert.doesNotContain("abcd", "bc"));
        assertThrows("error", IllegalArgumentException.class,
                () -> AbstractAssert.doesNotContain("abcd", "bc", () -> "error"));
    }
    
    @Test
    public void testNotEmpty() {
        assertThrows("error", IllegalArgumentException.class,
                () -> AbstractAssert.notEmpty(Collections.EMPTY_MAP, "error"));
        assertThrows("error", IllegalArgumentException.class,
                () -> AbstractAssert.notEmpty(Collections.EMPTY_MAP, () -> "error"));
        
        assertThrows("error", IllegalArgumentException.class, () -> AbstractAssert.notEmpty(new Object[] {}, "error"));
        assertThrows("error", IllegalArgumentException.class,
                () -> AbstractAssert.notEmpty(new Object[] {}, () -> "error"));
        
        assertThrows("error", IllegalArgumentException.class,
                () -> AbstractAssert.notEmpty(Collections.emptyList(), "error"));
        assertThrows("error", IllegalArgumentException.class,
                () -> AbstractAssert.notEmpty(Collections.emptyList(), () -> "error"));
    }
    
    @Test
    public void testNoNullElements() {
        List<String> list = new LinkedList<>();
        list.add(null);
        assertThrows("error", IllegalArgumentException.class, () -> AbstractAssert.noNullElements(list, "error"));
        assertThrows("error", IllegalArgumentException.class, () -> AbstractAssert.noNullElements(list, () -> "error"));
        
        Object[] objects = new Object[1];
        assertThrows("error", IllegalArgumentException.class, () -> AbstractAssert.noNullElements(objects, "error"));
        assertThrows("error", IllegalArgumentException.class,
                () -> AbstractAssert.noNullElements(objects, () -> "error"));
    }
    
    @Test
    public void testIsInstanceOf() {
        assertThrows("", IllegalArgumentException.class, () -> AbstractAssert.isInstanceOf(String.class, new Object()));
        assertThrows("error", IllegalArgumentException.class,
                () -> AbstractAssert.isInstanceOf(String.class, new Object(), "error"));
        assertThrows("error", IllegalArgumentException.class,
                () -> AbstractAssert.isInstanceOf(String.class, new Object(), () -> "error"));
    }
    
    @Test
    public void testIsAssignable() {
        assertThrows("", IllegalArgumentException.class, () -> AbstractAssert.isAssignable(String.class, null));
        assertThrows("error", IllegalArgumentException.class,
                () -> AbstractAssert.isAssignable(String.class, null, "error"));
        assertThrows("error", IllegalArgumentException.class,
                () -> AbstractAssert.isAssignable(String.class, Integer.class, "error"));
        assertThrows("error", IllegalArgumentException.class,
                () -> AbstractAssert.isAssignable(String.class, Integer.class, () -> "error"));
    }
    
}
