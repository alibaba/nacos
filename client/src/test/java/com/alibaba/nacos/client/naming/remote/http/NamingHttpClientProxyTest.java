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

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.core.ServerListManager;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.utils.HttpMethod;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NamingHttpClientProxyTest {
    
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
        verify(nacosRestTemplate, times(1)).exchangeForm(any(), any(), any(), any(), eq(HttpMethod.POST), any());
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
    
    public void testQueryService() {
    }
    
    public void testCreateService() {
    }
    
    public void testDeleteService() {
    }
    
    public void testUpdateService() {
    }
    
    public void testSendBeat() {
    }
    
    public void testServerHealthy() {
    }
    
    public void testGetServiceList() {
    }
    
    public void testSubscribe() {
    }
    
    public void testUnsubscribe() {
    }
    
    public void testUpdateBeatInfo() {
    }
    
    public void testReqApi() {
    }
    
    public void testTestReqApi() {
    }
    
    public void testTestReqApi1() {
    }
    
    public void testCallServer() {
    }
    
    public void testGetNamespaceId() {
    }
    
    public void testSetServerPort() {
    }
    
    public void testGetBeatReactor() {
    }
    
    public void testShutdown() {
    }
}