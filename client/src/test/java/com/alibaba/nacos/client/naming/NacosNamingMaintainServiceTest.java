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
import com.alibaba.nacos.client.naming.core.NamingServerListManager;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientProxy;
import com.alibaba.nacos.client.security.SecurityProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class NacosNamingMaintainServiceTest {
    
    private NacosNamingMaintainService nacosNamingMaintainService;
    
    private NamingHttpClientProxy serverProxy;
    
    private NamingServerListManager serverListManager;
    
    private SecurityProxy securityProxy;
    
    private ScheduledExecutorService executorService;
    
    @BeforeEach
    void setUp() throws Exception {
        Properties prop = new Properties();
        prop.setProperty(PropertyKeyConst.NAMESPACE, "public");
        prop.setProperty("serverAddr", "localhost");
        
        nacosNamingMaintainService = new NacosNamingMaintainService(prop);
        serverProxy = mock(NamingHttpClientProxy.class);
        serverListManager = mock(NamingServerListManager.class);
        securityProxy = mock(SecurityProxy.class);
        executorService = mock(ScheduledExecutorService.class);
        
        Field serverProxyField = NacosNamingMaintainService.class.getDeclaredField("serverProxy");
        serverProxyField.setAccessible(true);
        serverProxyField.set(nacosNamingMaintainService, serverProxy);
        Field serverListManagerField = NacosNamingMaintainService.class.getDeclaredField("serverListManager");
        serverListManagerField.setAccessible(true);
        serverListManagerField.set(nacosNamingMaintainService, serverListManager);
        Field securityProxyFiled = NacosNamingMaintainService.class.getDeclaredField("securityProxy");
        securityProxyFiled.setAccessible(true);
        securityProxyFiled.set(nacosNamingMaintainService, securityProxy);
        Field executorServiceField = NacosNamingMaintainService.class.getDeclaredField("executorService");
        executorServiceField.setAccessible(true);
        executorServiceField.set(nacosNamingMaintainService, executorService);
    }
    
    @AfterEach
    void tearDown() throws Exception {
    }
    
    @Test
    void testConstructor() throws NacosException {
        NacosNamingMaintainService client = new NacosNamingMaintainService("localhost");
        assertNotNull(client);
    }
    
    @Test
    void testUpdateInstance1() throws NacosException {
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
    void testUpdateInstance2() throws NacosException {
        //given
        String serviceName = "service1";
        Instance instance = new Instance();
        //when
        nacosNamingMaintainService.updateInstance(serviceName, instance);
        //then
        verify(serverProxy, times(1)).updateInstance(serviceName, Constants.DEFAULT_GROUP, instance);
    }
    
    @Test
    void testQueryService1() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "group1";
        //when
        nacosNamingMaintainService.queryService(serviceName, groupName);
        //then
        verify(serverProxy, times(1)).queryService(serviceName, groupName);
    }
    
    @Test
    void testQueryService2() throws NacosException {
        //given
        String serviceName = "service1";
        Instance instance = new Instance();
        //when
        nacosNamingMaintainService.queryService(serviceName);
        //then
        verify(serverProxy, times(1)).queryService(serviceName, Constants.DEFAULT_GROUP);
    }
    
    @Test
    void testCreateService1() throws NacosException {
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
    void testCreateService2() throws NacosException {
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
    void testCreateService3() throws NacosException {
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
    void testCreateService5() throws NacosException {
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
    void testCreateService4() throws NacosException {
        //given
        Service service = new Service();
        AbstractSelector selector = new NoneSelector();
        //when
        nacosNamingMaintainService.createService(service, selector);
        //then
        verify(serverProxy, times(1)).createService(service, selector);
    }
    
    @Test
    void testDeleteService1() throws NacosException {
        //given
        String serviceName = "service1";
        //when
        nacosNamingMaintainService.deleteService(serviceName);
        //then
        verify(serverProxy, times(1)).deleteService(serviceName, Constants.DEFAULT_GROUP);
    }
    
    @Test
    void testDeleteService2() throws NacosException {
        //given
        String serviceName = "service1";
        String groupName = "groupName";
        //when
        nacosNamingMaintainService.deleteService(serviceName, groupName);
        //then
        verify(serverProxy, times(1)).deleteService(serviceName, groupName);
    }
    
    @Test
    void testUpdateService1() throws NacosException {
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
    void testUpdateService2() throws NacosException {
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
    void testUpdateService3() throws NacosException {
        //given
        Service service = new Service();
        AbstractSelector selector = new NoneSelector();
        //when
        nacosNamingMaintainService.updateService(service, selector);
        //then
        verify(serverProxy, times(1)).updateService(service, selector);
    }
    
    @Test
    void testShutDown() throws NacosException {
        //when
        nacosNamingMaintainService.shutDown();
        //then
        verify(serverProxy, times(1)).shutdown();
        verify(serverListManager, times(1)).shutdown();
        verify(executorService, times(1)).shutdown();
    }
}