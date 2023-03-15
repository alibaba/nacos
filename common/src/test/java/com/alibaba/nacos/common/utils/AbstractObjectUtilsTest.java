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

import com.alibaba.nacos.api.exception.NacosException;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Optional;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AbstractObjectUtilsTest {
    
    @Test
    public void testIsCheckedException() {
        assertTrue(AbstractObjectUtils.isCheckedException(new NacosException()));
        assertFalse(AbstractObjectUtils.isCheckedException(new RuntimeException()));
        assertFalse(AbstractObjectUtils.isCheckedException(new Error()));
    }
    
    @Test
    public void testIsCompatibleWithThrowsClause() {
        assertTrue(AbstractObjectUtils.isCompatibleWithThrowsClause(new NacosException(), Exception.class));
        assertFalse(AbstractObjectUtils.isCompatibleWithThrowsClause(new NacosException(), RuntimeException.class));
    }
    
    @Test
    public void testIsArray() {
        assertTrue(AbstractObjectUtils.isArray(new Object[] {}));
        assertFalse(AbstractObjectUtils.isArray(new Object()));
    }
    
    @Test
    public void testIsEmpty() {
        assertTrue(AbstractObjectUtils.isEmpty(null));
        
        assertTrue(AbstractObjectUtils.isEmpty(Optional.empty()));
        assertFalse(AbstractObjectUtils.isEmpty(Optional.of("1")));
        
        assertTrue(AbstractObjectUtils.isEmpty(""));
        assertFalse(AbstractObjectUtils.isEmpty("1"));
        
        assertTrue(AbstractObjectUtils.isEmpty(Collections.EMPTY_LIST));
        assertFalse(AbstractObjectUtils.isEmpty(Arrays.asList(1, 2)));
        
        assertTrue(AbstractObjectUtils.isEmpty(Collections.EMPTY_MAP));
        assertFalse(AbstractObjectUtils.isEmpty(new HashMap<String, String>() {
            {
                put("k", "v");
            }
        }));
        
        assertFalse(AbstractObjectUtils.isEmpty(new Object()));
        
        assertTrue(AbstractObjectUtils.isEmpty(new Object[] {}));
        assertFalse(AbstractObjectUtils.isEmpty(new Object[] {""}));
    }
    
    @Test
    public void testUnwrapOptional() {
        Object o = new Object();
        assertNull(AbstractObjectUtils.unwrapOptional(null));
        assertNull(AbstractObjectUtils.unwrapOptional(Optional.empty()));
        assertEquals(o, AbstractObjectUtils.unwrapOptional(Optional.of(o)));
        assertEquals(o, AbstractObjectUtils.unwrapOptional(o));
    }
    
    @Test
    public void testContainsElement() {
        Object[] objects = new Object[] {1, 2, 3};
        assertTrue(AbstractObjectUtils.containsElement(objects, 1));
        assertFalse(AbstractObjectUtils.containsElement(objects, 4));
        assertFalse(AbstractObjectUtils.containsElement(null, 1));
    }
    
    private enum ETest {
        CASE1,
        CASE2
    }
    
    @Test
    public void testContainsConstant() {
        assertTrue(AbstractObjectUtils.containsConstant(ETest.values(), "case1"));
        assertFalse(AbstractObjectUtils.containsConstant(ETest.values(), "case3"));
        
        assertTrue(AbstractObjectUtils.containsConstant(ETest.values(), "CASE1", true));
        assertTrue(AbstractObjectUtils.containsConstant(ETest.values(), "CASE1", false));
        assertFalse(AbstractObjectUtils.containsConstant(ETest.values(), "case1", true));
    }
    
    @Test
    public void testCaseInsensitiveValueOf() {
        assertEquals(ETest.CASE1, AbstractObjectUtils.caseInsensitiveValueOf(ETest.values(), "case1"));
        assertThrows(IllegalArgumentException.class,
                () -> AbstractObjectUtils.caseInsensitiveValueOf(ETest.values(), "3"));
    }
    
    @Test
    public void testAddObjectToArray() {
        Object[] objects = new Object[0];
        Object[] objects1 = AbstractObjectUtils.addObjectToArray(objects, 1);
        assertEquals(1, objects1.length);
    }
    
    @Test
    public void testToObjectArray() {
        Object[] objects = new Object[0];
        assertArrayEquals(objects, AbstractObjectUtils.toObjectArray(objects));
        assertNotNull(AbstractObjectUtils.toObjectArray(null));
        assertThrows(IllegalArgumentException.class, () -> AbstractObjectUtils.toObjectArray(new Object()));
    }
    
    @Test
    public void testNullSafeEquals() {
        Integer o1 = new Integer(1111);
        Integer o2 = new Integer(1111);
        AbstractObjectUtils.nullSafeEquals(1, 1);
        assertTrue(AbstractObjectUtils.nullSafeEquals(o1, o2));
        assertTrue(AbstractObjectUtils.nullSafeEquals(null, null));
        
        assertFalse(AbstractObjectUtils.nullSafeEquals(o1, null));
        assertFalse(AbstractObjectUtils.nullSafeEquals(null, o2));
        
        assertTrue(AbstractObjectUtils.nullSafeEquals(1111, 1111));
        
        assertTrue(AbstractObjectUtils.nullSafeEquals(new Integer[] {1, 2}, new Integer[] {1, 2}));
        assertFalse(AbstractObjectUtils.nullSafeEquals(new Integer[] {1, 2}, new Integer[] {1, 2, 3}));
    }
    
    @Test
    public void testNullSafeHashCode() {
        Object o = null;
        assertEquals(0, AbstractObjectUtils.nullSafeHashCode(o));
        assertNotEquals(0, AbstractObjectUtils.nullSafeHashCode(new Object[] {}));
    }
    
    @Test
    public void testIdentityToString() {
        assertEquals("", AbstractObjectUtils.identityToString(null));
        
        String str = "1";
        assertEquals(str.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(str)),
                AbstractObjectUtils.identityToString(str));
    }
    
    @Test
    public void testGetIdentityHexString() {
        assertEquals(Integer.toHexString(System.identityHashCode("1")), AbstractObjectUtils.getIdentityHexString("1"));
    }
    
    @Test
    public void testGetDisplayString() {
        assertEquals("", AbstractObjectUtils.getDisplayString(null));
    }
    
    @Test
    public void testNullSafeClassName() {
        assertEquals("null", AbstractObjectUtils.nullSafeClassName(null));
        assertEquals(Integer.class.getName(), AbstractObjectUtils.nullSafeClassName(1));
    }
    
    @Test
    public void testNullSafeToString() {
        Object o = null;
        assertEquals("null", AbstractObjectUtils.nullSafeToString(o));
        assertSame("1", AbstractObjectUtils.nullSafeToString("1"));
        assertEquals("{1, 2}", AbstractObjectUtils.nullSafeToString(new Object[] {1, 2}));
        assertEquals("{true, false}", AbstractObjectUtils.nullSafeToString(new boolean[] {true, false}));
        assertEquals("{1}", AbstractObjectUtils.nullSafeToString(new byte[] {1}));
        assertEquals("{'a'}", AbstractObjectUtils.nullSafeToString(new char[] {'a'}));
        assertEquals("{1.1}", AbstractObjectUtils.nullSafeToString(new double[] {1.1d}));
        assertEquals("{1.1}", AbstractObjectUtils.nullSafeToString(new float[] {1.1f}));
        assertEquals("{11}", AbstractObjectUtils.nullSafeToString(new int[] {11}));
        assertEquals("{11}", AbstractObjectUtils.nullSafeToString(new long[] {11L}));
        assertEquals("{11}", AbstractObjectUtils.nullSafeToString(new short[] {11}));
    }
    
}
