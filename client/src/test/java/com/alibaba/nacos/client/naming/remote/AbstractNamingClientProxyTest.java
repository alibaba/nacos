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

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.pojo.Service;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.api.selector.AbstractSelector;
import com.alibaba.nacos.client.auth.ram.utils.SignUtil;
import com.alibaba.nacos.client.naming.event.ServerListChangedEvent;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.AppNameUtils;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AbstractNamingClientProxyTest {
    
    @Mock
    private SecurityProxy sc;
    
    /**
     * test get security headers for accessToken.
     */
    @Test
    public void testGetSecurityHeadersForAccessToken() {
        AbstractNamingClientProxy proxy = new MockNamingClientProxy(sc);
        String token = "aa";
        Map<String, String> keyMap = new HashMap<>();
        keyMap.put(Constants.ACCESS_TOKEN, token);
        when(sc.getIdentityContext(any(RequestResource.class))).thenReturn(keyMap);
        Map<String, String> securityHeaders = proxy.getSecurityHeaders("", "", "");
        Assert.assertEquals(2, securityHeaders.size());
        Assert.assertEquals(token, securityHeaders.get(Constants.ACCESS_TOKEN));
        Assert.assertEquals(AppNameUtils.getAppName(), securityHeaders.get("app"));
    }
    
    /**
     * get security headers for ram.
     *
     * @throws Exception exception
     */
    @Test
    public void testGetSecurityHeadersForRam() throws Exception {
        String ak = "aa";
        String sk = "bb";
        Map<String, String> mockIdentityContext = new HashMap<>();
        String serviceName = "aaa";
        mockIdentityContext.put("ak", ak);
        mockIdentityContext.put("data", System.currentTimeMillis() + "@@" + serviceName);
        mockIdentityContext.put("signature", SignUtil.sign(System.currentTimeMillis() + "@@" + serviceName, sk));
        when(sc.getIdentityContext(any(RequestResource.class))).thenReturn(mockIdentityContext);
        AbstractNamingClientProxy proxy = new MockNamingClientProxy(sc);
        Map<String, String> spasHeaders = proxy.getSecurityHeaders("", "", serviceName);
        Assert.assertEquals(4, spasHeaders.size());
        Assert.assertEquals(AppNameUtils.getAppName(), spasHeaders.get("app"));
        Assert.assertEquals(ak, spasHeaders.get("ak"));
        Assert.assertTrue(spasHeaders.get("data").endsWith("@@" + serviceName));
        String expectSign = SignUtil.sign(spasHeaders.get("data"), sk);
        Assert.assertEquals(expectSign, spasHeaders.get("signature"));
        
    }
    
    private class MockNamingClientProxy extends AbstractNamingClientProxy {
        
        protected MockNamingClientProxy(SecurityProxy securityProxy) {
            super(securityProxy);
        }
        
        @Override
        public void registerService(String serviceName, String groupName, Instance instance) throws NacosException {
        
        }
        
        @Override
        public void batchRegisterService(String serviceName, String groupName, List<Instance> instances)
                throws NacosException {
            
        }
        
        @Override
        public void batchDeregisterService(String serviceName, String groupName, List<Instance> instances)
                throws NacosException {
            
        }
        
        @Override
        public void deregisterService(String serviceName, String groupName, Instance instance) throws NacosException {
        
        }
        
        @Override
        public void updateInstance(String serviceName, String groupName, Instance instance) throws NacosException {
        
        }
        
        @Override
        public ServiceInfo queryInstancesOfService(String serviceName, String groupName, String clusters, int udpPort,
                boolean healthyOnly) throws NacosException {
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
        public ListView<String> getServiceList(int pageNo, int pageSize, String groupName, AbstractSelector selector)
                throws NacosException {
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
        public boolean isSubscribed(String serviceName, String groupName, String clusters) throws NacosException {
            return false;
        }
        
        @Override
        public boolean serverHealthy() {
            return false;
        }
        
        @Override
        public void shutdown() throws NacosException {
        
        }
        
        @Override
        public void onEvent(ServerListChangedEvent event) {
        
        }
        
        @Override
        public Class<? extends Event> subscribeType() {
            return null;
        }
    }
}
