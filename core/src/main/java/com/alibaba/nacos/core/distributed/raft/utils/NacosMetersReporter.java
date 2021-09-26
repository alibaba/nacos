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

import com.alibaba.nacos.metrics.manager.CoreMetricsConstant;
import com.alibaba.nacos.metrics.manager.MetricsManager;
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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A reporter which outputs measurements to a Micrometer Registry.
 *
 * @author holdonbei
 */
public class NacosMetersReporter extends ScheduledReporter {
    
    private String groupName;
    
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
        
        private String groupName;
        
        private TimeUnit rateUnit;
        
        private TimeUnit durationUnit;
        
        private MetricFilter filter;
        
        private ScheduledExecutorService executor;
        
        private boolean shutdownExecutorOnStop;
        
        private Set<MetricAttribute> disabledMetricAttributes;
        
        private Builder(MetricRegistry registry) {
            this.registry = registry;
            this.rateUnit = TimeUnit.SECONDS;
            this.durationUnit = TimeUnit.MILLISECONDS;
            this.filter = MetricFilter.ALL;
            this.executor = null;
            this.shutdownExecutorOnStop = true;
            disabledMetricAttributes = Collections.emptySet();
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
         * groupName for this reporter.
         *
         * @param groupName groupName
         * @return {@code this}
         */
        public Builder setGroupName(String groupName) {
            this.groupName = groupName;
            return this;
        }
        
        /**
         * Builds a {@link ConsoleReporter} with the given properties.
         *
         * @return a {@link ConsoleReporter}
         */
        public NacosMetersReporter build() {
            return new NacosMetersReporter(registry,
                    groupName,
                    rateUnit,
                    durationUnit,
                    filter,
                    executor,
                    shutdownExecutorOnStop,
                    disabledMetricAttributes);
        }
    }
    
    private NacosMetersReporter(MetricRegistry registry,
            String groupName,
            TimeUnit rateUnit,
            TimeUnit durationUnit,
            MetricFilter filter,
            ScheduledExecutorService executor,
            boolean shutdownExecutorOnStop,
            Set<MetricAttribute> disabledMetricAttributes) {
        super(registry, "nacos-meters--reporter", filter, rateUnit, durationUnit, executor, shutdownExecutorOnStop, disabledMetricAttributes);
        this.groupName = groupName;
    }
    
    @Override
    public void report(SortedMap<String, Gauge> gauges, SortedMap<String, Counter> counters,
            SortedMap<String, Histogram> histograms, SortedMap<String, Meter> meters, SortedMap<String, Timer> timers) {
        
        for (Map.Entry<String, Gauge> entry : gauges.entrySet()) {
            reportGauge(entry.getKey(), entry.getValue());
        }
    
        for (Map.Entry<String, Histogram> entry : histograms.entrySet()) {
            reportHistogram(entry.getKey(), entry.getValue());
        }
    
        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
            reportTimer(entry.getKey(), entry.getValue());
        }
    }
    
    private void reportTimer(String key, Timer value) {
        String metricsKey = key.replace("-", "_");
        if (metricsKey.equals(CoreMetricsConstant.APPEND_LOGS)
                || metricsKey.equals(CoreMetricsConstant.REPLICATE_ENTRIES)
                || metricsKey.equals(CoreMetricsConstant.PRE_VOTE)
                || metricsKey.equals(CoreMetricsConstant.REQUEST_VOTE)) {
            MetricsManager.gauge(metricsKey.concat("_total"),
                            CoreMetricsConstant.GROUP_NAME, groupName)
                    .getAndAdd(value.getCount());
        }
    }
    
    private void reportHistogram(String key, Histogram value) {
        String metricsKey = key.replace("-", "_");
        if (metricsKey.equals(CoreMetricsConstant.APPEND_LOGS_COUNT)
                || metricsKey.equals(CoreMetricsConstant.REPLICATE_ENTRIES_COUNT)) {
            MetricsManager.gauge(metricsKey,
                            CoreMetricsConstant.GROUP_NAME, groupName)
                    .set(value.getCount());
        }
    }

    private void reportGauge(String key, Gauge value) {
        String metricsKey = key.replace("-", "_");
        if (metricsKey.equals(CoreMetricsConstant.NEXT_INDEX)
                || metricsKey.equals(CoreMetricsConstant.LOG_LAGS)) {
            MetricsManager.gauge(metricsKey,
                            CoreMetricsConstant.GROUP_NAME, groupName)
                    .set(Long.parseLong(value.getValue().toString()));
        }
    }
}
