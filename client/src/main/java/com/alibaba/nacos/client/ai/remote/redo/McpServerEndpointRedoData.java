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

package com.alibaba.nacos.client.ai.remote.redo;

import com.alibaba.nacos.client.redo.data.RedoData;

import java.util.Objects;

/**
 * Nacos AI module mcp server endpoint redo data.
 *
 * @author xiweng.yy
 */
public class McpServerEndpointRedoData extends RedoData<McpServerEndpoint> {
    
    private final String mcpName;
    
    public McpServerEndpointRedoData(String mcpName) {
        this.mcpName = mcpName;
    }
    
    public String getMcpName() {
        return mcpName;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        McpServerEndpointRedoData that = (McpServerEndpointRedoData) o;
        return Objects.equals(mcpName, that.mcpName) && super.equals(o);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mcpName);
    }
}
