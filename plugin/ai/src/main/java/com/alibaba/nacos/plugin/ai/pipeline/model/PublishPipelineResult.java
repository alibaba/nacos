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

import java.util.List;

/**
 * Result of a single publish pipeline plugin execution.
 *
 * @author mosong.lp
 * @since 3.2.0
 */
public class PublishPipelineResult {
    
    /**
     * Whether the review passed. The pipeline engine uses this to decide whether to continue
     * executing the next pipeline plugin.
     */
    private boolean passed;
    
    /**
     * Review message. Contains review opinions, suggestions, error descriptions, etc.
     * When passed is false, this should describe the reason for rejection.
     */
    private String message;
    
    /**
     * Semantic type of {@link #message} (e.g. markdown report from skill-scanner).
     */
    private PublishPipelineMessageType type;
    
    /**
     * Per-criterion audit outcomes for this plugin run.
     */
    private List<Checkpoint> checkpoints;
    
    public PublishPipelineResult() {
    }
    
    public PublishPipelineResult(boolean passed, String message) {
        this.passed = passed;
        this.message = message;
        this.type = PublishPipelineMessageType.TEXT;
    }
    
    /**
     * Create a passed result (message treated as plain text).
     */
    public static PublishPipelineResult pass(String message) {
        return pass(message, PublishPipelineMessageType.TEXT, null);
    }
    
    /**
     * Create a passed result with explicit message type and audit checkpoints.
     */
    public static PublishPipelineResult pass(String message, PublishPipelineMessageType type,
        List<Checkpoint> checkpoints) {
        PublishPipelineResult r = new PublishPipelineResult();
        r.passed = true;
        r.message = message;
        r.type = type != null ? type : PublishPipelineMessageType.TEXT;
        r.checkpoints = checkpoints;
        return r;
    }
    
    /**
     * Create a rejected result (message treated as plain text).
     */
    public static PublishPipelineResult reject(String message) {
        return reject(message, PublishPipelineMessageType.TEXT, null);
    }
    
    /**
     * Create a rejected result with explicit message type and audit checkpoints.
     */
    public static PublishPipelineResult reject(String message, PublishPipelineMessageType type,
        List<Checkpoint> checkpoints) {
        PublishPipelineResult r = new PublishPipelineResult();
        r.passed = false;
        r.message = message;
        r.type = type != null ? type : PublishPipelineMessageType.TEXT;
        r.checkpoints = checkpoints;
        return r;
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
    
    public PublishPipelineMessageType getType() {
        return type;
    }
    
    public void setType(PublishPipelineMessageType type) {
        this.type = type;
    }
    
    public List<Checkpoint> getCheckpoints() {
        return checkpoints;
    }
    
    public void setCheckpoints(List<Checkpoint> checkpoints) {
        this.checkpoints = checkpoints;
    }
    
    @Override
    public String toString() {
        return "PublishPipelineResult{passed=" + passed + ", message='" + message + "', type="
            + type
            + ", checkpoints=" + checkpoints + "}";
    }
}
