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

import com.alibaba.nacos.client.utils.ValidatorUtils;
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
     * The <strong>NACOS_METER_REGISTRY</strong> is pointing to
     * {@link io.micrometer.core.instrument.Metrics#globalRegistry}.
     *
     * <p><strong>DO NOT</strong> directly use {@link io.micrometer.core.instrument.Metrics#globalRegistry} in Nacos
     * client, or the metrics of Nacos client may not export to Prometheus and OpenTelemetry.
     */
    private static final CompositeMeterRegistry NACOS_METER_REGISTRY = Metrics.globalRegistry;
    
    private static final String NACOS_OTEL_ENV = "NACOS_OTEL_COLLECTOR_ENDPOINT";
    
    private static final String NACOS_OTEL_DEFAULT_ENDPOINT = "http://localhost:4318/v1/metrics";
    
    static {
        CompositeMeterRegistry nacosMeterRegistry = NACOS_METER_REGISTRY;
        
        // OpenTelemetry metrics exporter
        nacosMeterRegistry.add(new OtlpMeterRegistry(new OtlpConfig() {
            
            @Override
            public String get(final @NonNull String key) {
                return null;
            }
            
            @Override
            public @NonNull String url() {
                // User should set the environment variable `NACOS_OTEL_COLLECTOR_ENDPOINT` to customize the OpenTelemetry collector endpoint.
                String url = ValidatorUtils.checkValidUrl(System.getenv(NACOS_OTEL_ENV));
                return url == null ? NACOS_OTEL_DEFAULT_ENDPOINT : url;
            }
        }, Clock.SYSTEM));
        
        // Prometheus metrics exporter
        nacosMeterRegistry.add(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
    }
    
    public static CompositeMeterRegistry getNacosMeterRegistry() {
        return NACOS_METER_REGISTRY;
    }
}
