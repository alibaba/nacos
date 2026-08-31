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

/**
 * SPI for client metrics recording.
 *
 * <p>The nacos-client keeps no hard dependency on any metrics library. Implementations are discovered via
 * {@link java.util.ServiceLoader}; when none is provided, metrics recording is a no-op. Users who want metrics
 * exported to a specific system (Prometheus, JMX, etc.) can supply an adapter artifact that implements this
 * interface and registers it in {@code META-INF/services}.
 *
 * @author Nacos
 */
public interface NacosClientMetricsProvider {
    
    /**
     * Record a gauge value.
     *
     * @param module the metrics module, e.g. {@code naming} or {@code config}
     * @param name   the gauge name, e.g. {@code serviceInfoMapSize}
     * @param value  current gauge value
     */
    void recordGauge(String module, String name, double value);
    
    /**
     * Observe the elapsed time of a client request.
     *
     * @param module        the metrics module, e.g. {@code naming} or {@code config}
     * @param method        request method
     * @param url           request url or path
     * @param code          response code, {@code NA} when no response was received
     * @param elapsedMillis elapsed time in milliseconds
     */
    void observeRequest(String module, String method, String url, String code, long elapsedMillis);
    
    /**
     * Increment the counter of failed naming requests.
     *
     * @param requestClass simple class name of the failed request
     * @param responseStatus response result code, {@code NONE} when no response was received
     * @param responseCode   response error code, {@code NONE} when no response was received
     * @param exceptionClass simple class name of the thrown exception, {@code NONE} when no exception was thrown
     */
    void incrementNamingRequestFailed(String requestClass, String responseStatus,
        String responseCode,
        String exceptionClass);
}
