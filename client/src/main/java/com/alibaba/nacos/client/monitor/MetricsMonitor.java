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
 * Metrics Monitor.
 *
 * @author Nacos
 */
public class MetricsMonitor {
    
    private static final Gauge NACOS_MONITOR = Gauge.build().name("nacos_monitor").labelNames("module", "name")
            .help("nacos_monitor").register();
    
    private static final Histogram NACOS_CLIENT_REQUEST_HISTOGRAM = Histogram.build()
            .labelNames("module", "method", "url", "code").name("nacos_client_request").help("nacos_client_request")
            .register();
    
    public static Gauge.Child getServiceInfoMapSizeMonitor() {
        return NACOS_MONITOR.labels("naming", "serviceInfoMapSize");
    }
    
    public static Gauge.Child getDom2BeatSizeMonitor() {
        return NACOS_MONITOR.labels("naming", "dom2BeatSize");
    }
    
    public static Gauge.Child getListenConfigCountMonitor() {
        return NACOS_MONITOR.labels("naming", "listenConfigCount");
    }
    
    public static Histogram.Timer getConfigRequestMonitor(String method, String url, String code) {
        return NACOS_CLIENT_REQUEST_HISTOGRAM.labels("config", method, url, code).startTimer();
    }
    
    public static Histogram.Child getNamingRequestMonitor(String method, String url, String code) {
        return NACOS_CLIENT_REQUEST_HISTOGRAM.labels("naming", method, url, code);
    }
}

