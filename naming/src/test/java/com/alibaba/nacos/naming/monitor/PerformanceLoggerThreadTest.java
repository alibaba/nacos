/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.monitor;

import com.alibaba.nacos.core.distributed.distro.monitor.DistroRecord;
import com.alibaba.nacos.core.distributed.distro.monitor.DistroRecordsHolder;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.v2.DistroClientDataProcessor;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.misc.GlobalExecutor;
import com.alibaba.nacos.naming.misc.NamingExecuteTaskDispatcher;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class PerformanceLoggerThreadTest {
    
    @AfterEach
    void tearDown() {
        MetricsMonitor.resetAll();
        MetricsMonitor.getDomCountMonitor().set(0);
        MetricsMonitor.getIpCountMonitor().set(0);
        MetricsMonitor.getSubscriberCount().set(0);
    }
    
    @Test
    void testInitSchedulesPerformanceLogger() {
        EnvUtil.setEnvironment(new MockEnvironment());
        try (MockedStatic<GlobalExecutor> mockedGlobalExecutor =
            Mockito.mockStatic(GlobalExecutor.class)) {
            
            new PerformanceLoggerThread().init();
            
            mockedGlobalExecutor.verify(() -> GlobalExecutor.schedulePerformanceLogger(
                any(Runnable.class), eq(30L), eq(60L), eq(TimeUnit.SECONDS)));
        }
    }
    
    @Test
    void testRefreshMetricsResetsHealthAndPushMetrics() {
        MetricsMonitor.getHttpHealthCheckMonitor().set(1);
        MetricsMonitor.getMysqlHealthCheckMonitor().set(2);
        MetricsMonitor.getTcpHealthCheckMonitor().set(3);
        MetricsMonitor.incrementPush();
        MetricsMonitor.incrementFailPush();
        MetricsMonitor.incrementEmptyPush();
        
        new PerformanceLoggerThread().refreshMetrics();
        
        assertEquals(0, MetricsMonitor.getHttpHealthCheckMonitor().get());
        assertEquals(0, MetricsMonitor.getMysqlHealthCheckMonitor().get());
        assertEquals(0, MetricsMonitor.getTcpHealthCheckMonitor().get());
        assertEquals(0, MetricsMonitor.getTotalPushMonitor().get());
        assertEquals(0, MetricsMonitor.getFailedPushMonitor().get());
        assertEquals(0, MetricsMonitor.getEmptyPushMonitor().get());
    }
    
    @Test
    void testCollectMetricsUpdatesServiceCountAndAvgPushCost() {
        MetricsMonitor.incrementPushCost(10);
        MetricsMonitor.incrementPushCost(20);
        PerformanceLoggerThread performanceLoggerThread = new PerformanceLoggerThread();
        
        performanceLoggerThread.collectMetrics();
        
        assertEquals(ServiceManager.getInstance().size(),
            MetricsMonitor.getDomCountMonitor().get());
        assertEquals(15, MetricsMonitor.getAvgPushCostMonitor().get());
    }
    
    @Test
    void testPerformanceLogTaskRunResetsRollingPushMetrics() {
        MetricsMonitor.getIpCountMonitor().set(2);
        MetricsMonitor.getSubscriberCount().set(3);
        MetricsMonitor.getMaxPushCostMonitor().set(99);
        MetricsMonitor.incrementPush();
        MetricsMonitor.incrementFailPush();
        MetricsMonitor.incrementPushCost(30);
        DistroRecord distroRecord =
            DistroRecordsHolder.getInstance().getRecord(DistroClientDataProcessor.TYPE);
        distroRecord.syncSuccess();
        distroRecord.syncFail();
        distroRecord.verifyFail();
        PerformanceLoggerThread performanceLoggerThread = new PerformanceLoggerThread();
        PerformanceLoggerThread.PerformanceLogTask task =
            performanceLoggerThread.new PerformanceLogTask();
        
        task.run();
        
        assertEquals(0, MetricsMonitor.getTotalPushCountForAvg().get());
        assertEquals(0, MetricsMonitor.getTotalPushCostForAvg().get());
        assertEquals(-1, MetricsMonitor.getMaxPushCostMonitor().get());
    }
    
    @Test
    void testPerformanceLogTaskRunCatchesException() {
        NamingExecuteTaskDispatcher dispatcher = Mockito.mock(NamingExecuteTaskDispatcher.class);
        Mockito.when(dispatcher.workersStatus()).thenThrow(new RuntimeException("failed"));
        PerformanceLoggerThread performanceLoggerThread = new PerformanceLoggerThread();
        PerformanceLoggerThread.PerformanceLogTask task =
            performanceLoggerThread.new PerformanceLogTask();
        
        try (MockedStatic<NamingExecuteTaskDispatcher> mockedDispatcher =
            Mockito.mockStatic(NamingExecuteTaskDispatcher.class)) {
            mockedDispatcher.when(NamingExecuteTaskDispatcher::getInstance).thenReturn(dispatcher);
            
            assertDoesNotThrow(task::run);
        }
    }
}
