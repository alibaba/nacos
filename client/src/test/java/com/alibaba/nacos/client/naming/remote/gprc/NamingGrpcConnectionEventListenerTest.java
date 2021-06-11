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
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class NamingGrpcConnectionEventListenerTest {
    
    @Test
    public void testCacheInstanceForRedo() throws NacosException {
        //given
        NamingGrpcClientProxy proxy = mock(NamingGrpcClientProxy.class);
        NamingGrpcConnectionEventListener listener = new NamingGrpcConnectionEventListener(proxy);
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        listener.cacheInstanceForRedo(serviceName, groupName, instance);
        //when
        listener.onConnected();
        //then
        verify(proxy, times(1)).registerService(serviceName, groupName, instance);
    }
    
    @Test
    public void testRemoveInstanceForRedo() throws NacosException {
        //given
        NamingGrpcClientProxy proxy = mock(NamingGrpcClientProxy.class);
        NamingGrpcConnectionEventListener listener = new NamingGrpcConnectionEventListener(proxy);
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        listener.cacheInstanceForRedo(serviceName, groupName, instance);
        listener.removeInstanceForRedo(serviceName, groupName, instance);
        //when
        listener.onConnected();
        //then
        verify(proxy, times(0)).registerService(serviceName, groupName, instance);
    }
    
    @Test
    public void testCacheSubscriberForRedo() throws NacosException {
        //given
        NamingGrpcClientProxy proxy = mock(NamingGrpcClientProxy.class);
        NamingGrpcConnectionEventListener listener = new NamingGrpcConnectionEventListener(proxy);
        String fullServiceName = "group1@@service1";
        String cluster = "cluster1";
        listener.cacheSubscriberForRedo(fullServiceName, cluster);
        //when
        listener.onConnected();
        //then
        ServiceInfo info = ServiceInfo.fromKey(fullServiceName);
        verify(proxy, times(1)).subscribe(info.getName(), info.getGroupName(), cluster);
    }
    
    @Test
    public void testRemoveSubscriberForRedo() throws NacosException {
        //given
        NamingGrpcClientProxy proxy = mock(NamingGrpcClientProxy.class);
        NamingGrpcConnectionEventListener listener = new NamingGrpcConnectionEventListener(proxy);
        String fullServiceName = "group1@@service1";
        String cluster = "cluster1";
        listener.cacheSubscriberForRedo(fullServiceName, cluster);
        listener.removeSubscriberForRedo(fullServiceName, cluster);
        //when
        listener.onConnected();
        //then
        ServiceInfo info = ServiceInfo.fromKey(fullServiceName);
        verify(proxy, times(0)).subscribe(info.getName(), info.getGroupName(), cluster);
    }
}