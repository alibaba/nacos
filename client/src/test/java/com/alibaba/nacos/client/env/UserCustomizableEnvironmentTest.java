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

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class UserCustomizableEnvironmentTest {
    
    @Test
    public void testGetProperty() {
        final UserCustomizableEnvironment instance = UserCustomizableEnvironment.getInstance();
        instance.setProperty("user.home", "home");
    
        final String value = instance.getProperty("user.home");
    
        Assert.assertEquals("home", value);
    }
    
    @Test
    public void testGetLongWithoutValue() {
        final UserCustomizableEnvironment instance = UserCustomizableEnvironment.getInstance();
        final Long value = instance.getLong("user.timeout");
        Assert.assertNull(value);
    }
    
    @Test
    public void testGetLong() {
        final UserCustomizableEnvironment instance = UserCustomizableEnvironment.getInstance();
        instance.setProperty("user.timeout1", "1000");
    
        final Long value = instance.getLong("user.timeout1");
        
        Assert.assertEquals(1000L, value.longValue());
    
        instance.removeProperty("user.timeout1");
    
    }
    
    @Test
    public void getIntegerWithoutValue() {
        final UserCustomizableEnvironment instance = UserCustomizableEnvironment.getInstance();
    
        final Integer integer = instance.getInteger("user.timeout2");
        
        Assert.assertNull(integer);
    }
    
    @Test
    public void getInteger() {
        final UserCustomizableEnvironment instance = UserCustomizableEnvironment.getInstance();
        instance.setProperty("user.timeout2", "2000");
    
        final Integer value = instance.getInteger("user.timeout2");
    
        Assert.assertEquals(2000, value.intValue());
    
        instance.removeProperty("user.timeout2");
    }
    
    @Test
    public void getBooleanWithoutValue() {
    
        final UserCustomizableEnvironment instance = UserCustomizableEnvironment.getInstance();
        final Boolean value = instance.getBoolean("nacos.standalone");
        
        Assert.assertNull(value);
    }
    
    @Test
    public void getBoolean() {
        final UserCustomizableEnvironment instance = UserCustomizableEnvironment.getInstance();
        
        instance.setProperty("nacos.standalone", "true");
        final Boolean value = instance.getBoolean("nacos.standalone");
        
        Assert.assertTrue(value);
        
        instance.removeProperty("nacos.standalone");
    }
    
    @Test
    public void testAddProperties() {
        final UserCustomizableEnvironment instance = UserCustomizableEnvironment.getInstance();
    
        Properties properties = new Properties();
        properties.setProperty("user.home1", "home1");
        properties.setProperty("user.home2", "home2");
        properties.setProperty("user.home3", "home3");
        
        instance.addProperties(properties);
    
        final String value1 = instance.getProperty("user.home1");
        final String value2 = instance.getProperty("user.home2");
        final String value3 = instance.getProperty("user.home3");
        
        Assert.assertEquals("home1", value1);
        Assert.assertEquals("home2", value2);
        Assert.assertEquals("home3", value3);
    }
    
    @Test
    public void testRemoveProperty() {
        final UserCustomizableEnvironment instance = UserCustomizableEnvironment.getInstance();
    
        instance.setProperty("user.home4", "home4");
        
        instance.removeProperty("user.home4");
    
        final String value = instance.getProperty("user.home4");
        
        Assert.assertNull(value);
    
    }
    
    @Test
    public void testClean() {
    
        final UserCustomizableEnvironment instance = UserCustomizableEnvironment.getInstance();
    
        instance.setProperty("user.home5", "home5");
        instance.setProperty("user.home6", "home6");
        
        instance.clean();
    
        final String value1 = instance.getProperty("user.home5");
        final String value2 = instance.getProperty("user.home6");
        
        Assert.assertNull(value1);
        Assert.assertNull(value2);
    }
    
    @After
    public void teardown() {
        final UserCustomizableEnvironment instance = UserCustomizableEnvironment.getInstance();
        instance.clean();
    }
    
}
