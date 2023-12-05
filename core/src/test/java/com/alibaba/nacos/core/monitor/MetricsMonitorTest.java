/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.monitor;

import com.alibaba.nacos.sys.utils.ApplicationUtils;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.when;

/**
 * {@link MetricsMonitor} and {@link NacosMeterRegistryCenter} unit tests.
 *
 * @author chenglu
 * @author <a href="mailto:liuyixiao0821@gmail.com">liuyixiao</a>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class MetricsMonitorTest {
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @Before
    public void initMeterRegistry() {
        ApplicationUtils.injectContext(context);
        when(context.getBean(PrometheusMeterRegistry.class)).thenReturn(null);
        // add simple meterRegistry.
        NacosMeterRegistryCenter.getMeterRegistry(NacosMeterRegistryCenter.CORE_STABLE_REGISTRY)
                .add(new SimpleMeterRegistry());
    }

    @Test
    public void testSdkServerExecutorMetric() {
        MetricsMonitor.getSdkServerExecutorMetric().getPoolSize().set(1);
        MetricsMonitor.getSdkServerExecutorMetric().getMaximumPoolSize().set(1);
        MetricsMonitor.getSdkServerExecutorMetric().getCorePoolSize().set(1);
        MetricsMonitor.getSdkServerExecutorMetric().getActiveCount().set(1);
        MetricsMonitor.getSdkServerExecutorMetric().getInQueueTaskCount().set(1);
        MetricsMonitor.getSdkServerExecutorMetric().getTaskCount().set(1);
        MetricsMonitor.getSdkServerExecutorMetric().getCompletedTaskCount().set(1);
        Assert.assertEquals(MetricsMonitor.getSdkServerExecutorMetric().getType(), "grpcSdkServer");
        Assert.assertEquals(MetricsMonitor.getSdkServerExecutorMetric().getPoolSize().get(), 1);
        Assert.assertEquals(MetricsMonitor.getSdkServerExecutorMetric().getMaximumPoolSize().get(), 1);
        Assert.assertEquals(MetricsMonitor.getSdkServerExecutorMetric().getCorePoolSize().get(), 1);
        Assert.assertEquals(MetricsMonitor.getSdkServerExecutorMetric().getActiveCount().get(), 1);
        Assert.assertEquals(MetricsMonitor.getSdkServerExecutorMetric().getInQueueTaskCount().get(), 1);
        Assert.assertEquals(MetricsMonitor.getSdkServerExecutorMetric().getTaskCount().get(), 1);
        Assert.assertEquals(MetricsMonitor.getSdkServerExecutorMetric().getCompletedTaskCount().get(), 1);
    }

    @Test
    public void testClusterServerExecutorMetric() {
        MetricsMonitor.getClusterServerExecutorMetric().getPoolSize().set(1);
        MetricsMonitor.getClusterServerExecutorMetric().getMaximumPoolSize().set(1);
        MetricsMonitor.getClusterServerExecutorMetric().getCorePoolSize().set(1);
        MetricsMonitor.getClusterServerExecutorMetric().getActiveCount().set(1);
        MetricsMonitor.getClusterServerExecutorMetric().getInQueueTaskCount().set(1);
        MetricsMonitor.getClusterServerExecutorMetric().getTaskCount().set(1);
        MetricsMonitor.getClusterServerExecutorMetric().getCompletedTaskCount().set(1);
        Assert.assertEquals(MetricsMonitor.getClusterServerExecutorMetric().getType(), "grpcClusterServer");
        Assert.assertEquals(MetricsMonitor.getClusterServerExecutorMetric().getPoolSize().get(), 1);
        Assert.assertEquals(MetricsMonitor.getClusterServerExecutorMetric().getMaximumPoolSize().get(), 1);
        Assert.assertEquals(MetricsMonitor.getClusterServerExecutorMetric().getCorePoolSize().get(), 1);
        Assert.assertEquals(MetricsMonitor.getClusterServerExecutorMetric().getActiveCount().get(), 1);
        Assert.assertEquals(MetricsMonitor.getClusterServerExecutorMetric().getInQueueTaskCount().get(), 1);
        Assert.assertEquals(MetricsMonitor.getClusterServerExecutorMetric().getTaskCount().get(), 1);
        Assert.assertEquals(MetricsMonitor.getClusterServerExecutorMetric().getCompletedTaskCount().get(), 1);
    }
    
    @Test
    public void testGetLongConnectionMonitor() {
        AtomicInteger atomicInteger = MetricsMonitor.getLongConnectionMonitor();
        Assert.assertEquals(atomicInteger.get(), 0);
    }
    
    @Test
    public void testRaftReadIndexFailed() {
        MetricsMonitor.raftReadIndexFailed();
        MetricsMonitor.raftReadIndexFailed();
        Assert.assertEquals(2D, MetricsMonitor.getRaftReadIndexFailed().totalAmount(), 0.01);
    }
    
    @Test
    public void testRaftReadFromLeader() {
        MetricsMonitor.raftReadFromLeader();
        Assert.assertEquals(1D, MetricsMonitor.getRaftFromLeader().totalAmount(), 0.01);
    }
    
    @Test
    public void testRaftApplyLogTimer() {
        Timer raftApplyTimerLog = MetricsMonitor.getRaftApplyLogTimer();
        raftApplyTimerLog.record(10, TimeUnit.SECONDS);
        raftApplyTimerLog.record(20, TimeUnit.SECONDS);
        Assert.assertEquals(0.5D, raftApplyTimerLog.totalTime(TimeUnit.MINUTES), 0.01);
        
        Assert.assertEquals(30D, raftApplyTimerLog.totalTime(TimeUnit.SECONDS), 0.01);
    }
    
    @Test
    public void testRaftApplyReadTimer() {
        Timer raftApplyReadTimer = MetricsMonitor.getRaftApplyReadTimer();
        raftApplyReadTimer.record(10, TimeUnit.SECONDS);
        raftApplyReadTimer.record(20, TimeUnit.SECONDS);
        Assert.assertEquals(0.5D, raftApplyReadTimer.totalTime(TimeUnit.MINUTES), 0.01);
        
        Assert.assertEquals(30D, raftApplyReadTimer.totalTime(TimeUnit.SECONDS), 0.01);
    }

    @Test
    public void testRefreshModuleConnectionCount() {
        // refresh
        Map<String, Integer> map = new HashMap<>();
        map.put("naming", 10);
        MetricsMonitor.refreshModuleConnectionCount(map);
        Assert.assertEquals(1, MetricsMonitor.getModuleConnectionCnt().size());
        Assert.assertEquals(10, MetricsMonitor.getModuleConnectionCnt().get("naming").get());

        // refresh again
        map = new HashMap<>();
        map.put("naming", 11);
        map.put("config", 1);
        MetricsMonitor.refreshModuleConnectionCount(map);
        Assert.assertEquals(2, MetricsMonitor.getModuleConnectionCnt().size());
        Assert.assertEquals(11, MetricsMonitor.getModuleConnectionCnt().get("naming").get());
        Assert.assertEquals(1, MetricsMonitor.getModuleConnectionCnt().get("config").get());

        // refresh again
        map = new HashMap<>();
        map.put("naming", 1);
        MetricsMonitor.refreshModuleConnectionCount(map);
        Assert.assertEquals(2, MetricsMonitor.getModuleConnectionCnt().size());
        Assert.assertEquals(1, MetricsMonitor.getModuleConnectionCnt().get("naming").get());
        Assert.assertEquals(0, MetricsMonitor.getModuleConnectionCnt().get("config").get());
    }
}
