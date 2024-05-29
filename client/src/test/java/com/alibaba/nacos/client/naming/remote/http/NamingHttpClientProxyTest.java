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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
// todo  remove strictness lenient
@MockitoSettings(strictness = Strictness.LENIENT)
class NamingHttpClientProxyTest {
    
    @Mock
    private SecurityProxy proxy;
    
    @Mock
    private ServerListManager mgr;
    
    private Properties props;
    
    private NamingHttpClientProxy clientProxy;
    
    @BeforeEach
    void setUp() {
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        props = new Properties();
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(props);
        clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, nacosClientProperties);
    }
    
    @AfterEach
    void tearDown() throws NacosException {
        clientProxy.shutdown();
        System.clearProperty(SystemPropertyKeyConst.NAMING_SERVER_PORT);
    }
    
    @Test
    void testOnEvent() {
        clientProxy.onEvent(new ServerListChangedEvent());
        // Do nothing
    }
    
    @Test
    void testSubscribeType() {
        assertEquals(ServerListChangedEvent.class, clientProxy.subscribeType());
    }
    
    @Test
    void testRegisterService() throws Exception {
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
    
    @Test
    void testRegisterEphemeralInstance() throws NacosException {
        assertThrows(UnsupportedOperationException.class, () -> {
            Instance instance = new Instance();
            clientProxy.registerService("a", "b", instance);
        });
    }
    
    @Test
    void testRegisterServiceThrowsNacosException() throws Exception {
        Throwable exception = assertThrows(NacosException.class, () -> {
            
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
                assertNull(ex.getCause());
                
                throw ex;
            }
        });
        assertTrue(exception.getMessage().contains("failed to req API"));
    }
    
    @Test
    void testRegisterServiceThrowsException() throws Exception {
        assertThrows(NacosException.class, () -> {
            
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
                assertTrue(ex.getErrMsg().contains("java.lang.NullPointerException"));
                assertEquals(NacosException.SERVER_ERROR, ex.getErrCode());
                
                throw ex;
            }
        });
    }
    
    @Test
    void testBatchRegisterService() {
        assertThrows(UnsupportedOperationException.class, () -> {
            clientProxy.batchRegisterService("a", "b", null);
        });
    }
    
    @Test
    void testBatchDeregisterService() {
        assertThrows(UnsupportedOperationException.class, () -> {
            clientProxy.batchDeregisterService("a", "b", null);
        });
    }
    
    @Test
    void testDeregisterService() throws Exception {
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
    void testDeregisterServiceForEphemeral() throws Exception {
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        Instance instance = new Instance();
        clientProxy.deregisterService("serviceName", "groupName", instance);
        verify(nacosRestTemplate, never()).exchangeForm(any(), any(), any(), any(), eq(HttpMethod.DELETE), any());
        
    }
    
    @Test
    void testUpdateInstance() throws Exception {
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
    void testQueryInstancesOfServiceThrowsException() {
        //assert exception
        String serviceName = "service1";
        String groupName = "group1";
        String clusters = "cluster1";
        assertThrows(UnsupportedOperationException.class,
                () -> clientProxy.queryInstancesOfService(serviceName, groupName, clusters, false));
    }
    
    @Test
    void testQueryService() throws Exception {
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
        verify(nacosRestTemplate, times(1)).exchangeForm(endsWith(UtilAndComs.nacosUrlService), any(), any(), any(),
                eq(HttpMethod.GET), any());
        assertEquals(serviceName, service.getName());
        assertEquals(groupName, service.getGroupName());
    }
    
    @Test
    void testCreateService() throws Exception {
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
        verify(nacosRestTemplate, times(1)).exchangeForm(endsWith(UtilAndComs.nacosUrlService), any(), any(), any(),
                eq(HttpMethod.POST), any());
    }
    
    @Test
    void testDeleteService() throws Exception {
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
        verify(nacosRestTemplate, times(1)).exchangeForm(endsWith(UtilAndComs.nacosUrlService), any(), any(), any(),
                eq(HttpMethod.DELETE), any());
    }
    
    @Test
    void testUpdateService() throws Exception {
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
        verify(nacosRestTemplate, times(1)).exchangeForm(endsWith(UtilAndComs.nacosUrlService), any(), any(), any(),
                eq(HttpMethod.PUT), any());
        
    }
    
    @Test
    void testServerHealthy() throws Exception {
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
        verify(nacosRestTemplate, times(1)).exchangeForm(endsWith("/operator/metrics"), any(), any(), any(),
                eq(HttpMethod.GET), any());
        assertTrue(serverHealthy);
    }
    
    @Test
    void testServerHealthyForException() throws Exception {
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenThrow(
                new RuntimeException("test"));
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        assertFalse(clientProxy.serverHealthy());
    }
    
    @Test
    void testGetServiceList() throws Exception {
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
        verify(nacosRestTemplate, times(1)).exchangeForm(endsWith("/service/list"), any(), any(), any(),
                eq(HttpMethod.GET), any());
        assertEquals(2, serviceList.getCount());
        assertEquals("aaa", serviceList.getData().get(0));
        assertEquals("bbb", serviceList.getData().get(1));
    }
    
    @Test
    void testGetServiceListWithLabelSelector() throws Exception {
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
        verify(nacosRestTemplate, times(1)).exchangeForm(endsWith("/service/list"), any(), any(), any(),
                eq(HttpMethod.GET), any());
        assertEquals(2, serviceList.getCount());
        assertEquals("aaa", serviceList.getData().get(0));
        assertEquals("bbb", serviceList.getData().get(1));
    }
    
    @Test
    void testSubscribe() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> {
            String groupName = "group1";
            String serviceName = "serviceName";
            String clusters = "clusters";
            
            //when
            clientProxy.subscribe(serviceName, groupName, clusters);
        });
    }
    
    @Test
    void testUnsubscribe() throws Exception {
        String groupName = "group1";
        String serviceName = "serviceName";
        String clusters = "clusters";
        
        //when
        clientProxy.unsubscribe(serviceName, groupName, clusters);
        // do nothing
    }
    
    @Test
    void testIsSubscribed() throws NacosException {
        assertTrue(clientProxy.isSubscribed("serviceName", "group1", "clusters"));
    }
    
    @Test
    void testReqApi() throws Exception {
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
    void testReqApi2() throws Exception {
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
    void testReqApi3() throws Exception {
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
    void testCallServerFail() throws Exception {
        assertThrows(NacosException.class, () -> {
            
            //given
            NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
            
            when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenAnswer(
                    invocationOnMock -> {
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
            
        });
        
    }
    
    @Test
    void testCallServerFail304() throws Exception {
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
    void testGetNamespaceId() {
        String namespaceId = "aaa";
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(props);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy(namespaceId, proxy, mgr, nacosClientProperties);
        String actualNamespaceId = clientProxy.getNamespaceId();
        assertEquals(namespaceId, actualNamespaceId);
    }
    
    @Test
    void testSetServerPort() {
        clientProxy.setServerPort(1234);
        assertEquals(1234, ReflectUtils.getFieldValue(clientProxy, "serverPort"));
        System.setProperty(SystemPropertyKeyConst.NAMING_SERVER_PORT, "1111");
        clientProxy.setServerPort(1234);
        assertEquals(1111, ReflectUtils.getFieldValue(clientProxy, "serverPort"));
    }
    
    @Test
    void testReqApiForEmptyServer() throws NacosException {
        assertThrows(NacosException.class, () -> {
            Map<String, String> params = new HashMap<>();
            clientProxy.reqApi("api", params, Collections.emptyMap(), Collections.emptyList(), HttpMethod.GET);
        });
    }
    
    @Test
    void testRegApiForDomain() throws NacosException {
        assertThrows(NacosException.class, () -> {
            Map<String, String> params = new HashMap<>();
            when(mgr.isDomain()).thenReturn(true);
            when(mgr.getNacosDomain()).thenReturn("http://test.nacos.domain");
            clientProxy.reqApi("api", params, Collections.emptyMap(), Collections.emptyList(), HttpMethod.GET);
            
        });
        
    }
}
