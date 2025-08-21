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

import com.alibaba.nacos.common.utils.FuzzyGroupKeyPattern;
import com.alibaba.nacos.config.server.configuration.ConfigCommonConfig;
import com.alibaba.nacos.config.server.model.event.ConfigFuzzyWatchEvent;
import com.alibaba.nacos.config.server.service.ConfigFuzzyWatchContextService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RpcPushService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConfigFuzzyWatchSyncNotifierTest {
    
    ConfigFuzzyWatchSyncNotifier configFuzzyWatchSyncNotifier;
    
    @Mock
    ConnectionManager connectionManager;
    
    @Mock
    RpcPushService rpcPushService;
    
    @Mock
    ConfigFuzzyWatchContextService configFuzzyWatchContextService;
    
    MockedStatic<ConfigExecutor> tMockedStatic;
    
    @AfterEach
    void after() {
        tMockedStatic.close();
    }
    
    @BeforeEach
    void setUp() throws IOException {
        tMockedStatic = Mockito.mockStatic(ConfigExecutor.class);
        configFuzzyWatchSyncNotifier = new ConfigFuzzyWatchSyncNotifier(connectionManager, rpcPushService,
                configFuzzyWatchContextService);
    }
    
    @Test
    void testInitNotifyWithoutMatchGroupKeys() {
        String connectionId = "conn12345678";
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("dataId*", "group", "tnnt1234");
        ConfigFuzzyWatchEvent configFuzzyWatchEvent = new ConfigFuzzyWatchEvent(connectionId, null, groupKeyPattern,
                true);
        configFuzzyWatchSyncNotifier.onEvent(configFuzzyWatchEvent);
        tMockedStatic.verify(
                () -> ConfigExecutor.scheduleClientConfigNotifier(any(FuzzyWatchSyncNotifyTask.class), eq(0L),
                        eq(TimeUnit.SECONDS)), times(1));
        
    }
    
    @Test
    void testInitNotifyWithMatchGroupKeys() {
        
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("dataId*", "group", "tnnt1234");
        Set<String> matchGroupKeys = new HashSet<>();
        int batchSize = ConfigCommonConfig.getInstance().getBatchSize();
        for (int i = batchSize; i < batchSize * 2; i++) {
            matchGroupKeys.add(GroupKey.getKeyTenant("dataId" + i, "group", "tnnt1234"));
        }
        Set<String> clientMatchGroupKeys = new HashSet<>();
        for (int i = 0; i < batchSize; i++) {
            clientMatchGroupKeys.add(GroupKey.getKeyTenant("dataId" + i, "group", "tnnt1234"));
        }
        
        when(configFuzzyWatchContextService.matchGroupKeys(groupKeyPattern)).thenReturn(matchGroupKeys);
        when(configFuzzyWatchContextService.reachToUpLimit(eq(groupKeyPattern))).thenReturn(false);
        String connectionId = "conn12345678";
    
        ConfigFuzzyWatchEvent configFuzzyWatchEvent = new ConfigFuzzyWatchEvent(connectionId, clientMatchGroupKeys,
                groupKeyPattern, true);
        configFuzzyWatchSyncNotifier.onEvent(configFuzzyWatchEvent);
        tMockedStatic.verify(
                () -> ConfigExecutor.scheduleClientConfigNotifier(any(FuzzyWatchSyncNotifyTask.class), eq(0L),
                        eq(TimeUnit.SECONDS)), times(2));
        
    }
    
    @Test
    void testInitNotifyWithMatchGroupKeysOnDeleteProtection() {
        
        String groupKeyPattern = FuzzyGroupKeyPattern.generatePattern("dataId*", "group", "tnnt1234");
        
        Set<String> matchGroupKeys = new HashSet<>();
        int batchSize = ConfigCommonConfig.getInstance().getBatchSize();
        for (int i = batchSize; i < batchSize * 2; i++) {
            matchGroupKeys.add(GroupKey.getKeyTenant("dataId" + i, "group", "tnnt1234"));
        }
        Set<String> clientMatchGroupKeys = new HashSet<>();
        for (int i = 0; i < batchSize; i++) {
            clientMatchGroupKeys.add(GroupKey.getKeyTenant("dataId" + i, "group", "tnnt1234"));
        }
        
        when(configFuzzyWatchContextService.matchGroupKeys(groupKeyPattern)).thenReturn(matchGroupKeys);
        when(configFuzzyWatchContextService.reachToUpLimit(eq(groupKeyPattern))).thenReturn(true);
        String connectionId = "conn12345678";
        ConfigFuzzyWatchEvent configFuzzyWatchEvent = new ConfigFuzzyWatchEvent(connectionId, clientMatchGroupKeys,
                groupKeyPattern, true);
        configFuzzyWatchSyncNotifier.onEvent(configFuzzyWatchEvent);
        tMockedStatic.verify(
                () -> ConfigExecutor.scheduleClientConfigNotifier(any(FuzzyWatchSyncNotifyTask.class), eq(0L),
                        eq(TimeUnit.SECONDS)), times(1));
        
    }
    
}
