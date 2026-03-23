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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecForm;
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecScopeForm;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.AgentSpecMaintainerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for {@link AgentSpecRemoteHandler}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentSpecRemoteHandlerTest {

    private static final String NAMESPACE_ID = "test-ns";

    private static final String AGENT_SPEC_NAME = "test-agentspec";

    @Mock
    private NacosMaintainerClientHolder clientHolder;

    @Mock
    private AiMaintainerService aiMaintainerService;

    @Mock
    private AgentSpecMaintainerService agentSpecMaintainerService;

    private AgentSpecRemoteHandler agentSpecRemoteHandler;

    @BeforeEach
    void setUp() {
        when(clientHolder.getAiMaintainerService()).thenReturn(aiMaintainerService);
        when(aiMaintainerService.agentSpec()).thenReturn(agentSpecMaintainerService);
        agentSpecRemoteHandler = new AgentSpecRemoteHandler(clientHolder);
    }

    @Test
    void testGetAgentSpecVersion() throws NacosException {
        AgentSpecForm form = new AgentSpecForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setVersion("v1");
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setName(AGENT_SPEC_NAME);
        when(agentSpecMaintainerService.getAgentSpecVersionDetail(eq(NAMESPACE_ID), eq(AGENT_SPEC_NAME), eq("v1")))
                .thenReturn(agentSpec);

        AgentSpec result = agentSpecRemoteHandler.getAgentSpecVersion(form);

        assertEquals(AGENT_SPEC_NAME, result.getName());
        verify(agentSpecMaintainerService).getAgentSpecVersionDetail(NAMESPACE_ID, AGENT_SPEC_NAME, "v1");
    }

    @Test
    void testUpdateScope() throws NacosException {
        AgentSpecScopeForm form = new AgentSpecScopeForm();
        form.setNamespaceId(NAMESPACE_ID);
        form.setAgentSpecName(AGENT_SPEC_NAME);
        form.setScope("PUBLIC");
        when(agentSpecMaintainerService.updateScope(eq(NAMESPACE_ID), eq(AGENT_SPEC_NAME), eq("PUBLIC")))
                .thenReturn(true);

        agentSpecRemoteHandler.updateScope(form);

        verify(agentSpecMaintainerService).updateScope(NAMESPACE_ID, AGENT_SPEC_NAME, "PUBLIC");
    }
}