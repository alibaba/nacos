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

import com.alibaba.nacos.common.spi.NacosServiceLoader;

import java.util.Collection;

/**
 * Metrics Monitor.
 *
 * <p>Delegates all metrics recording to a {@link NacosClientMetricsProvider} loaded via SPI. Without a provider
 * artifact on the classpath the default {@link NoopClientMetricsProvider} is used, so the client records nothing
 * and carries no metrics library dependency.
 *
 * @author Nacos
 */
public class MetricsMonitor {
    
    private static final String MODULE_NAMING = "naming";
    
    private static final String MODULE_CONFIG = "config";
    
    private static final String GAUGE_SERVICE_INFO_MAP_SIZE = "serviceInfoMapSize";
    
    private static final String GAUGE_LISTEN_CONFIG_COUNT = "listenConfigCount";
    
    private static final NacosClientMetricsProvider METRICS_PROVIDER = loadMetricsProvider();
    
    private static NacosClientMetricsProvider loadMetricsProvider() {
        Collection<NacosClientMetricsProvider> providers =
            NacosServiceLoader.load(NacosClientMetricsProvider.class);
        return providers.isEmpty() ? new NoopClientMetricsProvider() : providers.iterator().next();
    }
    
    public static void recordServiceInfoMapSize(int size) {
        METRICS_PROVIDER.recordGauge(MODULE_NAMING, GAUGE_SERVICE_INFO_MAP_SIZE, size);
    }
    
    public static void recordListenConfigCount(int count) {
        METRICS_PROVIDER.recordGauge(MODULE_CONFIG, GAUGE_LISTEN_CONFIG_COUNT, count);
    }
    
    public static void observeConfigRequest(String method, String url, String code,
        long elapsedMillis) {
        METRICS_PROVIDER.observeRequest(MODULE_CONFIG, method, url, code, elapsedMillis);
    }
    
    public static void observeNamingRequest(String method, String url, String code,
        long elapsedMillis) {
        METRICS_PROVIDER.observeRequest(MODULE_NAMING, method, url, code, elapsedMillis);
    }
    
    public static void recordNamingRequestFailed(String requestClass, String responseStatus,
        String responseCode,
        String exceptionClass) {
        METRICS_PROVIDER.incrementNamingRequestFailed(requestClass, responseStatus, responseCode,
            exceptionClass);
    }
    
    /**
     * Get the effective metrics provider, mainly for test and diagnostics.
     *
     * @return current {@link NacosClientMetricsProvider}
     */
    public static NacosClientMetricsProvider getMetricsProvider() {
        return METRICS_PROVIDER;
    }
}
