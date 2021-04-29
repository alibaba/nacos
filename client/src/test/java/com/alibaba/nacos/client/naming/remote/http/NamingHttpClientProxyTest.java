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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.client.naming.beat.BeatInfo;
import com.alibaba.nacos.client.naming.beat.BeatReactor;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.core.ServerListManager;
import com.alibaba.nacos.client.naming.utils.UtilAndComs;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.utils.HttpMethod;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NamingHttpClientProxyTest {
    
    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    
    @Test
    public void testRegisterService() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("127.0.0.1:8848");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, props, holder);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        //when
        clientProxy.registerService(serviceName, groupName, instance);
        //then
        verify(nacosRestTemplate, times(1)).exchangeForm(any(), any(), any(), any(), any(), any());
    }
    
    @Test
    public void testDeregisterService() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("127.0.0.1:8848");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, props, holder);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        //when
        clientProxy.deregisterService(serviceName, groupName, instance);
        //then
        verify(nacosRestTemplate, times(1)).exchangeForm(any(), any(), any(), any(), eq(HttpMethod.DELETE), any());
    }
    
    @Test
    public void testUpdateInstance() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("127.0.0.1:8848");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, props, holder);
        
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
    public void testQueryInstancesOfService() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, props, holder);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        
        String serviceName = "service1";
        String groupName = "group1";
        String clusters = "cluster1";
        //when
        ServiceInfo serviceInfo = clientProxy.queryInstancesOfService(serviceName, groupName, clusters, 0, false);
        //then
        verify(nacosRestTemplate, times(1)).exchangeForm(any(), any(), any(), any(), eq(HttpMethod.GET), any());
        Assert.assertEquals(groupName + "@@" + serviceName, serviceInfo.getName());
        Assert.assertEquals(clusters, serviceInfo.getClusters());
    }
    
    @Test
    public void testQueryService() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("{\"name\":\"service1\",\"groupName\":\"group1\"}");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, props, holder);
        
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
        Assert.assertEquals(serviceName, service.getName());
        Assert.assertEquals(groupName, service.getGroupName());
    }
    
    @Test
    public void testCreateService() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, props, holder);
        
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
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, props, holder);
        
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
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, props, holder);
        
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
    public void testSendBeat() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, props, holder);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        
        BeatInfo beat = new BeatInfo();
        
        //when
        clientProxy.sendBeat(beat, true);
        //then
        verify(nacosRestTemplate, times(1))
                .exchangeForm(endsWith("/instance/beat"), any(), any(), any(), eq(HttpMethod.PUT), any());
    }
    
    @Test
    public void testServerHealthy() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("{\"status\":\"UP\"}");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, props, holder);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String serviceName = "service1";
        String groupName = "group1";
        
        //when
        boolean serverHealthy = clientProxy.serverHealthy();
        //then
        verify(nacosRestTemplate, times(1))
                .exchangeForm(endsWith("/operator/metrics"), any(), any(), any(), eq(HttpMethod.GET), any());
        Assert.assertTrue(serverHealthy);
    }
    
    @Test
    public void testGetServiceList() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("{\"count\":2,\"doms\":[\"aaa\",\"bbb\"]}");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, props, holder);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String groupName = "group1";
        
        //when
        ListView<String> serviceList = clientProxy.getServiceList(1, 10, groupName, new NoneSelector());
        //then
        verify(nacosRestTemplate, times(1))
                .exchangeForm(endsWith("/service/list"), any(), any(), any(), eq(HttpMethod.GET), any());
        Assert.assertEquals(2, serviceList.getCount());
        Assert.assertEquals("aaa", serviceList.getData().get(0));
        Assert.assertEquals("bbb", serviceList.getData().get(1));
    }
    
    @Test
    public void testSubscribe() throws Exception {
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, props, holder);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String groupName = "group1";
        String serviceName = "serviceName";
        String clusters = "clusters";
        
        //when
        ServiceInfo serviceInfo = clientProxy.subscribe(serviceName, groupName, clusters);
        //then
        verify(nacosRestTemplate, times(1))
                .exchangeForm(endsWith("/instance/list"), any(), any(), any(), eq(HttpMethod.GET), any());
        Assert.assertEquals(groupName + "@@" + serviceName, serviceInfo.getName());
        Assert.assertEquals(clusters, serviceInfo.getClusters());
    }
    
    @Test
    public void testUnsubscribe() throws Exception {
        //TODO thrown.expect(UnsupportedOperationException.class);
        //given
        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        HttpRestResult<Object> a = new HttpRestResult<Object>();
        a.setData("");
        a.setCode(200);
        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, props, holder);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String groupName = "group1";
        String serviceName = "serviceName";
        String clusters = "clusters";
        
        //when
        clientProxy.unsubscribe(serviceName, groupName, clusters);
    }
    
    @Test
    public void testUpdateBeatInfo() throws Exception {
        //given
        BeatReactor mockBeatReactor = mock(BeatReactor.class);
        Field dom2Beat = BeatReactor.class.getDeclaredField("dom2Beat");
        ConcurrentHashMap<String, BeatInfo> beatMap = new ConcurrentHashMap<>();
        String beatServiceName = "service1#127.0.0.1#10000";
        beatMap.put(beatServiceName, new BeatInfo());
        dom2Beat.setAccessible(true);
        dom2Beat.set(mockBeatReactor, beatMap);
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, props, holder);
        
        final Field mockBeatReactorField = NamingHttpClientProxy.class.getDeclaredField("beatReactor");
        mockBeatReactorField.setAccessible(true);
        mockBeatReactorField.set(clientProxy, mockBeatReactor);
        
        Instance instance = new Instance();
        instance.setInstanceId("id1");
        instance.setIp("127.0.0.1");
        instance.setPort(10000);
        instance.setServiceName("service1");
        instance.setClusterName("cluster1");
        Set<Instance> beats = new HashSet<>();
        beats.add(instance);
        when(mockBeatReactor.buildKey("service1", "127.0.0.1", 10000)).thenReturn(beatServiceName);
        
        //when
        clientProxy.updateBeatInfo(beats);
        //then
        verify(mockBeatReactor, times(1)).addBeatInfo(eq("service1"), any());
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
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, new Properties(),
                holder);
        
        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        nacosRestTemplateField.setAccessible(true);
        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        String api = "/api";
        Map<String, String> params = new HashMap<>();
        String method = HttpMethod.GET;
        //when
        String res = clientProxy.reqApi(api, params, method);
        //then
        Assert.assertEquals("http://localhost:8848/api", res);
        
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
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, new Properties(),
                holder);
        
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
        Assert.assertEquals("http://localhost:8848/api", res);
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
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, new Properties(),
                holder);
        
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
        Assert.assertEquals("http://127.0.0.1:8848/api", res);
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
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, new Properties(),
                holder);
        
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
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, new Properties(),
                holder);
        
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
        Assert.assertEquals("", s);
    }
    
    @Test
    public void testGetNamespaceId() {
        //given
        //        NacosRestTemplate nacosRestTemplate = mock(NacosRestTemplate.class);
        //        HttpRestResult<Object> a = new HttpRestResult<Object>();
        //        a.setData("");
        //        a.setCode(200);
        //        when(nacosRestTemplate.exchangeForm(any(), any(), any(), any(), any(), any())).thenReturn(a);
        
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        String namespaceId = "aaa";
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy(namespaceId, proxy, mgr, props, holder);
        
        //        final Field nacosRestTemplateField = NamingHttpClientProxy.class.getDeclaredField("nacosRestTemplate");
        //        nacosRestTemplateField.setAccessible(true);
        //        nacosRestTemplateField.set(clientProxy, nacosRestTemplate);
        
        //when
        String actualNamespaceId = clientProxy.getNamespaceId();
        Assert.assertEquals(namespaceId, actualNamespaceId);
    }
    
    @Test
    public void testSetServerPort() {
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        String namespaceId = "aaa";
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy(namespaceId, proxy, mgr, props, holder);
        
        //when
        clientProxy.setServerPort(1234);
    }
    
    @Test
    public void testGetBeatReactor() throws Exception {
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        String namespaceId = "aaa";
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy(namespaceId, proxy, mgr, props, holder);
        
        BeatReactor mockBeatReactor = mock(BeatReactor.class);
        final Field mockBeatReactorField = NamingHttpClientProxy.class.getDeclaredField("beatReactor");
        mockBeatReactorField.setAccessible(true);
        mockBeatReactorField.set(clientProxy, mockBeatReactor);
        
        //when
        BeatReactor beatReactor = clientProxy.getBeatReactor();
        //then
        Assert.assertEquals(mockBeatReactor, beatReactor);
    }
    
    @Test
    public void testShutdown() throws Exception {
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        when(mgr.getServerList()).thenReturn(Arrays.asList("localhost"));
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        String namespaceId = "aaa";
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy(namespaceId, proxy, mgr, props, holder);
        
        BeatReactor mockBeatReactor = mock(BeatReactor.class);
        final Field mockBeatReactorField = NamingHttpClientProxy.class.getDeclaredField("beatReactor");
        mockBeatReactorField.setAccessible(true);
        mockBeatReactorField.set(clientProxy, mockBeatReactor);
        
        //when
        clientProxy.shutdown();
        //then
        verify(mockBeatReactor, times(1)).shutdown();
    }
}