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

package com.alibaba.nacos.maintainer.client.ai;

import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

import java.util.List;

/**
 * Nacos AI module maintainer service.
 *
 * @author xiweng.yy
 */
public interface AiMaintainerService extends McpMaintainerService, A2aMaintainerService {
    
    SkillMaintainerService skill();
    
    AgentSpecMaintainerService agentSpec();
    
    McpMaintainerService mcp();
    
    A2aMaintainerService a2a();
    
    PromptMaintainerService prompt();
    
    PipelineMaintainerService pipeline();
    
    @Override
    default Page<McpServerBasicInfo> listMcpServer(String namespaceId, String mcpName, int pageNo,
        int pageSize)
        throws NacosException {
        return mcp().listMcpServer(namespaceId, mcpName, pageNo, pageSize);
    }
    
    @Override
    default Page<McpServerBasicInfo> searchMcpServer(String namespaceId, String mcpName, int pageNo,
        int pageSize)
        throws NacosException {
        return mcp().searchMcpServer(namespaceId, mcpName, pageNo, pageSize);
    }
    
    @Override
    default McpServerDetailInfo getMcpServerDetail(String namespaceId, String mcpName, String mcpId,
        String version)
        throws NacosException {
        return mcp().getMcpServerDetail(namespaceId, mcpName, mcpId, version);
    }
    
    @Override
    default String createMcpServer(String namespaceId, String mcpName,
        McpServerBasicInfo serverSpec,
        McpToolSpecification toolSpec, McpEndpointSpec endpointSpec) throws NacosException {
        return mcp().createMcpServer(namespaceId, mcpName, serverSpec, toolSpec, endpointSpec);
    }
    
    @Override
    default boolean updateMcpServer(String namespaceId, String mcpName, boolean isLatest,
        McpServerBasicInfo serverSpec, McpToolSpecification toolSpec, McpEndpointSpec endpointSpec,
        boolean overrideExisting) throws NacosException {
        return mcp().updateMcpServer(namespaceId, mcpName, isLatest, serverSpec, toolSpec,
            endpointSpec,
            overrideExisting);
    }
    
    @Override
    default boolean deleteMcpServer(String namespaceId, String mcpName, String mcpId,
        String version)
        throws NacosException {
        return mcp().deleteMcpServer(namespaceId, mcpName, mcpId, version);
    }
    
    @Override
    default boolean registerAgent(AgentCard agentCard, String namespaceId, String registrationType)
        throws NacosException {
        return a2a().registerAgent(agentCard, namespaceId, registrationType);
    }
    
    @Override
    default AgentCardDetailInfo getAgentCard(String agentName, String namespaceId,
        String registrationType,
        String version) throws NacosException {
        return a2a().getAgentCard(agentName, namespaceId, registrationType, version);
    }
    
    @Override
    default boolean updateAgentCard(AgentCard agentCard, String namespaceId, boolean setAsLatest,
        String registrationType) throws NacosException {
        return a2a().updateAgentCard(agentCard, namespaceId, setAsLatest, registrationType);
    }
    
    @Override
    default boolean deleteAgent(String agentName, String namespaceId, String version)
        throws NacosException {
        return a2a().deleteAgent(agentName, namespaceId, version);
    }
    
    @Override
    default List<AgentVersionDetail> listAllVersionOfAgent(String agentName, String namespaceId)
        throws NacosException {
        return a2a().listAllVersionOfAgent(agentName, namespaceId);
    }
    
    @Override
    default Page<AgentCardVersionInfo> searchAgentCardsByName(String namespaceId,
        String agentNamePattern,
        int pageNo, int pageSize) throws NacosException {
        return a2a().searchAgentCardsByName(namespaceId, agentNamePattern, pageNo, pageSize);
    }
    
    @Override
    default Page<AgentCardVersionInfo> listAgentCards(String namespaceId, String agentName,
        int pageNo, int pageSize)
        throws NacosException {
        return a2a().listAgentCards(namespaceId, agentName, pageNo, pageSize);
    }
    
}
