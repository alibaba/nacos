package com.alibaba.nacos.config.server.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Metrics Monitor
 *
 * @author Nacos
 */
public class MetricsMonitor {
    private static AtomicInteger getConfig = new AtomicInteger();
    private static AtomicInteger publish = new AtomicInteger();
    private static AtomicInteger longPolling = new AtomicInteger();
    private static AtomicInteger configCount = new AtomicInteger();
    private static AtomicInteger notifyTask = new AtomicInteger();
    private static AtomicInteger dumpTask = new AtomicInteger();

    static {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("module", "config"));
        tags.add(Tag.of("name", "getConfig"));
        Metrics.gauge("nacos_monitor", tags, getConfig);

        tags = new ArrayList<>();
        tags.add(Tag.of("module", "config"));
        tags.add(Tag.of("name", "publish"));
        Metrics.gauge("nacos_monitor", tags, publish);

        tags = new ArrayList<>();
        tags.add(Tag.of("module", "config"));
        tags.add(Tag.of("name", "longPolling"));
        Metrics.gauge("nacos_monitor", tags, longPolling);

        tags = new ArrayList<>();
        tags.add(Tag.of("module", "config"));
        tags.add(Tag.of("name", "configCount"));
        Metrics.gauge("nacos_monitor", tags, configCount);

        tags = new ArrayList<>();
        tags.add(Tag.of("module", "config"));
        tags.add(Tag.of("name", "notifyTask"));
        Metrics.gauge("nacos_monitor", tags, notifyTask);

        tags = new ArrayList<>();
        tags.add(Tag.of("module", "config"));
        tags.add(Tag.of("name", "dumpTask"));

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

    public static AtomicInteger getDumpTaskMonitor() {
        return dumpTask;
    }

    public static Timer getNotifyRtTimer() {
        return Metrics.timer("nacos_timer",
            "module", "config", "name", "notifyRt");
    }

    public static Counter getIllegalArgumentException() {
        return Metrics.counter("nacos_exception",
            "module", "config", "name", "illegalArgument");
    }

    public static Counter getNacosException() {
        return Metrics.counter("nacos_exception",
            "module", "config", "name", "nacos");
    }

    public static Counter getDbException() {
        return Metrics.counter("nacos_exception",
            "module", "config", "name", "db");
    }

    public static Counter getConfigNotifyException() {
        return Metrics.counter("nacos_exception",
            "module", "config", "name", "configNotify");
    }

    public static Counter getUnhealthException() {
        return Metrics.counter("nacos_exception",
            "module", "config", "name", "unhealth");
    }

}
