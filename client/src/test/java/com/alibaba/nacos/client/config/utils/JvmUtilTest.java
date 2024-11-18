/*
 *
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
 *
 */

package com.alibaba.nacos.client.config.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmUtilTest {
    
    Method initMethod;
    
    @BeforeEach
    void setUp() throws NoSuchMethodException {
        initMethod = JvmUtil.class.getDeclaredMethod("init");
        initMethod.setAccessible(true);
    }
    
    @AfterEach
    void tearDown() throws NoSuchFieldException, IllegalAccessException {
        System.clearProperty("isMultiInstance");
        Field field = JvmUtil.class.getDeclaredField("isMultiInstance");
        field.setAccessible(true);
        field.set(JvmUtil.class, false);
    }
    
    @Test
    void testIsMultiInstance() throws InvocationTargetException, IllegalAccessException {
        initMethod.invoke(JvmUtil.class);
        Boolean multiInstance = JvmUtil.isMultiInstance();
        assertFalse(multiInstance);
    }
    
    @Test
    void testIsMultiInstance2() throws InvocationTargetException, IllegalAccessException {
        System.setProperty("isMultiInstance", "true");
        initMethod.invoke(JvmUtil.class);
        Boolean multiInstance = JvmUtil.isMultiInstance();
        assertTrue(multiInstance);
    }
}