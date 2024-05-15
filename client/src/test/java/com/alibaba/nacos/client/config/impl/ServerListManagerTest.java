/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.env.NacosClientProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTP_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ServerListManagerTest {
    
    @Test
    void testStart() throws NacosException {
        final ServerListManager mgr = new ServerListManager("localhost", 0);
        try {
            mgr.start();
            fail();
        } catch (NacosException e) {
            assertEquals(
                    "fail to get NACOS-server serverlist! env:custom-localhost_0_nacos_serverlist, not connnect url:http://localhost:0/nacos/serverlist",
                    e.getErrMsg());
        }
        mgr.shutdown();
    }
    
    @Test
    void testGetter() throws NacosException {
        {
            final ServerListManager mgr = new ServerListManager();
            assertEquals("nacos", mgr.getContentPath());
            assertEquals("default", mgr.getName());
            assertEquals("", mgr.getTenant());
            assertEquals("", mgr.getNamespace());
            assertEquals("1.1.1.1-2.2.2.2_8848", mgr.getFixedNameSuffix("http://1.1.1.1", "2.2.2.2:8848"));
        }
        
        {
            Properties properties = new Properties();
            properties.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties.put(PropertyKeyConst.ENDPOINT, "endpoint");
            
            final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
            final ServerListManager mgr2 = new ServerListManager(nacosClientProperties);
            assertEquals("aaa", mgr2.getContentPath());
        }
        
        // Test https
        {
            Properties properties = new Properties();
            properties.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties.put(PropertyKeyConst.SERVER_ADDR, "https://1.1.1.1:8848");
            final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
            final ServerListManager mgr2 = new ServerListManager(nacosClientProperties);
            assertEquals("aaa", mgr2.getContentPath());
            assertEquals("[https://1.1.1.1:8848]", mgr2.getServerUrls().toString());
        }
        
        {
            Properties properties2 = new Properties();
            properties2.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties2.put(PropertyKeyConst.SERVER_ADDR, "1.1.1.1:8848");
            
            final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties2);
            final ServerListManager mgr3 = new ServerListManager(nacosClientProperties);
            assertEquals(1, mgr3.getServerUrls().size());
            assertEquals("http://1.1.1.1:8848", mgr3.getServerUrls().get(0));
            assertEquals("[http://1.1.1.1:8848]", mgr3.getUrlString());
            assertTrue(mgr3.contain("http://1.1.1.1:8848"));
            assertEquals("ServerManager-fixed-1.1.1.1_8848-[http://1.1.1.1:8848]", mgr3.toString());
        }
        
        {
            Properties properties3 = new Properties();
            properties3.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties3.put(PropertyKeyConst.SERVER_ADDR, "1.1.1.1:8848,2.2.2.2:8848");
            
            final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties3);
            final ServerListManager mgr4 = new ServerListManager(nacosClientProperties);
            assertEquals(2, mgr4.getServerUrls().size());
            assertEquals("http://1.1.1.1:8848", mgr4.getServerUrls().get(0));
            assertEquals("http://2.2.2.2:8848", mgr4.getServerUrls().get(1));
            assertTrue(mgr4.contain("http://1.1.1.1:8848"));
            assertEquals("ServerManager-fixed-1.1.1.1_8848-2.2.2.2_8848-[http://1.1.1.1:8848, http://2.2.2.2:8848]",
                    mgr4.toString());
        }
        
        {
            Properties properties4 = new Properties();
            properties4.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties4.put(PropertyKeyConst.SERVER_ADDR, "1.1.1.1:8848;2.2.2.2:8848");
            
            final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties4);
            final ServerListManager mgr5 = new ServerListManager(nacosClientProperties);
            assertEquals(2, mgr5.getServerUrls().size());
            assertEquals("http://1.1.1.1:8848", mgr5.getServerUrls().get(0));
            assertEquals("http://2.2.2.2:8848", mgr5.getServerUrls().get(1));
            assertTrue(mgr5.contain("http://1.1.1.1:8848"));
            assertEquals("ServerManager-fixed-1.1.1.1_8848-2.2.2.2_8848-[http://1.1.1.1:8848, http://2.2.2.2:8848]",
                    mgr5.toString());
        }
        
    }
    
    @Test
    void testIterator() {
        List<String> addrs = new ArrayList<>();
        String addr = "1.1.1.1:8848";
        addrs.add(addr);
        final ServerListManager mgr = new ServerListManager(addrs, "aaa");
        
        // new iterator
        final Iterator<String> it = mgr.iterator();
        assertTrue(it.hasNext());
        assertEquals(addr, it.next());
        
        assertNull(mgr.getIterator());
        mgr.refreshCurrentServerAddr();
        assertNotNull(mgr.getIterator());
        
        final String currentServerAddr = mgr.getCurrentServerAddr();
        assertEquals(addr, currentServerAddr);
        
        final String nextServerAddr = mgr.getNextServerAddr();
        assertEquals(addr, nextServerAddr);
        
        final Iterator<String> iterator1 = mgr.iterator();
        assertTrue(iterator1.hasNext());
        
    }
    
    @Test
    void testAddressServerBaseServerAddrsStr() throws NacosException {
        Properties properties = new Properties();
        String serverAddrStr = "nacos.test.com:8080";
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddrStr);
        String endpointContextPath = "/endpoint";
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, endpointContextPath);
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ServerListManager serverListManager = new ServerListManager(clientProperties);
        assertEquals(1, serverListManager.serverUrls.size());
        assertTrue(serverListManager.serverUrls.contains(HTTP_PREFIX + serverAddrStr));
    }
    
    @Test
    void testAddressServerBaseEndpoint() throws NacosException {
        Properties properties = new Properties();
        String endpoint = "127.0.0.1";
        properties.setProperty(PropertyKeyConst.ENDPOINT, endpoint);
        String endpointPort = "8080";
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, endpointPort);
        String endpointContextPath = "/endpoint";
        properties.setProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH, endpointContextPath);
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ServerListManager serverListManager = new ServerListManager(clientProperties);
        assertTrue(serverListManager.addressServerUrl.startsWith(
                HTTP_PREFIX + endpoint + ":" + endpointPort + endpointContextPath));
    }
    
    @Test
    void testInitParam() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        String endpoint = "127.0.0.1";
        properties.setProperty(PropertyKeyConst.ENDPOINT, endpoint);
        String endpointPort = "9090";
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, endpointPort);
        String endpointContextPath = "/endpointContextPath";
        properties.setProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH, endpointContextPath);
        String contextPath = "/contextPath";
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, contextPath);
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ServerListManager serverListManager = new ServerListManager(clientProperties);
        Field endpointField = ServerListManager.class.getDeclaredField("endpoint");
        endpointField.setAccessible(true);
        String fieldEndpoint = (String) endpointField.get(serverListManager);
        assertEquals(endpoint, fieldEndpoint);
        
        Field endpointPortField = ServerListManager.class.getDeclaredField("endpointPort");
        endpointPortField.setAccessible(true);
        String fieldEndpointPort = String.valueOf(endpointPortField.get(serverListManager));
        assertEquals(endpointPort, fieldEndpointPort);
        
        Field endpointContextPathField = ServerListManager.class.getDeclaredField("endpointContextPath");
        endpointContextPathField.setAccessible(true);
        String fieldEndpointContextPath = String.valueOf(endpointContextPathField.get(serverListManager));
        assertEquals(endpointContextPath, fieldEndpointContextPath);
        
        Field contentPathField = ServerListManager.class.getDeclaredField("contentPath");
        contentPathField.setAccessible(true);
        String fieldContentPath = String.valueOf(contentPathField.get(serverListManager));
        assertEquals(fieldContentPath, contextPath);
    }
    
    @Test
    void testWithEndpointContextPath() throws NacosException {
        Properties properties = new Properties();
        String endpoint = "127.0.0.1";
        properties.setProperty(PropertyKeyConst.ENDPOINT, endpoint);
        String endpointPort = "9090";
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, endpointPort);
        String endpointContextPath = "/endpointContextPath";
        properties.setProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH, endpointContextPath);
        String contextPath = "/contextPath";
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, contextPath);
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ServerListManager serverListManager = new ServerListManager(clientProperties);
        assertTrue(serverListManager.addressServerUrl.contains(endpointContextPath));
        assertTrue(serverListManager.getName().contains("endpointContextPath"));
    }
    
    @Test
    void testWithoutEndpointContextPath() throws NacosException {
        Properties properties = new Properties();
        String endpoint = "127.0.0.1";
        properties.setProperty(PropertyKeyConst.ENDPOINT, endpoint);
        String endpointPort = "9090";
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, endpointPort);
        String contextPath = "/contextPath";
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, contextPath);
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ServerListManager serverListManager = new ServerListManager(clientProperties);
        String endpointContextPath = "/endpointContextPath";
        assertFalse(serverListManager.addressServerUrl.contains(endpointContextPath));
        assertTrue(serverListManager.addressServerUrl.contains(contextPath));
        assertFalse(serverListManager.getName().contains("endpointContextPath"));
        assertTrue(serverListManager.getName().contains("contextPath"));
    }
}