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

package com.alibaba.nacos.client.ai.index;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.ai.remote.AiGrpcClient;
import com.alibaba.nacos.common.lifecycle.Closeable;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nacos AI module MCP server name and id Index caches.
 *
 * @author xiweng.yy
 */
public class McpNameIndexCache implements Closeable {

    private final Map<String, String> nameToIdMap;
    
    private final AiGrpcClient grpcClient;
    
    public McpNameIndexCache(AiGrpcClient grpcClient) {
        this.grpcClient = grpcClient;
        this.nameToIdMap = new ConcurrentHashMap<>(32);
    }
    
    /**
     * Index from MCP server name to MCP ID. If cached found, use cached id, otherwise query from server and cache it.
     *
     * @param mcpName name of mcp server
     * @return id of mcp server
     * @throws NacosException if request parameter is invalid or not found or handle error
     */
    public String indexMcpNameToMcpId(String mcpName) throws NacosException {
        String mcpId = nameToIdMap.get(mcpName);
        if (StringUtils.isNotEmpty(mcpId)) {
            return mcpId;
        }
        mcpId = grpcClient.indexMcpNameToMcpId(mcpName);
        nameToIdMap.put(mcpName, mcpId);
        return mcpId;
    }
    
    @Override
    public void shutdown() throws NacosException {
        nameToIdMap.clear();
    }
}
