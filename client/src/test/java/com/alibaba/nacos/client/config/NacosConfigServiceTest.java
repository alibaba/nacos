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

package com.alibaba.nacos.client.config;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Executor;

public class NacosConfigServiceTest {
    
    private NacosConfigService nacosConfigService;
    
    private ClientWorker mockWoker;
    
    private void setFinal(Field field, Object ins, Object newValue) throws Exception {
        field.setAccessible(true);
        field.set(ins, newValue);
        field.setAccessible(false);
        
    }
    
    @Before
    public void mock() throws Exception {
        final Properties properties = new Properties();
        properties.put("serverAddr", "1.1.1.1");
        nacosConfigService = new NacosConfigService(properties);
        mockWoker = Mockito.mock(ClientWorker.class);
        setFinal(NacosConfigService.class.getDeclaredField("worker"), nacosConfigService, mockWoker);
    }
    
    @After
    public void clean() {
        LocalConfigInfoProcessor.cleanAllSnapshot();
    }
    
    @Test
    public void testGetConfig() throws NacosException {
        final String dataId = "1";
        final String group = "2";
        final String tenant = "";
        final int timeout = 3000;
        ConfigResponse response = new ConfigResponse();
        response.setContent("aa");
        response.setConfigType("bb");
        Mockito.when(mockWoker.getServerConfig(dataId, group, "", timeout, false)).thenReturn(response);
        final String config = nacosConfigService.getConfig(dataId, group, timeout);
        Assert.assertEquals("aa", config);
        Mockito.verify(mockWoker, Mockito.times(1)).getServerConfig(dataId, group, tenant, timeout, false);
        
    }
    
    @Test
    public void testGetConfigAndSignListener() throws NacosException {
        final String dataId = "1";
        final String group = "2";
        final String tenant = "";
        final String content = "123";
        final int timeout = 3000;
        final Listener listener = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }
            
            @Override
            public void receiveConfigInfo(String configInfo) {
            
            }
        };
        
        ConfigResponse response = new ConfigResponse();
        response.setContent(content);
        response.setConfigType("bb");
        Mockito.when(mockWoker.getServerConfig(dataId, group, "", timeout, false)).thenReturn(response);
        
        final String config = nacosConfigService.getConfigAndSignListener(dataId, group, timeout, listener);
        Assert.assertEquals(content, config);
        
        Mockito.verify(mockWoker, Mockito.times(1)).getServerConfig(dataId, group, tenant, timeout, false);
        Mockito.verify(mockWoker, Mockito.times(1))
                .addTenantListenersWithContent(dataId, group, content, Arrays.asList(listener));
        
    }
    
    @Test
    public void testAddListener() throws NacosException {
        String dataId = "1";
        String group = "2";
        Listener listener = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }
            
            @Override
            public void receiveConfigInfo(String configInfo) {
            
            }
        };
        
        nacosConfigService.addListener(dataId, group, listener);
        Mockito.verify(mockWoker, Mockito.times(1)).addTenantListeners(dataId, group, Arrays.asList(listener));
    }
    
    @Test
    public void testPublishConfig() throws NacosException {
        String dataId = "1";
        String group = "2";
        String content = "123";
        String namespace = "";
        String type = ConfigType.getDefaultType().getType();
        Mockito.when(mockWoker.publishConfig(dataId, group, namespace, null, null, null, content, null, null, type))
                .thenReturn(true);
        
        final boolean b = nacosConfigService.publishConfig(dataId, group, content);
        Assert.assertTrue(b);
        
        Mockito.verify(mockWoker, Mockito.times(1))
                .publishConfig(dataId, group, namespace, null, null, null, content, null, null, type);
    }
    
    @Test
    public void testPublishConfig2() throws NacosException {
        String dataId = "1";
        String group = "2";
        String content = "123";
        String namespace = "";
        String type = ConfigType.PROPERTIES.getType();
        
        Mockito.when(mockWoker.publishConfig(dataId, group, namespace, null, null, null, content, null, null, type))
                .thenReturn(true);
        
        final boolean b = nacosConfigService.publishConfig(dataId, group, content, type);
        Assert.assertTrue(b);
        
        Mockito.verify(mockWoker, Mockito.times(1))
                .publishConfig(dataId, group, namespace, null, null, null, content, null, null, type);
    }
    
    @Test
    public void testPublishConfigCas() throws NacosException {
        String dataId = "1";
        String group = "2";
        String content = "123";
        String namespace = "";
        String casMd5 = "96147704e3cb8be8597d55d75d244a02";
        String type = ConfigType.getDefaultType().getType();
        
        Mockito.when(mockWoker.publishConfig(dataId, group, namespace, null, null, null, content, null, casMd5, type))
                .thenReturn(true);
        
        final boolean b = nacosConfigService.publishConfigCas(dataId, group, content, casMd5);
        Assert.assertTrue(b);
        
        Mockito.verify(mockWoker, Mockito.times(1))
                .publishConfig(dataId, group, namespace, null, null, null, content, null, casMd5, type);
    }
    
    @Test
    public void testPublishConfigCas2() throws NacosException {
        String dataId = "1";
        String group = "2";
        String content = "123";
        String namespace = "";
        String casMd5 = "96147704e3cb8be8597d55d75d244a02";
        String type = ConfigType.PROPERTIES.getType();
        
        Mockito.when(mockWoker.publishConfig(dataId, group, namespace, null, null, null, content, null, casMd5, type))
                .thenReturn(true);
        
        final boolean b = nacosConfigService.publishConfigCas(dataId, group, content, casMd5, type);
        Assert.assertTrue(b);
        
        Mockito.verify(mockWoker, Mockito.times(1))
                .publishConfig(dataId, group, namespace, null, null, null, content, null, casMd5, type);
    }
    
    @Test
    public void testRemoveConfig() throws NacosException {
        String dataId = "1";
        String group = "2";
        String tenant = "";
        
        Mockito.when(mockWoker.removeConfig(dataId, group, tenant, null)).thenReturn(true);
        
        final boolean b = nacosConfigService.removeConfig(dataId, group);
        Assert.assertTrue(b);
        
        Mockito.verify(mockWoker, Mockito.times(1)).removeConfig(dataId, group, tenant, null);
    }
    
    @Test
    public void testRemoveListener() {
        String dataId = "1";
        String group = "2";
        Listener listener = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }
            
            @Override
            public void receiveConfigInfo(String configInfo) {
            
            }
        };
        
        nacosConfigService.removeListener(dataId, group, listener);
        Mockito.verify(mockWoker, Mockito.times(1)).removeTenantListener(dataId, group, listener);
    }
    
    @Test
    public void testGetServerStatus() {
        Mockito.when(mockWoker.isHealthServer()).thenReturn(true);
        Assert.assertEquals("UP", nacosConfigService.getServerStatus());
        Mockito.verify(mockWoker, Mockito.times(1)).isHealthServer();
        
        Mockito.when(mockWoker.isHealthServer()).thenReturn(false);
        Assert.assertEquals("DOWN", nacosConfigService.getServerStatus());
        Mockito.verify(mockWoker, Mockito.times(2)).isHealthServer();
        
    }
    
    @Test
    public void testShutDown() {
        try {
            nacosConfigService.shutDown();
        } catch (Exception e) {
            Assert.fail();
        }
    }
}