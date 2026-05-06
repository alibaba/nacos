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

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassUtilsTest {
    
    @Test
    void testFindClassByName1() {
        Class<?> clazz = ClassUtils.findClassByName("java.lang.Integer");
        assertEquals("java.lang.Integer", clazz.getName());
    }
    
    @Test
    void testFindClassByName2() {
        assertThrows(NacosRuntimeException.class, () -> {
            ClassUtils.findClassByName("not.exist.Class");
        });
    }
    
    @Test
    void testGetName() {
        final String name = "java.lang.Integer";
        Integer val = 1;
        assertEquals(name, ClassUtils.getName(val));
        assertEquals(name, ClassUtils.getName(Integer.class));
        
        assertEquals(name, ClassUtils.getCanonicalName(val));
        assertEquals(name, ClassUtils.getCanonicalName(Integer.class));
        
        assertEquals("Integer", ClassUtils.getSimpleName(val));
        assertEquals("Integer", ClassUtils.getSimpleName(Integer.class));
    }
    
    @Test
    void testIsAssignableFrom() {
        assertTrue(ClassUtils.isAssignableFrom(Object.class, Integer.class));
    }
    
    @Test
    void testForNameArray() throws ClassNotFoundException {
        Class clazz = ClassUtils.forName("[Lcom.alibaba.nacos.common.utils.ClassUtilsTest;", null);
        assertEquals("[Lcom.alibaba.nacos.common.utils.ClassUtilsTest;", clazz.getName());
        clazz = ClassUtils.forName("java.lang.String[]", null);
        assertEquals("[Ljava.lang.String;", clazz.getName());
        clazz = ClassUtils.forName("[[Ljava.lang.String;", null);
        assertEquals("[[Ljava.lang.String;", clazz.getName());
    }
    
    @Test
    void testForNameNonExist() throws ClassNotFoundException {
        assertThrows(ClassNotFoundException.class, () -> {
            ClassUtils.forName("com.alibaba.nacos.common.NonExistClass", null);
        });
    }
    
    @Test
    void testForNameFromPrimitive() throws NoSuchFieldException, IllegalAccessException, ClassNotFoundException {
        Field field = ClassUtils.class.getDeclaredField("PRIMITIVE_TYPE_NAME_MAP");
        field.setAccessible(true);
        Map<String, Class<?>> map = (Map<String, Class<?>>) field.get(null);
        map.put("Test", ClassUtilsTest.class);
        assertEquals(ClassUtilsTest.class, ClassUtils.forName("Test", null));
    }
    
    @Test
    void testGetDefaultClassLoader() {
        ClassLoader cachedClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            assertNotNull(ClassUtils.getDefaultClassLoader());
        } finally {
            Thread.currentThread().setContextClassLoader(cachedClassLoader);
        }
    }
    
    @Test
    void testClassPackageAsResourcePath() throws ClassNotFoundException {
        Class noPackageClass = ClassUtils.forName("ClassUtilsTestMockClass", null);
        assertEquals("", ClassUtils.classPackageAsResourcePath(null));
        assertEquals("", ClassUtils.classPackageAsResourcePath(noPackageClass));
        assertEquals("com/alibaba/nacos/common/utils", ClassUtils.classPackageAsResourcePath(ClassUtilsTest.class));
    }
    
    @Test
    void testConvertClassNameAndClassPath() {
        String name = ClassUtilsTest.class.getName();
        assertEquals("com/alibaba/nacos/common/utils/ClassUtilsTest", ClassUtils.convertClassNameToResourcePath(name));
        assertEquals(name, ClassUtils.resourcePathToConvertClassName("com/alibaba/nacos/common/utils/ClassUtilsTest"));
    }
}