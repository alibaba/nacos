/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.dns;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Metrics collector for the Nacos DNS server.
 *
 * <p>This class is optional: when no {@link MeterRegistry} is available (e.g.
 * actuator not on classpath), a {@link SimpleMeterRegistry} is used internally
 * so the DNS server functions without any monitoring backend.
 *
 * <p>Exposes the following metrics:
 * <ul>
 *   <li>nacos.dns.queries.total - total DNS queries received</li>
 *   <li>nacos.dns.queries.success - queries answered successfully</li>
 *   <li>nacos.dns.queries.forwarded - queries forwarded to upstream</li>
 *   <li>nacos.dns.queries.failed - queries that failed</li>
 *   <li>nacos.dns.query.duration - query processing duration timer, tagged by
 *       {@code type} (A/AAAA/SRV/UNKNOWN) and {@code rcode} (NOERROR/NXDOMAIN/SERVFAIL/...)</li>
 * </ul>
 *
 * <p>Tag cardinality is bounded: type has at most 5 values and rcode uses the
 * standard DNS response code names. Domain names are never used as tags.
 *
 * @author Nacos
 */
public class NacosDnsMetrics {
    
    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Timer> timers = new ConcurrentHashMap<>();
    
    public NacosDnsMetrics(MeterRegistry registry) {
        this.registry = registry != null ? registry : new SimpleMeterRegistry();
    }
    
    private Counter counter(String name, String description) {
        return counters.computeIfAbsent(name,
            k -> Counter.builder(k).description(description).register(registry));
    }
    
    /**
     * Get or create a tagged timer for query duration.
     *
     * @param type  query record type (A, AAAA, SRV, UNKNOWN) — bounded cardinality
     * @param rcode DNS response code name (NOERROR, NXDOMAIN, SERVFAIL, ...) — bounded cardinality
     * @return the tagged Timer
     */
    private Timer durationTimer(String type, String rcode) {
        String key = type + "|" + rcode;
        return timers.computeIfAbsent(key, k -> Timer.builder("nacos.dns.query.duration")
            .description("DNS query processing duration")
            .tag("type", type)
            .tag("rcode", rcode)
            .register(registry));
    }
    
    /** Record a received query. */
    public void recordQuery() {
        counter("nacos.dns.queries.total", "Total DNS queries received").increment();
    }
    
    /** Record a successful query. */
    public void recordSuccess() {
        counter("nacos.dns.queries.success", "Queries answered successfully").increment();
    }
    
    /** Record a forwarded query. */
    public void recordForwarded() {
        counter("nacos.dns.queries.forwarded", "Queries forwarded to upstream DNS").increment();
    }
    
    /** Record a failed query. */
    public void recordFailed() {
        counter("nacos.dns.queries.failed", "Queries that failed").increment();
    }
    
    /**
     * Start a Micrometer {@link Timer.Sample} for measuring query duration.
     * Call {@link #stopSample(Timer.Sample, String, String)} with the resolved
     * type and rcode to record into the correct tagged timer.
     *
     * @return the active sample
     */
    public Timer.Sample startSample() {
        return Timer.start(registry);
    }
    
    /**
     * Stop a previously started sample and record its duration into the tagged timer.
     *
     * @param sample the sample started by {@link #startSample()}
     * @param type   query record type (bounded cardinality)
     * @param rcode  response code name (bounded cardinality)
     */
    public void stopSample(Timer.Sample sample, String type, String rcode) {
        if (sample != null) {
            sample.stop(durationTimer(type, rcode));
        }
    }
}
