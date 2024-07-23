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

import com.alibaba.nacos.core.monitor.NacosMeterRegistryCenter;
import com.alibaba.nacos.core.monitor.topn.StringTopNCounter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Metrics Monitor.
 *
 * @author Nacos
 */
public class MetricsMonitor {
    
    private static final String METER_REGISTRY = NacosMeterRegistryCenter.CONFIG_STABLE_REGISTRY;
    
    private static AtomicInteger getConfig = new AtomicInteger();
    
    private static AtomicInteger publish = new AtomicInteger();
    
    /**
     * task for notify config change to sub client of http long polling.
     */
    private static AtomicInteger longPolling = new AtomicInteger();
    
    private static AtomicInteger configCount = new AtomicInteger();
    
    /**
     * task for notify config change to cluster server.
     */
    private static AtomicInteger notifyTask = new AtomicInteger();
    
    /**
     * task for notify config change to sub client of long connection.
     */
    private static AtomicInteger notifyClientTask = new AtomicInteger();
    
    private static AtomicInteger dumpTask = new AtomicInteger();
    
    /**
     * config fuzzy search count.
     */
    private static AtomicInteger fuzzySearch = new AtomicInteger();
    
    /**
     * version -> client config subscriber count.
     */
    private static ConcurrentHashMap<String, AtomicInteger> configSubscriber = new ConcurrentHashMap<>();
    
    /**
     * config change count.
     */
    private static StringTopNCounter configChangeCount = new StringTopNCounter();
    
    static {
        ImmutableTag immutableTag = new ImmutableTag("module", "config");
        
        List<Tag> tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "getConfig"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor", tags, getConfig);
        
        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "publish"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor", tags, publish);
        
        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "longPolling"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor", tags, longPolling);
        
        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "configCount"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor", tags, configCount);
        
        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "notifyTask"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor", tags, notifyTask);
        
        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "notifyClientTask"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor", tags, notifyClientTask);
        
        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "dumpTask"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor", tags, dumpTask);
        
        tags = new ArrayList<>();
        tags.add(immutableTag);
        tags.add(new ImmutableTag("name", "fuzzySearch"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_monitor", tags, fuzzySearch);
        
        configSubscriber.put("v1", new AtomicInteger(0));
        configSubscriber.put("v2", new AtomicInteger(0));
        
        tags = new ArrayList<>();
        tags.add(new ImmutableTag("version", "v1"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_config_subscriber", tags, configSubscriber.get("v1"));
        
        tags = new ArrayList<>();
        tags.add(new ImmutableTag("version", "v2"));
        NacosMeterRegistryCenter.gauge(METER_REGISTRY, "nacos_config_subscriber", tags, configSubscriber.get("v2"));
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
    
    public static AtomicInteger getFuzzySearchMonitor() {
        return fuzzySearch;
    }
    
    public static AtomicInteger getConfigSubscriberMonitor(String version) {
        return configSubscriber.get(version);
    }
    
    public static StringTopNCounter getConfigChangeCount() {
        return configChangeCount;
    }
    
    public static Timer getReadConfigRtTimer() {
        return NacosMeterRegistryCenter
                .timer(METER_REGISTRY, "nacos_timer", "module", "config", "name", "readConfigRt");
    }
    
    public static Timer getReadConfigRpcRtTimer() {
        return NacosMeterRegistryCenter
                .timer(METER_REGISTRY, "nacos_timer", "module", "config", "name", "readConfigRpcRt");
    }
    
    public static Timer getWriteConfigRtTimer() {
        return NacosMeterRegistryCenter
                .timer(METER_REGISTRY, "nacos_timer", "module", "config", "name", "writeConfigRt");
    }
    
    public static Timer getWriteConfigRpcRtTimer() {
        return NacosMeterRegistryCenter
                .timer(METER_REGISTRY, "nacos_timer", "module", "config", "name", "writeConfigRpcRt");
    }
    
    public static Timer getNotifyRtTimer() {
        return NacosMeterRegistryCenter.timer(METER_REGISTRY, "nacos_timer", "module", "config", "name", "notifyRt");
    }
    
    public static Timer getDumpRtTimer() {
        return NacosMeterRegistryCenter.timer(METER_REGISTRY, "nacos_timer", "module", "config", "name", "dumpRt");
    }
    
    public static Counter getIllegalArgumentException() {
        return NacosMeterRegistryCenter
                .counter(METER_REGISTRY, "nacos_exception", "module", "config", "name", "illegalArgument");
    }
    
    public static Counter getNacosException() {
        return NacosMeterRegistryCenter.counter(METER_REGISTRY, "nacos_exception", "module", "config", "name", "nacos");
    }
    
    public static Counter getConfigNotifyException() {
        return NacosMeterRegistryCenter
                .counter(METER_REGISTRY, "nacos_exception", "module", "config", "name", "configNotify");
    }
    
    public static Counter getUnhealthException() {
        return NacosMeterRegistryCenter
                .counter(METER_REGISTRY, "nacos_exception", "module", "config", "name", "unhealth");
    }
    
    public static void incrementConfigChangeCount(String tenant, String group, String dataId) {
        configChangeCount.increment(tenant + "@" + group + "@" + dataId);
    }
}
