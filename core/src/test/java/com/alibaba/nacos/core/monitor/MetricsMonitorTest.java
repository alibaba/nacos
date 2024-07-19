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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * {@link MetricsMonitor} and {@link NacosMeterRegistryCenter} unit tests.
 *
 * @author chenglu
 * @author <a href="mailto:liuyixiao0821@gmail.com">liuyixiao</a>
 */
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class MetricsMonitorTest {
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @BeforeEach
    void initMeterRegistry() {
        ApplicationUtils.injectContext(context);
        when(context.getBean(PrometheusMeterRegistry.class)).thenReturn(null);
        // add simple meterRegistry.
        NacosMeterRegistryCenter.getMeterRegistry(NacosMeterRegistryCenter.CORE_STABLE_REGISTRY).add(new SimpleMeterRegistry());
    }
    
    @Test
    void testSdkServerExecutorMetric() {
        MetricsMonitor.getSdkServerExecutorMetric().getPoolSize().set(1);
        MetricsMonitor.getSdkServerExecutorMetric().getMaximumPoolSize().set(1);
        MetricsMonitor.getSdkServerExecutorMetric().getCorePoolSize().set(1);
        MetricsMonitor.getSdkServerExecutorMetric().getActiveCount().set(1);
        MetricsMonitor.getSdkServerExecutorMetric().getInQueueTaskCount().set(1);
        MetricsMonitor.getSdkServerExecutorMetric().getTaskCount().set(1);
        MetricsMonitor.getSdkServerExecutorMetric().getCompletedTaskCount().set(1);
        assertEquals("grpcSdkServer", MetricsMonitor.getSdkServerExecutorMetric().getType());
        assertEquals(1, MetricsMonitor.getSdkServerExecutorMetric().getPoolSize().get());
        assertEquals(1, MetricsMonitor.getSdkServerExecutorMetric().getMaximumPoolSize().get());
        assertEquals(1, MetricsMonitor.getSdkServerExecutorMetric().getCorePoolSize().get());
        assertEquals(1, MetricsMonitor.getSdkServerExecutorMetric().getActiveCount().get());
        assertEquals(1, MetricsMonitor.getSdkServerExecutorMetric().getInQueueTaskCount().get());
        assertEquals(1, MetricsMonitor.getSdkServerExecutorMetric().getTaskCount().get());
        assertEquals(1, MetricsMonitor.getSdkServerExecutorMetric().getCompletedTaskCount().get());
    }
    
    @Test
    void testClusterServerExecutorMetric() {
        MetricsMonitor.getClusterServerExecutorMetric().getPoolSize().set(1);
        MetricsMonitor.getClusterServerExecutorMetric().getMaximumPoolSize().set(1);
        MetricsMonitor.getClusterServerExecutorMetric().getCorePoolSize().set(1);
        MetricsMonitor.getClusterServerExecutorMetric().getActiveCount().set(1);
        MetricsMonitor.getClusterServerExecutorMetric().getInQueueTaskCount().set(1);
        MetricsMonitor.getClusterServerExecutorMetric().getTaskCount().set(1);
        MetricsMonitor.getClusterServerExecutorMetric().getCompletedTaskCount().set(1);
        assertEquals("grpcClusterServer", MetricsMonitor.getClusterServerExecutorMetric().getType());
        assertEquals(1, MetricsMonitor.getClusterServerExecutorMetric().getPoolSize().get());
        assertEquals(1, MetricsMonitor.getClusterServerExecutorMetric().getMaximumPoolSize().get());
        assertEquals(1, MetricsMonitor.getClusterServerExecutorMetric().getCorePoolSize().get());
        assertEquals(1, MetricsMonitor.getClusterServerExecutorMetric().getActiveCount().get());
        assertEquals(1, MetricsMonitor.getClusterServerExecutorMetric().getInQueueTaskCount().get());
        assertEquals(1, MetricsMonitor.getClusterServerExecutorMetric().getTaskCount().get());
        assertEquals(1, MetricsMonitor.getClusterServerExecutorMetric().getCompletedTaskCount().get());
    }
    
    @Test
    void testGetLongConnectionMonitor() {
        AtomicInteger atomicInteger = MetricsMonitor.getLongConnectionMonitor();
        assertEquals(0, atomicInteger.get());
    }
    
    @Test
    void testRaftReadIndexFailed() {
        MetricsMonitor.raftReadIndexFailed();
        MetricsMonitor.raftReadIndexFailed();
        assertEquals(2D, MetricsMonitor.getRaftReadIndexFailed().totalAmount(), 0.01);
    }
    
    @Test
    void testRaftReadFromLeader() {
        MetricsMonitor.raftReadFromLeader();
        assertEquals(1D, MetricsMonitor.getRaftFromLeader().totalAmount(), 0.01);
    }
    
    @Test
    void testRaftApplyLogTimer() {
        Timer raftApplyTimerLog = MetricsMonitor.getRaftApplyLogTimer();
        raftApplyTimerLog.record(10, TimeUnit.SECONDS);
        raftApplyTimerLog.record(20, TimeUnit.SECONDS);
        assertEquals(0.5D, raftApplyTimerLog.totalTime(TimeUnit.MINUTES), 0.01);
        
        assertEquals(30D, raftApplyTimerLog.totalTime(TimeUnit.SECONDS), 0.01);
    }
    
    @Test
    void testRaftApplyReadTimer() {
        Timer raftApplyReadTimer = MetricsMonitor.getRaftApplyReadTimer();
        raftApplyReadTimer.record(10, TimeUnit.SECONDS);
        raftApplyReadTimer.record(20, TimeUnit.SECONDS);
        assertEquals(0.5D, raftApplyReadTimer.totalTime(TimeUnit.MINUTES), 0.01);
        
        assertEquals(30D, raftApplyReadTimer.totalTime(TimeUnit.SECONDS), 0.01);
    }
    
    @Test
    void testRefreshModuleConnectionCount() {
        // refresh
        Map<String, Integer> map = new HashMap<>();
        map.put("naming", 10);
        MetricsMonitor.refreshModuleConnectionCount(map);
        assertEquals(1, MetricsMonitor.getModuleConnectionCnt().size());
        assertEquals(10, MetricsMonitor.getModuleConnectionCnt().get("naming").get());
        
        // refresh again
        map = new HashMap<>();
        map.put("naming", 11);
        map.put("config", 1);
        MetricsMonitor.refreshModuleConnectionCount(map);
        assertEquals(2, MetricsMonitor.getModuleConnectionCnt().size());
        assertEquals(11, MetricsMonitor.getModuleConnectionCnt().get("naming").get());
        assertEquals(1, MetricsMonitor.getModuleConnectionCnt().get("config").get());
        
        // refresh again
        map = new HashMap<>();
        map.put("naming", 1);
        MetricsMonitor.refreshModuleConnectionCount(map);
        assertEquals(2, MetricsMonitor.getModuleConnectionCnt().size());
        assertEquals(1, MetricsMonitor.getModuleConnectionCnt().get("naming").get());
        assertEquals(0, MetricsMonitor.getModuleConnectionCnt().get("config").get());
    }
}
