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
import java.util.Properties;

public class NacosEnvironmentsTest {
    
    private static final Properties JVM_ARGS = new Properties();
    
    private static final Map<String, String> SYS_ENV = new HashMap<>();
    
    @BeforeClass
    public static void init() throws NoSuchFieldException, IllegalAccessException {
        injectJvmArgs(JVM_ARGS);
        injectSystemEnv(SYS_ENV);
        
        SYS_ENV.put("nacos.home", "home.sys.env");
        SYS_ENV.put("nacos.timeout1", "1000");
        
        JVM_ARGS.put("nacos.home", "home.jvm.args");
        JVM_ARGS.put("nacos.timeout2", "2000");
        
        UserCustomizableEnvironment.getInstance().setProperty("nacos.home", "home.user");
        UserCustomizableEnvironment.getInstance().setProperty("nacos.standalone", "true");
    }
    
    @AfterClass
    public static void teardown() throws NoSuchFieldException, IllegalAccessException {
        injectJvmArgs(System.getProperties());
        injectSystemEnv(System.getenv());
        UserCustomizableEnvironment.getInstance().clean();
    }
    
    private static void injectJvmArgs(Object o) throws NoSuchFieldException, IllegalAccessException {
        
        final Class<JvmArgumentsEnvironment> jvmArgumentsEnvironmentClass = JvmArgumentsEnvironment.class;
        final Field envsField = jvmArgumentsEnvironmentClass.getDeclaredField("envs");
        envsField.setAccessible(true);
        
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(envsField, envsField.getModifiers() & ~Modifier.FINAL);
        
        envsField.set(JvmArgumentsEnvironment.getInstance(), o);
    }
    
    private static void injectSystemEnv(Object o) throws NoSuchFieldException, IllegalAccessException {
        final Class<SystemEnvironment> systemEnvironmentClass = SystemEnvironment.class;
        final Field envsField = systemEnvironmentClass.getDeclaredField("envs");
        envsField.setAccessible(true);
        
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(envsField, envsField.getModifiers() & ~Modifier.FINAL);
        
        envsField.set(SystemEnvironment.getInstance(), o);
    }
    
    @Test
    public void testGetProperty() {
        
        final String value = NacosEnvironments.getProperty("nacos.home");
        Assert.assertEquals("home.user", value);
    }
    
    @Test
    public void testGetPropertyWithDefaultValue() {
        final String value = NacosEnvironments.getProperty("nacos.home.test", "test");
        Assert.assertEquals("test", value);
    }
    
    @Test
    public void testGetBoolean() {
        final Boolean value = NacosEnvironments.getBoolean("nacos.standalone");
        Assert.assertTrue(value);
    }
    
    @Test
    public void testGetBooleanWithDefaultValue() {
        final Boolean value = NacosEnvironments.getBoolean("nacos.standalone.test", Boolean.FALSE);
        Assert.assertFalse(value);
    }
    
    @Test
    public void testGetInteger() {
        final Integer value = NacosEnvironments.getInteger("nacos.timeout1");
        Assert.assertEquals(1000, value.intValue());
    }
    
    @Test
    public void testGetIntegerWithDefaultValue() {
        final Integer value = NacosEnvironments.getInteger("nacos.timeout1.test", 1500);
        Assert.assertEquals(1500, value.intValue());
    }
    
    @Test
    public void testGetLong() {
        final Long value = NacosEnvironments.getLong("nacos.timeout2");
        Assert.assertEquals(2000L, value.longValue());
    }
    
    @Test
    public void testGetLongWithDefaultValue() {
        final Long value = NacosEnvironments.getLong("nacos.timeout2.test", 2500L);
        Assert.assertEquals(2500L, value.longValue());
    }
    
    @Test
    public void testSetProperty() {
        
        NacosEnvironments.setProperty("nacos.set.property", "nacos");
        final String value = NacosEnvironments.getProperty("nacos.set.property");
        Assert.assertEquals("nacos", value);
    }
    
    @Test
    public void testAddProperties() {
        Properties properties = new Properties();
        properties.setProperty("nacos.add.property", "nacos");
        NacosEnvironments.addProperties(properties);
    
        final String value = NacosEnvironments.getProperty("nacos.add.property");
        Assert.assertEquals("nacos", value);
    }
    
}
