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

package com.alibaba.nacos.plugin.ai.pipeline.model;

import com.alibaba.nacos.plugin.ai.pipeline.spi.PublishPipelineService;

/**
 * Publish pipeline resource type enumeration.
 *
 * <p>Each AI resource type corresponds to an enum value. Pipeline plugins declare supported types
 * via {@link PublishPipelineService#pipelineResourceTypes()}, and {@code PublishPipelineManager}
 * routes to the corresponding plugin list by resource type.</p>
 *
 * @author mosong.lp
 * @since 3.2.0
 */
public enum PublishPipelineResourceType {
    
    /**
     * Skill resource type.
     */
    SKILL,
    
    /**
     * Prompt resource type.
     */
    PROMPT,
    
    /**
     * AgentSpec resource type.
     */
    AGENTSPEC
}
