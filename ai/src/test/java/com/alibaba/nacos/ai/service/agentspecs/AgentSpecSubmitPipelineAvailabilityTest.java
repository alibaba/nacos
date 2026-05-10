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

package com.alibaba.nacos.ai.service.agentspecs;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property 5: Submit behavior consistency with Pipeline availability.
 *
 * <p>When Pipeline is not enabled or has no matching nodes, submit directly publishes
 * and version status becomes online; when Pipeline is enabled and has matching nodes,
 * version status becomes reviewing, editingVersion is cleared, reviewingVersion is set.</p>
 *
 * <p><b>Validates: Requirements 4.3, 4.4</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecSubmitPipelineAvailabilityTest {
    
    private static final String RESOURCE_TYPE_AGENTSPEC = "agentspec";
    
    private static java.util.List<SubmitInput> sampleSubmitInputs() {
        return java.util.Arrays.asList(
            new SubmitInput("public", "a", "v1"),
            new SubmitInput("ns", "b", "ver"));
    }
    
    /**
     * When Pipeline is not enabled or has no matching nodes (execute returns null),
     * submit directly publishes and version status becomes online.
     *
     * <p><b>Validates: Requirements 4.3</b></p>
     */
    @Test
    void submitDirectlyPublishesWhenPipelineDisabled() throws Exception {
        for (SubmitInput input : sampleSubmitInputs()) {
            EnvUtil.setEnvironment(new StandardEnvironment());
            
            // Mock dependencies
            final AiResourcePersistService aiResourcePersistService =
                mock(AiResourcePersistService.class);
            final AiResourceVersionPersistService aiResourceVersionPersistService =
                mock(AiResourceVersionPersistService.class);
            final PublishPipelineExecutor publishPipelineExecutor =
                mock(PublishPipelineExecutor.class);
            final PipelineExecutionRepository pipelineExecutionRepository =
                mock(PipelineExecutionRepository.class);
            
            // Build meta with editingVersion
            AiResource meta = buildMeta(input.namespaceId, input.name, input.version);
            when(aiResourcePersistService.find(eq(input.namespaceId), eq(input.name),
                eq(RESOURCE_TYPE_AGENTSPEC)))
                .thenReturn(meta);
            
            // Build version row as draft for submit's find(), then as reviewing for publish's find()
            AiResourceVersion draftVersion =
                buildVersionRow(input.namespaceId, input.name, input.version, "draft");
            AiResourceVersion reviewingVersion =
                buildVersionRow(input.namespaceId, input.name, input.version,
                    "reviewing");
            // submit() calls find() once, then publish() calls find() again
            when(aiResourceVersionPersistService.find(
                eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC),
                eq(input.version)))
                .thenReturn(draftVersion)
                .thenReturn(reviewingVersion);
            
            // isPipelineAvailable returns false -> pipeline disabled / no matching nodes
            when(
                publishPipelineExecutor.isPipelineAvailable(any(PublishPipelineResourceType.class)))
                .thenReturn(false);
            
            // Mock updateMetaCas to return true
            when(aiResourcePersistService.updateMetaCas(
                eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC), any(Long.class),
                any()))
                .thenReturn(true);
            
            // Construct service and call submit
            AgentSpecOperationServiceImpl service = new AgentSpecOperationServiceImpl(
                aiResourcePersistService, aiResourceVersionPersistService,
                publishPipelineExecutor,
                new AiResourceManager(aiResourcePersistService, aiResourceVersionPersistService,
                    pipelineExecutionRepository));
            String result = service.submit(input.namespaceId, input.name, input.version);
            
            assertEquals(input.version, result, "submit should return the version");
            
            // Verify: updateStatus was called with "online" (from publish path)
            ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
            verify(aiResourceVersionPersistService).updateStatus(
                eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC),
                eq(input.version), statusCaptor.capture());
            assertEquals("online", statusCaptor.getValue(),
                "Version status should become 'online' when pipeline is disabled");
            
            // Verify: execute was never called
            verify(publishPipelineExecutor, never()).execute(any(), any(), any(String.class));
            
            // Verify: updatePublishPipelineInfo was NOT called (no pipeline execution)
            verify(aiResourceVersionPersistService, never()).updatePublishPipelineInfo(
                any(), any(), any(), any(), any());
        }
    }
    
    /**
     * When Pipeline is enabled and has matching nodes (execute returns non-blank executionId),
     * version status becomes reviewing, editingVersion is cleared, reviewingVersion is set.
     *
     * <p><b>Validates: Requirements 4.4</b></p>
     */
    @Test
    void submitSetsReviewingWhenPipelineEnabled() throws Exception {
        for (SubmitInput input : sampleSubmitInputs()) {
            EnvUtil.setEnvironment(new StandardEnvironment());
            
            // Mock dependencies
            final AiResourcePersistService aiResourcePersistService =
                mock(AiResourcePersistService.class);
            final AiResourceVersionPersistService aiResourceVersionPersistService =
                mock(AiResourceVersionPersistService.class);
            final PublishPipelineExecutor publishPipelineExecutor =
                mock(PublishPipelineExecutor.class);
            final PipelineExecutionRepository pipelineExecutionRepository =
                mock(PipelineExecutionRepository.class);
            
            // Build meta with editingVersion
            AiResource meta = buildMeta(input.namespaceId, input.name, input.version);
            when(aiResourcePersistService.find(eq(input.namespaceId), eq(input.name),
                eq(RESOURCE_TYPE_AGENTSPEC)))
                .thenReturn(meta);
            
            // Build version row as draft
            AiResourceVersion draftVersion =
                buildVersionRow(input.namespaceId, input.name, input.version, "draft");
            when(aiResourceVersionPersistService.find(
                eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC),
                eq(input.version)))
                .thenReturn(draftVersion);
            
            // isPipelineAvailable returns true -> pipeline enabled with matching nodes
            when(
                publishPipelineExecutor.isPipelineAvailable(any(PublishPipelineResourceType.class)))
                .thenReturn(true);
            
            // Pipeline executor returns the caller's pre-generated executionId
            when(publishPipelineExecutor.execute(any(PublishPipelineContext.class), any(),
                any(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(2));
            
            // Mock updateMetaCas to return true
            when(aiResourcePersistService.updateMetaCas(
                eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC), any(Long.class),
                any()))
                .thenReturn(true);
            
            // Construct service and call submit
            AgentSpecOperationServiceImpl service = new AgentSpecOperationServiceImpl(
                aiResourcePersistService, aiResourceVersionPersistService,
                publishPipelineExecutor,
                new AiResourceManager(aiResourcePersistService, aiResourceVersionPersistService,
                    pipelineExecutionRepository));
            String result = service.submit(input.namespaceId, input.name, input.version);
            
            assertEquals(input.version, result, "submit should return the version");
            
            // Verify: updateStatus was called with "reviewing"
            ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
            verify(aiResourceVersionPersistService).updateStatus(
                eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC),
                eq(input.version), statusCaptor.capture());
            assertEquals("reviewing", statusCaptor.getValue(),
                "Version status should become 'reviewing' when pipeline is enabled");
            
            // Verify: updateMetaCas was called (editingVersion cleared, reviewingVersion set)
            ArgumentCaptor<AiResource> metaCaptor = ArgumentCaptor.forClass(AiResource.class);
            verify(aiResourcePersistService).updateMetaCas(
                eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC),
                any(Long.class), metaCaptor.capture());
            String updatedVersionInfo = metaCaptor.getValue().getVersionInfo();
            assertTrue(
                updatedVersionInfo.contains("\"reviewingVersion\":\"" + input.version + "\""),
                "Meta should have reviewingVersion set to " + input.version
                    + ", but versionInfo was: " + updatedVersionInfo);
            assertTrue(!updatedVersionInfo.contains("\"editingVersion\":\"" + input.version + "\""),
                "Meta should have editingVersion cleared, but versionInfo was: "
                    + updatedVersionInfo);
            
            // Verify: updatePublishPipelineInfo was called with IN_PROGRESS
            ArgumentCaptor<String> pipelineInfoCaptor = ArgumentCaptor.forClass(String.class);
            verify(aiResourceVersionPersistService).updatePublishPipelineInfo(
                eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC),
                eq(input.version), pipelineInfoCaptor.capture());
            String pipelineInfoJson = pipelineInfoCaptor.getValue();
            assertTrue(pipelineInfoJson.contains("IN_PROGRESS"),
                "publishPipelineInfo should contain IN_PROGRESS status, but was: "
                    + pipelineInfoJson);
            
            // Verify: the executionId in pipelineInfo matches the one passed to execute
            ArgumentCaptor<String> execIdCaptor = ArgumentCaptor.forClass(String.class);
            verify(publishPipelineExecutor).execute(any(PublishPipelineContext.class), any(),
                execIdCaptor.capture());
            String capturedExecId = execIdCaptor.getValue();
            assertTrue(pipelineInfoJson.contains(capturedExecId),
                "publishPipelineInfo should contain executionId '" + capturedExecId
                    + "', but was: " + pipelineInfoJson);
        }
    }
    
    // ---- Helper methods ----
    
    private static AiResource buildMeta(String namespaceId, String name, String version) {
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName(name);
        meta.setType(RESOURCE_TYPE_AGENTSPEC);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"editingVersion\":\"" + version
            + "\",\"onlineCnt\":0,\"labels\":{}}");
        return meta;
    }
    
    private static AiResourceVersion buildVersionRow(String namespaceId, String name,
        String version, String status) {
        AiResourceVersion versionRow = new AiResourceVersion();
        versionRow.setNamespaceId(namespaceId);
        versionRow.setName(name);
        versionRow.setType(RESOURCE_TYPE_AGENTSPEC);
        versionRow.setVersion(version);
        versionRow.setStatus(status);
        return versionRow;
    }
    
    // ---- Test input models ----
    
    static class SubmitInput {
        
        final String namespaceId;
        final String name;
        final String version;
        
        SubmitInput(String namespaceId, String name, String version) {
            this.namespaceId = namespaceId;
            this.name = name;
            this.version = version;
        }
        
        @Override
        public String toString() {
            return "SubmitInput{ns='" + namespaceId + "', name='" + name
                + "', version='" + version + "'}";
        }
    }
    
}
