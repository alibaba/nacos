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

package com.alibaba.nacos.ai.pipeline.model;

import java.util.Properties;

/**
 * Configuration for a single pipeline node, containing the node ID and custom properties.
 *
 * @author kiro
 * @since 3.2.0
 */
public class PipelineNodeConfig {
    
    /**
     * Node ID, corresponding to {@code PublishPipelineService.pipelineId()}.
     */
    private String pipelineId;
    
    /**
     * Custom configuration properties for this node (e.g. endpoint, timeout).
     */
    private Properties properties;
    
    public PipelineNodeConfig() {
    }
    
    public String getPipelineId() {
        return pipelineId;
    }
    
    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }
    
    public Properties getProperties() {
        return properties;
    }
    
    public void setProperties(Properties properties) {
        this.properties = properties;
    }
}
