/*
 *
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.client.naming.remote.http;

import com.alibaba.nacos.api.SystemPropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.core.ServerListManager;
import com.alibaba.nacos.client.naming.event.ServerListChangedEvent;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.utils.HttpMethod;
import com.alibaba.nacos.common.utils.ReflectUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class NamingHttpClientProxyTest {
    
    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    
    @Mock
    private SecurityProxy proxy;
    
    @Mock
    private ServerListManager mgr;
    
    private Properties props;
    
    private NamingHttpClientProxy clientProxy;
    
    @Before
    public void setUp() {
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        props = new Properties();
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(props);
        clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, nacosClientProperties);
    }
    
    @After
    public void tearDown() throws NacosException {
        clientProxy.shutdown();
        System.clearProperty(SystemPropertyKeyConst.NAMING_SERVER_PORT);
    }
    
    @Test
    public void testOnEvent() {
        clientProxy.onEvent(new ServerListChangedEvent());
        // Do nothing
    }
    
    @Test
    public void testSubscribeType() {
        assertEquals(ServerListChangedEvent.class, clientProxy.subscribeType());
    }
    
    @Test
    public void testRegisterService() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("127.0.0.1:8848");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        instance.setEphemeral(false);
        //when
        clientProxy.registerService(serviceName, groupName, instance);
        //then
        verify(nacosRestTemplate, times(1)).exchangeForm(any(), any(), any(), any(), any(), any());
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void testRegisterEphemeralInstance() throws NacosException {
        Instance instance = new Instance();
        clientProxy.registerService("a", "b", instance);
    }
    
    @Test
    public void testRegisterServiceThrowsNacosException() throws Exception {
        thrown.expect(NacosException.class);
        thrown.expectMessage("failed to req API");
        
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setCode(503);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        instance.setEphemeral(false);
        try {
            clientProxy.registerService(serviceName, groupName, instance);
        } catch (NacosException ex) {
            // verify the `NacosException` is directly thrown
            assertEquals(null, ex.getCause());
            
            throw ex;
        }
    }
    
    @Test
    public void testRegisterServiceThrowsException() throws Exception {
        // assert throw NacosException
        thrown.expect(NacosException.class);
        
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setCode(503);
        // makes exchangeForm failed with a NullPointerException
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(null);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        instance.setEphemeral(false);
        
        try {
            clientProxy.registerService(serviceName, groupName, instance);
        } catch (NacosException ex) {
            // verify the `NacosException` is directly thrown
            Assert.assertTrue(ex.getErrMsg().contains("java.lang.NullPointerException"));
            assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
            
            throw ex;
        }
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void testBatchRegisterService() {
        clientProxy.batchRegisterService("a", "b", null);
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void testBatchDeregisterService() {
        clientProxy.batchDeregisterService("a", "b", null);
    }
    
    @Test
    public void testDeregisterService() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("127.0.0.1:8848");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        instance.setEphemeral(false);
        //when
        clientProxy.deregisterService(serviceName, groupName, instance);
        //then
        verify(nacosRestTemplate, times(1)).exchangeForm(any(), any(), any(), any(), eq(HttpMethod.DELETE), any());
    }
    
    @Test
    public void testDeregisterServiceForEphemeral() throws Exception {
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        Instance instance = new Instance();
        clientProxy.deregisterService("serviceName", "groupName", instance);
        verify(nacosRestTemplate, never()).exchangeForm(any(), any(), any(), any(), eq(HttpMethod.DELETE), any());
        
    }
    
    @Test
    public void testUpdateInstance() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("127.0.0.1:8848");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        //when
        clientProxy.updateInstance(serviceName, groupName, instance);
        //then
        verify(nacosRestTemplate, times(1)).exchangeForm(any(), any(), any(), any(), eq(HttpMethod.PUT), any());
    }
    
    @Test
    public void testQueryInstancesOfServiceThrowsException() {
        //assert exception
        String serviceName = "service1";
        String groupName = "group1";
        String clusters = "cluster1";
        Assert.assertThrows(UnsupportedOperationException.class,
                () -> clientProxy.queryInstancesOfService(serviceName, groupName, clusters, false));
    }
    
    @Test
    public void testQueryService() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("{\"name\":\"service1\",\"groupName\":\"group1\"}");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        
        String serviceName = "service1";
        String groupName = "group1";
        
        //when
        Service service = clientProxy.queryService(serviceName, groupName);
        //then
        verify(nacosRestTemplate, times(1))
                .exchangeForm(endsWith(UtilAndComs.nacosUrlService), any(), any(), any(), eq(HttpMethod.GET), any());
        assertEquals(serviceName, service.getName());
        assertEquals(groupName, service.getGroupName());
    }
    
    @Test
    public void testCreateService() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        
        //when
        clientProxy.createService(new Service(), new NoneSelector());
        //then
        verify(nacosRestTemplate, times(1))
                .exchangeForm(endsWith(UtilAndComs.nacosUrlService), any(), any(), any(), eq(HttpMethod.POST), any());
    }
    
    @Test
    public void testDeleteService() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("{\"name\":\"service1\",\"groupName\":\"group1\"}");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String serviceName = "service1";
        String groupName = "group1";
        
        //when
        clientProxy.deleteService(serviceName, groupName);
        //then
        verify(nacosRestTemplate, times(1))
                .exchangeForm(endsWith(UtilAndComs.nacosUrlService), any(), any(), any(), eq(HttpMethod.DELETE), any());
    }
    
    @Test
    public void testUpdateService() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String serviceName = "service1";
        String groupName = "group1";
        
        //when
        clientProxy.updateService(new Service(), new NoneSelector());
        //then
        verify(nacosRestTemplate, times(1))
                .exchangeForm(endsWith(UtilAndComs.nacosUrlService), any(), any(), any(), eq(HttpMethod.PUT), any());
        
    }
    
    @Test
    public void testServerHealthy() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("{\"status\":\"UP\"}");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        
        //when
        boolean serverHealthy = clientProxy.serverHealthy();
        //then
        verify(nacosRestTemplate, times(1))
                .exchangeForm(endsWith("/operator/metrics"), any(), any(), any(), eq(HttpMethod.GET), any());
        Assert.assertTrue(serverHealthy);
    }
    
    @Test
    public void testServerHealthyForException() throws Exception {
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("test"));
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        assertFalse(clientProxy.serverHealthy());
    }
    
    @Test
    public void testGetServiceList() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("{\"count\":2,\"doms\":[\"aaa\",\"bbb\"]}");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String groupName = "group1";
        
        //when
        ListView<String> serviceList = clientProxy.getServiceList(1, 10, groupName, new NoneSelector());
        //then
        verify(nacosRestTemplate, times(1))
                .exchangeForm(endsWith("/service/list"), any(), any(), any(), eq(HttpMethod.GET), any());
        assertEquals(2, serviceList.getCount());
        assertEquals("aaa", serviceList.getData().get(0));
        assertEquals("bbb", serviceList.getData().get(1));
    }
    
    @Test
    public void testGetServiceListWithLabelSelector() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("{\"count\":2,\"doms\":[\"aaa\",\"bbb\"]}");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String groupName = "group1";
        
        //when
        ListView<String> serviceList = clientProxy.getServiceList(1, 10, groupName, new ExpressionSelector());
        //then
        verify(nacosRestTemplate, times(1))
                .exchangeForm(endsWith("/service/list"), any(), any(), any(), eq(HttpMethod.GET), any());
        assertEquals(2, serviceList.getCount());
        assertEquals("aaa", serviceList.getData().get(0));
        assertEquals("bbb", serviceList.getData().get(1));
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void testSubscribe() throws Exception {
        String groupName = "group1";
        String serviceName = "serviceName";
        String clusters = "clusters";
        
        //when
        clientProxy.subscribe(serviceName, groupName, clusters);
    }
    
    @Test
    public void testUnsubscribe() throws Exception {
        String groupName = "group1";
        String serviceName = "serviceName";
        String clusters = "clusters";
        
        //when
        clientProxy.unsubscribe(serviceName, groupName, clusters);
        // do nothing
    }
    
    @Test
    public void testIsSubscribed() throws NacosException {
        assertTrue(clientProxy.isSubscribed("serviceName", "group1", "clusters"));
    }
    
    @Test
    public void testReqApi() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenAnswer(invocationOnMock -> {
            //return url
            HttpRestResult<Object> res = new HttpRestResult<Object>();
            res.setData(invocationOnMock.getArgument(0));
            res.setCode(200);
            return res;
        });
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String api = "/api";
        Map<String, String> params = new HashMap<>();
        String method = HttpMethod.GET;
        //when
        String res = clientProxy.reqApi(api, params, method);
        //then
        assertEquals("http://localhost:8848/api", res);
        
    }
    
    @Test
    public void testReqApi2() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenAnswer(invocationOnMock -> {
            //return url
            HttpRestResult<Object> res = new HttpRestResult<Object>();
            res.setData(invocationOnMock.getArgument(0));
            res.setCode(200);
            return res;
        });
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String api = "/api";
        Map<String, String> params = new HashMap<>();
        Map<String, String> body = new HashMap<>();
        String method = HttpMethod.GET;
        //when
        String res = clientProxy.reqApi(api, params, body, method);
        //then
        assertEquals("http://localhost:8848/api", res);
    }
    
    @Test
    public void testReqApi3() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenAnswer(invocationOnMock -> {
            //return url
            HttpRestResult<Object> res = new HttpRestResult<Object>();
            res.setData(invocationOnMock.getArgument(0));
            res.setCode(200);
            return res;
        });
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String api = "/api";
        Map<String, String> params = new HashMap<>();
        Map<String, String> body = new HashMap<>();
        String method = HttpMethod.GET;
        List<String> servers = Arrays.asList("127.0.0.1");
        //when
        String res = clientProxy.reqApi(api, params, body, servers, method);
        //then
        assertEquals("http://127.0.0.1:8848/api", res);
    }
    
    @Test
    public void testCallServerFail() throws Exception {
        //then
        thrown.expect(NacosException.class);
        
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenAnswer(invocationOnMock -> {
            //return url
            HttpRestResult<Object> res = new HttpRestResult<Object>();
            res.setMessage("fail");
            res.setCode(400);
            return res;
        });
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String api = "/api";
        Map<String, String> params = new HashMap<>();
        Map<String, String> body = new HashMap<>();
        String method = HttpMethod.GET;
        String curServer = "127.0.0.1";
        //when
        clientProxy.callServer(api, params, body, curServer, method);
        
    }
    
    @Test
    public void testCallServerFail304() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenAnswer(invocationOnMock -> {
            //return url
            HttpRestResult<Object> res = new HttpRestResult<Object>();
            res.setMessage("redirect");
            res.setCode(304);
            return res;
        });
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String api = "/api";
        Map<String, String> params = new HashMap<>();
        Map<String, String> body = new HashMap<>();
        String method = HttpMethod.GET;
        String curServer = "127.0.0.1";
        //when
        String s = clientProxy.callServer(api, params, body, curServer, method);
        //then
        assertEquals("", s);
    }
    
    @Test
    public void testGetNamespaceId() {
        String namespaceId = "aaa";
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(props);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy(namespaceId, proxy, mgr, nacosClientProperties);
        String actualNamespaceId = clientProxy.getNamespaceId();
        assertEquals(namespaceId, actualNamespaceId);
    }
    
    @Test
    public void testSetServerPort() {
        clientProxy.setServerPort(1234);
        assertEquals(1234, ReflectUtils.getFieldValue(clientProxy, "serverPort"));
        System.setProperty(SystemPropertyKeyConst.NAMING_SERVER_PORT, "1111");
        clientProxy.setServerPort(1234);
        assertEquals(1111, ReflectUtils.getFieldValue(clientProxy, "serverPort"));
    }
    
    @Test(expected = NacosException.class)
    public void testReqApiForEmptyServer() throws NacosException {
        Map<String, String> params = new HashMap<>();
        clientProxy
                .reqApi("api", params, Collections.emptyMap(), Collections.emptyList(), HttpMethod.GET);
    }
    
    @Test(expected = NacosException.class)
    public void testRegApiForDomain() throws NacosException {
        Map<String, String> params = new HashMap<>();
        when(mgr.isDomain()).thenReturn(true);
        when(mgr.getNacosDomain()).thenReturn("http://test.nacos.domain");
        clientProxy
                .reqApi("api", params, Collections.emptyMap(), Collections.emptyList(), HttpMethod.GET);
        
    }
}
