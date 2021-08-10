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

package com.alibaba.nacos.config.server.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Metrics Monitor.
 *
 * @author Nacos
 */
public class MetricsMonitor {
    
    private static AtomicInteger getConfig = new AtomicInteger();
    
    private static AtomicInteger publish = new AtomicInteger();
    
    /**
     * task for notify config change to sub client of http long polling..
     */
    private static AtomicInteger longPolling = new AtomicInteger();
    
    private static AtomicInteger configCount = new AtomicInteger();
    
    /**
     * task for ntify config change to cluster server.
     */
    private static AtomicInteger notifyTask = new AtomicInteger();
    
    /**
     * task for notify config change to sub client of long connection.
     */
    private static AtomicInteger notifyClientTask = new AtomicInteger();
    
    private static AtomicInteger dumpTask = new AtomicInteger();
    
    static {
        List<Tag> tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "config"));
        tags.add(new ImmutableTag("name", "getConfig"));
        Metrics.gauge("nacos_monitor", tags, getConfig);
        
        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "config"));
        tags.add(new ImmutableTag("name", "publish"));
        Metrics.gauge("nacos_monitor", tags, publish);
        
        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "config"));
        tags.add(new ImmutableTag("name", "longPolling"));
        Metrics.gauge("nacos_monitor", tags, longPolling);
        
        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "config"));
        tags.add(new ImmutableTag("name", "configCount"));
        Metrics.gauge("nacos_monitor", tags, configCount);
        
        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "config"));
        tags.add(new ImmutableTag("name", "notifyTask"));
        Metrics.gauge("nacos_monitor", tags, notifyTask);
    
        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "config"));
        tags.add(new ImmutableTag("name", "notifyClientTask"));
        Metrics.gauge("nacos_monitor", tags, notifyClientTask);
        
        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "config"));
        tags.add(new ImmutableTag("name", "dumpTask"));
        
        Metrics.gauge("nacos_monitor", tags, dumpTask);
    }
    
    public static AtomicInteger getConfigMonitor() {
        return getConfig;
    }
    
    public static AtomicInteger getPublishMonitor() {
        return publish;
    }
    
    public static AtomicInteger getLongPollingMonitor() {
        return longPolling;
    }
    
    public static AtomicInteger getConfigCountMonitor() {
        return configCount;
    }
    
    public static AtomicInteger getNotifyTaskMonitor() {
        return notifyTask;
    }
    
    public static AtomicInteger getNotifyClientTaskMonitor() {
        return notifyClientTask;
    }
    
    public static AtomicInteger getDumpTaskMonitor() {
        return dumpTask;
    }
    
    public static Timer getNotifyRtTimer() {
        return Metrics.timer("nacos_timer", "module", "config", "name", "notifyRt");
    }
    
    public static Counter getIllegalArgumentException() {
        return Metrics.counter("nacos_exception", "module", "config", "name", "illegalArgument");
    }
    
    public static Counter getNacosException() {
        return Metrics.counter("nacos_exception", "module", "config", "name", "nacos");
    }
    
    public static Counter getDbException() {
        return Metrics.counter("nacos_exception", "module", "config", "name", "db");
    }
    
    public static Counter getConfigNotifyException() {
        return Metrics.counter("nacos_exception", "module", "config", "name", "configNotify");
    }
    
    public static Counter getUnhealthException() {
        return Metrics.counter("nacos_exception", "module", "config", "name", "unhealth");
    }
    
}
