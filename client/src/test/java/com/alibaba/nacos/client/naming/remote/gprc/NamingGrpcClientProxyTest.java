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

package com.alibaba.nacos.client.naming.remote.gprc;


import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.InstanceRequest;
import com.alibaba.nacos.api.naming.remote.response.InstanceResponse;
import com.alibaba.nacos.api.naming.remote.response.QueryServiceResponse;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.ServerListFactory;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentMatcher;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NamingGrpcClientProxyTest {
    
    @Rule
    public final ExpectedException thrown = ExpectedException.none();
    
    @Test
    public void testRegisterService() throws NacosException, NoSuchFieldException, IllegalAccessException {
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setIp("1.1.1.1");
        instance.setPort(1111);
        // inject rpcClient;
        RpcClient rpc = mock(RpcClient.class);
        Field rpcClient = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClient.setAccessible(true);
        rpcClient.set(client, rpc);
        
        Response res = new InstanceResponse();
        when(rpc.request(any())).thenReturn(res);
        
        // when
        client.registerService(serviceName, groupName, instance);
        
        // then
        verify(rpc, times(1)).request(argThat(new ArgumentMatcher<Request>() {
            @Override
            public boolean matches(Request request) {
                if (request instanceof InstanceRequest) {
                    InstanceRequest request1 = (InstanceRequest) request;
                    return request1.getType().equals(NamingRemoteConstants.REGISTER_INSTANCE);
                }
                return false;
            }
        }));
        
    }
    
    @Test
    public void testDeregisterService() throws NacosException, NoSuchFieldException, IllegalAccessException {
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setIp("1.1.1.1");
        instance.setPort(1111);
        // inject rpcClient;
        RpcClient rpc = mock(RpcClient.class);
        Field rpcClient = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClient.setAccessible(true);
        rpcClient.set(client, rpc);
        
        Response res = new InstanceResponse();
        when(rpc.request(any())).thenReturn(res);
        
        // when
        client.deregisterService(serviceName, groupName, instance);
        
        // then
        verify(rpc, times(1)).request(argThat(new ArgumentMatcher<Request>() {
            @Override
            public boolean matches(Request request) {
                if (request instanceof InstanceRequest) {
                    InstanceRequest request1 = (InstanceRequest) request;
                    return request1.getType().equals(NamingRemoteConstants.DE_REGISTER_INSTANCE);
                }
                return false;
            }
        }));
    }
    
    @Test
    public void testUpdateInstance() throws Exception {
        //TODO thrown.expect(UnsupportedOperationException.class);
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setIp("1.1.1.1");
        instance.setPort(1111);
        
        client.updateInstance(serviceName, groupName, instance);
    }
    
    @Test
    public void testQueryInstancesOfService() throws Exception {
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        String serviceName = "service1";
        String groupName = "group1";
        String clusters = "cluster1";
        
        Instance instance = new Instance();
        instance.setServiceName(serviceName);
        instance.setIp("1.1.1.1");
        instance.setPort(1111);
        // inject rpcClient;
        RpcClient rpc = mock(RpcClient.class);
        Field rpcClient = NamingGrpcClientProxy.class.getDeclaredField("rpcClient");
        rpcClient.setAccessible(true);
        rpcClient.set(client, rpc);
        
        QueryServiceResponse res = new QueryServiceResponse();
        ServiceInfo info = new ServiceInfo(groupName + "@@" + serviceName + "@@" + clusters);
        res.setServiceInfo(info);
        when(rpc.request(any())).thenReturn(res);
        
        // when
        ServiceInfo actual = client.queryInstancesOfService(serviceName, groupName, clusters, 0, false);
        
        Assert.assertEquals(info, actual);
    }
    
    @Test
    public void testQueryService() throws Exception {
        // given
        String namespaceId = "ns1";
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListFactory factory = mock(ServerListFactory.class);
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        Properties prop = new Properties();
        NamingGrpcClientProxy client = new NamingGrpcClientProxy(namespaceId, proxy, factory, prop, holder);
        
        String serviceName = "service1";
        String groupName = "group1";
        // when
        Service service = client.queryService(serviceName, groupName);
        // then
        Assert.assertNull(service);
    }
    
    @Test
    public void testCreateService() {
    }
    
    @Test
    public void testDeleteService() {
    }
    
    @Test
    public void testUpdateService() {
    }
    
    @Test
    public void testGetServiceList() {
    }
    
    @Test
    public void testSubscribe() {
    }
    
    @Test
    public void testUnsubscribe() {
    }
    
    @Test
    public void testUpdateBeatInfo() {
    }
    
    @Test
    public void testServerHealthy() {
    }
    
    @Test
    public void testShutdown() {
    }
    
    @Test
    public void testIsEnable() {
    }
}