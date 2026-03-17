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
 * Pipeline execution record entity, persisted to the database.
 *
 * @author kiro
 * @since 3.2.0
 */
public class PipelineExecution {
    
    /**
     * Execution ID (UUID).
     */
    private String executionId;
    
    /**
     * Resource type.
     */
    private String resourceType;
    
    /**
     * Resource name.
     */
    private String resourceName;
    
    /**
     * Namespace ID.
     */
    private String namespaceId;
    
    /**
     * Resource version.
     */
    private String version;
    
    /**
     * Execution status: IN_PROGRESS, APPROVED, REJECTED.
     */
    private PipelineExecutionStatus status;
    
    /**
     * Node execution details list (serialized as JSON in the pipeline field).
     */
    private List<PipelineNodeResult> pipeline;
    
    /**
     * Creation time.
     */
    private long createTime;
    
    /**
     * Last update time.
     */
    private long updateTime;
    
    public PipelineExecution() {
    }
    
    public String getExecutionId() {
        return executionId;
    }
    
    public void setExecutionId(String executionId) {
        this.executionId = executionId;
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
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
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
    
    public long getCreateTime() {
        return createTime;
    }
    
    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }
    
    public long getUpdateTime() {
        return updateTime;
    }
    
    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }
}
