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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosClientPropertiesTest {
    
    @BeforeAll
    static void init() {
        System.setProperty("nacos.env.first", "jvm");
    }
    
    @AfterAll
    static void teardown() {
        System.clearProperty("nacos.env.first");
    }
    
    @Test
    void testGetProperty() {
        NacosClientProperties.PROTOTYPE.setProperty("nacos.home", "/home/nacos");
        final String value = NacosClientProperties.PROTOTYPE.getProperty("nacos.home");
        assertEquals("/home/nacos", value);
    }
    
    @Test
    void testGetPropertyMultiLayer() {
        
        NacosClientProperties.PROTOTYPE.setProperty("top.layer", "top");
        
        final NacosClientProperties layerAEnv = NacosClientProperties.PROTOTYPE.derive();
        layerAEnv.setProperty("a.layer", "a");
        
        final NacosClientProperties layerBEnv = layerAEnv.derive();
        layerBEnv.setProperty("b.layer", "b");
        
        final NacosClientProperties layerCEnv = layerBEnv.derive();
        layerCEnv.setProperty("c.layer", "c");
        
        String value = layerCEnv.getProperty("c.layer");
        assertEquals("c", value);
        
        value = layerCEnv.getProperty("b.layer");
        assertEquals("b", value);
        
        value = layerCEnv.getProperty("a.layer");
        assertEquals("a", value);
        
        value = layerCEnv.getProperty("top.layer");
        assertEquals("top", value);
    }
    
    @Test
    void testGetPropertyDefaultValue() {
        final String value = NacosClientProperties.PROTOTYPE.getProperty("nacos.home.default", "/home/default_value");
        assertEquals("/home/default_value", value);
    }
    
    @Test
    void testGetBoolean() {
        NacosClientProperties.PROTOTYPE.setProperty("use.cluster", "true");
        final Boolean value = NacosClientProperties.PROTOTYPE.getBoolean("use.cluster");
        assertTrue(value);
    }
    
    @Test
    void testGetBooleanDefaultValue() {
        final Boolean value = NacosClientProperties.PROTOTYPE.getBoolean("use.cluster.default", false);
        assertFalse(value);
    }
    
    @Test
    void testGetInteger() {
        NacosClientProperties.PROTOTYPE.setProperty("max.timeout", "200");
        final Integer value = NacosClientProperties.PROTOTYPE.getInteger("max.timeout");
        assertEquals(200, value.intValue());
    }
    
    @Test
    void testGetIntegerDefaultValue() {
        final Integer value = NacosClientProperties.PROTOTYPE.getInteger("max.timeout.default", 400);
        assertEquals(400, value.intValue());
    }
    
    @Test
    void testGetLong() {
        NacosClientProperties.PROTOTYPE.setProperty("connection.timeout", "200");
        final Long value = NacosClientProperties.PROTOTYPE.getLong("connection.timeout");
        assertEquals(200L, value.longValue());
    }
    
    @Test
    void testGetLongDefault() {
        final Long value = NacosClientProperties.PROTOTYPE.getLong("connection.timeout.default", 400L);
        assertEquals(400L, value.longValue());
    }
    
    @Test
    void setProperty() {
        NacosClientProperties.PROTOTYPE.setProperty("nacos.set.property", "true");
        final String ret = NacosClientProperties.PROTOTYPE.getProperty("nacos.set.property");
        assertEquals("true", ret);
    }
    
    @Test
    void setPropertyWithScope() {
        
        final NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty("nacos.set.property.scope", "config");
        
        String ret = NacosClientProperties.PROTOTYPE.getProperty("nacos.set.property.scope");
        assertNull(ret);
        
        ret = properties.getProperty("nacos.set.property.scope");
        assertEquals("config", ret);
    }
    
    @Test
    void testAddProperties() {
        Properties properties = new Properties();
        properties.setProperty("nacos.add.properties", "true");
        
        NacosClientProperties.PROTOTYPE.addProperties(properties);
        
        final String ret = NacosClientProperties.PROTOTYPE.getProperty("nacos.add.properties");
        
        assertEquals("true", ret);
    }
    
    @Test
    void testAddPropertiesWithScope() {
        
        Properties properties = new Properties();
        properties.setProperty("nacos.add.properties.scope", "config");
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive();
        nacosClientProperties.addProperties(properties);
        
        String ret = NacosClientProperties.PROTOTYPE.getProperty("nacos.add.properties.scope");
        assertNull(ret);
        
        ret = nacosClientProperties.getProperty("nacos.add.properties.scope");
        assertEquals("config", ret);
        
    }
    
    @Test
    void testTestDerive() {
        Properties properties = new Properties();
        properties.setProperty("nacos.derive.properties.scope", "derive");
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        
        final String value = nacosClientProperties.getProperty("nacos.derive.properties.scope");
        
        assertEquals("derive", value);
        
    }
    
    @Test
    void testContainsKey() {
        NacosClientProperties.PROTOTYPE.setProperty("nacos.contains.key", "true");
        
        boolean ret = NacosClientProperties.PROTOTYPE.containsKey("nacos.contains.key");
        assertTrue(ret);
        
        ret = NacosClientProperties.PROTOTYPE.containsKey("nacos.contains.key.in.sys");
        assertFalse(ret);
    }
    
    @Test
    void testContainsKeyMultiLayers() {
        
        NacosClientProperties.PROTOTYPE.setProperty("top.layer", "top");
        
        final NacosClientProperties layerAEnv = NacosClientProperties.PROTOTYPE.derive();
        layerAEnv.setProperty("a.layer", "a");
        
        final NacosClientProperties layerBEnv = layerAEnv.derive();
        layerBEnv.setProperty("b.layer", "b");
        
        final NacosClientProperties layerCEnv = layerBEnv.derive();
        layerCEnv.setProperty("c.layer", "c");
        
        boolean exist = layerCEnv.containsKey("c.layer");
        assertTrue(exist);
        
        exist = layerCEnv.containsKey("b.layer");
        assertTrue(exist);
        
        exist = layerCEnv.containsKey("a.layer");
        assertTrue(exist);
        
        exist = layerCEnv.containsKey("top.layer");
        assertTrue(exist);
        
    }
    
    @Test
    void testContainsKeyWithScope() {
        NacosClientProperties.PROTOTYPE.setProperty("nacos.contains.global.scope", "global");
        final NacosClientProperties namingProperties = NacosClientProperties.PROTOTYPE.derive();
        namingProperties.setProperty("nacos.contains.naming.scope", "naming");
        
        boolean ret = NacosClientProperties.PROTOTYPE.containsKey("nacos.contains.global.scope");
        assertTrue(ret);
        
        ret = NacosClientProperties.PROTOTYPE.containsKey("nacos.contains.naming.scope");
        assertFalse(ret);
        
        ret = namingProperties.containsKey("nacos.contains.naming.scope");
        assertTrue(ret);
        
        ret = namingProperties.containsKey("nacos.contains.global.scope");
        assertTrue(ret);
        
    }
    
    @Test
    void testAsProperties() {
        NacosClientProperties.PROTOTYPE.setProperty("nacos.as.properties", "true");
        final Properties properties = NacosClientProperties.PROTOTYPE.asProperties();
        assertNotNull(properties);
        assertEquals("true", properties.getProperty("nacos.as.properties"));
    }
    
    @Test
    void testAsPropertiesWithScope() {
        
        NacosClientProperties.PROTOTYPE.setProperty("nacos.as.properties.global.scope", "global");
        NacosClientProperties.PROTOTYPE.setProperty("nacos.server.addr.scope", "global");
        
        final NacosClientProperties configProperties = NacosClientProperties.PROTOTYPE.derive();
        configProperties.setProperty("nacos.server.addr.scope", "config");
        
        final Properties properties = configProperties.asProperties();
        assertNotNull(properties);
        
        String ret = properties.getProperty("nacos.as.properties.global.scope");
        assertEquals("global", ret);
        
        ret = properties.getProperty("nacos.server.addr.scope");
        assertEquals("config", ret);
    }
    
    @Test
    void testGetPropertyWithScope() {
        
        NacosClientProperties.PROTOTYPE.setProperty("nacos.global.scope", "global");
        
        final NacosClientProperties configProperties = NacosClientProperties.PROTOTYPE.derive();
        configProperties.setProperty("nacos.config.scope", "config");
        
        final NacosClientProperties namingProperties = NacosClientProperties.PROTOTYPE.derive();
        namingProperties.setProperty("nacos.naming.scope", "naming");
        
        String ret = NacosClientProperties.PROTOTYPE.getProperty("nacos.global.scope");
        assertEquals("global", ret);
        
        ret = NacosClientProperties.PROTOTYPE.getProperty("nacos.config.scope");
        assertNull(ret);
        
        ret = NacosClientProperties.PROTOTYPE.getProperty("nacos.naming.scope");
        assertNull(ret);
        
        ret = configProperties.getProperty("nacos.config.scope");
        assertEquals("config", ret);
        ret = configProperties.getProperty("nacos.global.scope");
        assertEquals("global", ret);
        ret = configProperties.getProperty("nacos.naming.scope");
        assertNull(ret);
        
        ret = namingProperties.getProperty("nacos.naming.scope");
        assertEquals("naming", ret);
        ret = namingProperties.getProperty("nacos.global.scope");
        assertEquals("global", ret);
        ret = namingProperties.getProperty("nacos.config.scope");
        assertNull(ret);
        
    }
    
    @Test
    void testGetPropertyFrom() {
        System.setProperty("nacos.home.default.test", "/home/jvm_args");
        NacosClientProperties.PROTOTYPE.setProperty("nacos.home.default.test", "/home/properties_args");
        
        assertEquals("/home/jvm_args",
                NacosClientProperties.PROTOTYPE.getPropertyFrom(SourceType.JVM, "nacos.home.default.test"));
        assertEquals("/home/properties_args",
                NacosClientProperties.PROTOTYPE.getPropertyFrom(SourceType.PROPERTIES, "nacos.home.default.test"));
        assertEquals(NacosClientProperties.PROTOTYPE.getPropertyFrom(null, "nacos.home.default.test"),
                NacosClientProperties.PROTOTYPE.getProperty("nacos.home.default.test"));
    }
    
}
