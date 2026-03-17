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

import java.util.List;

/**
 * Pipeline execution result returned to the caller via callback.
 *
 * @author kiro
 * @since 3.2.0
 */
public class PipelineExecutionResult {
    
    /**
     * Execution ID.
     */
    private String executionId;
    
    /**
     * Final status: APPROVED or REJECTED.
     */
    private PipelineExecutionStatus status;
    
    /**
     * Node execution details.
     */
    private List<PipelineNodeResult> pipeline;
    
    public PipelineExecutionResult() {
    }
    
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
