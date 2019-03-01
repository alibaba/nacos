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

import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

/**
 * Metrics Monitor
 *
 * @author Nacos
 */
public class MetricsMonitor {
    private static Gauge nacosMonitor = Gauge.build()
        .name("nacos_monitor").labelNames("module", "name")
        .help("nacos_monitor").register();

    private static Histogram nacosClientRequestHistogram = Histogram.build().labelNames("module", "method", "url", "code")
        .name("nacos_client_request").help("nacos_client_request")
        .register();


    public static Gauge.Child getServiceInfoMapSizeMonitor() {
        return nacosMonitor.labels("naming", "serviceInfoMapSize");
    }

    public static Gauge.Child getDom2BeatSizeMonitor() {
        return nacosMonitor.labels("naming", "dom2BeatSize");
    }

    public static Gauge.Child getListenConfigCountMonitor() {
        return nacosMonitor.labels("naming", "listenConfigCount");
    }

    public static Histogram.Timer getConfigRequestMonitor(String method, String url, String code) {
        return nacosClientRequestHistogram.labels("config", method, url, code).startTimer();
    }

    public static Histogram.Child getNamingRequestMonitor(String method, String url, String code) {
        return nacosClientRequestHistogram.labels("naming", method, url, code);
    }
}

