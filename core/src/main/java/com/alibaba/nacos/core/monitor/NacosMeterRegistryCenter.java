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

import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Metrics unified usage center.
 *
 * @author <a href="mailto:liuyixiao0821@gmail.com">liuyixiao</a>
 */
@SuppressWarnings("all")
public final class NacosMeterRegistryCenter {
    
    // stable registries.
    
    public static final String CORE_STABLE_REGISTRY = "CORE_STABLE_REGISTRY";
    
    public static final String CONFIG_STABLE_REGISTRY = "CONFIG_STABLE_REGISTRY";
    
    public static final String NAMING_STABLE_REGISTRY = "NAMING_STABLE_REGISTRY";
    
    // dynamic registries.
    
    public static final String TOPN_CONFIG_CHANGE_REGISTRY = "TOPN_CONFIG_CHANGE_REGISTRY";
    
    public static final String TOPN_SERVICE_CHANGE_REGISTRY = "TOPN_SERVICE_CHANGE_REGISTRY";
    
    private static final ConcurrentHashMap<String, CompositeMeterRegistry> METER_REGISTRIES = new ConcurrentHashMap<>();
    
    private static PrometheusMeterRegistry PROMETHEUS_METER_REGISTRY = null;
    
    static {
        try {
            PROMETHEUS_METER_REGISTRY = ApplicationUtils.getBean(PrometheusMeterRegistry.class);
        } catch (Throwable t) {
            Loggers.CORE.warn("Metrics init failed :", t);
        }
        
        CompositeMeterRegistry compositeMeterRegistry;
        
        compositeMeterRegistry = new CompositeMeterRegistry();
        if (PROMETHEUS_METER_REGISTRY != null) {
            compositeMeterRegistry.add(PROMETHEUS_METER_REGISTRY);
        }
        METER_REGISTRIES.put(CORE_STABLE_REGISTRY, compositeMeterRegistry);
    
        compositeMeterRegistry = new CompositeMeterRegistry();
        if (PROMETHEUS_METER_REGISTRY != null) {
            compositeMeterRegistry.add(PROMETHEUS_METER_REGISTRY);
        }
        METER_REGISTRIES.put(CONFIG_STABLE_REGISTRY, compositeMeterRegistry);
    
        compositeMeterRegistry = new CompositeMeterRegistry();
        if (PROMETHEUS_METER_REGISTRY != null) {
            compositeMeterRegistry.add(PROMETHEUS_METER_REGISTRY);
        }
        METER_REGISTRIES.put(NAMING_STABLE_REGISTRY, compositeMeterRegistry);
    
        compositeMeterRegistry = new CompositeMeterRegistry();
        if (PROMETHEUS_METER_REGISTRY != null) {
            compositeMeterRegistry.add(PROMETHEUS_METER_REGISTRY);
        }
        METER_REGISTRIES.put(TOPN_CONFIG_CHANGE_REGISTRY, compositeMeterRegistry);
    
        compositeMeterRegistry = new CompositeMeterRegistry();
        if (PROMETHEUS_METER_REGISTRY != null) {
            compositeMeterRegistry.add(PROMETHEUS_METER_REGISTRY);
        }
        METER_REGISTRIES.put(TOPN_SERVICE_CHANGE_REGISTRY, compositeMeterRegistry);
    }
    
    public static Counter counter(String registry, String name, Iterable<Tag> tags) {
        CompositeMeterRegistry compositeMeterRegistry = METER_REGISTRIES.get(registry);
        if (compositeMeterRegistry != null) {
            return METER_REGISTRIES.get(registry).counter(name, tags);
        }
        return null;
    }
    
    public static Counter counter(String registry, String name, String... tags) {
        CompositeMeterRegistry compositeMeterRegistry = METER_REGISTRIES.get(registry);
        if (compositeMeterRegistry != null) {
            return METER_REGISTRIES.get(registry).counter(name, tags);
        }
        return null;
    }
    
    public static <T extends Number> T gauge(String registry, String name, Iterable<Tag> tags, T number) {
        CompositeMeterRegistry compositeMeterRegistry = METER_REGISTRIES.get(registry);
        if (compositeMeterRegistry != null) {
            return METER_REGISTRIES.get(registry).gauge(name, tags, number);
        }
        return null;
    }
    
    public static Timer timer(String registry, String name, Iterable<Tag> tags) {
        CompositeMeterRegistry compositeMeterRegistry = METER_REGISTRIES.get(registry);
        if (compositeMeterRegistry != null) {
            return METER_REGISTRIES.get(registry).timer(name, tags);
        }
        return null;
    }
    
    public static Timer timer(String registry, String name, String... tags) {
        CompositeMeterRegistry compositeMeterRegistry = METER_REGISTRIES.get(registry);
        if (compositeMeterRegistry != null) {
            return METER_REGISTRIES.get(registry).timer(name, tags);
        }
        return null;
    }
    
    public static DistributionSummary summary(String registry, String name, Iterable<Tag> tags) {
        CompositeMeterRegistry compositeMeterRegistry = METER_REGISTRIES.get(registry);
        if (compositeMeterRegistry != null) {
            return METER_REGISTRIES.get(registry).summary(name, tags);
        }
        return null;
    }
    
    public static DistributionSummary summary(String registry, String name, String... tags) {
        CompositeMeterRegistry compositeMeterRegistry = METER_REGISTRIES.get(registry);
        if (compositeMeterRegistry != null) {
            return METER_REGISTRIES.get(registry).summary(name, tags);
        }
        return null;
    }
    
    public static void clear(String registry) {
        METER_REGISTRIES.get(registry).clear();
    }
    
    /**
     * Just for test. Don't register meter by getMeterRegistry.
     *
     * @param registry
     * @return CompositeMeterRegistry in NacosMeterRegistryCenter.
     */
    public static CompositeMeterRegistry getMeterRegistry(String registry) {
        return METER_REGISTRIES.get(registry);
    }
}
