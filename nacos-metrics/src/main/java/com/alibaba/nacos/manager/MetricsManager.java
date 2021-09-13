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

package com.alibaba.nacos.manager;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * manager.
 *
 * @author holdonbei
 */
public class MetricsManager {
    
    /**
     * split to create key.
     */
    private static final String SPILT = "_";
    
    private static final MetricsManager INSTANCE = new MetricsManager();
    
    /**
     * manager Gauge.
     */
    private final Map<String, AtomicLong> gaugesMap = new ConcurrentHashMap<>();
    
    /**
     * manager Counter.
     */
    private final Map<String, Counter> countersMap = new ConcurrentHashMap<>();
    
    /**
     * manager Timer.
     */
    private final Map<String, Timer> timersMap = new ConcurrentHashMap<>();
    
    /**
     * register counter with description.
     */
    public static Counter counter(String name, String... tags) {
        return INSTANCE.countersMap.computeIfAbsent(getKey(name, tags), s ->
                Counter.builder(name).tags(tags).register(Metrics.globalRegistry));
    }
    
    /**
     * register timer with description.
     */
    public static Timer timer(String name, String... tags) {
        return INSTANCE.timersMap.computeIfAbsent(getKey(name, tags), s ->
                Timer.builder(name).tags(tags).register(Metrics.globalRegistry));
    }
    
    /**
     * register gauge.
     */
    public static AtomicLong gauge(String name, String... tags) {
        if ((tags.length & 1) == 1) {
            throw new IllegalArgumentException("tags' length is odd, gauge need even.");
        }
        return INSTANCE.gaugesMap.computeIfAbsent(getKey(name, tags), s -> {
            AtomicLong gauge = new AtomicLong();
            Gauge.builder(name, () -> gauge).tags(tags).register(Metrics.globalRegistry);
            return gauge;
        });
    }
    
    /**
     * exporter for Metrics.
     */
    private static List<Metrics> exporter() {
        return null;
    }
    
    /**
     * create key for metrics.
     */
    private static String getKey(String name, String... k) {
        StringBuilder sb = new StringBuilder(name + SPILT);
        for (String s : k) {
            sb.append(s).append(SPILT);
        }
        return sb.toString();
    }
    
}
