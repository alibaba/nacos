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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics Monitor
 *
 * @author Nacos
 */
public class MetricsMonitor {
    private static AtomicInteger mysqlHealthCheck = new AtomicInteger();
    private static AtomicInteger httpHealthCheck = new AtomicInteger();
    private static AtomicInteger tcpHealthCheck = new AtomicInteger();
    private static AtomicInteger serviceCount = new AtomicInteger();
    private static AtomicInteger ipCount = new AtomicInteger();
    private static AtomicLong maxPushCost = new AtomicLong();
    private static AtomicLong avgPushCost = new AtomicLong();
    private static AtomicLong leaderStatus = new AtomicLong();
    private static AtomicInteger totalPush = new AtomicInteger();
    private static AtomicInteger failedPush = new AtomicInteger();

    static {
        List<Tag> tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "naming"));
        tags.add(new ImmutableTag("name", "mysqlhealthCheck"));
        Metrics.gauge("nacos_monitor", tags, mysqlHealthCheck);

        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "naming"));
        tags.add(new ImmutableTag("name", "httpHealthCheck"));
        Metrics.gauge("nacos_monitor", tags, httpHealthCheck);

        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "naming"));
        tags.add(new ImmutableTag("name", "tcpHealthCheck"));
        Metrics.gauge("nacos_monitor", tags, tcpHealthCheck);

        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "naming"));
        tags.add(new ImmutableTag("name", "serviceCount"));
        Metrics.gauge("nacos_monitor", tags, serviceCount);

        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "naming"));
        tags.add(new ImmutableTag("name", "ipCount"));
        Metrics.gauge("nacos_monitor", tags, ipCount);

        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "naming"));
        tags.add(new ImmutableTag("name", "maxPushCost"));
        Metrics.gauge("nacos_monitor", tags, maxPushCost);

        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "naming"));
        tags.add(new ImmutableTag("name", "avgPushCost"));
        Metrics.gauge("nacos_monitor", tags, avgPushCost);

        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "naming"));
        tags.add(new ImmutableTag("name", "leaderStatus"));
        Metrics.gauge("nacos_monitor", tags, leaderStatus);

        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "naming"));
        tags.add(new ImmutableTag("name", "totalPush"));
        Metrics.gauge("nacos_monitor", tags, totalPush);

        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "naming"));
        tags.add(new ImmutableTag("name", "failedPush"));
        Metrics.gauge("nacos_monitor", tags, failedPush);
    }

    public static AtomicInteger getMysqlHealthCheckMonitor() {
        return mysqlHealthCheck;
    }

    public static AtomicInteger getHttpHealthCheckMonitor() {
        return httpHealthCheck;
    }

    public static AtomicInteger getTcpHealthCheckMonitor() {
        return tcpHealthCheck;
    }

    public static AtomicInteger getDomCountMonitor() {
        return serviceCount;
    }

    public static AtomicInteger getIpCountMonitor() {
        return ipCount;
    }

    public static AtomicLong getMaxPushCostMonitor() {
        return maxPushCost;
    }

    public static AtomicLong getAvgPushCostMonitor() {
        return avgPushCost;
    }

    public static AtomicLong getLeaderStatusMonitor() {
        return leaderStatus;
    }

    public static AtomicInteger getTotalPushMonitor() {
        return totalPush;
    }

    public static AtomicInteger getFailedPushMonitor() {
        return failedPush;
    }

    public static Counter getDiskException() {
        return Metrics.counter("nacos_exception",
            "module", "naming", "name", "disk");
    }

    public static Counter getLeaderSendBeatFailedException() {
        return Metrics.counter("nacos_exception",
            "module", "naming", "name", "leaderSendBeatFailed");
    }
}
