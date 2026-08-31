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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MetricsMonitorTest {
    
    @BeforeEach
    void setUp() {
        RecordingMetricsProvider.reset();
    }
    
    @Test
    void testConstructor() {
        assertNotNull(new MetricsMonitor());
    }
    
    @Test
    void testProviderLoadedViaSpi() {
        assertInstanceOf(RecordingMetricsProvider.class, MetricsMonitor.getMetricsProvider());
    }
    
    @Test
    void testRecordServiceInfoMapSizeDelegates() {
        MetricsMonitor.recordServiceInfoMapSize(3);
        
        assertEquals(1, RecordingMetricsProvider.GAUGES.size());
        assertEquals("naming|serviceInfoMapSize|3.0", RecordingMetricsProvider.GAUGES.get(0));
    }
    
    @Test
    void testRecordListenConfigCountDelegates() {
        MetricsMonitor.recordListenConfigCount(5);
        
        assertEquals(1, RecordingMetricsProvider.GAUGES.size());
        assertEquals("config|listenConfigCount|5.0", RecordingMetricsProvider.GAUGES.get(0));
    }
    
    @Test
    void testObserveConfigRequestDelegates() {
        MetricsMonitor.observeConfigRequest("GET", "/cs/configs", "200", 12L);
        
        assertEquals(1, RecordingMetricsProvider.REQUESTS.size());
        assertEquals("config|GET|/cs/configs|200|12", RecordingMetricsProvider.REQUESTS.get(0));
    }
    
    @Test
    void testObserveNamingRequestDelegates() {
        MetricsMonitor.observeNamingRequest("POST", "/ns/instance", "NA", 7L);
        
        assertEquals(1, RecordingMetricsProvider.REQUESTS.size());
        assertEquals("naming|POST|/ns/instance|NA|7", RecordingMetricsProvider.REQUESTS.get(0));
    }
    
    @Test
    void testRecordNamingRequestFailedDelegates() {
        MetricsMonitor.recordNamingRequestFailed("InstanceRequest", "500", "10001", "NONE");
        
        assertEquals(1, RecordingMetricsProvider.FAILED_REQUESTS.size());
        assertEquals("InstanceRequest|500|10001|NONE",
            RecordingMetricsProvider.FAILED_REQUESTS.get(0));
    }
    
    @Test
    void testNoopProviderAcceptsAllCalls() {
        NoopClientMetricsProvider noopProvider = new NoopClientMetricsProvider();
        
        assertDoesNotThrow(() -> {
            noopProvider.recordGauge("naming", "serviceInfoMapSize", 1);
            noopProvider.observeRequest("config", "GET", "/cs/configs", "200", 10L);
            noopProvider.incrementNamingRequestFailed("InstanceRequest", "NONE", "NONE",
                "NacosException");
        });
    }
    
    /**
     * Recording provider registered via {@code META-INF/services} in test resources, standing in for a real metrics
     * adapter such as a Prometheus exporter.
     */
    public static class RecordingMetricsProvider implements NacosClientMetricsProvider {
        
        static final List<String> GAUGES = new ArrayList<>();
        
        static final List<String> REQUESTS = new ArrayList<>();
        
        static final List<String> FAILED_REQUESTS = new ArrayList<>();
        
        static void reset() {
            GAUGES.clear();
            REQUESTS.clear();
            FAILED_REQUESTS.clear();
        }
        
        @Override
        public void recordGauge(String module, String name, double value) {
            GAUGES.add(module + "|" + name + "|" + value);
        }
        
        @Override
        public void observeRequest(String module, String method, String url, String code,
            long elapsedMillis) {
            REQUESTS.add(module + "|" + method + "|" + url + "|" + code + "|" + elapsedMillis);
        }
        
        @Override
        public void incrementNamingRequestFailed(String requestClass, String responseStatus,
            String responseCode,
            String exceptionClass) {
            FAILED_REQUESTS.add(
                requestClass + "|" + responseStatus + "|" + responseCode + "|" + exceptionClass);
        }
    }
}
