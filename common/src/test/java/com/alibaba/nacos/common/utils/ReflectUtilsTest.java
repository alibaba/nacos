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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;

/**
 * ReflectUtils unit test.
 *
 * @author karsonto
 * @date 2022/08/19
 */
public class ReflectUtilsTest {
    
    List<String> listStr;
    
    @Before
    public void before() {
        listStr = new ArrayList<>(2);
    }
    
    @Test
    public void testGetFieldValue() {
        Object elementData = ReflectUtils.getFieldValue(listStr, "elementData");
        Assert.assertTrue(elementData instanceof Object[]);
        Assert.assertEquals(2, ((Object[]) elementData).length);
    }
    
    @Test(expected = RuntimeException.class)
    public void testGetFieldValueWithoutField() {
        ReflectUtils.getFieldValue(listStr, "elementDataxx");
    }
    
    @Test
    public void testGetFieldValueWithDefault() {
        Object elementData = ReflectUtils.getFieldValue(listStr, "elementDataxx", 3);
        Assert.assertEquals(elementData, 3);
        elementData = ReflectUtils.getFieldValue(listStr, "elementData", 3);
        Assert.assertTrue(elementData instanceof Object[]);
        Assert.assertEquals(2, ((Object[]) elementData).length);
    }
    
    @Test
    public void testGetField() throws NoSuchFieldException {
        Field field = listStr.getClass().getDeclaredField("elementData");
        field.setAccessible(true);
        Object elementData = ReflectUtils.getField(field, listStr);
        Assert.assertTrue(elementData instanceof Object[]);
    }
    
    @Test(expected = IllegalStateException.class)
    public void testGetFieldWithoutAccess() throws NoSuchFieldException {
        Field field = listStr.getClass().getDeclaredField("elementData");
        ReflectUtils.getField(field, listStr);
    }
    
    @Test
    public void testInvokeMethod() throws Exception {
        Method method = listStr.getClass().getDeclaredMethod("grow", int.class);
        method.setAccessible(true);
        ReflectUtils.invokeMethod(method, listStr, 4);
        Object elementData = ReflectUtils.getFieldValue(listStr, "elementData");
        Assert.assertEquals(4, ((Object[]) elementData).length);
    }
    
    @Test(expected = IllegalStateException.class)
    public void testInvokeMethodWithoutAccess() throws Exception {
        Method method = listStr.getClass().getDeclaredMethod("grow", int.class);
        ReflectUtils.invokeMethod(method, listStr, 4);
    }
    
    @Test(expected = UndeclaredThrowableException.class)
    public void testHandleReflectionException() {
        try {
            NoSuchMethodException exception = new NoSuchMethodException("test");
            ReflectUtils.handleReflectionException(exception);
        } catch (Exception e) {
            Assert.assertEquals("Method not found: test", e.getMessage());
        }
        try {
            IllegalAccessException exception = new IllegalAccessException("test");
            ReflectUtils.handleReflectionException(exception);
        } catch (Exception e) {
            Assert.assertEquals("Could not access method or field: test", e.getMessage());
        }
        RuntimeException exception = new RuntimeException("test");
        try {
            ReflectUtils.handleReflectionException(exception);
        } catch (Exception e) {
            Assert.assertEquals(exception, e);
        }
        try {
            InvocationTargetException invocationTargetException = new InvocationTargetException(exception);
            ReflectUtils.handleReflectionException(invocationTargetException);
        } catch (Exception e) {
            Assert.assertEquals(exception, e);
        }
        ReflectUtils.handleReflectionException(new IOException());
    }
    
    @Test(expected = UndeclaredThrowableException.class)
    public void testRethrowRuntimeException() {
        ClassFormatError error = new ClassFormatError("test");
        try {
            ReflectUtils.rethrowRuntimeException(error);
        } catch (Error e) {
            Assert.assertEquals(error, e);
        }
        ReflectUtils.rethrowRuntimeException(new IOException());
    }
}
