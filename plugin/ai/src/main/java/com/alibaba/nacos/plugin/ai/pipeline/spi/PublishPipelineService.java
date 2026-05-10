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

package com.alibaba.nacos.plugin.ai.pipeline.spi;

import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResult;

/**
 * Publish pipeline service SPI interface.
 *
 * <p>Inspired by {@code ConfigChangePluginService}, this interface provides an interception/review mechanism
 * before AI resource publishing. It is designed for generic AI resources (Skill, Prompt, MCP, etc.),
 * not limited to a single resource type.</p>
 *
 * <p>Multiple pipeline plugins are sorted by {@link #getPreferOrder()} and executed serially.
 * The next plugin is executed only after the previous one passes.</p>
 *
 * <p>Implementations should be created via {@link PublishPipelineServiceBuilder}.</p>
 *
 * @author mosong.lp
 * @since 3.2.0
 */
public interface PublishPipelineService {
    
    /**
     * Unique identifier for this pipeline plugin, e.g. "ai-review", "manual-confirm".
     *
     * @return pipeline plugin id
     */
    String pipelineId();
    
    /**
     * Execute the review/interception logic.
     *
     * @param context publish context containing resource metadata, version info, file contents, etc.
     * @return review result with passed status and comments
     */
    PublishPipelineResult execute(PublishPipelineContext context);
    
    /**
     * Execution order. Lower values execute first.
     * Inspired by {@code ConfigChangePluginService.getOrder()}.
     *
     * @return order value
     */
    int getPreferOrder();
    
    /**
     * Declare the resource types this plugin supports for review.
     * Inspired by {@code ConfigChangePluginService.pointcutMethodNames()},
     * used by {@code PublishPipelineManager} to route to the corresponding plugin list by resource type.
     *
     * @return array of supported resource types
     */
    PublishPipelineResourceType[] pipelineResourceTypes();
}
