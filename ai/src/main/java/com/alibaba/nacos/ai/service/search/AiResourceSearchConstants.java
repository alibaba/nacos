/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.search;

/**
 * Constants for Nacos Local AI resource index.
 *
 * @author nacos
 */
public final class AiResourceSearchConstants {
    
    public static final String STATUS_ENABLED = "enabled";
    
    public static final String STATUS_PENDING = "pending";
    
    public static final String GENERATE_MODE_AUTO = "auto";
    
    public static final String CHUNK_TYPE_DESCRIPTION = "description";
    
    public static final String CHUNK_TYPE_CAPABILITY = "capability";
    
    public static final String CHUNK_TYPE_REPRESENTATIVE_QUERY = "representative_query";
    
    public static final String CHUNK_TYPE_TAG = "tag";
    
    public static final String CHUNK_TYPE_METADATA_IO = "metadata_io";
    
    public static final String CHUNK_TYPE_METADATA_RISK = "metadata_risk";
    
    public static final String CHUNK_TYPE_NOT_FOR = "not_for";
    
    public static final String CHUNK_TYPE_SKILL_CONTENT = "skill_content";
    
    public static final String CHUNK_TYPE_PROMPT_CONTENT = "prompt_content";
    
    public static final String CHUNK_TYPE_MCP_CONTENT = "mcp_content";
    
    public static final String CHUNK_TYPE_AI_SUMMARY = "ai_summary";
    
    public static final String CHUNK_TYPE_SEARCH_INTENT = "search_intent";
    
    public static final String CHUNK_TYPE_SEARCH_TERM = "search_term";
    
    private AiResourceSearchConstants() {
    }
}
