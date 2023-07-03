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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unified management of Micrometer registry.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
@SuppressWarnings("all")
public class NacosMeterRegistry {
    
    /**
     * The <strong>NACOS_METER_REGISTRY</strong> is pointing to
     * {@link io.micrometer.core.instrument.Metrics#globalRegistry}.
     * <p>
     * <strong>DO NOT</strong> directly use {@link io.micrometer.core.instrument.Metrics#globalRegistry} in Nacos
     * client, or the metrics of Nacos client may not export to Prometheus and OpenTelemetry.
     */
    private static final CompositeMeterRegistry NACOS_METER_REGISTRY = Metrics.globalRegistry;
    
    /**
     *  Initialize the {@link NACOS_METER_REGISTRY}
     */
    static {
        CompositeMeterRegistry nacosMeterRegistry = NACOS_METER_REGISTRY;
        
        nacosMeterRegistry.add(new OtlpMeterRegistry(new OtlpConfig() {
            @Override
            public String get(final String key) {
                return null;
            }
            
            @Override
            public String url() {
                // TODO: Enable Nacos users to customize the URL of OpenTelemetry Collector.
                return "http://localhost:4318/v1/metrics";
            }
        }, Clock.SYSTEM));
        
        nacosMeterRegistry.add(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
    }
    
    // “heisen-gauge” principal: https://micrometer.io/docs/concepts#_gauges
    // DO NOT interact with the gauge object directly. Rather, interacting with the thing that will cause the gauge
    private static AtomicInteger SERVICE_INFO_MAP_SIZE_GAUGE = NACOS_METER_REGISTRY.gauge("nacos.monitor",
            Tags.of("module", "naming", "name", "serviceInfoMapSize"), new AtomicInteger(0));
    
    private static AtomicInteger LISTENER_CONFIG_COUNT_GAUGE = NACOS_METER_REGISTRY.gauge("nacos.monitor",
            Tags.of("module", "config", "name", "listenConfigCount"), new AtomicInteger(0));
    
    /**
     * Set the value of serviceInfoMapSize gauge.
     *
     * @param size the size of serviceInfoMap
     */
    public static void setServiceInfoMapSizeMonitor(int size) {
        SERVICE_INFO_MAP_SIZE_GAUGE.set(size);
    }
    
    
    /**
     * set the value of listenConfigCount gauge.
     *
     * @param count the count of listen config
     */
    public static void setListenerConfigCountMonitor(int count) {
        LISTENER_CONFIG_COUNT_GAUGE.set(count);
    }
    
    /**
     * Record the request time in config module.
     *
     * @param url      request url
     * @param method   request method
     * @param code     response code
     * @param duration request duration, unit: ms
     */
    public static void recordConfigRequestMonitor(String url, String method, String code, long duration) {
        NACOS_METER_REGISTRY.timer("nacos.client.request",
                        Tags.of("module", "config", "url", url, "method", method, "code", code))
                .record(duration, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Record the request time in naming module.
     *
     * @param url      request url
     * @param method   request method
     * @param code     response code
     * @param duration request duration, unit: ms
     */
    public static void recordNamingRequestMonitor(String url, String method, String code, long duration) {
        NACOS_METER_REGISTRY.timer("nacos.client.request",
                        Tags.of("module", "naming", "url", url, "method", method, "code", code))
                .record(duration, TimeUnit.MILLISECONDS);
    }
    
    public static CompositeMeterRegistry getNacosMeterRegistry() {
        return NACOS_METER_REGISTRY;
    }
}
