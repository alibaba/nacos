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

package com.alibaba.nacos.copilot.connection;

import com.alibaba.nacos.copilot.config.CopilotProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for DeploymentConfigProviderReader.
 *
 * @author nacos
 */
class DeploymentConfigProviderReaderTest {
    
    private static final ProviderReadContext CONTEXT =
        new ProviderReadContext(new CopilotProperties());
    
    @Test
    void testReadsAllConnections() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("PROD_KEY", "secret")
            .withProperty("nacos.copilot.connections.prod-qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.prod-qwen.model", "qwen-max")
            .withProperty("nacos.copilot.connections.prod-qwen.apiKey", "${PROD_KEY}")
            .withProperty("nacos.copilot.connections.prod-qwen.displayName", "Prod Qwen")
            .withProperty("nacos.copilot.connections.prod-qwen.baseUrl", "https://llm.example.com")
            .withProperty("nacos.copilot.connections.prod-qwen.endpointPath", "/v1")
            .withProperty("nacos.copilot.connections.prod-qwen.stream", "false")
            .withProperty("nacos.copilot.connections.prod-qwen.enableThinking", "true")
            .withProperty("nacos.copilot.connections.prod-qwen.options.enableSearch", "true")
            .withProperty("nacos.copilot.connections.local-ollama.providerId", "ollama")
            .withProperty("nacos.copilot.connections.local-ollama.model", "qwen2.5");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader(environment);
        
        Map<String, CopilotModelConnection> connections = byName(reader.read(CONTEXT));
        
        assertEquals(2, connections.size());
        CopilotModelConnection prod = connections.get("prod-qwen");
        assertEquals("dashscope:qwen-max", prod.getModelId());
        assertEquals("secret", prod.getApiKey());
        assertEquals("Prod Qwen", prod.getDisplayName());
        assertEquals("https://llm.example.com", prod.getBaseUrl());
        assertEquals("/v1", prod.getEndpointPath());
        assertFalse(prod.getStream());
        assertTrue(prod.getEnableThinking());
        assertEquals(Boolean.TRUE, prod.getOptions().get("enableSearch"));
        assertEquals(ConnectionSource.DEPLOYMENT, prod.getSource());
        assertEquals(DeploymentConfigProviderReader.ORDER, prod.getPriority());
        assertTrue(prod.isEnabled());
        assertEquals("local-ollama", connections.get("local-ollama").getName());
    }
    
    @Test
    void testNormalizesMixedCaseConnectionName() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.Prod-Qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.Prod-Qwen.model", "qwen-max")
            .withProperty("nacos.copilot.connections.Prod-Qwen.apiKey", "secret");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader(environment);
        
        List<CopilotModelConnection> connections = reader.read(CONTEXT);
        
        assertEquals(1, connections.size());
        assertEquals("prod-qwen", connections.get(0).getName());
        assertEquals("dashscope:qwen-max", connections.get(0).getModelId());
        assertEquals("secret", connections.get(0).getApiKey());
    }
    
    @Test
    void testDuplicateSpellingsKeepOneConnection() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.Prod-Qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.Prod-Qwen.model", "qwen-plus")
            .withProperty("nacos.copilot.connections.prod-qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.prod-qwen.model", "qwen-max");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader(environment);
        
        List<CopilotModelConnection> connections = reader.read(CONTEXT);
        
        assertEquals(1, connections.size());
        assertEquals("prod-qwen", connections.get(0).getName());
        assertEquals("qwen-max", connections.get(0).getModel());
    }
    
    @Test
    void testDuplicateNonCanonicalSpellingsKeepDeterministicConnection() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.PROD-QWEN.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.PROD-QWEN.model", "qwen-max")
            .withProperty("nacos.copilot.connections.Prod-Qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.Prod-Qwen.model", "qwen-plus");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader(environment);
        
        List<CopilotModelConnection> connections = reader.read(CONTEXT);
        
        assertEquals(1, connections.size());
        assertEquals("prod-qwen", connections.get(0).getName());
        assertEquals("qwen-max", connections.get(0).getModel());
    }
    
    @Test
    void testIgnoredSpellingDoesNotLeakIntoConnection() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.Prod-Qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.prod-qwen.model", "qwen-max");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader(environment);
        
        List<CopilotModelConnection> connections = reader.read(CONTEXT);
        
        assertEquals(1, connections.size());
        assertEquals("prod-qwen", connections.get(0).getName());
        assertNull(connections.get(0).getProviderId());
        assertEquals("qwen-max", connections.get(0).getModel());
    }
    
    @Test
    void testDefaultsDisplayNameToConnectionName() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.prod-qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.prod-qwen.model", "qwen-max");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader(environment);
        
        CopilotModelConnection connection = reader.read(CONTEXT).get(0);
        
        assertEquals("prod-qwen", connection.getDisplayName());
        assertTrue(connection.isEnabled());
    }
    
    @Test
    void testExplicitDisableIsKept() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.prod-qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.prod-qwen.model", "qwen-max")
            .withProperty("nacos.copilot.connections.prod-qwen.enabled", "false");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader(environment);
        
        assertFalse(reader.read(CONTEXT).get(0).isEnabled());
    }
    
    @Test
    void testMalformedConnectionDoesNotHideOthers() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.broken.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.broken.model", "qwen-max")
            .withProperty("nacos.copilot.connections.broken.stream", "not-a-boolean")
            .withProperty("nacos.copilot.connections.prod-qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.prod-qwen.model", "qwen-max");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader(environment);
        
        List<CopilotModelConnection> connections = reader.read(CONTEXT);
        
        assertEquals(1, connections.size());
        assertEquals("prod-qwen", connections.get(0).getName());
    }
    
    @Test
    void testNoConnections() {
        DeploymentConfigProviderReader reader =
            new DeploymentConfigProviderReader(new MockEnvironment());
        
        assertTrue(reader.read(CONTEXT).isEmpty());
    }
    
    @Test
    void testOrder() {
        assertEquals(10,
            new DeploymentConfigProviderReader(new MockEnvironment()).order());
    }
    
    private Map<String, CopilotModelConnection> byName(List<CopilotModelConnection> connections) {
        Map<String, CopilotModelConnection> result = new HashMap<>();
        for (CopilotModelConnection connection : connections) {
            result.put(connection.getName(), connection);
        }
        return result;
    }
}
