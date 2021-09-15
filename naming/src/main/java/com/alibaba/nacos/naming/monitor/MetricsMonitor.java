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
import com.alibaba.nacos.naming.misc.Loggers;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics Monitor.
 *
 * @author Nacos
 */
public class MetricsMonitor {
    
    private static final MetricsMonitor INSTANCE = new MetricsMonitor();
    
    private final AtomicInteger mysqlHealthCheck = new AtomicInteger();
    
    private final AtomicInteger httpHealthCheck = new AtomicInteger();
    
    private final AtomicInteger tcpHealthCheck = new AtomicInteger();
    
    private final AtomicInteger serviceCount = new AtomicInteger();
    
    private final AtomicInteger ipCount = new AtomicInteger();
    
    private final AtomicInteger subscriberCount = new AtomicInteger();
    
    private final AtomicLong maxPushCost = new AtomicLong(-1);
    
    private final AtomicLong avgPushCost = new AtomicLong(-1);
    
    private final AtomicLong leaderStatus = new AtomicLong();
    
    private final AtomicInteger totalPush = new AtomicInteger();
    
    private final AtomicInteger totalPushCountForAvg = new AtomicInteger();
    
    private final AtomicLong totalPushCostForAvg = new AtomicLong();
    
    private final AtomicInteger failedPush = new AtomicInteger();
    
    private MetricsMonitor() {
        for (Field each : MetricsMonitor.class.getDeclaredFields()) {
            if (Number.class.isAssignableFrom(each.getType())) {
                each.setAccessible(true);
                try {
                    registerToMetrics(each.getName(), (Number) each.get(this));
                } catch (IllegalAccessException e) {
                    Loggers.PERFORMANCE_LOG.error("Init metrics for {} failed", each.getName(), e);
                }
            }
        }
    }
    
    private <T extends Number> void registerToMetrics(String name, T number) {
        List<Tag> tags = new ArrayList<>();
        tags.add(new ImmutableTag("module", "naming"));
        tags.add(new ImmutableTag("name", name));
        Metrics.gauge("nacos_monitor", tags, number);
    }
    
    public static AtomicInteger getMysqlHealthCheckMonitor() {
        return INSTANCE.mysqlHealthCheck;
    }
    
    public static AtomicInteger getHttpHealthCheckMonitor() {
        return INSTANCE.httpHealthCheck;
    }
    
    public static AtomicInteger getTcpHealthCheckMonitor() {
        return INSTANCE.tcpHealthCheck;
    }
    
    public static AtomicInteger getDomCountMonitor() {
        return INSTANCE.serviceCount;
    }
    
    public static AtomicInteger getIpCountMonitor() {
        return INSTANCE.ipCount;
    }
    
    public static AtomicInteger getSubscriberCount() {
        return INSTANCE.subscriberCount;
    }
    
    public static AtomicLong getMaxPushCostMonitor() {
        return INSTANCE.maxPushCost;
    }
    
    public static AtomicLong getAvgPushCostMonitor() {
        return INSTANCE.avgPushCost;
    }
    
    public static AtomicLong getLeaderStatusMonitor() {
        return INSTANCE.leaderStatus;
    }
    
    public static AtomicInteger getTotalPushMonitor() {
        return INSTANCE.totalPush;
    }
    
    public static AtomicInteger getFailedPushMonitor() {
        return INSTANCE.failedPush;
    }
    
    public static AtomicInteger getTotalPushCountForAvg() {
        return INSTANCE.totalPushCountForAvg;
    }
    
    public static AtomicLong getTotalPushCostForAvg() {
        return INSTANCE.totalPushCostForAvg;
    }
    
    /**
     * compareAndSetMaxPushCost.
     */
    public static void compareAndSetMaxPushCost(long newCost) {
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_MAX_PUSH_COST)
                .getAndUpdate((prev) -> Math.max(newCost, prev));
    }
    
    /**
     * incrementPush.
     */
    public static void incrementPush() {
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_TOTAL_PUSH)
                .incrementAndGet();
    }
    
    /**
     * incrementPushCost.
     */
    public static void incrementPushCost(long costTime) {
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_TOTAL_PUSH_COUNT_FOR_AVG)
                .incrementAndGet();
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                        NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                        NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_TOTAL_PUSH_COST_FOR_AVG)
                .addAndGet(costTime);
    }
    
    /**
     * incrementFailPush.
     */
    public static void incrementFailPush() {
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_FAILED_PUSH)
                .incrementAndGet();
    }
    
    /**
     * incrementInstanceCount.
     */
    public static void incrementInstanceCount() {
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                        NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                        NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_IP_COUNT)
                .incrementAndGet();
    }
    
    /**
     * decrementInstanceCount.
     */
    public static void decrementInstanceCount() {
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                        NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                        NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_IP_COUNT)
                .decrementAndGet();
    }
    
    /**
     * incrementSubscribeCount.
     */
    public static void incrementSubscribeCount() {
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                        NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                        NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_SUBSCRIBER_COUNT)
                .incrementAndGet();
    }
    
    /**
     * decrementSubscribeCount.
     */
    public static void decrementSubscribeCount() {
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_SUBSCRIBER_COUNT)
                .decrementAndGet();
    }
    
    public static Counter getDiskException() {
        return Metrics.counter("nacos_exception", "module", "naming", "name", "disk");
    }
    
    public static Counter getLeaderSendBeatFailedException() {
        return Metrics.counter("nacos_exception", "module", "naming", "name", "leaderSendBeatFailed");
    }
    
    public static void setServerPushCost(Long amount, String type, String isSuccess) {
        MetricsManager.timer(NamingMetricsConstant.N_NACOS_SERVER_PUSH,
                NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                NamingMetricsConstant.TK_TYPE, type,
                NamingMetricsConstant.TK_SUCCESS, isSuccess)
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
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_HTTP_HEALTH_CHECK).set(0);
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_MYSQL_HEALTH_CHECK).set(0);
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_TCP_HEALTH_CHECK).set(0);
    }
    
    /**
     * Reset push metrics.
     */
    public static void resetPush() {
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_TOTAL_PUSH).set(0);
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_FAILED_PUSH).set(0);
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_TOTAL_PUSH_COST_FOR_AVG).set(0);
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_TOTAL_PUSH_COUNT_FOR_AVG).set(0);
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_MAX_PUSH_COST).set(-1);
        MetricsManager.gauge(NamingMetricsConstant.N_NACOS_MONITOR,
                NamingMetricsConstant.TK_MODULE, NamingMetricsConstant.TV_NAMING,
                NamingMetricsConstant.TK_NAME, NamingMetricsConstant.TV_AVG_PUSH_COST).set(-1);
    }
}
