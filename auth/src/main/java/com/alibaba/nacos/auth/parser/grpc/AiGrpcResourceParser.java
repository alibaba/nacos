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

package com.alibaba.nacos.auth.parser.grpc;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.remote.request.AbstractAgentRequest;
import com.alibaba.nacos.api.ai.remote.request.AbstractMcpRequest;
import com.alibaba.nacos.api.ai.remote.request.AbstractPromptRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseAgentCardRequest;
import com.alibaba.nacos.api.ai.remote.request.ReleaseMcpServerRequest;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Properties;

import static com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE_AGENT;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE_MCP;
import static com.alibaba.nacos.plugin.auth.constant.Constants.Resource.AI_TYPE_PROMPT;

/**
 * AI Grpc resource parser.
 *
 * @author hongye.nhy xiweng.yy
 */
public class AiGrpcResourceParser extends AbstractGrpcResourceParser {
    
    @Override
    protected String getNamespaceId(Request request) {
        String namespaceId = null;
        if (request instanceof AbstractMcpRequest) {
            namespaceId = ((AbstractMcpRequest) request).getNamespaceId();
        } else if (request instanceof AbstractAgentRequest) {
            namespaceId = ((AbstractAgentRequest) request).getNamespaceId();
        } else if (request instanceof AbstractPromptRequest) {
            namespaceId = ((AbstractPromptRequest) request).getNamespaceId();
        }
        if (StringUtils.isBlank(namespaceId)) {
            namespaceId = AiConstants.Mcp.MCP_DEFAULT_NAMESPACE;
        }
        return namespaceId;
    }
    
    @Override
    protected String getGroup(Request request) {
        return Constants.DEFAULT_GROUP;
    }
    
    @Override
    protected String getResourceName(Request request) {
        if (request instanceof AbstractMcpRequest) {
            return getMcpName((AbstractMcpRequest) request);
        } else if (request instanceof AbstractAgentRequest) {
            return getAgentName((AbstractAgentRequest) request);
        } else if (request instanceof AbstractPromptRequest) {
            return getPromptName((AbstractPromptRequest) request);
        }
        return StringUtils.EMPTY;
    }
    
    private String getMcpName(AbstractMcpRequest request) {
        String mcpName = request.getMcpName();
        if (request instanceof ReleaseMcpServerRequest) {
            ReleaseMcpServerRequest releaseMcpServerRequest = (ReleaseMcpServerRequest) request;
            if (null != releaseMcpServerRequest.getServerSpecification()) {
                mcpName = releaseMcpServerRequest.getServerSpecification().getName();
            }
        }
        return StringUtils.isBlank(mcpName) ? StringUtils.EMPTY : mcpName;
    }
    
    private String getAgentName(AbstractAgentRequest request) {
        String agentName = request.getAgentName();
        if (request instanceof ReleaseAgentCardRequest) {
            ReleaseAgentCardRequest releaseAgentCardRequest = (ReleaseAgentCardRequest) request;
            if (null != releaseAgentCardRequest.getAgentCard()) {
                agentName = releaseAgentCardRequest.getAgentCard().getName();
            }
        }
        return StringUtils.isBlank(agentName) ? StringUtils.EMPTY : agentName;
    }
    
    private String getPromptName(AbstractPromptRequest request) {
        String promptKey = request.getPromptKey();
        return StringUtils.isBlank(promptKey) ? StringUtils.EMPTY : promptKey;
    }
    
    @Override
    protected Properties getProperties(Request request) {
        Properties properties = super.getProperties(request);
        if (request instanceof AbstractMcpRequest) {
            properties.setProperty(AI_TYPE, AI_TYPE_MCP);
        } else if (request instanceof AbstractAgentRequest) {
            properties.setProperty(AI_TYPE, AI_TYPE_AGENT);
        } else if (request instanceof AbstractPromptRequest) {
            properties.setProperty(AI_TYPE, AI_TYPE_PROMPT);
        }
        return properties;
    }
}
