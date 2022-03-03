package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.config.server.model.event.LocalDataChangeEvent;
import com.alibaba.nacos.config.server.utils.GroupKey2;
import com.alibaba.nacos.core.remote.ConnectionManager;
import com.alibaba.nacos.core.remote.RpcPushService;
import com.alibaba.nacos.core.remote.control.TpsMonitorManager;
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
    private TpsMonitorManager tpsMonitorManager;
    
    @Mock
    private ConfigChangeListenContext configChangeListenContext;
    
    @Mock
    private RpcPushService rpcPushService;
    
    @Mock
    private ConnectionManager connectionManager;
    
    @Before
    public void setUp() {
        rpcConfigChangeNotifier = new RpcConfigChangeNotifier();
        
        ReflectionTestUtils.setField(rpcConfigChangeNotifier, "tpsMonitorManager", tpsMonitorManager);
        ReflectionTestUtils.setField(rpcConfigChangeNotifier, "configChangeListenContext", configChangeListenContext);
        ReflectionTestUtils.setField(rpcConfigChangeNotifier, "rpcPushService", rpcPushService);
        ReflectionTestUtils.setField(rpcConfigChangeNotifier, "connectionManager", connectionManager);
    }
    
    @Test
    public void testOnEvent() {
        final String groupKey = GroupKey2.getKey("nacos.internal.tps.control_rule_1", "nacos", "tenant");
        final String limitGroupKey = GroupKey2.getKey("nacos.internal.tps.nacos.internal.connection.limit.rule", "nacos", "tenant");
        List<String> betaIps = new ArrayList<>();
        
        betaIps.add("1.1.1.1");
        rpcConfigChangeNotifier.onEvent(new LocalDataChangeEvent(groupKey, true, betaIps));
        rpcConfigChangeNotifier.onEvent(new LocalDataChangeEvent(limitGroupKey));
    }
}
