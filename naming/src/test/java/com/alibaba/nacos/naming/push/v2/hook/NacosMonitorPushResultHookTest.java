/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.push.v2.hook;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.ServiceInfo;
import com.alibaba.nacos.naming.monitor.MetricsMonitor;
import com.alibaba.nacos.naming.pojo.Subscriber;
import com.alibaba.nacos.plugin.control.tps.TpsControlManager;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * test for NacosMonitorPushResultHook.
 *
 * @author <a href="mailto:liuyixiao0821@gmail.com">liuyixiao</a>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class NacosMonitorPushResultHookTest {
    
    @Mock
    private PushResult pushResult;
    
    @Mock
    private Subscriber subscriber;
    
    @Mock
    private TpsControlManager tpsControlManager;
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @Mock
    private Instance instance;
    
    private final ServiceInfo serviceInfo = new ServiceInfo("name", "cluster");
    
    private final long allCost = 100L;
    
    @Before
    public void setUp() {
        MetricsMonitor.resetAll();
        serviceInfo.setHosts(new ArrayList<>());
        subscriber.setIp("0.0.0.0");
        when(instance.getWeight()).thenReturn(1.0);
        when(instance.isHealthy()).thenReturn(true);
        when(pushResult.getAllCost()).thenReturn(allCost);
        when(pushResult.getData()).thenReturn(serviceInfo);
        when(pushResult.getSubscriber()).thenReturn(subscriber);
        ApplicationUtils.injectContext(context);
        when(context.getBean(TpsControlManager.class)).thenReturn(tpsControlManager);
    }
    
    @Test
    public void testPushSuccessForEmptyPush() {
        new NacosMonitorPushResultHook().pushSuccess(pushResult);
        assertEquals(1, MetricsMonitor.getTotalPushMonitor().get());
        assertEquals(1, MetricsMonitor.getEmptyPushMonitor().get());
        assertEquals(allCost, MetricsMonitor.getMaxPushCostMonitor().get());
    }
    
    @Test
    public void testPushSuccessForNoEmptyPush() {
        ArrayList<Instance> hosts = new ArrayList<>();
        hosts.add(instance);
        serviceInfo.setHosts(hosts);
        new NacosMonitorPushResultHook().pushSuccess(pushResult);
        assertEquals(1, MetricsMonitor.getTotalPushMonitor().get());
        assertEquals(0, MetricsMonitor.getEmptyPushMonitor().get());
        assertEquals(allCost, MetricsMonitor.getMaxPushCostMonitor().get());
    }
    
    @Test
    public void testPushFailed() {
        new NacosMonitorPushResultHook().pushFailed(pushResult);
        assertEquals(1, MetricsMonitor.getFailedPushMonitor().get());
    }
}
