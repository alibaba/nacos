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
import com.alibaba.nacos.client.utils.ValidatorUtils;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.lang.NonNull;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;

/**
 * Unified management of Micrometer registry.
 *
 * @author <a href="https://github.com/FAWC438">FAWC438</a>
 */
public class MetricsMonitor {
    
    /**
     * Directly use the <tt> globalRegistry </tt> of Micrometer.
     */
    private static final CompositeMeterRegistry NACOS_METER_REGISTRY = Metrics.globalRegistry;
    
    private static final String NACOS_METRICS_ENABLE_PROPERTY = "nacos.metrics.enable";
    
    private static final String NACOS_OTEL_ENABLE_PROPERTY = "nacos.metrics.otel.enable";
    
    private static final String NACOS_OTEL_ENDPOINT_PROPERTY = "nacos.metrics.otel.collector.endpoint";
    
    private static final String NACOS_OTEL_DEFAULT_ENDPOINT = "http://localhost:4318/v1/metrics";
    
    /**
     * Initialize the Micrometer registry.
     */
    public static void init() {
        if (isEnable()) {
            CompositeMeterRegistry nacosMeterRegistry = NACOS_METER_REGISTRY;
            
            // OpenTelemetry metrics exporter
            if (isOtelEnable()) {
                OtlpMeterRegistry otlpMeterRegistry = new OtlpMeterRegistry(new OtlpConfig() {
                    
                    @Override
                    public String get(final @NonNull String key) {
                        return null;
                    }
                    
                    @Override
                    public @NonNull String url() {
                        // User should set the environment variable `NACOS_OTEL_COLLECTOR_ENDPOINT` to customize the OpenTelemetry collector endpoint.
                        String url = ValidatorUtils.checkValidUrl(
                                NacosClientProperties.PROTOTYPE.getProperty(NACOS_OTEL_ENDPOINT_PROPERTY));
                        return url == null ? NACOS_OTEL_DEFAULT_ENDPOINT : url;
                    }
                }, Clock.SYSTEM);
                otlpMeterRegistry.config().commonTags("nacos.client.version", VersionUtils.getFullClientVersion());
                nacosMeterRegistry.add(otlpMeterRegistry);
            }
            
            // Prometheus metrics exporter
            PrometheusMeterRegistry prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
            prometheusMeterRegistry.config().commonTags("nacos.client.version", VersionUtils.getFullClientVersion());
            nacosMeterRegistry.add(prometheusMeterRegistry);
        }
    }
    
    static {
        init();
    }
    
    /**
     * User should set the property <tt>nacos.metrics.enable</tt> to enable the metrics to be collected.
     * <p></p>
     * <tt>config</tt> and <tt>naming</tt> modules will be affected by this property. However, they both have their own
     * enable switch additionally.
     *
     * @return true if the monitor of metrics is enabled, otherwise false.
     */
    public static boolean isEnable() {
        return ConvertUtils.toBoolean(
                NacosClientProperties.PROTOTYPE.getProperty(NACOS_METRICS_ENABLE_PROPERTY, "false"));
    }
    
    /**
     * Whether to enable OpenTelemetry metrics exporter. Default is false. Once enabled, Micrometer will keep trying
     * connecting to the OpenTelemetry collector, even if the collector is not available.
     *
     * @return true if the OpenTelemetry metrics exporter is enabled, otherwise false.
     */
    public static boolean isOtelEnable() {
        return ConvertUtils.toBoolean(NacosClientProperties.PROTOTYPE.getProperty(NACOS_OTEL_ENABLE_PROPERTY, "false"));
    }
    
    public static CompositeMeterRegistry getNacosMeterRegistry() {
        return NACOS_METER_REGISTRY;
    }
    
    public static String getNacosMetricsEnableProperty() {
        return NACOS_METRICS_ENABLE_PROPERTY;
    }
    
    public static String getNacosOtelEnableProperty() {
        return NACOS_OTEL_ENABLE_PROPERTY;
    }
    
    public static String getNacosOtelEndpointProperty() {
        return NACOS_OTEL_ENDPOINT_PROPERTY;
    }
    
    public static String getNacosOtelDefaultEndpoint() {
        return NACOS_OTEL_DEFAULT_ENDPOINT;
    }
}
