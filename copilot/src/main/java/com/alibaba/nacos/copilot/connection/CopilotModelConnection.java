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

package com.alibaba.nacos.copilot.connection;

import io.agentscope.core.model.ModelCreationContext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One model connection managed by Nacos.
 *
 * <p>The first four fields are Nacos management fields. The remaining fields mirror
 * {@link ModelCreationContext} and are passed through to AgentScope without translation.
 *
 * <p>Instances are immutable, so a connection held by {@link ConnectionTable} can not be
 * changed without a refresh.
 *
 * @author nacos
 */
public final class CopilotModelConnection {
    
    private final String name;
    
    private final String providerId;
    
    private final String displayName;
    
    private final boolean enabled;
    
    private final ConnectionSource source;
    
    private final int priority;
    
    private final boolean available;
    
    private final String unavailableReason;
    
    private final String model;
    
    private final String apiKey;
    
    private final String baseUrl;
    
    private final String endpointPath;
    
    private final Boolean stream;
    
    private final Boolean enableThinking;
    
    private final Map<String, Object> options;
    
    private CopilotModelConnection(Builder builder) {
        this.name = builder.name;
        this.providerId = builder.providerId;
        this.displayName = builder.displayName;
        this.enabled = builder.enabled;
        this.source = builder.source;
        this.priority = builder.priority;
        this.available = builder.available;
        this.unavailableReason = builder.unavailableReason;
        this.model = builder.model;
        this.apiKey = builder.apiKey;
        this.baseUrl = builder.baseUrl;
        this.endpointPath = builder.endpointPath;
        this.stream = builder.stream;
        this.enableThinking = builder.enableThinking;
        this.options = builder.options == null ? Collections.emptyMap()
            : Collections.unmodifiableMap(new LinkedHashMap<>(builder.options));
    }
    
    /**
     * Create a builder for a connection.
     *
     * @return builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Return a copy of this connection with the load-time validation result.
     *
     * @param available         whether the connection can currently create a model
     * @param unavailableReason diagnostic reason, or {@code null} when available
     * @return connection copy carrying the validation result
     */
    public CopilotModelConnection withAvailability(boolean available, String unavailableReason) {
        return new Builder(this).available(available).unavailableReason(unavailableReason)
            .build();
    }
    
    /**
     * Build the model id resolved by AgentScope, in {@code providerId:model} form.
     *
     * @return model id
     */
    public String getModelId() {
        return providerId + ":" + model;
    }
    
    /**
     * Convert the passthrough fields to an AgentScope model creation context.
     *
     * @return model creation context
     */
    public ModelCreationContext toCreationContext() {
        ModelCreationContext.Builder builder = ModelCreationContext.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .endpointPath(endpointPath)
            .stream(stream)
            .enableThinking(enableThinking);
        options.forEach(builder::option);
        return builder.build();
    }
    
    public String getName() {
        return name;
    }
    
    public String getProviderId() {
        return providerId;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public ConnectionSource getSource() {
        return source;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public boolean isAvailable() {
        return available;
    }
    
    public String getUnavailableReason() {
        return unavailableReason;
    }
    
    public String getModel() {
        return model;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public String getEndpointPath() {
        return endpointPath;
    }
    
    public Boolean getStream() {
        return stream;
    }
    
    public Boolean getEnableThinking() {
        return enableThinking;
    }
    
    public Map<String, Object> getOptions() {
        return options;
    }
    
    /**
     * Builder for {@link CopilotModelConnection}.
     */
    public static final class Builder {
        
        private String name;
        
        private String providerId;
        
        private String displayName;
        
        private boolean enabled = true;
        
        private ConnectionSource source = ConnectionSource.LEGACY;
        
        private int priority = 100;
        
        private boolean available = true;
        
        private String unavailableReason;
        
        private String model;
        
        private String apiKey;
        
        private String baseUrl;
        
        private String endpointPath;
        
        private Boolean stream;
        
        private Boolean enableThinking;
        
        private Map<String, Object> options;
        
        private Builder() {
        }
        
        private Builder(CopilotModelConnection connection) {
            this.name = connection.name;
            this.providerId = connection.providerId;
            this.displayName = connection.displayName;
            this.enabled = connection.enabled;
            this.source = connection.source;
            this.priority = connection.priority;
            this.available = connection.available;
            this.unavailableReason = connection.unavailableReason;
            this.model = connection.model;
            this.apiKey = connection.apiKey;
            this.baseUrl = connection.baseUrl;
            this.endpointPath = connection.endpointPath;
            this.stream = connection.stream;
            this.enableThinking = connection.enableThinking;
            this.options = connection.options;
        }
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder providerId(String providerId) {
            this.providerId = providerId;
            return this;
        }
        
        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder source(ConnectionSource source) {
            this.source = source;
            return this;
        }
        
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder available(boolean available) {
            this.available = available;
            return this;
        }
        
        public Builder unavailableReason(String unavailableReason) {
            this.unavailableReason = unavailableReason;
            return this;
        }
        
        public Builder model(String model) {
            this.model = model;
            return this;
        }
        
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }
        
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }
        
        public Builder endpointPath(String endpointPath) {
            this.endpointPath = endpointPath;
            return this;
        }
        
        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }
        
        public Builder enableThinking(Boolean enableThinking) {
            this.enableThinking = enableThinking;
            return this;
        }
        
        public Builder options(Map<String, Object> options) {
            this.options = options;
            return this;
        }
        
        /**
         * Build the connection.
         *
         * @return immutable connection
         */
        public CopilotModelConnection build() {
            return new CopilotModelConnection(this);
        }
    }
}
