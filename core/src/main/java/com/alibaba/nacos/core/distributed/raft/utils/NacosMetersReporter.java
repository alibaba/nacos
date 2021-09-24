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

package com.alibaba.nacos.core.distributed.raft.utils;

import com.codahale.metrics.Clock;
import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricAttribute;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Timer;

import java.text.DateFormat;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A reporter which outputs measurements to a Micrometer Registry.
 *
 * @author holdonbei
 */
public class NacosMetersReporter extends ScheduledReporter {
    
    private long cnt = 0;
    
    /**
     * Returns a new {@link Builder} for {@link NacosMetersReporter}.
     *
     * @param registry the registry to report
     * @return a {@link Builder} instance for a {@link ConsoleReporter}
     */
    public static Builder forRegistry(MetricRegistry registry) {
        return new Builder(registry);
    }
    
    /**
     * A builder for {@link NacosMetersReporter} instances.
     */
    public static class Builder {
        private final MetricRegistry registry;
        private Locale locale;
        private Clock clock;
        private TimeZone timeZone;
        private TimeUnit rateUnit;
        private TimeUnit durationUnit;
        private MetricFilter filter;
        private ScheduledExecutorService executor;
        private boolean shutdownExecutorOnStop;
        private Set<MetricAttribute> disabledMetricAttributes;
        
        private Builder(MetricRegistry registry) {
            this.registry = registry;
            this.locale = Locale.getDefault();
            this.clock = Clock.defaultClock();
            this.timeZone = TimeZone.getDefault();
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.filter = MetricFilter.ALL;
            this.executor = null;
            this.shutdownExecutorOnStop = true;
            disabledMetricAttributes = Collections.emptySet();
        }
        
        /**
         * Specifies whether or not, the executor (used for reporting) will be stopped with same time with reporter.
         * Default value is true.
         * Setting this parameter to false, has the sense in combining with providing external managed executor via {@link #scheduleOn(ScheduledExecutorService)}.
         *
         * @param shutdownExecutorOnStop if true, then executor will be stopped in same time with this reporter
         * @return {@code this}
         */
        public Builder shutdownExecutorOnStop(boolean shutdownExecutorOnStop) {
            this.shutdownExecutorOnStop = shutdownExecutorOnStop;
            return this;
        }
        
        /**
         * Specifies the executor to use while scheduling reporting of metrics.
         * Default value is null.
         * Null value leads to executor will be auto created on start.
         *
         * @param executor the executor to use while scheduling reporting of metrics.
         * @return {@code this}
         */
        public Builder scheduleOn(ScheduledExecutorService executor) {
            this.executor = executor;
            return this;
        }
        
        /**
         * Format numbers for the given {@link Locale}.
         *
         * @param locale a {@link Locale}
         * @return {@code this}
         */
        public Builder formattedFor(Locale locale) {
            this.locale = locale;
            return this;
        }
        
        /**
         * Use the given {@link Clock} instance for the time.
         *
         * @param clock a {@link Clock} instance
         * @return {@code this}
         */
        public Builder withClock(Clock clock) {
            this.clock = clock;
            return this;
        }
        
        /**
         * Use the given {@link TimeZone} for the time.
         *
         * @param timeZone a {@link TimeZone}
         * @return {@code this}
         */
        public Builder formattedFor(TimeZone timeZone) {
            this.timeZone = timeZone;
            return this;
        }
        
        /**
         * Convert rates to the given time unit.
         *
         * @param rateUnit a unit of time
         * @return {@code this}
         */
        public Builder convertRatesTo(TimeUnit rateUnit) {
            this.rateUnit = rateUnit;
            return this;
        }
        
        /**
         * Convert durations to the given time unit.
         *
         * @param durationUnit a unit of time
         * @return {@code this}
         */
        public Builder convertDurationsTo(TimeUnit durationUnit) {
            this.durationUnit = durationUnit;
            return this;
        }
        
        /**
         * Only report metrics which match the given filter.
         *
         * @param filter a {@link MetricFilter}
         * @return {@code this}
         */
        public Builder filter(MetricFilter filter) {
            this.filter = filter;
            return this;
        }
        
        /**
         * Don't report the passed metric attributes for all metrics (e.g. "p999", "stddev" or "m15").
         * See {@link MetricAttribute}.
         *
         * @param disabledMetricAttributes a {@link MetricFilter}
         * @return {@code this}
         */
        public Builder disabledMetricAttributes(Set<MetricAttribute> disabledMetricAttributes) {
            this.disabledMetricAttributes = disabledMetricAttributes;
            return this;
        }
        
        /**
         * Builds a {@link ConsoleReporter} with the given properties.
         *
         * @return a {@link ConsoleReporter}
         */
        public NacosMetersReporter build() {
            return new NacosMetersReporter(registry,
                    locale,
                    clock,
                    timeZone,
                    rateUnit,
                    durationUnit,
                    filter,
                    executor,
                    shutdownExecutorOnStop,
                    disabledMetricAttributes);
        }
    }
    
    private final Locale locale;
    private final Clock clock;
    private final DateFormat dateFormat;
    
    private NacosMetersReporter(MetricRegistry registry,
            Locale locale,
            Clock clock,
            TimeZone timeZone,
            TimeUnit rateUnit,
            TimeUnit durationUnit,
            MetricFilter filter,
            ScheduledExecutorService executor,
            boolean shutdownExecutorOnStop,
            Set<MetricAttribute> disabledMetricAttributes) {
        super(registry, "nacos-meters--reporter", filter, rateUnit, durationUnit, executor, shutdownExecutorOnStop, disabledMetricAttributes);
        this.locale = locale;
        this.clock = clock;
        this.dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                DateFormat.MEDIUM,
                locale);
        dateFormat.setTimeZone(timeZone);
    }
    
    @Override
    public void report(SortedMap<String, Gauge> gauges, SortedMap<String, Counter> counters,
            SortedMap<String, Histogram> histograms, SortedMap<String, Meter> meters, SortedMap<String, Timer> timers) {
        
        for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
            reportGauge(entry.getKey(), entry.getValue());
        }
    
        for (Map.Entry<String, Counter> entry : counters.entrySet()) {
            reportCounter(entry.getKey(), entry.getValue());
        }
    
        for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
            reportHistogram(entry.getKey(), entry.getValue());
        }
    
        for (Map.Entry<String, Meter> entry : meters.entrySet()) {
            reportMeter(entry.getKey(), entry.getValue());
        }
    
        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
            reportTimer(entry.getKey(), entry.getValue());
        }
    }
    
    private void reportTimer(String key, Timer value) {
    
    }
    
    private void reportMeter(String key, Meter value) {
    
    }
    
    private void reportHistogram(String key, Histogram value) {
    
    }
    
    private void reportCounter(String key, Counter value) {
    
    }
    
    private void reportGauge(String key, Gauge value) {
    
    }
}
