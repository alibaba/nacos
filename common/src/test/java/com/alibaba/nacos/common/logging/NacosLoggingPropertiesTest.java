/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.common.logging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NacosLoggingPropertiesTest {
    
    NacosLoggingProperties loggingProperties;
    
    Properties properties;
    
    @BeforeEach
    void setUp() throws Exception {
        properties = new Properties();
        loggingProperties = new NacosLoggingProperties("classpath:test.xml", properties);
    }
    
    @Test
    void testGetLocationWithDefault() {
        assertEquals("classpath:test.xml", loggingProperties.getLocation());
    }
    
    @Test
    void testGetLocationWithoutDefault() {
        properties.setProperty("nacos.logging.default.config.enabled", "false");
        assertNull(loggingProperties.getLocation());
    }
    
    @Test
    void testGetLocationForSpecified() {
        properties.setProperty("nacos.logging.config", "classpath:specified-test.xml");
        properties.setProperty("nacos.logging.default.config.enabled", "false");
        assertEquals("classpath:specified-test.xml", loggingProperties.getLocation());
    }
    
    @Test
    void testGetLocationForSpecifiedWithDefault() {
        properties.setProperty("nacos.logging.config", "classpath:specified-test.xml");
        assertEquals("classpath:specified-test.xml", loggingProperties.getLocation());
    }
    
    @Test
    void testGetReloadInternal() {
        properties.setProperty("nacos.logging.reload.interval.seconds", "50000");
        assertEquals(50000L, loggingProperties.getReloadInternal());
    }
    
    @Test
    void testGetValue() {
        properties.setProperty("test.key", "test.value");
        assertEquals("test.value", loggingProperties.getValue("test.key", "default.value"));
        properties.clear();
        assertEquals("default.value", loggingProperties.getValue("test.key", "default.value"));
    }
}