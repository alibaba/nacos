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

    public PublishPipelineResult() {
    }

    public PublishPipelineResult(boolean passed, String message) {
        this.passed = passed;
        this.message = message;
    }

    /**
     * Create a passed result.
     */
    public static PublishPipelineResult pass(String message) {
        return new PublishPipelineResult(true, message);
    }

    /**
     * Create a rejected result.
     */
    public static PublishPipelineResult reject(String message) {
        return new PublishPipelineResult(false, message);
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

    @Override
    public String toString() {
        return "PublishPipelineResult{passed=" + passed + ", message='" + message + "'}";
    }
}

