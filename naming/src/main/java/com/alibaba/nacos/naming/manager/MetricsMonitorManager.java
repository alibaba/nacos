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

package com.alibaba.nacos.naming.manager;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * manager.
 *
 * @author holdonbei
 */
public class MetricsMonitorManager {
    
    /**
     * 管理 Gauge.
     */
    private static final Map<String, AtomicLong> GAUGES_MAP = new ConcurrentHashMap<>();
    
    /**
     * 管理 Counter.
     */
    private static final Map<String, Counter> COUNTERS_MAP = new ConcurrentHashMap<>();
    
    /**
     * 管理 Timer.
     */
    private static final Map<String, Timer> TIMERS_MAP = new ConcurrentHashMap<>();
    
    public static Counter counter(String name, String... tags) {
        return COUNTERS_MAP.computeIfAbsent(getKey(name, tags), s -> Metrics.counter(name, tags));
    }

    public static Timer timer(String name, String... tags) {
        return TIMERS_MAP.computeIfAbsent(getKey(name, tags), s -> Metrics.timer(name, tags));
    }
    
    /**
     * gauge.
     */
    public static AtomicLong gauge(String name, String... tags) {
        if ((tags.length & 1) == 1) {
            // 报异常
            return null;
        }
        return GAUGES_MAP.computeIfAbsent(getKey(name, tags), s -> {
            List<Tag> tagList = new ArrayList<>();
            for (int i = 0; i < tags.length;) {
                tagList.add(new ImmutableTag(tags[i++], tags[i++]));
            }
            return Metrics.gauge(name, tagList, new AtomicLong());
        });
    }
    
    /**
     * 暴露 Manager 管理的 Metrics 信息.
     */
    public static List<Metrics> exporter() {
        return null;
    }
    
    private static String getKey(String name, String... k) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("_");
        for (String s : k) {
            sb.append(s).append("_");
        }
        return sb.toString();
    }

}
