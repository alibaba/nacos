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

package com.alibaba.nacos.client.naming.cache;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.naming.listener.AbstractFuzzyWatchEventWatcher;
import com.alibaba.nacos.api.naming.listener.FuzzyWatchChangeEvent;
import com.alibaba.nacos.api.naming.pojo.ListView;
import com.alibaba.nacos.api.naming.remote.request.NamingFuzzyWatchChangeNotifyRequest;
import com.alibaba.nacos.api.naming.remote.request.NamingFuzzyWatchRequest;
import com.alibaba.nacos.api.naming.remote.request.NamingFuzzyWatchSyncRequest;
import com.alibaba.nacos.api.naming.remote.response.NamingFuzzyWatchResponse;
import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.client.naming.event.NamingFuzzyWatchLoadEvent;
import com.alibaba.nacos.client.naming.remote.gprc.NamingFuzzyWatchNotifyRequestHandler;
import com.alibaba.nacos.client.naming.remote.gprc.NamingGrpcClientProxy;
import com.alibaba.nacos.common.remote.client.Connection;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.nacos.api.common.Constants.FINISH_FUZZY_WATCH_INIT_NOTIFY;
import static com.alibaba.nacos.api.common.Constants.FUZZY_WATCH_INIT_NOTIFY;
import static com.alibaba.nacos.api.common.Constants.ServiceChangedType.ADD_SERVICE;
import static com.alibaba.nacos.api.common.Constants.ServiceChangedType.DELETE_SERVICE;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT;
import static com.alibaba.nacos.api.model.v2.ErrorCode.FUZZY_WATCH_PATTERN_OVER_LIMIT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NamingFuzzyWatchServiceListHolderTest {
    
    NamingFuzzyWatchServiceListHolder namingFuzzyWatchServiceListHolder;
    
    NamingFuzzyWatchNotifyRequestHandler namingFuzzyWatchNotifyRequestHandler;
    
    String eventScope = "scope" + System.currentTimeMillis();
    
    @Mock
    NamingGrpcClientProxy namingGrpcClientProxy;
    
    @Mock
    Connection connection;
    
    @BeforeEach
    void before() {
        namingFuzzyWatchServiceListHolder = new NamingFuzzyWatchServiceListHolder(eventScope);
        namingFuzzyWatchServiceListHolder.registerNamingGrpcClientProxy(namingGrpcClientProxy);
        namingFuzzyWatchNotifyRequestHandler = new NamingFuzzyWatchNotifyRequestHandler(
                namingFuzzyWatchServiceListHolder);
        when(namingGrpcClientProxy.isAbilitySupportedByServer(AbilityKey.SERVER_FUZZY_WATCH)).thenReturn(true);
    }
    
    @AfterEach
    void after() {
    
    }
    
    @Test
    void testOnEventWatchNotify() throws InterruptedException {
        
        String serviceKey = NamingUtils.getServiceKey("namespace123", "group", "serviceName124");
        String generatePattern = FuzzyGroupKeyPattern.generatePattern("serviceName124*", "group", "namespace123");
        
        AtomicInteger watcherFlag = new AtomicInteger(0);
        namingFuzzyWatchServiceListHolder.registerFuzzyWatcher(generatePattern, new AbstractFuzzyWatchEventWatcher() {
            @Override
            public void onEvent(FuzzyWatchChangeEvent event) {
                watcherFlag.incrementAndGet();
            }
        });
        
        NamingFuzzyWatchChangeNotifyRequest namingFuzzyWatchChangeNotifyRequest = new NamingFuzzyWatchChangeNotifyRequest(
                serviceKey, ADD_SERVICE);
        Response response = namingFuzzyWatchNotifyRequestHandler.requestReply(namingFuzzyWatchChangeNotifyRequest,
                connection);
        Assertions.assertNotNull(response);
        Thread.sleep(100L);
        Assertions.assertEquals(1, watcherFlag.get());
        
        Response duplicatedResponse = namingFuzzyWatchNotifyRequestHandler.requestReply(
                namingFuzzyWatchChangeNotifyRequest, connection);
        Assertions.assertNotNull(duplicatedResponse);
        Thread.sleep(100L);
        Assertions.assertEquals(1, watcherFlag.get());
        
        namingFuzzyWatchChangeNotifyRequest.setChangedType(DELETE_SERVICE);
        Response deleteResponse = namingFuzzyWatchNotifyRequestHandler.requestReply(namingFuzzyWatchChangeNotifyRequest,
                connection);
        Assertions.assertNotNull(deleteResponse);
        Thread.sleep(100L);
        Assertions.assertEquals(2, watcherFlag.get());
        
    }
    
    @Test
    void testOnEventWatchSync() throws InterruptedException {
        
        String generatePattern = FuzzyGroupKeyPattern.generatePattern("serviceName124*", "group", "namespace123");
        
        AtomicInteger watcherFlag = new AtomicInteger(0);
        NamingFuzzyWatchContext namingFuzzyWatchContext = namingFuzzyWatchServiceListHolder.registerFuzzyWatcher(
                generatePattern, new AbstractFuzzyWatchEventWatcher() {
                    @Override
                    public void onEvent(FuzzyWatchChangeEvent event) {
                        watcherFlag.incrementAndGet();
                    }
                });
        
        String serviceKey1 = NamingUtils.getServiceKey("namespace123", "group", "serviceName124");
        String serviceKey2 = NamingUtils.getServiceKey("namespace123", "group", "serviceName124234");
        
        Set<NamingFuzzyWatchSyncRequest.Context> contexts = new HashSet<>();
        contexts.add(NamingFuzzyWatchSyncRequest.Context.build(serviceKey1, ADD_SERVICE));
        contexts.add(NamingFuzzyWatchSyncRequest.Context.build(serviceKey2, ADD_SERVICE));
        
        //init notify
        NamingFuzzyWatchSyncRequest namingFuzzyWatchSyncRequest = new NamingFuzzyWatchSyncRequest(generatePattern,
                FUZZY_WATCH_INIT_NOTIFY, contexts);
        Response responseInitNotify = namingFuzzyWatchNotifyRequestHandler.requestReply(namingFuzzyWatchSyncRequest,
                connection);
        Assertions.assertNotNull(responseInitNotify);
        Thread.sleep(100L);
        Assertions.assertEquals(2, watcherFlag.get());
        try {
            Future<ListView<String>> newFuture = namingFuzzyWatchContext.createNewFuture();
            newFuture.get(100L, TimeUnit.MILLISECONDS);
            Assertions.fail();
        } catch (TimeoutException timeoutException) {
            Assertions.assertTrue(true);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        //init finish notify
        NamingFuzzyWatchSyncRequest namingFuzzyWatchSyncRequestFinish = new NamingFuzzyWatchSyncRequest(generatePattern,
                FINISH_FUZZY_WATCH_INIT_NOTIFY, null);
        Response responseInitNotifyFinish = namingFuzzyWatchNotifyRequestHandler.requestReply(
                namingFuzzyWatchSyncRequestFinish, connection);
        
        Assertions.assertNotNull(responseInitNotifyFinish);
        Thread.sleep(100L);
        Assertions.assertEquals(2, watcherFlag.get());
        try {
            Future<ListView<String>> newFuture = namingFuzzyWatchContext.createNewFuture();
            ListView<String> stringListView = newFuture.get();
            Assertions.assertTrue(
                    stringListView.getData().contains(serviceKey1) && stringListView.getData().contains(serviceKey2));
            
            Assertions.assertFalse(newFuture.isCancelled());
            Assertions.assertTrue(newFuture.isDone());
            try {
                newFuture.cancel(true);
                Assertions.fail();
            } catch (UnsupportedOperationException unsupportedOperationException) {
                Assertions.assertTrue(true);
            }
        } catch (Exception timeoutException) {
            Assertions.fail();
        }
        
        namingFuzzyWatchServiceListHolder.registerFuzzyWatcher(generatePattern, new AbstractFuzzyWatchEventWatcher() {
            @Override
            public void onEvent(FuzzyWatchChangeEvent event) {
                watcherFlag.incrementAndGet();
            }
        });
        Thread.sleep(100L);
        Assertions.assertEquals(4, watcherFlag.get());
    }
    
    @Test
    void testOnEventLoadEvent() throws InterruptedException {
        
        String generatePattern = FuzzyGroupKeyPattern.generatePattern("serviceName124*", "group", "namespace123");
        
        AtomicInteger watcherPatternOverFlag = new AtomicInteger(0);
        AtomicInteger watcherServiceOverFlag = new AtomicInteger(0);
        
        namingFuzzyWatchServiceListHolder.registerFuzzyWatcher(generatePattern, new AbstractFuzzyWatchEventWatcher() {
            @Override
            public void onEvent(FuzzyWatchChangeEvent event) {
            
            }
            
            @Override
            public void onPatternOverLimit() {
                watcherPatternOverFlag.incrementAndGet();
            }
            
            @Override
            public void onServiceReachUpLimit() {
                watcherServiceOverFlag.incrementAndGet();
            }
        });
        
        NamingFuzzyWatchLoadEvent namingFuzzyWatchLoadEvent = NamingFuzzyWatchLoadEvent.buildEvent(
                FUZZY_WATCH_PATTERN_OVER_LIMIT.getCode(), generatePattern, eventScope);
        namingFuzzyWatchServiceListHolder.onEvent(namingFuzzyWatchLoadEvent);
        Assertions.assertEquals(1, watcherPatternOverFlag.get());
        Assertions.assertEquals(0, watcherServiceOverFlag.get());
        
        NamingFuzzyWatchLoadEvent namingFuzzyWatchLoadEventDup = NamingFuzzyWatchLoadEvent.buildEvent(
                FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT.getCode(), generatePattern, eventScope);
        namingFuzzyWatchServiceListHolder.onEvent(namingFuzzyWatchLoadEventDup);
        Assertions.assertEquals(1, watcherPatternOverFlag.get());
        Assertions.assertEquals(0, watcherServiceOverFlag.get());
        NamingFuzzyWatchContext namingFuzzyWatchContext = namingFuzzyWatchServiceListHolder.getFuzzyWatchContext(
                generatePattern);
        namingFuzzyWatchContext.clearOverLimitTs();
        NamingFuzzyWatchLoadEvent namingFuzzyWatchLoadEvent2 = NamingFuzzyWatchLoadEvent.buildEvent(
                FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT.getCode(), generatePattern, eventScope);
        namingFuzzyWatchServiceListHolder.onEvent(namingFuzzyWatchLoadEvent2);
        Assertions.assertEquals(1, watcherPatternOverFlag.get());
        Assertions.assertEquals(1, watcherServiceOverFlag.get());
    }
    
    @Test
    void testExecuteNamingFuzzyWatch() throws NacosException, InterruptedException {
        String generatePattern = FuzzyGroupKeyPattern.generatePattern("serviceName124*", "group", "namespace123");
        
        AtomicInteger watcherPatternOverFlag = new AtomicInteger(0);
        AtomicInteger watcherServiceOverFlag = new AtomicInteger(0);
        
        AbstractFuzzyWatchEventWatcher abstractFuzzyWatchEventWatcher = new AbstractFuzzyWatchEventWatcher() {
            @Override
            public void onEvent(FuzzyWatchChangeEvent event) {
            
            }
            
            @Override
            public void onPatternOverLimit() {
                watcherPatternOverFlag.incrementAndGet();
            }
            
            @Override
            public void onServiceReachUpLimit() {
                watcherServiceOverFlag.incrementAndGet();
            }
            
        };
        
        NamingFuzzyWatchContext namingFuzzyWatchContext = namingFuzzyWatchServiceListHolder.registerFuzzyWatcher(
                generatePattern, abstractFuzzyWatchEventWatcher);
        Assertions.assertFalse(namingFuzzyWatchContext.isConsistentWithServer());
        
        //check success fuzzy watch
        when(namingGrpcClientProxy.fuzzyWatchRequest(any(NamingFuzzyWatchRequest.class))).thenReturn(
                NamingFuzzyWatchResponse.buildSuccessResponse());
        namingFuzzyWatchServiceListHolder.executeNamingFuzzyWatch();
        Assertions.assertTrue(namingFuzzyWatchContext.isConsistentWithServer());
        
        //check sync skip
        namingFuzzyWatchServiceListHolder.executeNamingFuzzyWatch();
        
        namingFuzzyWatchServiceListHolder.resetConsistenceStatus();
        //check over fuzzy watch pattern count
        when(namingGrpcClientProxy.fuzzyWatchRequest(any(NamingFuzzyWatchRequest.class))).thenThrow(
                new NacosException(FUZZY_WATCH_PATTERN_OVER_LIMIT.getCode(), FUZZY_WATCH_PATTERN_OVER_LIMIT.getMsg()));
        namingFuzzyWatchServiceListHolder.executeNamingFuzzyWatch();
        Thread.sleep(1000L);
        Assertions.assertEquals(1, watcherPatternOverFlag.get());
        Assertions.assertEquals(0, watcherServiceOverFlag.get());
        
        namingFuzzyWatchContext.clearOverLimitTs();
        //check over fuzzy watch service count
        when(namingGrpcClientProxy.fuzzyWatchRequest(any(NamingFuzzyWatchRequest.class))).thenThrow(
                new NacosException(FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT.getCode(),
                        FUZZY_WATCH_PATTERN_MATCH_COUNT_OVER_LIMIT.getMsg()));
        namingFuzzyWatchServiceListHolder.executeNamingFuzzyWatch();
        Thread.sleep(100L);
        Assertions.assertEquals(1, watcherPatternOverFlag.get());
        Assertions.assertEquals(1, watcherServiceOverFlag.get());
        
        when(namingGrpcClientProxy.fuzzyWatchRequest(any(NamingFuzzyWatchRequest.class))).thenThrow(
                new NacosException(500, "unknow"));
        namingFuzzyWatchServiceListHolder.executeNamingFuzzyWatch();
        
        //check cancel fuzzy watch
        namingFuzzyWatchContext.removeWatcher(abstractFuzzyWatchEventWatcher);
        when(namingGrpcClientProxy.fuzzyWatchRequest(any(NamingFuzzyWatchRequest.class))).thenReturn(
                NamingFuzzyWatchResponse.buildSuccessResponse());
        namingFuzzyWatchServiceListHolder.executeNamingFuzzyWatch();
        Assertions.assertNull(namingFuzzyWatchServiceListHolder.getFuzzyWatchContext(generatePattern));
        
    }
    
    @Test
    void testSyncWhenWatcherFail() throws NacosException {
        
        String serviceKey = NamingUtils.getServiceKey("namespace123", "group", "serviceName124");
        
        String generatePattern = FuzzyGroupKeyPattern.generatePattern("serviceName124*", "group", "namespace123");
        
        AtomicInteger watcherFlag = new AtomicInteger(0);
        
        AbstractFuzzyWatchEventWatcher abstractFuzzyWatchEventWatcher = new AbstractFuzzyWatchEventWatcher() {
            @Override
            public void onEvent(FuzzyWatchChangeEvent event) {
                int get = watcherFlag.incrementAndGet();
                if (get < 2) {
                    System.out.println("times " + get + " fail");
                    throw new RuntimeException("mock exception");
                } else {
                    System.out.println("times " + get + " success");
                }
            }
        };
        
        NamingFuzzyWatchContext namingFuzzyWatchContext = namingFuzzyWatchServiceListHolder.registerFuzzyWatcher(
                generatePattern, abstractFuzzyWatchEventWatcher);
        
        NamingFuzzyWatchChangeNotifyRequest namingFuzzyWatchChangeNotifyRequest = new NamingFuzzyWatchChangeNotifyRequest(
                serviceKey, ADD_SERVICE);
        namingFuzzyWatchNotifyRequestHandler.requestReply(namingFuzzyWatchChangeNotifyRequest, connection);
        //notify 1, fail
        namingFuzzyWatchContext.syncFuzzyWatchers();
        //notify 2,success
        namingFuzzyWatchContext.syncFuzzyWatchers();
        //notify 3 +, will not trigger watchers.
        namingFuzzyWatchContext.syncFuzzyWatchers();
        namingFuzzyWatchContext.syncFuzzyWatchers();
        namingFuzzyWatchContext.syncFuzzyWatchers();
        //expect  2 times notified
        Assertions.assertEquals(2, watcherFlag.get());
    }
    
    @Test
    void testFuzzyWatchNotSupport() {
        when(namingGrpcClientProxy.isAbilitySupportedByServer(AbilityKey.SERVER_FUZZY_WATCH)).thenReturn(false);
        Assertions.assertThrows(NacosRuntimeException.class, () -> {
            namingFuzzyWatchServiceListHolder.registerFuzzyWatcher("*", new AbstractFuzzyWatchEventWatcher() {
                @Override
                public void onEvent(FuzzyWatchChangeEvent event) {
                
                }
            });
        });
    }
}
