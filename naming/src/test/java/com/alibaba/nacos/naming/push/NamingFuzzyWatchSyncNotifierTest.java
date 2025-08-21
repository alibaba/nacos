/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.push;

import com.alibaba.nacos.api.naming.utils.NamingUtils;
import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.naming.core.v2.event.client.ClientOperationEvent;
import com.alibaba.nacos.naming.core.v2.index.NamingFuzzyWatchContextService;
import com.alibaba.nacos.naming.push.v2.task.FuzzyWatchPushDelayTaskEngine;
import com.alibaba.nacos.naming.push.v2.task.FuzzyWatchSyncNotifyTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static com.alibaba.nacos.naming.push.NamingFuzzyWatchSyncNotifier.BATCH_SIZE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class NamingFuzzyWatchSyncNotifierTest {
    
    @Mock
    private NamingFuzzyWatchContextService namingFuzzyWatchContextService;
    
    @Mock
    private FuzzyWatchPushDelayTaskEngine fuzzyWatchPushDelayTaskEngine;
    
    NamingFuzzyWatchSyncNotifier namingFuzzyWatchSyncNotifier;
    
    @BeforeEach
    void before() {
        namingFuzzyWatchSyncNotifier = new NamingFuzzyWatchSyncNotifier(namingFuzzyWatchContextService,
                fuzzyWatchPushDelayTaskEngine);
    }
    
    @AfterEach
    void after() {
    }
    
    @Test
    void testOnClientFuzzyWatchEventInit() {
        
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("service*", "group123", "namespace");
        
        Set<String> clientReceivedServiceKeys = new HashSet<>();
        for (int i = 0; i < BATCH_SIZE * 2; i++) {
            clientReceivedServiceKeys.add(NamingUtils.getServiceKey("namespace", "group123" + i, "service" + i));
        }
        Set<String> matchedServiceKeys = new HashSet<>();
        for (int i = BATCH_SIZE; i < BATCH_SIZE * 3; i++) {
            matchedServiceKeys.add(NamingUtils.getServiceKey("namespace", "group123" + i, "service" + i));
        }
        
        when(namingFuzzyWatchContextService.matchServiceKeys(eq(groupKeyPattern))).thenReturn(matchedServiceKeys);
        
        when(namingFuzzyWatchContextService.reachToUpLimit(eq(groupKeyPattern))).thenReturn(false);
        String clientId = "onn1234";
        boolean isInitializing = true;
        ClientOperationEvent.ClientFuzzyWatchEvent clientFuzzyWatchEvent = new ClientOperationEvent.ClientFuzzyWatchEvent(
                groupKeyPattern, clientId, clientReceivedServiceKeys, isInitializing);
        namingFuzzyWatchSyncNotifier.onEvent(clientFuzzyWatchEvent);
        
        verify(fuzzyWatchPushDelayTaskEngine, times(2)).addTask(anyString(), any(FuzzyWatchSyncNotifyTask.class));
    }
    
    @Test
    void testOnClientFuzzyWatchEventInitFinish() {
        
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("service*", "group123", "namespace");
        
        String clientId = "onn1234";
        Set<String> clientReceivedServiceKeys = new HashSet<>();
        
        boolean isInitializing = true;
        
        Set<String> matchedServiceKeys = new HashSet<>();
        
        when(namingFuzzyWatchContextService.matchServiceKeys(eq(groupKeyPattern))).thenReturn(matchedServiceKeys);
        
        when(namingFuzzyWatchContextService.reachToUpLimit(eq(groupKeyPattern))).thenReturn(false);
        ClientOperationEvent.ClientFuzzyWatchEvent clientFuzzyWatchEvent = new ClientOperationEvent.ClientFuzzyWatchEvent(
                groupKeyPattern, clientId, clientReceivedServiceKeys, isInitializing);
        namingFuzzyWatchSyncNotifier.onEvent(clientFuzzyWatchEvent);
        
        verify(fuzzyWatchPushDelayTaskEngine, times(1)).addTask(anyString(), any(FuzzyWatchSyncNotifyTask.class));
    }
    
    @Test
    void testOnClientFuzzyWatchEventDiffSync() {
        
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("service*", "group123", "namespace");
        
        Set<String> clientReceivedServiceKeys = new HashSet<>();
        for (int i = 0; i < BATCH_SIZE * 2; i++) {
            clientReceivedServiceKeys.add(NamingUtils.getServiceKey("namespace", "group123" + i, "service" + i));
        }
        
        Set<String> matchedServiceKeys = new HashSet<>();
        for (int i = BATCH_SIZE; i < BATCH_SIZE * 3; i++) {
            matchedServiceKeys.add(NamingUtils.getServiceKey("namespace", "group123" + i, "service" + i));
        }
        
        when(namingFuzzyWatchContextService.matchServiceKeys(eq(groupKeyPattern))).thenReturn(matchedServiceKeys);
        when(namingFuzzyWatchContextService.reachToUpLimit(eq(groupKeyPattern))).thenReturn(false);
        String clientId = "onn1234";
        boolean isInitializing = false;
        ClientOperationEvent.ClientFuzzyWatchEvent clientFuzzyWatchEvent = new ClientOperationEvent.ClientFuzzyWatchEvent(
                groupKeyPattern, clientId, clientReceivedServiceKeys, isInitializing);
        namingFuzzyWatchSyncNotifier.onEvent(clientFuzzyWatchEvent);
        verify(fuzzyWatchPushDelayTaskEngine, times(2)).addTask(anyString(), any(FuzzyWatchSyncNotifyTask.class));
        
    }
    
    @Test
    void testOnClientFuzzyWatchEventWhenOverLoadModel() {
        
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("service*", "group123", "namespace");
        
        Set<String> clientReceivedServiceKeys = new HashSet<>();
        for (int i = 0; i < BATCH_SIZE * 2; i++) {
            clientReceivedServiceKeys.add(NamingUtils.getServiceKey("namespace", "group123" + i, "service" + i));
        }
        
        Set<String> matchedServiceKeys = new HashSet<>();
        for (int i = BATCH_SIZE; i < BATCH_SIZE * 3; i++) {
            matchedServiceKeys.add(NamingUtils.getServiceKey("namespace", "group123" + i, "service" + i));
        }
        
        when(namingFuzzyWatchContextService.matchServiceKeys(eq(groupKeyPattern))).thenReturn(matchedServiceKeys);
        
        when(namingFuzzyWatchContextService.reachToUpLimit(eq(groupKeyPattern))).thenReturn(true);
        String clientId = "onn1234";
        boolean isInitializing = false;
    
        ClientOperationEvent.ClientFuzzyWatchEvent clientFuzzyWatchEvent = new ClientOperationEvent.ClientFuzzyWatchEvent(
                groupKeyPattern, clientId, clientReceivedServiceKeys, isInitializing);
        namingFuzzyWatchSyncNotifier.onEvent(clientFuzzyWatchEvent);
        verify(fuzzyWatchPushDelayTaskEngine, times(1)).addTask(anyString(), any(FuzzyWatchSyncNotifyTask.class));
        
    }
}
