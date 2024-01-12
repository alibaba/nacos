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

package com.alibaba.nacos.client.naming.net;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.config.http.ServerHttpAgentTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.URL;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NamingProxyTest {
    
    private Properties properties;
    
    NamingProxy namingProxy;
    
    @Before
    public void setUp() throws Exception {
        properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
    }
    
    @After
    public void tearDown() throws Exception {
        if (null != namingProxy) {
            namingProxy.shutdown();
        }
    }
    
    @Test
    public void testGetAccessKeFromProperties() {
        properties.setProperty(PropertyKeyConst.ACCESS_KEY, "akProperties");
        namingProxy = new NamingProxy("", "", "127.0.0.1:8848", properties);
        assertEquals("akProperties", namingProxy.getAccessKey());
    }
    
    @Test
    public void testGetSecretKeyFromProperties() {
        properties.setProperty(PropertyKeyConst.SECRET_KEY, "skProperties");
        namingProxy = new NamingProxy("", "", "127.0.0.1:8848", properties);
        assertEquals("skProperties", namingProxy.getSecretKey());
    }
    
    @Test
    public void testGetAccessKeFromSpas() {
        URL url = ServerHttpAgentTest.class.getResource("/test_ram_info.properties");
        System.setProperty("spas.identity", url.getPath());
        namingProxy = new NamingProxy("", "", "127.0.0.1:8848", properties);
        assertEquals("akFromSpas", namingProxy.getAccessKey());
    }
    
    @Test
    public void testGetSecretKeyFromSpas() {
        URL url = ServerHttpAgentTest.class.getResource("/test_ram_info.properties");
        System.setProperty("spas.identity", url.getPath());
        namingProxy = new NamingProxy("", "", "127.0.0.1:8848", properties);
        assertEquals("skFromSpas", namingProxy.getSecretKey());
    }
    
    @Test
    public void testGetAccessKeFromPropertiesWithUseRamInfoParsingFalse() {
        properties.setProperty(PropertyKeyConst.ACCESS_KEY, "akProperties");
        properties.setProperty(PropertyKeyConst.IS_USE_RAM_INFO_PARSING, "false");
        namingProxy = new NamingProxy("", "", "127.0.0.1:8848", properties);
        assertEquals("akProperties", namingProxy.getAccessKey());
    }
    
    @Test
    public void testGetSecretKeyFromPropertiesWithUseRamInfoParsingFalse() {
        properties.setProperty(PropertyKeyConst.SECRET_KEY, "skProperties");
        properties.setProperty(PropertyKeyConst.IS_USE_RAM_INFO_PARSING, "false");
        namingProxy = new NamingProxy("", "", "127.0.0.1:8848", properties);
        assertEquals("skProperties", namingProxy.getSecretKey());
    }
    
    @Test
    public void testGetAccessKeFromSpasWithUseRamInfoParsingFalse() {
        properties.setProperty(PropertyKeyConst.IS_USE_RAM_INFO_PARSING, "false");
        URL url = ServerHttpAgentTest.class.getResource("/test_ram_info.properties");
        System.setProperty("spas.identity", url.getPath());
        namingProxy = new NamingProxy("", "", "127.0.0.1:8848", properties);
        assertNull(namingProxy.getAccessKey());
    }
    
    @Test
    public void testGetSecretKeyFromSpasWithUseRamInfoParsingFalse() {
        properties.setProperty(PropertyKeyConst.IS_USE_RAM_INFO_PARSING, "false");
        URL url = ServerHttpAgentTest.class.getResource("/test_ram_info.properties");
        System.setProperty("spas.identity", url.getPath());
        namingProxy = new NamingProxy("", "", "127.0.0.1:8848", properties);
        assertNull(namingProxy.getSecretKey());
    }
}