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

package com.alibaba.nacos.naming.monitor;

import com.alibaba.nacos.metrics.manager.MetricsManager;
import com.alibaba.nacos.metrics.manager.NamingMetricsConstant;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;

import java.util.concurrent.TimeUnit;

/**
 * Metrics Monitor.
 *
 * @author Nacos
 */
public class MetricsMonitor {
    
    /**
     * compareAndSetMaxPushCost.
     */
    public static void compareAndSetMaxPushCost(long newCost) {
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.MAX_PUSH_COST)
                .getAndUpdate((prev) -> Math.max(newCost, prev));
    }
    
    /**
     * incrementPush.
     */
    public static void incrementPush() {
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.TOTAL_PUSH)
                .incrementAndGet();
    }
    
    /**
     * incrementPushCost.
     */
    public static void incrementPushCost(long costTime) {
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.TOTAL_PUSH_COUNT_FOR_AVG)
                .incrementAndGet();
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                        NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                        NamingMetricsConstant.NAME, NamingMetricsConstant.TOTAL_PUSH_COST_FOR_AVG)
                .addAndGet(costTime);
    }
    
    /**
     * incrementFailPush.
     */
    public static void incrementFailPush() {
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.FAILED_PUSH)
                .incrementAndGet();
    }
    
    /**
     * incrementInstanceCount.
     */
    public static void incrementInstanceCount() {
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                        NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                        NamingMetricsConstant.NAME, NamingMetricsConstant.IP_COUNT)
                .incrementAndGet();
    }
    
    /**
     * decrementInstanceCount.
     */
    public static void decrementInstanceCount() {
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                        NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                        NamingMetricsConstant.NAME, NamingMetricsConstant.IP_COUNT)
                .decrementAndGet();
    }
    
    /**
     * incrementSubscribeCount.
     */
    public static void incrementSubscribeCount() {
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                        NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                        NamingMetricsConstant.NAME, NamingMetricsConstant.SUBSCRIBER_COUNT)
                .incrementAndGet();
    }
    
    /**
     * decrementSubscribeCount.
     */
    public static void decrementSubscribeCount() {
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.SUBSCRIBER_COUNT)
                .decrementAndGet();
    }
    
    public static Counter getDiskException() {
        return Metrics.counter("nacos_exception", "module", "naming", "name", "disk");
    }
    
    public static Counter getLeaderSendBeatFailedException() {
        return Metrics.counter("nacos_exception", "module", "naming", "name", "leaderSendBeatFailed");
    }
    
    public static void setServerPushCost(Long amount, String type, String isSuccess) {
        MetricsManager.timer(NamingMetricsConstant.NACOS_SERVER_PUSH,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.TYPE, type,
                NamingMetricsConstant.SUCCESS, isSuccess)
                .record(amount, TimeUnit.MILLISECONDS);
    }
    
    public static Counter getGrpcPushSuccessCount() {
        return Metrics.counter("nacos_server_push_count", "module", "naming", "type", "grpc", "success", "true");
    }
    
    public static Counter getGrpcPushFailedCount() {
        return Metrics.counter("nacos_server_push_count", "module", "naming", "type", "grpc", "success", "false");
    }
    
    public static Counter getUdpPushSuccessCount() {
        return Metrics.counter("nacos_server_push_count", "module", "naming", "type", "udp", "success", "true");
    }
    
    public static Counter getUdpPushFailedCount() {
        return Metrics.counter("nacos_server_push_count", "module", "naming", "type", "udp", "success", "false");
    }
        
    /**
     * Reset all metrics.
     */
    public static void resetAll() {
        resetPush();
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.HTTP_HEALTH_CHECK).set(0);
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.MYSQL_HEALTH_CHECK).set(0);
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.TCP_HEALTH_CHECK).set(0);
    }
    
    /**
     * Reset push metrics.
     */
    public static void resetPush() {
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.TOTAL_PUSH).set(0);
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.FAILED_PUSH).set(0);
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.TOTAL_PUSH_COST_FOR_AVG).set(0);
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.TOTAL_PUSH_COUNT_FOR_AVG).set(0);
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.MAX_PUSH_COST).set(-1);
        MetricsManager.gauge(NamingMetricsConstant.NACOS_MONITOR,
                NamingMetricsConstant.MODULE, NamingMetricsConstant.NAMING,
                NamingMetricsConstant.NAME, NamingMetricsConstant.AVG_PUSH_COST).set(-1);
    }
}
