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

package com.alibaba.nacos.api.ai.remote.request;

import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.remote.response.AgentPublishRpcResponse;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentPublishRpcRequestTest extends BasicRequestTest {
    
    @Test
    void testRequestAccessorsAndSerialization() throws Exception {
        AgentPublishRpcRequest request = new AgentPublishRpcRequest();
        request.setNamespaceId("team");
        assertNull(request.extractAgentName());
        AgentPublishRequest publication = new AgentPublishRequest();
        publication.setAgentName("demo-agent");
        publication.setVersion("1.0.0");
        publication.setAutoSubmit(true);
        request.setPublishRequest(publication);
        assertEquals("team", request.getNamespaceId());
        assertEquals("team", request.extractNamespaceId());
        assertSame(publication, request.getPublishRequest());
        assertEquals("demo-agent", request.extractAgentName());
        
        AgentPublishRpcRequest restored = mapper.readValue(mapper.writeValueAsString(request),
            AgentPublishRpcRequest.class);
        assertEquals("team", restored.getNamespaceId());
        assertEquals("demo-agent", restored.getPublishRequest().getAgentName());
        assertEquals("1.0.0", restored.getPublishRequest().getVersion());
        assertEquals(true, restored.getPublishRequest().isAutoSubmit());
    }
    
    @Test
    void testResponseAccessors() {
        AgentPublishRpcResponse response = new AgentPublishRpcResponse();
        AgentVersionDetail detail = new AgentVersionDetail();
        response.setVersionDetail(detail);
        assertSame(detail, response.getVersionDetail());
    }
}
