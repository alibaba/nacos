/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Used to collect grpc server executor metrics.
 *
 * @author Daydreamer-ia
 */
@Component
public class GrpcServerThreadPoolMonitor implements SchedulingConfigurer {

    @Resource
    private GrpcSdkServer sdkServer;

    @Resource
    private GrpcClusterServer clusterServer;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        Boolean enabled = EnvUtil.getProperty("nacos.metric.grpc.server.executor.enabled", Boolean.class, true);
        if (!enabled) {
            return;
        }
        taskRegistrar.addFixedRateTask(new IntervalTask(() -> {
            // sdk server
            ThreadPoolExecutor sdkServerRpcExecutor = sdkServer.getRpcExecutor();
            MetricsMonitor.getSdkServerExecutorMetric().getTaskCount().set(sdkServerRpcExecutor.getTaskCount());
            MetricsMonitor.getSdkServerExecutorMetric().getCompletedTaskCount().set(sdkServerRpcExecutor.getCompletedTaskCount());
            MetricsMonitor.getSdkServerExecutorMetric().getInQueueTaskCount().set(sdkServerRpcExecutor.getQueue().size());
            MetricsMonitor.getSdkServerExecutorMetric().getActiveCount().set(sdkServerRpcExecutor.getActiveCount());
            MetricsMonitor.getSdkServerExecutorMetric().getCorePoolSize().set(sdkServerRpcExecutor.getCorePoolSize());
            MetricsMonitor.getSdkServerExecutorMetric().getMaximumPoolSize().set(sdkServerRpcExecutor.getMaximumPoolSize());
            MetricsMonitor.getSdkServerExecutorMetric().getPoolSize().set(sdkServerRpcExecutor.getPoolSize());

            // cluster server
            ThreadPoolExecutor clusterServerRpcExecutor = clusterServer.getRpcExecutor();
            MetricsMonitor.getClusterServerExecutorMetric().getTaskCount().set(clusterServerRpcExecutor.getTaskCount());
            MetricsMonitor.getClusterServerExecutorMetric().getCompletedTaskCount().set(clusterServerRpcExecutor.getCompletedTaskCount());
            MetricsMonitor.getClusterServerExecutorMetric().getInQueueTaskCount().set(clusterServerRpcExecutor.getQueue().size());
            MetricsMonitor.getClusterServerExecutorMetric().getActiveCount().set(clusterServerRpcExecutor.getActiveCount());
            MetricsMonitor.getClusterServerExecutorMetric().getCorePoolSize().set(clusterServerRpcExecutor.getCorePoolSize());
            MetricsMonitor.getClusterServerExecutorMetric().getMaximumPoolSize().set(clusterServerRpcExecutor.getMaximumPoolSize());
            MetricsMonitor.getClusterServerExecutorMetric().getPoolSize().set(clusterServerRpcExecutor.getPoolSize());
        }, Integer.parseInt(EnvUtil.getProperty("nacos.metric.grpc.server.executor.interval", "15000")), 1000L));
    }
}
