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

package com.alibaba.nacos.core.monitor;

import com.alibaba.nacos.core.remote.grpc.GrpcClusterServer;
import com.alibaba.nacos.core.remote.grpc.GrpcSdkServer;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GrpcServerThreadPoolMonitor} unit test.
 */
@ExtendWith(MockitoExtension.class)
class GrpcServerThreadPoolMonitorTest {
    
    @Mock
    private GrpcSdkServer sdkServer;
    
    @Mock
    private GrpcClusterServer clusterServer;
    
    @Test
    void configureTasksWhenDisabledDoesNotAddTask() {
        GrpcServerThreadPoolMonitor monitor = new GrpcServerThreadPoolMonitor();
        org.springframework.test.util.ReflectionTestUtils.setField(monitor, "sdkServer", sdkServer);
        org.springframework.test.util.ReflectionTestUtils.setField(monitor, "clusterServer",
            clusterServer);
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class)) {
            envMock
                .when(() -> EnvUtil.getProperty(eq("nacos.metric.grpc.server.executor.enabled"),
                    eq(Boolean.class), eq(true)))
                .thenReturn(false);
            monitor.configureTasks(registrar);
        }
        assertTrue(registrar.getFixedRateTaskList().isEmpty());
    }
    
    @Test
    void configureTasksWhenEnabledAddsFixedRateTask() {
        ThreadPoolExecutor sdkExecutor =
            new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        ThreadPoolExecutor clusterExecutor =
            new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        when(sdkServer.getRpcExecutor()).thenReturn(sdkExecutor);
        when(clusterServer.getRpcExecutor()).thenReturn(clusterExecutor);
        
        GrpcServerThreadPoolMonitor monitor = new GrpcServerThreadPoolMonitor();
        org.springframework.test.util.ReflectionTestUtils.setField(monitor, "sdkServer", sdkServer);
        org.springframework.test.util.ReflectionTestUtils.setField(monitor, "clusterServer",
            clusterServer);
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        
        try (MockedStatic<EnvUtil> envMock = mockStatic(EnvUtil.class)) {
            envMock
                .when(() -> EnvUtil.getProperty(eq("nacos.metric.grpc.server.executor.enabled"),
                    eq(Boolean.class), eq(true)))
                .thenReturn(true);
            envMock
                .when(() -> EnvUtil.getProperty(eq("nacos.metric.grpc.server.executor.interval"),
                    eq("15000")))
                .thenReturn("15000");
            monitor.configureTasks(registrar);
        }
        assertFalse(registrar.getFixedRateTaskList().isEmpty());
        registrar.getFixedRateTaskList().forEach(task -> task.getRunnable().run());
        verify(sdkServer).getRpcExecutor();
        verify(clusterServer).getRpcExecutor();
        sdkExecutor.shutdown();
        clusterExecutor.shutdown();
    }
}
