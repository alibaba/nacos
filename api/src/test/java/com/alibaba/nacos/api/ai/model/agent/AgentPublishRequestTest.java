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

package com.alibaba.nacos.api.ai.model.agent;

import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;

import com.alibaba.nacos.api.ai.model.agent.admin.AgentDraftCreateRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPublishRequestTest {
    
    @Test
    void testAutoSubmitAndInheritedValidation() {
        AgentPublishRequest request = new AgentPublishRequest();
        request.setAgentName("demo-agent");
        request.setVersion("1.0.0");
        request.setCallInterfaces(Collections.singletonList(new AgentCallInterface()));
        assertFalse(request.isAutoSubmit());
        request.setAutoSubmit(true);
        assertTrue(request.isAutoSubmit());
        request.validate();
    }
    
    @Test
    void testPublicationKeepsDraftSourceValidation() {
        AgentPublishRequest request = new AgentPublishRequest();
        request.setAgentName("demo-agent");
        request.setVersion("2.0.0");
        assertThrows(IllegalArgumentException.class, request::validate);
        request.setBasedOnVersion("1.0.0");
        request.validate();
        request.setCallInterfaces(Collections.singletonList(new AgentCallInterface()));
        assertThrows(IllegalArgumentException.class, request::validate);
        request.setBasedOnVersion(null);
        request.validate();
        request.setAgentName(null);
        assertThrows(IllegalArgumentException.class, request::validate);
    }
    
    @Test
    void testClientAndAdminBindSameContentWithoutSharingOperationFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String content = "{\"agentName\":\"demo\",\"displayName\":\"Demo\","
            + "\"description\":\"description\",\"iconUrl\":\"https://example.com/icon\","
            + "\"provider\":{\"name\":\"Nacos\"},\"tags\":[\"test\"],"
            + "\"extensions\":{\"example.com/data\":{\"nested\":[1,2]}},"
            + "\"version\":\"2.0.0\",\"basedOnVersion\":\"1.0.0\","
            + "\"author\":\"author\",\"changeDescription\":\"copy\"}";
        AgentDraftCreateRequest admin =
            mapper.readValue(content, AgentDraftCreateRequest.class);
        AgentPublishRequest client =
            mapper.readValue(content, AgentPublishRequest.class);
        admin.validate();
        client.validate();
        com.fasterxml.jackson.databind.node.ObjectNode expected =
            (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(content);
        expected.putNull("callInterfaces");
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected.get("provider")).putNull("url");
        assertEquals(expected, mapper.valueToTree(admin));
        expected.put("autoSubmit", false);
        assertEquals(expected, mapper.valueToTree(client));
        assertFalse(mapper.valueToTree(admin).has("autoSubmit"));
        assertFalse(mapper.valueToTree(client).has("namespaceId"));
    }
}
