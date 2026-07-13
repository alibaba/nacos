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

package com.alibaba.nacos.copilot.config;

import java.util.List;

/**
 * Console-facing metadata for a Copilot model provider.
 *
 * @author nacos
 */
public class CopilotProviderMetadata {
    
    private final String name;
    
    private final String defaultModel;
    
    private final String defaultProtocol;
    
    private final String defaultRegion;
    
    private final boolean streaming;
    
    private final List<ModelMetadata> models;
    
    private final List<ProtocolMetadata> protocols;
    
    public CopilotProviderMetadata(String name, String defaultModel, String defaultProtocol,
        String defaultRegion, boolean streaming, List<ModelMetadata> models,
        List<ProtocolMetadata> protocols) {
        this.name = name;
        this.defaultModel = defaultModel;
        this.defaultProtocol = defaultProtocol;
        this.defaultRegion = defaultRegion;
        this.streaming = streaming;
        this.models = List.copyOf(models);
        this.protocols = List.copyOf(protocols);
    }
    
    public String getName() {
        return name;
    }
    
    public String getDefaultModel() {
        return defaultModel;
    }
    
    public String getDefaultProtocol() {
        return defaultProtocol;
    }
    
    public String getDefaultRegion() {
        return defaultRegion;
    }
    
    public boolean isStreaming() {
        return streaming;
    }
    
    public List<ModelMetadata> getModels() {
        return models;
    }
    
    public List<ProtocolMetadata> getProtocols() {
        return protocols;
    }
    
    /**
     * Model option exposed by a provider.
     */
    public static class ModelMetadata {
        
        private final String modelId;
        
        private final Integer contextWindow;
        
        private final List<String> inputModalities;
        
        private final List<String> thinking;
        
        public ModelMetadata(String modelId, Integer contextWindow, List<String> inputModalities,
            List<String> thinking) {
            this.modelId = modelId;
            this.contextWindow = contextWindow;
            this.inputModalities = List.copyOf(inputModalities);
            this.thinking = List.copyOf(thinking);
        }
        
        public String getModelId() {
            return modelId;
        }
        
        public Integer getContextWindow() {
            return contextWindow;
        }
        
        public List<String> getInputModalities() {
            return inputModalities;
        }
        
        public List<String> getThinking() {
            return thinking;
        }
    }
    
    /**
     * API protocol exposed by a provider.
     */
    public static class ProtocolMetadata {
        
        private final String name;
        
        private final List<EndpointMetadata> endpoints;
        
        public ProtocolMetadata(String name, List<EndpointMetadata> endpoints) {
            this.name = name;
            this.endpoints = List.copyOf(endpoints);
        }
        
        public String getName() {
            return name;
        }
        
        public List<EndpointMetadata> getEndpoints() {
            return endpoints;
        }
    }
    
    /**
     * Regional API endpoint exposed by a protocol.
     */
    public static class EndpointMetadata {
        
        private final String region;
        
        private final String baseUrl;
        
        public EndpointMetadata(String region, String baseUrl) {
            this.region = region;
            this.baseUrl = baseUrl;
        }
        
        public String getRegion() {
            return region;
        }
        
        public String getBaseUrl() {
            return baseUrl;
        }
    }
}
