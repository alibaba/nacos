/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;

/**
 * Prometheus metrics helper. This class is only loaded when prometheus client
 * is on the classpath. It isolates all direct prometheus API calls so that
 * {@link MetricsMonitor} can guard against ClassNotFoundException.
 *
 * @author Nacos
 */
final class PrometheusMetricsHelper {
    
    private PrometheusMetricsHelper() {
    }
    
    static Object createGauge(String name, String help, String... labelNames) {
        return Gauge.build().name(name).labelNames(labelNames).help(help).register();
    }
    
    static Object createHistogram(String name, String help, String... labelNames) {
        return Histogram.build().name(name).labelNames(labelNames).help(help).register();
    }
    
    static Object createCounter(String name, String help, String... labelNames) {
        return Counter.build().name(name).labelNames(labelNames).help(help).register();
    }
    
    static MetricsMonitor.MetricsTimer getHistogramChild(Object histogramObj,
        String... labelValues) {
        Histogram histogram = (Histogram) histogramObj;
        Histogram.Child child = histogram.labels(labelValues);
        return child::observe;
    }
    
    static void setGaugeChild(Object gaugeObj, double value, String... labelValues) {
        Gauge gauge = (Gauge) gaugeObj;
        gauge.labels(labelValues).set(value);
    }
    
    static void incCounterChild(Object counterObj, String... labelValues) {
        Counter counter = (Counter) counterObj;
        counter.labels(labelValues).inc();
    }
}
