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

import com.alibaba.nacos.ai.service.mcp.McpLifecycleOperationService;
import com.alibaba.nacos.ai.service.mcp.McpCompatibilityMode;
import com.alibaba.nacos.ai.service.mcp.McpCompatibilityModeResolver;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.agent.storage.AgentVersionStorageService;
import com.alibaba.nacos.ai.service.agentspecs.AgentSpecStorageReader;
import com.alibaba.nacos.ai.service.prompt.PromptStorageReader;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Independent Hub/ordinary source grants and fixed-source rechecks around draft activation. */
class HubResourceTransferServiceTest {
    
    private final HubResourcePublicationService publications =
        mock(HubResourcePublicationService.class);
    private final HubResourceEligibilityValidator eligibility =
        mock(HubResourceEligibilityValidator.class);
    private final HubResourcePermissionGuard permissions = mock(HubResourcePermissionGuard.class);
    private final AiResourceManager manager = mock(AiResourceManager.class);
    private final NamespaceOperationService namespaces = mock(NamespaceOperationService.class);
    private final PromptStorageReader prompts = mock(PromptStorageReader.class);
    private final AgentSpecStorageReader agentSpecs = mock(AgentSpecStorageReader.class);
    private final AgentVersionStorageService agents = mock(AgentVersionStorageService.class);
    private final HubResourceTransferContents contents = mock(HubResourceTransferContents.class);
    private final HubResourceDraftTransferWriter writer =
        mock(HubResourceDraftTransferWriter.class);
    private final McpLifecycleOperationService mcp = mock(McpLifecycleOperationService.class);
    private final McpCompatibilityModeResolver mcpMode = mock(McpCompatibilityModeResolver.class);
    private final HubMcpTransferDependencyGuard mcpDependencies =
        mock(HubMcpTransferDependencyGuard.class);
    private final HubMcpTransferDependencyGuard.PreparedImport mcpPlan =
        mock(HubMcpTransferDependencyGuard.PreparedImport.class);
    private final HubResourceTransferService service = new HubResourceTransferService(publications,
        eligibility, permissions, manager, namespaces, prompts, agentSpecs, agents, contents,
        writer,
        mcp, mcpMode, mcpDependencies);
    private final HubResourceTransferService.Target target =
        new HubResourceTransferService.Target("target-ns", "target", "0.0.1");
    private final AiResource source = new AiResource();
    private final AiResourceVersion version = new AiResourceVersion();
    private final AiResource publication = new AiResource();
    private HubResourceReference reference;
    
    @BeforeEach
    void setUp() throws Exception {
        source.setId(101L);
        source.setNamespaceId("source-ns");
        source.setName("same-name");
        source.setType("prompt");
        source.setScope("PRIVATE");
        source.setStatus("enable");
        version.setId(201L);
        version.setNamespaceId("source-ns");
        version.setName("same-name");
        version.setType("prompt");
        version.setVersion("1.0.0");
        version.setStatus("online");
        version.setStorage("{\"provider\":\"nacos_config\"}");
        publication.setMetaVersion(3L);
        reference =
            HubResourceReferenceJsonUtil.build("source-ns", 101L, "same-name", 201L, "1.0.0");
        lenient().when(eligibility.requireSource(eq(HubResourceType.PROMPT), eq("source-ns"),
            eq("same-name"), any())).thenReturn(source);
        lenient().when(manager.findVersion("source-ns", "same-name", "prompt", "1.0.0"))
            .thenReturn(version);
        PromptVersionInfo body = new PromptVersionInfo();
        body.setTemplate("Hello {{name}}");
        lenient().when(prompts.read("source-ns", "same-name", "1.0.0", version.getStorage()))
            .thenReturn(body);
        lenient().when(contents.prompt("target-ns", "target", "0.0.1", body))
            .thenReturn(mock(HubResourceDraftTransferWriter.PreparedContent.class));
        lenient()
            .when(publications.inspectPinned(eq(HubResourceType.PROMPT), eq(501L), any(), any()))
            .thenAnswer(call -> {
                HubResourcePublicationService.PinnedPublicationReader<?> reader =
                    call.getArgument(3);
                return reader.read(publication, source, reference);
            });
        lenient().when(writer.create(eq(HubResourceType.PROMPT), eq("target-ns"), eq("target"),
            eq("0.0.1"), anyString(), any(), any(), any(), any())).thenAnswer(call -> {
                HubResourceDraftTransferWriter.RunnableWithNacosException check =
                    call.getArgument(8);
                check.run();
                check.run();
                return new HubResourceDraftTransferWriter.CreatedDraft(901L, "0.0.1");
            });
    }
    
    @Test
    void hubImportUsesPublicationGrantWithoutGrantingSourceRead() throws Exception {
        assertEquals(901L,
            service.importFromHub(HubResourceType.PROMPT, 501L, target).resourceId());
        verify(permissions, never()).requireSourceRead(any(), any());
        verify(publications).inspectPinned(eq(HubResourceType.PROMPT), eq(501L), isNull(), any());
        verify(publications, times(2)).inspectPinned(eq(HubResourceType.PROMPT), eq(501L),
            eq("1.0.0"), any());
    }
    
    @Test
    void adjustedPublicationBeforeActivationIsNotSilentlyRebound() throws Exception {
        doAnswer(call -> {
            publication.setMetaVersion(4L);
            return null;
        }).when(writer).requireAvailable(HubResourceType.PROMPT, "target-ns", "target", "0.0.1");
        when(contents.prompt(any(), any(), any(), any())).thenAnswer(call -> {
            publication.setMetaVersion(5L);
            return mock(HubResourceDraftTransferWriter.PreparedContent.class);
        });
        assertThrows(NacosException.class,
            () -> service.importFromHub(HubResourceType.PROMPT, 501L, target));
    }
    
    @Test
    void mcpHubImportCreatesAnIndependentDraftAndRechecksPublication() throws Exception {
        McpServerVersionDetail body = prepareMcpTransfer();
        assertEquals(901L, service.importFromHub(HubResourceType.MCP, 501L, target).resourceId());
        verify(contents).mcp(eq("target-ns"), eq("target"), eq("0.0.1"), anyString(),
            eq(body), isNull());
        verify(mcpDependencies).prepare(eq("source-ns"), eq("same-name"), eq("1.0.0"),
            eq(body.getServerSpecification()), eq("target-ns"), eq("target"), eq("0.0.1"),
            anyString(), isNull());
        verify(mcpPlan, times(2)).revalidate();
        verify(publications, times(2)).inspectPinned(eq(HubResourceType.MCP), eq(501L),
            eq("1.0.0"), any());
        verify(permissions, never()).requireSourceRead(any(), any());
        assertEquals("source-id", body.getServerSpecification().getId());
        assertEquals("same-name", source.getName());
    }
    
    @Test
    void mcpImportWritesTheConfigurationSelectedByItsDedicatedPlan() throws Exception {
        McpServerVersionDetail body = prepareMcpTransfer();
        McpServerRemoteServiceConfig original = new McpServerRemoteServiceConfig();
        com.alibaba.nacos.api.ai.model.mcp.McpServiceRef ref =
            new com.alibaba.nacos.api.ai.model.mcp.McpServiceRef();
        ref.setNamespaceId("source-ns");
        ref.setGroupName("mcp-endpoints");
        ref.setServiceName("same-name::1.0.0");
        original.setServiceRef(ref);
        body.getServerSpecification().setRemoteServerConfig(original);
        when(mcpPlan.configuration()).thenReturn(original);
        service.importFromHub(HubResourceType.MCP, 501L, target);
        verify(contents).mcp(eq("target-ns"), eq("target"), eq("0.0.1"), anyString(), eq(body),
            eq(original));
        assertEquals("source-ns", ref.getNamespaceId());
    }
    
    @Test
    void mcpTargetConflictIsDetectedBeforeReadingSourceContent() throws Exception {
        when(mcpMode.resolve()).thenReturn(McpCompatibilityMode.LIFECYCLE_MANAGED);
        when(mcpMode.localMemberSupportsManagedLifecycle()).thenReturn(true);
        doThrow(new NacosException(409, "target exists")).when(writer)
            .requireAvailable(HubResourceType.MCP, "target-ns", "target", "0.0.1");
        assertEquals(409, assertThrows(NacosException.class,
            () -> service.importFromHub(HubResourceType.MCP, 501L, target)).getErrCode());
        verifyNoInteractions(publications, mcp, contents);
    }
    
    @Test
    void mcpDependencyLossAtActivationFailsTheTransfer() throws Exception {
        prepareMcpTransfer();
        doThrow(new NacosException(409, "target dependency disappeared")).when(mcpPlan)
            .revalidate();
        assertEquals(409, assertThrows(NacosException.class,
            () -> service.importFromHub(HubResourceType.MCP, 501L, target)).getErrCode());
    }
    
    private McpServerVersionDetail prepareMcpTransfer() throws Exception {
        source.setType("mcp");
        source.setScope("PUBLIC");
        version.setType("mcp");
        when(mcpMode.resolve()).thenReturn(McpCompatibilityMode.LIFECYCLE_MANAGED);
        when(mcpMode.localMemberSupportsManagedLifecycle()).thenReturn(true);
        when(eligibility.requireSource(eq(HubResourceType.MCP), eq("source-ns"),
            eq("same-name"), any())).thenReturn(source);
        when(manager.findVersion("source-ns", "same-name", "mcp", "1.0.0")).thenReturn(version);
        McpServerBasicInfo server = new McpServerBasicInfo();
        server.setName("same-name");
        server.setId("source-id");
        server.setProtocol("stdio");
        McpServerVersionDetail body = new McpServerVersionDetail();
        body.setServerSpecification(server);
        when(mcp.projectSharedVersion(eq(source), any())).thenReturn(body);
        when(mcpDependencies.prepare(eq("source-ns"), eq("same-name"), eq("1.0.0"), eq(server),
            eq("target-ns"), eq("target"), eq("0.0.1"), anyString(), isNull())).thenReturn(mcpPlan);
        when(mcpPlan.attach(any())).thenAnswer(call -> call.getArgument(0));
        lenient().when(contents.mcp(eq("target-ns"), eq("target"), eq("0.0.1"), anyString(),
            eq(body), any()))
            .thenReturn(mock(HubResourceDraftTransferWriter.PreparedContent.class));
        lenient().when(publications.inspectPinned(eq(HubResourceType.MCP), eq(501L), any(), any()))
            .thenAnswer(call -> {
                HubResourcePublicationService.PinnedPublicationReader<?> reader =
                    call.getArgument(3);
                return reader.read(publication, source, reference);
            });
        lenient().when(writer.create(eq(HubResourceType.MCP), eq("target-ns"), eq("target"),
            eq("0.0.1"), anyString(), any(), any(), any(), any())).thenAnswer(call -> {
                HubResourceDraftTransferWriter.RunnableWithNacosException check =
                    call.getArgument(8);
                check.run();
                check.run();
                return new HubResourceDraftTransferWriter.CreatedDraft(901L, "0.0.1");
            });
        return body;
    }
    
}
