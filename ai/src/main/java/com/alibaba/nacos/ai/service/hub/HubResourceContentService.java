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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.agent.AgentVersionContent;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageService;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecStorageReader;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageContents;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.mcp.storage.McpVersionStorageService;
import com.alibaba.nacos.ai.service.prompt.PromptStorageReader;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Resource-specific canonical content loaders inside the Hub publication fences. These internal
 * results are not HTTP response contracts. Runtime endpoints and registrations are never copied.
 */
@Service
public class HubResourceContentService {
    
    private final HubResourcePublicationService publications;
    
    private final AiResourceManager manager;
    
    private final PromptStorageReader prompts;
    
    private final AgentSpecStorageReader agentSpecs;
    
    private final AgentVersionStorageService agents;
    
    private final McpVersionStorageService mcp;
    
    public HubResourceContentService(HubResourcePublicationService publications,
        AiResourceManager manager, PromptStorageReader prompts, AgentSpecStorageReader agentSpecs,
        AgentVersionStorageService agents, McpVersionStorageService mcp) {
        this.publications = publications;
        this.manager = manager;
        this.prompts = prompts;
        this.agentSpecs = agentSpecs;
        this.agents = agents;
        this.mcp = mcp;
    }
    
    /** Read a template and variables, preserving the existing Prompt content and MD5 model. */
    public PromptVersionInfo readPrompt(long entryId, String version) throws NacosException {
        return publications.readPinned(HubResourceType.PROMPT, entryId, version,
            (source, reference) -> {
                AiResourceVersion row = sourceVersion(HubResourceType.PROMPT, reference);
                PromptVersionInfo result = prompts.read(reference.getSourceNamespaceId(),
                    reference.getSourceName(), reference.getSourceVersion(), row.getStorage());
                requireUnchangedStorage(HubResourceType.PROMPT, reference, row.getStorage());
                return result;
            });
    }
    
    /** Read the complete AgentSpec manifest and typed resource package through its saved provider. */
    public AgentSpec readAgentSpec(long entryId, String version) throws NacosException {
        return publications.readPinned(HubResourceType.AGENTSPEC, entryId, version,
            (source, reference) -> {
                AiResourceVersion row = sourceVersion(HubResourceType.AGENTSPEC, reference);
                AgentSpec result = agentSpecs.read(reference.getSourceNamespaceId(),
                    reference.getSourceName(), reference.getSourceVersion(), row.getStorage());
                requireUnchangedStorage(HubResourceType.AGENTSPEC, reference, row.getStorage());
                return result;
            });
    }
    
    /** Read declared Agent call interfaces; runtime Naming endpoints are outside version content. */
    public AgentVersionContent readAgent(long entryId, String version) throws NacosException {
        return publications.readPinned(HubResourceType.AGENT, entryId, version,
            (source, reference) -> {
                AiResourceVersion row = sourceVersion(HubResourceType.AGENT, reference);
                AgentVersionContent result = agents.load(
                    AgentVersionStorageDescriptorSerializer.deserialize(row.getStorage()));
                requireUnchangedStorage(HubResourceType.AGENT, reference, row.getStorage());
                return result;
            });
    }
    
    /** Read canonical MCP Server/Tools/Resources bytes without writing Manifest or Naming state. */
    public McpVersionStorageContents readMcp(long entryId, String version) throws NacosException {
        return publications.readPinned(HubResourceType.MCP, entryId, version,
            (source, reference) -> {
                AiResourceVersion row = sourceVersion(HubResourceType.MCP, reference);
                McpVersionStorageContents result = mcp.load(
                    McpVersionStorageDescriptorSerializer.deserialize(row.getStorage()));
                requireUnchangedStorage(HubResourceType.MCP, reference, row.getStorage());
                return result;
            });
    }
    
    private AiResourceVersion sourceVersion(HubResourceType type, HubResourceReference reference)
        throws NacosException {
        AiResourceVersion row = manager.findVersion(reference.getSourceNamespaceId(),
            reference.getSourceName(), type.resourceType(), reference.getSourceVersion());
        if (row == null || !Objects.equals(row.getId(), reference.getSourceVersionId())
            || !Objects.equals(row.getNamespaceId(), reference.getSourceNamespaceId())
            || !Objects.equals(row.getName(), reference.getSourceName())
            || !Objects.equals(row.getVersion(), reference.getSourceVersion())
            || !type.resourceType().equals(row.getType())
            || !AiResourceConstants.VERSION_STATUS_ONLINE.equals(row.getStatus())) {
            throw unavailable();
        }
        return row;
    }
    
    private void requireUnchangedStorage(HubResourceType type, HubResourceReference reference,
        String expectedStorage) throws NacosException {
        if (!Objects.equals(expectedStorage, sourceVersion(type, reference).getStorage())) {
            throw unavailable();
        }
    }
    
    private NacosApiException unavailable() {
        return new NacosApiException(NacosException.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
            "Shared source content is unavailable");
    }
}
