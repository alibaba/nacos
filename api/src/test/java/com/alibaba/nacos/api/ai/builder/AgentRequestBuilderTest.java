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

package com.alibaba.nacos.api.ai.builder;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.a2a.AgentSkill;
import com.alibaba.nacos.api.ai.model.a2a.SecurityRequirement;
import com.alibaba.nacos.api.ai.model.a2a.StringList;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.RuntimeVersionBinding;
import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;
import com.alibaba.nacos.api.ai.utils.A2aAgentCardUtils;
import com.alibaba.nacos.api.ai.utils.A2aRequestUtils;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.alibaba.nacos.api.utils.json.JsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

class AgentRequestBuilderTest extends BasicRequestTest {
    
    private MockedStatic<JsonUtils> json;
    
    @BeforeEach
    void bindCodec() {
        // API has no runtime adapter. Exercise real JSON round trips through its test mapper.
        json = mockStatic(JsonUtils.class);
        json.when(() -> JsonUtils.toObj(anyString(), any(Class.class)))
            .thenAnswer(invocation -> mapper.readValue((String) invocation.getArgument(0),
                (Class<?>) invocation.getArgument(1)));
        json.when(() -> JsonUtils.toJson(any()))
            .thenAnswer(invocation -> mapper.writeValueAsString(invocation.getArgument(0)));
    }
    
    @AfterEach
    void closeCodec() {
        json.close();
    }
    
    @Test
    void nativePublicationPreservesCardAndIndependentBuilds() throws Exception {
        AgentCard card = card();
        final String original = mapper.writeValueAsString(card);
        AgentRequestBuilder.A2aPublishAgentBuilder builder = AgentRequestBuilder.publishAgent()
            .autoSubmit(true).a2a().fromAgentCard(card).description("presentation override");
        AgentPublishRequest first = builder.build();
        assertEquals("demo", first.getAgentName());
        assertEquals("1.2.0", first.getVersion());
        assertTrue(first.isAutoSubmit());
        assertEquals("presentation override", first.getDescription());
        assertEquals("organization", first.getProvider().getName());
        assertEquals("https://docs.example/icon", first.getIconUrl());
        AgentCallInterface call = first.getCallInterfaces().get(0);
        assertEquals("a2a", call.getProtocol());
        assertEquals("1.0.0", call.getProtocolVersion());
        assertEquals("application/json", call.getDescriptorMediaType());
        assertEquals(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED),
            call.getEndpointSourceOrder());
        assertEquals("native description",
            ((Map<?, ?>) call.getNativeDescriptor()).get("description"));
        List<Endpoint> endpoints = call.getEndpointSets().get(0).getEndpoints();
        assertEndpoint(endpoints.get(0), "https://first.example:443/rpc?x=1", "1.0.0", "", 0);
        assertEndpoint(endpoints.get(1), "http://second.example:80/rpc", "1.1.0", "tenant-a", 1);
        assertNull(endpoints.get(0).getBindings());
        endpoints.get(0).getMetadata().clear();
        ((Map<?, ?>) call.getNativeDescriptor()).clear();
        card.getSupportedInterfaces().clear();
        assertEquals(2, builder.build().getCallInterfaces().get(0).getEndpointSets().get(0)
            .getEndpoints().size());
        assertFalse(((Map<?, ?>) builder.build().getCallInterfaces().get(0).getNativeDescriptor())
            .isEmpty());
        assertTrue(original.contains("first.example"));
    }
    
    @Test
    void inputCardIsUnchangedByNormalization() throws Exception {
        AgentCard card = card();
        final String original = mapper.writeValueAsString(card);
        AgentRequestBuilder.publishAgent().a2a().fromAgentCard(card).build();
        assertEquals(original, mapper.writeValueAsString(card));
        assertNull(card.getUrl());
        assertNull(card.getSupportedInterfaces().get(0).getTransport());
    }
    
    @Test
    void sourcePreferenceCanBeSetBeforeOrAfterCard() {
        List<EndpointSource> preference =
            new ArrayList<>(Arrays.asList(EndpointSource.DECLARED, EndpointSource.RUNTIME));
        AgentRequestBuilder.A2aPublishAgentBuilder builder =
            AgentRequestBuilder.publishAgent().a2a()
                .endpointSourceOrder(preference).fromAgentCard(card());
        preference.clear();
        assertEquals(EndpointSource.DECLARED,
            builder.build().getCallInterfaces().get(0).getEndpointSourceOrder().get(0));
        builder.endpointSourceOrder(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
        assertEquals(EndpointSource.RUNTIME,
            builder.build().getCallInterfaces().get(0).getEndpointSourceOrder().get(0));
        assertFalse(builder.build().isAutoSubmit());
    }
    
    @Test
    void genericPublicationCopiesEveryMutableInputAndSupportsDraftBase() {
        AgentProvider provider = new AgentProvider();
        provider.setName("provider");
        List<String> tags = new ArrayList<>(Collections.singletonList("tag"));
        Map<String, Object> extensions = new HashMap<>();
        extensions.put("nested", new ArrayList<>(tags));
        List<AgentCallInterface> calls = new ArrayList<>(Collections.singletonList(call()));
        AgentRequestBuilder.PublishAgentBuilder builder = AgentRequestBuilder.publishAgent()
            .agentName("demo").version("1.2.0").displayName("Display").description("description")
            .iconUrl("https://docs.example/icon").provider(provider).tags(tags)
            .extensions(extensions)
            .author("author").changeDescription("change").autoSubmit(true).callInterfaces(calls);
        provider.setName("mutated");
        tags.clear();
        ((List<?>) extensions.get("nested")).clear();
        calls.get(0).setNativeDescriptor(null);
        AgentPublishRequest first = builder.build();
        assertEquals("Display", first.getDisplayName());
        assertEquals("description", first.getDescription());
        assertEquals("author", first.getAuthor());
        assertEquals("change", first.getChangeDescription());
        assertEquals("provider", first.getProvider().getName());
        assertEquals(Collections.singletonList("tag"), first.getTags());
        assertEquals(Collections.singletonList("tag"), first.getExtensions().get("nested"));
        assertEquals("custom", first.getCallInterfaces().get(0).getProtocol());
        first.getTags().clear();
        assertEquals(Collections.singletonList("tag"), builder.build().getTags());
        AgentPublishRequest derived = AgentRequestBuilder.publishAgent().agentName("demo")
            .version("2.0.0").basedOnVersion("1.2.0").provider(null).tags(null).extensions(null)
            .callInterfaces(null).build();
        assertNull(derived.getCallInterfaces());
        assertEquals("1.2.0", derived.getBasedOnVersion());
    }
    
    @Test
    void protocolSelectionDoesNotShareTheParentBuilder() {
        AgentRequestBuilder.PublishAgentBuilder parent =
            AgentRequestBuilder.publishAgent().agentName("demo");
        AgentRequestBuilder.A2aPublishAgentBuilder selected = parent.a2a().fromAgentCard(card());
        parent.agentName("changed");
        assertEquals("demo", selected.build().getAgentName());
        AgentRequestBuilder.RegisterEndpointsBuilder runtime =
            AgentRequestBuilder.registerEndpoints()
                .agentName("demo").runtimeVersion("1.2.0");
        AgentRequestBuilder.A2aRegisterEndpointsBuilder a2a =
            runtime.a2a().fromAgentInterfaces(card().getSupportedInterfaces());
        runtime.agentName("changed");
        assertEquals("demo", a2a.build().getAgentName());
    }
    
    @Test
    void cardDefaultsCanBeReplacedByActualInstanceInterfaces() {
        AgentCard card = card();
        AgentRequestBuilder.A2aRegisterEndpointsBuilder builder =
            AgentRequestBuilder.registerEndpoints()
                .a2a().fromAgentCard(card);
        assertEquals(2, builder.build().getEndpoints().size());
        AgentInterface actual = address("https://node.example/run", "2.0.0", null);
        builder.fromAgentInterfaces(Collections.singletonList(actual));
        actual.setUrl("relative");
        card.setName("changed");
        AgentEndpointRegistrationBatch batch = builder.build();
        assertEquals("demo", batch.getAgentName());
        assertEquals("a2a", batch.getProtocol());
        assertEquals("1.2.0", batch.getRuntimeVersion());
        assertNull(batch.getVersionRange());
        assertEquals(1, batch.getEndpoints().size());
        Endpoint endpoint = batch.getEndpoints().get(0);
        assertEndpoint(endpoint, "https://node.example:443/run", "2.0.0", null, 0);
        assertEquals("[1.2.0]", endpoint.getBindings().get(0).getVersionRange());
        endpoint.getBindings().get(0).setRuntimeVersion("9.0.0");
        endpoint.getMetadata().clear();
        assertEquals("1.2.0",
            builder.build().getEndpoints().get(0).getBindings().get(0).getRuntimeVersion());
        assertEquals(2, card.getSupportedInterfaces().size());
    }
    
    @Test
    void identityOnlyCardAndExplicitRangeDoNotRequireFullDescriptor() {
        AgentCard identity = new AgentCard();
        identity.setName("demo");
        identity.setVersion("1.2.0");
        AgentEndpointRegistrationBatch batch = AgentRequestBuilder.registerEndpoints().a2a()
            .fromAgentCard(identity).fromAgentInterfaces(card().getSupportedInterfaces())
            .versionRange("[1.0.0,2.0.0)").build();
        assertEquals("[1.0.0,2.0.0)",
            batch.getEndpoints().get(1).getBindings().get(0).getVersionRange());
        assertNull(identity.getSupportedInterfaces());
        assertThrows(IllegalArgumentException.class,
            () -> AgentRequestBuilder.registerEndpoints().a2a().fromAgentCard(identity).build());
    }
    
    @Test
    void genericEndpointsPreserveFlagsMetadataAndPerEndpointBindingOverrides() {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri("https://node.example/run");
        endpoint.setTransport("custom");
        endpoint.setPriority(4);
        endpoint.setWeight(0.5D);
        endpoint.setHealthy(false);
        endpoint.setEnabled(false);
        endpoint.setMetadata(new HashMap<>(Collections.singletonMap("custom", "value")));
        RuntimeVersionBinding binding = new RuntimeVersionBinding();
        binding.setRuntimeVersion("1.5.0");
        binding.setVersionRange("[1.0.0,2.0.0)");
        endpoint.setBindings(Collections.singletonList(binding));
        AgentRequestBuilder.RegisterEndpointsBuilder builder =
            AgentRequestBuilder.registerEndpoints()
                .agentName("demo").protocol("custom").runtimeVersion("1.2.0")
                .endpoints(Collections.singletonList(endpoint));
        binding.setRuntimeVersion("invalid");
        endpoint.getMetadata().clear();
        Endpoint result = builder.build().getEndpoints().get(0);
        assertEquals("1.5.0", result.getBindings().get(0).getRuntimeVersion());
        assertEquals("value", result.getMetadata().get("custom"));
        assertEquals(4, result.getPriority());
        assertEquals(0.5D, result.getWeight());
        assertFalse(result.getHealthy());
        assertFalse(result.getEnabled());
    }
    
    @Test
    void legacyProjectionRetainsMetadataOmissionFirstWinsAndZeroPriority() throws Exception {
        AgentCard card = card();
        AgentInterface duplicate = address("https://first.example/other", "9.0.0", "other");
        card.getSupportedInterfaces().add(1, duplicate);
        A2aAgentCardUtils.validateAgentCard(card);
        List<Endpoint> endpoints = A2aRequestUtils.legacyDeclaredEndpoints("ns", card);
        assertEquals(2, endpoints.size());
        assertEquals("https://first.example:443/rpc?x=1", endpoints.get(0).getUri());
        for (Endpoint endpoint : endpoints) {
            assertNull(endpoint.getMetadata());
            assertEquals(0, endpoint.getPriority());
        }
        assertNull(A2aRequestUtils
            .populateDraft(new AgentPublishRequest(), card,
                Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED),
                endpoints, false)
            .getProvider());
        card.setProvider(null);
        assertNull(A2aRequestUtils
            .populateDraft(new AgentPublishRequest(), card,
                Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED),
                endpoints, true)
            .getProvider());
        assertThrows(IllegalArgumentException.class,
            () -> AgentRequestBuilder.publishAgent().a2a().fromAgentCard(card));
    }
    
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"relative", "host:9090", "https://user:pass@host/run",
        "https://host/run#fragment"})
    void invalidUrisNeverGuessAScheme(String uri) {
        assertThrows(IllegalArgumentException.class,
            () -> AgentRequestBuilder.registerEndpoints().a2a()
                .fromAgentInterfaces(Collections.singletonList(address(uri, "1.0.0", null))));
    }
    
    @ParameterizedTest
    @MethodSource("invalidInterfaces")
    void incompleteNativeInterfacesAreRejected(AgentInterface value) {
        assertThrows(IllegalArgumentException.class,
            () -> AgentRequestBuilder.registerEndpoints().a2a()
                .fromAgentInterfaces(Collections.singletonList(value)));
    }
    
    static Stream<AgentInterface> invalidInterfaces() {
        AgentInterface missingBinding = address("https://host/run", "1.0.0", null);
        missingBinding.setProtocolBinding(null);
        missingBinding.setTransport("JSONRPC");
        return Stream.of(null, missingBinding, address("https://host/run", null, null),
            address("https://host/run", "", null), address("https://host/run", "bad version", null),
            address("https://host/run", "1.0.0", String.join("", Collections.nCopies(257, "x"))));
    }
    
    @ParameterizedTest
    @MethodSource("invalidPublications")
    void invalidPublicationInputsFailLocally(
        Consumer<AgentRequestBuilder.PublishAgentBuilder> operation) {
        assertThrows(IllegalArgumentException.class,
            () -> operation.accept(AgentRequestBuilder.publishAgent()));
    }
    
    static Stream<Consumer<AgentRequestBuilder.PublishAgentBuilder>> invalidPublications() {
        return Stream.of(b -> b.build(), b -> b.a2a().build(), b -> b.a2a().fromAgentCard(null),
            b -> b.a2a().fromAgentCard(new AgentCard()),
            b -> b.agentName("different").a2a().fromAgentCard(card()),
            b -> b.version("2.0.0").a2a().fromAgentCard(card()),
            b -> b.a2a().fromAgentCard(card()).version("2.0.0").build(),
            b -> b.a2a().fromAgentCard(card()).agentName("different").build(),
            b -> b.callInterfaces(Collections.singletonList(call())).a2a(),
            b -> b.callInterfaces(Collections.singletonList(null)),
            b -> b.agentName("demo").version("1.0.0").callInterfaces(Collections.emptyList())
                .build(),
            b -> b.agentName("demo").version("1.0.0").callInterfaces(Arrays.asList(call(), call()))
                .build(),
            b -> b.agentName("demo").version("1.0.0")
                .callInterfaces(Collections.nCopies(17, call())).build());
    }
    
    @ParameterizedTest
    @MethodSource("invalidSourceOrders")
    void invalidSourceOrdersFailLocally(List<EndpointSource> value) {
        assertThrows(IllegalArgumentException.class,
            () -> AgentRequestBuilder.publishAgent().a2a().endpointSourceOrder(value));
    }
    
    static Stream<List<EndpointSource>> invalidSourceOrders() {
        return Stream.of(null, Collections.emptyList(),
            Collections.singletonList(EndpointSource.RUNTIME),
            Arrays.asList(EndpointSource.RUNTIME, EndpointSource.RUNTIME),
            Arrays.asList(EndpointSource.DECLARED, EndpointSource.DECLARED));
    }
    
    @Test
    void legacyCardCanUseTheExistingNormalizerWithoutChangingInput() {
        AgentCard legacy = new AgentCard();
        legacy.setName("demo");
        legacy.setVersion("1.2.0");
        legacy.setUrl("https://legacy.example/rpc");
        legacy.setPreferredTransport("JSONRPC");
        legacy.setProtocolVersion("0.3");
        AgentPublishRequest result = AgentRequestBuilder.publishAgent().agentName("demo")
            .version("1.2.0").a2a().fromAgentCard(legacy).build();
        assertNull(legacy.getSupportedInterfaces());
        assertNull(result.getProvider());
        Endpoint endpoint =
            result.getCallInterfaces().get(0).getEndpointSets().get(0).getEndpoints().get(0);
        assertEndpoint(endpoint, "https://legacy.example:443/rpc", "0.3", null, 0);
        assertEquals("a2a", AgentRequestBuilder.registerEndpoints().protocol("a2a").a2a()
            .agentName("demo").runtimeVersion("1.2.0")
            .fromAgentInterfaces(card().getSupportedInterfaces()).build().getProtocol());
    }
    
    @Test
    void malformedSecurityCannotBeLostDuringCopy() {
        AgentCard card = card();
        AgentSkill skill = new AgentSkill();
        SecurityRequirement requirement = new SecurityRequirement();
        StringList scopes = new StringList();
        scopes.setList(null);
        requirement.setSchemes(Collections.singletonMap("oauth", scopes));
        skill.setSecurityRequirements(Collections.singletonList(requirement));
        card.setSkills(Collections.singletonList(skill));
        assertThrows(IllegalArgumentException.class,
            () -> AgentRequestBuilder.publishAgent().a2a().fromAgentCard(card));
        assertNull(scopes.getList());
    }
    
    @Test
    void invalidRuntimeSelectionAndBatchAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentRequestBuilder.registerEndpoints().build());
        assertThrows(IllegalArgumentException.class,
            () -> AgentRequestBuilder.registerEndpoints().protocol("other").a2a());
        Endpoint endpoint = new Endpoint();
        endpoint.setUri("http://node/run");
        endpoint.setTransport("JSONRPC");
        assertThrows(IllegalArgumentException.class, () -> AgentRequestBuilder.registerEndpoints()
            .endpoints(Collections.singletonList(endpoint)).a2a());
        assertThrows(IllegalArgumentException.class,
            () -> AgentRequestBuilder.registerEndpoints().endpoints(null));
        endpoint.setEnabled(null);
        assertThrows(IllegalArgumentException.class, () -> AgentRequestBuilder.registerEndpoints()
            .endpoints(Collections.singletonList(endpoint)));
        assertThrows(IllegalArgumentException.class,
            () -> AgentRequestBuilder.registerEndpoints().a2a().fromAgentCard(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentRequestBuilder.registerEndpoints().a2a().fromAgentInterfaces(null));
        assertThrows(IllegalArgumentException.class,
            () -> AgentRequestBuilder.registerEndpoints().a2a()
                .fromAgentInterfaces(Collections.emptyList()));
        assertThrows(IllegalArgumentException.class,
            () -> AgentRequestBuilder.registerEndpoints().a2a()
                .fromAgentCard(card()).versionRange("[3.0.0]").build());
    }
    
    @Test
    void codecFailuresAreControlled() {
        json.when(() -> JsonUtils.toJson(any())).thenThrow(new IllegalStateException("codec"));
        assertThrows(IllegalArgumentException.class,
            () -> AgentRequestBuilder.publishAgent().tags(Collections.emptyList()));
    }
    
    private static AgentCard card() {
        AgentCard card = new AgentCard();
        card.setName("demo");
        card.setVersion("1.2.0");
        card.setDescription("native description");
        card.setIconUrl("https://docs.example/icon");
        com.alibaba.nacos.api.ai.model.a2a.AgentProvider provider =
            new com.alibaba.nacos.api.ai.model.a2a.AgentProvider();
        provider.setOrganization("organization");
        provider.setUrl("https://docs.example");
        card.setProvider(provider);
        card.setSupportedInterfaces(new ArrayList<>(Arrays.asList(
            address("https://first.example/rpc?x=1", "1.0.0", ""),
            address("http://second.example/rpc", "1.1.0", "tenant-a"))));
        return card;
    }
    
    private static AgentInterface address(String uri, String version, String tenant) {
        AgentInterface address = new AgentInterface();
        address.setUrl(uri);
        address.setProtocolBinding("JSONRPC");
        address.setProtocolVersion(version);
        address.setTenant(tenant);
        return address;
    }
    
    private static AgentCallInterface call() {
        AgentCallInterface call = new AgentCallInterface();
        call.setProtocol("custom");
        call.setProtocolVersion("1.0");
        call.setDescriptorMediaType("application/json");
        call.setNativeDescriptor(Collections.singletonMap("id", "native"));
        call.setEndpointSourceOrder(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
        return call;
    }
    
    private static void assertEndpoint(Endpoint endpoint, String uri, String version, String tenant,
        int priority) {
        assertEquals(uri, endpoint.getUri());
        assertEquals("JSONRPC", endpoint.getTransport());
        assertEquals(version,
            endpoint.getMetadata().get(AiConstants.A2a.ENDPOINT_PROTOCOL_VERSION));
        assertEquals(tenant, endpoint.getMetadata().get(AiConstants.A2a.ENDPOINT_TENANT));
        assertEquals(tenant != null,
            endpoint.getMetadata().containsKey(AiConstants.A2a.ENDPOINT_TENANT));
        assertEquals(priority, endpoint.getPriority());
        assertEquals(1D, endpoint.getWeight());
        assertTrue(endpoint.getHealthy());
        assertTrue(endpoint.getEnabled());
    }
}
