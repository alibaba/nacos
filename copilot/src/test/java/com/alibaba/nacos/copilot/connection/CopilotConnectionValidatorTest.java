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

import io.agentscope.core.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test for CopilotConnectionValidator.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class CopilotConnectionValidatorTest {
    
    @Mock
    private CopilotModelResolver modelResolver;
    
    private final AtomicReference<String> allowlist = new AtomicReference<>("");
    
    private InetAddress[] resolvedAddresses;
    
    private UnknownHostException resolveFailure;
    
    private CopilotConnectionValidator validator;
    
    @BeforeEach
    void setUp() {
        lenient().when(modelResolver.canResolve(any())).thenReturn(true);
        validator = new CopilotConnectionValidator(modelResolver, allowlist::get, host -> {
            if (resolveFailure != null) {
                throw resolveFailure;
            }
            return resolvedAddresses;
        });
    }
    
    @Test
    void testMissingProviderIdIsUnavailable() {
        CopilotModelConnection connection = CopilotModelConnection.builder()
            .name("broken")
            .model("qwen-max")
            .build();
        
        CopilotModelConnection validated = validator.validate(connection);
        
        assertFalse(validated.isAvailable());
        assertTrue(validated.getUnavailableReason().contains("providerId"));
    }
    
    @Test
    void testMissingModelIsUnavailable() {
        CopilotModelConnection connection = CopilotModelConnection.builder()
            .name("broken")
            .providerId("dashscope")
            .build();
        
        CopilotModelConnection validated = validator.validate(connection);
        
        assertFalse(validated.isAvailable());
        assertTrue(validated.getUnavailableReason().contains("model"));
    }
    
    @Test
    void testLegacyConnectionWithoutApiKeyIsUnavailable() {
        CopilotModelConnection connection = CopilotModelConnection.builder()
            .name("legacy")
            .providerId("dashscope")
            .model("qwen-max")
            .source(ConnectionSource.LEGACY)
            .build();
        
        CopilotModelConnection validated = validator.validate(connection);
        
        assertFalse(validated.isAvailable());
        assertTrue(validated.getUnavailableReason().contains("apiKey"));
        verify(modelResolver, never()).resolve(any());
    }
    
    @Test
    void testDeploymentConnectionWithoutApiKeyIsProbed() {
        when(modelResolver.resolve(any())).thenReturn(mock(Model.class));
        CopilotModelConnection connection = CopilotModelConnection.builder()
            .name("local-ollama")
            .providerId("ollama")
            .model("qwen2.5")
            .source(ConnectionSource.DEPLOYMENT)
            .build();
        
        CopilotModelConnection validated = validator.validate(connection);
        
        assertTrue(validated.isAvailable());
        assertNull(validated.getUnavailableReason());
        verify(modelResolver).resolve(connection);
    }
    
    @Test
    void testUnresolvableProviderIsUnavailableWithoutProbe() {
        when(modelResolver.canResolve(any())).thenReturn(false);
        CopilotModelConnection connection = CopilotModelConnection.builder()
            .name("prod-openai")
            .providerId("openai")
            .model("gpt-4")
            .apiKey("key")
            .source(ConnectionSource.DEPLOYMENT)
            .build();
        
        CopilotModelConnection validated = validator.validate(connection);
        
        assertFalse(validated.isAvailable());
        assertTrue(validated.getUnavailableReason().contains("openai:gpt-4"));
        verify(modelResolver, never()).resolve(any());
    }
    
    @Test
    void testResolveFailureReasonIsPreserved() {
        when(modelResolver.resolve(any()))
            .thenThrow(new IllegalArgumentException("No provider for openai:gpt-4"));
        CopilotModelConnection connection = CopilotModelConnection.builder()
            .name("prod-openai")
            .providerId("openai")
            .model("gpt-4")
            .apiKey("key")
            .source(ConnectionSource.DEPLOYMENT)
            .build();
        
        CopilotModelConnection validated = validator.validate(connection);
        
        assertFalse(validated.isAvailable());
        assertEquals("No provider for openai:gpt-4", validated.getUnavailableReason());
    }
    
    @Test
    void testExplicitHttpBaseUrlIsRejected() throws UnknownHostException {
        resolvedAddresses = new InetAddress[] {InetAddress.getByName("8.8.8.8")};
        CopilotModelConnection connection = deploymentConnection("http://example.com");
        
        CopilotModelConnection validated = validator.validate(connection);
        
        assertFalse(validated.isAvailable());
        assertTrue(validated.getUnavailableReason().contains("https"));
    }
    
    @Test
    void testAllowlistedHostMayUseHttp() throws UnknownHostException {
        allowlist.set("example.com");
        resolvedAddresses = new InetAddress[] {InetAddress.getByName("10.0.0.5")};
        when(modelResolver.resolve(any())).thenReturn(mock(Model.class));
        CopilotModelConnection connection = deploymentConnection("http://llm.example.com");
        
        CopilotModelConnection validated = validator.validate(connection);
        
        assertTrue(validated.isAvailable());
    }
    
    @Test
    void testPrivateHostIsRejected() throws UnknownHostException {
        resolvedAddresses = new InetAddress[] {InetAddress.getByName("10.0.0.5")};
        CopilotModelConnection connection = deploymentConnection("https://llm.example.com");
        
        CopilotModelConnection validated = validator.validate(connection);
        
        assertFalse(validated.isAvailable());
        assertTrue(validated.getUnavailableReason().contains("private or local"));
    }
    
    @Test
    void testLoopbackHostIsRejected() throws UnknownHostException {
        resolvedAddresses = new InetAddress[] {InetAddress.getLoopbackAddress()};
        CopilotModelConnection connection = deploymentConnection("https://llm.example.com");
        
        CopilotModelConnection validated = validator.validate(connection);
        
        assertFalse(validated.isAvailable());
        assertTrue(validated.getUnavailableReason().contains("private or local"));
    }
    
    @Test
    void testInvalidBaseUrlIsRejected() {
        CopilotModelConnection connection = deploymentConnection("not a url");
        
        CopilotModelConnection validated = validator.validate(connection);
        
        assertFalse(validated.isAvailable());
        assertTrue(validated.getUnavailableReason().contains("valid URI"));
    }
    
    @Test
    void testUnresolvableHostIsRejected() {
        resolveFailure = new UnknownHostException("llm.example.com");
        CopilotModelConnection connection = deploymentConnection("https://llm.example.com");
        
        CopilotModelConnection validated = validator.validate(connection);
        
        assertFalse(validated.isAvailable());
        assertTrue(validated.getUnavailableReason().contains("can not be resolved"));
    }
    
    @Test
    void testPublicHttpsHostIsAvailable() throws UnknownHostException {
        resolvedAddresses = new InetAddress[] {InetAddress.getByName("8.8.8.8")};
        when(modelResolver.resolve(any())).thenReturn(mock(Model.class));
        CopilotModelConnection connection = deploymentConnection("https://llm.example.com");
        
        CopilotModelConnection validated = validator.validate(connection);
        
        assertTrue(validated.isAvailable());
    }
    
    private CopilotModelConnection deploymentConnection(String baseUrl) {
        return CopilotModelConnection.builder()
            .name("prod-qwen")
            .providerId("dashscope")
            .model("qwen-max")
            .apiKey("key")
            .baseUrl(baseUrl)
            .source(ConnectionSource.DEPLOYMENT)
            .build();
    }
}
