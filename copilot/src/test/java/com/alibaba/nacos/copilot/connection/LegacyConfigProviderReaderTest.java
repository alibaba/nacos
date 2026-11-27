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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for LegacyConfigProviderReader.
 *
 * @author nacos
 */
class LegacyConfigProviderReaderTest {
    
    private final LegacyConfigProviderReader reader = new LegacyConfigProviderReader();
    
    @Test
    void testOrder() {
        assertEquals(100, reader.order());
    }
    
    @Test
    void testReadUsesConfigurationFromContext() {
        CopilotProperties config = configWithKey("config-key");
        List<CopilotModelConnection> connections =
            reader.read(context(config, CredentialSource.CONSOLE));
        
        assertEquals(1, connections.size());
        CopilotModelConnection connection = connections.get(0);
        assertEquals(LegacyConfigProviderReader.CONNECTION_NAME, connection.getName());
        assertEquals("dashscope:qwen-turbo", connection.getModelId());
        assertEquals("config-key", connection.getApiKey());
        assertEquals("Legacy DashScope", connection.getDisplayName());
        assertTrue(connection.isEnabled());
        assertTrue(connection.getStream());
        assertTrue(connection.getEnableThinking());
        assertEquals(ConnectionSource.LEGACY, connection.getSource());
        assertEquals(100, connection.getPriority());
    }
    
    @Test
    void testReadPrefersStoredApiKeyOverCapturedEnvironment() {
        CopilotModelConnection connection = reader.read(
            context(configWithKey("config-key"), CredentialSource.CONSOLE)).get(0);
        
        assertEquals("config-key", connection.getApiKey());
    }
    
    @Test
    void testReadBlocksEnvironmentApiKeyWhenConsoleClearedIt() {
        CopilotProperties config = configWithKey(null);
        config.setApiKeyCleared(Boolean.TRUE);
        
        CopilotModelConnection connection = reader.read(context(config, null)).get(0);
        
        assertNull(connection.getApiKey());
    }
    
    @Test
    void testReadUsesDeploymentApiKeyWhenConsoleClearedStoredKey() {
        // The manager has already resolved the lower clear against the higher deployment key.
        CopilotModelConnection connection = reader.read(
            context(configWithKey("deploy-key"), CredentialSource.DEPLOYMENT)).get(0);
        
        assertEquals("deploy-key", connection.getApiKey());
    }
    
    @Test
    void testReadReportsEnvironmentCredentialSource() {
        CopilotModelConnection connection = reader.read(
            context(configWithKey(null), CredentialSource.ENVIRONMENT)).get(0);
        
        assertEquals("env-key", connection.getApiKey());
        assertEquals(CredentialSource.ENVIRONMENT, connection.getCredentialSource());
    }
    
    @Test
    void testReadReportsConsoleCredentialSourceForStoredKey() {
        CopilotModelConnection connection = reader.read(
            context(configWithKey("config-key"), CredentialSource.CONSOLE)).get(0);
        
        assertEquals(CredentialSource.CONSOLE, connection.getCredentialSource());
    }
    
    @Test
    void testReadReportsDeploymentCredentialSourceForDeploymentKeyWhenConsoleCleared() {
        CopilotModelConnection connection = reader.read(
            context(configWithKey("deploy-key"), CredentialSource.DEPLOYMENT)).get(0);
        
        assertEquals(CredentialSource.DEPLOYMENT, connection.getCredentialSource());
    }
    
    @Test
    void testReadReportsDeploymentCredentialSourceBeforeEnvironmentKey() {
        CopilotModelConnection connection = reader.read(
            context(configWithKey("deploy-key"), CredentialSource.DEPLOYMENT)).get(0);
        
        assertEquals("deploy-key", connection.getApiKey());
        assertEquals(CredentialSource.DEPLOYMENT, connection.getCredentialSource());
    }
    
    @Test
    void testReadWithoutApiKeyReportsNoCredentialSource() {
        CopilotProperties config = configWithKey(null);
        config.setApiKeyCleared(Boolean.TRUE);
        
        CopilotModelConnection connection = reader.read(context(config, null)).get(0);
        
        assertNull(connection.getCredentialSource());
    }
    
    private CopilotProperties configWithKey(String apiKey) {
        CopilotProperties config = new CopilotProperties();
        config.setModel("qwen-turbo");
        config.setApiKey(apiKey);
        return config;
    }
    
    private ProviderReadContext context(CopilotProperties config, CredentialSource source) {
        MockEnvironment environment =
            new MockEnvironment().withProperty("COPILOT_API_KEY", "env-key");
        return new ProviderReadContext(ProviderReadContext.captureDeployment(environment),
            config, true, source);
    }
}
