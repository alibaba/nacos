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
}