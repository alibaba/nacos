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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.config.listener.AbstractFuzzyWatchEventWatcher;
import com.alibaba.nacos.api.config.listener.ConfigFuzzyWatchChangeEvent;
import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchChangeNotifyRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchRequest;
import com.alibaba.nacos.api.config.remote.request.ConfigFuzzyWatchSyncRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigFuzzyWatchResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.client.config.common.GroupKey;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.ADD_CONFIG;
import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.DELETE_CONFIG;
import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_DIFF_SYNC_NOTIFY;
import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_INIT_NOTIFY;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_OVER_LIMIT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConfigFuzzyWatchGroupKeyHolderTest {
    
    ConfigFuzzyWatchGroupKeyHolder configFuzzyWatchGroupKeyHolder;
    
    @Mock
    ClientWorker.ConfigRpcTransportClient rpcTransportClient;
    
    String clientId = "conn" + System.currentTimeMillis();
    
    String tenant = "t1";
    
    @BeforeEach
    void before() {
        configFuzzyWatchGroupKeyHolder = new ConfigFuzzyWatchGroupKeyHolder(rpcTransportClient, clientId);
        doReturn(true).when(rpcTransportClient).isAbilitySupportedByServer(AbilityKey.SERVER_FUZZY_WATCH);
    }
    
    @AfterEach
    void after() {
    }
    
    @Test
    void testRegisterFuzzyWatcherAndNotify() throws InterruptedException {
        when(rpcTransportClient.getTenant()).thenReturn(tenant);
        
        String dataId = "dataId";
        String group = "group";
        
        AtomicInteger watcher1Flag = new AtomicInteger(0);
        AtomicInteger watcher2Flag = new AtomicInteger(0);
        configFuzzyWatchGroupKeyHolder.registerFuzzyWatcher(dataId + "*", group, new AbstractFuzzyWatchEventWatcher() {
            @Override
            public void onEvent(ConfigFuzzyWatchChangeEvent event) {
                watcher1Flag.incrementAndGet();
            }
        });
        
        configFuzzyWatchGroupKeyHolder.registerFuzzyWatcher(dataId + "*", group, new AbstractFuzzyWatchEventWatcher() {
            @Override
            public void onEvent(ConfigFuzzyWatchChangeEvent event) {
                watcher2Flag.incrementAndGet();
            }
        });
        
        String groupKey1 = GroupKey.getKeyTenant(dataId + 1, group, tenant);
        //build init notify add
        Set<ConfigFuzzyWatchSyncRequest.Context> contexts = new HashSet<>();
        contexts.add(ConfigFuzzyWatchSyncRequest.Context.build(groupKey1, ADD_CONFIG));
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern(dataId + "*", group, tenant);
        ConfigFuzzyWatchSyncRequest initNotifyRequest = ConfigFuzzyWatchSyncRequest.buildSyncRequest(
                FUZZY_WATCH_INIT_NOTIFY, contexts, groupKeyPattern, 1, 1);
        configFuzzyWatchGroupKeyHolder.handleFuzzyWatchSyncNotifyRequest(initNotifyRequest);
        //check watcher notified
        Thread.sleep(100L);
        System.out.println(watcher1Flag.get());
        Assertions.assertTrue(watcher1Flag.get() == 1);
        
        //build change notify add
        String changedGroupKey2Add = GroupKey.getKeyTenant(dataId + 2, group, tenant);
        ConfigFuzzyWatchChangeNotifyRequest changedNotifyRequest = new ConfigFuzzyWatchChangeNotifyRequest(
                changedGroupKey2Add, ADD_CONFIG);
        configFuzzyWatchGroupKeyHolder.handlerFuzzyWatchChangeNotifyRequest(changedNotifyRequest);
        
        //check watcher notified
        Thread.sleep(100L);
        Assertions.assertTrue(watcher1Flag.get() == 2);
        
        //check not complete future timeout
        ConfigFuzzyWatchContext configFuzzyWatchContext = configFuzzyWatchGroupKeyHolder.getFuzzyListenContext(
                dataId + "*", group);
        Future<Set<String>> newFutureNotFinish = configFuzzyWatchContext.createNewFuture();
        try {
            newFutureNotFinish.get(1000L, TimeUnit.MILLISECONDS);
            Assertions.assertFalse(true);
        } catch (TimeoutException e) {
            Assertions.assertTrue(true);
        } catch (Throwable throwable) {
            Assertions.assertFalse(true);
        }
        
        // build init finish notify
        ConfigFuzzyWatchSyncRequest configFuzzyWatchSyncRequest = ConfigFuzzyWatchSyncRequest.buildInitFinishRequest(
                groupKeyPattern);
        configFuzzyWatchGroupKeyHolder.handleFuzzyWatchSyncNotifyRequest(configFuzzyWatchSyncRequest);
        
        //check a completed future.
        Future<Set<String>> newFutureFinish = configFuzzyWatchContext.createNewFuture();
        try {
            Set<String> groupKeys = newFutureFinish.get(10L, TimeUnit.MILLISECONDS);
            Assertions.assertTrue(
                    groupKeys != null && groupKeys.contains(groupKey1) && groupKeys.contains(changedGroupKey2Add));
        } catch (Exception e) {
            Assertions.assertTrue(false);
        }
        
        //check watcher notified delete
        Thread.sleep(100L);
        Assertions.assertTrue(watcher1Flag.get() == 2);
        Assertions.assertTrue(watcher1Flag.get() == 2);
        
        //build change notify
        String changedGroupKey2Delete = changedGroupKey2Add;
        
        ConfigFuzzyWatchChangeNotifyRequest changedNotifyRequestDelete = new ConfigFuzzyWatchChangeNotifyRequest(
                changedGroupKey2Delete, DELETE_CONFIG);
        configFuzzyWatchGroupKeyHolder.handlerFuzzyWatchChangeNotifyRequest(changedNotifyRequestDelete);
        //check watcher notified delete
        Thread.sleep(100L);
        Assertions.assertTrue(watcher1Flag.get() == 3);
        Assertions.assertTrue(watcher1Flag.get() == 3);
        
        Future<Set<String>> newFuture = configFuzzyWatchContext.createNewFuture();
        
        try {
            Set<String> groupKeys = newFuture.get(10L, TimeUnit.MILLISECONDS);
            Assertions.assertTrue(
                    groupKeys != null && groupKeys.contains(groupKey1) && !groupKeys.contains(changedGroupKey2Delete));
        } catch (Exception e) {
            Assertions.assertTrue(false);
        }
        
        //build sync delete
        String groupKey1Delete = groupKey1;
        //build init notify add
        Set<ConfigFuzzyWatchSyncRequest.Context> contextsDelete = new HashSet<>();
        contextsDelete.add(ConfigFuzzyWatchSyncRequest.Context.build(groupKey1Delete, DELETE_CONFIG));
        ConfigFuzzyWatchSyncRequest deleteNotifyRequest = ConfigFuzzyWatchSyncRequest.buildSyncRequest(
                FUZZY_WATCH_DIFF_SYNC_NOTIFY, contextsDelete, groupKeyPattern, 1, 1);
        configFuzzyWatchGroupKeyHolder.handleFuzzyWatchSyncNotifyRequest(deleteNotifyRequest);
        
        //check watcher notified delete
        Thread.sleep(100L);
        Assertions.assertTrue(watcher1Flag.get() == 4);
        Assertions.assertTrue(watcher1Flag.get() == 4);
        
        Future<Set<String>> newFutureEmpty = configFuzzyWatchContext.createNewFuture();
        
        try {
            Set<String> groupKeys = newFutureEmpty.get(10L, TimeUnit.MILLISECONDS);
            Assertions.assertTrue(CollectionUtils.isEmpty(groupKeys));
        } catch (Exception e) {
            Assertions.assertTrue(false);
        }
        
        configFuzzyWatchGroupKeyHolder.resetConsistenceStatus();
        Assertions.assertFalse(configFuzzyWatchContext.isConsistentWithServer());
    }
    
    @Test
    void testExecuteConfigFuzzyListen() throws NacosException {
        when(rpcTransportClient.getTenant()).thenReturn(tenant);
        
        ConfigFuzzyWatchContext configFuzzyWatchContext = configFuzzyWatchGroupKeyHolder.registerFuzzyWatcher("da1*",
                "group*", new AbstractFuzzyWatchEventWatcher() {
                    @Override
                    public void onEvent(ConfigFuzzyWatchChangeEvent event) {
                    
                    }
                });
        configFuzzyWatchContext.setConsistentWithServer(true);
        
        configFuzzyWatchGroupKeyHolder.registerFuzzyWatcher("da2*", "group*", new AbstractFuzzyWatchEventWatcher() {
            @Override
            public void onEvent(ConfigFuzzyWatchChangeEvent event) {
            
            }
        });
        
        configFuzzyWatchGroupKeyHolder.registerFuzzyWatcher("da3*", "group*", new AbstractFuzzyWatchEventWatcher() {
            @Override
            public void onEvent(ConfigFuzzyWatchChangeEvent event) {
            
            }
        });
        
        RpcClient rpcClient = Mockito.mock(RpcClient.class);
        when(rpcTransportClient.ensureRpcClient(eq("0"))).thenReturn(rpcClient);
        ThreadPoolExecutor scheduledExecutorService = Mockito.mock(ThreadPoolExecutor.class);
        when(rpcTransportClient.getExecutor()).thenReturn(scheduledExecutorService);
        when(scheduledExecutorService.submit(any(Runnable.class))).thenReturn(Mockito.mock(Future.class));
        configFuzzyWatchGroupKeyHolder.executeConfigFuzzyListen();
        
        verify(scheduledExecutorService, times(2)).submit(ArgumentMatchers.any(Runnable.class));
        
    }
    
    @Test
    void testExecuteFuzzyWatchRequestNormal() throws NacosException {
        reset(rpcTransportClient);
        String envName = "name";
        String groupKeyPattern = "pattern";
        ConfigFuzzyWatchContext configFuzzyWatchContext = new ConfigFuzzyWatchContext(envName, groupKeyPattern);
        configFuzzyWatchContext.refreshOverLimitTs();
        RpcClient rpcClient = Mockito.mock(RpcClient.class);
        
        when(rpcTransportClient.requestProxy(eq(rpcClient), any(ConfigFuzzyWatchRequest.class))).thenReturn(
                new ConfigFuzzyWatchResponse());
        configFuzzyWatchGroupKeyHolder.executeFuzzyWatchRequest(configFuzzyWatchContext, rpcClient);
        
        Assertions.assertTrue(!configFuzzyWatchContext.patternLimitSuppressed());
        Assertions.assertTrue(configFuzzyWatchContext.isConsistentWithServer());
        
    }
    
    @Test
    void testExecuteFuzzyWatchRequestRemove() throws NacosException {
        
        AbstractFuzzyWatchEventWatcher abstractFuzzyWatchEventWatcher = new AbstractFuzzyWatchEventWatcher() {
            @Override
            public void onEvent(ConfigFuzzyWatchChangeEvent event) {
            
            }
        };
        configFuzzyWatchGroupKeyHolder.registerFuzzyWatcher("*", "*", abstractFuzzyWatchEventWatcher);
        configFuzzyWatchGroupKeyHolder.removeFuzzyWatcher("*", "*", abstractFuzzyWatchEventWatcher);
        
        RpcClient rpcClient = Mockito.mock(RpcClient.class);
        when(rpcTransportClient.requestProxy(eq(rpcClient), any(ConfigFuzzyWatchRequest.class))).thenReturn(
                new ConfigFuzzyWatchResponse());
        configFuzzyWatchGroupKeyHolder.executeFuzzyWatchRequest(
                configFuzzyWatchGroupKeyHolder.getFuzzyListenContext("*", "*"), rpcClient);
        
        Assertions.assertTrue(configFuzzyWatchGroupKeyHolder.getFuzzyListenContext("*", "*") == null);
        
    }
    
    @Test
    void testExecuteFuzzyWatchRequestOverLoad() throws NacosException, InterruptedException {
        
        AtomicBoolean patternOvrFlag = new AtomicBoolean(false);
        AtomicBoolean configCountOverFlag = new AtomicBoolean(false);
        
        AbstractFuzzyWatchEventWatcher abstractFuzzyWatchEventWatcher = new AbstractFuzzyWatchEventWatcher() {
            @Override
            public void onEvent(ConfigFuzzyWatchChangeEvent event) {
            
            }
            
            @Override
            public void onPatternOverLimit() {
                patternOvrFlag.set(true);
            }
            
            @Override
            public void onConfigReachUpLimit() {
                configCountOverFlag.set(true);
            }
        };
        ConfigFuzzyWatchContext configFuzzyWatchContext = configFuzzyWatchGroupKeyHolder.registerFuzzyWatcher("*", "*",
                abstractFuzzyWatchEventWatcher);
        
        RpcClient rpcClient = Mockito.mock(RpcClient.class);
        
        //test pattern over load
        ConfigFuzzyWatchResponse overloadResponse = new ConfigFuzzyWatchResponse();
        overloadResponse.setErrorInfo(FUZZY_WATCH_PATTERN_OVER_LIMIT.getCode(),
                FUZZY_WATCH_PATTERN_OVER_LIMIT.getMsg());
        when(rpcTransportClient.requestProxy(eq(rpcClient), any(ConfigFuzzyWatchRequest.class))).thenReturn(
                overloadResponse);
        configFuzzyWatchGroupKeyHolder.executeFuzzyWatchRequest(configFuzzyWatchContext, rpcClient);
        Thread.sleep(100L);
        Assertions.assertTrue(configFuzzyWatchContext.patternLimitSuppressed());
        Assertions.assertTrue(!configFuzzyWatchContext.isConsistentWithServer());
        Assertions.assertTrue(patternOvrFlag.get());
        Assertions.assertFalse(configCountOverFlag.get());
        
        configFuzzyWatchContext.clearOverLimitTs();
        ConfigFuzzyWatchResponse countOverloadResponse = new ConfigFuzzyWatchResponse();
        countOverloadResponse.setErrorInfo(FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT.getCode(),
                FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT.getMsg());
        when(rpcTransportClient.requestProxy(eq(rpcClient), any(ConfigFuzzyWatchRequest.class))).thenReturn(
                countOverloadResponse);
        configFuzzyWatchGroupKeyHolder.executeFuzzyWatchRequest(configFuzzyWatchContext, rpcClient);
        Thread.sleep(100L);
        Assertions.assertTrue(configFuzzyWatchContext.patternLimitSuppressed());
        Assertions.assertTrue(!configFuzzyWatchContext.isConsistentWithServer());
        Assertions.assertTrue(configCountOverFlag.get());
        
    }
    
    @Test
    void testSyncWhenWatcherFail() throws NacosException, InterruptedException {
        when(rpcTransportClient.getTenant()).thenReturn(tenant);
        
        String groupKey = GroupKey.getKeyTenant("dataIdName124", "group", tenant);
        
        AtomicInteger watcherFlag = new AtomicInteger(0);
        
        AbstractFuzzyWatchEventWatcher abstractFuzzyWatchEventWatcher = new AbstractFuzzyWatchEventWatcher() {
            
            @Override
            public void onEvent(ConfigFuzzyWatchChangeEvent event) {
                int get = watcherFlag.incrementAndGet();
                if (get < 2) {
                    System.out.println("times " + get + " fail");
                    throw new RuntimeException("mock exception");
                } else {
                    System.out.println("times " + get + " success");
                    
                }
            }
        };
        
        ConfigFuzzyWatchContext configFuzzyWatchContext = configFuzzyWatchGroupKeyHolder.registerFuzzyWatcher(
                "dataIdName*", "group", abstractFuzzyWatchEventWatcher);
        
        ConfigFuzzyWatchChangeNotifyRequest configFuzzyWatchChangeNotifyRequest = new ConfigFuzzyWatchChangeNotifyRequest(
                groupKey, ADD_CONFIG);
        configFuzzyWatchGroupKeyHolder.handlerFuzzyWatchChangeNotifyRequest(configFuzzyWatchChangeNotifyRequest);
        
        TimeUnit.MILLISECONDS.sleep(100L);
        
        //notify 1, fail
        configFuzzyWatchContext.syncFuzzyWatchers();
        //notify 2,success
        configFuzzyWatchContext.syncFuzzyWatchers();
        //notify 3 +, will not trigger watchers.
        configFuzzyWatchContext.syncFuzzyWatchers();
        configFuzzyWatchContext.syncFuzzyWatchers();
        configFuzzyWatchContext.syncFuzzyWatchers();
        //expect  2 times notified
        Assertions.assertEquals(2, watcherFlag.get());
    }
    
    @Test
    void testFuzzyWatchNotSupport() {
        when(rpcTransportClient.isAbilitySupportedByServer(AbilityKey.SERVER_FUZZY_WATCH)).thenReturn(false);
        Assertions.assertThrows(NacosRuntimeException.class, () -> {
            configFuzzyWatchGroupKeyHolder.registerFuzzyWatcher("dataIdName*", "group",
                    new AbstractFuzzyWatchEventWatcher() {
                        
                        @Override
                        public void onEvent(ConfigFuzzyWatchChangeEvent event) {
                        }
                    });
        });
    }
}
