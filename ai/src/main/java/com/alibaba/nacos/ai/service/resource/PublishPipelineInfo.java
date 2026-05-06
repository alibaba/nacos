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

package com.alibaba.nacos.ai.service.resource;

import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeResult;

import java.util.List;

/**
 * Shared publish pipeline info structure for AI resources (Skill, AgentSpec, etc.).
 *
 * <p>Replaces the duplicated inner classes {@code SkillPublishPipelineInfo} and
 * {@code AgentSpecPublishPipelineInfo} that were previously defined in their respective
 * operation service implementations.</p>
 *
 * @author nacos
 */
public class PublishPipelineInfo {
    
    private String executionId;
    
    private PipelineExecutionStatus status;
    
    private List<PipelineNodeResult> pipeline;
    
    public String getExecutionId() {
        return executionId;
    }
    
    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }
    
    public PipelineExecutionStatus getStatus() {
        return status;
    }
    
    public void setStatus(PipelineExecutionStatus status) {
        this.status = status;
    }
    
    public List<PipelineNodeResult> getPipeline() {
        return pipeline;
    }
    
    public void setPipeline(List<PipelineNodeResult> pipeline) {
        this.pipeline = pipeline;
    }
}
