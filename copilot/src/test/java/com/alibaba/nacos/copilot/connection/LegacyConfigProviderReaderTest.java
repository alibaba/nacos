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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Test for LegacyConfigProviderReader.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class LegacyConfigProviderReaderTest {
    
    @Mock
    private Environment environment;
    
    private LegacyConfigProviderReader reader;
    
    @BeforeEach
    void setUp() {
        reader = new LegacyConfigProviderReader(environment);
    }
    
    @Test
    void testOrder() {
        assertEquals(100, reader.order());
    }
    
    @Test
    void testReadUsesConfigurationFromContext() {
        CopilotProperties config = new CopilotProperties();
        config.setModel("qwen-turbo");
        config.setApiKey("config-key");
        when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn(null);
        
        List<CopilotModelConnection> connections = reader.read(new ProviderReadContext(config));
        
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
    void testReadPrefersEnvironmentApiKey() {
        CopilotProperties config = new CopilotProperties();
        config.setModel("qwen-turbo");
        config.setApiKey("config-key");
        when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn("env-key");
        
        CopilotModelConnection connection = reader.read(new ProviderReadContext(config)).get(0);
        
        assertEquals("env-key", connection.getApiKey());
    }
    
    @Test
    void testReadBlocksEnvironmentApiKeyWhenConsoleClearedIt() {
        CopilotProperties config = new CopilotProperties();
        config.setModel("qwen-turbo");
        config.setApiKey("");
        config.setApiKeyCleared(Boolean.TRUE);
        lenient().when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn("env-key");
        
        CopilotModelConnection connection = reader.read(new ProviderReadContext(config)).get(0);
        
        assertEquals("", connection.getApiKey());
    }
    
    @Test
    void testReadUsesDeploymentApiKeyWhenConsoleClearedStoredKey() {
        CopilotProperties config = new CopilotProperties();
        config.setModel("qwen-turbo");
        config.setApiKey("deploy-key");
        config.setApiKeyCleared(Boolean.TRUE);
        lenient().when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn("env-key");
        
        CopilotModelConnection connection = reader.read(new ProviderReadContext(config)).get(0);
        
        assertEquals("deploy-key", connection.getApiKey());
    }
    
    @Test
    void testReadReportsEnvironmentCredentialSource() {
        CopilotProperties config = new CopilotProperties();
        config.setModel("qwen-turbo");
        config.setApiKey("config-key");
        when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn("env-key");
        
        CopilotModelConnection connection = reader.read(new ProviderReadContext(config)).get(0);
        
        assertEquals("env-key", connection.getApiKey());
        assertEquals(CredentialSource.ENVIRONMENT, connection.getCredentialSource());
    }
    
    @Test
    void testReadReportsConfigurationCredentialSource() {
        CopilotProperties config = new CopilotProperties();
        config.setModel("qwen-turbo");
        config.setApiKey("config-key");
        when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn(null);
        
        CopilotModelConnection connection = reader.read(new ProviderReadContext(config)).get(0);
        
        assertEquals(CredentialSource.CONFIGURATION, connection.getCredentialSource());
    }
    
    @Test
    void testReadReportsConfigurationCredentialSourceForDeploymentKeyWhenConsoleCleared() {
        CopilotProperties config = new CopilotProperties();
        config.setModel("qwen-turbo");
        config.setApiKey("deploy-key");
        config.setApiKeyCleared(Boolean.TRUE);
        lenient().when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn("env-key");
        
        CopilotModelConnection connection = reader.read(new ProviderReadContext(config)).get(0);
        
        assertEquals(CredentialSource.CONFIGURATION, connection.getCredentialSource());
    }
    
    @Test
    void testReadWithoutApiKeyReportsNoCredentialSource() {
        CopilotProperties config = new CopilotProperties();
        config.setModel("qwen-turbo");
        config.setApiKey("");
        config.setApiKeyCleared(Boolean.TRUE);
        lenient().when(environment.getProperty(eq("COPILOT_API_KEY"))).thenReturn("env-key");
        
        CopilotModelConnection connection = reader.read(new ProviderReadContext(config)).get(0);
        
        assertNull(connection.getCredentialSource());
    }
}
