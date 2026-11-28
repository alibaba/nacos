/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.control.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable runtime snapshot delivered to control plugin checks.
 *
 * <p>A context is assembled by the Nacos node that owns the runtime state and is
 * read by the selected control plugin. Numeric values describe node or request
 * state; attributes describe the resource that is being checked. The well-known
 * keys defined by this class are part of the control plugin contract, so custom
 * plugins may rely on them.
 *
 * <p>Missing metrics read as {@code 0}. Use {@link #hasMetric(String)} when a
 * plugin must distinguish an absent metric from a real zero.
 *
 * @author Drifter
 * @since 3.3.2
 */
public final class ControlContext {
    
    /**
     * Metric key for the number of active connections on the current node before the requested
     * connection is admitted.
     */
    public static final String CONNECTION_CURRENT_TOTAL = "connection.current.total";
    
    /**
     * Metric key for the number of active connections from the requesting client IP before
     * admission.
     */
    public static final String CONNECTION_CURRENT_IP = "connection.current.ip";
    
    /**
     * Metric key for the number of push tasks waiting in the naming delay engine.
     */
    public static final String PUSH_PENDING_TASKS = "push.pending.tasks";
    
    /**
     * Metric key for the number of subscribers targeted by the checked push task.
     */
    public static final String PUSH_TARGET_SUBSCRIBERS = "push.target.subscribers";
    
    /**
     * Attribute key for the namespace of the service that owns the checked push task.
     */
    public static final String PUSH_SERVICE_NAMESPACE = "push.service.namespace";
    
    /**
     * Attribute key for the group of the service that owns the checked push task.
     */
    public static final String PUSH_SERVICE_GROUP = "push.service.group";
    
    /**
     * Attribute key for the name of the service that owns the checked push task.
     */
    public static final String PUSH_SERVICE_NAME = "push.service.name";
    
    private static final ControlContext EMPTY =
        new ControlContext(0L, Collections.emptyMap(), Collections.emptyMap());
    
    private final long timestamp;
    
    private final Map<String, Long> metrics;
    
    private final Map<String, String> attributes;
    
    private ControlContext(long timestamp, Map<String, Long> metrics,
        Map<String, String> attributes) {
        this.timestamp = timestamp;
        this.metrics = Collections
            .unmodifiableMap(new HashMap<>(metrics));
        this.attributes = Collections
            .unmodifiableMap(new HashMap<>(attributes));
    }
    
    /**
     * Get the empty context shared by checks without runtime information.
     *
     * @return empty context
     */
    public static ControlContext empty() {
        return EMPTY;
    }
    
    /**
     * Create a builder for a new context snapshot.
     *
     * @return context builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Get the time when this snapshot was assembled, in milliseconds.
     *
     * @return snapshot timestamp
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Get all numeric metrics of this snapshot.
     *
     * @return unmodifiable metric map
     */
    public Map<String, Long> getMetrics() {
        return metrics;
    }
    
    /**
     * Get all string attributes of this snapshot.
     *
     * @return unmodifiable attribute map
     */
    public Map<String, String> getAttributes() {
        return attributes;
    }
    
    /**
     * Get a numeric metric.
     *
     * @param name metric key
     * @return metric value, or {@code 0} when the metric is absent
     */
    public long getMetric(String name) {
        Long value = metrics.get(name);
        return null == value ? 0L : value;
    }
    
    /**
     * Whether this snapshot contains the requested metric.
     *
     * @param name metric key
     * @return {@code true} when the metric is present
     */
    public boolean hasMetric(String name) {
        return metrics.containsKey(name);
    }
    
    /**
     * Get a string attribute.
     *
     * @param name attribute key
     * @return attribute value, or {@code null} when the attribute is absent
     */
    public String getAttribute(String name) {
        return attributes.get(name);
    }
    
    /**
     * Whether this snapshot contains neither metrics nor attributes.
     *
     * @return {@code true} when the snapshot is empty
     */
    public boolean isEmpty() {
        return metrics.isEmpty() && attributes.isEmpty();
    }
    
    /**
     * Builder for {@link ControlContext}.
     */
    public static final class Builder {
        
        private final Map<String, Long> metrics = new HashMap<>();
        
        private final Map<String, String> attributes = new HashMap<>();
        
        private long timestamp = System.currentTimeMillis();
        
        private Builder() {
        }
        
        /**
         * Set the snapshot timestamp.
         *
         * @param timestamp timestamp in milliseconds
         * @return this builder
         */
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        /**
         * Put one numeric metric, ignoring a {@code null} name.
         *
         * @param name metric key
         * @param value metric value
         * @return this builder
         */
        public Builder metric(String name, long value) {
            if (null != name) {
                metrics.put(name, value);
            }
            return this;
        }
        
        /**
         * Put one string attribute, ignoring a {@code null} name.
         *
         * @param name attribute key
         * @param value attribute value
         * @return this builder
         */
        public Builder attribute(String name, String value) {
            if (null != name) {
                attributes.put(name, value);
            }
            return this;
        }
        
        /**
         * Build an immutable context snapshot.
         *
         * @return context snapshot
         */
        public ControlContext build() {
            return new ControlContext(timestamp, metrics, attributes);
        }
    }
}
