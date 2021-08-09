package com.alibaba.nacos.metrics.register;
/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class PrometheusMetricsRegisterTest {
    
    private final MeterRegistry meterRegistry;
    
    private final PrometheusMetricsRegister prometheusMetricsRegister;
    
    public PrometheusMetricsRegisterTest() {
        meterRegistry = new SimpleMeterRegistry();
        prometheusMetricsRegister = new PrometheusMetricsRegister(meterRegistry);
    }
    
    @Test
    public void testRegisterGauge() {
        double gauge = 10.0;
        prometheusMetricsRegister
                .registerGauge("test_gauge", Collections.singletonList(new ImmutableTag("test", "gauge")), () -> gauge, "");
        Assert.assertEquals(gauge, meterRegistry.get("test_gauge").gauge().value(), 1e-9);
    }
    
    @Test
    public void testRegisterCounter() {
        prometheusMetricsRegister
                .registerCounter("test_counter", Collections.singletonList(new ImmutableTag("test", "counter")), "");
        Assert.assertEquals(0.0, meterRegistry.get("test_counter").counter().count(), 1e-9);
    }
    
    @Test
    public void testRegisterTimer() {
        // Amount and TimeUnit
        prometheusMetricsRegister
                .registerTimer("test_timer_unit", Collections.singletonList(new ImmutableTag("test", "timer")), 1, TimeUnit.SECONDS, "");
        Assert.assertEquals(1L, meterRegistry.get("test_timer_unit").timer().count());
        // Duration
        prometheusMetricsRegister
                .registerTimer("test_timer_duration", Collections.singletonList(new ImmutableTag("test", "timer")), Duration.ofSeconds(1), "");
        Assert.assertEquals(1L, meterRegistry.get("test_timer_duration").timer().count());
    }
    
    @Test
    public void testCounterIncrement() {
        // Increment by one step
        prometheusMetricsRegister
                .registerCounter("test_counter_oneStep", Collections.singletonList(new ImmutableTag("test", "counter")), "");
        prometheusMetricsRegister.counterIncrement("test_counter_oneStep", Collections.singletonList(new ImmutableTag("test", "counter")));
        Assert.assertEquals(1.0, meterRegistry.get("test_counter_oneStep").counter().count(), 1e-9);
        // Increment by amount step
        prometheusMetricsRegister
                .registerCounter("test_counter_amount", Collections.singletonList(new ImmutableTag("test", "counter")), "");
        prometheusMetricsRegister.counterIncrement("test_counter_amount", Collections.singletonList(new ImmutableTag("test", "counter")), 10);
        Assert.assertEquals(10.0, meterRegistry.get("test_counter_amount").counter().count(), 1e-9);
    }
    
}