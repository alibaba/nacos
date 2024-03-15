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

package com.alibaba.nacos.client.config.http;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.impl.ServerListManager;
import com.alibaba.nacos.common.http.HttpClientConfig;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ServerHttpAgentTest {
    
    private static final String SERVER_ADDRESS_1 = "http://1.1.1.1:8848";
    
    private static final String SERVER_ADDRESS_2 = "http://2.2.2.2:8848";
    
    @Mock
    ServerListManager serverListManager;
    
    @Mock
    HttpRestResult<String> mockResult;
    
    @Mock
    Iterator<String> mockIterator;
    
    @Mock
    NacosRestTemplate nacosRestTemplate;
    
    ServerHttpAgent serverHttpAgent;
    
    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        serverHttpAgent = new ServerHttpAgent(serverListManager, new Properties());
        injectRestTemplate();
        when(serverListManager.getCurrentServerAddr()).thenReturn(SERVER_ADDRESS_1);
        when(serverListManager.getIterator()).thenReturn(mockIterator);
        when(mockIterator.next()).thenReturn(SERVER_ADDRESS_2);
    }
    
    private void injectRestTemplate() throws NoSuchFieldException, IllegalAccessException {
        Field restTemplateField = ServerHttpAgent.class.getDeclaredField("nacosRestTemplate");
        restTemplateField.setAccessible(true);
        restTemplateField.set(serverHttpAgent, nacosRestTemplate);
    }
    
    @After
    public void tearDown() throws NacosException {
        serverHttpAgent.shutdown();
    }
    
    @Test
    public void testConstruct() throws NacosException {
        ServerListManager server = new ServerListManager();
        final ServerHttpAgent serverHttpAgent1 = new ServerHttpAgent(server);
        Assert.assertNotNull(serverHttpAgent1);
        
        final ServerHttpAgent serverHttpAgent2 = new ServerHttpAgent(server, new Properties());
        Assert.assertNotNull(serverHttpAgent2);
        
        final Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "1.1.1.1");
        final ServerHttpAgent serverHttpAgent3 = new ServerHttpAgent(properties);
        Assert.assertNotNull(serverHttpAgent3);
        
    }
    
    @Test
    public void testGetterAndSetter() throws NacosException {
        ServerListManager server = new ServerListManager("aaa", "namespace1");
        final ServerHttpAgent serverHttpAgent = new ServerHttpAgent(server, new Properties());
        
        final String appname = ServerHttpAgent.getAppname();
        //set by AppNameUtils, init in ParamUtils static block
        Assert.assertEquals("unknown", appname);
        
        final String encode = serverHttpAgent.getEncode();
        final String namespace = serverHttpAgent.getNamespace();
        final String tenant = serverHttpAgent.getTenant();
        final String name = serverHttpAgent.getName();
        Assert.assertNull(encode);
        Assert.assertEquals("namespace1", namespace);
        Assert.assertEquals("namespace1", tenant);
        Assert.assertEquals("custom-aaa_8080_nacos_serverlist_namespace1", name);
        
    }
    
    @Test
    public void testLifCycle() throws NacosException {
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, "aaa");
        ServerListManager server = Mockito.mock(ServerListManager.class);
        final ServerHttpAgent serverHttpAgent = new ServerHttpAgent(server, properties);
        
        serverHttpAgent.start();
        Mockito.verify(server).start();
        
        try {
            serverHttpAgent.shutdown();
        } catch (NullPointerException e) {
            Assert.fail();
        }
    }
    
    @Test
    public void testHttpGetSuccess() throws Exception {
        when(nacosRestTemplate.<String>get(eq(SERVER_ADDRESS_1 + "/test"), any(HttpClientConfig.class),
                any(Header.class), any(Query.class), eq(String.class))).thenReturn(mockResult);
        when(mockResult.getCode()).thenReturn(HttpURLConnection.HTTP_OK);
        HttpRestResult<String> actual = serverHttpAgent
                .httpGet("/test", Collections.emptyMap(), Collections.emptyMap(), "UTF-8", 1000);
        Assert.assertEquals(mockResult, actual);
    }
    
    @Test(expected = ConnectException.class)
    public void testHttpGetFailed() throws Exception {
        when(nacosRestTemplate.<String>get(eq(SERVER_ADDRESS_1 + "/test"), any(HttpClientConfig.class),
                any(Header.class), any(Query.class), eq(String.class))).thenReturn(mockResult);
        when(mockResult.getCode()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);
        serverHttpAgent.httpGet("/test", Collections.emptyMap(), Collections.emptyMap(), "UTF-8", 1000);
    }
    
    @Test(expected = NacosException.class)
    public void testHttpWithRequestException() throws Exception {
        when(nacosRestTemplate.<String>get(eq(SERVER_ADDRESS_1 + "/test"), any(HttpClientConfig.class),
                any(Header.class), any(Query.class), eq(String.class)))
                .thenThrow(new ConnectException(), new SocketTimeoutException(), new NacosException());
        serverHttpAgent.httpGet("/test", Collections.emptyMap(), Collections.emptyMap(), "UTF-8", 1000);
    }
    
    @Test
    public void testRetryWithNewServer() throws Exception {
        when(mockIterator.hasNext()).thenReturn(true);
        when(nacosRestTemplate.<String>get(eq(SERVER_ADDRESS_1 + "/test"), any(HttpClientConfig.class),
                any(Header.class), any(Query.class), eq(String.class))).thenThrow(new ConnectException());
        when(nacosRestTemplate.<String>get(eq(SERVER_ADDRESS_2 + "/test"), any(HttpClientConfig.class),
                any(Header.class), any(Query.class), eq(String.class))).thenReturn(mockResult);
        when(mockResult.getCode()).thenReturn(HttpURLConnection.HTTP_OK);
        HttpRestResult<String> actual = serverHttpAgent
                .httpGet("/test", Collections.emptyMap(), Collections.emptyMap(), "UTF-8", 1000);
        Assert.assertEquals(mockResult, actual);
    }
    
    @Test(expected = ConnectException.class)
    public void testRetryTimeout() throws Exception {
        when(nacosRestTemplate.<String>get(eq(SERVER_ADDRESS_1 + "/test"), any(HttpClientConfig.class),
                any(Header.class), any(Query.class), eq(String.class))).thenThrow(new SocketTimeoutException());
        serverHttpAgent.httpGet("/test", Collections.emptyMap(), Collections.emptyMap(), "UTF-8", 0);
    }
    
    @Test
    public void testHttpPostSuccess() throws Exception {
        when(nacosRestTemplate.<String>postForm(eq(SERVER_ADDRESS_1 + "/test"), any(HttpClientConfig.class),
                any(Header.class), anyMap(), eq(String.class))).thenReturn(mockResult);
        when(mockResult.getCode()).thenReturn(HttpURLConnection.HTTP_OK);
        HttpRestResult<String> actual = serverHttpAgent
                .httpPost("/test", Collections.emptyMap(), Collections.emptyMap(), "UTF-8", 1000);
        Assert.assertEquals(mockResult, actual);
    }
    
    @Test(expected = ConnectException.class)
    public void testHttpPostFailed() throws Exception {
        when(nacosRestTemplate.<String>postForm(eq(SERVER_ADDRESS_1 + "/test"), any(HttpClientConfig.class),
                any(Header.class), anyMap(), eq(String.class))).thenReturn(mockResult);
        when(mockResult.getCode()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);
        serverHttpAgent.httpPost("/test", Collections.emptyMap(), Collections.emptyMap(), "UTF-8", 1000);
    }
    
    @Test(expected = NacosException.class)
    public void testHttpPostWithRequestException() throws Exception {
        when(nacosRestTemplate.<String>postForm(eq(SERVER_ADDRESS_1 + "/test"), any(HttpClientConfig.class),
                any(Header.class), anyMap(), eq(String.class)))
                .thenThrow(new ConnectException(), new SocketTimeoutException(), new NacosException());
        serverHttpAgent.httpPost("/test", Collections.emptyMap(), Collections.emptyMap(), "UTF-8", 1000);
    }
    
    @Test
    public void testRetryPostWithNewServer() throws Exception {
        when(mockIterator.hasNext()).thenReturn(true);
        when(nacosRestTemplate.<String>postForm(eq(SERVER_ADDRESS_1 + "/test"), any(HttpClientConfig.class),
                any(Header.class), anyMap(), eq(String.class))).thenThrow(new ConnectException());
        when(nacosRestTemplate.<String>postForm(eq(SERVER_ADDRESS_2 + "/test"), any(HttpClientConfig.class),
                any(Header.class), anyMap(), eq(String.class))).thenReturn(mockResult);
        when(mockResult.getCode()).thenReturn(HttpURLConnection.HTTP_OK);
        HttpRestResult<String> actual = serverHttpAgent
                .httpPost("/test", Collections.emptyMap(), Collections.emptyMap(), "UTF-8", 1000);
        Assert.assertEquals(mockResult, actual);
    }
    
    @Test(expected = ConnectException.class)
    public void testRetryPostTimeout() throws Exception {
        when(nacosRestTemplate.<String>postForm(eq(SERVER_ADDRESS_1 + "/test"), any(HttpClientConfig.class),
                any(Header.class), anyMap(), eq(String.class))).thenThrow(new SocketTimeoutException());
        serverHttpAgent.httpPost("/test", Collections.emptyMap(), Collections.emptyMap(), "UTF-8", 0);
    }
    
    @Test
    public void testHttpDeleteSuccess() throws Exception {
        when(nacosRestTemplate.<String>delete(eq(SERVER_ADDRESS_1 + "/test"), any(HttpClientConfig.class),
                any(Header.class), any(Query.class), eq(String.class))).thenReturn(mockResult);
        when(mockResult.getCode()).thenReturn(HttpURLConnection.HTTP_OK);
        HttpRestResult<String> actual = serverHttpAgent
                .httpDelete("/test", Collections.emptyMap(), Collections.emptyMap(), "UTF-8", 1000);
        Assert.assertEquals(mockResult, actual);
    }
    
    @Test(expected = ConnectException.class)
    public void testHttpDeleteFailed() throws Exception {
        when(nacosRestTemplate.<String>delete(eq(SERVER_ADDRESS_1 + "/test"), any(HttpClientConfig.class),
                any(Header.class), any(Query.class), eq(String.class))).thenReturn(mockResult);
        when(mockResult.getCode()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);
        serverHttpAgent.httpDelete("/test", Collections.emptyMap(), Collections.emptyMap(), "UTF-8", 1000);
    }
    
    @Test(expected = NacosException.class)
    public void testHttpDeleteWithRequestException() throws Exception {
        when(nacosRestTemplate.<String>delete(eq(SERVER_ADDRESS_1 + "/test"), any(HttpClientConfig.class),
                any(Header.class), any(Query.class), eq(String.class)))
                .thenThrow(new ConnectException(), new SocketTimeoutException(), new NacosException());
        serverHttpAgent.httpDelete("/test", Collections.emptyMap(), Collections.emptyMap(), "UTF-8", 1000);
    }
    
    @Test
    public void testRetryDeleteWithNewServer() throws Exception {
        when(mockIterator.hasNext()).thenReturn(true);
        when(nacosRestTemplate.<String>delete(eq(SERVER_ADDRESS_1 + "/test"), any(HttpClientConfig.class),
                any(Header.class), any(Query.class), eq(String.class))).thenThrow(new ConnectException());
        when(nacosRestTemplate.<String>delete(eq(SERVER_ADDRESS_2 + "/test"), any(HttpClientConfig.class),
                any(Header.class), any(Query.class), eq(String.class))).thenReturn(mockResult);
        when(mockResult.getCode()).thenReturn(HttpURLConnection.HTTP_OK);
        HttpRestResult<String> actual = serverHttpAgent
                .httpDelete("/test", Collections.emptyMap(), Collections.emptyMap(), "UTF-8", 1000);
        Assert.assertEquals(mockResult, actual);
    }
    
    @Test(expected = ConnectException.class)
    public void testRetryDeleteTimeout() throws Exception {
        when(nacosRestTemplate.<String>delete(eq(SERVER_ADDRESS_1 + "/test"), any(HttpClientConfig.class),
                any(Header.class), any(Query.class), eq(String.class))).thenThrow(new SocketTimeoutException());
        serverHttpAgent.httpDelete("/test", Collections.emptyMap(), Collections.emptyMap(), "UTF-8", 0);
    }
}