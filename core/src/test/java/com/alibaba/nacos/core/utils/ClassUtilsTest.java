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

package com.alibaba.nacos.core.utils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClassUtils} unit tests.
 */
class ClassUtilsTest {
    
    static abstract class GenericSuper<T> {
    }
    
    static class StringChild extends GenericSuper<String> {
    }
    
    interface GenericInterface<T> {
    }
    
    static class StringImpl implements GenericInterface<String> {
    }
    
    @Test
    void testGeneric() {
        Type type = new GenericType<List<String>>() {
        }.getType();
        assertEquals("java.util.List<java.lang.String>", type.getTypeName());
        assertTrue(type instanceof ParameterizedType);
    }
    
    @Test
    void testResolveGenericType() {
        Class<String> resolved = ClassUtils.resolveGenericType(StringChild.class);
        assertEquals(String.class, resolved);
    }
    
    @Test
    void testResolveGenericTypeByInterface() {
        Class<String> resolved = ClassUtils.resolveGenericTypeByInterface(StringImpl.class);
        assertEquals(String.class, resolved);
    }
    
    @Test
    void testFindClassByName() {
        Class clazz = ClassUtils.findClassByName("java.lang.Integer");
        assertEquals("java.lang.Integer", clazz.getName());
    }
    
    @Test
    void testFindClassByNameNotFound() {
        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> ClassUtils.findClassByName("not.exist.ClassXYZ"));
        assertEquals("this class name not found", ex.getMessage());
    }
    
    @Test
    void testGetName() {
        final String name = "java.lang.Integer";
        Integer val = 1;
        assertEquals(name, ClassUtils.getName(val));
        assertEquals(name, ClassUtils.getName(Integer.class));
        
        assertEquals(name, ClassUtils.getCanonicalName(val));
        assertEquals(name, ClassUtils.getCanonicalName(Integer.class));
        
        assertEquals("Integer", ClassUtils.getSimplaName(val));
        assertEquals("Integer", ClassUtils.getSimplaName(Integer.class));
    }
    
    @Test
    void testGetNameRequiresNonnull() {
        assertThrows(NullPointerException.class, () -> ClassUtils.getName((Object) null));
        assertThrows(NullPointerException.class, () -> ClassUtils.getName((Class) null));
        assertThrows(NullPointerException.class, () -> ClassUtils.getCanonicalName((Object) null));
        assertThrows(NullPointerException.class, () -> ClassUtils.getCanonicalName((Class) null));
        assertThrows(NullPointerException.class, () -> ClassUtils.getSimplaName((Object) null));
        assertThrows(NullPointerException.class, () -> ClassUtils.getSimplaName((Class) null));
    }
}
