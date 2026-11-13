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

import com.alibaba.nacos.ai.model.mcp.McpResourceExt;
import com.alibaba.nacos.ai.service.mcp.McpLifecycleOperationService;
import com.alibaba.nacos.ai.service.mcp.McpCompatibilityMode;
import com.alibaba.nacos.ai.service.mcp.McpCompatibilityModeResolver;
import com.alibaba.nacos.ai.service.mcp.storage.McpResourceExtSerializer;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import java.util.UUID;
import com.alibaba.nacos.ai.config.HubConfig;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.agent.AgentResourceExt;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.agent.metadata.AgentResourceExtSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageDescriptorSerializer;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageService;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecStorageReader;
import com.alibaba.nacos.ai.service.prompt.PromptStorageReader;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.AgentSpecContentDigestUtils;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionInfo;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.utils.AgentValidationUtils;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Independent draft transfer orchestration; shared grants and direct-source grants stay separate. */
@Service
public class HubResourceTransferService {
    
    private final HubResourcePublicationService publications;
    private final HubResourceEligibilityValidator eligibility;
    private final HubResourcePermissionGuard permissions;
    private final AiResourceManager manager;
    private final NamespaceOperationService namespaces;
    private final PromptStorageReader prompts;
    private final AgentSpecStorageReader agentSpecs;
    private final AgentVersionStorageService agents;
    private final HubResourceTransferContents contents;
    private final HubResourceDraftTransferWriter writer;
    private final McpLifecycleOperationService mcp;
    private final McpCompatibilityModeResolver mcpMode;
    private final HubMcpTransferDependencyGuard mcpDependencies;
    
    public HubResourceTransferService(HubResourcePublicationService publications,
        HubResourceEligibilityValidator eligibility, HubResourcePermissionGuard permissions,
        AiResourceManager manager, NamespaceOperationService namespaces,
        PromptStorageReader prompts,
        AgentSpecStorageReader agentSpecs, AgentVersionStorageService agents,
        HubResourceTransferContents contents, HubResourceDraftTransferWriter writer,
        McpLifecycleOperationService mcp, McpCompatibilityModeResolver mcpMode,
        HubMcpTransferDependencyGuard mcpDependencies) {
        this.publications = publications;
        this.eligibility = eligibility;
        this.permissions = permissions;
        this.manager = manager;
        this.namespaces = namespaces;
        this.prompts = prompts;
        this.agentSpecs = agentSpecs;
        this.agents = agents;
        this.contents = contents;
        this.writer = writer;
        this.mcp = mcp;
        this.mcpMode = mcpMode;
        this.mcpDependencies = mcpDependencies;
    }
    
    /** Resolve a shared publication without granting source READ, then recheck its exact revision at commit. */
    public HubResourceDraftTransferWriter.CreatedDraft importFromHub(HubResourceType type,
        long entryId, Target target) throws NacosException {
        HubConfig.requireEnabled();
        requireTarget(type, target);
        PreparedSource prepared = publications.inspectPinned(type, entryId, null,
            (hub, source, reference) -> prepare(type, source, reference, entryId,
                hub.getMetaVersion(), target));
        return writer.create(type, target.namespaceId(), target.name(), target.version(),
            prepared.ext(), prepared.description(), prepared.bizTags(), prepared.content(), () -> {
                requireTargetWrite(type, target);
                recheckDependencies(type, target, prepared);
                publications.inspectPinned(type, entryId, prepared.reference().getSourceVersion(),
                    (hub, source, reference) -> {
                        if (!Objects.equals(hub.getMetaVersion(), prepared.publicationRevision())
                            || !Objects.equals(reference.getSourceResourceId(),
                                prepared.reference().getSourceResourceId())
                            || !Objects.equals(reference.getSourceVersionId(),
                                prepared.reference().getSourceVersionId())) {
                            throw changed();
                        }
                        requireSourceVersion(type, source, reference, prepared.storage());
                        return null;
                    });
            });
    }
    
    private PreparedSource prepare(HubResourceType type, AiResource source,
        HubResourceReference reference, Long entryId, Long revision, Target target)
        throws NacosException {
        AiResourceVersion version = requireSourceVersion(type, source, reference, null);
        String storage = version.getStorage();
        Map<String, Object> origin = origin(reference, entryId);
        String ext = JacksonUtils.toJson(Collections.singletonMap("origin", origin));
        HubResourceDraftTransferWriter.PreparedContent content;
        HubMcpTransferDependencyGuard.PreparedImport dependencies = null;
        switch (type) {
            case PROMPT:
                content = contents.prompt(target.namespaceId(), target.name(), target.version(),
                    prompts.read(reference.getSourceNamespaceId(), reference.getSourceName(),
                        reference.getSourceVersion(), storage));
                break;
            case AGENTSPEC:
                AgentSpec metadata = agentSpecs.readMeta(reference.getSourceNamespaceId(),
                    reference.getSourceName(), reference.getSourceVersion(), storage);
                AgentSpec body =
                    agentSpecs.read(reference.getSourceNamespaceId(), reference.getSourceName(),
                        reference.getSourceVersion(), storage);
                if (metadata.getResource() != null && (body.getResource() == null
                    || !metadata.getResource().keySet().equals(body.getResource().keySet()))) {
                    throw changed();
                }
                Map<?, ?> descriptor = JacksonUtils.toObj(storage, Map.class);
                Object digest = descriptor == null ? null : descriptor.get("contentMd5");
                if (digest != null && !Objects.equals(digest,
                    AgentSpecContentDigestUtils.computeContentMd5(body))) {
                    throw changed();
                }
                content =
                    contents.agentSpec(target.namespaceId(), target.name(), target.version(), body);
                break;
            case AGENT:
                AgentResourceExt agentExt = AgentResourceExtSerializer.deserialize(source.getExt());
                agentExt.setOrigin(origin);
                AgentVersionInfo catalog = new AgentVersionInfo();
                catalog.setOnlineVersions(Collections.emptyList());
                agentExt.setVersionCatalog(catalog);
                ext = AgentResourceExtSerializer.serialize(agentExt);
                content = contents.agent(target.namespaceId(), target.name(), target.version(),
                    agents.load(AgentVersionStorageDescriptorSerializer.deserialize(storage)));
                break;
            case MCP:
                McpServerVersionDetail bodyMcp = mcp.projectSharedVersion(source, reference);
                String mcpId = UUID.randomUUID().toString();
                dependencies = mcpDependencies.prepare(reference.getSourceNamespaceId(),
                    reference.getSourceName(), reference.getSourceVersion(),
                    bodyMcp.getServerSpecification(), target.namespaceId(), target.name(),
                    target.version(), mcpId, target.mcpRemoteServerConfig());
                McpResourceExt mcpExt = new McpResourceExt();
                mcpExt.setSchemaVersion(McpResourceExt.SCHEMA_VERSION);
                mcpExt.setMcpId(mcpId);
                mcpExt.setOrigin(origin);
                ext = McpResourceExtSerializer.serialize(mcpExt);
                content = dependencies.attach(contents.mcp(target.namespaceId(), target.name(),
                    target.version(), mcpId, bodyMcp, dependencies.configuration()));
                break;
            default:
                throw invalid(
                    "Resource-specific transfer preparation is unavailable for this type");
        }
        AiResource current = eligibility.requireSource(type, reference.getSourceNamespaceId(),
            reference.getSourceName(), reference.getSourceResourceId());
        requireSourceVersion(type, current, reference, storage);
        return new PreparedSource(reference, revision, storage, ext, source.getDesc(),
            source.getBizTags(), content, dependencies);
    }
    
    private AiResourceVersion requireSourceVersion(HubResourceType type, AiResource source,
        HubResourceReference reference, String storage) throws NacosException {
        if (!Objects.equals(source.getId(), reference.getSourceResourceId())
            || !"enable".equals(source.getStatus())) {
            throw changed();
        }
        AiResourceVersion row = manager.findVersion(reference.getSourceNamespaceId(),
            reference.getSourceName(), type.resourceType(), reference.getSourceVersion());
        if (row == null || !Objects.equals(row.getId(), reference.getSourceVersionId())
            || !reference.getSourceNamespaceId().equals(row.getNamespaceId())
            || !reference.getSourceName().equals(row.getName())
            || !type.resourceType().equals(row.getType())
            || !reference.getSourceVersion().equals(row.getVersion())
            || !"online".equals(row.getStatus())
            || (storage != null && !Objects.equals(row.getStorage(), storage))) {
            throw changed();
        }
        return row;
    }
    
    private void requireTarget(HubResourceType type, Target target) throws NacosException {
        if (type == null || type == HubResourceType.SKILL || target == null) {
            throw invalid("A supported resource type and explicit target are required");
        }
        if (target.namespaceId() == null || target.namespaceId().isBlank()
            || target.namespaceId().length() > 128 || target.name() == null
            || target.name().isBlank()
            || target.name().length() > 256 || target.version() == null
            || target.version().isBlank()
            || target.version().length() > 64) {
            throw invalid("Invalid target draft identity");
        }
        if (type == HubResourceType.AGENT) {
            AgentValidationUtils.validateNamespaceId(target.namespaceId());
            AgentValidationUtils.validateAgentName(target.name());
            AgentValidationUtils.validateVersion(target.version());
        } else if (type != HubResourceType.MCP
            && !VersionUtils.isSupportedVersionFormat(target.version())) {
            throw invalid("Invalid target version, expected x.y.z or vN");
        }
        if (type != HubResourceType.MCP && target.mcpRemoteServerConfig() != null) {
            throw invalid("MCP dependencies are only valid for MCP transfers");
        }
        requireTargetWrite(type, target);
        if (type == HubResourceType.MCP) {
            requireMcpReady();
        }
        writer.requireAvailable(type, target.namespaceId(), target.name(), target.version());
    }
    
    private void requireMcpReady() throws NacosException {
        if (mcpMode.resolve() != McpCompatibilityMode.LIFECYCLE_MANAGED
            || !mcpMode.localMemberSupportsManagedLifecycle()) {
            throw new NacosApiException(409, ErrorCode.RESOURCE_CONFLICT,
                "MCP transfers require LIFECYCLE_MANAGED on a capable member");
        }
    }
    
    private void recheckDependencies(HubResourceType type, Target target, PreparedSource prepared)
        throws NacosException {
        if (type == HubResourceType.MCP) {
            requireMcpReady();
            prepared.mcpDependencies().revalidate();
        }
    }
    
    private void requireTargetWrite(HubResourceType type, Target target) throws NacosException {
        permissions.requireTargetWrite(type, target.namespaceId(), target.name());
        namespaces.getNamespace(target.namespaceId());
    }
    
    private Map<String, Object> origin(HubResourceReference reference, Long entryId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("sourceNamespaceId", reference.getSourceNamespaceId());
        result.put("sourceResourceId", reference.getSourceResourceId());
        result.put("sourceName", reference.getSourceName());
        result.put("sourceVersionId", reference.getSourceVersionId());
        result.put("sourceVersion", reference.getSourceVersion());
        result.put("hubEntryId", entryId);
        result.put("importedAt", Instant.now().toString());
        return result;
    }
    
    private NacosApiException changed() {
        return new NacosApiException(409, ErrorCode.RESOURCE_CONFLICT,
            "Transfer source or shared publication changed or is unavailable");
    }
    
    private NacosApiException invalid(String message) {
        return new NacosApiException(400, ErrorCode.PARAMETER_VALIDATE_ERROR, message);
    }
    
    /** Server-internal target identity; transport adapters supply the chosen initial draft version. */
    public record Target(String namespaceId, String name, String version,
        McpServerRemoteServiceConfig mcpRemoteServerConfig) {
        
        public Target(String namespaceId, String name, String version) {
            this(namespaceId, name, version, null);
        }
        
        public Target {
            mcpRemoteServerConfig = copyDependencies(mcpRemoteServerConfig);
        }
        
        @Override
        public McpServerRemoteServiceConfig mcpRemoteServerConfig() {
            return copyDependencies(mcpRemoteServerConfig);
        }
        
        private static McpServerRemoteServiceConfig copyDependencies(
            McpServerRemoteServiceConfig config) {
            return config == null ? null : JacksonUtils.toObj(JacksonUtils.toJson(config),
                McpServerRemoteServiceConfig.class);
        }
    }
    
    private record PreparedSource(HubResourceReference reference, Long publicationRevision,
        String storage, String ext, String description, String bizTags,
        HubResourceDraftTransferWriter.PreparedContent content,
        HubMcpTransferDependencyGuard.PreparedImport mcpDependencies) {
    }
}
