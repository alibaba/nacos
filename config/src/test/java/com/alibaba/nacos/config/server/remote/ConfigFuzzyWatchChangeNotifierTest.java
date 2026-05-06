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

import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.ConfigFuzzyWatchContextService;
import com.alibaba.nacos.config.server.utils.ConfigExecutor;
import com.alibaba.nacos.config.server.utils.GroupKey;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.core.remote.grpc.GrpcConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.alibaba.nacos.api.common.Constants.ConfigChangedType.CONFIG_CHANGED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ConfigFuzzyWatchChangeNotifierTest {
    
    ConfigFuzzyWatchChangeNotifier configFuzzyWatchChangeNotifier;
    
    @Mock
    ConnectionManager connectionManager;
    
    @Mock
    RpcPushService rpcPushService;
    
    @Mock
    ConfigFuzzyWatchContextService configFuzzyWatchContextService;
    
    MockedStatic<ConfigExecutor> tMockedStatic;
    
    MockedStatic<ConfigCacheService> configCacheServiceMockedStatic;
    
    @AfterEach
    void after() {
        tMockedStatic.close();
        configCacheServiceMockedStatic.close();
    }
    
    @BeforeEach
    void setUp() throws IOException {
        tMockedStatic = Mockito.mockStatic(ConfigExecutor.class);
        configCacheServiceMockedStatic = Mockito.mockStatic(ConfigCacheService.class);
        configFuzzyWatchChangeNotifier = new ConfigFuzzyWatchChangeNotifier(connectionManager, rpcPushService,
                configFuzzyWatchContextService);
    }
    
    @Test
    void testOnConfigAdd() {
        
        String groupKey = GroupKey.getKeyTenant("data1234", "group", "tnnt1234");
        when(configFuzzyWatchContextService.syncGroupKeyContext(eq(groupKey), eq(CONFIG_CHANGED))).thenReturn(true);
        
        CacheItem cacheItem = Mockito.mock(CacheItem.class);
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.getContentCache(eq(groupKey)))
                .thenReturn(cacheItem);
        
        String connectionId = "123456";
        when(configFuzzyWatchContextService.getMatchedClients(eq(groupKey))).thenReturn(
                Collections.singleton(connectionId));
        GrpcConnection grpcConnection = Mockito.mock(GrpcConnection.class);
        when(connectionManager.getConnection(eq(connectionId))).thenReturn(grpcConnection);
        
        LocalDataChangeEvent localDataChangeEvent = new LocalDataChangeEvent(groupKey);
        configFuzzyWatchChangeNotifier.onEvent(localDataChangeEvent);
        
        tMockedStatic.verify(
                () -> ConfigExecutor.scheduleClientConfigNotifier(any(FuzzyWatchChangeNotifyTask.class), eq(0L),
                        eq(TimeUnit.SECONDS)), times(1));
        
    }
    
    @Test
    void testOnEmptyConnection() {
        
        String groupKey = GroupKey.getKeyTenant("data1234", "group", "tnnt1234");
        when(configFuzzyWatchContextService.syncGroupKeyContext(eq(groupKey), eq(CONFIG_CHANGED))).thenReturn(true);
        
        CacheItem cacheItem = Mockito.mock(CacheItem.class);
        configCacheServiceMockedStatic.when(() -> ConfigCacheService.getContentCache(eq(groupKey)))
                .thenReturn(cacheItem);
        
        String connectionId = "123456";
        when(configFuzzyWatchContextService.getMatchedClients(eq(groupKey))).thenReturn(
                Collections.singleton(connectionId));
        when(connectionManager.getConnection(eq(connectionId))).thenReturn(null);
        
        LocalDataChangeEvent localDataChangeEvent = new LocalDataChangeEvent(groupKey);
        configFuzzyWatchChangeNotifier.onEvent(localDataChangeEvent);
        
        tMockedStatic.verify(
                () -> ConfigExecutor.scheduleClientConfigNotifier(any(FuzzyWatchChangeNotifyTask.class), eq(0L),
                        eq(TimeUnit.SECONDS)), times(0));
        
    }
    
}
