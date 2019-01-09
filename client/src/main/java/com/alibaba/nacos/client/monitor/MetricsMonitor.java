package com.alibaba.nacos.client.monitor;

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
    private static AtomicInteger serviceInfoMapSize = new AtomicInteger();
    private static AtomicInteger dom2BeatSize = new AtomicInteger();
    private static AtomicInteger listenConfigCount = new AtomicInteger();

    static {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("module", "naming"));
        tags.add(Tag.of("name", "subServiceCount"));
        Metrics.gauge("nacos_monitor", tags, serviceInfoMapSize);

        tags = new ArrayList<>();
        tags.add(Tag.of("module", "naming"));
        tags.add(Tag.of("name", "pubServiceCount"));
        Metrics.gauge("nacos_monitor", tags, dom2BeatSize);

        tags = new ArrayList<>();
        tags.add(Tag.of("module", "config"));
        tags.add(Tag.of("name", "listenConfigCount"));
        Metrics.gauge("nacos_monitor", tags, listenConfigCount);
    }

    public static AtomicInteger getServiceInfoMapSizeMonitor() {
        return serviceInfoMapSize;
    }

    public static AtomicInteger getDom2BeatSizeMonitor() {
        return dom2BeatSize;
    }

    public static AtomicInteger getListenConfigCountMonitor() {
        return listenConfigCount;
    }

    public static Timer getConfigRequestMonitor(String method, String url, String code) {
        return Metrics.timer("nacos_client_request",
            "module", "config",
            "method", method,
            "url", url,
            "code", code);
    }

    public static Timer getNamingRequestMonitor(String method, String url, String code) {
        return Metrics.timer("nacos_client_request",
            "module", "naming",
            "method", method,
            "url", url,
            "code", code);
    }
}
