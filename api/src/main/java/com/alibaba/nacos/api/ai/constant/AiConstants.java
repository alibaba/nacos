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
    }
}
