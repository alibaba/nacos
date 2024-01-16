/*
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
 */

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.model.event.ConfigDataChangeEvent;
import com.alibaba.nacos.config.server.service.AggrWhitelist;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoBetaPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoTagPersistService;
import com.alibaba.nacos.persistence.configuration.DatasourceConfiguration;
import com.alibaba.nacos.sys.env.EnvUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ConfigPublishRequestHandlerTest {
    
    private ConfigPublishRequestHandler configPublishRequestHandler;
    
    @Mock
    ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    ConfigInfoBetaPersistService configInfoBetaPersistService;
    
    @Mock
    ConfigInfoTagPersistService configInfoTagPersistService;
    
    MockedStatic<AggrWhitelist> aggrWhitelistMockedStatic;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    @Before
    public void setUp() {
        aggrWhitelistMockedStatic = Mockito.mockStatic(AggrWhitelist.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        
        configPublishRequestHandler = new ConfigPublishRequestHandler(configInfoPersistService,
                configInfoTagPersistService, configInfoBetaPersistService);
        DatasourceConfiguration.setEmbeddedStorage(false);
        
    }
    
    @After
    public void after() {
        aggrWhitelistMockedStatic.close();
        envUtilMockedStatic.close();
    }
    
    /**
     * publish a not-exist config. expect : 1.response return true 2. publish ConfigDataChangeEvent
     *
     * @throws Exception exception.
     */
    @Test
    public void testNormalPublishConfigNotCas() throws Exception {
        String dataId = "testNormalPublishConfigNotCas";
        String group = "group";
        String tenant = "tenant";
        String content = "content";
        
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        configPublishRequest.setDataId(dataId);
        configPublishRequest.setGroup(group);
        configPublishRequest.setTenant(tenant);
        configPublishRequest.setContent(content);
        Map<String, String> keyMap = new HashMap<>();
        String srcUser = "src_user111";
        keyMap.put("src_user", srcUser);
        configPublishRequest.setAdditionMap(keyMap);
        
        RequestMeta requestMeta = new RequestMeta();
        String clientIp = "127.0.0.1";
        requestMeta.setClientIp(clientIp);
        
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                ConfigDataChangeEvent event1 = (ConfigDataChangeEvent) event;
                if (event1.dataId.equals(dataId)) {
                    reference.set((ConfigDataChangeEvent) event);
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        ConfigOperateResult configOperateResult = new ConfigOperateResult(true);
        long timestamp = System.currentTimeMillis();
        long id = timestamp / 1000;
        configOperateResult.setId(id);
        configOperateResult.setLastModified(timestamp);
        when(configInfoPersistService.insertOrUpdate(eq(requestMeta.getClientIp()), eq(srcUser), any(ConfigInfo.class),
                any(Map.class))).thenReturn(configOperateResult);
        ConfigPublishResponse response = configPublishRequestHandler.handle(configPublishRequest, requestMeta);
        
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        Thread.sleep(500L);
        Assert.assertTrue(reference.get() != null);
        Assert.assertEquals(dataId, reference.get().dataId);
        Assert.assertEquals(group, reference.get().group);
        Assert.assertEquals(tenant, reference.get().tenant);
        Assert.assertEquals(timestamp, reference.get().lastModifiedTs);
        Assert.assertFalse(reference.get().isBatch);
        Assert.assertFalse(reference.get().isBeta);
        
    }
    
    /**
     * publish a exist config.
     *
     * @throws Exception exception.
     */
    @Test
    public void testNormalPublishConfigCas() throws Exception {
        String dataId = "testNormalPublishConfigCas";
        String group = "group";
        String tenant = "tenant";
        String content = "content";
        
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        configPublishRequest.setDataId(dataId);
        configPublishRequest.setGroup(group);
        configPublishRequest.setTenant(tenant);
        configPublishRequest.setContent(content);
        configPublishRequest.setCasMd5("12314532");
        Map<String, String> keyMap = new HashMap<>();
        String srcUser = "src_user111";
        keyMap.put("src_user", srcUser);
        configPublishRequest.setAdditionMap(keyMap);
        
        RequestMeta requestMeta = new RequestMeta();
        String clientIp = "127.0.0.1";
        requestMeta.setClientIp(clientIp);
        
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                ConfigDataChangeEvent event1 = (ConfigDataChangeEvent) event;
                if (event1.dataId.equals(dataId)) {
                    reference.set((ConfigDataChangeEvent) event);
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        ConfigOperateResult configOperateResult = new ConfigOperateResult(true);
        long timestamp = System.currentTimeMillis();
        long id = timestamp / 1000;
        configOperateResult.setId(id);
        configOperateResult.setLastModified(timestamp);
        when(configInfoPersistService.insertOrUpdateCas(eq(requestMeta.getClientIp()), eq(srcUser),
                any(ConfigInfo.class), any(Map.class))).thenReturn(configOperateResult);
        ConfigPublishResponse response = configPublishRequestHandler.handle(configPublishRequest, requestMeta);
        
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        Thread.sleep(500L);
        Assert.assertTrue(reference.get() != null);
        Assert.assertEquals(dataId, reference.get().dataId);
        Assert.assertEquals(group, reference.get().group);
        Assert.assertEquals(tenant, reference.get().tenant);
        Assert.assertEquals(timestamp, reference.get().lastModifiedTs);
        Assert.assertFalse(reference.get().isBatch);
        Assert.assertFalse(reference.get().isBeta);
    }
    
    /**
     * publish a exist config.
     *
     * @throws Exception exception.
     */
    @Test
    public void testNormalPublishConfigCasError() throws Exception {
        String dataId = "testNormalPublishConfigCasError";
        String group = "group";
        String tenant = "tenant";
        String content = "content";
        
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        configPublishRequest.setDataId(dataId);
        configPublishRequest.setGroup(group);
        configPublishRequest.setTenant(tenant);
        configPublishRequest.setContent(content);
        configPublishRequest.setCasMd5("12314532");
        Map<String, String> keyMap = new HashMap<>();
        String srcUser = "src_user111";
        keyMap.put("src_user", srcUser);
        configPublishRequest.setAdditionMap(keyMap);
        
        RequestMeta requestMeta = new RequestMeta();
        String clientIp = "127.0.0.1";
        requestMeta.setClientIp(clientIp);
        
        ConfigInfoStateWrapper configInfoStateWrapper = new ConfigInfoStateWrapper();
        configInfoStateWrapper.setId(12345678);
        long timeStamp = System.currentTimeMillis();
        configInfoStateWrapper.setLastModified(timeStamp);
        
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                ConfigDataChangeEvent event1 = (ConfigDataChangeEvent) event;
                if (event1.dataId.equals(dataId)) {
                    reference.set((ConfigDataChangeEvent) event);
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        ConfigOperateResult configOperateResult = new ConfigOperateResult(true);
        long timestamp = System.currentTimeMillis();
        long id = timestamp / 1000;
        configOperateResult.setId(id);
        configOperateResult.setLastModified(timestamp);
        when(configInfoPersistService.insertOrUpdateCas(eq(requestMeta.getClientIp()), eq(srcUser),
                any(ConfigInfo.class), any(Map.class))).thenThrow(new NacosRuntimeException(502, "mock error"));
        ConfigPublishResponse response = configPublishRequestHandler.handle(configPublishRequest, requestMeta);
        
        Assert.assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        Assert.assertTrue(response.getMessage().contains("mock error"));
        Thread.sleep(500L);
        Assert.assertTrue(reference.get() == null);
        
    }
    
    @Test
    public void testPublishAggrCheckFail() throws NacosException, InterruptedException {
        
        RequestMeta requestMeta = new RequestMeta();
        String clientIp = "127.0.0.1";
        requestMeta.setClientIp(clientIp);
        
        String dataId = "testPublishAggrCheckFail";
        String group = "group";
        String tenant = "tenant";
        String content = "content";
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        configPublishRequest.setDataId(dataId);
        configPublishRequest.setGroup(group);
        configPublishRequest.setTenant(tenant);
        configPublishRequest.setContent(content);
        when(AggrWhitelist.isAggrDataId(eq(dataId))).thenReturn(Boolean.TRUE);
        
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                ConfigDataChangeEvent event1 = (ConfigDataChangeEvent) event;
                if (event1.dataId.equals(dataId)) {
                    reference.set((ConfigDataChangeEvent) event);
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        ConfigPublishResponse response = configPublishRequestHandler.handle(configPublishRequest, requestMeta);
        
        Assert.assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        Assert.assertTrue(response.getMessage().contains("is aggr"));
        Thread.sleep(500L);
        Assert.assertTrue(reference.get() == null);
    }
    
    @Test
    public void testBetaPublishNotCas() throws NacosException, InterruptedException {
        String dataId = "testBetaPublish";
        String group = "group";
        String tenant = "tenant";
        String content = "content";
        
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        configPublishRequest.setDataId(dataId);
        configPublishRequest.setGroup(group);
        configPublishRequest.setTenant(tenant);
        configPublishRequest.setContent(content);
        Map<String, String> keyMap = new HashMap<>();
        String srcUser = "src_user111";
        keyMap.put("src_user", srcUser);
        String betaIps = "127.0.0.1,127.0.0.2";
        keyMap.put("betaIps", betaIps);
        configPublishRequest.setAdditionMap(keyMap);
        
        RequestMeta requestMeta = new RequestMeta();
        String clientIp = "127.0.0.1";
        requestMeta.setClientIp(clientIp);
        
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                ConfigDataChangeEvent event1 = (ConfigDataChangeEvent) event;
                if (event1.dataId.equals(dataId)) {
                    reference.set((ConfigDataChangeEvent) event);
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        ConfigOperateResult configOperateResult = new ConfigOperateResult(true);
        long timestamp = System.currentTimeMillis();
        long id = timestamp / 1000;
        configOperateResult.setId(id);
        configOperateResult.setLastModified(timestamp);
        when(configInfoBetaPersistService.insertOrUpdateBeta(any(ConfigInfo.class), eq(betaIps),
                eq(requestMeta.getClientIp()), eq(srcUser))).thenReturn(configOperateResult);
        ConfigPublishResponse response = configPublishRequestHandler.handle(configPublishRequest, requestMeta);
        
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        Thread.sleep(500L);
        Assert.assertTrue(reference.get() != null);
        Assert.assertEquals(dataId, reference.get().dataId);
        Assert.assertEquals(group, reference.get().group);
        Assert.assertEquals(tenant, reference.get().tenant);
        Assert.assertEquals(timestamp, reference.get().lastModifiedTs);
        Assert.assertFalse(reference.get().isBatch);
        Assert.assertTrue(reference.get().isBeta);
        
    }
    
    @Test
    public void testBetaPublishCas() throws NacosException, InterruptedException {
        String dataId = "testBetaPublishCas";
        String group = "group";
        String tenant = "tenant";
        String content = "content";
        
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        configPublishRequest.setDataId(dataId);
        configPublishRequest.setGroup(group);
        configPublishRequest.setTenant(tenant);
        configPublishRequest.setContent(content);
        configPublishRequest.setCasMd5("12314532");
        Map<String, String> keyMap = new HashMap<>();
        String srcUser = "src_user111";
        keyMap.put("src_user", srcUser);
        String betaIps = "127.0.0.1,127.0.0.2";
        keyMap.put("betaIps", betaIps);
        configPublishRequest.setAdditionMap(keyMap);
        
        RequestMeta requestMeta = new RequestMeta();
        String clientIp = "127.0.0.1";
        requestMeta.setClientIp(clientIp);
        
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                ConfigDataChangeEvent event1 = (ConfigDataChangeEvent) event;
                if (event1.dataId.equals(dataId)) {
                    reference.set((ConfigDataChangeEvent) event);
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        ConfigOperateResult configOperateResult = new ConfigOperateResult(true);
        long timestamp = System.currentTimeMillis();
        long id = timestamp / 1000;
        configOperateResult.setId(id);
        configOperateResult.setLastModified(timestamp);
        when(configInfoBetaPersistService.insertOrUpdateBetaCas(any(ConfigInfo.class), eq(betaIps),
                eq(requestMeta.getClientIp()), eq(srcUser))).thenReturn(configOperateResult);
        ConfigPublishResponse response = configPublishRequestHandler.handle(configPublishRequest, requestMeta);
        
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        Thread.sleep(500L);
        Assert.assertTrue(reference.get() != null);
        Assert.assertEquals(dataId, reference.get().dataId);
        Assert.assertEquals(group, reference.get().group);
        Assert.assertEquals(tenant, reference.get().tenant);
        Assert.assertEquals(timestamp, reference.get().lastModifiedTs);
        Assert.assertFalse(reference.get().isBatch);
        Assert.assertTrue(reference.get().isBeta);
        
    }
    
    @Test
    public void testTagPublishNotCas() throws NacosException, InterruptedException {
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        String dataId = "testTagPublishNotCas";
        
        configPublishRequest.setDataId(dataId);
        String group = "group";
        
        configPublishRequest.setGroup(group);
        String tenant = "tenant";
        
        configPublishRequest.setTenant(tenant);
        
        Map<String, String> keyMap = new HashMap<>();
        String srcUser = "src_user111";
        keyMap.put("src_user", srcUser);
        String tag = "testTag";
        keyMap.put("tag", tag);
        configPublishRequest.setAdditionMap(keyMap);
        String content = "content";
        configPublishRequest.setContent(content);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                ConfigDataChangeEvent event1 = (ConfigDataChangeEvent) event;
                if (event1.dataId.equals(dataId)) {
                    reference.set((ConfigDataChangeEvent) event);
                }
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        ConfigOperateResult configOperateResult = new ConfigOperateResult(true);
        long timestamp = System.currentTimeMillis();
        long id = timestamp / 1000;
        configOperateResult.setId(id);
        configOperateResult.setLastModified(timestamp);
        when(configInfoTagPersistService.insertOrUpdateTag(any(ConfigInfo.class), eq(tag),
                eq(requestMeta.getClientIp()), eq(srcUser))).thenReturn(configOperateResult);
        
        ConfigPublishResponse response = configPublishRequestHandler.handle(configPublishRequest, requestMeta);
        
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        Thread.sleep(500L);
        Assert.assertTrue(reference.get() != null);
        Assert.assertEquals(dataId, reference.get().dataId);
        Assert.assertEquals(group, reference.get().group);
        Assert.assertEquals(tenant, reference.get().tenant);
        Assert.assertEquals(timestamp, reference.get().lastModifiedTs);
        Assert.assertFalse(reference.get().isBatch);
        Assert.assertFalse(reference.get().isBeta);
        Assert.assertEquals(tag, reference.get().tag);
        
    }
    
    @Test
    public void testTagPublishCas() throws NacosException, InterruptedException {
        String dataId = "testTagPublishCas";
        String group = "group";
        ConfigPublishRequest configPublishRequest = new ConfigPublishRequest();
        configPublishRequest.setDataId(dataId);
        configPublishRequest.setGroup(group);
        configPublishRequest.setCasMd5("casmd512");
        Map<String, String> keyMap = new HashMap<>();
        String srcUser = "src_user111";
        keyMap.put("src_user", srcUser);
        String tag = "testTag";
        keyMap.put("tag", tag);
        configPublishRequest.setAdditionMap(keyMap);
        String tenant = "tenant";
        configPublishRequest.setTenant(tenant);
        String content = "content";
        configPublishRequest.setContent(content);
        RequestMeta requestMeta = new RequestMeta();
        requestMeta.setClientIp("127.0.0.1");
        
        AtomicReference<ConfigDataChangeEvent> reference = new AtomicReference<>();
        
        NotifyCenter.registerSubscriber(new Subscriber() {
            
            @Override
            public void onEvent(Event event) {
                reference.set((ConfigDataChangeEvent) event);
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConfigDataChangeEvent.class;
            }
        });
        
        ConfigOperateResult configOperateResult = new ConfigOperateResult(true);
        long timestamp = System.currentTimeMillis();
        long id = timestamp / 1000;
        configOperateResult.setId(id);
        configOperateResult.setLastModified(timestamp);
        when(configInfoTagPersistService.insertOrUpdateTagCas(any(ConfigInfo.class), eq(tag),
                eq(requestMeta.getClientIp()), eq(srcUser))).thenReturn(configOperateResult);
        
        ConfigPublishResponse response = configPublishRequestHandler.handle(configPublishRequest, requestMeta);
        
        Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        Thread.sleep(500L);
        Assert.assertTrue(reference.get() != null);
        Assert.assertEquals(dataId, reference.get().dataId);
        Assert.assertEquals(group, reference.get().group);
        Assert.assertEquals(tenant, reference.get().tenant);
        Assert.assertEquals(timestamp, reference.get().lastModifiedTs);
        Assert.assertFalse(reference.get().isBatch);
        Assert.assertFalse(reference.get().isBeta);
        Assert.assertEquals(tag, reference.get().tag);
        
    }
    
}