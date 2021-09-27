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
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.api.selector.ExpressionSelector;
import com.alibaba.nacos.api.selector.NoneSelector;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientProxy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class NacosNamingMaintainServiceTest {
    
    private NacosNamingMaintainService nacosNamingMaintainService;
    
    private NamingHttpClientProxy serverProxy;
    
    @Before
    public void setUp() throws Exception {
        Properties prop = new Properties();
        prop.setProperty(PropertyKeyConst.NAMESPACE, "public");
        nacosNamingMaintainService = new NacosNamingMaintainService(prop);
        serverProxy = mock(NamingHttpClientProxy.class);
        Field serverProxyField = NacosNamingMaintainService.class.getDeclaredField("serverProxy");
        serverProxyField.setAccessible(true);
        serverProxyField.set(nacosNamingMaintainService, serverProxy);
    }
    
    @After
    public void tearDown() throws Exception {
    }
    
    @Test
    public void testConstructor() throws NacosException {
        NacosNamingMaintainService client = new NacosNamingMaintainService("localhost");
        Assert.assertNotNull(client);
    }
    
    @Test
    public void testUpdateInstance1() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        Instance instance = new Instance();
        //when
        nacosNamingMaintainService.updateInstance(serviceName, groupName, instance);
        //then
        verify(serverProxy, times(1)).updateInstance(serviceName, groupName, instance);
    }
    
    @Test
    public void testUpdateInstance2() throws NacosException {
        //given
        String serviceName = "service1";
        Instance instance = new Instance();
        //when
        nacosNamingMaintainService.updateInstance(serviceName, instance);
        //then
        verify(serverProxy, times(1)).updateInstance(serviceName, Constants.DEFAULT_GROUP, instance);
    }
    
    @Test
    public void testQueryService1() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        //when
        nacosNamingMaintainService.queryService(serviceName, groupName);
        //then
        verify(serverProxy, times(1)).queryService(serviceName, groupName);
    }
    
    @Test
    public void testQueryService2() throws NacosException {
        //given
        String serviceName = "service1";
        Instance instance = new Instance();
        //when
        nacosNamingMaintainService.queryService(serviceName);
        //then
        verify(serverProxy, times(1)).queryService(serviceName, Constants.DEFAULT_GROUP);
    }
    
    @Test
    public void testCreateService1() throws NacosException {
        //given
        String serviceName = "service1";
        //when
        nacosNamingMaintainService.createService(serviceName);
        //then
        verify(serverProxy, times(1)).createService(argThat(new ArgumentMatcher<Service>() {
            @Override
            public boolean matches(Service service) {
                return service.getName().equals(serviceName) && service.getGroupName().equals(Constants.DEFAULT_GROUP)
                        && Math.abs(service.getProtectThreshold() - Constants.DEFAULT_PROTECT_THRESHOLD) < 0.1f
                        && service.getMetadata().size() == 0;
            }
        }), argThat(o -> o instanceof NoneSelector));
    }
    
    @Test
    public void testCreateService2() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "groupName";
        //when
        nacosNamingMaintainService.createService(serviceName, groupName);
        //then
        verify(serverProxy, times(1)).createService(argThat(new ArgumentMatcher<Service>() {
            @Override
            public boolean matches(Service service) {
                return service.getName().equals(serviceName) && service.getGroupName().equals(groupName)
                        && Math.abs(service.getProtectThreshold() - Constants.DEFAULT_PROTECT_THRESHOLD) < 0.1f
                        && service.getMetadata().size() == 0;
            }
        }), argThat(o -> o instanceof NoneSelector));
    }
    
    @Test
    public void testCreateService3() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "groupName";
        float protectThreshold = 0.1f;
        //when
        nacosNamingMaintainService.createService(serviceName, groupName, protectThreshold);
        //then
        verify(serverProxy, times(1)).createService(argThat(new ArgumentMatcher<Service>() {
            @Override
            public boolean matches(Service service) {
                return service.getName().equals(serviceName) && service.getGroupName().equals(groupName)
                        && Math.abs(service.getProtectThreshold() - protectThreshold) < 0.1f
                        && service.getMetadata().size() == 0;
            }
        }), argThat(o -> o instanceof NoneSelector));
    }
    
    @Test
    public void testCreateService5() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "groupName";
        float protectThreshold = 0.1f;
        String expression = "k=v";
        //when
        nacosNamingMaintainService.createService(serviceName, groupName, protectThreshold, expression);
        //then
        verify(serverProxy, times(1)).createService(argThat(new ArgumentMatcher<Service>() {
            @Override
            public boolean matches(Service service) {
                return service.getName().equals(serviceName) && service.getGroupName().equals(groupName)
                        && Math.abs(service.getProtectThreshold() - protectThreshold) < 0.1f
                        && service.getMetadata().size() == 0;
            }
        }), argThat(o -> ((ExpressionSelector) o).getExpression().equals(expression)));
    }
    
    @Test
    public void testCreateService4() throws NacosException {
        //given
        Service service = new Service();
        AbstractSelector selector = new NoneSelector();
        //when
        nacosNamingMaintainService.createService(service, selector);
        //then
        verify(serverProxy, times(1)).createService(service, selector);
    }
    
    @Test
    public void testDeleteService1() throws NacosException {
        //given
        String serviceName = "service1";
        //when
        nacosNamingMaintainService.deleteService(serviceName);
        //then
        verify(serverProxy, times(1)).deleteService(serviceName, Constants.DEFAULT_GROUP);
    }
    
    @Test
    public void testDeleteService2() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "groupName";
        //when
        nacosNamingMaintainService.deleteService(serviceName, groupName);
        //then
        verify(serverProxy, times(1)).deleteService(serviceName, groupName);
    }
    
    @Test
    public void testUpdateService1() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "groupName";
        float protectThreshold = 0.1f;
        
        //when
        nacosNamingMaintainService.updateService(serviceName, groupName, protectThreshold);
        //then
        verify(serverProxy, times(1)).updateService(argThat(new ArgumentMatcher<Service>() {
            @Override
            public boolean matches(Service service) {
                return service.getName().equals(serviceName) && service.getGroupName().equals(groupName)
                        && Math.abs(service.getProtectThreshold() - protectThreshold) < 0.1f;
            }
        }), argThat(o -> o instanceof NoneSelector));
    }
    
    @Test
    public void testUpdateService2() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "groupName";
        float protectThreshold = 0.1f;
        Map<String, String> meta = new HashMap<>();
        meta.put("k", "v");
        
        //when
        nacosNamingMaintainService.updateService(serviceName, groupName, protectThreshold, meta);
        //then
        verify(serverProxy, times(1)).updateService(argThat(new ArgumentMatcher<Service>() {
            @Override
            public boolean matches(Service service) {
                return service.getName().equals(serviceName) && service.getGroupName().equals(groupName)
                        && Math.abs(service.getProtectThreshold() - protectThreshold) < 0.1f
                        && service.getMetadata().size() == 1;
            }
        }), argThat(o -> o instanceof NoneSelector));
    }
    
    @Test
    public void testUpdateService3() throws NacosException {
        //given
        Service service = new Service();
        AbstractSelector selector = new NoneSelector();
        //when
        nacosNamingMaintainService.updateService(service, selector);
        //then
        verify(serverProxy, times(1)).updateService(service, selector);
    }
    
    @Test
    public void testShutDown() throws NacosException {
        nacosNamingMaintainService.shutDown();
        //then
        verify(serverProxy, times(1)).shutdown();
    }
}