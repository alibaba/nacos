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

package com.alibaba.nacos.common.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ReflectUtils unit test.
 *
 * @author karsonto
 * @date 2022/08/19
 */
class ReflectUtilsTest {
    
    List<String> listStr;
    
    @BeforeEach
    void before() {
        listStr = new ArrayList<>(2);
    }
    
    @Test
    void testGetFieldValue() {
        Object elementData = ReflectUtils.getFieldValue(listStr, "elementData");
        assertTrue(elementData instanceof Object[]);
        assertEquals(2, ((Object[]) elementData).length);
    }
    
    @Test
    void testGetFieldValueWithoutField() {
        assertThrows(RuntimeException.class, () -> {
            ReflectUtils.getFieldValue(listStr, "elementDataxx");
        });
    }
    
    @Test
    void testGetFieldValueWithDefault() {
        Object elementData = ReflectUtils.getFieldValue(listStr, "elementDataxx", 3);
        assertEquals(3, elementData);
        elementData = ReflectUtils.getFieldValue(listStr, "elementData", 3);
        assertTrue(elementData instanceof Object[]);
        assertEquals(2, ((Object[]) elementData).length);
    }
    
    @Test
    void testGetField() throws NoSuchFieldException {
        Field field = listStr.getClass().getDeclaredField("elementData");
        field.setAccessible(true);
        Object elementData = ReflectUtils.getField(field, listStr);
        assertTrue(elementData instanceof Object[]);
    }
    
    @Test
    void testGetFieldWithoutAccess() throws NoSuchFieldException {
        assertThrows(IllegalStateException.class, () -> {
            Field field = listStr.getClass().getDeclaredField("elementData");
            ReflectUtils.getField(field, listStr);
        });
    }
    
    @Test
    void testInvokeMethod() throws Exception {
        Method method = listStr.getClass().getDeclaredMethod("grow", int.class);
        method.setAccessible(true);
        ReflectUtils.invokeMethod(method, listStr, 4);
        Object elementData = ReflectUtils.getFieldValue(listStr, "elementData");
        assertEquals(4, ((Object[]) elementData).length);
    }
    
    @Test
    void testInvokeMethodWithoutAccess() throws Exception {
        assertThrows(IllegalStateException.class, () -> {
            Method method = listStr.getClass().getDeclaredMethod("grow", int.class);
            ReflectUtils.invokeMethod(method, listStr, 4);
        });
    }
    
    @Test
    void testHandleReflectionException() {
        assertThrows(UndeclaredThrowableException.class, () -> {
            try {
                NoSuchMethodException exception = new NoSuchMethodException("test");
                ReflectUtils.handleReflectionException(exception);
            } catch (Exception e) {
                assertEquals("Method not found: test", e.getMessage());
            }
            try {
                IllegalAccessException exception = new IllegalAccessException("test");
                ReflectUtils.handleReflectionException(exception);
            } catch (Exception e) {
                assertEquals("Could not access method or field: test", e.getMessage());
            }
            RuntimeException exception = new RuntimeException("test");
            try {
                ReflectUtils.handleReflectionException(exception);
            } catch (Exception e) {
                assertEquals(exception, e);
            }
            try {
                InvocationTargetException invocationTargetException = new InvocationTargetException(exception);
                ReflectUtils.handleReflectionException(invocationTargetException);
            } catch (Exception e) {
                assertEquals(exception, e);
            }
            ReflectUtils.handleReflectionException(new IOException());
        });
    }
    
    @Test
    void testRethrowRuntimeException() {
        assertThrows(UndeclaredThrowableException.class, () -> {
            ClassFormatError error = new ClassFormatError("test");
            try {
                ReflectUtils.rethrowRuntimeException(error);
            } catch (Error e) {
                assertEquals(error, e);
            }
            ReflectUtils.rethrowRuntimeException(new IOException());
        });
    }
}
