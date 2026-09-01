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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MetricsMonitor} on top of the Micrometer global registry.
 *
 * @author Nacos
 */
class MetricsMonitorTest {
    
    private final List<MeterRegistry> addedRegistries = new ArrayList<>();
    
    @AfterEach
    void tearDown() {
        for (MeterRegistry registry : addedRegistries) {
            Metrics.globalRegistry.remove(registry);
        }
        addedRegistries.clear();
    }
    
    private <T extends MeterRegistry> T addRegistry(T registry) {
        Metrics.globalRegistry.add(registry);
        addedRegistries.add(registry);
        return registry;
    }
    
    @Test
    void testNoRegistryConfiguredDoesNotAffectClientBehavior() {
        assertDoesNotThrow(() -> {
            MetricsMonitor.recordServiceInfoMapSize(3);
            MetricsMonitor.recordListenConfigCount(7);
            MetricsMonitor.observeConfigRequest("GET", "/cs/configs/no-registry", "200", 12);
            MetricsMonitor.observeNamingRequest("GET", "/ns/instance/list/no-registry", "200", 12);
            MetricsMonitor.recordNamingRequestFailed("InstanceRequest", "500", "10001",
                "NacosException");
        });
    }
    
    @Test
    void testRegistryAddedAfterMetersCreationCollectsSubsequentValues() {
        MetricsMonitor.recordServiceInfoMapSize(5);
        MetricsMonitor.observeConfigRequest("GET", "/cs/configs/late-registry", "200", 20);
        
        SimpleMeterRegistry lateRegistry = addRegistry(new SimpleMeterRegistry());
        
        Gauge gauge = lateRegistry.find("nacos_monitor")
            .tags("module", "naming", "name", "serviceInfoMapSize")
            .gauge();
        assertNotNull(gauge);
        assertEquals(5.0, gauge.value());
        
        MetricsMonitor.observeConfigRequest("GET", "/cs/configs/late-registry", "200", 20);
        Timer timer = lateRegistry.find("nacos_client_request")
            .tags("module", "config", "method", "GET", "url", "/cs/configs/late-registry", "code",
                "200")
            .timer();
        assertNotNull(timer);
        assertTrue(timer.count() >= 1);
        
        MetricsMonitor.recordNamingRequestFailed("InstanceRequest", "NONE", "NONE",
            "NacosException");
        Counter counter = lateRegistry.find("nacos_client_naming_request_failed_total")
            .tags("module", "naming", "req_class", "InstanceRequest", "res_status", "NONE",
                "res_code", "NONE",
                "err_class", "NacosException")
            .counter();
        assertNotNull(counter);
        assertTrue(counter.count() >= 1.0);
    }
    
    @Test
    void testPrometheusScrapeContainsExpectedMetrics() {
        PrometheusMeterRegistry prometheusRegistry =
            addRegistry(new PrometheusMeterRegistry(PrometheusConfig.DEFAULT));
        
        MetricsMonitor.recordServiceInfoMapSize(3);
        MetricsMonitor.recordListenConfigCount(7);
        MetricsMonitor.observeConfigRequest("GET", "/cs/configs/scrape", "200", 12);
        MetricsMonitor.recordNamingRequestFailed("InstanceRequest", "500", "10001",
            "NacosException");
        
        String scrape = prometheusRegistry.scrape();
        assertTrue(scrape.contains("nacos_monitor{"));
        assertTrue(scrape.contains("nacos_client_request_seconds_bucket"));
        assertTrue(scrape.contains("nacos_client_request_seconds_count"));
        assertTrue(scrape.contains("nacos_client_request_seconds_sum"));
        assertTrue(scrape.contains("le=\"0.005\""));
        assertTrue(scrape.contains("le=\"10.0\""));
        assertTrue(scrape.contains("le=\"+Inf\""));
        assertTrue(scrape.contains("url=\"/cs/configs/scrape\""));
        assertTrue(scrape.contains("nacos_client_naming_request_failed_total{"));
        assertFalse(scrape.contains("nacos_client_naming_request_failed_total_total"));
        assertTrue(scrape.contains("req_class=\"InstanceRequest\""));
        
        assertEquals(3.0, prometheusRegistry.find("nacos_monitor")
            .tags("module", "naming", "name", "serviceInfoMapSize").gauge().value());
        assertEquals(7.0, prometheusRegistry.find("nacos_monitor")
            .tags("module", "config", "name", "listenConfigCount").gauge().value());
        Timer timer = prometheusRegistry.find("nacos_client_request")
            .tags("module", "config", "method", "GET", "url", "/cs/configs/scrape", "code", "200")
            .timer();
        assertNotNull(timer);
        assertEquals(12.0, timer.totalTime(TimeUnit.MILLISECONDS));
    }
    
    @Test
    void testRegistryFailuresDoNotAffectBusinessRequests() {
        addRegistry(new ThrowingMeterRegistry());
        
        assertDoesNotThrow(() -> {
            MetricsMonitor.recordServiceInfoMapSize(1);
            MetricsMonitor.recordListenConfigCount(2);
            MetricsMonitor.observeNamingRequest("GET", "/ns/instance/list/failure", "200", 10);
            MetricsMonitor.recordNamingRequestFailed("FailureRequest", "NONE", "NONE",
                "NacosException");
        });
        
        String businessResult = simulateConfigRequestWithMetricsInFinally();
        assertEquals("business-ok", businessResult);
    }
    
    @Test
    void testAgentWatchGaugesSupportIncrementAndReadBackWithoutRegistry() {
        double intents = MetricsMonitor.agentWatchIntentCount().get();
        double pending = MetricsMonitor.agentWatchPendingCount().get();
        double dirty = MetricsMonitor.agentWatchDirtyCount().get();
        
        MetricsMonitor.agentWatchIntentCount().increment();
        MetricsMonitor.agentWatchPendingCount().increment();
        MetricsMonitor.agentWatchDirtyCount().increment();
        assertEquals(intents + 1D, MetricsMonitor.agentWatchIntentCount().get());
        assertEquals(pending + 1D, MetricsMonitor.agentWatchPendingCount().get());
        assertEquals(dirty + 1D, MetricsMonitor.agentWatchDirtyCount().get());
        
        MetricsMonitor.agentWatchIntentCount().decrement();
        MetricsMonitor.agentWatchPendingCount().decrement();
        MetricsMonitor.agentWatchDirtyCount().decrement();
        assertEquals(intents, MetricsMonitor.agentWatchIntentCount().get());
        assertEquals(pending, MetricsMonitor.agentWatchPendingCount().get());
        assertEquals(dirty, MetricsMonitor.agentWatchDirtyCount().get());
    }
    
    @Test
    void testAgentWatchEventCounterIsExportedAndReadableByLabels() {
        double before = MetricsMonitor.getAgentWatchEventCount("listener_callback", "success");
        SimpleMeterRegistry meterRegistry = addRegistry(new SimpleMeterRegistry());
        
        MetricsMonitor.recordAgentWatchEvent("listener_callback", "success");
        
        assertEquals(before + 1D,
            MetricsMonitor.getAgentWatchEventCount("listener_callback", "success"));
        Counter counter = meterRegistry.find("nacos_client_ai_watch_events_total")
            .tags("event", "listener_callback", "result", "success").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
        assertEquals(0.0, MetricsMonitor.getAgentWatchEventCount("listener_callback", "failed"));
    }
    
    /**
     * Mirrors {@code MetricsHttpAgent}: metrics are recorded in a {@code finally} block, so a registry failure must
     * neither mask the original exception nor replace a successful result.
     */
    private String simulateConfigRequestWithMetricsInFinally() {
        String result;
        try {
            result = "business-ok";
        } finally {
            MetricsMonitor.observeConfigRequest("GET", "/cs/configs/failure", "200", 10);
        }
        return result;
    }
    
    /**
     * Fails on the meters created by the recording calls under test. Gauges are registered once during class
     * initialization, so a gauge failure cannot be reproduced here and is not simulated.
     */
    private static class ThrowingMeterRegistry extends SimpleMeterRegistry {
        
        @Override
        protected Timer newTimer(Timer.Id id,
            DistributionStatisticConfig distributionStatisticConfig,
            PauseDetector pauseDetector) {
            if (isFailureProbe(id.getTag("url"))) {
                throw new IllegalStateException("simulated registry failure");
            }
            return super.newTimer(id, distributionStatisticConfig, pauseDetector);
        }
        
        @Override
        protected Counter newCounter(Counter.Id id) {
            if ("FailureRequest".equals(id.getTag("req_class"))) {
                throw new IllegalStateException("simulated registry failure");
            }
            return super.newCounter(id);
        }
        
        private boolean isFailureProbe(String tagValue) {
            return tagValue != null && tagValue.contains("failure");
        }
    }
}
