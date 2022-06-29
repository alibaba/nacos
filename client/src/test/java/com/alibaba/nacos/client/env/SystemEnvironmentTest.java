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

package com.alibaba.nacos.client.env;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class SystemEnvironmentTest {
    
    @BeforeClass
    public static void init() throws NoSuchFieldException, IllegalAccessException {
        final Map<String, String> env = new HashMap<>(4);
        env.put("user.home", "home");
        env.put("user.timeout1", "1000");
        env.put("user.timeout2", "2000");
        env.put("nacos.standalone", "true");
        
        injectMap(env);
    }
    
    @Test
    public void testGetPropertyWithoutValue() {
        final SystemEnvironment instance = SystemEnvironment.getInstance();
        final String value = instance.getProperty("user.home.test");
        
        Assert.assertNull(value);
    }
    
    @Test
    public void testGetProperty() {
        final SystemEnvironment instance = SystemEnvironment.getInstance();
        final String value = instance.getProperty("user.home");
        
        Assert.assertEquals("home", value);
    }
    
    @Test
    public void testGetIntegerWithoutValue() {
        final SystemEnvironment instance = SystemEnvironment.getInstance();
        final Integer value = instance.getInteger("user.timeout1.test");
        
        Assert.assertNull(value);
    }
    
    @Test
    public void testGetInteger() {
        final SystemEnvironment instance = SystemEnvironment.getInstance();
        final Integer value = instance.getInteger("user.timeout1");
        
        Assert.assertEquals(1000, value.intValue());
    }
    
    @Test
    public void getLongWithoutValue() {
        final SystemEnvironment instance = SystemEnvironment.getInstance();
        final Long value = instance.getLong("user.timeout2.test");
        
        Assert.assertNull(value);
        
    }
    
    @Test
    public void getLong() {
        final SystemEnvironment instance = SystemEnvironment.getInstance();
        final Long value = instance.getLong("user.timeout2");
        
        Assert.assertEquals(2000, value.longValue());
    }
    
    @Test
    public void getBooleanWithoutValue() {
        final SystemEnvironment instance = SystemEnvironment.getInstance();
        final Boolean value = instance.getBoolean("nacos.standalone.test");
        
        Assert.assertNull(value);
    }
    
    @Test
    public void getBoolean() {
        final SystemEnvironment instance = SystemEnvironment.getInstance();
        final Boolean value = instance.getBoolean("nacos.standalone");
        
        Assert.assertTrue(value);
    }
    
    @AfterClass
    public static void teardown() throws NoSuchFieldException, IllegalAccessException {
        final Map<String, String> envs = System.getenv();
        injectMap(envs);
    }
    
    private static void injectMap(Object o) throws NoSuchFieldException, IllegalAccessException {
        final Class<SystemEnvironment> systemEnvironmentClass = SystemEnvironment.class;
        final Field envsField = systemEnvironmentClass.getDeclaredField("envs");
        envsField.setAccessible(true);
        
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(envsField, envsField.getModifiers() & ~Modifier.FINAL);
        
        envsField.set(SystemEnvironment.getInstance(), o);
    
    }
    
}
