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

public class NacosClientPropertiesTest {
    
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
        NacosClientProperties.PROTOTYPE.setProperty("nacos.home", "/home/nacos");
        final String value = NacosClientProperties.PROTOTYPE.getProperty("nacos.home");
        Assert.assertEquals("/home/nacos", value);
    }
    
    @Test
    public void testGetPropertyMultiLayer() {
        
        NacosClientProperties.PROTOTYPE.setProperty("top.layer", "top");
        
        final NacosClientProperties layerAEnv = NacosClientProperties.PROTOTYPE.derive();
        layerAEnv.setProperty("a.layer", "a");
        
        final NacosClientProperties layerBEnv = layerAEnv.derive();
        layerBEnv.setProperty("b.layer", "b");
        
        final NacosClientProperties layerCEnv = layerBEnv.derive();
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
        final String value = NacosClientProperties.PROTOTYPE.getProperty("nacos.home.default", "/home/default_value");
        Assert.assertEquals("/home/default_value", value);
    }
    
    @Test
    public void testGetBoolean() {
        NacosClientProperties.PROTOTYPE.setProperty("use.cluster", "true");
        final Boolean value = NacosClientProperties.PROTOTYPE.getBoolean("use.cluster");
        Assert.assertTrue(value);
    }
    
    @Test
    public void testGetBooleanDefaultValue() {
        final Boolean value = NacosClientProperties.PROTOTYPE.getBoolean("use.cluster.default", false);
        Assert.assertFalse(value);
    }
    
    @Test
    public void testGetInteger() {
        NacosClientProperties.PROTOTYPE.setProperty("max.timeout", "200");
        final Integer value = NacosClientProperties.PROTOTYPE.getInteger("max.timeout");
        Assert.assertEquals(200, value.intValue());
    }
    
    @Test
    public void testGetIntegerDefaultValue() {
        final Integer value = NacosClientProperties.PROTOTYPE.getInteger("max.timeout.default", 400);
        Assert.assertEquals(400, value.intValue());
    }
    
    @Test
    public void testGetLong() {
        NacosClientProperties.PROTOTYPE.setProperty("connection.timeout", "200");
        final Long value = NacosClientProperties.PROTOTYPE.getLong("connection.timeout");
        Assert.assertEquals(200L, value.longValue());
    }
    
    @Test
    public void testGetLongDefault() {
        final Long value = NacosClientProperties.PROTOTYPE.getLong("connection.timeout.default", 400L);
        Assert.assertEquals(400L, value.longValue());
    }
    
    @Test
    public void setProperty() {
        NacosClientProperties.PROTOTYPE.setProperty("nacos.set.property", "true");
        final String ret = NacosClientProperties.PROTOTYPE.getProperty("nacos.set.property");
        Assert.assertEquals("true", ret);
    }
    
    @Test
    public void setPropertyWithScope() {
        
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty("nacos.set.property.scope", "config");
        
        String ret = NacosClientProperties.PROTOTYPE.getProperty("nacos.set.property.scope");
        Assert.assertNull(ret);
        
        ret = properties.getProperty("nacos.set.property.scope");
        Assert.assertEquals("config", ret);
    }
    
    @Test
    public void testAddProperties() {
        Properties properties = new Properties();
        properties.setProperty("nacos.add.properties", "true");
        
        NacosClientProperties.PROTOTYPE.addProperties(properties);
        
        final String ret = NacosClientProperties.PROTOTYPE.getProperty("nacos.add.properties");
        
        Assert.assertEquals("true", ret);
    }
    
    @Test
    public void testAddPropertiesWithScope() {
        
        Properties properties = new Properties();
        properties.setProperty("nacos.add.properties.scope", "config");
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive();
        nacosClientProperties.addProperties(properties);
        
        String ret = NacosClientProperties.PROTOTYPE.getProperty("nacos.add.properties.scope");
        Assert.assertNull(ret);
        
        ret = nacosClientProperties.getProperty("nacos.add.properties.scope");
        Assert.assertEquals("config", ret);
        
    }
    
    @Test
    public void testTestDerive() {
        Properties properties = new Properties();
        properties.setProperty("nacos.derive.properties.scope", "derive");
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        
        final String value = nacosClientProperties.getProperty("nacos.derive.properties.scope");
        
        Assert.assertEquals("derive", value);
        
    }
    
    @Test
    public void testContainsKey() {
        NacosClientProperties.PROTOTYPE.setProperty("nacos.contains.key", "true");
        
        boolean ret = NacosClientProperties.PROTOTYPE.containsKey("nacos.contains.key");
        Assert.assertTrue(ret);
        
        ret = NacosClientProperties.PROTOTYPE.containsKey("nacos.contains.key.in.sys");
        Assert.assertFalse(ret);
    }
    
    @Test
    public void testContainsKeyMultiLayers() {
        
        NacosClientProperties.PROTOTYPE.setProperty("top.layer", "top");
        
        final NacosClientProperties layerAEnv = NacosClientProperties.PROTOTYPE.derive();
        layerAEnv.setProperty("a.layer", "a");
        
        final NacosClientProperties layerBEnv = layerAEnv.derive();
        layerBEnv.setProperty("b.layer", "b");
        
        final NacosClientProperties layerCEnv = layerBEnv.derive();
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
        NacosClientProperties.PROTOTYPE.setProperty("nacos.contains.global.scope", "global");
        final NacosClientProperties namingProperties = NacosClientProperties.PROTOTYPE.derive();
        namingProperties.setProperty("nacos.contains.naming.scope", "naming");
        
        boolean ret = NacosClientProperties.PROTOTYPE.containsKey("nacos.contains.global.scope");
        Assert.assertTrue(ret);
        
        ret = NacosClientProperties.PROTOTYPE.containsKey("nacos.contains.naming.scope");
        Assert.assertFalse(ret);
        
        ret = namingProperties.containsKey("nacos.contains.naming.scope");
        Assert.assertTrue(ret);
        
        ret = namingProperties.containsKey("nacos.contains.global.scope");
        Assert.assertTrue(ret);
        
    }
    
    @Test
    public void testAsProperties() {
        NacosClientProperties.PROTOTYPE.setProperty("nacos.as.properties", "true");
        final Properties properties = NacosClientProperties.PROTOTYPE.asProperties();
        Assert.assertNotNull(properties);
        Assert.assertEquals("true", properties.getProperty("nacos.as.properties"));
    }
    
    @Test
    public void testAsPropertiesWithScope() {
        
        NacosClientProperties.PROTOTYPE.setProperty("nacos.as.properties.global.scope", "global");
        NacosClientProperties.PROTOTYPE.setProperty("nacos.server.addr.scope", "global");
        
        final NacosClientProperties configProperties = NacosClientProperties.PROTOTYPE.derive();
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
        
        NacosClientProperties.PROTOTYPE.setProperty("nacos.global.scope", "global");
        
        final NacosClientProperties configProperties = NacosClientProperties.PROTOTYPE.derive();
        configProperties.setProperty("nacos.config.scope", "config");
        
        final NacosClientProperties namingProperties = NacosClientProperties.PROTOTYPE.derive();
        namingProperties.setProperty("nacos.naming.scope", "naming");
        
        String ret = NacosClientProperties.PROTOTYPE.getProperty("nacos.global.scope");
        Assert.assertEquals("global", ret);
        
        ret = NacosClientProperties.PROTOTYPE.getProperty("nacos.config.scope");
        Assert.assertNull(ret);
        
        ret = NacosClientProperties.PROTOTYPE.getProperty("nacos.naming.scope");
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
    
    @Test
    public void testGetPropertyFrom() {
        System.setProperty("nacos.home.default.test", "/home/jvm_args");
        NacosClientProperties.PROTOTYPE.setProperty("nacos.home.default.test", "/home/properties_args");
        
        Assert.assertEquals(NacosClientProperties.PROTOTYPE.getPropertyFrom(SourceType.JVM, "nacos.home.default.test"),
                "/home/jvm_args");
        Assert.assertEquals(
                NacosClientProperties.PROTOTYPE.getPropertyFrom(SourceType.PROPERTIES, "nacos.home.default.test"),
                "/home/properties_args");
        Assert.assertEquals(NacosClientProperties.PROTOTYPE.getPropertyFrom(null, "nacos.home.default.test"),
                NacosClientProperties.PROTOTYPE.getProperty("nacos.home.default.test"));
    }
    
}
