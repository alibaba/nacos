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
import com.alibaba.nacos.client.address.AbstractServerListManager;
import com.alibaba.nacos.client.address.AbstractServerListProvider;
import com.alibaba.nacos.client.address.EndpointServerListProvider;
import com.alibaba.nacos.client.address.ServerListProvider;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;

import static com.alibaba.nacos.common.constant.RequestUrlConstants.HTTP_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfigServerListManagerTest {
    
    @Mock
    NacosRestTemplate nacosRestTemplate;
    
    NacosRestTemplate cachedNacosRestTemplate;
    
    HttpRestResult httpRestResult;
    
    @BeforeEach
    void setUp() throws Exception {
        Field restMapField = HttpClientBeanHolder.class.getDeclaredField("SINGLETON_REST");
        restMapField.setAccessible(true);
        Map<String, NacosRestTemplate> restMap = (Map<String, NacosRestTemplate>) restMapField.get(null);
        cachedNacosRestTemplate = restMap.get(
                "com.alibaba.nacos.client.config.impl.ConfigHttpClientManager$ConfigHttpClientFactory");
        restMap.put("com.alibaba.nacos.client.config.impl.ConfigHttpClientManager$ConfigHttpClientFactory",
                nacosRestTemplate);
        httpRestResult = new HttpRestResult<>();
        httpRestResult.setData("127.0.0.1:8848");
        httpRestResult.setCode(200);
        when(nacosRestTemplate.get(contains("1.1.1.1:9090"), any(), any(), any())).thenReturn(httpRestResult);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (null != cachedNacosRestTemplate) {
            Field restMapField = HttpClientBeanHolder.class.getDeclaredField("SINGLETON_REST");
            restMapField.setAccessible(true);
            Map<String, NacosRestTemplate> restMap = (Map<String, NacosRestTemplate>) restMapField.get(null);
            restMap.put("com.alibaba.nacos.client.config.impl.ConfigHttpClientManager$ConfigHttpClientFactory",
                    cachedNacosRestTemplate);
        }
    }
    
    @Test
    void testStart() throws NacosException {
        NacosClientProperties mockedProperties = mock(NacosClientProperties.class);
        when(mockedProperties.getProperty(PropertyKeyConst.ENDPOINT)).thenReturn("1.1.1.1");
        when(mockedProperties.getProperty(PropertyKeyConst.ENDPOINT_PORT)).thenReturn("9090");
        when(mockedProperties.getProperty(PropertyKeyConst.ENDPOINT_REFRESH_INTERVAL_SECONDS, "30")).thenReturn("30");
        when(mockedProperties.derive()).thenReturn(mockedProperties);
        final ConfigServerListManager mgr = new ConfigServerListManager(mockedProperties);
        try {
            mgr.start();
            assertEquals("Config-custom-1.1.1.1_9090_nacos_serverlist", mgr.getName());
        } finally {
            mgr.shutdown();
        }
    }
    
    @Test
    void testStartWithCustomServerName() throws NacosException {
        NacosClientProperties properties = NacosClientProperties.PROTOTYPE.derive();
        properties.setProperty(PropertyKeyConst.SERVER_NAME, "test");
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "1.1.1.1");
        final ConfigServerListManager mgr = new ConfigServerListManager(properties);
        try {
            mgr.start();
            assertEquals("test", mgr.getName());
        } finally {
            mgr.shutdown();
        }
    }
    
    @Test
    void testGetter() throws NacosException {
        {
            NacosClientProperties mockedProperties = mock(NacosClientProperties.class);
            when(mockedProperties.getProperty(PropertyKeyConst.SERVER_ADDR)).thenReturn("1.1.1.1");
            when(mockedProperties.getProperty(PropertyKeyConst.NAMESPACE)).thenReturn("namespace");
            when(mockedProperties.derive()).thenReturn(mockedProperties);
            final ConfigServerListManager mgr = new ConfigServerListManager(mockedProperties);
            mgr.start();
            assertEquals("nacos", mgr.getContextPath());
            assertEquals("Config-fixed-namespace-1.1.1.1_8848", mgr.getName());
            assertEquals("namespace", mgr.getTenant());
            assertEquals("namespace", mgr.getNamespace());
            assertEquals("Config-fixed-namespace-1.1.1.1_8848", mgr.getServerName());
        }
        
        {
            Properties properties = new Properties();
            properties.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties.put(PropertyKeyConst.ENDPOINT, "1.1.1.1");
            properties.put(PropertyKeyConst.ENDPOINT_PORT, "9090");
            
            final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
            final ConfigServerListManager mgr2 = new ConfigServerListManager(nacosClientProperties);
            mgr2.start();
            assertEquals("aaa", mgr2.getContextPath());
        }
        
        // Test https
        {
            Properties properties = new Properties();
            properties.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties.put(PropertyKeyConst.SERVER_ADDR, "https://1.1.1.1:8848");
            final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
            final ConfigServerListManager mgr2 = new ConfigServerListManager(nacosClientProperties);
            mgr2.start();
            assertEquals("aaa", mgr2.getContextPath());
            assertEquals("[https://1.1.1.1:8848]", mgr2.getServerList().toString());
        }
        
        {
            Properties properties2 = new Properties();
            properties2.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties2.put(PropertyKeyConst.SERVER_ADDR, "1.1.1.1:8848");
            
            final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties2);
            final ConfigServerListManager mgr3 = new ConfigServerListManager(nacosClientProperties);
            mgr3.start();
            assertEquals(1, mgr3.getServerList().size());
            assertEquals("1.1.1.1:8848", mgr3.getServerList().get(0));
            assertEquals("[1.1.1.1:8848]", mgr3.getUrlString());
            assertTrue(mgr3.contain("1.1.1.1:8848"));
            assertEquals("ServerManager-Config-fixed-1.1.1.1_8848-[1.1.1.1:8848]", mgr3.toString());
        }
        
        {
            Properties properties3 = new Properties();
            properties3.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties3.put(PropertyKeyConst.SERVER_ADDR, "1.1.1.1:8848,2.2.2.2:8848");
            
            final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties3);
            final ConfigServerListManager mgr4 = new ConfigServerListManager(nacosClientProperties);
            mgr4.start();
            assertEquals(2, mgr4.getServerList().size());
            assertEquals("1.1.1.1:8848", mgr4.getServerList().get(0));
            assertEquals("2.2.2.2:8848", mgr4.getServerList().get(1));
            assertTrue(mgr4.contain("1.1.1.1:8848"));
            assertEquals("ServerManager-Config-fixed-1.1.1.1_8848-2.2.2.2_8848-[1.1.1.1:8848, 2.2.2.2:8848]",
                    mgr4.toString());
        }
        
        {
            Properties properties4 = new Properties();
            properties4.put(PropertyKeyConst.CONTEXT_PATH, "aaa");
            properties4.put(PropertyKeyConst.SERVER_ADDR, "1.1.1.1:8848;2.2.2.2:8848");
            
            final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties4);
            final ConfigServerListManager mgr5 = new ConfigServerListManager(nacosClientProperties);
            mgr5.start();
            assertEquals(2, mgr5.getServerList().size());
            assertEquals("1.1.1.1:8848", mgr5.getServerList().get(0));
            assertEquals("2.2.2.2:8848", mgr5.getServerList().get(1));
            assertTrue(mgr5.contain("1.1.1.1:8848"));
            assertEquals("ServerManager-Config-fixed-1.1.1.1_8848-2.2.2.2_8848-[1.1.1.1:8848, 2.2.2.2:8848]",
                    mgr5.toString());
        }
        
    }
    
    @Test
    void testIterator() throws NacosException {
        NacosClientProperties mockedProperties = mock(NacosClientProperties.class);
        when(mockedProperties.getProperty(PropertyKeyConst.SERVER_ADDR)).thenReturn("1.1.1.1:8848");
        when(mockedProperties.getProperty(PropertyKeyConst.NAMESPACE)).thenReturn("aaa");
        when(mockedProperties.derive()).thenReturn(mockedProperties);
        final ConfigServerListManager mgr = new ConfigServerListManager(mockedProperties);
        mgr.start();
        
        // new iterator
        final Iterator<String> it = mgr.iterator();
        assertTrue(it.hasNext());
        assertEquals("1.1.1.1:8848", it.next());
        
        Iterator<String> initIterator = mgr.getIterator();
        assertNotNull(initIterator);
        mgr.refreshCurrentServerAddr();
        assertNotNull(mgr.getIterator());
        assertNotEquals(initIterator, mgr.getIterator());
        
        final String currentServerAddr = mgr.getCurrentServer();
        assertEquals("1.1.1.1:8848", currentServerAddr);
        
        final String nextServerAddr = mgr.genNextServer();
        assertEquals("1.1.1.1:8848", nextServerAddr);
        
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
        ConfigServerListManager serverListManager = new ConfigServerListManager(clientProperties);
        serverListManager.start();
        assertEquals(1, serverListManager.getServerList().size());
        assertTrue(serverListManager.getServerList().contains(serverAddrStr));
    }
    
    @Test
    void testAddressServerBaseEndpoint() throws NacosException {
        Properties properties = new Properties();
        String endpoint = "1.1.1.1";
        properties.setProperty(PropertyKeyConst.ENDPOINT, endpoint);
        String endpointPort = "9090";
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, endpointPort);
        String endpointContextPath = "/endpoint";
        properties.setProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH, endpointContextPath);
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ConfigServerListManager serverListManager = new ConfigServerListManager(clientProperties);
        serverListManager.start();
        assertTrue(serverListManager.getAddressSource()
                .startsWith(HTTP_PREFIX + endpoint + ":" + endpointPort + endpointContextPath));
    }
    
    @Test
    void testInitParam() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        String endpoint = "1.1.1.1";
        properties.setProperty(PropertyKeyConst.ENDPOINT, endpoint);
        String endpointPort = "9090";
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, endpointPort);
        String endpointContextPath = "/endpointContextPath";
        properties.setProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH, endpointContextPath);
        String contextPath = "/contextPath";
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, contextPath);
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ConfigServerListManager serverListManager = new ConfigServerListManager(clientProperties);
        serverListManager.start();
        Field providerField = AbstractServerListManager.class.getDeclaredField("serverListProvider");
        providerField.setAccessible(true);
        ServerListProvider serverListProvider = (ServerListProvider) providerField.get(serverListManager);
        
        Field endpointField = EndpointServerListProvider.class.getDeclaredField("endpoint");
        endpointField.setAccessible(true);
        String fieldEndpoint = (String) endpointField.get(serverListProvider);
        assertEquals(endpoint, fieldEndpoint);
        
        Field endpointPortField = EndpointServerListProvider.class.getDeclaredField("endpointPort");
        endpointPortField.setAccessible(true);
        String fieldEndpointPort = String.valueOf(endpointPortField.get(serverListProvider));
        assertEquals(endpointPort, fieldEndpointPort);
        
        Field endpointContextPathField = EndpointServerListProvider.class.getDeclaredField("endpointContextPath");
        endpointContextPathField.setAccessible(true);
        String fieldEndpointContextPath = String.valueOf(endpointContextPathField.get(serverListProvider));
        assertEquals(endpointContextPath, fieldEndpointContextPath);
        
        Field contentPathField = AbstractServerListProvider.class.getDeclaredField("contextPath");
        contentPathField.setAccessible(true);
        String fieldContentPath = String.valueOf(contentPathField.get(serverListProvider));
        assertEquals(fieldContentPath, contextPath);
    }
    
    @Test
    void testWithEndpointContextPath() throws NacosException {
        Properties properties = new Properties();
        String endpoint = "1.1.1.1";
        properties.setProperty(PropertyKeyConst.ENDPOINT, endpoint);
        String endpointPort = "9090";
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, endpointPort);
        String endpointContextPath = "/endpointContextPath";
        properties.setProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH, endpointContextPath);
        String contextPath = "/contextPath";
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, contextPath);
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ConfigServerListManager serverListManager = new ConfigServerListManager(clientProperties);
        serverListManager.start();
        assertTrue(serverListManager.getAddressSource().contains(endpointContextPath));
        assertTrue(serverListManager.getName().contains("endpointContextPath"));
    }
    
    @Test
    void testWithEndpointClusterName() throws NacosException {
        Properties properties = new Properties();
        String endpoint = "1.1.1.1";
        properties.setProperty(PropertyKeyConst.ENDPOINT, endpoint);
        String endpointPort = "9090";
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, endpointPort);
        String testEndpointClusterName = "testEndpointClusterName";
        properties.setProperty(PropertyKeyConst.ENDPOINT_CLUSTER_NAME, testEndpointClusterName);
        String testClusterName = "testClusterName";
        properties.setProperty(PropertyKeyConst.CLUSTER_NAME, testClusterName);
        String endpointContextPath = "/endpointContextPath";
        properties.setProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH, endpointContextPath);
        String contextPath = "/contextPath";
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, contextPath);
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ConfigServerListManager serverListManager = new ConfigServerListManager(clientProperties);
        serverListManager.start();
        String addressSource = serverListManager.getAddressSource();
        assertTrue(addressSource.contains(endpointContextPath));
        assertTrue(serverListManager.getName().contains("endpointContextPath"));
        
        assertTrue(addressSource.contains(testEndpointClusterName));
        assertTrue(serverListManager.getName().contains(testEndpointClusterName));
        
        assertFalse(addressSource.contains(testClusterName));
        assertFalse(serverListManager.getName().contains(testClusterName));
        
    }
    
    @Test
    void testWithoutEndpointContextPath() throws NacosException {
        Properties properties = new Properties();
        String endpoint = "1.1.1.1";
        properties.setProperty(PropertyKeyConst.ENDPOINT, endpoint);
        String endpointPort = "9090";
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, endpointPort);
        String contextPath = "/contextPath";
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, contextPath);
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ConfigServerListManager serverListManager = new ConfigServerListManager(clientProperties);
        serverListManager.start();
        String endpointContextPath = "/endpointContextPath";
        
        assertFalse(serverListManager.getAddressSource().contains(endpointContextPath));
        assertTrue(serverListManager.getAddressSource().contains(contextPath));
        assertFalse(serverListManager.getName().contains("endpointContextPath"));
        assertTrue(serverListManager.getName().contains("contextPath"));
    }
    
    @Test
    void testUseEndpointParsingRule() throws NacosException {
        System.setProperty("nacos.endpoint", "1.1.1.1");
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.ENDPOINT, "${nacos.endpoint}");
        properties.setProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE, "true");
        properties.setProperty(PropertyKeyConst.ENDPOINT_PORT, "9090");
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ConfigServerListManager serverListManager = new ConfigServerListManager(clientProperties);
        serverListManager.start();
        String addressServerUrl = serverListManager.getAddressSource();
        assertTrue(addressServerUrl.startsWith("http://1.1.1.1"));
    }
    
    @Test
    void testUpdateCurrentServerAddr() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "1.1.1.1:8848,2.2.2.2:8848");
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ConfigServerListManager serverListManager = new ConfigServerListManager(clientProperties);
        serverListManager.start();
        assertTrue("1.1.1.1:8848,2.2.2.2:8848".contains(serverListManager.getCurrentServer()));
        serverListManager.updateCurrentServerAddr(null);
        assertTrue("1.1.1.1:8848,2.2.2.2:8848".contains(serverListManager.getCurrentServer()));
        serverListManager.updateCurrentServerAddr("1.1.1.1:8848");
        assertEquals("1.1.1.1:8848", serverListManager.getCurrentServer());
    }
    
    @Test
    void testStartWithEmptyServerList() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty("EmptyList", "true");
        properties.setProperty("MockTest", "true");
        NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        final ConfigServerListManager mgr = new ConfigServerListManager(clientProperties);
        try {
            assertThrows(NoSuchElementException.class, mgr::start);
        } finally {
            mgr.shutdown();
        }
    }
    
    @Test
    void testGenNextServer() throws NacosException {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "1.1.1.1:8848,2.2.2.2:8848");
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ConfigServerListManager serverListManager = new ConfigServerListManager(clientProperties);
        serverListManager.start();
        String currentServer = serverListManager.getCurrentServer();
        String expectedServer = "1.1.1.1:8848,2.2.2.2:8848".replace(currentServer, "");
        expectedServer = expectedServer.replace(",", "");
        assertEquals(expectedServer, serverListManager.genNextServer());
        // Don't throw NoSuchElementException, re-generate server list and re-shuffle.
        assertTrue("1.1.1.1:8848,2.2.2.2:8848".contains(serverListManager.genNextServer()));
    }
    
    @Test
    void testGenNextServerWithMockConcurrent() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "1.1.1.1:8848,2.2.2.2:8848");
        final NacosClientProperties clientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        ConfigServerListManager serverListManager = new ConfigServerListManager(clientProperties);
        serverListManager.start();
        Iterator<String> mockIterator = mock(Iterator.class);
        Field field = ConfigServerListManager.class.getDeclaredField("iterator");
        field.setAccessible(true);
        field.set(serverListManager, mockIterator);
        // Mock async call gen next server, hasNext return `ture` and item be got by other thread.
        when(mockIterator.hasNext()).thenReturn(true);
        when(mockIterator.next()).thenThrow(new NoSuchElementException());
        assertNotNull(serverListManager.genNextServer());
    }
}