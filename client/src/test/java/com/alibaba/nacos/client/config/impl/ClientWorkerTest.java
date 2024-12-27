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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.config.remote.request.ClientConfigMetricRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigBatchListenRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigChangeNotifyRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigPublishRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigQueryRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.config.remote.response.ClientConfigMetricResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigChangeBatchListenResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigPublishResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigQueryResponse;
import com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.common.GroupKey;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.RpcClientTlsConfig;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.nacos.api.annotation.NacosProperties.NAMESPACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ClientWorkerTest {
    
    private static final String TEST_NAMESPACE = "TEST_NAMESPACE";
    
    MockedStatic<RpcClientFactory> rpcClientFactoryMockedStatic;
    
    MockedStatic<LocalConfigInfoProcessor> localConfigInfoProcessorMockedStatic;
    
    @Mock
    RpcClient rpcClient;
    
    private ClientWorker clientWorker;
    
    private ClientWorker clientWorkerSpy;
    
    @BeforeEach
    void before() {
        rpcClientFactoryMockedStatic = Mockito.mockStatic(RpcClientFactory.class);
        
        rpcClientFactoryMockedStatic.when(
                () -> RpcClientFactory.createClient(anyString(), any(ConnectionType.class), any(Map.class),
                        any(RpcClientTlsConfig.class))).thenReturn(rpcClient);
        rpcClientFactoryMockedStatic.when(
                () -> RpcClientFactory.createClient(anyString(), any(ConnectionType.class), any(Map.class),
                        any(RpcClientTlsConfig.class))).thenReturn(rpcClient);
        localConfigInfoProcessorMockedStatic = Mockito.mockStatic(LocalConfigInfoProcessor.class);
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.NAMESPACE, TEST_NAMESPACE);
        ConfigFilterChainManager filter = new ConfigFilterChainManager(properties);
        ConfigServerListManager serverListManager = Mockito.mock(ConfigServerListManager.class);
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(properties);
        try {
            clientWorker = new ClientWorker(filter, serverListManager, nacosClientProperties);
        } catch (NacosException e) {
            throw new RuntimeException(e);
        }
        clientWorkerSpy = Mockito.spy(clientWorker);
    }
    
    @AfterEach
    void after() {
        rpcClientFactoryMockedStatic.close();
        localConfigInfoProcessorMockedStatic.close();
    }
    
    @Test
    void testConstruct() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(filter, agent, nacosClientProperties);
        assertNotNull(clientWorker);
    }
    
    @Test
    void testAddListenerWithoutTenant() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(filter, agent, nacosClientProperties);
        String dataId = "a";
        String group = "b";
        
        Listener listener = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
            }
        };
        
        clientWorker.addListeners(dataId, group, Collections.singletonList(listener));
        List<Listener> listeners = clientWorker.getCache(dataId, group).getListeners();
        assertEquals(1, listeners.size());
        assertEquals(listener, listeners.get(0));
        
        clientWorker.removeListener(dataId, group, listener);
        listeners = clientWorker.getCache(dataId, group).getListeners();
        assertEquals(0, listeners.size());
        
        CacheData cacheData = clientWorker.addCacheDataIfAbsent(dataId, group);
        assertEquals(cacheData, clientWorker.getCache(dataId, group));
    }
    
    @Test
    void testListenerWithTenant() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(filter, agent, nacosClientProperties);
        
        Listener listener = new AbstractListener() {
            @Override
            public void receiveConfigInfo(String configInfo) {
            }
        };
        
        String dataId = "a";
        String group = "b";
        
        clientWorker.addTenantListeners(dataId, group, Collections.singletonList(listener));
        List<Listener> listeners = clientWorker.getCache(dataId, group).getListeners();
        assertEquals(1, listeners.size());
        assertEquals(listener, listeners.get(0));
        
        clientWorker.removeTenantListener(dataId, group, listener);
        listeners = clientWorker.getCache(dataId, group).getListeners();
        assertEquals(0, listeners.size());
        
        String content = "d";
        clientWorker.addTenantListenersWithContent(dataId, group, content, null, Collections.singletonList(listener));
        listeners = clientWorker.getCache(dataId, group).getListeners();
        assertEquals(1, listeners.size());
        assertEquals(listener, listeners.get(0));
        
        clientWorker.removeTenantListener(dataId, group, listener);
        listeners = clientWorker.getCache(dataId, group).getListeners();
        assertEquals(0, listeners.size());
        
        String tenant = "c";
        CacheData cacheData = clientWorker.addCacheDataIfAbsent(dataId, group, tenant);
        assertEquals(cacheData, clientWorker.getCache(dataId, group, tenant));
        
        clientWorker.removeCache(dataId, group, tenant);
        assertNull(clientWorker.getCache(dataId, group, tenant));
        
    }
    
    @Test
    void testPublishConfigSuccess() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(filter, agent, nacosClientProperties);
        
        String dataId = "a";
        String group = "b";
        String tenant = "c";
        String content = "d";
        
        String appName = "app";
        String tag = "tag";
        
        String betaIps = "1.1.1.1";
        String casMd5 = "1111";
        
        String type = "properties";
        Mockito.when(rpcClient.request(any(ConfigPublishRequest.class)))
                .thenReturn(new ConfigPublishResponse());
        boolean b = clientWorker.publishConfig(dataId, group, tenant, appName, tag, betaIps, content, null, casMd5,
                type);
        assertTrue(b);
        
    }
    
    @Test
    void testPublishConfigFail() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(filter, agent, nacosClientProperties);
        
        String dataId = "a";
        String group = "b";
        String tenant = "c";
        String content = "d";
        
        String appName = "app";
        String tag = "tag";
        
        String betaIps = "1.1.1.1";
        String casMd5 = "1111";
        
        String type = "properties";
        Mockito.when(rpcClient.request(any(ConfigPublishRequest.class)))
                .thenReturn(ConfigPublishResponse.buildFailResponse(503, "over limit"));
        boolean b = clientWorker.publishConfig(dataId, group, tenant, appName, tag, betaIps, content, null, casMd5,
                type);
        assertFalse(b);
        
    }
    
    @Test
    void testPublishConfigException() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(filter, agent, nacosClientProperties);
        
        String dataId = "a";
        String group = "b";
        String tenant = "c";
        String content = "d";
        
        String appName = "app";
        String tag = "tag";
        
        String betaIps = "1.1.1.1";
        String casMd5 = "1111";
        
        String type = "properties";
        Mockito.when(rpcClient.request(any(ConfigPublishRequest.class))).thenThrow(new NacosException());
        boolean b = clientWorker.publishConfig(dataId, group, tenant, appName, tag, betaIps, content, null, casMd5,
                type);
        assertFalse(b);
        
    }
    
    @Test
    void testRemoveConfig() throws NacosException {
        
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(filter, agent, nacosClientProperties);
        
        String dataId = "a";
        String group = "b";
        String tenant = "c";
        
        String tag = "tag";
        try {
            Mockito.when(rpcClient.request(any(ConfigRemoveRequest.class)))
                    .thenThrow(new NacosException(503, "overlimit"));
            
            clientWorker.removeConfig(dataId, group, tenant, tag);
            fail();
        } catch (NacosException e) {
            assertEquals("overlimit", e.getErrMsg());
            assertEquals(503, e.getErrCode());
            
        }
    }
    
    @Test
    void testGeConfigConfigSuccess() throws NacosException {
        
        Properties prop = new Properties();
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(null, agent, nacosClientProperties);
        
        String dataId = "a";
        String group = "b";
        String tenant = "c";
        String content = "content" + System.currentTimeMillis();
        
        Mockito.when(rpcClient.request(any(ConfigQueryRequest.class), anyLong()))
                .thenReturn(ConfigQueryResponse.buildSuccessResponse(content));
        
        ConfigResponse configResponse = clientWorker.getServerConfig(dataId, group, tenant, 100, true);
        assertEquals(content, configResponse.getContent());
        localConfigInfoProcessorMockedStatic.verify(
                () -> LocalConfigInfoProcessor.saveSnapshot(eq(clientWorker.getAgentName()), eq(dataId), eq(group),
                        eq(tenant), eq(content)), times(1));
    }
    
    @Test
    void testHandleConfigChangeReqeust() throws Exception {
        
        Properties prop = new Properties();
        String tenant = "c";
        
        prop.put(NAMESPACE, tenant);
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(null, agent, nacosClientProperties);
        
        AtomicReference<Map<String, CacheData>> cacheMapMocked = Mockito.mock(AtomicReference.class);
        Field cacheMap = ClientWorker.class.getDeclaredField("cacheMap");
        cacheMap.setAccessible(true);
        cacheMap.set(clientWorker, cacheMapMocked);
        Map<String, CacheData> cacheDataMapMocked = Mockito.mock(Map.class);
        Mockito.when(cacheMapMocked.get()).thenReturn(cacheDataMapMocked);
        CacheData cacheDataMocked = Mockito.mock(CacheData.class);
        AtomicBoolean atomicBoolean = Mockito.mock(AtomicBoolean.class);
        Mockito.when(cacheDataMocked.getReceiveNotifyChanged()).thenReturn(atomicBoolean);
        String dataId = "a";
        String group = "b";
        Mockito.when(cacheDataMapMocked.get(GroupKey.getKeyTenant(dataId, group, tenant))).thenReturn(cacheDataMocked);
        ConfigChangeNotifyRequest configChangeNotifyRequest = ConfigChangeNotifyRequest.build(dataId, group, tenant);
        ((ClientWorker.ConfigRpcTransportClient) clientWorker.getAgent()).handleConfigChangeNotifyRequest(
                configChangeNotifyRequest, "testname");
        Mockito.verify(cacheDataMocked, times(1)).setConsistentWithServer(false);
        Mockito.verify(atomicBoolean, times(1)).set(true);
    }
    
    @Test
    void testHandleClientMetricsReqeust() throws Exception {
        
        Properties prop = new Properties();
        String tenant = "c";
        
        prop.put(NAMESPACE, tenant);
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(null, agent, nacosClientProperties);
        
        AtomicReference<Map<String, CacheData>> cacheMapMocked = Mockito.mock(AtomicReference.class);
        Field cacheMap = ClientWorker.class.getDeclaredField("cacheMap");
        cacheMap.setAccessible(true);
        cacheMap.set(clientWorker, cacheMapMocked);
        Map<String, CacheData> cacheDataMapMocked = Mockito.mock(Map.class);
        Mockito.when(cacheMapMocked.get()).thenReturn(cacheDataMapMocked);
        CacheData cacheDataMocked = Mockito.mock(CacheData.class);
        String content = "content1324567";
        String md5 = MD5Utils.md5Hex(content, "UTF-8");
        Mockito.when(cacheDataMocked.getContent()).thenReturn(content);
        Mockito.when(cacheDataMocked.getMd5()).thenReturn(md5);
        Field uuid1 = ClientWorker.class.getDeclaredField("uuid");
        uuid1.setAccessible(true);
        String uuid = (String) uuid1.get(clientWorker);
        String dataId = "a23456789";
        String group = "b";
        Mockito.when(cacheDataMapMocked.get(GroupKey.getKeyTenant(dataId, group, tenant))).thenReturn(cacheDataMocked);
        ClientConfigMetricRequest configMetricsRequest = new ClientConfigMetricRequest();
        
        configMetricsRequest.setMetricsKeys(Arrays.asList(
                ClientConfigMetricRequest.MetricsKey.build(ClientConfigMetricRequest.MetricsKey.CACHE_DATA,
                        GroupKey.getKeyTenant(dataId, group, tenant)),
                ClientConfigMetricRequest.MetricsKey.build(ClientConfigMetricRequest.MetricsKey.SNAPSHOT_DATA,
                        GroupKey.getKeyTenant(dataId, group, tenant))));
        
        ClientConfigMetricResponse metricResponse = ((ClientWorker.ConfigRpcTransportClient) clientWorker.getAgent()).handleClientMetricsRequest(
                configMetricsRequest);
        JsonNode jsonNode = JacksonUtils.toObj(metricResponse.getMetrics().get(uuid).toString());
        String metricValues = jsonNode.get("metricValues")
                .get(ClientConfigMetricRequest.MetricsKey.build(ClientConfigMetricRequest.MetricsKey.CACHE_DATA,
                        GroupKey.getKeyTenant(dataId, group, tenant)).toString()).textValue();
        
        int colonIndex = metricValues.lastIndexOf(":");
        assertEquals(content, metricValues.substring(0, colonIndex));
        assertEquals(md5, metricValues.substring(colonIndex + 1, metricValues.length()));
        
    }
    
    @Test
    void testGeConfigConfigNotFound() throws NacosException {
        
        Properties prop = new Properties();
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(null, agent, nacosClientProperties);
        
        String dataId = "a";
        String group = "b";
        String tenant = "c";
        ConfigQueryResponse configQueryResponse = new ConfigQueryResponse();
        configQueryResponse.setErrorInfo(ConfigQueryResponse.CONFIG_NOT_FOUND, "config not found");
        Mockito.when(rpcClient.request(any(ConfigQueryRequest.class), anyLong())).thenReturn(configQueryResponse);
        
        ConfigResponse configResponse = clientWorker.getServerConfig(dataId, group, tenant, 100, true);
        assertNull(configResponse.getContent());
        localConfigInfoProcessorMockedStatic.verify(
                () -> LocalConfigInfoProcessor.saveSnapshot(eq(clientWorker.getAgentName()), eq(dataId), eq(group),
                        eq(tenant), eq(null)), times(1));
        
    }
    
    @Test
    void testGeConfigConfigConflict() throws NacosException {
        
        Properties prop = new Properties();
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(null, agent, nacosClientProperties);
        
        String dataId = "a";
        String group = "b";
        String tenant = "c";
        ConfigQueryResponse configQueryResponse = new ConfigQueryResponse();
        configQueryResponse.setErrorInfo(ConfigQueryResponse.CONFIG_QUERY_CONFLICT, "config is being modified");
        Mockito.when(rpcClient.request(any(ConfigQueryRequest.class), anyLong())).thenReturn(configQueryResponse);
        
        try {
            clientWorker.getServerConfig(dataId, group, tenant, 100, true);
            fail();
        } catch (NacosException e) {
            assertEquals(NacosException.CONFLICT, e.getErrCode());
        }
    }
    
    @Test
    void testShutdown() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(filter, agent, nacosClientProperties);
        clientWorker.shutdown();
        Field agent1 = ClientWorker.class.getDeclaredField("agent");
        agent1.setAccessible(true);
        ConfigTransportClient o = (ConfigTransportClient) agent1.get(clientWorker);
        assertTrue(o.executor.isShutdown());
        agent1.setAccessible(false);
        
        assertNull(clientWorker.getAgentName());
    }
    
    @Test
    void testExecuteConfigListen() throws Exception {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        Mockito.when(agent.getName()).thenReturn("mocktest");
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(filter, agent, nacosClientProperties);
        clientWorker.shutdown();
        
        List<CacheData> cacheDatas = new ArrayList<>();
        String group = "group123";
        String tenant = "tenant122324";
        //mock discards cache
        String dataIdDiscard = "dataIdDiscard" + System.currentTimeMillis();
        
        CacheData cacheDataDiscard = discardCache(filter, agent.getName(), dataIdDiscard, group, tenant);
        cacheDatas.add(cacheDataDiscard);
        //mock use local cache
        String dataIdUseLocalCache = "dataIdUseLocalCache" + System.currentTimeMillis();
        CacheData cacheUseLocalCache = useLocalCache(filter, agent.getName(), dataIdUseLocalCache, group, tenant,
                "content" + System.currentTimeMillis());
        assertFalse(cacheUseLocalCache.isUseLocalConfigInfo());
        
        cacheDatas.add(cacheUseLocalCache);
        
        //mock normal cache
        String dataIdNormal = "dataIdNormal" + System.currentTimeMillis();
        CacheData cacheNormal = normalNotConsistentCache(filter, agent.getName(), dataIdNormal, group, tenant);
        AtomicReference<String> normalContent = new AtomicReference<>();
        cacheNormal.addListener(new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }
            
            @Override
            public void receiveConfigInfo(String configInfo) {
                System.out.println(configInfo);
                normalContent.set(configInfo);
            }
        });
        cacheDatas.add(cacheNormal);
        cacheNormal.setInitializing(false);
        Map<String, CacheData> cacheDataMapMocked = Mockito.mock(Map.class);
        Mockito.when(cacheDataMapMocked.get(GroupKey.getKeyTenant(dataIdNormal, group, tenant)))
                .thenReturn(cacheNormal);
        Mockito.when(cacheDataMapMocked.containsKey(GroupKey.getKeyTenant(dataIdNormal, group, tenant)))
                .thenReturn(true);
        
        Mockito.when(cacheDataMapMocked.values()).thenReturn(cacheDatas);
        AtomicReference<Map<String, CacheData>> cacheMapMocked = Mockito.mock(AtomicReference.class);
        Mockito.when(cacheMapMocked.get()).thenReturn(cacheDataMapMocked);
        Field cacheMap = ClientWorker.class.getDeclaredField("cacheMap");
        cacheMap.setAccessible(true);
        cacheMap.set(clientWorker, cacheMapMocked);
        
        //mock request
        ConfigChangeBatchListenResponse.ConfigContext configContext = new ConfigChangeBatchListenResponse.ConfigContext();
        configContext.setDataId(dataIdNormal);
        configContext.setGroup(group);
        configContext.setTenant(tenant);
        ConfigChangeBatchListenResponse response = new ConfigChangeBatchListenResponse();
        response.setChangedConfigs(Collections.singletonList(configContext));
        
        RpcClient rpcClientInner = Mockito.mock(RpcClient.class);
        Mockito.when(rpcClientInner.isWaitInitiated()).thenReturn(true, false);
        rpcClientFactoryMockedStatic.when(
                () -> RpcClientFactory.createClient(anyString(), any(ConnectionType.class), any(Map.class),
                        any(RpcClientTlsConfig.class))).thenReturn(rpcClientInner);
        // mock listen and remove listen request
        Mockito.when(rpcClientInner.request(any(ConfigBatchListenRequest.class)))
                .thenReturn(response, response);
        // mock query changed config
        ConfigQueryResponse configQueryResponse = new ConfigQueryResponse();
        configQueryResponse.setContent("content" + System.currentTimeMillis());
        configQueryResponse.setContentType(ConfigType.JSON.getType());
        Mockito.when(rpcClientInner.request(any(ConfigQueryRequest.class))).thenReturn(configQueryResponse);
        (clientWorker.getAgent()).executeConfigListen();
        //assert
        //use local cache.
        assertTrue(cacheUseLocalCache.isUseLocalConfigInfo());
        //discard cache to be deleted.
        assertFalse(cacheMapMocked.get().containsKey(GroupKey.getKeyTenant(dataIdDiscard, group, tenant)));
        //normal cache listener be notified.
        assertEquals(configQueryResponse.getContent(), normalContent.get());
        
    }
    
    private CacheData discardCache(ConfigFilterChainManager filter, String envName, String dataId, String group,
            String tenant) {
        CacheData cacheData = new CacheData(filter, envName, dataId, group, tenant);
        cacheData.setDiscard(true);
        cacheData.setConsistentWithServer(false);
        File file = Mockito.mock(File.class);
        Mockito.when(file.exists()).thenReturn(false);
        localConfigInfoProcessorMockedStatic.when(
                () -> LocalConfigInfoProcessor.getFailoverFile(envName, dataId, group, tenant)).thenReturn(file);
        return cacheData;
    }
    
    private CacheData normalNotConsistentCache(ConfigFilterChainManager filter, String envName, String dataId,
            String group, String tenant) throws NacosException {
        CacheData cacheData = new CacheData(filter, envName, dataId, group, tenant);
        cacheData.setDiscard(false);
        cacheData.setConsistentWithServer(false);
        File file = Mockito.mock(File.class);
        Mockito.when(file.exists()).thenReturn(false);
        localConfigInfoProcessorMockedStatic.when(
                () -> LocalConfigInfoProcessor.getFailoverFile(envName, dataId, group, tenant)).thenReturn(file);
        return cacheData;
    }
    
    private CacheData useLocalCache(ConfigFilterChainManager filter, String envName, String dataId, String group,
            String tenant, String failOverContent) {
        CacheData cacheData = new CacheData(filter, envName, dataId, group, tenant);
        cacheData.setDiscard(true);
        File file = Mockito.mock(File.class);
        Mockito.when(file.exists()).thenReturn(true);
        localConfigInfoProcessorMockedStatic.when(
                () -> LocalConfigInfoProcessor.getFailoverFile(envName, dataId, group, tenant)).thenReturn(file);
        localConfigInfoProcessorMockedStatic.when(
                () -> LocalConfigInfoProcessor.getFailover(envName, dataId, group, tenant)).thenReturn(failOverContent);
        return cacheData;
    }
    
    @Test
    void testIsHealthServer() throws NacosException, NoSuchFieldException, IllegalAccessException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(filter, agent, nacosClientProperties);
        ClientWorker.ConfigRpcTransportClient client = Mockito.mock(ClientWorker.ConfigRpcTransportClient.class);
        Mockito.when(client.isHealthServer()).thenReturn(Boolean.TRUE);
        
        Field declaredField = ClientWorker.class.getDeclaredField("agent");
        declaredField.setAccessible(true);
        declaredField.set(clientWorker, client);
        
        assertTrue(clientWorker.isHealthServer());
        
        Mockito.when(client.isHealthServer()).thenReturn(Boolean.FALSE);
        assertFalse(clientWorker.isHealthServer());
    }
    
    @Test
    void testPutCache() throws Exception {
        // 反射调用私有方法putCacheIfAbsent
        Method putCacheMethod = ClientWorker.class.getDeclaredMethod("putCache", String.class, CacheData.class);
        putCacheMethod.setAccessible(true);
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        ClientWorker clientWorker = new ClientWorker(filter, agent, nacosClientProperties);
        String key = "testKey";
        CacheData cacheData = new CacheData(filter, "env", "dataId", "group");
        putCacheMethod.invoke(clientWorker, key, cacheData);
        Field cacheMapField = ClientWorker.class.getDeclaredField("cacheMap");
        cacheMapField.setAccessible(true);
        AtomicReference<Map<String, CacheData>> cacheMapRef = (AtomicReference<Map<String, CacheData>>) cacheMapField.get(
                clientWorker);
        // 检查cacheMap是否包含特定的key
        assertNotNull(cacheMapRef.get().get(key));
        assertEquals(cacheData, cacheMapRef.get().get(key));
        // 测试再次插入相同的key将覆盖原始的值
        CacheData newCacheData = new CacheData(filter, "newEnv", "newDataId", "newGroup");
        putCacheMethod.invoke(clientWorker, key, newCacheData);
        // 检查key对应的value是否改变为newCacheData
        assertEquals(newCacheData, cacheMapRef.get().get(key));
    }
    
    @Test
    void testAddListenersEnsureCacheDataSafe() throws NacosException, IllegalAccessException, NoSuchFieldException {
        String dataId = "testDataId";
        String group = "testGroup";
        // 将key-cacheData插入到cacheMap中
        CacheData cacheData = new CacheData(null, "env", dataId, group);
        Field cacheMapField = ClientWorker.class.getDeclaredField("cacheMap");
        cacheMapField.setAccessible(true);
        AtomicReference<Map<String, CacheData>> cacheMapRef = (AtomicReference<Map<String, CacheData>>) cacheMapField.get(
                clientWorker);
        String key = GroupKey.getKey(dataId, group);
        cacheMapRef.get().put(key, cacheData);
        // 当addCacheDataIfAbsent得到的differentCacheData，同cacheMap中该key对应的cacheData不一致
        CacheData differentCacheData = new CacheData(null, "env", dataId, group);
        doReturn(differentCacheData).when(clientWorkerSpy).addCacheDataIfAbsent(anyString(), anyString());
        // 使用addListeners将differentCacheData插入到cacheMap中
        clientWorkerSpy.addListeners(dataId, group, Collections.EMPTY_LIST);
        CacheData cacheDataFromCache1 = clientWorker.getCache(dataId, group);
        assertNotNull(cacheDataFromCache1);
        assertEquals(cacheDataFromCache1, differentCacheData);
        assertFalse(cacheDataFromCache1.isDiscard());
        assertFalse(cacheDataFromCache1.isConsistentWithServer());
        // 再次调用addListeners，此时addCacheDataIfAbsent得到的cacheData同cacheMap中该key对应的cacheData一致，均为differentCacheData
        clientWorkerSpy.addListeners(dataId, group, Collections.EMPTY_LIST);
        CacheData cacheDataFromCache2 = clientWorker.getCache(dataId, group);
        assertNotNull(cacheDataFromCache2);
        assertEquals(cacheDataFromCache2, differentCacheData);
        assertFalse(cacheDataFromCache2.isDiscard());
        assertFalse(cacheDataFromCache2.isConsistentWithServer());
    }
    
    @Test
    void testAddTenantListenersEnsureCacheDataSafe()
            throws NacosException, IllegalAccessException, NoSuchFieldException {
        String dataId = "testDataId";
        String group = "testGroup";
        // 将key-cacheData插入到cacheMap中
        CacheData cacheData = new CacheData(null, "env", dataId, group);
        Field cacheMapField = ClientWorker.class.getDeclaredField("cacheMap");
        cacheMapField.setAccessible(true);
        AtomicReference<Map<String, CacheData>> cacheMapRef = (AtomicReference<Map<String, CacheData>>) cacheMapField.get(
                clientWorker);
        String key = GroupKey.getKeyTenant(dataId, group, TEST_NAMESPACE);
        cacheMapRef.get().put(key, cacheData);
        // 当addCacheDataIfAbsent得到的differentCacheData，同cacheMap中该key对应的cacheData不一致
        CacheData differentCacheData = new CacheData(null, "env", dataId, group);
        doReturn(differentCacheData).when(clientWorkerSpy)
                .addCacheDataIfAbsent(anyString(), anyString(), eq(TEST_NAMESPACE));
        // 使用addListeners将differentCacheData插入到cacheMap中
        clientWorkerSpy.addTenantListeners(dataId, group, Collections.EMPTY_LIST);
        CacheData cacheDataFromCache1 = clientWorker.getCache(dataId, group, TEST_NAMESPACE);
        assertNotNull(cacheDataFromCache1);
        assertEquals(cacheDataFromCache1, differentCacheData);
        assertFalse(cacheDataFromCache1.isDiscard());
        assertFalse(cacheDataFromCache1.isConsistentWithServer());
        // 再次调用addListeners，此时addCacheDataIfAbsent得到的cacheData同cacheMap中该key对应的cacheData一致，均为differentCacheData
        clientWorkerSpy.addTenantListeners(dataId, group, Collections.EMPTY_LIST);
        CacheData cacheDataFromCache2 = clientWorker.getCache(dataId, group, TEST_NAMESPACE);
        assertNotNull(cacheDataFromCache2);
        assertEquals(cacheDataFromCache2, differentCacheData);
        assertFalse(cacheDataFromCache2.isDiscard());
        assertFalse(cacheDataFromCache2.isConsistentWithServer());
    }
    
    @Test
    void testAddTenantListenersWithContentEnsureCacheDataSafe()
            throws NacosException, IllegalAccessException, NoSuchFieldException {
        String dataId = "testDataId";
        String group = "testGroup";
        // 将key-cacheData插入到cacheMap中
        CacheData cacheData = new CacheData(null, "env", dataId, group);
        Field cacheMapField = ClientWorker.class.getDeclaredField("cacheMap");
        cacheMapField.setAccessible(true);
        AtomicReference<Map<String, CacheData>> cacheMapRef = (AtomicReference<Map<String, CacheData>>) cacheMapField.get(
                clientWorker);
        String key = GroupKey.getKeyTenant(dataId, group, TEST_NAMESPACE);
        cacheMapRef.get().put(key, cacheData);
        // 当addCacheDataIfAbsent得到的differentCacheData，同cacheMap中该key对应的cacheData不一致
        CacheData differentCacheData = new CacheData(null, "env", dataId, group);
        doReturn(differentCacheData).when(clientWorkerSpy)
                .addCacheDataIfAbsent(anyString(), anyString(), eq(TEST_NAMESPACE));
        // 使用addListeners将differentCacheData插入到cacheMap中
        clientWorkerSpy.addTenantListenersWithContent(dataId, group, "", "", Collections.EMPTY_LIST);
        CacheData cacheDataFromCache1 = clientWorker.getCache(dataId, group, TEST_NAMESPACE);
        assertNotNull(cacheDataFromCache1);
        assertEquals(cacheDataFromCache1, differentCacheData);
        assertFalse(cacheDataFromCache1.isDiscard());
        assertFalse(cacheDataFromCache1.isConsistentWithServer());
        // 再次调用addListeners，此时addCacheDataIfAbsent得到的cacheData同cacheMap中该key对应的cacheData一致，均为differentCacheData
        clientWorkerSpy.addTenantListenersWithContent(dataId, group, "", "", Collections.EMPTY_LIST);
        CacheData cacheDataFromCache2 = clientWorker.getCache(dataId, group, TEST_NAMESPACE);
        assertNotNull(cacheDataFromCache2);
        assertEquals(cacheDataFromCache2, differentCacheData);
        assertFalse(cacheDataFromCache2.isDiscard());
        assertFalse(cacheDataFromCache2.isConsistentWithServer());
    }
    
    @Test
    void testResponse403() throws NacosException {
        Properties prop = new Properties();
        ConfigFilterChainManager filter = new ConfigFilterChainManager(new Properties());
        ConfigServerListManager agent = Mockito.mock(ConfigServerListManager.class);
        
        final NacosClientProperties nacosClientProperties = NacosClientProperties.PROTOTYPE.derive(prop);
        final ClientWorker clientWorker = new ClientWorker(filter, agent, nacosClientProperties);
        
        ConfigRemoveResponse response = ConfigRemoveResponse.buildFailResponse("accessToken invalid");
        response.setErrorCode(ConfigQueryResponse.NO_RIGHT);
        Mockito.when(rpcClient.request(any(ConfigRemoveRequest.class)))
                .thenReturn(response);
        boolean result = clientWorker.removeConfig("a", "b", "c", "tag");
        assertFalse(result);
    }
}
