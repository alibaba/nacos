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

package com.alibaba.nacos.client.naming.core;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosLoadException;
import com.alibaba.nacos.client.address.AbstractServerListManager;
import com.alibaba.nacos.client.address.EndpointServerListProvider;
import com.alibaba.nacos.client.address.ServerListProvider;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NamingServerListManagerTest {
    
    @Mock
    NacosRestTemplate nacosRestTemplate;
    
    NacosRestTemplate cachedNacosRestTemplate;
    
    NacosClientProperties clientProperties;
    
    HttpRestResult httpRestResult;
    
    NamingServerListManager serverListManager;
    
    @BeforeEach
    void setUp() throws Exception {
        clientProperties = NacosClientProperties.PROTOTYPE.derive();
        Field restMapField = HttpClientBeanHolder.class.getDeclaredField("SINGLETON_REST");
        restMapField.setAccessible(true);
        Map<String, NacosRestTemplate> restMap = (Map<String, NacosRestTemplate>) restMapField.get(null);
        cachedNacosRestTemplate = restMap.get(
                "com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager$NamingHttpClientFactory");
        restMap.put("com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager$NamingHttpClientFactory",
                nacosRestTemplate);
        httpRestResult = new HttpRestResult<>();
        httpRestResult.setData("127.0.0.1:8848");
        httpRestResult.setCode(200);
        Mockito.when(nacosRestTemplate.get(contains("127.0.0.1"), any(), any(), any())).thenReturn(httpRestResult);
    }
    
    @AfterEach
    void tearDown() throws Exception {
        if (null != cachedNacosRestTemplate) {
            Field restMapField = HttpClientBeanHolder.class.getDeclaredField("SINGLETON_REST");
            restMapField.setAccessible(true);
            Map<String, NacosRestTemplate> restMap = (Map<String, NacosRestTemplate>) restMapField.get(null);
            restMap.put("com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager$NamingHttpClientFactory",
                    cachedNacosRestTemplate);
        }
        if (null != serverListManager) {
            serverListManager.shutdown();
        }
    }
    
    @Test
    void testConstructWithAddr() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848,127.0.0.1:8849");
        serverListManager = new NamingServerListManager(properties);
        serverListManager.start();
        final List<String> serverList = serverListManager.getServerList();
        assertEquals(2, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
        assertEquals("127.0.0.1:8849", serverList.get(1));
    }
    
    @Test
    void testConstructWithAddrTryToRefresh() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848,127.0.0.1:8849");
        serverListManager = new NamingServerListManager(properties);
        serverListManager.start();
        List<String> serverList = serverListManager.getServerList();
        assertEquals(2, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
        assertEquals("127.0.0.1:8849", serverList.get(1));
        serverList = serverListManager.getServerList();
        assertEquals(2, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
        assertEquals("127.0.0.1:8849", serverList.get(1));
    }
    
    @Test
    void testConstructWithEndpointAndRefresh() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        serverListManager = new NamingServerListManager(properties);
        serverListManager.start();
        List<String> serverList = serverListManager.getServerList();
        assertEquals(1, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
        
        httpRestResult.setData("127.0.0.1:8848\n127.0.0.1:8948");
        mockThreadInvoke(serverListManager, true);
        serverList = serverListManager.getServerList();
        assertEquals(2, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
        assertEquals("127.0.0.1:8948", serverList.get(1));
    }
    
    @Test
    void testConstructWithEndpointAndTimedNotNeedRefresh() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        serverListManager = new NamingServerListManager(properties);
        serverListManager.start();
        List<String> serverList = serverListManager.getServerList();
        assertEquals(1, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
        
        httpRestResult.setData("127.0.0.1:8848\n127.0.0.1:8948");
        serverList = serverListManager.getServerList();
        assertEquals(1, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
    }
    
    @Test
    void testConstructWithEndpointAndRefreshEmpty() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        serverListManager = new NamingServerListManager(properties);
        serverListManager.start();
        List<String> serverList = serverListManager.getServerList();
        assertEquals(1, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
        
        httpRestResult.setData("");
        serverList = serverListManager.getServerList();
        assertEquals(1, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
    }
    
    @Test
    void testConstructWithEndpointAndRefreshException() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        serverListManager = new NamingServerListManager(properties);
        serverListManager.start();
        List<String> serverList = serverListManager.getServerList();
        assertEquals(1, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
        
        httpRestResult.setCode(500);
        serverList = serverListManager.getServerList();
        assertEquals(1, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
    }
    
    @Test
    void testConstructWithEndpointWithCustomPathAndName() throws Exception {
        clientProperties.setProperty(PropertyKeyConst.CONTEXT_PATH, "aaa");
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT_CLUSTER_NAME, "bbb");
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        Mockito.reset(nacosRestTemplate);
        Mockito.when(nacosRestTemplate.get(eq("http://127.0.0.1:8080/aaa/bbb"), any(), any(), any()))
                .thenReturn(httpRestResult);
        serverListManager = new NamingServerListManager(clientProperties, "");
        serverListManager.start();
        List<String> serverList = serverListManager.getServerList();
        assertEquals(1, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
    }
    
    @Test
    void testConstructWithEndpointWithEndpointPathAndName() throws Exception {
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH, "aaa");
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT_CLUSTER_NAME, "bbb");
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        Mockito.reset(nacosRestTemplate);
        Mockito.when(nacosRestTemplate.get(eq("http://127.0.0.1:8080/aaa/bbb"), any(), any(), any()))
                .thenReturn(httpRestResult);
        serverListManager = new NamingServerListManager(clientProperties, "");
        serverListManager.start();
        List<String> serverList = serverListManager.getServerList();
        assertEquals(1, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
    }
    
    @Test
    void testConstructEndpointContextPathPriority() throws Exception {
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH, "aaa");
        clientProperties.setProperty(PropertyKeyConst.CONTEXT_PATH, "bbb");
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT_CLUSTER_NAME, "ccc");
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        Mockito.reset(nacosRestTemplate);
        Mockito.when(nacosRestTemplate.get(eq("http://127.0.0.1:8080/aaa/ccc"), any(), any(), any()))
                .thenReturn(httpRestResult);
        serverListManager = new NamingServerListManager(clientProperties, "");
        serverListManager.start();
        List<String> serverList = serverListManager.getServerList();
        assertEquals(1, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
    }
    
    @Test
    void testConstructEndpointContextPathIsEmpty() throws Exception {
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH, "");
        clientProperties.setProperty(PropertyKeyConst.CONTEXT_PATH, "bbb");
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT_CLUSTER_NAME, "ccc");
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        Mockito.reset(nacosRestTemplate);
        Mockito.when(nacosRestTemplate.get(eq("http://127.0.0.1:8080/bbb/ccc"), any(), any(), any()))
                .thenReturn(httpRestResult);
        serverListManager = new NamingServerListManager(clientProperties, "");
        serverListManager.start();
        List<String> serverList = serverListManager.getServerList();
        assertEquals(1, serverList.size());
        assertEquals("127.0.0.1:8848", serverList.get(0));
    }
    
    @Test
    void testIsDomain() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        serverListManager = new NamingServerListManager(properties);
        serverListManager.start();
        assertTrue(serverListManager.isDomain());
        assertEquals("127.0.0.1:8848", serverListManager.getNacosDomain());
    }
    
    @Test
    void testGetCurrentServer() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        final NamingServerListManager serverListManager = new NamingServerListManager(properties);
        serverListManager.start();
        assertEquals("127.0.0.1:8848", serverListManager.getCurrentServer());
        assertEquals("127.0.0.1:8848", serverListManager.genNextServer());
    }
    
    @Test
    void testShutdown() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        final NamingServerListManager serverListManager = new NamingServerListManager(properties);
        serverListManager.start();
        Assertions.assertDoesNotThrow(() -> {
            serverListManager.shutdown();
        });
    }
    
    @Test
    void testStartWithEmptyServerList() {
        Properties properties = new Properties();
        properties.setProperty("EmptyList", "true");
        properties.setProperty("MockTest", "true");
        final NamingServerListManager serverListManager = new NamingServerListManager(properties);
        assertThrows(NacosLoadException.class, serverListManager::start);
    }
    
    private void mockThreadInvoke(NamingServerListManager serverListManager, boolean expectedInvoked)
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Field providerField = AbstractServerListManager.class.getDeclaredField("serverListProvider");
        providerField.setAccessible(true);
        ServerListProvider serverListProvider = (ServerListProvider) providerField.get(serverListManager);
        assertInstanceOf(EndpointServerListProvider.class, serverListProvider);
        Field field = EndpointServerListProvider.class.getDeclaredField("lastServerListRefreshTime");
        field.setAccessible(true);
        field.set(serverListProvider, expectedInvoked ? 0 : System.currentTimeMillis());
        Method method = EndpointServerListProvider.class.getDeclaredMethod("refreshServerListIfNeed");
        method.setAccessible(true);
        method.invoke(serverListProvider);
    }
}
