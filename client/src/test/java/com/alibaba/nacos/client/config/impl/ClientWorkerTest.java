/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class ClientWorkerTest {
    
    @Test
    public void testConstruct() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ServerListManager agent = Mockito.mock(ServerListManager.class);
        ClientWorker clientWorker = new ClientWorker(filter, agent, prop);
        Assert.assertNotNull(clientWorker);
    }
    
    @Test
    public void testAddListenerWithoutTenant() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ServerListManager agent = Mockito.mock(ServerListManager.class);
        ClientWorker clientWorker = new ClientWorker(filter, agent, prop);
        String dataId = "a";
        String group = "b";
        
        Listener listener = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
            }
        };
        
        clientWorker.addListeners(dataId, group, Arrays.asList(listener));
        List<Listener> listeners = clientWorker.getCache(dataId, group).getListeners();
        Assert.assertEquals(1, listeners.size());
        Assert.assertEquals(listener, listeners.get(0));
        
        clientWorker.removeListener(dataId, group, listener);
        listeners = clientWorker.getCache(dataId, group).getListeners();
        Assert.assertEquals(0, listeners.size());
        
        CacheData cacheData = clientWorker.addCacheDataIfAbsent(dataId, group);
        Assert.assertEquals(cacheData, clientWorker.getCache(dataId, group));
    }
    
    @Test
    public void testListenerWithTenant() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ServerListManager agent = Mockito.mock(ServerListManager.class);
        ClientWorker clientWorker = new ClientWorker(filter, agent, prop);
        
        Listener listener = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
            }
        };
        
        String dataId = "a";
        String group = "b";
        
        clientWorker.addTenantListeners(dataId, group, Arrays.asList(listener));
        List<Listener> listeners = clientWorker.getCache(dataId, group).getListeners();
        Assert.assertEquals(1, listeners.size());
        Assert.assertEquals(listener, listeners.get(0));
        
        clientWorker.removeTenantListener(dataId, group, listener);
        listeners = clientWorker.getCache(dataId, group).getListeners();
        Assert.assertEquals(0, listeners.size());
        
        String content = "d";
        clientWorker.addTenantListenersWithContent(dataId, group, content, Arrays.asList(listener));
        listeners = clientWorker.getCache(dataId, group).getListeners();
        Assert.assertEquals(1, listeners.size());
        Assert.assertEquals(listener, listeners.get(0));
        
        clientWorker.removeTenantListener(dataId, group, listener);
        listeners = clientWorker.getCache(dataId, group).getListeners();
        Assert.assertEquals(0, listeners.size());
        
        String tenant = "c";
        CacheData cacheData = clientWorker.addCacheDataIfAbsent(dataId, group, tenant);
        Assert.assertEquals(cacheData, clientWorker.getCache(dataId, group, tenant));
        
        clientWorker.removeCache(dataId, group, tenant);
        Assert.assertNull(clientWorker.getCache(dataId, group, tenant));
        
    }
    
    @Test
    public void testPublishConfig() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ServerListManager agent = Mockito.mock(ServerListManager.class);
        ClientWorker clientWorker = new ClientWorker(filter, agent, prop);
        ClientWorker.ConfigRpcTransportClient mockClient = Mockito.mock(ClientWorker.ConfigRpcTransportClient.class);
        
        String dataId = "a";
        String group = "b";
        String tenant = "c";
        String content = "d";
        
        String appName = "app";
        String tag = "tag";
        
        String betaIps = "1.1.1.1";
        String casMd5 = "1111";
        
        String type = "properties";
        
        boolean b = clientWorker
                .publishConfig(dataId, group, tenant, appName, tag, betaIps, content, null, casMd5, type);
        Assert.assertFalse(b);
        try {
            clientWorker.removeConfig(dataId, group, tenant, tag);
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("Client not connected,current status:STARTING", e.getErrMsg());
            Assert.assertEquals(-401, e.getErrCode());
            
        }
        try {
            clientWorker.getServerConfig(dataId, group, tenant, 100, false);
            Assert.fail();
        } catch (NacosException e) {
            Assert.assertEquals("Client not connected,current status:STARTING", e.getErrMsg());
            Assert.assertEquals(-401, e.getErrCode());
        }
    }
    
    @Test
    public void testShutdown() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ServerListManager agent = Mockito.mock(ServerListManager.class);
        ClientWorker clientWorker = new ClientWorker(filter, agent, prop);
        String dataId = "a";
        String group = "b";
        String tenant = "c";
        String content = "d";
        clientWorker.shutdown();
        
        Field agent1 = ClientWorker.class.getDeclaredField("agent");
        agent1.setAccessible(true);
        ConfigTransportClient o = (ConfigTransportClient) agent1.get(clientWorker);
        Assert.assertTrue(o.executor.isShutdown());
        agent1.setAccessible(false);
        
        Assert.assertTrue(clientWorker.isHealthServer());
        Assert.assertEquals("config_rpc_client", clientWorker.getAgentName());
    }
    
}
