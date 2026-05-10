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

package com.alibaba.nacos.core.monitor;

import com.alibaba.nacos.sys.utils.ApplicationUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class NacosMeterRegistryCenterTest {
    
    @Mock
    private ConfigurableApplicationContext context;
    
    @AfterAll
    static void tearDown() {
        ApplicationUtils.injectContext(null);
    }
    
    @BeforeEach
    void initMeterRegistry() {
        ApplicationUtils.injectContext(context);
        when(context.getBean(PrometheusMeterRegistry.class)).thenReturn(null);
    }
    
    @Test
    void testGetMeterRegistry() {
        assertNotNull(NacosMeterRegistryCenter
            .getMeterRegistry(NacosMeterRegistryCenter.CORE_STABLE_REGISTRY));
        assertNotNull(NacosMeterRegistryCenter
            .getMeterRegistry(NacosMeterRegistryCenter.CONFIG_STABLE_REGISTRY));
        assertNotNull(NacosMeterRegistryCenter
            .getMeterRegistry(NacosMeterRegistryCenter.NAMING_STABLE_REGISTRY));
        assertNotNull(NacosMeterRegistryCenter
            .getMeterRegistry(NacosMeterRegistryCenter.TOPN_CONFIG_CHANGE_REGISTRY));
        assertNotNull(NacosMeterRegistryCenter
            .getMeterRegistry(NacosMeterRegistryCenter.TOPN_SERVICE_CHANGE_REGISTRY));
        assertNull(NacosMeterRegistryCenter.getMeterRegistry("unknown"));
    }
    
    @Test
    void testCounterWithTags() {
        Iterable<Tag> tags = Collections.singletonList(Tag.of("k", "v"));
        Counter c = NacosMeterRegistryCenter.counter(NacosMeterRegistryCenter.CORE_STABLE_REGISTRY,
            "test_counter", tags);
        assertNotNull(c);
        c.increment();
        assertNull(NacosMeterRegistryCenter.counter("unknown", "n", tags));
    }
    
    @Test
    void testCounterWithStringTags() {
        Counter c = NacosMeterRegistryCenter.counter(NacosMeterRegistryCenter.CORE_STABLE_REGISTRY,
            "test_c2", "k", "v");
        assertNotNull(c);
        c.increment(2);
        assertNull(NacosMeterRegistryCenter.counter("unknown", "n", "a", "b"));
    }
    
    @Test
    void testGauge() {
        Number n = NacosMeterRegistryCenter.gauge(NacosMeterRegistryCenter.CORE_STABLE_REGISTRY,
            "test_gauge",
            Collections.singletonList(Tag.of("g", "1")), 42);
        assertNotNull(n);
        assertEquals(42, n.intValue());
        assertNull(NacosMeterRegistryCenter.gauge("unknown", "g", Collections.emptyList(), 1));
    }
    
    @Test
    void testTimerWithTags() {
        Iterable<Tag> tags = Collections.singletonList(Tag.of("t", "1"));
        Timer t = NacosMeterRegistryCenter.timer(NacosMeterRegistryCenter.CORE_STABLE_REGISTRY,
            "test_timer", tags);
        assertNotNull(t);
        t.record(1, TimeUnit.SECONDS);
        assertNull(NacosMeterRegistryCenter.timer("unknown", "n", tags));
    }
    
    @Test
    void testTimerWithStringTags() {
        Timer t = NacosMeterRegistryCenter.timer(NacosMeterRegistryCenter.CORE_STABLE_REGISTRY,
            "test_t2", "t", "2");
        assertNotNull(t);
        t.record(2, TimeUnit.SECONDS);
        assertNull(NacosMeterRegistryCenter.timer("unknown", "n", "a", "b"));
    }
    
    @Test
    void testSummaryWithTags() {
        Iterable<Tag> tags = Collections.singletonList(Tag.of("s", "1"));
        DistributionSummary s = NacosMeterRegistryCenter
            .summary(NacosMeterRegistryCenter.CORE_STABLE_REGISTRY, "test_summary", tags);
        assertNotNull(s);
        s.record(10);
        assertNull(NacosMeterRegistryCenter.summary("unknown", "n", tags));
    }
    
    @Test
    void testSummaryWithStringTags() {
        DistributionSummary s = NacosMeterRegistryCenter
            .summary(NacosMeterRegistryCenter.CORE_STABLE_REGISTRY, "test_s2", "s", "2");
        assertNotNull(s);
        s.record(20);
        assertNull(NacosMeterRegistryCenter.summary("unknown", "n", "a", "b"));
    }
    
    @Test
    void testClear() {
        NacosMeterRegistryCenter.getMeterRegistry(NacosMeterRegistryCenter.LOCK_STABLE_REGISTRY)
            .counter("clear_test");
        NacosMeterRegistryCenter.clear(NacosMeterRegistryCenter.LOCK_STABLE_REGISTRY);
        assertNotNull(NacosMeterRegistryCenter
            .getMeterRegistry(NacosMeterRegistryCenter.LOCK_STABLE_REGISTRY));
    }
}
