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
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Metrics collector for the Nacos DNS server.
 *
 * <p>Exposes the following metrics:
 * <ul>
 *   <li>nacos.dns.queries.total - total DNS queries received</li>
 *   <li>nacos.dns.queries.success - queries answered successfully</li>
 *   <li>nacos.dns.queries.forwarded - queries forwarded to upstream</li>
 *   <li>nacos.dns.queries.failed - queries that failed</li>
 *   <li>nacos.dns.query.duration - query processing duration timer</li>
 * </ul>
 *
 * @author Nacos
 */
@Component
public class NacosDnsMetrics {
    
    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final Timer queryTimer;
    
    public NacosDnsMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.queryTimer = Timer.builder("nacos.dns.query.duration")
            .description("DNS query processing duration")
            .register(registry);
    }
    
    private Counter counter(String name, String description) {
        return counters.computeIfAbsent(name,
            k -> Counter.builder(k).description(description).register(registry));
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
    
    /** Get the query timer for recording duration. */
    public Timer getQueryTimer() {
        return queryTimer;
    }
}
