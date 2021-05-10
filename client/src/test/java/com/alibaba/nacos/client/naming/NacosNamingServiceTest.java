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

package com.alibaba.nacos.client.naming;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.client.naming.remote.NamingClientProxy;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientProxy;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class NacosNamingServiceTest {
    
    private NacosNamingService client;
    
    private NamingClientProxy proxy;
    
    @Before
    public void before() throws NoSuchFieldException, NacosException, IllegalAccessException {
        Properties prop = new Properties();
        prop.put(PropertyKeyConst.NAMESPACE, "test");
        client = new NacosNamingService(prop);
        proxy = mock(NamingHttpClientProxy.class);
        Field serverProxyField = NacosNamingService.class.getDeclaredField("clientProxy");
        serverProxyField.setAccessible(true);
        serverProxyField.set(client, proxy);
    }
    
    @Test
    public void testRegisterInstance1() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        //when
        client.registerInstance(serviceName, groupName, instance);
        //then
        verify(proxy, times(1)).registerService(serviceName, groupName, instance);
    }
    
    @Test
    public void testRegisterInstance2() {
    }
    
    @Test
    public void testTestRegisterInstance2() {
    }
    
    @Test
    public void testTestRegisterInstance3() {
    }
    
    @Test
    public void testTestRegisterInstance4() {
    }
    
    @Test
    public void testDeregisterInstance() {
    }
    
    @Test
    public void testTestDeregisterInstance() {
    }
    
    @Test
    public void testTestDeregisterInstance1() {
    }
    
    @Test
    public void testTestDeregisterInstance2() {
    }
    
    @Test
    public void testTestDeregisterInstance3() {
    }
    
    @Test
    public void testTestDeregisterInstance4() {
    }
    
    @Test
    public void testGetAllInstances() {
    }
    
    @Test
    public void testTestGetAllInstances() {
    }
    
    @Test
    public void testTestGetAllInstances1() {
    }
    
    @Test
    public void testTestGetAllInstances2() {
    }
    
    @Test
    public void testTestGetAllInstances3() {
    }
    
    @Test
    public void testTestGetAllInstances4() {
    }
    
    @Test
    public void testTestGetAllInstances5() {
    }
    
    @Test
    public void testTestGetAllInstances6() {
    }
    
    @Test
    public void testSelectInstances() {
    }
    
    @Test
    public void testTestSelectInstances() {
    }
    
    @Test
    public void testTestSelectInstances1() {
    }
    
    @Test
    public void testTestSelectInstances2() {
    }
    
    @Test
    public void testTestSelectInstances3() {
    }
    
    @Test
    public void testTestSelectInstances4() {
    }
    
    @Test
    public void testTestSelectInstances5() {
    }
    
    @Test
    public void testTestSelectInstances6() {
    }
    
    @Test
    public void testSelectOneHealthyInstance() {
    }
    
    @Test
    public void testTestSelectOneHealthyInstance() {
    }
    
    @Test
    public void testTestSelectOneHealthyInstance1() {
    }
    
    @Test
    public void testTestSelectOneHealthyInstance2() {
    }
    
    @Test
    public void testTestSelectOneHealthyInstance3() {
    }
    
    @Test
    public void testTestSelectOneHealthyInstance4() {
    }
    
    @Test
    public void testTestSelectOneHealthyInstance5() {
    }
    
    @Test
    public void testTestSelectOneHealthyInstance6() {
    }
    
    @Test
    public void testSubscribe() {
    }
    
    @Test
    public void testTestSubscribe() {
    }
    
    @Test
    public void testTestSubscribe1() {
    }
    
    @Test
    public void testTestSubscribe2() {
    }
    
    @Test
    public void testUnsubscribe() {
    }
    
    @Test
    public void testTestUnsubscribe() {
    }
    
    @Test
    public void testTestUnsubscribe1() {
    }
    
    @Test
    public void testTestUnsubscribe2() {
    }
    
    @Test
    public void testGetServicesOfServer() {
    }
    
    @Test
    public void testTestGetServicesOfServer() {
    }
    
    @Test
    public void testTestGetServicesOfServer1() {
    }
    
    @Test
    public void testTestGetServicesOfServer2() {
    }
    
    @Test
    public void testGetSubscribeServices() {
    }
    
    @Test
    public void testGetServerStatus() {
    }
    
    @Test
    public void testShutDown() {
    }
}