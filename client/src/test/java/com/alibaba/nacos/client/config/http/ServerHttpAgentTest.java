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
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.client.config.impl.ConfigHttpClientManager;
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
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@RunWith(MockitoJUnitRunner.class)
public class ServerHttpAgentTest {
    
    NacosRestTemplate nacosRestTemplate;
    
    MockedStatic<ConfigHttpClientManager> configHttpClientManagerMockedStatic;
    
    @Mock
    ConfigHttpClientManager configHttpClientManager;
    
    @Before
    public void before() {
        configHttpClientManagerMockedStatic = Mockito.mockStatic(ConfigHttpClientManager.class);
        configHttpClientManagerMockedStatic.when(() -> ConfigHttpClientManager.getInstance())
                .thenReturn(configHttpClientManager);
        nacosRestTemplate = Mockito.mock(NacosRestTemplate.class);
        Mockito.when(configHttpClientManager.getNacosRestTemplate()).thenReturn(nacosRestTemplate);
    }
    
    @After
    public void after() {
        configHttpClientManagerMockedStatic.close();
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
    
    private void resetNacosHttpTemplate(ServerHttpAgent serverHttpAgent, NacosRestTemplate nacosRestTemplate)
            throws Exception {
        Field nacosRestTemplateFiled = ServerHttpAgent.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateFiled.setAccessible(true);
        nacosRestTemplateFiled.set(serverHttpAgent, nacosRestTemplate);
    }
    
    @Test
    public void testHttpGetSuccess() throws Exception {
        
        Mockito.when(
                        nacosRestTemplate.get(anyString(), any(HttpClientConfig.class), any(Header.class), any(Query.class),
                                eq(String.class))).thenReturn(new HttpRestResult(Header.newInstance(), 500, "", ""))
                .thenThrow(new ConnectException())
                .thenReturn(new HttpRestResult(Header.newInstance(), 200, "hello", "success"));
        ServerListManager server = new ServerListManager(
                Arrays.asList("127.0.0.1", "127.0.0.2", "127.0.0.3", "127.0.0.4", "127.0.0.5"));
        final ServerHttpAgent serverHttpAgent = new ServerHttpAgent(server);
        resetNacosHttpTemplate(serverHttpAgent, nacosRestTemplate);
        String path = "config.do";
        Map<String, String> parmas = new HashMap<>();
        parmas.put("dataId", "12345");
        HttpRestResult<String> stringHttpRestResult = serverHttpAgent.httpGet(path, Header.newInstance().getHeader(),
                parmas, "UTF-8", 3000L);
        Assert.assertEquals("hello", stringHttpRestResult.getData());
        Assert.assertEquals(true, stringHttpRestResult.ok());
        Assert.assertEquals("success", stringHttpRestResult.getMessage());
        
    }
    
    @Test
    public void testHttpGetFail() throws Exception {
        
        Mockito.when(
                        nacosRestTemplate.get(anyString(), any(HttpClientConfig.class), any(Header.class), any(Query.class),
                                eq(String.class))).thenThrow(new SocketTimeoutException()).thenThrow(new ConnectException())
                .thenThrow(new ConnectException()).thenThrow(new NacosRuntimeException(2048));
        ServerListManager server = new ServerListManager(Arrays.asList("127.0.0.1", "127.0.0.2"));
        final ServerHttpAgent serverHttpAgent = new ServerHttpAgent(server);
        resetNacosHttpTemplate(serverHttpAgent, nacosRestTemplate);
        
        String path = "config.do";
        Map<String, String> parmas = new HashMap<>();
        parmas.put("dataId", "12345");
        try {
            serverHttpAgent.httpGet(path, Header.newInstance().getHeader(), parmas, "UTF-8", 3000L);
            Assert.fail();
        } catch (NacosRuntimeException e) {
            Assert.assertEquals(e.getErrCode(), 2048);
        } catch (Exception e) {
            Assert.fail();
        }
    }
    
    @Test
    public void testHttpPostSuccess() throws Exception {
        
        Mockito.when(
                        nacosRestTemplate.postForm(anyString(), any(HttpClientConfig.class), any(Header.class), any(Map.class),
                                eq(String.class))).thenReturn(new HttpRestResult(Header.newInstance(), 500, "", ""))
                .thenThrow(new ConnectException())
                .thenReturn(new HttpRestResult(Header.newInstance(), 200, "hello", "success"));
        ServerListManager server = new ServerListManager(
                Arrays.asList("127.0.0.1", "127.0.0.2", "127.0.0.3", "127.0.0.4", "127.0.0.5"));
        final ServerHttpAgent serverHttpAgent = new ServerHttpAgent(server);
        resetNacosHttpTemplate(serverHttpAgent, nacosRestTemplate);
        String path = "config.do";
        Map<String, String> parmas = new HashMap<>();
        parmas.put("dataId", "12345");
        HttpRestResult<String> stringHttpRestResult = serverHttpAgent.httpPost(path, Header.newInstance().getHeader(),
                parmas, "UTF-8", 3000L);
        Assert.assertEquals("hello", stringHttpRestResult.getData());
        Assert.assertEquals(true, stringHttpRestResult.ok());
        Assert.assertEquals("success", stringHttpRestResult.getMessage());
        
    }
    
    @Test
    public void testHttpPostFail() throws Exception {
        
        Mockito.when(
                        nacosRestTemplate.postForm(anyString(), any(HttpClientConfig.class), any(Header.class), any(Map.class),
                                eq(String.class))).thenThrow(new SocketTimeoutException()).thenThrow(new ConnectException())
                .thenThrow(new ConnectException()).thenThrow(new NacosRuntimeException(2048));
        ServerListManager server = new ServerListManager(Arrays.asList("127.0.0.1", "127.0.0.2"));
        final ServerHttpAgent serverHttpAgent = new ServerHttpAgent(server);
        resetNacosHttpTemplate(serverHttpAgent, nacosRestTemplate);
        
        String path = "config.do";
        Map<String, String> parmas = new HashMap<>();
        parmas.put("dataId", "12345");
        try {
            serverHttpAgent.httpPost(path, Header.newInstance().getHeader(), parmas, "UTF-8", 3000L);
            Assert.fail();
        } catch (NacosRuntimeException e) {
            Assert.assertEquals(e.getErrCode(), 2048);
        } catch (Exception e) {
            Assert.fail();
        }
    }
    
    @Test
    public void testHttpDeleteSuccess() throws Exception {
        
        Mockito.when(
                        nacosRestTemplate.delete(anyString(), any(HttpClientConfig.class), any(Header.class), any(Query.class),
                                eq(String.class))).thenReturn(new HttpRestResult(Header.newInstance(), 500, "", ""))
                .thenThrow(new ConnectException())
                .thenReturn(new HttpRestResult(Header.newInstance(), 200, "hello", "success"));
        ServerListManager server = new ServerListManager(
                Arrays.asList("127.0.0.1", "127.0.0.2", "127.0.0.3", "127.0.0.4", "127.0.0.5"));
        final ServerHttpAgent serverHttpAgent = new ServerHttpAgent(server);
        resetNacosHttpTemplate(serverHttpAgent, nacosRestTemplate);
        String path = "config.do";
        Map<String, String> parmas = new HashMap<>();
        parmas.put("dataId", "12345");
        HttpRestResult<String> stringHttpRestResult = serverHttpAgent.httpDelete(path, Header.newInstance().getHeader(),
                parmas, "UTF-8", 3000L);
        Assert.assertEquals("hello", stringHttpRestResult.getData());
        Assert.assertEquals(true, stringHttpRestResult.ok());
        Assert.assertEquals("success", stringHttpRestResult.getMessage());
        
    }
    
    @Test
    public void testHttpDeleteFail() throws Exception {
        
        Mockito.when(
                        nacosRestTemplate.delete(anyString(), any(HttpClientConfig.class), any(Header.class), any(Query.class),
                                eq(String.class))).thenThrow(new SocketTimeoutException()).thenThrow(new ConnectException())
                .thenThrow(new ConnectException()).thenThrow(new NacosRuntimeException(2048));
        ServerListManager server = new ServerListManager(Arrays.asList("127.0.0.1", "127.0.0.2"));
        final ServerHttpAgent serverHttpAgent = new ServerHttpAgent(server);
        resetNacosHttpTemplate(serverHttpAgent, nacosRestTemplate);
        
        String path = "config.do";
        Map<String, String> parmas = new HashMap<>();
        parmas.put("dataId", "12345");
        try {
            serverHttpAgent.httpDelete(path, Header.newInstance().getHeader(), parmas, "UTF-8", 3000L);
            Assert.fail();
        } catch (NacosRuntimeException e) {
            Assert.assertEquals(e.getErrCode(), 2048);
        } catch (Exception e) {
            Assert.fail();
        }
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
    
}