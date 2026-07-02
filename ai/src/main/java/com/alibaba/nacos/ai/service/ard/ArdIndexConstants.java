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

package com.alibaba.nacos.ai.service.ard;

/**
 * Constants for Nacos Local ARD index.
 *
 * @author nacos
 */
final class ArdIndexConstants {
    
    static final String STATUS_ENABLED = "enabled";
    
    static final String STATUS_DISABLED = "disabled";
    
    static final String GENERATE_MODE_AUTO = "auto";
    
    static final String SOURCE_NACOS_LOCAL = "nacos-local";
    
    static final String RESOURCE_TYPE_MCP = "mcp";
    
    static final String MEDIA_TYPE_SKILL = "application/ai-skill+md";
    
    static final String MEDIA_TYPE_PROMPT = "application/vnd.nacos.ai-prompt+json";
    
    static final String MEDIA_TYPE_MCP = "application/mcp-server-card+json";
    
    static final String CHUNK_TYPE_DESCRIPTION = "description";
    
    static final String CHUNK_TYPE_CAPABILITY = "capability";
    
    static final String CHUNK_TYPE_REPRESENTATIVE_QUERY = "representative_query";
    
    static final String CHUNK_TYPE_TAG = "tag";
    
    static final String CHUNK_TYPE_METADATA_IO = "metadata_io";
    
    static final String CHUNK_TYPE_METADATA_RISK = "metadata_risk";
    
    static final String CHUNK_TYPE_NOT_FOR = "not_for";
    
    static final String CHUNK_TYPE_SKILL_CONTENT = "skill_content";
    
    static final String CHUNK_TYPE_PROMPT_CONTENT = "prompt_content";
    
    static final String CHUNK_TYPE_MCP_CONTENT = "mcp_content";
    
    static final String CHUNK_TYPE_AI_SUMMARY = "ai_summary";
    
    static final String CHUNK_TYPE_BILINGUAL_ALIAS = "bilingual_alias";
    
    static final String CHUNK_TYPE_CAPABILITY_SYNONYM = "capability_synonym";
    
    static final String CHUNK_TYPE_EXAMPLE_QUERY = "example_query";
    
    private ArdIndexConstants() {
    }
}
