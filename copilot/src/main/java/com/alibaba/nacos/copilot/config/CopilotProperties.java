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

package com.alibaba.nacos.copilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Copilot configuration properties.
 *
 * @author nacos
 */
@Component
@ConfigurationProperties(prefix = "nacos.copilot")
public class CopilotProperties {
    
    /**
     * Whether Copilot is enabled.
     */
    private boolean enabled = true;
    
    /**
     * Default namespace.
     */
    private String defaultNamespace = "public";
    
    /**
     * LLM configuration.
     */
    private LlmConfig llm = new LlmConfig();
    
    /**
     * Stream configuration.
     */
    private StreamConfig stream = new StreamConfig();
    
    /**
     * Retry configuration.
     */
    private RetryConfig retry = new RetryConfig();
    
    /**
     * Timeout configuration.
     */
    private TimeoutConfig timeout = new TimeoutConfig();
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getDefaultNamespace() {
        return defaultNamespace;
    }
    
    public void setDefaultNamespace(String defaultNamespace) {
        this.defaultNamespace = defaultNamespace;
    }
    
    public LlmConfig getLlm() {
        return llm;
    }
    
    public void setLlm(LlmConfig llm) {
        this.llm = llm;
    }
    
    public StreamConfig getStream() {
        return stream;
    }
    
    public void setStream(StreamConfig stream) {
        this.stream = stream;
    }
    
    public RetryConfig getRetry() {
        return retry;
    }
    
    public void setRetry(RetryConfig retry) {
        this.retry = retry;
    }
    
    public TimeoutConfig getTimeout() {
        return timeout;
    }
    
    public void setTimeout(TimeoutConfig timeout) {
        this.timeout = timeout;
    }
    
    public static class LlmConfig {
        
        /**
         * LLM provider: qwen, claude, openai, custom.
         */
        private String provider = "qwen";
        
        /**
         * API Key (from environment variable or config file).
         */
        private String apiKey;
        
        /**
         * API Endpoint.
         */
        private String endpoint = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
        
        /**
         * Model configuration.
         */
        private ModelConfig model = new ModelConfig();
        
        public String getProvider() {
            return provider;
        }
        
        public void setProvider(String provider) {
            this.provider = provider;
        }
        
        public String getApiKey() {
            return apiKey;
        }
        
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
        
        public String getEndpoint() {
            return endpoint;
        }
        
        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
        
        public ModelConfig getModel() {
            return model;
        }
        
        public void setModel(ModelConfig model) {
            this.model = model;
        }
        
        public static class ModelConfig {
            
            /**
             * Temperature.
             */
            private double temperature = 0.7;
            
            /**
             * Max tokens.
             */
            private int maxTokens = 4096;
            
            /**
             * Model name.
             */
            private String modelName = "qwen-turbo";
            
            public double getTemperature() {
                return temperature;
            }
            
            public void setTemperature(double temperature) {
                this.temperature = temperature;
            }
            
            public int getMaxTokens() {
                return maxTokens;
            }
            
            public void setMaxTokens(int maxTokens) {
                this.maxTokens = maxTokens;
            }
            
            public String getModelName() {
                return modelName;
            }
            
            public void setModelName(String modelName) {
                this.modelName = modelName;
            }
        }
    }
    
    public static class StreamConfig {
        
        /**
         * Whether stream is enabled.
         */
        private boolean enabled = true;
        
        /**
         * Chunk size.
         */
        private int chunkSize = 1024;
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        public int getChunkSize() {
            return chunkSize;
        }
        
        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }
    }
    
    public static class RetryConfig {
        
        /**
         * Max retry attempts.
         */
        private int maxAttempts = 3;
        
        /**
         * Backoff milliseconds.
         */
        private long backoffMs = 1000;
        
        public int getMaxAttempts() {
            return maxAttempts;
        }
        
        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
        
        public long getBackoffMs() {
            return backoffMs;
        }
        
        public void setBackoffMs(long backoffMs) {
            this.backoffMs = backoffMs;
        }
    }
    
    public static class TimeoutConfig {
        
        /**
         * Connect timeout in milliseconds.
         */
        private int connectMs = 5000;
        
        /**
         * Read timeout in milliseconds.
         */
        private int readMs = 60000;
        
        public int getConnectMs() {
            return connectMs;
        }
        
        public void setConnectMs(int connectMs) {
            this.connectMs = connectMs;
        }
        
        public int getReadMs() {
            return readMs;
        }
        
        public void setReadMs(int readMs) {
            this.readMs = readMs;
        }
    }
}
