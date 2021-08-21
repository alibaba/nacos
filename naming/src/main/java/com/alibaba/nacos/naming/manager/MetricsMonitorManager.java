package com.alibaba.nacos.naming.manager;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class MetricsMonitorManager {
    
    /**
     * 管理 Gauge
     */
    private static final Map<String, AtomicLong> GAUGES_MAP = new ConcurrentHashMap<>();
    
    /**
     * 管理 Counter
     */
    private static final Map<String, Counter> COUNTERS_MAP = new ConcurrentHashMap<>();
    
    /**
     * 管理 Timer
     */
    private static final Map<String, Timer> TIMERS_MAP = new ConcurrentHashMap<>();
    
    public static Counter counter(String name, String... tags) {
        return COUNTERS_MAP.computeIfAbsent(getKey(name, tags), s -> Metrics.counter(name, tags));
    }

    public static Timer timer(String name, String... tags) {
        return TIMERS_MAP.computeIfAbsent(getKey(name, tags), s -> Metrics.timer(name, tags));
    }

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
     * 暴露 Manager 管理的 Metrics 信息
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
