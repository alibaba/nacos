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

package com.alibaba.nacos.client.ai.utils;

import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Nacos AI module cache key utils.
 *
 * @author xiweng.yy
 */
public class CacheKeyUtils {
    
    public static final String LATEST_VERSION = "latest";
    
    /**
     * Build mcp server versioned key.
     *
     * @param mcpName name of mcp server
     * @param version version of mcp server, if version is blank or null, use latest version
     * @return mcp server versioned key, pattern ${mcpName}::${version}
     */
    public static String buildMcpServerKey(String mcpName, String version) {
        return buildVersionedKey(mcpName, version);
    }
    
    /**
     * Build AgentCard versioned key.
     *
     * @param agentName name of agent name
     * @param version version of agent name, if version is blank or null, use latest version
     * @return mcp server versioned key, pattern ${mcpName}::${version}
     */
    public static String buildAgentCardKey(String agentName, String version) {
        return buildVersionedKey(agentName, version);
    }
    
    private static String buildVersionedKey(String name, String version) {
        if (StringUtils.isBlank(version)) {
            version = LATEST_VERSION;
        }
        return name + "::" + version;
    }
}
