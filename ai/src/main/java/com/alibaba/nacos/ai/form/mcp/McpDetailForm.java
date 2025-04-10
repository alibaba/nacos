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

package com.alibaba.nacos.ai.form.mcp;

import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;

import java.io.Serial;
import java.util.List;
import java.util.Objects;

/**
 * Nacos AI Mcp Server request detail form, used in create or update.
 *
 * @author xiweng.yy
 */
public class McpDetailForm extends McpForm {
    
    @Serial
    private static final long serialVersionUID = 8016131725604983670L;
    
    private McpServerBasicInfo serverSpecification;
    
    private List<McpTool> toolSpecification;
    
    @Override
    public void validate() throws NacosApiException {
        super.validate();
        if (Objects.isNull(serverSpecification)) {
            throw new NacosApiException(NacosException.INVALID_PARAM, ErrorCode.PARAMETER_MISSING,
                    "Required parameter 'serverSpecification' type McpServerBasicInfo is not present");
        }
    }
    
    public McpServerBasicInfo getServerSpecification() {
        return serverSpecification;
    }
    
    public void setServerSpecification(McpServerBasicInfo serverSpecification) {
        this.serverSpecification = serverSpecification;
    }
    
    public List<McpTool> getToolSpecification() {
        return toolSpecification;
    }
    
    public void setToolSpecification(List<McpTool> toolSpecification) {
        this.toolSpecification = toolSpecification;
    }
}
