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
    
    private static final String NACOS_OTEL_PROPERTY = "nacos.otel.collector.endpoint";
    
    private static final String NACOS_OTEL_DEFAULT_ENDPOINT = "http://localhost:4318/v1/metrics";
    
    private static final Boolean NACOS_METRICS_ENABLE = ConvertUtils.toBoolean(
            NacosClientProperties.PROTOTYPE.getProperty(NACOS_METRICS_ENABLE_PROPERTY, "false"));
    
    static {
        
        CompositeMeterRegistry nacosMeterRegistry = NACOS_METER_REGISTRY;
        
        nacosMeterRegistry.config().commonTags("nacos.client.version", VersionUtils.getFullClientVersion());
        
        // OpenTelemetry metrics exporter
        nacosMeterRegistry.add(new OtlpMeterRegistry(new OtlpConfig() {
            
            @Override
            public String get(final @NonNull String key) {
                return null;
            }
            
            @Override
            public @NonNull String url() {
                // User should set the environment variable `NACOS_OTEL_COLLECTOR_ENDPOINT` to customize the OpenTelemetry collector endpoint.
                String url = ValidatorUtils.checkValidUrl(
                        NacosClientProperties.PROTOTYPE.getProperty(NACOS_OTEL_PROPERTY));
                return url == null ? NACOS_OTEL_DEFAULT_ENDPOINT : url;
            }
        }, Clock.SYSTEM));
        
        // Prometheus metrics exporter
        nacosMeterRegistry.add(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
    }
    
    /**
     * User should set the property <tt>nacos.metrics.enable</tt> to enable the <b>new</b> metrics. For backward
     * compatible reason, the <b>old</b> metrics are always enabled and can not be influenced by this property. They are
     * including:
     * <ul>
     *     <li>nacos.monitor-listenerConfigCount</li>
     *     <li>nacos.monitor-serviceInfoMapSize</li>
     *     <li>nacos.client.request-configRequest</li>
     *     <li>nacos.client.request-namingRequest</li>
     * </ul>
     *
     * @return true if the <b>new</b> metrics are enabled, otherwise false.
     */
    public static boolean isEnable() {
        return NACOS_METRICS_ENABLE;
    }
    
    public static CompositeMeterRegistry getNacosMeterRegistry() {
        return NACOS_METER_REGISTRY;
    }
}
