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

package com.alibaba.nacos.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.Histogram;

import java.util.function.Supplier;

/**
 * Metrics register.
 *
 * @author holdonbei
 */
public interface MetricsRegister {


    /**
     * Register Gauge.
     *
     * @param name name
     * @param tags Key/value pairs
     * @param description description for gauge
     * @param number number for gauge
     */
    void registerGauge(String name, Iterable<Tag> tags, String description, Supplier<Number> number);


    /**
     * Register Gauge.
     *
     * @param name name
     * @param tags Key/value pairs
     * @param description description for gauge
     * @return Gauge
     */
    Gauge registerGauge(String name, Iterable<Tag> tags, String description);

    /**
     * Register Counter.
     *
     * @param name name
     * @param tags Key/value pairs
     * @param description description for gauge
     * @return Counter
     */
    Counter registerCounter(String name, Iterable<Tag> tags, String description);


    /**
     * RegisterTimer.
     *
     * @param name name
     * @param tags Key/value pairs
     * @param description description for gauge
     * @return Timer
     */
    Timer registerTimer(String name, Iterable<Tag> tags, String description);


    /**
     * Register Histogram.
     *
     * @param name name
     * @param tags Key/value pairs
     * @param description description for gauge
     * @return Histogram
     */
    Histogram registerHistogram(String name, Iterable<Tag> tags, String description);


    /**
     * Summary.
     *
     * @param name name
     * @param tags Key/value pairs
     * @param description description for gauge
     * @return DistributionSummary
     */
    DistributionSummary summary(String name, Iterable<Tag> tags, String description);


    /**
     * Counter Increment.
     *
     * @param name name
     * @param tags Key/value pairs
     */
    void counterIncrement(String name, Iterable<Tag> tags);


    /**
     * Counter increment by count.
     *
     * @param name name
     * @param tags Key/value pairs
     * @param count step
     */
    void counterIncrement(String name, Iterable<Tag> tags, long count);

}
