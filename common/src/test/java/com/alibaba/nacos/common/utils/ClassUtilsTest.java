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
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;

public class ClassUtilsTest {
    
    @Test
    public void testFindClassByName1() {
        Class<?> clazz = ClassUtils.findClassByName("java.lang.Integer");
        Assert.assertEquals("java.lang.Integer", clazz.getName());
    }
    
    @Test(expected = NacosRuntimeException.class)
    public void testFindClassByName2() {
        ClassUtils.findClassByName("not.exist.Class");
    }
    
    @Test
    public void testGetName() {
        final String name = "java.lang.Integer";
        Integer val = 1;
        Assert.assertEquals(name, ClassUtils.getName(val));
        Assert.assertEquals(name, ClassUtils.getName(Integer.class));
        
        Assert.assertEquals(name, ClassUtils.getCanonicalName(val));
        Assert.assertEquals(name, ClassUtils.getCanonicalName(Integer.class));
        
        Assert.assertEquals("Integer", ClassUtils.getSimpleName(val));
        Assert.assertEquals("Integer", ClassUtils.getSimpleName(Integer.class));
    }
    
    @Test
    public void testIsAssignableFrom() {
        Assert.assertTrue(ClassUtils.isAssignableFrom(Object.class, Integer.class));
    }
    
    @Test
    public void testForNameArray() throws ClassNotFoundException {
        Class clazz = ClassUtils.forName("[Lcom.alibaba.nacos.common.utils.ClassUtilsTest;", null);
        Assert.assertEquals("[Lcom.alibaba.nacos.common.utils.ClassUtilsTest;", clazz.getName());
        clazz = ClassUtils.forName("java.lang.String[]", null);
        Assert.assertEquals("[Ljava.lang.String;", clazz.getName());
        clazz = ClassUtils.forName("[[Ljava.lang.String;", null);
        Assert.assertEquals("[[Ljava.lang.String;", clazz.getName());
    }
    
    @Test(expected = ClassNotFoundException.class)
    public void testForNameNonExist() throws ClassNotFoundException {
        ClassUtils.forName("com.alibaba.nacos.common.NonExistClass", null);
    }
    
    @Test
    public void testForNameFromPrimitive() throws NoSuchFieldException, IllegalAccessException, ClassNotFoundException {
        Field field = ClassUtils.class.getDeclaredField("PRIMITIVE_TYPE_NAME_MAP");
        field.setAccessible(true);
        Map<String, Class<?>> map = (Map<String, Class<?>>) field.get(null);
        map.put("Test", ClassUtilsTest.class);
        Assert.assertEquals(ClassUtilsTest.class, ClassUtils.forName("Test", null));
    }
    
    @Test
    public void testGetDefaultClassLoader() {
        ClassLoader cachedClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            Assert.assertNotNull(ClassUtils.getDefaultClassLoader());
        } finally {
            Thread.currentThread().setContextClassLoader(cachedClassLoader);
        }
    }
    
    @Test
    public void testClassPackageAsResourcePath() throws ClassNotFoundException {
        Class noPackageClass = ClassUtils.forName("ClassUtilsTestMockClass", null);
        Assert.assertEquals("", ClassUtils.classPackageAsResourcePath(null));
        Assert.assertEquals("", ClassUtils.classPackageAsResourcePath(noPackageClass));
        Assert.assertEquals("com/alibaba/nacos/common/utils",
                ClassUtils.classPackageAsResourcePath(ClassUtilsTest.class));
    }
    
    @Test
    public void testConvertClassNameAndClassPath() {
        String name = ClassUtilsTest.class.getName();
        Assert.assertEquals("com/alibaba/nacos/common/utils/ClassUtilsTest",
                ClassUtils.convertClassNameToResourcePath(name));
        Assert.assertEquals(name,
                ClassUtils.resourcePathToConvertClassName("com/alibaba/nacos/common/utils/ClassUtilsTest"));
    }
}