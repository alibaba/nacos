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
import com.alibaba.nacos.client.naming.cache.ServiceInfoHolder;
import com.alibaba.nacos.client.naming.core.ServerListManager;
import com.alibaba.nacos.client.security.SecurityProxy;
import org.junit.Test;

import java.util.Properties;

import static org.mockito.Mockito.mock;

public class NamingHttpClientProxyTest {
    
    @Test
    public void testRegisterService() throws NacosException {
        //given
        SecurityProxy proxy = mock(SecurityProxy.class);
        ServerListManager mgr = mock(ServerListManager.class);
        Properties props = new Properties();
        ServiceInfoHolder holder = mock(ServiceInfoHolder.class);
        NamingHttpClientProxy clientProxy = new NamingHttpClientProxy("namespaceId", proxy, mgr, props, holder);
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        //when
        clientProxy.registerService(serviceName, groupName, instance);
        //then
    }
    
    public void testDeregisterService() {
    }
    
    public void testUpdateInstance() {
    }
    
    public void testQueryInstancesOfService() {
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