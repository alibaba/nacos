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

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link MetricsMonitor} and {@link NacosMeterRegistry} unit tests.
 *
 * @author chenglu
 * @date 2021-06-15 22:58
 */
public class MetricsMonitorTest {
    
    @Before
    public void initMeterRegistry() {
        NacosMeterRegistry.getMeterRegistry().add(new SimpleMeterRegistry());
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
}
