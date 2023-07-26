/*
 *
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.client.monitor;

import io.micrometer.core.instrument.Tags;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class NamingMetrics {
    
    // “heisen-gauge” principal: https://micrometer.io/docs/concepts#_gauges
    // DO NOT interact with the gauge object directly. Rather, interacting with the thing that will cause the gauge
    
    /**
     * Micrometer recommends using <b>. (dots)</b> to uniformly separate meter names, see <a
     * href="https://micrometer.io/docs/concepts#_naming_meters">official docs</a> .
     */
    private static final AtomicInteger SERVICE_INFO_MAP_SIZE_GAUGE = MetricsMonitor.getNacosMeterRegistry()
            .gauge("nacos.monitor", Tags.of("module", "naming", "name", "serviceInfoMapSize"), new AtomicInteger(0));
    
    /**
     * Set the value of `serviceInfoMapSize` gauge. `serviceInfoMapSize` is to record the number of stored service
     * info.
     *
     * @param size the size of serviceInfoMap
     */
    public static void setServiceInfoMapSizeMonitor(int size) {
        if (SERVICE_INFO_MAP_SIZE_GAUGE != null) {
            SERVICE_INFO_MAP_SIZE_GAUGE.set(size);
        }
    }
    
    /**
     * Record the request time in naming module.
     *
     * @param url      request url
     * @param method   request method
     * @param code     response code
     * @param duration request duration, unit: ms
     */
    public static void recordNamingRequestMonitor(String method, String url, String code, long duration) {
        MetricsMonitor.getNacosMeterRegistry()
                .timer("nacos.client.request", Tags.of("module", "naming", "method", method, "url", url, "code", code))
                .record(duration, TimeUnit.MILLISECONDS);
    }
}
