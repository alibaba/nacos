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

public class ConfigMetrics {
    
    // “heisen-gauge” principal: https://micrometer.io/docs/concepts#_gauges
    // DO NOT interact with the gauge object directly. Rather, interacting with the thing that will cause the gauge
    
    /**
     * Micrometer recommends using <b>. (dots)</b> to uniformly separate meter names, see <a
     * href="https://micrometer.io/docs/concepts#_naming_meters">official docs</a> .
     */
    private static final AtomicInteger LISTENER_CONFIG_COUNT_GAUGE = MetricsMonitor.getNacosMeterRegistry()
            .gauge("nacos.monitor", Tags.of("module", "config", "name", "listenerConfigCount"), new AtomicInteger(0));
    
    /**
     * <i>This metric can not be disabled.</i>
     * <p></p>
     * Set the value of <b>listenConfigCount</b> gauge. <b>listenConfigCount</b> is to record the number of listening
     * configs. As a matter of fact, this value reflects the actual size of the config <tt>cacheMap</tt>
     *
     * @param count the count of listened configs
     */
    public static void setListenerConfigCount(int count) {
        if (LISTENER_CONFIG_COUNT_GAUGE != null) {
            LISTENER_CONFIG_COUNT_GAUGE.set(count);
        }
    }
    
    /**
     * <i>This metric can not be disabled.</i>
     * <p></p>
     * Record the request time in config module.
     *
     * @param url      request url
     * @param method   request method
     * @param code     response code
     * @param duration request duration, unit: ms
     */
    public static void recordConfigRequest(String method, String url, String code, long duration) {
        MetricsMonitor.getNacosMeterRegistry().timer("nacos.client.request",
                        Tags.of("module", "config", "method", method, "url", url, "code", code, "name", "configRequest"))
                .record(duration, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Record the notification time for a single config change on the client.
     *
     * @param envName  client name
     * @param dataId   config data id
     * @param group    config group
     * @param tenant   config tenant
     * @param duration request duration, unit: ms
     */
    public static void recordConfigNotifyCostDuration(String envName, String dataId, String group, String tenant,
            long duration) {
        if (MetricsMonitor.isEnable()) {
            MetricsMonitor.getNacosMeterRegistry().timer("nacos.client.notify",
                    Tags.of("module", "config", "env", envName, "dataId", dataId, "group", group, "tenant", tenant,
                            "name", "configNotifyCostDuration")).record(duration, TimeUnit.MILLISECONDS);
        }
    }
}
