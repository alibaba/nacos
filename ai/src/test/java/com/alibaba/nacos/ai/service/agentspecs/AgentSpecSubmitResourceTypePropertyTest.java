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
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineContext;
import com.alibaba.nacos.plugin.ai.pipeline.model.PublishPipelineResourceType;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Property 2: Submit resource type correctness.
 *
 * <p>For any AgentSpec submit operation, the PublishPipelineContext's resourceType
 * is always {@link PublishPipelineResourceType#AGENTSPEC}.</p>
 *
 * <p><b>Validates: Requirements 2.1, 2.2</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecSubmitResourceTypePropertyTest {

    private static final String RESOURCE_TYPE_AGENTSPEC = "agentspec";

    /**
     * Property 2: For any valid namespaceId, name, and version strings, calling submit()
     * on AgentSpecOperationServiceImpl always passes a PublishPipelineContext with
     * resourceType == AGENTSPEC to the PublishPipelineExecutor.
     */
    @Property(tries = 50)
    void submitAlwaysSetsResourceTypeToAgentSpec(
            @ForAll("submitInputs") SubmitInput input) throws Exception {

        // Mock dependencies
        final AiResourcePersistService aiResourcePersistService = mock(AiResourcePersistService.class);
        final AiResourceVersionPersistService aiResourceVersionPersistService =
                mock(AiResourceVersionPersistService.class);
        final PublishPipelineExecutor publishPipelineExecutor = mock(PublishPipelineExecutor.class);
        final PipelineExecutionRepository pipelineExecutionRepository = mock(PipelineExecutionRepository.class);

        // Build a valid AiResource with versionInfo JSON containing editingVersion
        AiResource meta = buildMeta(input);
        when(aiResourcePersistService.find(eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC)))
                .thenReturn(meta);

        // Build a valid AiResourceVersion
        AiResourceVersion versionRow = buildVersionRow(input);
        when(aiResourceVersionPersistService.find(
                eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC), eq(input.version)))
                .thenReturn(versionRow);

        // Pipeline executor returns null (pipeline disabled) so submit() won't try to update reviewing status
        when(publishPipelineExecutor.execute(any(PublishPipelineContext.class), any()))
                .thenReturn(null);

        // Construct the service under test and call submit
        AgentSpecOperationServiceImpl service = new AgentSpecOperationServiceImpl(
                aiResourcePersistService, aiResourceVersionPersistService,
                publishPipelineExecutor, pipelineExecutionRepository);
        try {
            service.submit(input.namespaceId, input.name, input.version);
        } catch (Exception e) {
            // publish() may fail due to unmocked storage, but the context was already captured
        }

        // Capture the PublishPipelineContext passed to execute()
        ArgumentCaptor<PublishPipelineContext> captor = ArgumentCaptor.forClass(PublishPipelineContext.class);
        verify(publishPipelineExecutor).execute(captor.capture(), any());

        PublishPipelineContext capturedCtx = captor.getValue();
        assertNotNull(capturedCtx, "PublishPipelineContext should not be null");
        assertEquals(PublishPipelineResourceType.AGENTSPEC, capturedCtx.getResourceType(),
                "resourceType must always be AGENTSPEC for AgentSpec submit, but was: "
                        + capturedCtx.getResourceType());
    }

    private static AiResource buildMeta(SubmitInput input) {
        AiResource meta = new AiResource();
        meta.setNamespaceId(input.namespaceId);
        meta.setName(input.name);
        meta.setType(RESOURCE_TYPE_AGENTSPEC);
        meta.setStatus("enable");
        meta.setMetaVersion(1L);
        meta.setVersionInfo("{\"editingVersion\":\"" + input.version
                + "\",\"onlineCnt\":0,\"labels\":{}}");
        return meta;
    }

    private static AiResourceVersion buildVersionRow(SubmitInput input) {
        AiResourceVersion versionRow = new AiResourceVersion();
        versionRow.setNamespaceId(input.namespaceId);
        versionRow.setName(input.name);
        versionRow.setType(RESOURCE_TYPE_AGENTSPEC);
        versionRow.setVersion(input.version);
        versionRow.setStatus("draft");
        return versionRow;
    }

    // ---- Test input model ----

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
            return "SubmitInput{namespaceId='" + namespaceId + "', name='" + name
                    + "', version='" + version + "'}";
        }
    }

    // ---- Arbitraries ----

    @Provide
    Arbitrary<SubmitInput> submitInputs() {
        Arbitrary<String> namespaceIds = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> names = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);
        Arbitrary<String> versions = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20);

        return Combinators.combine(namespaceIds, names, versions).as(SubmitInput::new);
    }
}
