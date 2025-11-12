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

package com.alibaba.nacos.ai.constant;

/**
 * Nacos AI Server Constants.
 *
 * @author xiweng.yy
 */
public class Constants {
    
    public static final String MCP_PATH = "/ai/mcp";
    
    public static final String MCP_ADMIN_PATH = "/v3/admin" + MCP_PATH;
    
    public static final String MCP_CONSOLE_PATH = "/v3/console" + MCP_PATH;
    
    public static final String MCP_LIST_SEARCH_ACCURATE = "accurate";
    
    public static final String MCP_LIST_SEARCH_BLUR = "blur";
    
    public static final String ALL_PATTERN = com.alibaba.nacos.api.common.Constants.ALL_PATTERN;
    
    public static final String MCP_SERVER_VERSIONS_GROUP = "mcp-server-versions";
    
    public static final String MCP_SERVER_GROUP = "mcp-server";
    
    public static final String MCP_SERVER_TOOL_GROUP = "mcp-tools";
    
    public static final String MCP_SERVER_SPEC_DATA_ID_SUFFIX = "-mcp-server.json";
    
    public static final String MCP_SERVER_VERSION_DATA_ID_SUFFIX = "-mcp-versions.json";
    
    public static final String MCP_SERVER_TOOL_DATA_ID_SUFFIX = "-mcp-tools.json";
    
    public static final String MCP_SERVER_ENDPOINT_GROUP = "mcp-endpoints";
    
    public static final String MCP_SERVER_ENDPOINT_CLUSTER = com.alibaba.nacos.api.common.Constants.DEFAULT_CLUSTER_NAME;
    
    public static final String MCP_BACKEND_INSTANCE_PROTOCOL_KEY = "transportProtocol";

    public static final String MCP_SERVER_ENDPOINT_ADDRESS = "address";
    
    public static final String MCP_SERVER_ENDPOINT_PORT = "port";
    
    public static final String MCP_SERVER_ENDPOINT_METADATA_MARK = "__nacos.ai.mcp.service__";
    
    public static final String MCP_SERVER_CONFIG_MARK = "nacos.internal.config=mcp";
    
    public static final String PROTOCOL_TYPE_HTTP = "http";
    
    public static final String PROTOCOL_TYPE_HTTPS = "https";
    
    public static final String SERVER_EXPORT_PATH_KEY = "exportPath";
    
    public static final String MCP_SERVER_NAME_TAG_KEY_PREFIX = "mcpServerName=";
    
    public static final int MAX_LIST_SIZE = 100;
    
    public static final String RELEASE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    
    public static final String CONFIG_TAGS_NAME = "config_tags";
    
    public static final String META_PATH = "path";
    
    public static final String SERVER_VERSION_CONFIG_DATA_ID_TEMPLATE = "%s" + MCP_SERVER_VERSION_DATA_ID_SUFFIX;
    
    public static final String SERVER_SPECIFICATION_CONFIG_DATA_ID_TEMPLATE = "%s-%s" + MCP_SERVER_SPEC_DATA_ID_SUFFIX;
    
    public static final String SERVER_TOOLS_SPEC_CONFIG_DATA_ID_TEMPLATE = "%s-%s" + MCP_SERVER_TOOL_DATA_ID_SUFFIX;
    
    public static class A2A {
        
        public static final String CONSOLE_PATH = "/v3/console/ai/a2a";
        
        public static final String ADMIN_PATH = "/v3/admin/ai/a2a";
        
        public static final String AGENT_GROUP = "agent";
        
        public static final String AGENT_VERSION_GROUP = "agent-version";
        
        public static final String SEARCH_BLUR = "blur";
        
        public static final String SEARCH_ACCURATE = "accurate";
        
        public static final String AGENT_ENDPOINT_GROUP = "agent-endpoints";
        
        public static final String AGENT_ENDPOINT_PATH_KEY = "__nacos.agent.endpoint.path__";
        
        public static final String AGENT_ENDPOINT_TRANSPORT_KEY = "__nacos.agent.endpoint.transport__";
        
        public static final String NACOS_AGENT_ENDPOINT_SUPPORT_TLS = "__nacos.agent.endpoint.supportTls__";
    }
}
