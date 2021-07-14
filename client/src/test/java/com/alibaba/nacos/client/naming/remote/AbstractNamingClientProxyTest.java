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

package com.alibaba.nacos.client.naming.remote;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.client.naming.event.ServerListChangedEvent;
import com.alibaba.nacos.client.naming.utils.SignUtil;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.AppNameUtils;
import com.alibaba.nacos.common.notify.Event;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class AbstractNamingClientProxyTest {
    
    @Test
    public void testGetSecurityHeaders() {
        SecurityProxy sc = Mockito.mock(SecurityProxy.class);
        Properties props = new Properties();
        AbstractNamingClientProxy proxy = new AbstractNamingClientProxy(sc, props) {
            @Override
            public void onEvent(ServerListChangedEvent event) {
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ServerListChangedEvent.class;
            }
            
            @Override
            public void registerService(String serviceName, String groupName, Instance instance) throws NacosException {
            
            }
            
            @Override
            public void deregisterService(String serviceName, String groupName, Instance instance)
                    throws NacosException {
                
            }
            
            @Override
            public void updateInstance(String serviceName, String groupName, Instance instance) throws NacosException {
            
            }
            
            @Override
            public ServiceInfo queryInstancesOfService(String serviceName, String groupName, String clusters,
                    int udpPort, boolean healthyOnly) throws NacosException {
                return null;
            }
            
            @Override
            public Service queryService(String serviceName, String groupName) throws NacosException {
                return null;
            }
            
            @Override
            public void createService(Service service, AbstractSelector selector) throws NacosException {
            
            }
            
            @Override
            public boolean deleteService(String serviceName, String groupName) throws NacosException {
                return false;
            }
            
            @Override
            public void updateService(Service service, AbstractSelector selector) throws NacosException {
            
            }
            
            @Override
            public ListView<String> getServiceList(int pageNo, int pageSize, String groupName,
                    AbstractSelector selector) throws NacosException {
                return null;
            }
            
            @Override
            public ServiceInfo subscribe(String serviceName, String groupName, String clusters) throws NacosException {
                return null;
            }
            
            @Override
            public void unsubscribe(String serviceName, String groupName, String clusters) throws NacosException {
            
            }
            
            @Override
            public void updateBeatInfo(Set<Instance> modifiedInstances) {
            
            }
            
            @Override
            public boolean serverHealthy() {
                return false;
            }
            
            @Override
            public void shutdown() throws NacosException {
            
            }
        };
        String token = "aa";
        Mockito.when(sc.getAccessToken()).thenReturn(token);
        Map<String, String> securityHeaders = proxy.getSecurityHeaders();
        Assert.assertEquals(1, securityHeaders.size());
        Assert.assertEquals(token, securityHeaders.get(Constants.ACCESS_TOKEN));
        
    }
    
    @Test
    public void testGetSpasHeaders() throws Exception {
        SecurityProxy sc = Mockito.mock(SecurityProxy.class);
        Properties props = new Properties();
        String ak = "aa";
        String sk = "bb";
        props.put(PropertyKeyConst.ACCESS_KEY, ak);
        props.put(PropertyKeyConst.SECRET_KEY, sk);
        AbstractNamingClientProxy proxy = new AbstractNamingClientProxy(sc, props) {
            @Override
            public void onEvent(ServerListChangedEvent event) {
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ServerListChangedEvent.class;
            }
            
            @Override
            public void registerService(String serviceName, String groupName, Instance instance) throws NacosException {
            
            }
            
            @Override
            public void deregisterService(String serviceName, String groupName, Instance instance)
                    throws NacosException {
                
            }
            
            @Override
            public void updateInstance(String serviceName, String groupName, Instance instance) throws NacosException {
            
            }
            
            @Override
            public ServiceInfo queryInstancesOfService(String serviceName, String groupName, String clusters,
                    int udpPort, boolean healthyOnly) throws NacosException {
                return null;
            }
            
            @Override
            public Service queryService(String serviceName, String groupName) throws NacosException {
                return null;
            }
            
            @Override
            public void createService(Service service, AbstractSelector selector) throws NacosException {
            
            }
            
            @Override
            public boolean deleteService(String serviceName, String groupName) throws NacosException {
                return false;
            }
            
            @Override
            public void updateService(Service service, AbstractSelector selector) throws NacosException {
            
            }
            
            @Override
            public ListView<String> getServiceList(int pageNo, int pageSize, String groupName,
                    AbstractSelector selector) throws NacosException {
                return null;
            }
            
            @Override
            public ServiceInfo subscribe(String serviceName, String groupName, String clusters) throws NacosException {
                return null;
            }
            
            @Override
            public void unsubscribe(String serviceName, String groupName, String clusters) throws NacosException {
            
            }
            
            @Override
            public void updateBeatInfo(Set<Instance> modifiedInstances) {
            
            }
            
            @Override
            public boolean serverHealthy() {
                return false;
            }
            
            @Override
            public void shutdown() throws NacosException {
            
            }
        };
        String serviceName = "aaa";
        Map<String, String> spasHeaders = proxy.getSpasHeaders(serviceName);
        Assert.assertEquals(4, spasHeaders.size());
        Assert.assertEquals(AppNameUtils.getAppName(), spasHeaders.get("app"));
        Assert.assertEquals(ak, spasHeaders.get("ak"));
        Assert.assertTrue(spasHeaders.get("data").endsWith("@@" + serviceName));
        
        String expectSign = SignUtil.sign(spasHeaders.get("data"), sk);
        Assert.assertEquals(expectSign, spasHeaders.get("signature"));
        
    }
}