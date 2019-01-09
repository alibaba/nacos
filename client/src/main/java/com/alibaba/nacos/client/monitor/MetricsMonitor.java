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
package com.alibaba.nacos.client.monitor;

import io.micrometer.core.instrument.ImmutableTag;
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
        List<Tag> tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "naming"));
        tags.add(new ImmutableTag("name", "subServiceCount"));
        Metrics.gauge("nacos_monitor", tags, serviceInfoMapSize);

        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "naming"));
        tags.add(new ImmutableTag("name", "pubServiceCount"));
        Metrics.gauge("nacos_monitor", tags, dom2BeatSize);

        tags = new ArrayList<Tag>();
        tags.add(new ImmutableTag("module", "config"));
        tags.add(new ImmutableTag("name", "listenConfigCount"));
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
