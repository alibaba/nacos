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

import com.alibaba.nacos.plugin.ai.pipeline.model.Checkpoint;

import java.util.List;
import java.util.Objects;

/**
 * Result of a single pipeline node execution, stored as JSON in the pipeline field.
 *
 * @author kiro
 * @since 3.2.0
 */
public class PipelineNodeResult {
    
    /**
     * Node ID, corresponding to {@code PublishPipelineService.pipelineId()}.
     */
    private String nodeId;
    
    /**
     * Execution time.
     */
    private String executedAt;
    
    /**
     * Whether the node passed.
     */
    private boolean passed;
    
    /**
     * Review message or error description.
     */
    private String message;
    
    /**
     * Semantic type of {@link #message} ({@code text}, {@code json}, {@code markdown}, {@code html}).
     */
    private String messageType;
    
    /**
     * Per-criterion audit outcomes from the pipeline plugin (e.g. security scanner checkpoints).
     */
    private List<Checkpoint> checkpoints;
    
    /**
     * Execution duration in milliseconds.
     */
    private long durationMs;
    
    public PipelineNodeResult() {
    }
    
    public String getNodeId() {
        return nodeId;
    }
    
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
    
    public String getExecutedAt() {
        return executedAt;
    }
    
    public void setExecutedAt(String executedAt) {
        this.executedAt = executedAt;
    }
    
    public boolean isPassed() {
        return passed;
    }
    
    public void setPassed(boolean passed) {
        this.passed = passed;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public String getMessageType() {
        return messageType;
    }
    
    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
    
    public List<Checkpoint> getCheckpoints() {
        return checkpoints;
    }
    
    public void setCheckpoints(List<Checkpoint> checkpoints) {
        this.checkpoints = checkpoints;
    }
    
    public long getDurationMs() {
        return durationMs;
    }
    
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PipelineNodeResult that = (PipelineNodeResult) o;
        return passed == that.passed && durationMs == that.durationMs
            && Objects.equals(nodeId, that.nodeId)
            && Objects.equals(executedAt, that.executedAt)
            && Objects.equals(message, that.message)
            && Objects.equals(messageType, that.messageType)
            && Objects.equals(checkpoints, that.checkpoints);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(nodeId, executedAt, passed, message, messageType, checkpoints,
            durationMs);
    }
}
