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
 * Semantic type of {@link PublishPipelineResult#getMessage()} for clients that render review output.
 *
 * @author qiacheng.cxy
 * @since 3.2.0
 */
public enum PublishPipelineMessageType {
    
    /**
     * Plain text.
     */
    TEXT("text"),
    
    /**
     * JSON payload.
     */
    JSON("json"),
    
    /**
     * Markdown (e.g. skill-scanner {@code --format markdown} stdout).
     */
    MARKDOWN("markdown"),
    
    /**
     * HTML fragment or document.
     */
    HTML("html");
    
    private final String code;
    
    PublishPipelineMessageType(String code) {
        this.code = code;
    }
    
    /**
     * Wire / API value (lowercase), e.g. {@code markdown}.
     */
    public String getCode() {
        return code;
    }
    
    /**
     * Resolve from API wire value, or null if unknown.
     */
    public static PublishPipelineMessageType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (PublishPipelineMessageType t : values()) {
            if (t.code.equalsIgnoreCase(code)) {
                return t;
            }
        }
        return null;
    }
}
