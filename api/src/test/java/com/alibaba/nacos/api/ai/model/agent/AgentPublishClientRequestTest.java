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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPublishClientRequestTest {
    
    @Test
    void testAutoSubmitAndInheritedValidation() {
        AgentPublishClientRequest request = new AgentPublishClientRequest();
        request.setAgentName("demo-agent");
        request.setVersion("1.0.0");
        request.setCallInterfaces(Collections.singletonList(new AgentDefinitionCallInterface()));
        assertFalse(request.isAutoSubmit());
        request.setAutoSubmit(true);
        assertTrue(request.isAutoSubmit());
        request.validate();
    }
    
    @Test
    void testPublicationKeepsDraftSourceValidation() {
        AgentPublishClientRequest request = new AgentPublishClientRequest();
        request.setAgentName("demo-agent");
        request.setVersion("2.0.0");
        assertThrows(IllegalArgumentException.class, request::validate);
        request.setBasedOnVersion("1.0.0");
        request.validate();
        request.setCallInterfaces(Collections.singletonList(new AgentDefinitionCallInterface()));
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
        AgentDraftCreateAdminRequest admin =
            mapper.readValue(content, AgentDraftCreateAdminRequest.class);
        AgentPublishClientRequest client =
            mapper.readValue(content, AgentPublishClientRequest.class);
        admin.validate();
        client.validate();
        assertEquals(mapper.readTree(content), mapper.valueToTree(admin));
        String publication = content.substring(0, content.length() - 1) + ",\"autoSubmit\":false}";
        assertEquals(mapper.readTree(publication), mapper.valueToTree(client));
        assertFalse(mapper.valueToTree(admin).has("autoSubmit"));
        assertFalse(mapper.valueToTree(client).has("namespaceId"));
    }
}
