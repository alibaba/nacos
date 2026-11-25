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
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
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
    
    private ProviderReadContext context(MockEnvironment environment) {
        return new ProviderReadContext(ProviderReadContext.captureDeployment(environment),
            new CopilotProperties(), false, null);
    }
    
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
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader();
        
        Map<String, CopilotModelConnection> connections = byName(reader.read(context(environment)));
        
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
        assertEquals(CredentialSource.DEPLOYMENT, prod.getCredentialSource());
        assertTrue(prod.isEnabled());
        assertEquals("local-ollama", connections.get("local-ollama").getName());
        assertNull(connections.get("local-ollama").getCredentialSource());
    }
    
    @Test
    void testNormalizesMixedCaseConnectionName() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.Prod-Qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.Prod-Qwen.model", "qwen-max")
            .withProperty("nacos.copilot.connections.Prod-Qwen.apiKey", "secret");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader();
        
        List<CopilotModelConnection> connections = reader.read(context(environment));
        
        assertEquals(1, connections.size());
        assertEquals("prod-qwen", connections.get(0).getName());
        assertEquals("dashscope:qwen-max", connections.get(0).getModelId());
        assertEquals("secret", connections.get(0).getApiKey());
    }
    
    @Test
    void testUnsupportedNameSpellingsAreIgnored() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.prod-qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.prod-qwen.model", "qwen-max")
            .withProperty("nacos.copilot.connections.生产.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.生产.model", "qwen-plus");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader();
        
        List<CopilotModelConnection> connections = reader.read(context(environment));
        
        assertEquals(1, connections.size());
        assertEquals("prod-qwen", connections.get(0).getName());
    }
    
    @Test
    void testRelaxedSpellingsShareOneConnectionBlock() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.Prod-Qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.prod-qwen.model", "qwen-max")
            .withProperty("nacos.copilot.connections.PROD-QWEN.apiKey", "secret");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader();
        
        List<CopilotModelConnection> connections = reader.read(context(environment));
        
        assertEquals(1, connections.size());
        assertEquals("prod-qwen", connections.get(0).getName());
        assertEquals("dashscope", connections.get(0).getProviderId());
        assertEquals("qwen-max", connections.get(0).getModel());
        assertEquals("secret", connections.get(0).getApiKey());
    }
    
    @Test
    void testUnderscoredNameBecomesItsOwnConnection() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.prod-qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.prod-qwen.model", "qwen-max")
            .withProperty("nacos.copilot.connections.prod_qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.prod_qwen.model", "qwen-plus");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader();
        
        Map<String, CopilotModelConnection> connections = byName(reader.read(context(environment)));
        
        assertEquals(2, connections.size());
        assertEquals("qwen-max", connections.get("prod-qwen").getModel());
        assertEquals("qwen-plus", connections.get("prodqwen").getModel());
    }
    
    @Test
    void testDefaultsDisplayNameToConnectionName() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.prod-qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.prod-qwen.model", "qwen-max");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader();
        
        CopilotModelConnection connection = reader.read(context(environment)).get(0);
        
        assertEquals("prod-qwen", connection.getDisplayName());
        assertTrue(connection.isEnabled());
    }
    
    @Test
    void testExplicitDisableIsKept() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.prod-qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.prod-qwen.model", "qwen-max")
            .withProperty("nacos.copilot.connections.prod-qwen.enabled", "false");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader();
        
        assertFalse(reader.read(context(environment)).get(0).isEnabled());
    }
    
    @Test
    void testMalformedConnectionDoesNotHideOthers() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.broken.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.broken.model", "qwen-max")
            .withProperty("nacos.copilot.connections.broken.stream", "not-a-boolean")
            .withProperty("nacos.copilot.connections.prod-qwen.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.prod-qwen.model", "qwen-max");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader();
        
        List<CopilotModelConnection> connections = reader.read(context(environment));
        
        assertEquals(1, connections.size());
        assertEquals("prod-qwen", connections.get(0).getName());
    }
    
    @Test
    void testNoConnections() {
        MockEnvironment environment = new MockEnvironment();
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader();
        
        assertTrue(reader.read(context(environment)).isEmpty());
    }
    
    @Test
    void testOrder() {
        assertEquals(10,
            new DeploymentConfigProviderReader().order());
    }
    
    @Test
    void testCaptureResolvesNonEnumerablePlaceholderInputsOnlyOnce() {
        Map<String, String> values = new HashMap<>();
        values.put("MODEL", "qwen-max");
        values.put("KEY", "before");
        Map<String, Integer> reads = new HashMap<>();
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.alpha.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.alpha.model", "${MODEL}")
            .withProperty("nacos.copilot.connections.alpha.apiKey", "${KEY}")
            .withProperty("nacos.copilot.connections.beta.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.beta.model", "${MODEL}")
            .withProperty("nacos.copilot.connections.beta.apiKey", "${KEY}");
        environment.getPropertySources().addFirst(new PropertySource<>("external", values) {
            
            @Override
            public Object getProperty(String name) {
                reads.merge(name, 1, Integer::sum);
                return source.get(name);
            }
        });
        ConfigurationPropertySources.attach(environment);
        ProviderReadContext captured = context(environment);
        values.put("MODEL", "qwen-turbo");
        values.put("KEY", "after");
        
        List<CopilotModelConnection> connections =
            new DeploymentConfigProviderReader().read(captured);
        
        assertEquals(2, connections.size());
        for (CopilotModelConnection connection : connections) {
            assertEquals("qwen-max", connection.getModel());
            assertEquals("before", connection.getApiKey());
        }
        assertEquals(1, reads.get("MODEL"));
        assertEquals(1, reads.get("KEY"));
    }
    
    @Test
    void testCaptureKeepsNonEnumerableOverridesOfDiscoveredConnectionProperties() {
        String apiKey = "nacos.copilot.connections.alpha.apiKey";
        String model = "nacos.copilot.connections.alpha.model";
        Map<String, String> higherValues = new HashMap<>();
        higherValues.put(apiKey, "external-key");
        higherValues.put("SHARED_KEY", "shared-key");
        Map<String, String> lowerValues = new HashMap<>();
        lowerValues.put(apiKey, "lower-key");
        Map<String, Integer> higherReads = new HashMap<>();
        Map<String, Integer> lowerReads = new HashMap<>();
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.alpha.providerId", "dashscope")
            .withProperty(apiKey, "old-key")
            .withProperty("nacos.copilot.connections.beta.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.beta.model", "qwen-max")
            .withProperty("nacos.copilot.connections.beta.apiKey", "${SHARED_KEY}");
        environment.getPropertySources().addLast(
            new MapPropertySource("models", Map.of(model, "qwen-max")));
        environment.getPropertySources().addFirst(new PropertySource<>("higher", higherValues) {
            
            @Override
            public Object getProperty(String name) {
                higherReads.merge(name, 1, Integer::sum);
                return source.get(name);
            }
        });
        environment.getPropertySources().addLast(new PropertySource<>("lower", lowerValues) {
            
            @Override
            public Object getProperty(String name) {
                lowerReads.merge(name, 1, Integer::sum);
                return source.get(name);
            }
        });
        ConfigurationPropertySources.attach(environment);
        ProviderReadContext captured = context(environment);
        higherValues.put(apiKey, "changed-key");
        lowerValues.put(apiKey, "changed-lower-key");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader();
        
        for (int refreshReader = 0; refreshReader < 2; refreshReader++) {
            Map<String, CopilotModelConnection> connections = byName(reader.read(captured));
            assertEquals(2, connections.size());
            assertEquals("external-key", connections.get("alpha").getApiKey());
            assertEquals("shared-key", connections.get("beta").getApiKey());
            assertEquals("qwen-max", connections.get("alpha").getModel());
        }
        assertEquals(1, higherReads.get(apiKey));
        assertEquals(1, lowerReads.get(apiKey));
        assertEquals(1, higherReads.get(model));
        assertEquals(1, lowerReads.get(model));
    }
    
    @Test
    void testUnresolvedCredentialPlaceholderSkipsOnlyBrokenConnection() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.broken.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.broken.model", "qwen-max")
            .withProperty("nacos.copilot.connections.broken.apiKey", "${MISSING}")
            .withProperty("nacos.copilot.connections.healthy.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.healthy.model", "qwen-max")
            .withProperty("nacos.copilot.connections.healthy.apiKey", "healthy-key");
        
        List<CopilotModelConnection> connections =
            new DeploymentConfigProviderReader().read(context(environment));
        
        assertEquals(1, connections.size());
        assertEquals("healthy", connections.get(0).getName());
        assertEquals("healthy-key", connections.get(0).getApiKey());
    }
    
    @Test
    void testShadowedCircularModelPlaceholderDoesNotDropConnection() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("LOOP", "${LOOP}")
            .withProperty("nacos.copilot.connections.alpha.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.alpha.model", "${LOOP}")
            .withProperty("nacos.copilot.connections.alpha.apiKey", "healthy-key");
        environment.getPropertySources().addFirst(new MapPropertySource("higher", Map.of(
            "nacos.copilot.connections.alpha.model", "qwen-max")));
        
        List<CopilotModelConnection> connections =
            new DeploymentConfigProviderReader().read(context(environment));
        
        assertEquals(1, connections.size());
        assertEquals("qwen-max", connections.get(0).getModel());
        assertEquals("healthy-key", connections.get(0).getApiKey());
    }
    
    @Test
    void testRelaxedNameWinnerDoesNotResolveShadowedCredentialPlaceholder() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("LOOP", "${LOOP}")
            .withProperty("nacos.copilot.connections.alpha.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.alpha.model", "qwen-max")
            .withProperty("nacos.copilot.connections.alpha.apiKey", "${LOOP}");
        environment.getPropertySources().addFirst(new MapPropertySource("higher", Map.of(
            "nacos.copilot.connections.alpha.api-key", "healthy-key")));
        
        List<CopilotModelConnection> connections =
            new DeploymentConfigProviderReader().read(context(environment));
        
        assertEquals(1, connections.size());
        assertEquals("healthy-key", connections.get(0).getApiKey());
    }
    
    @Test
    void testEnvironmentVariablesOverrideDeclaredConnectionValues() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.alpha.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.alpha.model", "file-model")
            .withProperty("nacos.copilot.connections.alpha.apiKey", "file-key")
            .withProperty("nacos.copilot.connections.alpha.enabled", "true");
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("env",
            Map.of("NACOS_COPILOT_CONNECTIONS_ALPHA_MODEL", "env-model",
                "NACOS_COPILOT_CONNECTIONS_ALPHA_API_KEY", "env-key",
                "NACOS_COPILOT_CONNECTIONS_ALPHA_ENABLED", "false")));
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader();
        
        List<CopilotModelConnection> connections = reader.read(context(environment));
        
        assertEquals(1, connections.size());
        assertEquals("env-model", connections.get(0).getModel());
        assertEquals("env-key", connections.get(0).getApiKey());
        assertFalse(connections.get(0).isEnabled());
        assertEquals(CredentialSource.DEPLOYMENT, connections.get(0).getCredentialSource());
    }
    
    @Test
    void testEmptyEnvironmentValueBlocksDeclaredValue() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.alpha.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.alpha.model", "file-model")
            .withProperty("nacos.copilot.connections.alpha.apiKey", "file-key");
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("env",
            Map.of("NACOS_COPILOT_CONNECTIONS_ALPHA_MODEL", "",
                "NACOS_COPILOT_CONNECTIONS_ALPHA_API_KEY", "")));
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader();
        
        List<CopilotModelConnection> connections = reader.read(context(environment));
        
        assertEquals(1, connections.size());
        assertEquals("", connections.get(0).getModel());
        assertEquals("", connections.get(0).getApiKey());
        assertNull(connections.get(0).getCredentialSource());
    }
    
    @Test
    void testJvmPropertiesWinOverEnvironmentVariables() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.alpha.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.alpha.model", "file-model");
        environment.getPropertySources().addLast(new SystemEnvironmentPropertySource("env",
            Map.of("NACOS_COPILOT_CONNECTIONS_ALPHA_MODEL", "env-model")));
        environment.getPropertySources().addFirst(new MapPropertySource("systemProperties",
            Map.of("nacos.copilot.connections.alpha.model", "jvm-model")));
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader();
        
        assertEquals("jvm-model", reader.read(context(environment)).get(0).getModel());
    }
    
    @Test
    void testEnvironmentOverrideResolvesPlaceholders() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("MODEL", "qwen-max")
            .withProperty("nacos.copilot.connections.alpha.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.alpha.model", "file-model");
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("env",
            Map.of("NACOS_COPILOT_CONNECTIONS_ALPHA_MODEL", "${MODEL}")));
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader();
        
        assertEquals("qwen-max", reader.read(context(environment)).get(0).getModel());
    }
    
    @Test
    void testOnlyConnectionsUnderThePrefixAreRead() {
        MockEnvironment environment = new MockEnvironment()
            .withProperty("nacos.copilot.connections.alpha.providerId", "dashscope")
            .withProperty("nacos.copilot.connections.alpha.model", "qwen-max")
            .withProperty("nacos.copilot.other.providerId", "dashscope")
            .withProperty("nacos.copilot.other.model", "qwen-plus")
            .withProperty("nacos.copilot.connections.providerId", "dashscope");
        DeploymentConfigProviderReader reader = new DeploymentConfigProviderReader();
        
        List<CopilotModelConnection> connections = reader.read(context(environment));
        
        assertEquals(1, connections.size());
        assertEquals("alpha", connections.get(0).getName());
        assertEquals("qwen-max", connections.get(0).getModel());
    }
    
    private Map<String, CopilotModelConnection> byName(List<CopilotModelConnection> connections) {
        Map<String, CopilotModelConnection> result = new HashMap<>();
        for (CopilotModelConnection connection : connections) {
            result.put(connection.getName(), connection);
        }
        return result;
    }
}
