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

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Submit resource type correctness.
 *
 * <p><b>Validates: Requirements 2.1, 2.2</b></p>
 *
 * @author kiro
 * @since 3.2.0
 */
class AgentSpecSubmitResourceTypeTest {
    
    private static final String RESOURCE_TYPE_AGENTSPEC = "agentspec";
    
    private static List<SubmitInput> sampleSubmitInputs() {
        return Arrays.asList(
            new SubmitInput("public", "agent", "v1"),
            new SubmitInput("ns", "myname", "2.0"),
            new SubmitInput("testns", "abc", "draft"));
    }
    
    @Test
    void submitAlwaysSetsResourceTypeToAgentSpec() throws Exception {
        for (SubmitInput input : sampleSubmitInputs()) {
            EnvUtil.setEnvironment(new StandardEnvironment());
            
            final AiResourcePersistService aiResourcePersistService =
                mock(AiResourcePersistService.class);
            final AiResourceVersionPersistService aiResourceVersionPersistService =
                mock(AiResourceVersionPersistService.class);
            final PublishPipelineExecutor publishPipelineExecutor =
                mock(PublishPipelineExecutor.class);
            final PipelineExecutionRepository pipelineExecutionRepository =
                mock(PipelineExecutionRepository.class);
            
            AiResource meta = buildMeta(input);
            when(aiResourcePersistService.find(eq(input.namespaceId), eq(input.name),
                eq(RESOURCE_TYPE_AGENTSPEC)))
                .thenReturn(meta);
            
            AiResourceVersion versionRow = buildVersionRow(input);
            when(aiResourceVersionPersistService.find(
                eq(input.namespaceId), eq(input.name), eq(RESOURCE_TYPE_AGENTSPEC),
                eq(input.version)))
                .thenReturn(versionRow);
            
            // isPipelineAvailable returns true to enter the pipeline path
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
            
            AgentSpecOperationServiceImpl service = new AgentSpecOperationServiceImpl(
                aiResourcePersistService, aiResourceVersionPersistService,
                publishPipelineExecutor,
                new AiResourceManager(aiResourcePersistService, aiResourceVersionPersistService,
                    pipelineExecutionRepository));
            service.submit(input.namespaceId, input.name, input.version);
            
            ArgumentCaptor<PublishPipelineContext> captor =
                ArgumentCaptor.forClass(PublishPipelineContext.class);
            verify(publishPipelineExecutor).execute(captor.capture(), any(), any(String.class));
            
            PublishPipelineContext capturedCtx = captor.getValue();
            assertNotNull(capturedCtx, "PublishPipelineContext should not be null");
            assertEquals(PublishPipelineResourceType.AGENTSPEC, capturedCtx.getResourceType(),
                "resourceType must always be AGENTSPEC for AgentSpec submit, but was: "
                    + capturedCtx.getResourceType());
        }
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
    
    static class SubmitInput {
        
        final String namespaceId;
        final String name;
        final String version;
        
        SubmitInput(String namespaceId, String name, String version) {
            this.namespaceId = namespaceId;
            this.name = name;
            this.version = version;
        }
    }
}
