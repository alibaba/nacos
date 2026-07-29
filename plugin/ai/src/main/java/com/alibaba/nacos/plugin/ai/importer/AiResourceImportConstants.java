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

package com.alibaba.nacos.plugin.ai.importer;

/**
 * Common constants for AI resource import plugins.
 *
 * @author xiweng.yy
 * @since 3.2.1
 */
public final class AiResourceImportConstants {
    
    public static final String RESOURCE_TYPE_MCP = "mcp";
    
    public static final String RESOURCE_TYPE_SKILL = "skill";
    
    public static final String CONFIG_ENDPOINT = "endpoint";
    
    public static final String CONFIG_ALLOW_HTTP = "allow-http";
    
    public static final String CONFIG_ALLOW_PRIVATE_NETWORK = "allow-private-network";
    
    public static final String CONFIG_DISPLAY_NAME = "display-name";
    
    public static final String CONFIG_DESCRIPTION = "description";
    
    public static final String CONFIG_MAX_ITEM_COUNT = "max-item-count";
    
    public static final String CONFIG_MAX_ARTIFACT_SIZE = "max-artifact-size";
    
    public static final int DEFAULT_MAX_ITEM_COUNT = 500;
    
    public static final long DEFAULT_MAX_ARTIFACT_SIZE = 10L * 1024L * 1024L;
    
    private AiResourceImportConstants() {
    }
}
