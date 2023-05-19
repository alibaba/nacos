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

import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RpcPushService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class RpcConfigChangeNotifierTest {
    
    private RpcConfigChangeNotifier rpcConfigChangeNotifier;
    
    @Mock
    private ConfigChangeListenContext configChangeListenContext;
    
    @Mock
    private RpcPushService rpcPushService;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Before
    public void setUp() {
        rpcConfigChangeNotifier = new RpcConfigChangeNotifier();
        
        ReflectionTestUtils.setField(rpcConfigChangeNotifier, "configChangeListenContext", configChangeListenContext);
        ReflectionTestUtils.setField(rpcConfigChangeNotifier, "rpcPushService", rpcPushService);
        ReflectionTestUtils.setField(rpcConfigChangeNotifier, "connectionManager", connectionManager);
    }
    
    @Test
    public void testOnEvent() {
        final String groupKey = GroupKey2.getKey("nacos.internal.tps.control_rule_1", "nacos", "tenant");
        final String limitGroupKey = GroupKey2
                .getKey("nacos.internal.tps.nacos.internal.connection.limit.rule", "nacos", "tenant");
        List<String> betaIps = new ArrayList<>();
        
        betaIps.add("1.1.1.1");
        rpcConfigChangeNotifier.onEvent(new LocalDataChangeEvent(groupKey, true, betaIps));
        rpcConfigChangeNotifier.onEvent(new LocalDataChangeEvent(limitGroupKey));
    }
}
