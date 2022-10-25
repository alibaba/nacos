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

import com.alibaba.nacos.naming.core.v2.pojo.BatchInstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.common.utils.TopnCounterMetricsContainer;
import com.alibaba.nacos.core.monitor.NacosMeterRegistryCenter;
import com.alibaba.nacos.naming.misc.Loggers;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Tag;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics Monitor.
 *
 * @author Nacos
 */
public class MetricsMonitor {
    
    private static final String METER_REGISTRY = NacosMeterRegistryCenter.NAMING_STABLE_REGISTRY;
    
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
    
    private final AtomicInteger emptyPush = new AtomicInteger();
    
    private final AtomicInteger serviceSubscribedEventQueueSize = new AtomicInteger();
    
    private final AtomicInteger serviceChangedEventQueueSize = new AtomicInteger();
    
    private final AtomicInteger pushPendingTaskCount = new AtomicInteger();
    
    /**
     * version -> naming subscriber count.
     */
    private final ConcurrentHashMap<String, AtomicInteger> namingSubscriber = new ConcurrentHashMap<>();
    
    /**
     * version -> naming publisher count.
     */
    private final ConcurrentHashMap<String, AtomicInteger> namingPublisher = new ConcurrentHashMap<>();
    
    /**
     * topn service change count.
     */
    private final TopnCounterMetricsContainer serviceChangeCount = new TopnCounterMetricsContainer();
    
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
        
        namingSubscriber.put("v1", new AtomicInteger(0));
        namingSubscriber.put("v2", new AtomicInteger(0));
        
        List<Tag> tags = new ArrayList<>();
        tags.add(new ImmutableTag("version", "v1"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_naming_subscriber", tags, namingSubscriber.get("v1"));
    
        tags = new ArrayList<>();
        tags.add(new ImmutableTag("version", "v2"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_naming_subscriber", tags, namingSubscriber.get("v2"));
    
        namingPublisher.put("v1", new AtomicInteger(0));
        namingPublisher.put("v2", new AtomicInteger(0));
    
        tags = new ArrayList<>();
        tags.add(new ImmutableTag("version", "v1"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_naming_publisher", tags, namingPublisher.get("v1"));
    
        tags = new ArrayList<>();
        tags.add(new ImmutableTag("version", "v2"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_naming_publisher", tags, namingPublisher.get("v2"));
    }
    
    private <T extends Number> void registerToMetrics(String name, T number) {
        List<Tag> tags = new ArrayList<>();
        tags.add(new ImmutableTag("module", "naming"));
        tags.add(new ImmutableTag("name", name));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor", tags, number);
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
    
    public static AtomicInteger getEmptyPushMonitor() {
        return INSTANCE.emptyPush;
    }
    
    public static AtomicInteger getTotalPushCountForAvg() {
        return INSTANCE.totalPushCountForAvg;
    }
    
    public static AtomicInteger getServiceSubscribedEventQueueSize() {
        return INSTANCE.serviceSubscribedEventQueueSize;
    }
    
    public static AtomicInteger getServiceChangedEventQueueSize() {
        return INSTANCE.serviceChangedEventQueueSize;
    }
    
    public static AtomicInteger getPushPendingTaskCount() {
        return INSTANCE.pushPendingTaskCount;
    }
    
    public static AtomicLong getTotalPushCostForAvg() {
        return INSTANCE.totalPushCostForAvg;
    }
    
    public static AtomicInteger getNamingSubscriber(String version) {
        return INSTANCE.namingSubscriber.get(version);
    }
    
    public static AtomicInteger getNamingPublisher(String version) {
        return INSTANCE.namingPublisher.get(version);
    }
    
    public static TopnCounterMetricsContainer getServiceChangeCount() {
        return INSTANCE.serviceChangeCount;
    }
    
    public static void compareAndSetMaxPushCost(long newCost) {
        INSTANCE.maxPushCost.getAndUpdate((prev) -> Math.max(newCost, prev));
    }
    
    public static void incrementPush() {
        INSTANCE.totalPush.incrementAndGet();
    }
    
    public static void incrementPushCost(long costTime) {
        INSTANCE.totalPushCountForAvg.incrementAndGet();
        INSTANCE.totalPushCostForAvg.addAndGet(costTime);
    }
    
    public static void incrementFailPush() {
        INSTANCE.failedPush.incrementAndGet();
    }
    
    public static void incrementEmptyPush() {
        INSTANCE.emptyPush.incrementAndGet();
    }
    
    public static void incrementInstanceCount() {
        INSTANCE.ipCount.incrementAndGet();
    }
    
    public static void decrementInstanceCount() {
        INSTANCE.ipCount.decrementAndGet();
    }
    
    public static void incrementSubscribeCount() {
        INSTANCE.subscriberCount.incrementAndGet();
    }
    
    public static void decrementSubscribeCount() {
        INSTANCE.subscriberCount.decrementAndGet();
    }
    
    public static void incrementServiceChangeCount(String namespace, String group, String name) {
        INSTANCE.serviceChangeCount.increment(namespace + "@" + group + "@" + name);
    }
    
    public static Counter getDiskException() {
        return NacosMeterRegistryCenter.counter(METER_REGISTRY, "nacos_exception", "module", "naming", "name", "disk");
    }
    
    public static Counter getLeaderSendBeatFailedException() {
        return NacosMeterRegistryCenter.counter(METER_REGISTRY, "nacos_exception", "module", "naming", "name", "leaderSendBeatFailed");
    }
    
    /**
     * increment IpCount when use batchRegister instance.
     * @param instancePublishInfo must be BatchInstancePublishInfo
     */
    public static void incrementIpCountWithBatchRegister(InstancePublishInfo instancePublishInfo) {
        BatchInstancePublishInfo batchInstancePublishInfo = (BatchInstancePublishInfo) instancePublishInfo;
        List<InstancePublishInfo> instancePublishInfos = batchInstancePublishInfo.getInstancePublishInfos();
        getIpCountMonitor().addAndGet(instancePublishInfos.size());
    }
    
    /**
     * decrement IpCount when use batchRegister instance.
     * @param instancePublishInfo must be BatchInstancePublishInfo
     */
    public static void decrementIpCountWithBatchRegister(InstancePublishInfo instancePublishInfo) {
        BatchInstancePublishInfo batchInstancePublishInfo = (BatchInstancePublishInfo) instancePublishInfo;
        List<InstancePublishInfo> instancePublishInfos = batchInstancePublishInfo.getInstancePublishInfos();
        getIpCountMonitor().addAndGet(-1 * instancePublishInfos.size());
    }
    
    /**
     * Reset all metrics.
     */
    public static void resetAll() {
        resetPush();
        getHttpHealthCheckMonitor().set(0);
        getMysqlHealthCheckMonitor().set(0);
        getTcpHealthCheckMonitor().set(0);
    }
    
    /**
     * Reset push metrics.
     */
    public static void resetPush() {
        getTotalPushMonitor().set(0);
        getFailedPushMonitor().set(0);
        getEmptyPushMonitor().set(0);
        getTotalPushCostForAvg().set(0);
        getTotalPushCountForAvg().set(0);
        getMaxPushCostMonitor().set(-1);
        getAvgPushCostMonitor().set(-1);
    }
}
