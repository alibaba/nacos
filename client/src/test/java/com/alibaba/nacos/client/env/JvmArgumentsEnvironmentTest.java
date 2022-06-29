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
import java.util.Properties;

public class JvmArgumentsEnvironmentTest {
    
    @BeforeClass
    public static void init() throws NoSuchFieldException, IllegalAccessException {
    
        Properties properties = new Properties();
        properties.setProperty("user.home", "home");
        properties.setProperty("user.timeout1", "1000");
        properties.setProperty("user.timeout2", "2000");
        properties.setProperty("nacos.standalone", "true");
        
        inject(properties);
    }
    
    @Test
    public void testGetPropertyWithoutValue() {
        final JvmArgumentsEnvironment instance = JvmArgumentsEnvironment.getInstance();
        final String value = instance.getProperty("user.home.test");
    
        Assert.assertNull(value);
    }
    
    @Test
    public void testGetProperty() {
        final JvmArgumentsEnvironment instance = JvmArgumentsEnvironment.getInstance();
        final String value = instance.getProperty("user.home");
        
        Assert.assertEquals("home", value);
    }
    
    @Test
    public void testGetIntegerWithoutValue() {
        final JvmArgumentsEnvironment instance = JvmArgumentsEnvironment.getInstance();
        final Integer value = instance.getInteger("user.timeout1.test");
        
        Assert.assertNull(value);
    }
    
    @Test
    public void testGetInteger() {
        final JvmArgumentsEnvironment instance = JvmArgumentsEnvironment.getInstance();
        final Integer value = instance.getInteger("user.timeout1");
        
        Assert.assertEquals(1000, value.intValue());
    }
    
    @Test
    public void getLongWithoutValue() {
        final JvmArgumentsEnvironment instance = JvmArgumentsEnvironment.getInstance();
        final Long value = instance.getLong("user.timeout2.test");
        
        Assert.assertNull(value);
    
    }
    
    @Test
    public void getLong() {
        final JvmArgumentsEnvironment instance = JvmArgumentsEnvironment.getInstance();
        final Long value = instance.getLong("user.timeout2");
        
        Assert.assertEquals(2000, value.longValue());
    }
    
    @Test
    public void getBooleanWithoutValue() {
        final JvmArgumentsEnvironment instance = JvmArgumentsEnvironment.getInstance();
        final Boolean value = instance.getBoolean("nacos.standalone.test");
        
        Assert.assertNull(value);
    }
    
    @Test
    public void getBoolean() {
        final JvmArgumentsEnvironment instance = JvmArgumentsEnvironment.getInstance();
        final Boolean value = instance.getBoolean("nacos.standalone");
        
        Assert.assertTrue(value);
    }
    
    @AfterClass
    public static void teardown() throws NoSuchFieldException, IllegalAccessException {
        final Properties properties = System.getProperties();
        inject(properties);
    }
    
    private static void inject(Object o) throws NoSuchFieldException, IllegalAccessException {
        final Class<JvmArgumentsEnvironment> jvmArgumentsEnvironmentClass = JvmArgumentsEnvironment.class;
        final Field envsField = jvmArgumentsEnvironmentClass.getDeclaredField("envs");
        envsField.setAccessible(true);
        
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(envsField, envsField.getModifiers() & ~Modifier.FINAL);
        
        envsField.set(JvmArgumentsEnvironment.getInstance(), o);
        
    }
    
}
