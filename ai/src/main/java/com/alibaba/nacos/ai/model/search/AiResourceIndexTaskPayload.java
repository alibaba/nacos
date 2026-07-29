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

package com.alibaba.nacos.ai.model.search;

/**
 * Versioned input payload for the AI resource search-index task type.
 *
 * @author nacos
 */
public class AiResourceIndexTaskPayload {
    
    public static final int CURRENT_SCHEMA_VERSION = 1;
    
    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    
    private Subject subject;
    
    private Options options;
    
    public AiResourceIndexTaskPayload() {
    }
    
    public AiResourceIndexTaskPayload(String resourceType, String resourceName,
        boolean enhancementRequested) {
        this.subject = new Subject(resourceType, resourceName);
        this.options = new Options(enhancementRequested);
    }
    
    public int getSchemaVersion() {
        return schemaVersion;
    }
    
    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
    
    public Subject getSubject() {
        return subject;
    }
    
    public void setSubject(Subject subject) {
        this.subject = subject;
    }
    
    public Options getOptions() {
        return options;
    }
    
    public void setOptions(Options options) {
        this.options = options;
    }
    
    /**
     * Canonical resource identity owned by the search-index task type.
     */
    public static class Subject {
        
        private String resourceType;
        
        private String resourceName;
        
        public Subject() {
        }
        
        public Subject(String resourceType, String resourceName) {
            this.resourceType = resourceType;
            this.resourceName = resourceName;
        }
        
        public String getResourceType() {
            return resourceType;
        }
        
        public void setResourceType(String resourceType) {
            this.resourceType = resourceType;
        }
        
        public String getResourceName() {
            return resourceName;
        }
        
        public void setResourceName(String resourceName) {
            this.resourceName = resourceName;
        }
    }
    
    /**
     * Durable execution intent owned by the search-index task type.
     */
    public static class Options {
        
        private boolean enhancementRequested;
        
        public Options() {
        }
        
        public Options(boolean enhancementRequested) {
            this.enhancementRequested = enhancementRequested;
        }
        
        public boolean isEnhancementRequested() {
            return enhancementRequested;
        }
        
        public void setEnhancementRequested(boolean enhancementRequested) {
            this.enhancementRequested = enhancementRequested;
        }
    }
}
