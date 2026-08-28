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

import com.alibaba.nacos.api.annotation.Since;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardVersionInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleDraftRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionCommand;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpLifecycleVersionSummary;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;

import java.util.List;
import java.util.Map;

/**
 * Nacos AI module maintainer service.
 *
 * @author xiweng.yy
 */
public interface AiMaintainerService extends McpMaintainerService, A2aMaintainerService {
    
    @Since("3.2.0")
    SkillMaintainerService skill();
    
    @Since("3.2.0")
    AgentSpecMaintainerService agentSpec();
    
    /**
     * Get the protocol-neutral Agent maintainer facade.
     *
     * @return Agent maintainer service
     */
    @Since("3.3.0")
    default AgentMaintainerService agent() {
        throw new UnsupportedOperationException(
            "Protocol-neutral Agent maintenance is not supported by this implementation");
    }
    
    @Since("3.2.0")
    McpMaintainerService mcp();
    
    @Since("3.2.0")
    A2aMaintainerService a2a();
    
    @Since("3.2.0")
    PromptMaintainerService prompt();
    
    @Since("3.2.0")
    PipelineMaintainerService pipeline();
    
    @Since("3.2.0")
    @Override
    default Page<McpServerBasicInfo> listMcpServer(String namespaceId, String mcpName, int pageNo,
        int pageSize)
        throws NacosException {
        return mcp().listMcpServer(namespaceId, mcpName, pageNo, pageSize);
    }
    
    @Since("3.2.0")
    @Override
    default Page<McpServerBasicInfo> searchMcpServer(String namespaceId, String mcpName, int pageNo,
        int pageSize)
        throws NacosException {
        return mcp().searchMcpServer(namespaceId, mcpName, pageNo, pageSize);
    }
    
    /**
     * {@inheritDoc}
     *
     * @deprecated Since 3.3.0, use {@link #getLifecycleVersion(String, String, String)}. Planned
     *     for removal in Nacos 4.0.0.
     */
    @Since("3.2.0")
    @Deprecated
    @Override
    default McpServerDetailInfo getMcpServerDetail(String namespaceId, String mcpName, String mcpId,
        String version)
        throws NacosException {
        return mcp().getMcpServerDetail(namespaceId, mcpName, mcpId, version);
    }
    
    /**
     * {@inheritDoc}
     *
     * @deprecated Since 3.3.0, use
     *     {@link #createLifecycleDraft(String, McpLifecycleDraftRequest)} and
     *     {@link #submitLifecycleVersion(String, McpLifecycleVersionCommand)}. If review is
     *     enabled, use {@link #publishLifecycleVersion(String, McpLifecycleVersionCommand)}.
     *     Planned for removal in Nacos 4.0.0.
     */
    @Since("3.2.0")
    @Deprecated
    @Override
    default String createMcpServer(String namespaceId, String mcpName,
        McpServerBasicInfo serverSpec,
        McpToolSpecification toolSpec, McpEndpointSpec endpointSpec) throws NacosException {
        return mcp().createMcpServer(namespaceId, mcpName, serverSpec, toolSpec, endpointSpec);
    }
    
    /**
     * {@inheritDoc}
     *
     * @deprecated Since 3.3.0, use
     *     {@link #createLifecycleDraft(String, McpLifecycleDraftRequest)} for a new Version or
     *     {@link #updateLifecycleDraft(String, McpLifecycleDraftRequest)} for an existing draft,
     *     then use {@link #submitLifecycleVersion(String, McpLifecycleVersionCommand)} and, when
     *     review is enabled,
     *     {@link #publishLifecycleVersion(String, McpLifecycleVersionCommand)}. Planned for
     *     removal in Nacos 4.0.0.
     */
    @Since("3.2.0")
    @Deprecated
    @Override
    default boolean updateMcpServer(String namespaceId, String mcpName, boolean isLatest,
        McpServerBasicInfo serverSpec, McpToolSpecification toolSpec, McpEndpointSpec endpointSpec,
        boolean overrideExisting) throws NacosException {
        return mcp().updateMcpServer(namespaceId, mcpName, isLatest, serverSpec, toolSpec,
            endpointSpec,
            overrideExisting);
    }
    
    @Since("3.2.0")
    @Override
    default boolean deleteMcpServer(String namespaceId, String mcpName, String mcpId,
        String version)
        throws NacosException {
        return mcp().deleteMcpServer(namespaceId, mcpName, mcpId, version);
    }
    
    @Since("3.3.0")
    @Override
    default Page<McpLifecycleVersionSummary> listLifecycleVersions(String namespaceId,
        String mcpName, String status, int pageNo, int pageSize) throws NacosException {
        return mcp().listLifecycleVersions(namespaceId, mcpName, status, pageNo, pageSize);
    }
    
    @Since("3.3.0")
    @Override
    default McpLifecycleVersionDetail getLifecycleVersion(String namespaceId, String mcpName,
        String version) throws NacosException {
        return mcp().getLifecycleVersion(namespaceId, mcpName, version);
    }
    
    @Since("3.3.0")
    @Override
    default McpLifecycleVersionDetail createLifecycleDraft(String namespaceId,
        McpLifecycleDraftRequest request) throws NacosException {
        return mcp().createLifecycleDraft(namespaceId, request);
    }
    
    @Since("3.3.0")
    @Override
    default McpLifecycleVersionDetail updateLifecycleDraft(String namespaceId,
        McpLifecycleDraftRequest request) throws NacosException {
        return mcp().updateLifecycleDraft(namespaceId, request);
    }
    
    @Since("3.3.0")
    @Override
    default void deleteLifecycleDraft(String namespaceId, McpLifecycleVersionCommand command)
        throws NacosException {
        mcp().deleteLifecycleDraft(namespaceId, command);
    }
    
    @Since("3.3.0")
    @Override
    default McpLifecycleVersionSummary submitLifecycleVersion(String namespaceId,
        McpLifecycleVersionCommand command) throws NacosException {
        return mcp().submitLifecycleVersion(namespaceId, command);
    }
    
    @Since("3.3.0")
    @Override
    default McpLifecycleVersionSummary publishLifecycleVersion(String namespaceId,
        McpLifecycleVersionCommand command) throws NacosException {
        return mcp().publishLifecycleVersion(namespaceId, command);
    }
    
    @Since("3.3.0")
    @Override
    default McpLifecycleVersionSummary forcePublishLifecycleVersion(String namespaceId,
        McpLifecycleVersionCommand command) throws NacosException {
        return mcp().forcePublishLifecycleVersion(namespaceId, command);
    }
    
    @Since("3.3.0")
    @Override
    default McpLifecycleVersionSummary redraftLifecycleVersion(String namespaceId,
        McpLifecycleVersionCommand command) throws NacosException {
        return mcp().redraftLifecycleVersion(namespaceId, command);
    }
    
    @Since("3.3.0")
    @Override
    default McpLifecycleVersionSummary onlineLifecycleVersion(String namespaceId,
        McpLifecycleVersionCommand command) throws NacosException {
        return mcp().onlineLifecycleVersion(namespaceId, command);
    }
    
    @Since("3.3.0")
    @Override
    default McpLifecycleVersionSummary offlineLifecycleVersion(String namespaceId,
        McpLifecycleVersionCommand command) throws NacosException {
        return mcp().offlineLifecycleVersion(namespaceId, command);
    }
    
    @Since("3.3.0")
    @Override
    default Map<String, String> updateLifecycleLabels(String namespaceId,
        McpLifecycleLabelsUpdateRequest request) throws NacosException {
        return mcp().updateLifecycleLabels(namespaceId, request);
    }
    
    @Since("3.2.0")
    @Override
    default boolean registerAgent(AgentCard agentCard, String namespaceId, String registrationType)
        throws NacosException {
        return a2a().registerAgent(agentCard, namespaceId, registrationType);
    }
    
    @Since("3.2.0")
    @Override
    default AgentCardDetailInfo getAgentCard(String agentName, String namespaceId,
        String registrationType,
        String version) throws NacosException {
        return a2a().getAgentCard(agentName, namespaceId, registrationType, version);
    }
    
    @Since("3.2.0")
    @Override
    default boolean updateAgentCard(AgentCard agentCard, String namespaceId, boolean setAsLatest,
        String registrationType) throws NacosException {
        return a2a().updateAgentCard(agentCard, namespaceId, setAsLatest, registrationType);
    }
    
    @Since("3.2.0")
    @Override
    default boolean deleteAgent(String agentName, String namespaceId, String version)
        throws NacosException {
        return a2a().deleteAgent(agentName, namespaceId, version);
    }
    
    @Since("3.2.0")
    @Override
    default List<AgentVersionDetail> listAllVersionOfAgent(String agentName, String namespaceId)
        throws NacosException {
        return a2a().listAllVersionOfAgent(agentName, namespaceId);
    }
    
    @Since("3.2.0")
    @Override
    default Page<AgentCardVersionInfo> searchAgentCardsByName(String namespaceId,
        String agentNamePattern,
        int pageNo, int pageSize) throws NacosException {
        return a2a().searchAgentCardsByName(namespaceId, agentNamePattern, pageNo, pageSize);
    }
    
    @Since("3.2.0")
    @Override
    default Page<AgentCardVersionInfo> listAgentCards(String namespaceId, String agentName,
        int pageNo, int pageSize)
        throws NacosException {
        return a2a().listAgentCards(namespaceId, agentName, pageNo, pageSize);
    }
    
}
