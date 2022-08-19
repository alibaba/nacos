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

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Properties;

public class NPropertiesTest {
    
    @BeforeClass
    public static void init() {
        System.setProperty("nacos.env.first", "jvm");
    }
    
    @AfterClass
    public static void teardown() {
        System.clearProperty("nacos.env.first");
    }
    
    @Test
    public void testGetProperty() {
        NProperties.PROTOTYPE.setProperty("nacos.home", "/home/nacos");
        final String value = NProperties.PROTOTYPE.getProperty("nacos.home");
        Assert.assertEquals("/home/nacos", value);
    }
    
    @Test
    public void testGetPropertyMultiLayer() {
       
        NProperties.PROTOTYPE.setProperty("top.layer", "top");
    
        final NProperties layerAEnv = NProperties.PROTOTYPE.derive();
        layerAEnv.setProperty("a.layer", "a");
    
        final NProperties layerBEnv = layerAEnv.derive();
        layerBEnv.setProperty("b.layer", "b");
    
        final NProperties layerCEnv = layerBEnv.derive();
        layerCEnv.setProperty("c.layer", "c");
    
        String value = layerCEnv.getProperty("c.layer");
        Assert.assertEquals("c", value);
        
        value = layerCEnv.getProperty("b.layer");
        Assert.assertEquals("b", value);
        
        value = layerCEnv.getProperty("a.layer");
        Assert.assertEquals("a", value);
        
        value = layerCEnv.getProperty("top.layer");
        Assert.assertEquals("top", value);
    }
    
    @Test
    public void testGetPropertyDefaultValue() {
        final String value = NProperties.PROTOTYPE.getProperty("nacos.home.default", "/home/default_value");
        Assert.assertEquals("/home/default_value", value);
    }
    
    @Test
    public void testGetBoolean() {
        NProperties.PROTOTYPE.setProperty("use.cluster", "true");
        final Boolean value = NProperties.PROTOTYPE.getBoolean("use.cluster");
        Assert.assertTrue(value);
    }
    
    @Test
    public void testGetBooleanDefaultValue() {
        final Boolean value = NProperties.PROTOTYPE.getBoolean("use.cluster.default", false);
        Assert.assertFalse(value);
    }
    
    @Test
    public void testGetInteger() {
        NProperties.PROTOTYPE.setProperty("max.timeout", "200");
        final Integer value = NProperties.PROTOTYPE.getInteger("max.timeout");
        Assert.assertEquals(200, value.intValue());
    }
    
    @Test
    public void testGetIntegerDefaultValue() {
        final Integer value = NProperties.PROTOTYPE.getInteger("max.timeout.default", 400);
        Assert.assertEquals(400, value.intValue());
    }
    
    @Test
    public void testGetLong() {
        NProperties.PROTOTYPE.setProperty("connection.timeout", "200");
        final Long value = NProperties.PROTOTYPE.getLong("connection.timeout");
        Assert.assertEquals(200L, value.longValue());
    }
    
    @Test
    public void testGetLongDefault() {
        final Long value = NProperties.PROTOTYPE.getLong("connection.timeout.default", 400L);
        Assert.assertEquals(400L, value.longValue());
    }
    
    @Test
    public void testGetPropertyDefaultSetting() {
        
        final String value = NProperties.PROTOTYPE.getProperty("nacos.home.default.test");
        Assert.assertEquals("/home/default_setting", value);
    }
    
    @Test
    public void setProperty() {
        NProperties.PROTOTYPE.setProperty("nacos.set.property", "true");
        final String ret = NProperties.PROTOTYPE.getProperty("nacos.set.property");
        Assert.assertEquals("true", ret);
    }
    
    @Test
    public void setPropertyWithScope() {
    
        final NProperties properties = NProperties.PROTOTYPE.derive();
        properties.setProperty("nacos.set.property.scope", "config");
    
        String ret = NProperties.PROTOTYPE.getProperty("nacos.set.property.scope");
        Assert.assertNull(ret);
        
        ret = properties.getProperty("nacos.set.property.scope");
        Assert.assertEquals("config", ret);
    }
    
    @Test
    public void testAddProperties() {
        Properties properties = new Properties();
        properties.setProperty("nacos.add.properties", "true");
    
        NProperties.PROTOTYPE.addProperties(properties);
        
        final String ret = NProperties.PROTOTYPE.getProperty("nacos.add.properties");
        
        Assert.assertEquals("true", ret);
    }
    
    @Test
    public void testAddPropertiesWithScope() {
        
        Properties properties = new Properties();
        properties.setProperty("nacos.add.properties.scope", "config");
    
        final NProperties nProperties = NProperties.PROTOTYPE.derive();
        nProperties.addProperties(properties);
    
        String ret = NProperties.PROTOTYPE.getProperty("nacos.add.properties.scope");
        Assert.assertNull(ret);
        
        ret = nProperties.getProperty("nacos.add.properties.scope");
        Assert.assertEquals("config", ret);
        
    }
    
    @Test
    public void testTestDerive() {
        Properties properties = new Properties();
        properties.setProperty("nacos.derive.properties.scope", "derive");
    
        final NProperties nProperties = NProperties.PROTOTYPE.derive(properties);
    
        final String value = nProperties.getProperty("nacos.derive.properties.scope");
        
        Assert.assertEquals("derive", value);
    
    }
    
    @Test
    public void testContainsKey() {
        NProperties.PROTOTYPE.setProperty("nacos.contains.key", "true");
        
        boolean ret = NProperties.PROTOTYPE.containsKey("nacos.contains.key");
        Assert.assertTrue(ret);
        
        ret = NProperties.PROTOTYPE.containsKey("nacos.contains.key.in.sys");
        Assert.assertFalse(ret);
    }
    
    @Test
    public void testContainsKeyMultiLayers() {
        
        NProperties.PROTOTYPE.setProperty("top.layer", "top");
    
        final NProperties layerAEnv = NProperties.PROTOTYPE.derive();
        layerAEnv.setProperty("a.layer", "a");
    
        final NProperties layerBEnv = layerAEnv.derive();
        layerBEnv.setProperty("b.layer", "b");
    
        final NProperties layerCEnv = layerBEnv.derive();
        layerCEnv.setProperty("c.layer", "c");
    
        boolean exist = layerCEnv.containsKey("c.layer");
        Assert.assertTrue(exist);
        
        exist = layerCEnv.containsKey("b.layer");
        Assert.assertTrue(exist);
        
        exist = layerCEnv.containsKey("a.layer");
        Assert.assertTrue(exist);
        
        exist = layerCEnv.containsKey("top.layer");
        Assert.assertTrue(exist);
    
    }
    
    @Test
    public void testContainsKeyWithScope() {
        NProperties.PROTOTYPE.setProperty("nacos.contains.global.scope", "global");
        final NProperties namingProperties = NProperties.PROTOTYPE.derive();
        namingProperties.setProperty("nacos.contains.naming.scope", "naming");
    
        boolean ret = NProperties.PROTOTYPE.containsKey("nacos.contains.global.scope");
        Assert.assertTrue(ret);
        
        ret = NProperties.PROTOTYPE.containsKey("nacos.contains.naming.scope");
        Assert.assertFalse(ret);
        
        ret = namingProperties.containsKey("nacos.contains.naming.scope");
        Assert.assertTrue(ret);
        
        ret = namingProperties.containsKey("nacos.contains.global.scope");
        Assert.assertTrue(ret);
        
    }
    
    @Test
    public void testAsProperties() {
        NProperties.PROTOTYPE.setProperty("nacos.as.properties", "true");
        final Properties properties = NProperties.PROTOTYPE.asProperties();
        Assert.assertNotNull(properties);
        Assert.assertEquals("true", properties.getProperty("nacos.as.properties"));
    }
    
    @Test
    public void testAsPropertiesWithScope() {
    
        NProperties.PROTOTYPE.setProperty("nacos.as.properties.global.scope", "global");
        NProperties.PROTOTYPE.setProperty("nacos.server.addr.scope", "global");
    
        final NProperties configProperties = NProperties.PROTOTYPE.derive();
        configProperties.setProperty("nacos.server.addr.scope", "config");
    
        final Properties properties = configProperties.asProperties();
        Assert.assertNotNull(properties);
        
        String ret = properties.getProperty("nacos.as.properties.global.scope");
        Assert.assertEquals("global", ret);
        
        ret = properties.getProperty("nacos.server.addr.scope");
        Assert.assertEquals("config", ret);
    }
    
    @Test
    public void testGetPropertyWithScope() {
    
        NProperties.PROTOTYPE.setProperty("nacos.global.scope", "global");
        
        final NProperties configProperties = NProperties.PROTOTYPE.derive();
        configProperties.setProperty("nacos.config.scope", "config");
        
        final NProperties namingProperties = NProperties.PROTOTYPE.derive();
        namingProperties.setProperty("nacos.naming.scope", "naming");
    
        String ret = NProperties.PROTOTYPE.getProperty("nacos.global.scope");
        Assert.assertEquals("global", ret);
        
        ret = NProperties.PROTOTYPE.getProperty("nacos.config.scope");
        Assert.assertNull(ret);
        
        ret = NProperties.PROTOTYPE.getProperty("nacos.naming.scope");
        Assert.assertNull(ret);
        
        ret = configProperties.getProperty("nacos.config.scope");
        Assert.assertEquals("config", ret);
        ret = configProperties.getProperty("nacos.global.scope");
        Assert.assertEquals("global", ret);
        ret = configProperties.getProperty("nacos.naming.scope");
        Assert.assertNull(ret);
        
        ret = namingProperties.getProperty("nacos.naming.scope");
        Assert.assertEquals("naming", ret);
        ret = namingProperties.getProperty("nacos.global.scope");
        Assert.assertEquals("global", ret);
        ret = namingProperties.getProperty("nacos.config.scope");
        Assert.assertNull(ret);
        
    }
    
}
