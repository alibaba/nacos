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

import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.utils.ConvertUtils;
import io.micrometer.core.instrument.Tags;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class NamingMetrics {
    
    // Micrometer recommends using <b>. (dots)</b> to uniformly separate meter names, see <a
    // href="https://micrometer.io/docs/concepts#_naming_meters">official docs</a> .
    
    private static final String METRIC_MODULE_NAME = "naming";
    
    private static final String NACOS_NAMING_METRICS_ENABLE_PROPERTY = "nacos.metrics.naming.enable";
    
    /**
     * This property aims to control which module (config or naming, here is naming) is <b>not</b> monitored by
     * Micrometer. It's default value is <b>true</b> so that users who want to monitor whole Nacos client can ignore
     * this property and just only need to set <tt>nacos.metrics.enable</tt> in {@link MetricsMonitor}.
     */
    private static final Boolean NACOS_NAMING_METRICS_ENABLE = ConvertUtils.toBoolean(
            NacosClientProperties.PROTOTYPE.getProperty(NACOS_NAMING_METRICS_ENABLE_PROPERTY, "true"));
    
    public static boolean isEnable() {
        return NACOS_NAMING_METRICS_ENABLE && MetricsMonitor.isEnable();
    }
    
    // ------------------------ Gauges ------------------------
    
    // “heisen-gauge” principal: https://micrometer.io/docs/concepts#_gauges
    // DO NOT interact with the gauge object directly. Rather, interacting with the thing that will cause the gauge.
    
    private static final AtomicInteger SERVICE_INFO_MAP_SIZE_GAUGE = MetricsMonitor.getNacosMeterRegistry()
            .gauge("nacos.monitor", Tags.of("module", METRIC_MODULE_NAME, "name", "serviceInfoMapSize"),
                    new AtomicInteger(0));
    
    /**
     * Set the value of <b>serviceInfoMapSize</b> gauge. <b>serviceInfoMapSize</b> is to record the number of stored
     * service info.
     *
     * @param size the size of serviceInfoMap
     */
    public static void setServiceInfoMapSizeGauge(int size) {
        if (SERVICE_INFO_MAP_SIZE_GAUGE != null && isEnable()) {
            SERVICE_INFO_MAP_SIZE_GAUGE.set(size);
        }
    }
    
    // ------------------------ Timers ------------------------
    
    // For evey meter, Micrometer will generate an id (key) by its name, description and tags.
    // Then the meters will be stored in a ConcurrentHashMap as a cache.
    // So it is OK to build a new timer meter every time.
    
    /**
     * Record the request time in naming module.
     *
     * @param url      request url
     * @param method   request method
     * @param code     response code
     * @param duration request duration, unit: ms
     */
    public static void recordNamingRequestTimer(String method, String url, String code, long duration) {
        if (isEnable()) {
            MetricsMonitor.getNacosMeterRegistry().timer("nacos.client.request",
                    Tags.of("module", METRIC_MODULE_NAME, "method", method, "url", url, "code", code, "name",
                            "namingRequest")).record(duration, TimeUnit.MILLISECONDS);
        }
    }
    
    /**
     * Record the duration of a rpc request on the client.
     *
     * @param connectionType connection type, such as: GRPC
     * @param currentServer  current rpc server
     * @param rpcResultCode  rpc result code, usually <b>200</b> means success
     * @param duration       request duration, unit: ms
     */
    public static void recordRpcCostDurationTimer(String connectionType, String currentServer, String rpcResultCode,
            long duration) {
        if (isEnable()) {
            MetricsMonitor.getNacosMeterRegistry().timer("nacos.client.naming.timer",
                            Tags.of("module", METRIC_MODULE_NAME, "connectionType", connectionType, "currentServer",
                                    currentServer, "rpcResultCode", rpcResultCode, "name", "rpcCostDuration"))
                    .record(duration, TimeUnit.MILLISECONDS);
        }
    }
}
