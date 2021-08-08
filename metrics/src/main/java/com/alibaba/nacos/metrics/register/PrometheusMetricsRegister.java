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

package com.alibaba.nacos.metrics.register;

import com.alibaba.nacos.metrics.MetricsRegister;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * PrometheusMetricsRegister.
 * @author holdonbei
 */
@Component
public class PrometheusMetricsRegister implements MetricsRegister {
    
    /**
     * registry that contains all metrics from nacos.
     */
    private final MeterRegistry registry;
    
    /**
     * registry is injected by spring.
     *
     * @param registry registry provided by spring-boot-actuator
     */
    @Autowired
    public PrometheusMetricsRegister(MeterRegistry registry) {
        this.registry = registry;
    }
    
    @Override
    public void registerGauge(String name, Iterable<Tag> tags, Supplier<Number> number, String description) {
        Gauge.builder(name, number)
                .description(description)
                .tags(tags)
                .register(registry);
    }
    
    public void registerGauge(String name, Iterable<Tag> tags, Supplier<Number> number) {
        this.registerGauge(name, tags, number, "");
    }
    
    
    @Override
    public void registerCounter(String name, Iterable<Tag> tags, String description) {
        Counter.builder(name)
                .description(description)
                .tags(tags)
                .register(registry);
    }
    
    public void registerCounter(String name, Iterable<Tag> tags) {
        this.registerCounter(name, tags, "");
    }
    
    @Override
    public void registerTimer(String name, Iterable<Tag> tags, long amount, TimeUnit unit, String description) {
        Timer.builder(name)
                .tags(tags)
                .description(description)
                .register(registry)
                .record(amount, unit);
    }
    
    public void registerTimer(String name, Iterable<Tag> tags, long amount, TimeUnit unit) {
        this.registerTimer(name, tags, amount, unit, "");
    }
    
    @Override
    public void registerTimer(String name, Iterable<Tag> tags, long amount, Duration duration, String description) {
        Timer.builder(name)
                .tags(tags)
                .description(description)
                .register(registry)
                .record(duration);
    }
    
    public void registerTimer(String name, Iterable<Tag> tags, long amount, Duration duration) {
        this.registerTimer(name, tags, amount, duration);
    }
    
    public void counterIncrement(String name, Iterable<Tag> tags) {
        this.counterIncrement(name, tags, 0);
    }
    
    @Override
    public void counterIncrement(String name, Iterable<Tag> tags, long count) {
        registry.counter(name, tags).increment(count);
    }
}