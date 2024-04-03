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
import com.alibaba.nacos.api.exception.runtime.NacosLoadException;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.http.HttpClientBeanHolder;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(MockitoJUnitRunner.class)
public class ServerListManagerTest {
    
    private static final String NS = "ns";
    
    @Mock
    NacosRestTemplate nacosRestTemplate;
    
    NacosRestTemplate cachedNacosRestTemplate;
    
    NacosClientProperties clientProperties;
    
    HttpRestResult httpRestResult;
    
    ServerListManager serverListManager;
    
    @Before
    public void setUp() throws Exception {
        clientProperties = NacosClientProperties.PROTOTYPE.derive();
        Field restMapField = HttpClientBeanHolder.class.getDeclaredField("SINGLETON_REST");
        restMapField.setAccessible(true);
        Map<String, NacosRestTemplate> restMap = (Map<String, NacosRestTemplate>) restMapField.get(null);
        cachedNacosRestTemplate = restMap
                .get("com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager$NamingHttpClientFactory");
        restMap.put("com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager$NamingHttpClientFactory",
                nacosRestTemplate);
        httpRestResult = new HttpRestResult<>();
        httpRestResult.setData("127.0.0.1:8848");
        httpRestResult.setCode(200);
        Mockito.when(nacosRestTemplate.get(any(), any(), any(), any())).thenReturn(httpRestResult);
    }
    
    @After
    public void tearDown() throws Exception {
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
    
    @Test(expected = NacosLoadException.class)
    public void testConstructError() {
        serverListManager = new ServerListManager(new Properties());
    }
    
    @Test
    public void testConstructWithAddr() {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848,127.0.0.1:8849");
        serverListManager = new ServerListManager(properties);
        final List<String> serverList = serverListManager.getServerList();
        Assert.assertEquals(2, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
        Assert.assertEquals("127.0.0.1:8849", serverList.get(1));
    }
    
    @Test
    public void testConstructWithAddrTryToRefresh()
            throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, NoSuchFieldException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848,127.0.0.1:8849");
        serverListManager = new ServerListManager(properties);
        List<String> serverList = serverListManager.getServerList();
        Assert.assertEquals(2, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
        Assert.assertEquals("127.0.0.1:8849", serverList.get(1));
        mockThreadInvoke(serverListManager, false);
        serverList = serverListManager.getServerList();
        Assert.assertEquals(2, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
        Assert.assertEquals("127.0.0.1:8849", serverList.get(1));
    }
    
    @Test
    public void testConstructWithEndpointAndRefresh() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        serverListManager = new ServerListManager(properties);
        List<String> serverList = serverListManager.getServerList();
        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
        
        httpRestResult.setData("127.0.0.1:8848\n127.0.0.1:8948");
        mockThreadInvoke(serverListManager, true);
        serverList = serverListManager.getServerList();
        Assert.assertEquals(2, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
        Assert.assertEquals("127.0.0.1:8948", serverList.get(1));
    }
    
    @Test
    public void testConstructWithEndpointAndTimedNotNeedRefresh() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        serverListManager = new ServerListManager(properties);
        List<String> serverList = serverListManager.getServerList();
        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
        
        httpRestResult.setData("127.0.0.1:8848\n127.0.0.1:8948");
        mockThreadInvoke(serverListManager, false);
        serverList = serverListManager.getServerList();
        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
    }
    
    @Test
    public void testConstructWithEndpointAndRefreshEmpty() throws Exception {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        serverListManager = new ServerListManager(properties);
        List<String> serverList = serverListManager.getServerList();
        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
        
        httpRestResult.setData("");
        mockThreadInvoke(serverListManager, true);
        serverList = serverListManager.getServerList();
        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
    }
    
    @Test
    public void testConstructWithEndpointAndRefreshException()
            throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, NoSuchFieldException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        serverListManager = new ServerListManager(properties);
        List<String> serverList = serverListManager.getServerList();
        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
        
        httpRestResult.setCode(500);
        mockThreadInvoke(serverListManager, true);
        serverList = serverListManager.getServerList();
        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
    }
    
    @Test
    public void testConstructWithEndpointWithCustomPathAndName() throws Exception {
        clientProperties.setProperty(PropertyKeyConst.CONTEXT_PATH, "aaa");
        clientProperties.setProperty(PropertyKeyConst.CLUSTER_NAME, "bbb");
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        Mockito.reset(nacosRestTemplate);
        Mockito.when(nacosRestTemplate.get(eq("http://127.0.0.1:8080/aaa/bbb"), any(), any(), any()))
                .thenReturn(httpRestResult);
        serverListManager = new ServerListManager(clientProperties, "test");
        List<String> serverList = serverListManager.getServerList();
        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
    }
    
    @Test
    public void testConstructWithEndpointWithEndpointPathAndName() throws Exception {
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH, "aaa");
        clientProperties.setProperty(PropertyKeyConst.CLUSTER_NAME, "bbb");
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        Mockito.reset(nacosRestTemplate);
        Mockito.when(nacosRestTemplate.get(eq("http://127.0.0.1:8080/aaa/bbb"), any(), any(), any()))
                .thenReturn(httpRestResult);
        serverListManager = new ServerListManager(clientProperties, "test");
        List<String> serverList = serverListManager.getServerList();
        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
    }
    
    @Test
    public void testConstructEndpointContextPathPriority() throws Exception {
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH, "aaa");
        clientProperties.setProperty(PropertyKeyConst.CONTEXT_PATH, "bbb");
        clientProperties.setProperty(PropertyKeyConst.CLUSTER_NAME, "ccc");
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        Mockito.reset(nacosRestTemplate);
        Mockito.when(nacosRestTemplate.get(eq("http://127.0.0.1:8080/aaa/ccc"), any(), any(), any()))
                .thenReturn(httpRestResult);
        serverListManager = new ServerListManager(clientProperties, "test");
        List<String> serverList = serverListManager.getServerList();
        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
    }
    
    @Test
    public void testConstructEndpointContextPathIsEmpty() throws Exception {
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH, "");
        clientProperties.setProperty(PropertyKeyConst.CONTEXT_PATH, "bbb");
        clientProperties.setProperty(PropertyKeyConst.CLUSTER_NAME, "ccc");
        clientProperties.setProperty(PropertyKeyConst.ENDPOINT, "127.0.0.1");
        Mockito.reset(nacosRestTemplate);
        Mockito.when(nacosRestTemplate.get(eq("http://127.0.0.1:8080/bbb/ccc"), any(), any(), any()))
                .thenReturn(httpRestResult);
        serverListManager = new ServerListManager(clientProperties, "test");
        List<String> serverList = serverListManager.getServerList();
        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
    }
    
    @Test
    public void testIsDomain() throws IOException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        serverListManager = new ServerListManager(properties);
        Assert.assertTrue(serverListManager.isDomain());
        Assert.assertEquals("127.0.0.1:8848", serverListManager.getNacosDomain());
    }
    
    @Test
    public void testGetCurrentServer() {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        final ServerListManager serverListManager = new ServerListManager(properties);
        Assert.assertEquals("127.0.0.1:8848", serverListManager.getCurrentServer());
        Assert.assertEquals("127.0.0.1:8848", serverListManager.genNextServer());
    }
    
    @Test
    public void testShutdown() {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");
        final ServerListManager serverListManager = new ServerListManager(properties);
        try {
            serverListManager.shutdown();
        } catch (Exception e) {
            Assert.fail();
        }
    }
    
    private void mockThreadInvoke(ServerListManager serverListManager, boolean expectedInvoked)
            throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Field field = ServerListManager.class.getDeclaredField("lastServerListRefreshTime");
        field.setAccessible(true);
        field.set(serverListManager, expectedInvoked ? 0 : System.currentTimeMillis());
        Method method = ServerListManager.class.getDeclaredMethod("refreshServerListIfNeed");
        method.setAccessible(true);
        method.invoke(serverListManager);
    }
}
