package com.alibaba.nacos.metrics.register;

import com.alibaba.nacos.metrics.MetricsRegister;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.distribution.Histogram;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class PrometheusMetricsRegister implements MetricsRegister {

    /**
     * registry that contains all metrics from nacos
     */
    private final MeterRegistry registry;

    /**
     * @param registry registry provided by spring-boot-actuator
     */
    @Autowired
    public PrometheusMetricsRegister(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void registerGauge(String name, Iterable<Tag> tags, String description, Supplier<Number> number) {
        Gauge.builder(name, number)
                .description(description)
                .tags(tags)
                .register(registry);
    }

    @Override
    public Gauge registerGauge(String name, Iterable<Tag> tags, String description) {
        return Gauge.builder(name, () -> 0)
                .description(description)
                .tags(tags)
                .register(registry);
    }

    @Override
    public Counter registerCounter(String name, Iterable<Tag> tags, String description) {
        return Counter.builder(name)
                .description(description)
                .tags(tags)
                .register(registry);
    }

    @Override
    public Timer registerTimer(String name, Iterable<Tag> tags, String description) {
        return Timer.builder(name)
                .tags(tags)
                .description(description)
                .register(registry);
    }

    @Override
    public Histogram registerHistogram(String name, Iterable<Tag> tags, String description) {
        return (Histogram) DistributionSummary.builder(name)
                .description(description)
                .tags(tags)
                .publishPercentileHistogram()
                .register(registry);
    }

    @Override
    public DistributionSummary summary(String name, Iterable<Tag> tags, String description) {
        return DistributionSummary.builder(name)
                .description(description)
                .tags(tags)
                .register(registry);
    }

    @Override
    public void counterIncrement(String name, Iterable<Tag> tags) {
        registry.get(name)
                .tags(tags)
                .counter()
                .increment();
    }

    @Override
    public void counterIncrement(String name, Iterable<Tag> tags, long count) {
        registry.get(name)
                .tags(tags)
                .counter()
                .increment(count);
    }
}
