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

package com.alibaba.nacos.client.logging.logback;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NacosClientLogbackPropertiesTest {
    
    NacosClientLogbackProperties nacosClientLogbackProperties;
    
    @Before
    public void setUp() throws Exception {
        System.setProperty("nacos.logging.logback.test", "test");
        nacosClientLogbackProperties = new NacosClientLogbackProperties();
    }
    
    @After
    public void tearDown() throws Exception {
        System.clearProperty("nacos.logging.logback.test");
    }
    
    @Test
    public void testGetValue() {
        assertEquals("test", nacosClientLogbackProperties.getValue("nacos.logging.logback.test", ""));
        assertEquals("", nacosClientLogbackProperties.getValue("nacos.logging.logback.non.exist", ""));
    }
}