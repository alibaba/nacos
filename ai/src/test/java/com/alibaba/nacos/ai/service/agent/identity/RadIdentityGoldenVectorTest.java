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

package com.alibaba.nacos.ai.service.agent.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadIdentityGoldenVectorTest {
    
    private static final String VECTOR_RESOURCE =
        "/com/alibaba/nacos/ai/service/agent/identity/rad-identity-v1-golden-vectors.json.txt";
    
    @Test
    void testLanguageNeutralGoldenVectors() throws IOException {
        JsonNode vectors = readVectors();
        assertEquals("nacos-rad-identity-golden-vectors-v1",
            requiredText(vectors, "format"));
        assertEquals(RadAsciiAgentIdCodec.CODEC_ID, requiredText(vectors, "codecId"));
        assertEquals(RadServiceNameComposer.COMPOSER_ID,
            requiredText(vectors, "serviceNameComposerId"));
        JsonNode codecVectors = requiredNonEmptyArray(vectors, "codecVectors");
        for (JsonNode vector : codecVectors) {
            String agentName = requiredText(vector, "agentName");
            String encodedAgentId = requiredText(vector, "encodedAgentId");
            String decodedAgentName = requiredText(vector, "decodedAgentName");
            assertEquals(encodedAgentId, RadAsciiAgentIdCodec.encode(agentName));
            assertEquals(decodedAgentName, RadAsciiAgentIdCodec.decode(encodedAgentId));
        }
        JsonNode serviceNameVectors = requiredNonEmptyArray(vectors, "serviceNameVectors");
        for (JsonNode vector : serviceNameVectors) {
            String agentName = requiredText(vector, "agentName");
            String protocol = requiredText(vector, "protocol");
            String serviceName = requiredText(vector, "serviceName");
            assertEquals(serviceName, RadServiceNameComposer.compose(agentName, protocol));
        }
    }
    
    private JsonNode readVectors() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(VECTOR_RESOURCE)) {
            assertNotNull(input, VECTOR_RESOURCE);
            return new ObjectMapper().readTree(input);
        }
    }
    
    private JsonNode requiredNonEmptyArray(JsonNode parent, String fieldName) {
        JsonNode value = parent.path(fieldName);
        assertTrue(value.isArray(), fieldName);
        assertTrue(value.size() > 0, fieldName);
        return value;
    }
    
    private String requiredText(JsonNode parent, String fieldName) {
        JsonNode value = parent.path(fieldName);
        assertTrue(value.isTextual(), fieldName);
        return value.textValue();
    }
}
