/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class NacosEnvsTest {
    
    @Test
    public void testGetProperty() {
        NacosEnvs.setProperty("nacos.home", "/home/nacos");
        final String value = NacosEnvs.getProperty("nacos.home");
        Assert.assertEquals("/home/nacos", value);
    }
    
    @Test
    public void testGetPropertyDefaultValue() {
        final String value = NacosEnvs.getProperty("nacos.home.default", "/home/default_value");
        Assert.assertEquals("/home/default_value", value);
    }
    
    @Test
    public void testGetBoolean() {
        NacosEnvs.setProperty("use.cluster", "true");
        final Boolean value = NacosEnvs.getBoolean("use.cluster");
        Assert.assertTrue(value);
    }
    
    @Test
    public void testGetBooleanDefaultValue() {
        final Boolean value = NacosEnvs.getBoolean("use.cluster.default", false);
        Assert.assertFalse(value);
    }
    
    @Test
    public void testGetInteger() {
        NacosEnvs.setProperty("max.timeout", "200");
        final Integer value = NacosEnvs.getInteger("max.timeout");
        Assert.assertEquals(200, value.intValue());
    }
    
    @Test
    public void testGetIntegerDefaultValue() {
        final Integer value = NacosEnvs.getInteger("max.timeout.default", 400);
        Assert.assertEquals(400, value.intValue());
    }
    
    @Test
    public void testGetLong() {
        NacosEnvs.setProperty("connection.timeout", "200");
        final Long value = NacosEnvs.getLong("connection.timeout");
        Assert.assertEquals(200L, value.longValue());
    }
    
    @Test
    public void testGetLongDefault() {
        final Long value = NacosEnvs.getLong("connection.timeout.default", 400L);
        Assert.assertEquals(400L, value.longValue());
    }
    
    @Test
    public void testGetPropertyDefaultSetting() {
     
        final String value = NacosEnvs.getProperty("nacos.home.default.test");
        Assert.assertEquals("/home/default_setting", value);
    }
    
    @Test
    public void setProperty() {
        NacosEnvs.setProperty("nacos.set.property", "true");
        final String ret = NacosEnvs.getProperty("nacos.set.property");
        Assert.assertEquals("true", ret);
    }
    
    @Test
    public void setPropertyWithScope() {
        Object config = new Object();
        
        NacosEnvs.bind(config).setProperty("nacos.set.property.scope", "config");
    
        String ret = NacosEnvs.getProperty("nacos.set.property.scope");
        Assert.assertNull(ret);
        
        ret = NacosEnvs.bind(config).getProperty("nacos.set.property.scope");
        Assert.assertEquals("config", ret);
    }
    
    @Test
    public void testAddProperties() {
        Properties properties = new Properties();
        properties.setProperty("nacos.add.properties", "true");
        
        NacosEnvs.addProperties(properties);
    
        final String ret = NacosEnvs.getProperty("nacos.add.properties");
        
        Assert.assertEquals("true", ret);
    }
    
    @Test
    public void testAddPropertiesWithScope() {
        Object config = new Object();
        
        Properties properties = new Properties();
        properties.setProperty("nacos.add.properties.scope", "config");
        
        NacosEnvs.bind(config).addProperties(properties);
    
        String ret = NacosEnvs.getProperty("nacos.add.properties.scope");
        Assert.assertNull(ret);
        
        ret = NacosEnvs.bind(config).getProperty("nacos.add.properties.scope");
        Assert.assertEquals("config", ret);
    
    }
    
    @Test
    public void testContainsKey() {
        NacosEnvs.setProperty("nacos.contains.key", "true");
    
        boolean ret = NacosEnvs.containsKey("nacos.contains.key");
        Assert.assertTrue(ret);
    
        ret = NacosEnvs.containsKey("nacos.contains.key.in.sys");
        Assert.assertFalse(ret);
    }
    
    @Test
    public void testContainsKeyWithScope() {
        Object naming = new Object();
        NacosEnvs.setProperty("nacos.contains.global.scope", "global");
        NacosEnvs.bind(naming).setProperty("nacos.contains.naming.scope", "naming");
    
        boolean ret = NacosEnvs.containsKey("nacos.contains.global.scope");
        Assert.assertTrue(ret);
        
        ret = NacosEnvs.containsKey("nacos.contains.naming.scope");
        Assert.assertFalse(ret);
    
        ret = NacosEnvs.bind(naming).containsKey("nacos.contains.naming.scope");
        Assert.assertTrue(ret);
        
        ret = NacosEnvs.bind(naming).containsKey("nacos.contains.global.scope");
        Assert.assertTrue(ret);
        
        Object unknown = new Object();
        ret = NacosEnvs.bind(unknown).containsKey("nacos.contains.global.scope");
        Assert.assertTrue(ret);
    }
    
    @Test
    public void testAsProperties() {
        NacosEnvs.setProperty("nacos.as.properties", "true");
        final Properties properties = NacosEnvs.asProperties();
        Assert.assertNotNull(properties);
        Assert.assertEquals("true", properties.getProperty("nacos.as.properties"));
    }
    
    @Test
    public void testAsPropertiesWithScope() {
        Object config = new Object();
        
        NacosEnvs.setProperty("nacos.as.properties.global.scope", "global");
        NacosEnvs.setProperty("nacos.server.addr.scope", "global");
        
        NacosEnvs.bind(config).setProperty("nacos.server.addr.scope", "config");
    
        final Properties properties = NacosEnvs.bind(config).asProperties();
        Assert.assertNotNull(properties);
    
        String ret = properties.getProperty("nacos.as.properties.global.scope");
        Assert.assertEquals("global", ret);
        
        ret = properties.getProperty("nacos.server.addr.scope");
        Assert.assertEquals("config", ret);
    }
    
    @Test
    public void testGetPropertyWithScope() {
        Object config = new Object();
        Object naming = new Object();
        
        NacosEnvs.setProperty("nacos.global.scope", "global");
        NacosEnvs.bind(config).setProperty("nacos.config.scope", "config");
        NacosEnvs.bind(naming).setProperty("nacos.naming.scope", "naming");
    
        String ret = NacosEnvs.getProperty("nacos.global.scope");
        Assert.assertEquals("global", ret);
        
        ret = NacosEnvs.getProperty("nacos.config.scope");
        Assert.assertNull(ret);
        
        ret = NacosEnvs.getProperty("nacos.naming.scope");
        Assert.assertNull(ret);
        
        ret = NacosEnvs.bind(config).getProperty("nacos.config.scope");
        Assert.assertEquals("config", ret);
        ret = NacosEnvs.bind(config).getProperty("nacos.global.scope");
        Assert.assertEquals("global", ret);
        ret = NacosEnvs.bind(config).getProperty("nacos.naming.scope");
        Assert.assertNull(ret);
        
        ret = NacosEnvs.bind(naming).getProperty("nacos.naming.scope");
        Assert.assertEquals("naming", ret);
        ret = NacosEnvs.bind(naming).getProperty("nacos.global.scope");
        Assert.assertEquals("global", ret);
        ret = NacosEnvs.bind(naming).getProperty("nacos.config.scope");
        Assert.assertNull(ret);
        
        Object unknownScope = new Object();
        ret = NacosEnvs.bind(unknownScope).getProperty("nacos.global.scope");
        Assert.assertEquals("global", ret);
    }
    
}
