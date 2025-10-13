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

package com.alibaba.nacos.api.ai.remote.request;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseAgentCardRequestTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws Exception {
        ReleaseAgentCardRequest request = new ReleaseAgentCardRequest();
        String id = UUID.randomUUID().toString();
        request.setRequestId("1");
        request.setNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        request.setAgentName("testAgent");
        request.setRegistrationType(AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE);
        request.setSetAsLatest(true);
        
        AgentCard agentCard = new AgentCard();
        agentCard.setName("testAgentCard");
        agentCard.setVersion("1.0.0");
        request.setAgentCard(agentCard);
        
        String json = mapper.writeValueAsString(request);
        assertNotNull(json);
        assertTrue(json.contains("\"requestId\":\"1\""));
        assertTrue(json.contains("\"namespaceId\":\"public\""));
        assertTrue(json.contains("\"agentName\":\"testAgent\""));
        assertTrue(json.contains("\"registrationType\":\"SERVICE\""));
        assertTrue(json.contains("\"setAsLatest\":true"));
        assertTrue(json.contains("\"name\":\"testAgentCard\""));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
    }
    
    @Test
    void testDeserialize() throws Exception {
        String json = "{\"headers\":{},\"requestId\":\"1\",\"namespaceId\":\"public\",\"agentName\":\"testAgent\","
                + "\"agentCard\":{\"protocolVersion\":null,\"name\":\"testAgentCard\",\"description\":null,\"version\":\"1.0.0\","
                + "\"iconUrl\":null,\"capabilities\":null,\"skills\":null,\"url\":null,\"preferredTransport\":null,"
                + "\"additionalInterfaces\":null,\"provider\":null,\"documentationUrl\":null,\"securitySchemes\":null,"
                + "\"security\":null,\"defaultInputModes\":null,\"defaultOutputModes\":null,"
                + "\"supportsAuthenticatedExtendedCard\":null},"
                + "\"registrationType\":\"SERVICE\",\"setAsLatest\":true,\"module\":\"ai\"}";
        ReleaseAgentCardRequest result = mapper.readValue(json, ReleaseAgentCardRequest.class);
        assertNotNull(result);
        assertEquals("1", result.getRequestId());
        assertEquals(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE, result.getNamespaceId());
        assertEquals("testAgent", result.getAgentName());
        assertEquals(AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE, result.getRegistrationType());
        assertEquals(true, result.isSetAsLatest());
        
        AgentCard agentCard = result.getAgentCard();
        assertNotNull(agentCard);
        assertEquals("testAgentCard", agentCard.getName());
        assertEquals("1.0.0", agentCard.getVersion());
    }
}