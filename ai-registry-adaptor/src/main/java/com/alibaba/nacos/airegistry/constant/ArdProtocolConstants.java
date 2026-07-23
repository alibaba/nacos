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

package com.alibaba.nacos.airegistry.constant;

/**
 * Constants owned by the external ARD protocol adaptor.
 *
 * @author nacos
 */
public final class ArdProtocolConstants {
    
    public static final String CLIENT_PATH = "/v3/ai/ard";
    
    public static final String WELL_KNOWN_PATH = "/.well-known";
    
    public static final String SPEC_VERSION = "1.0";
    
    public static final String FEDERATION_NONE = "none";
    
    public static final String KEY_CATALOG_HOST_IDENTIFIER =
        "nacos.ai.ard.catalog.host.identifier";
    
    public static final String DEFAULT_CATALOG_HOST_IDENTIFIER = "nacos";
    
    public static final String MEDIA_TYPE_REGISTRY = "application/ai-registry+json";
    
    public static final String MEDIA_TYPE_SKILL_PACKAGE = "application/agent-skills+zip";
    
    public static final String MEDIA_TYPE_PROMPT = "application/vnd.nacos.ai-prompt+json";
    
    public static final String MEDIA_TYPE_MCP = "application/mcp-server-card+json";
    
    private ArdProtocolConstants() {
    }
}
