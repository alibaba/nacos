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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics monitor based on the Micrometer {@link Metrics#globalRegistry}.
 *
 * <p>The nacos-client keeps no hard dependency on any concrete metrics exporter. All meters are registered on the
 * global composite registry, which safely behaves as a no-op until the application adds a concrete
 * {@code MeterRegistry}. Once the application registers one (for example a {@code PrometheusMeterRegistry}), all
 * nacos client meters are collected automatically.
 *
 * <p>Metric names and tags keep the historical conventions: gauge {@code nacos_monitor}, timer
 * {@code nacos_client_request}, counters {@code nacos_client_naming_request_failed_total} and
 * {@code nacos_client_ai_watch_events_total}.
 *
 * <p>Gauge and AI watch event values are held in process so that they can be read back without a registry, and any
 * failure of the metrics system is swallowed, so it can never affect client requests.
 *
 * @author Nacos
 */
public class MetricsMonitor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsMonitor.class);
    
    private static final String MODULE_NAMING = "naming";
    
    private static final String MODULE_CONFIG = "config";
    
    private static final String MODULE_AI = "ai";
    
    private static final String GAUGE_SERVICE_INFO_MAP_SIZE = "serviceInfoMapSize";
    
    private static final String GAUGE_LISTEN_CONFIG_COUNT = "listenConfigCount";
    
    private static final String GAUGE_AGENT_WATCH_INTENT_COUNT = "agentWatchIntentCount";
    
    private static final String GAUGE_AGENT_WATCH_PENDING_COUNT = "agentWatchPendingCount";
    
    private static final String GAUGE_AGENT_WATCH_DIRTY_COUNT = "agentWatchDirtyCount";
    
    private static final String NACOS_MONITOR = "nacos_monitor";
    
    private static final String NACOS_CLIENT_REQUEST = "nacos_client_request";
    
    private static final String NACOS_CLIENT_NAMING_REQUEST_FAILED =
        "nacos_client_naming_request_failed_total";
    
    private static final String NACOS_CLIENT_AI_WATCH_EVENTS = "nacos_client_ai_watch_events_total";
    
    private static final String TAG_MODULE = "module";
    
    private static final String TAG_NAME = "name";
    
    private static final String TAG_METHOD = "method";
    
    private static final String TAG_URL = "url";
    
    private static final String TAG_CODE = "code";
    
    private static final String TAG_REQUEST_CLASS = "req_class";
    
    private static final String TAG_RESPONSE_STATUS = "res_status";
    
    private static final String TAG_RESPONSE_CODE = "res_code";
    
    private static final String TAG_EXCEPTION_CLASS = "err_class";
    
    private static final String TAG_EVENT = "event";
    
    private static final String TAG_RESULT = "result";
    
    private static final String KEY_SEPARATOR = "|";
    
    /**
     * Timer bucket boundaries aligned with the Prometheus Java client defaults, so dashboards built on the previous
     * {@code nacos_client_request} histogram keep comparable buckets.
     */
    private static final Duration[] REQUEST_DURATION_BUCKETS = {Duration.ofMillis(5),
        Duration.ofMillis(10),
        Duration.ofMillis(25), Duration.ofMillis(50), Duration.ofMillis(75), Duration.ofMillis(100),
        Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofMillis(750),
        Duration.ofSeconds(1),
        Duration.ofMillis(2500), Duration.ofSeconds(5), Duration.ofMillis(7500),
        Duration.ofSeconds(10)};
    
    private static final Map<String, AtomicLong> GAUGE_VALUES = new ConcurrentHashMap<>();
    
    private static final Map<String, AtomicLong> AI_WATCH_EVENT_COUNTS = new ConcurrentHashMap<>();
    
    private static final AtomicBoolean METRICS_FAILURE_LOGGED = new AtomicBoolean(false);
    
    private MetricsMonitor() {
    }
    
    public static void recordServiceInfoMapSize(int size) {
        gaugeValue(MODULE_NAMING, GAUGE_SERVICE_INFO_MAP_SIZE).set(size);
    }
    
    public static void recordListenConfigCount(int count) {
        gaugeValue(MODULE_CONFIG, GAUGE_LISTEN_CONFIG_COUNT).set(count);
    }
    
    public static void incrementAgentWatchIntentCount() {
        gaugeValue(MODULE_AI, GAUGE_AGENT_WATCH_INTENT_COUNT).incrementAndGet();
    }
    
    public static void decrementAgentWatchIntentCount() {
        gaugeValue(MODULE_AI, GAUGE_AGENT_WATCH_INTENT_COUNT).decrementAndGet();
    }
    
    public static double getAgentWatchIntentCount() {
        return gaugeValue(MODULE_AI, GAUGE_AGENT_WATCH_INTENT_COUNT).get();
    }
    
    public static void incrementAgentWatchPendingCount() {
        gaugeValue(MODULE_AI, GAUGE_AGENT_WATCH_PENDING_COUNT).incrementAndGet();
    }
    
    public static void decrementAgentWatchPendingCount() {
        gaugeValue(MODULE_AI, GAUGE_AGENT_WATCH_PENDING_COUNT).decrementAndGet();
    }
    
    public static double getAgentWatchPendingCount() {
        return gaugeValue(MODULE_AI, GAUGE_AGENT_WATCH_PENDING_COUNT).get();
    }
    
    public static void incrementAgentWatchDirtyCount() {
        gaugeValue(MODULE_AI, GAUGE_AGENT_WATCH_DIRTY_COUNT).incrementAndGet();
    }
    
    public static void decrementAgentWatchDirtyCount() {
        gaugeValue(MODULE_AI, GAUGE_AGENT_WATCH_DIRTY_COUNT).decrementAndGet();
    }
    
    public static double getAgentWatchDirtyCount() {
        return gaugeValue(MODULE_AI, GAUGE_AGENT_WATCH_DIRTY_COUNT).get();
    }
    
    public static void observeConfigRequest(String method, String url, String code,
        long elapsedMillis) {
        observeRequest(MODULE_CONFIG, method, url, code, elapsedMillis);
    }
    
    public static void observeNamingRequest(String method, String url, String code,
        long elapsedMillis) {
        observeRequest(MODULE_NAMING, method, url, code, elapsedMillis);
    }
    
    /**
     * Increment the counter of failed naming requests.
     *
     * @param requestClass   simple class name of the failed request
     * @param responseStatus response result code, {@code NONE} when no response was received
     * @param responseCode   response error code, {@code NONE} when no response was received
     * @param exceptionClass simple class name of the thrown exception, {@code NONE} when no exception was thrown
     */
    public static void recordNamingRequestFailed(String requestClass, String responseStatus,
        String responseCode,
        String exceptionClass) {
        try {
            Counter.builder(NACOS_CLIENT_NAMING_REQUEST_FAILED).tag(TAG_MODULE, MODULE_NAMING)
                .tag(TAG_REQUEST_CLASS, requestClass).tag(TAG_RESPONSE_STATUS, responseStatus)
                .tag(TAG_RESPONSE_CODE, responseCode).tag(TAG_EXCEPTION_CLASS, exceptionClass)
                .register(Metrics.globalRegistry).increment();
        } catch (Throwable t) {
            logMetricsFailure("increment naming request failed counter", t);
        }
    }
    
    /**
     * Increment the counter of AI watch events.
     *
     * @param event  event name, a closed enum value
     * @param result event result, a closed enum value
     */
    public static void recordAgentWatchEvent(String event, String result) {
        aiWatchEventCount(event, result).incrementAndGet();
        try {
            Counter.builder(NACOS_CLIENT_AI_WATCH_EVENTS).tag(TAG_EVENT, event)
                .tag(TAG_RESULT, result)
                .register(Metrics.globalRegistry).increment();
        } catch (Throwable t) {
            logMetricsFailure("increment ai watch event counter", t);
        }
    }
    
    public static double getAgentWatchEventCount(String event, String result) {
        return aiWatchEventCount(event, result).get();
    }
    
    private static void observeRequest(String module, String method, String url, String code,
        long elapsedMillis) {
        try {
            Timer.builder(NACOS_CLIENT_REQUEST).tag(TAG_MODULE, module).tag(TAG_METHOD, method)
                .tag(TAG_URL, url)
                .tag(TAG_CODE, code).serviceLevelObjectives(REQUEST_DURATION_BUCKETS)
                .register(Metrics.globalRegistry).record(elapsedMillis, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            logMetricsFailure("observe request elapsed time", t);
        }
    }
    
    /**
     * Get the in process holder of a gauge value, registering the gauge on the global registry on first use.
     *
     * <p>The holder is kept by this class so that the current value can be read back and updated incrementally even
     * when no registry has been added by the application.
     */
    private static AtomicLong gaugeValue(String module, String name) {
        String key = module + KEY_SEPARATOR + name;
        AtomicLong existing = GAUGE_VALUES.get(key);
        if (null != existing) {
            return existing;
        }
        AtomicLong created = new AtomicLong();
        AtomicLong previous = GAUGE_VALUES.putIfAbsent(key, created);
        if (null != previous) {
            return previous;
        }
        registerGauge(module, name, created);
        return created;
    }
    
    private static void registerGauge(String module, String name, AtomicLong holder) {
        try {
            Gauge.builder(NACOS_MONITOR, holder, AtomicLong::get).tag(TAG_MODULE, module)
                .tag(TAG_NAME, name)
                .register(Metrics.globalRegistry);
        } catch (Throwable t) {
            logMetricsFailure("register gauge " + name, t);
        }
    }
    
    private static AtomicLong aiWatchEventCount(String event, String result) {
        return AI_WATCH_EVENT_COUNTS.computeIfAbsent(event + KEY_SEPARATOR + result,
            key -> new AtomicLong());
    }
    
    private static void logMetricsFailure(String action, Throwable throwable) {
        if (METRICS_FAILURE_LOGGED.compareAndSet(false, true)) {
            LOGGER.warn("Fail to {} for nacos client metrics, further failures will be suppressed.",
                action,
                throwable);
        }
    }
}
