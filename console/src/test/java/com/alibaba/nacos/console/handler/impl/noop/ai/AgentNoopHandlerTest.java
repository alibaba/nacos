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

package com.alibaba.nacos.console.handler.impl.noop.ai;

import com.alibaba.nacos.api.ai.model.agent.AgentDraftCreateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentDraftUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentLabelsUpdateRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentUpdateRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link AgentNoopHandler}.
 *
 * @author Nacos
 */
class AgentNoopHandlerTest {
    
    private AgentNoopHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new AgentNoopHandler();
    }
    
    @Test
    void shouldRejectEveryOperationWhenAgentModuleIsDisabled() {
        assertDisabled(() -> handler.getAgent("ns", "agent"));
        assertDisabled(() -> handler.updateAgent("ns", new AgentUpdateRequest()));
        assertDisabled(() -> handler.deleteAgent("ns", "agent"));
        assertDisabled(
            () -> handler.listAgents("ns", "agent", "tag", "PUBLIC", "owner", null, 1, 10));
        assertDisabled(() -> handler.listVersions("ns", "agent", "draft", 1, 10));
        assertDisabled(() -> handler.getVersion("ns", "agent", "1.0.0"));
        assertDisabled(() -> handler.getRuntimeEndpoints("ns", "agent", "a2a", "1.0.0"));
        assertDisabled(() -> handler.createDraft("ns", new AgentDraftCreateRequest()));
        assertDisabled(() -> handler.updateDraft("ns", new AgentDraftUpdateRequest()));
        assertDisabled(() -> handler.deleteDraft("ns", "agent", "1.0.0"));
        assertDisabled(() -> handler.submit("ns", "agent", "1.0.0"));
        assertDisabled(() -> handler.publish("ns", "agent", "1.0.0"));
        assertDisabled(() -> handler.forcePublish("ns", "agent", "1.0.0"));
        assertDisabled(() -> handler.redraft("ns", "agent", "1.0.0"));
        assertDisabled(() -> handler.online("ns", "agent", "1.0.0"));
        assertDisabled(() -> handler.offline("ns", "agent", "1.0.0"));
        assertDisabled(() -> handler.updateLabels("ns", new AgentLabelsUpdateRequest()));
    }
    
    private void assertDisabled(Executable operation) {
        NacosApiException exception = assertThrows(NacosApiException.class, operation);
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, exception.getErrCode());
        assertEquals(ErrorCode.API_FUNCTION_DISABLED.getCode(), exception.getDetailErrCode());
    }
}
