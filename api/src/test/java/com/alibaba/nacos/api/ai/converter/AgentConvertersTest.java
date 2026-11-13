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

package com.alibaba.nacos.api.ai.converter;

import com.alibaba.nacos.api.ai.AgentService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSet;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.utils.AgentWatchLogUtils;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import com.alibaba.nacos.api.utils.json.JsonUtilsTestHelper;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.IOException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class AgentConvertersTest {
    
    private static final String DIGEST =
        "sha256:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    
    private static final String REVISION = "murmur3-x64-128-v1:0123456789abcdef0123456789abcdef";
    
    private final AgentConverter<AgentCard> converter = AgentConverters.a2a();
    
    @BeforeEach
    void setUpJsonAdapter() {
        // API deliberately has no production JSON provider dependency. Exercise real JSON
        // through the SPI here; SDK IT covers the shipped Jackson 2 and Jackson 3 providers.
        ObjectMapper mapper =
            new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        NacosJsonAdapter adapter = mock(NacosJsonAdapter.class);
        when(adapter.name()).thenReturn("jackson2");
        when(adapter.isAvailable()).thenReturn(true);
        when(adapter.toJson(any())).thenAnswer(invocation -> {
            try {
                return mapper.writeValueAsString(invocation.getArgument(0));
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot serialize test JSON", e);
            }
        });
        when(adapter.toObj(anyString(), eq(AgentCard.class))).thenAnswer(invocation -> {
            try {
                return mapper.readValue((String) invocation.getArgument(0), AgentCard.class);
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot parse test AgentCard", e);
            }
        });
        JsonUtilsTestHelper.useAdapter(adapter);
    }
    
    @AfterEach
    void resetJsonAdapter() {
        JsonUtilsTestHelper.reset();
    }
    
    @Test
    void factoryAndDefaultGetterHaveSameOfflineContract() {
        AgentService service = mock(AgentService.class, CALLS_REAL_METHODS);
        AgentDiscoveryResult snapshot = snapshot();
        assertEquals(JsonUtils.toJson(converter.convert(snapshot)),
            JsonUtils.toJson(service.a2aConverter().convert(snapshot)));
        verify(service).a2aConverter();
        verifyNoMoreInteractions(service);
    }
    
    @Test
    void selectsA2aFromMultipleProtocolsAndPreservesDescriptorFields() {
        AgentDiscoveryResult snapshot = snapshot();
        AgentCallInterface other = new AgentCallInterface();
        other.setProtocol("mcp");
        other.setDescriptorMediaType("application/json");
        other.setNativeDescriptor(Collections.singletonMap("other", "protocol"));
        other.setEndpointSets(Collections.emptyList());
        snapshot.setCallInterfaces(Arrays.asList(other, call(snapshot)));
        String before = JsonUtils.toJson(snapshot);
        AgentCard card = converter.convert(snapshot);
        assertEquals("sample", card.getName());
        assertEquals("1.0.0", card.getVersion());
        assertEquals("original description", card.getDescription());
        assertTrue(card.getCapabilities().getStreaming());
        assertEquals("Nacos", card.getProvider().getOrganization());
        assertEquals("skill", card.getSkills().get(0).getId());
        assertEquals(Collections.singletonList("text"), card.getDefaultInputModes());
        assertEquals(Collections.singletonList("read"), card.getSecurity().get(0).get("oauth"));
        assertEquals("https://docs.example/agent", card.getDocumentationUrl());
        assertEquals(before, JsonUtils.toJson(snapshot));
    }
    
    @ParameterizedTest
    @EnumSource(EndpointSource.class)
    void respectsSourceOrderAndFallsThroughOnlyToRetainedSources(EndpointSource preferred) {
        AgentDiscoveryResult snapshot = snapshot();
        EndpointSet runtime = set(EndpointSource.RUNTIME, "https://runtime.example:443/rpc");
        EndpointSet declared = set(EndpointSource.DECLARED, "https://declared.example:443/rpc");
        List<EndpointSet> sets = preferred == EndpointSource.RUNTIME
            ? Arrays.asList(runtime, declared) : Arrays.asList(declared, runtime);
        call(snapshot).setEndpointSets(sets);
        assertEquals(sets.get(0).getEndpoints().get(0).getUri(),
            converter.convert(snapshot).getUrl());
        assertEquals(1, converter.convert(snapshot).getSupportedInterfaces().size());
        sets.get(0).setEndpoints(Collections.emptyList());
        assertEquals(sets.get(1).getEndpoints().get(0).getUri(),
            converter.convert(snapshot).getUrl());
        call(snapshot).setEndpointSets(Collections.singletonList(sets.get(0)));
        assertFailure(snapshot, "No addresses");
        call(snapshot).setEndpointSets(Collections.singletonList(sets.get(1)));
        assertEquals(sets.get(1).getEndpoints().get(0).getUri(),
            converter.convert(snapshot).getUrl());
    }
    
    @Test
    void retainsUnhealthyAddressesAndUsesFirstInsteadOfOldPreferenceOrWeight() {
        AgentDiscoveryResult snapshot = snapshot();
        EndpointSet runtime = set(EndpointSource.RUNTIME, "https://first.example:443/rpc");
        Endpoint first = runtime.getEndpoints().get(0);
        first.setTransport("GRPC");
        first.setHealthy(false);
        first.setWeight(0D);
        first.setMetadata(
            Collections.singletonMap(AiConstants.A2a.ENDPOINT_PROTOCOL_VERSION, "1.1"));
        Endpoint second = endpoint(EndpointSource.RUNTIME, "https://second.example:443/rpc");
        second.setPriority(1);
        second.setWeight(100D);
        second.setHealthy(false);
        second.setMetadata(Collections.singletonMap(AiConstants.A2a.ENDPOINT_TENANT, "tenant-a"));
        runtime.setEndpoints(Arrays.asList(first, second));
        call(snapshot).setEndpointSets(Arrays.asList(runtime, set(EndpointSource.DECLARED,
            "https://healthy.example:443/rpc")));
        AgentCard card = converter.convert(snapshot);
        assertEquals("GRPC", card.getPreferredTransport());
        assertEquals(first.getUri(), card.getUrl());
        assertEquals("1.1", card.getProtocolVersion());
        assertEquals("1.0", card.getSupportedInterfaces().get(1).getProtocolVersion());
        assertEquals("tenant-a", card.getSupportedInterfaces().get(1).getTenant());
        assertNull(card.getSupportedInterfaces().get(0).getTenant());
        assertEquals("GRPC", card.getSupportedInterfaces().get(0).getTransport());
        assertEquals(2, card.getSupportedInterfaces().size());
        assertEquals(card.getSupportedInterfaces(), card.getAdditionalInterfaces());
        assertNotSame(card.getSupportedInterfaces(), card.getAdditionalInterfaces());
        // A filtered snapshot must not recover the first interface from the descriptor.
        runtime.setEndpoints(Collections.singletonList(second));
        assertEquals(second.getUri(), converter.convert(snapshot).getUrl());
        assertEquals(1, converter.convert(snapshot).getSupportedInterfaces().size());
    }
    
    @Test
    void convertsLegacyDescriptorWithoutSharingMutableInputOrOutput() {
        AgentDiscoveryResult snapshot = snapshot();
        String before = JsonUtils.toJson(snapshot);
        AgentCard first = converter.convert(snapshot);
        AgentCard second = converter.convert(snapshot);
        first.getCapabilities().setStreaming(false);
        first.getSkills().get(0).setDescription("changed");
        first.getSecurity().get(0).get("oauth").clear();
        first.getSupportedInterfaces().get(0).setUrl("https://changed.example:443/rpc");
        first.getAdditionalInterfaces().clear();
        assertEquals(1, first.getSupportedInterfaces().size());
        assertTrue(second.getCapabilities().getStreaming());
        assertEquals("skill description", second.getSkills().get(0).getDescription());
        assertEquals(Collections.singletonList("read"), second.getSecurity().get(0).get("oauth"));
        assertEquals("https://runtime.example:443/rpc", second.getUrl());
        assertEquals(before, JsonUtils.toJson(snapshot));
        call(snapshot).getEndpointSets().get(0).getEndpoints().get(0)
            .setUri("https://new.example:443/rpc");
        assertEquals("https://new.example:443/rpc", converter.convert(snapshot).getUrl());
    }
    
    @Test
    void reusableConverterDoesNotMixConcurrentSnapshots() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<AgentCard>> tasks = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                final int index = i;
                tasks.add(() -> {
                    AgentDiscoveryResult input = snapshot();
                    call(input).getEndpointSets().get(0).getEndpoints().get(0)
                        .setUri("https://agent" + index + ".example:443/rpc");
                    return converter.convert(input);
                });
            }
            List<Future<AgentCard>> results = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
            for (int i = 0; i < results.size(); i++) {
                assertEquals("https://agent" + i + ".example:443/rpc",
                    results.get(i).get().getUrl());
            }
        } finally {
            executor.shutdownNow();
        }
    }
    
    @Test
    void rejectsNullMissingProtocolAndMissingAddressesWithDistinctMessages() {
        assertFailure(null, "AgentDiscoveryResult");
        AgentDiscoveryResult snapshot = snapshot();
        call(snapshot).setProtocol("mcp");
        assertFailure(snapshot, "No A2A");
        snapshot.setCallInterfaces(Collections.emptyList());
        assertFailure(snapshot, "No A2A");
        snapshot = snapshot();
        call(snapshot).setEndpointSets(Collections.emptyList());
        assertFailure(snapshot, "No addresses");
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"name", "version"})
    void rejectsDescriptorIdentityMismatch(String field) {
        AgentDiscoveryResult snapshot = snapshot();
        Map<String, Object> descriptor = descriptor();
        descriptor.put(field, "name".equals(field) ? "other" : "2.0.0");
        call(snapshot).setNativeDescriptor(descriptor);
        assertFailure(snapshot, "nativeDescriptor." + field + " does not match");
    }
    
    @Test
    void rejectsMissingMalformedAndInvalidDescriptorsPreservingParsingCause() {
        AgentDiscoveryResult snapshot = snapshot();
        call(snapshot).setNativeDescriptor(null);
        assertFailure(snapshot, "nativeDescriptor");
        call(snapshot).setNativeDescriptor("{broken");
        assertFailure(snapshot, "JSON object");
        Map<String, Object> descriptor = descriptor();
        descriptor.put("capabilities", "not-an-object");
        call(snapshot).setNativeDescriptor(descriptor);
        IllegalArgumentException failure = assertFailure(snapshot, "cannot be decoded");
        assertNotNull(failure.getCause().getCause());
        descriptor.remove("capabilities");
        descriptor.remove("name");
        assertFailure(snapshot, "name");
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"disabled", "revision", "duplicate", "order", "metadata"})
    void rejectsIllegalHandBuiltDiscoverySnapshots(String invalid) {
        AgentDiscoveryResult snapshot = snapshot();
        EndpointSet set = call(snapshot).getEndpointSets().get(0);
        Endpoint endpoint = set.getEndpoints().get(0);
        switch (invalid) {
            case "disabled":
                endpoint.setEnabled(false);
                break;
            case "revision":
                set.setSourceRevision("invalid");
                break;
            case "duplicate":
                set.setEndpoints(Arrays.asList(endpoint, endpoint));
                break;
            case "order":
                Endpoint second = endpoint(EndpointSource.RUNTIME, "https://a.example:443/rpc");
                set.setEndpoints(Arrays.asList(endpoint, second));
                break;
            case "metadata":
                endpoint.setMetadata(
                    Collections.singletonMap(AiConstants.A2a.ENDPOINT_PROTOCOL_VERSION, ""));
                break;
            default:
                throw new AssertionError(invalid);
        }
        assertFailure(snapshot, "discovery snapshot");
    }
    
    @Test
    void logsSafeInputDiagnosticsWithoutDumpingDescriptorOrMetadata() {
        Logger logger = Logger.getLogger(AgentConverters.class.getName());
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            
            @Override
            public void publish(LogRecord record) {
                messages.add(MessageFormat.format(record.getMessage(), record.getParameters()));
            }
            
            @Override
            public void flush() {
            }
            
            @Override
            public void close() {
            }
        };
        Level previous = logger.getLevel();
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
        try {
            AgentDiscoveryResult snapshot = snapshot();
            Map<String, Object> descriptor = descriptor();
            descriptor.put("name", "other");
            descriptor.put("description", "descriptor-secret");
            call(snapshot).setNativeDescriptor(descriptor);
            call(snapshot).getEndpointSets().get(0).getEndpoints().get(0)
                .setMetadata(Collections.singletonMap("token", "metadata-secret"));
            assertFailure(snapshot, "identity");
            assertEquals(1, messages.size());
            assertTrue(messages.get(0).contains("agent=sample"));
            assertTrue(messages.get(0).contains("nativeDescriptor identity"));
            assertFalse(messages.get(0).contains("descriptor-secret"));
            assertFalse(messages.get(0).contains("metadata-secret"));
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previous);
        }
    }
    
    @Test
    void diagnosticRenderingFailureNeverMasksConversionFailure() {
        try (MockedStatic<AgentWatchLogUtils> logs = mockStatic(AgentWatchLogUtils.class)) {
            logs.when(() -> AgentWatchLogUtils.describeResult(null))
                .thenThrow(new IllegalStateException("diagnostic failure"));
            assertFailure(null, "AgentDiscoveryResult");
        }
    }
    
    private IllegalArgumentException assertFailure(AgentDiscoveryResult source, String reason) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> converter.convert(source));
        assertTrue(error.getMessage().contains(reason), error.getMessage());
        assertTrue(error.getMessage().contains(source == null ? "null" : "sample/1.0.0"));
        return error;
    }
    
    private static AgentDiscoveryResult snapshot() {
        AgentDiscoveryResult result = new AgentDiscoveryResult();
        result.setNamespaceId("public");
        result.setAgentName("sample");
        result.setVersion("1.0.0");
        result.setContentDigest(DIGEST);
        AgentCallInterface call = new AgentCallInterface();
        call.setProtocol("a2a");
        call.setProtocolVersion("1.0");
        call.setDescriptorMediaType("application/json");
        call.setNativeDescriptor(descriptor());
        call.setEndpointSets(
            Arrays.asList(set(EndpointSource.RUNTIME, "https://runtime.example:443/rpc"),
                set(EndpointSource.DECLARED, "https://declared.example:443/rpc")));
        result.setCallInterfaces(Collections.singletonList(call));
        return result;
    }
    
    private static Map<String, Object> descriptor() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "sample");
        result.put("version", "1.0.0");
        result.put("url", "https://old.example:443/rpc");
        result.put("preferredTransport", "JSONRPC");
        result.put("protocolVersion", "1.0");
        result.put("description", "original description");
        result.put("capabilities", Collections.singletonMap("streaming", true));
        result.put("provider", Collections.singletonMap("organization", "Nacos"));
        result.put("defaultInputModes", Collections.singletonList("text"));
        Map<String, Object> skill = new LinkedHashMap<>();
        skill.put("id", "skill");
        skill.put("description", "skill description");
        result.put("skills", Collections.singletonList(skill));
        result.put("security", Collections.singletonList(Collections.singletonMap("oauth",
            Collections.singletonList("read"))));
        result.put("documentationUrl", "https://docs.example/agent");
        return result;
    }
    
    private static AgentCallInterface call(AgentDiscoveryResult source) {
        return source.getCallInterfaces().get(source.getCallInterfaces().size() - 1);
    }
    
    private static EndpointSet set(EndpointSource source, String uri) {
        EndpointSet result = new EndpointSet();
        result.setSource(source);
        result.setSourceRevision(source == EndpointSource.RUNTIME ? REVISION : DIGEST);
        result.setEndpoints(Collections.singletonList(endpoint(source, uri)));
        return result;
    }
    
    private static Endpoint endpoint(EndpointSource source, String uri) {
        Endpoint result = new Endpoint();
        result.setUri(uri);
        result.setTransport("JSONRPC");
        if (source == EndpointSource.RUNTIME) {
            RuntimeVersionBinding binding = new RuntimeVersionBinding();
            binding.setRuntimeVersion("1.0.0");
            binding.setVersionRange("[1.0.0]");
            result.setBindings(Collections.singletonList(binding));
        }
        return result;
    }
}
