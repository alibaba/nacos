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

package com.alibaba.nacos.api.ai.remote.response;

import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryAgentCardResponseTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws Exception {
        final QueryAgentCardResponse response = new QueryAgentCardResponse();
        AgentCardDetailInfo agentCardDetailInfo = new AgentCardDetailInfo();
        agentCardDetailInfo.setName("testAgent");
        agentCardDetailInfo.setVersion("1.0.0");
        agentCardDetailInfo.setDescription("Test Agent Description");
        response.setAgentCardDetailInfo(agentCardDetailInfo);
        response.setRequestId("1");
        String json = mapper.writeValueAsString(response);
        assertNotNull(json);
        assertTrue(json.contains("\"requestId\":\"1\""));
        assertTrue(json.contains("\"agentCardDetailInfo\":{"));
        assertTrue(json.contains("\"name\":\"testAgent\""));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"description\":\"Test Agent Description\""));
    }
    
    @Test
    void testDeserialize() throws Exception {
        String json = "{\"resultCode\":200,\"errorCode\":0,\"requestId\":\"1\",\"agentCardDetailInfo\":"
                + "{\"name\":\"testAgent\",\"version\":\"1.0.0\",\"description\":\"Test Agent Description\"},\"success\":true}";
        QueryAgentCardResponse result = mapper.readValue(json, QueryAgentCardResponse.class);
        assertNotNull(result);
        assertEquals("1", result.getRequestId());
        AgentCardDetailInfo agentCardDetailInfo = result.getAgentCardDetailInfo();
        assertNotNull(agentCardDetailInfo);
        assertEquals("testAgent", agentCardDetailInfo.getName());
        assertEquals("1.0.0", agentCardDetailInfo.getVersion());
        assertEquals("Test Agent Description", agentCardDetailInfo.getDescription());
    }
}