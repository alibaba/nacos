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

package com.alibaba.nacos.api.ai.constant;

import com.alibaba.nacos.api.ai.model.mcp.registry.McpServerStatusEnum;

/**
 * Nacos Ai contants.
 *
 * @author xiweng.yy
 */
public class AiConstants {
    
    public static class Mcp {
        
        public static final String MCP_DEFAULT_NAMESPACE = "public";
        
        public static final String MCP_PROTOCOL_STDIO = "stdio";
        
        public static final String MCP_PROTOCOL_SSE = "mcp-sse";
        
        public static final String MCP_PROTOCOL_STREAMABLE = "mcp-streamable";
        
        public static final String MCP_PROTOCOL_HTTP = "http";
        
        public static final String MCP_PROTOCOL_DUBBO = "dubbo";
        
        public static final String MCP_ENDPOINT_TYPE_REF = "REF";
        
        public static final String MCP_ENDPOINT_TYPE_DIRECT = "DIRECT";
        
        public static final String MCP_FRONT_ENDPOINT_TYPE_TO_BACK = "BACKEND";
        
        public static final String MCP_STATUS_ACTIVE = McpServerStatusEnum.ACTIVE.getName();
        
        public static final String MCP_STATUS_DEPRECATED = McpServerStatusEnum.DEPRECATED.getName();

        public static final String MCP_STATUS_DELETED = McpServerStatusEnum.DELETED.getName();
        
        public static final String OFFICIAL_TRANSPORT_SSE = "sse";
        
        public static final String OFFICIAL_TRANSPORT_STREAMABLE = "streamable-http";
    }
    
    public static final String AI_REQUEST_TIMEOUT = "nacosAiRequestTimeout";
    
    public static final String AI_MCP_SERVER_CACHE_UPDATE_INTERVAL = "nacosAiMcpServerCacheUpdateInterval";
    
    public static final String AI_AGENT_CARD_CACHE_UPDATE_INTERVAL = "nacosAiAgentCardCacheUpdateInterval";
    
    public static final long DEFAULT_AI_CACHE_UPDATE_INTERVAL = 10000L;
    
    public static class A2a {
        
        public static final String A2A_DEFAULT_NAMESPACE = "public";
        
        /**
         * Default endpoint type using `url` field of agent card directly when discovery
         * a2a agent.
         */
        public static final String A2A_ENDPOINT_TYPE_URL = "URL";
        
        /**
         * Default endpoint type using `backend` service of agent when discovery a2a
         * agent.
         */
        public static final String A2A_ENDPOINT_TYPE_SERVICE = "SERVICE";
        
        public static final String A2A_ENDPOINT_DEFAULT_TRANSPORT = "JSONRPC";
        
        public static final String A2A_ENDPOINT_DEFAULT_PROTOCOL = "HTTP";
    }
}
