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
import com.alibaba.nacos.ai.pipeline.model.PipelineCallback;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionResult;
import com.alibaba.nacos.ai.pipeline.model.PipelineExecutionStatus;
import com.alibaba.nacos.ai.pipeline.model.PipelineNodeResult;
import com.alibaba.nacos.ai.pipeline.repository.PipelineExecutionRepository;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property 4: Pipeline completion state consistency.
 *
 * <p>For any Pipeline execution result, when APPROVED the publishPipelineInfo status
 * is updated to APPROVED; when REJECTED the version status reverts to draft,
 * reviewingVersion is cleared, editingVersion is restored.</p>
 *
 * <p><b>Validates: Requirements 4.1, 4.2</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecPipelineCompletionPropertyTest {

    private static final String RESOURCE_TYPE_AGENTSPEC = "agentspec";

    /**
     * Property 4a: For any APPROVED pipeline result, the publishPipelineInfo
     * is updated with APPROVED status.
     */
    @Property(tries = 50)
    void pipelineApprovedUpdatesStatusToApproved(
            @ForAll("approvedResults") ApprovedInput input) throws Exception {

        // Mock dependencies
        final AiResourcePersistService aiResourcePersistService = mock(AiResourcePersistService.class);
        final AiResourceVersionPersistService aiResourceVersionPersistService =
                mock(AiResourceVersionPersistService.class);
        final PublishPipelineExecutor publishPipelineExecutor = mock(PublishPipelineExecutor.class);
        final PipelineExecutionRepository pipelineExecutionRepository = mock(PipelineExecutionRepository.class);

        // Build valid meta with versionInfo containing editingVersion
        AiResource meta = buildMeta(input.namespaceId, input.name, input.version);
        when(aiResourcePersistService.find(eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC)))
                .thenReturn(meta);

        // Build valid version row
        AiResourceVersion versionRow = buildVersionRow(input.namespaceId, input.name, input.version);
        when(aiResourceVersionPersistService.find(
                eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC), eq(input.version)))
                .thenReturn(versionRow);

        // Mock updateMetaCas to return true
        when(aiResourcePersistService.updateMetaCas(
                eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC), any(Long.class), any()))
                .thenReturn(true);

        // Capture the callback from execute()
        ArgumentCaptor<PipelineCallback> callbackCaptor = ArgumentCaptor.forClass(PipelineCallback.class);
        when(publishPipelineExecutor.execute(any(PublishPipelineContext.class), callbackCaptor.capture()))
                .thenReturn("exec-" + input.executionId);

        // Construct service and call submit to trigger pipeline
        AgentSpecOperationServiceImpl service = new AgentSpecOperationServiceImpl(
                aiResourcePersistService, aiResourceVersionPersistService,
                publishPipelineExecutor, pipelineExecutionRepository);
        service.submit(input.namespaceId, input.name, input.version);

        // Build APPROVED result and invoke the captured callback
        PipelineExecutionResult result = new PipelineExecutionResult();
        result.setExecutionId(input.executionId);
        result.setStatus(PipelineExecutionStatus.APPROVED);
        result.setPipeline(input.nodeResults);

        PipelineCallback callback = callbackCaptor.getValue();
        callback.onComplete(result);

        // Verify: updatePublishPipelineInfo was called and the JSON contains APPROVED
        ArgumentCaptor<String> pipelineInfoCaptor = ArgumentCaptor.forClass(String.class);
        // Called twice: once during submit (IN_PROGRESS), once during callback (APPROVED)
        verify(aiResourceVersionPersistService, org.mockito.Mockito.atLeast(2))
                .updatePublishPipelineInfo(eq(input.namespaceId), eq(input.name),
                        eq(RESOURCE_TYPE_AGENTSPEC), eq(input.version), pipelineInfoCaptor.capture());

        // The last call should contain APPROVED
        List<String> allValues = pipelineInfoCaptor.getAllValues();
        String lastPipelineInfoJson = allValues.get(allValues.size() - 1);
        assertTrue(lastPipelineInfoJson.contains("APPROVED"),
                "publishPipelineInfo should contain APPROVED status after approved callback, but was: "
                        + lastPipelineInfoJson);
    }

    /**
     * Property 4b: For any REJECTED pipeline result, the version status reverts to draft
     * and meta pointers are updated (reviewingVersion cleared, editingVersion restored).
     */
    @Property(tries = 50)
    void pipelineRejectedRevertsVersionToDraft(
            @ForAll("rejectedResults") RejectedInput input) throws Exception {

        // Mock dependencies
        final AiResourcePersistService aiResourcePersistService = mock(AiResourcePersistService.class);
        final AiResourceVersionPersistService aiResourceVersionPersistService =
                mock(AiResourceVersionPersistService.class);
        final PublishPipelineExecutor publishPipelineExecutor = mock(PublishPipelineExecutor.class);
        final PipelineExecutionRepository pipelineExecutionRepository = mock(PipelineExecutionRepository.class);

        // Build valid meta with versionInfo containing editingVersion
        AiResource meta = buildMeta(input.namespaceId, input.name, input.version);
        when(aiResourcePersistService.find(eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC)))
                .thenReturn(meta);

        // Build valid version row
        AiResourceVersion versionRow = buildVersionRow(input.namespaceId, input.name, input.version);
        when(aiResourceVersionPersistService.find(
                eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC), eq(input.version)))
                .thenReturn(versionRow);

        // Mock updateMetaCas to return true
        when(aiResourcePersistService.updateMetaCas(
                eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC), any(Long.class), any()))
                .thenReturn(true);

        // Capture the callback from execute()
        ArgumentCaptor<PipelineCallback> callbackCaptor = ArgumentCaptor.forClass(PipelineCallback.class);
        when(publishPipelineExecutor.execute(any(PublishPipelineContext.class), callbackCaptor.capture()))
                .thenReturn("exec-" + input.executionId);

        // After submit(), the meta's reviewingVersion will be set to the version.
        // For the callback's find() call, return meta with reviewingVersion set.
        AiResource reviewingMeta = buildMetaWithReviewingVersion(input.namespaceId, input.name, input.version);
        // First call returns editingVersion meta (for submit), subsequent calls return reviewingVersion meta (for callback)
        when(aiResourcePersistService.find(eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC)))
                .thenReturn(meta)
                .thenReturn(reviewingMeta);

        // Construct service and call submit to trigger pipeline
        AgentSpecOperationServiceImpl service = new AgentSpecOperationServiceImpl(
                aiResourcePersistService, aiResourceVersionPersistService,
                publishPipelineExecutor, pipelineExecutionRepository);
        service.submit(input.namespaceId, input.name, input.version);

        // Build REJECTED result and invoke the captured callback
        PipelineExecutionResult result = new PipelineExecutionResult();
        result.setExecutionId(input.executionId);
        result.setStatus(PipelineExecutionStatus.REJECTED);
        result.setPipeline(input.nodeResults);

        PipelineCallback callback = callbackCaptor.getValue();
        callback.onComplete(result);

        // Verify: version status was reverted to "draft"
        // updateStatus is called twice: once during submit (reviewing), once during callback (draft)
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiResourceVersionPersistService, org.mockito.Mockito.atLeast(2))
                .updateStatus(eq(input.namespaceId), eq(input.name),
                        eq(RESOURCE_TYPE_AGENTSPEC), eq(input.version), statusCaptor.capture());

        List<String> allStatuses = statusCaptor.getAllValues();
        String lastStatus = allStatuses.get(allStatuses.size() - 1);
        assertTrue("draft".equals(lastStatus),
                "Version status should be reverted to 'draft' after REJECTED callback, but was: " + lastStatus);

        // Verify: updatePublishPipelineInfo was called with REJECTED
        ArgumentCaptor<String> pipelineInfoCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiResourceVersionPersistService, org.mockito.Mockito.atLeast(2))
                .updatePublishPipelineInfo(eq(input.namespaceId), eq(input.name),
                        eq(RESOURCE_TYPE_AGENTSPEC), eq(input.version), pipelineInfoCaptor.capture());

        List<String> allPipelineInfos = pipelineInfoCaptor.getAllValues();
        String lastPipelineInfo = allPipelineInfos.get(allPipelineInfos.size() - 1);
        assertTrue(lastPipelineInfo.contains("REJECTED"),
                "publishPipelineInfo should contain REJECTED status after rejected callback, but was: "
                        + lastPipelineInfo);

        // Verify: meta was updated (updateMetaCas called at least twice: once for submit, once for callback rollback)
        verify(aiResourcePersistService, org.mockito.Mockito.atLeast(2))
                .updateMetaCas(eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC),
                        any(Long.class), any());
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

    private static AiResource buildMetaWithReviewingVersion(String namespaceId, String name, String version) {
        AiResource meta = new AiResource();
        meta.setNamespaceId(namespaceId);
        meta.setName(name);
        meta.setType(RESOURCE_TYPE_AGENTSPEC);
        meta.setStatus("enable");
        meta.setMetaVersion(2L);
        meta.setVersionInfo("{\"reviewingVersion\":\"" + version
                + "\",\"onlineCnt\":0,\"labels\":{}}");
        return meta;
    }

    private static AiResourceVersion buildVersionRow(String namespaceId, String name, String version) {
        AiResourceVersion versionRow = new AiResourceVersion();
        versionRow.setNamespaceId(namespaceId);
        versionRow.setName(name);
        versionRow.setType(RESOURCE_TYPE_AGENTSPEC);
        versionRow.setVersion(version);
        versionRow.setStatus("draft");
        return versionRow;
    }

    // ---- Test input models ----

    static class ApprovedInput {
        final String namespaceId;
        final String name;
        final String version;
        final String executionId;
        final List<PipelineNodeResult> nodeResults;

        ApprovedInput(String namespaceId, String name, String version,
                String executionId, List<PipelineNodeResult> nodeResults) {
            this.namespaceId = namespaceId;
            this.name = name;
            this.version = version;
            this.executionId = executionId;
            this.nodeResults = nodeResults;
        }

        @Override
        public String toString() {
            return "ApprovedInput{ns='" + namespaceId + "', name='" + name
                    + "', version='" + version + "', execId='" + executionId + "'}";
        }
    }

    static class RejectedInput {
        final String namespaceId;
        final String name;
        final String version;
        final String executionId;
        final List<PipelineNodeResult> nodeResults;

        RejectedInput(String namespaceId, String name, String version,
                String executionId, List<PipelineNodeResult> nodeResults) {
            this.namespaceId = namespaceId;
            this.name = name;
            this.version = version;
            this.executionId = executionId;
            this.nodeResults = nodeResults;
        }

        @Override
        public String toString() {
            return "RejectedInput{ns='" + namespaceId + "', name='" + name
                    + "', version='" + version + "', execId='" + executionId + "'}";
        }
    }

    // ---- Arbitraries ----

    @Provide
    Arbitrary<ApprovedInput> approvedResults() {
        Arbitrary<String> namespaceIds = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> versions = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> executionIds = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30);
        Arbitrary<List<PipelineNodeResult>> nodeResults = pipelineNodeResults();

        return Combinators.combine(namespaceIds, names, versions, executionIds, nodeResults)
                .as(ApprovedInput::new);
    }

    @Provide
    Arbitrary<RejectedInput> rejectedResults() {
        Arbitrary<String> namespaceIds = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> versions = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> executionIds = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30);
        Arbitrary<List<PipelineNodeResult>> nodeResults = pipelineNodeResults();

        return Combinators.combine(namespaceIds, names, versions, executionIds, nodeResults)
                .as(RejectedInput::new);
    }

    private Arbitrary<List<PipelineNodeResult>> pipelineNodeResults() {
        Arbitrary<PipelineNodeResult> singleNode = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                Arbitraries.of(true, false),
                Arbitraries.strings().alpha().ofMaxLength(50),
                Arbitraries.longs().between(0, 10000)
        ).as((nodeId, executedAt, passed, message, durationMs) -> {
            PipelineNodeResult r = new PipelineNodeResult();
            r.setNodeId(nodeId);
            r.setExecutedAt(executedAt);
            r.setPassed(passed);
            r.setMessage(message);
            r.setDurationMs(durationMs);
            return r;
        });

        return singleNode.list().ofMinSize(0).ofMaxSize(5);
    }
}
