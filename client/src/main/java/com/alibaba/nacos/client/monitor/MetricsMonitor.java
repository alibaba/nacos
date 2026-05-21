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

import java.util.function.Supplier;

/**
 * Metrics Monitor.
 *
 * <p>Prometheus dependency is optional. If prometheus client is not on the
 * classpath, all monitoring operations become no-ops.
 *
 * @author Nacos
 */
public class MetricsMonitor {

    private static final boolean PROMETHEUS_AVAILABLE;

    static {
        boolean available;
        try {
            Class.forName("io.prometheus.client.Counter");
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        PROMETHEUS_AVAILABLE = available;
    }

    private static volatile Object gauge;
    private static volatile Object histogram;
    private static volatile Object counter;

    private static Object getOrInitGauge() {
        if (gauge == null) {
            synchronized (MetricsMonitor.class) {
                if (gauge == null) {
                    gauge = PrometheusMetricsHelper.createGauge("nacos_monitor",
                            "nacos_monitor", "module", "name");
                }
            }
        }
        return gauge;
    }

    private static Object getOrInitHistogram() {
        if (histogram == null) {
            synchronized (MetricsMonitor.class) {
                if (histogram == null) {
                    histogram = PrometheusMetricsHelper.createHistogram(
                            "nacos_client_request", "nacos_client_request",
                            "module", "method", "url", "code");
                }
            }
        }
        return histogram;
    }

    private static Object getOrInitCounter() {
        if (counter == null) {
            synchronized (MetricsMonitor.class) {
                if (counter == null) {
                    counter = PrometheusMetricsHelper.createCounter(
                            "nacos_client_naming_request_failed_total",
                            "nacos_client_naming_request_failed_total",
                            "module", "req_class", "res_status", "res_code",
                            "err_class");
                }
            }
        }
        return counter;
    }

    public static MetricsTimer getConfigRequestMonitor(String method, String url,
            String code) {
        if (!PROMETHEUS_AVAILABLE) {
            return MetricsTimer.NOOP;
        }
        return PrometheusMetricsHelper.getHistogramChild(getOrInitHistogram(),
                "config", method, url, code);
    }

    public static MetricsTimer getNamingRequestMonitor(String method, String url,
            String code) {
        if (!PROMETHEUS_AVAILABLE) {
            return MetricsTimer.NOOP;
        }
        return PrometheusMetricsHelper.getHistogramChild(getOrInitHistogram(),
                "naming", method, url, code);
    }

    public static void recordServiceInfoMapSize(double size) {
        if (!PROMETHEUS_AVAILABLE) {
            return;
        }
        PrometheusMetricsHelper.setGaugeChild(getOrInitGauge(), size,
                "naming", "serviceInfoMapSize");
    }

    public static void recordListenConfigCount(double count) {
        if (!PROMETHEUS_AVAILABLE) {
            return;
        }
        PrometheusMetricsHelper.setGaugeChild(getOrInitGauge(), count,
                "config", "listenConfigCount");
    }

    public static void recordNamingRequestFailed(String reqClass, String resStatus,
            String resCode, String errClass) {
        if (!PROMETHEUS_AVAILABLE) {
            return;
        }
        PrometheusMetricsHelper.incCounterChild(getOrInitCounter(),
                "naming", reqClass, resStatus, resCode, errClass);
    }

    /**
     * Timer abstraction that wraps prometheus Histogram.Child observation.
     */
    public interface MetricsTimer {

        MetricsTimer NOOP = duration -> { };

        void observe(double durationMs);
    }
}
