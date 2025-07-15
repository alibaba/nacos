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

package com.alibaba.nacos.api.ai.remote.request;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.remote.request.Request;

/**
 * Nacos AI module mcp request.
 *
 * @author xiweng.yy
 */
public abstract class AbstractMcpRequest extends Request {
    
    private String namespaceId;
    
    private String mcpId;
    
    private String mcpName;
    
    @Override
    public String getModule() {
        return Constants.AI.AI_MODULE;
    }
    
    public String getNamespaceId() {
        return namespaceId;
    }
    
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }
    
    public String getMcpId() {
        return mcpId;
    }
    
    public void setMcpId(String mcpId) {
        this.mcpId = mcpId;
    }
    
    public String getMcpName() {
        return mcpName;
    }
    
    public void setMcpName(String mcpName) {
        this.mcpName = mcpName;
    }
}
