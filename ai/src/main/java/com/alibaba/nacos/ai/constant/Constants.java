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
    
    public static final String MCP_SERVER_GROUP = "mcp_server";
    
    public static final String MCP_SERVER_TOOL_GROUP = "mcp_server_tool";
    
    public static final String MCP_SERVER_SPEC_DATA_ID_SUFFIX = "_mcp_server.json";
    
    public static final String MCP_SERVER_TOOL_DATA_ID_SUFFIX = "_mcp_server_tool.json";
    
}
