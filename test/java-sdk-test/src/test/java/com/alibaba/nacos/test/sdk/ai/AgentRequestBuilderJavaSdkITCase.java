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

package com.alibaba.nacos.test.sdk.ai;

import com.alibaba.nacos.api.ai.AgentTransportMode;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.builder.AgentRequestBuilder;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.converter.AgentConverters;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEvent;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.agent.AgentReference;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSet;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agent.client.AgentPublishRequest;
import com.alibaba.nacos.api.ai.utils.AgentDiscoveryCanonicalizer;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.maintainer.client.ai.AgentMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenario coverage for offline Agent request construction with a real external SDK client.
 * Verifies generic/native publication, Card defaults and instance-interface replacement,
 * metadata and priority round trips, Watch changes, exact/range bindings and controlled
 * invalid input. Uses the normal authenticated fixture and runs with both JSON adapters.
 * Detailed scope is in JAVA_SDK_IT_SCENARIOS.md. Pure input aliasing is covered in API UTs.
 * @author Nacos
 */
class AgentRequestBuilderJavaSdkITCase extends JavaSdkBaseITCase {

    @ParameterizedTest
    @EnumSource(AgentTransportMode.class)
    void nativeBuildersPreserveMetadataThroughPublicationReplacementAndWatch(AgentTransportMode mode) throws Exception {
        AiService service = service(mode);
        String name = newAgent("builder-a2a");
        AgentCard card = card(name);
        AgentPublishRequest publication = AgentRequestBuilder.publishAgent().a2a().fromAgentCard(card).build();
        assertFalse(publication.isAutoSubmit());
        service.agent().publishAgent(publication);
        AgentReference reference = reference(name);
        AgentCard declared = AgentConverters.a2a().convert(service.agent().discoverAgent(reference));
        assertEquals(2, declared.getSupportedInterfaces().size());
        assertEquals("", declared.getSupportedInterfaces().get(0).getTenant());
        assertEquals("1.1.0", declared.getSupportedInterfaces().get(1).getProtocolVersion());
        assertEquals("definition-tenant", declared.getSupportedInterfaces().get(1).getTenant());
        assertNull(card.getUrl(), "building must not normalize the input Card in place");
        AtomicReference<AgentDiscoveryResult> observed = new AtomicReference<>();
        AbstractNacosAgentDiscoveryListener listener = new AbstractNacosAgentDiscoveryListener() {
            @Override
            public void onEvent(NacosAgentDiscoveryEvent event) {
                observed.set(event.getAgentDiscoveryResult());
            }
        };
        service.agent().subscribeAgent(reference, listener);
        try {
            AgentInterface first = address("http://127.0.0.1:18081/instance", "1.0.0", "instance-tenant");
            AgentInterface second = address("http://127.0.0.1:18082/instance", "1.2.0", "");
            AgentEndpointRegistrationBatch batch = AgentRequestBuilder.registerEndpoints().a2a()
                .fromAgentCard(card).fromAgentInterfaces(Arrays.asList(first, second)).build();
            addCleanup(() -> service.agent().deregisterAgentEndpoints(name, "a2a", batch.getEndpoints()));
            service.agent().registerAgentEndpoints(batch);
            waitUntil("builder registration is visible to Watch", () -> runtime(observed.get()).size() == 2);
            AgentCard runtimeCard = AgentConverters.a2a().convert(observed.get());
            assertEquals(first.getUrl(), runtimeCard.getSupportedInterfaces().get(0).getUrl());
            assertEquals("instance-tenant", runtimeCard.getSupportedInterfaces().get(0).getTenant());
            assertEquals("1.2.0", runtimeCard.getSupportedInterfaces().get(1).getProtocolVersion());
            assertEquals("", runtimeCard.getSupportedInterfaces().get(1).getTenant());
            assertEquals("[1.0.0]", runtime(observed.get()).get(0).getBindings().get(0).getVersionRange());
            final String before = AgentDiscoveryCanonicalizer.fingerprint(observed.get());
            first.setTenant("changed-tenant");
            AgentEndpointRegistrationBatch replacement = AgentRequestBuilder.registerEndpoints()
                .agentName(name).runtimeVersion("1.0.0").versionRange("[1.0.0,2.0.0)")
                .a2a().fromAgentInterfaces(Collections.singletonList(first)).build();
            service.agent().registerAgentEndpoints(replacement);
            waitUntil("complete replacement and tenant change reach Watch", () -> {
                List<Endpoint> endpoints = runtime(observed.get());
                return endpoints.size() == 1 && "changed-tenant".equals(endpoints.get(0).getMetadata()
                    .get(AiConstants.A2a.ENDPOINT_TENANT));
            });
            assertNotEquals(before, AgentDiscoveryCanonicalizer.fingerprint(observed.get()));
            assertEquals("[1.0.0,2.0.0)", runtime(observed.get()).get(0).getBindings().get(0).getVersionRange());
            assertEquals(1, runtime(service.agent().discoverAgent(reference)).size());
            assertEquals(2, card.getSupportedInterfaces().size());
            assertEquals("instance-tenant", batch.getEndpoints().get(0).getMetadata().get(AiConstants.A2a.ENDPOINT_TENANT));
        } finally {
            service.agent().unsubscribeAgent(reference, listener);
        }
    }

    @ParameterizedTest
    @EnumSource(AgentTransportMode.class)
    void genericBuildersRetainNativeProtocolAndRegistrationFlags(AgentTransportMode mode) throws Exception {
        AiService service = service(mode);
        String name = newAgent("builder-generic");
        AgentCallInterface call = new AgentCallInterface();
        call.setProtocol("custom");
        call.setProtocolVersion("v1");
        call.setDescriptorMediaType("application/json");
        call.setNativeDescriptor(Collections.singletonMap("operation", "invoke"));
        call.setEndpointSourceOrder(Arrays.asList(EndpointSource.RUNTIME, EndpointSource.DECLARED));
        AgentPublishRequest publication = AgentRequestBuilder.publishAgent().agentName(name).version("1.0.0")
            .displayName("Builder Agent").description("generic publication").tags(Collections.singletonList("builder"))
            .callInterfaces(Collections.singletonList(call)).build();
        service.agent().publishAgent(publication);
        Endpoint endpoint = new Endpoint();
        endpoint.setUri("http://127.0.0.1:18083/custom");
        endpoint.setTransport("custom");
        endpoint.setPriority(5);
        endpoint.setWeight(0.4D);
        endpoint.setMetadata(Collections.singletonMap("key", "value"));
        AgentEndpointRegistrationBatch batch = AgentRequestBuilder.registerEndpoints().agentName(name)
            .protocol("custom").runtimeVersion("1.0.0").endpoints(Collections.singletonList(endpoint)).build();
        addCleanup(() -> service.agent().deregisterAgentEndpoints(name, "custom", batch.getEndpoints()));
        service.agent().registerAgentEndpoints(batch);
        AgentReference reference = reference(name);
        waitUntil("generic builder registration is visible", () -> runtime(service.agent().discoverAgent(reference)).size() == 1);
        AgentDiscoveryResult result = service.agent().discoverAgent(reference);
        assertEquals("generic publication", result.getDescription());
        assertEquals(Collections.singletonList("builder"), result.getTags());
        assertEquals("invoke", ((java.util.Map<?, ?>) result.getCallInterfaces().get(0).getNativeDescriptor()).get("operation"));
        Endpoint actual = runtime(result).get(0);
        assertEquals(5, actual.getPriority());
        assertEquals(0.4D, actual.getWeight());
        assertEquals("value", actual.getMetadata().get("key"));
        assertTrue(actual.getEnabled());
        assertTrue(actual.getHealthy());
        AgentPublishRequest next = AgentRequestBuilder.publishAgent().agentName(name).version("1.1.0")
            .basedOnVersion("1.0.0").autoSubmit(true).build();
        service.agent().publishAgent(next);
        AgentReference latest = new AgentReference();
        latest.setAgentName(name);
        latest.setLabel("latest");
        waitUntil("ordinary autoSubmit publishes the derived Version", () ->
            "1.1.0".equals(service.agent().discoverAgent(latest).getVersion()));
    }

    @ParameterizedTest
    @EnumSource(AgentTransportMode.class)
    void invalidBuilderInputDoesNotPublishAnAgent(AgentTransportMode mode) throws Exception {
        AiService service = service(mode);
        String name = randomServiceName("builder-invalid");
        AgentCard card = card(name);
        AgentInterface first = card.getSupportedInterfaces().get(0);
        assertThrows(IllegalArgumentException.class, () -> AgentRequestBuilder.registerEndpoints().a2a()
            .fromAgentCard(card).fromAgentInterfaces(Arrays.asList(first, first)).build());
        assertThrows(IllegalArgumentException.class, () -> AgentRequestBuilder.publishAgent().a2a()
            .fromAgentCard(card).version("2.0.0").build());
        assertThrows(IllegalArgumentException.class, () -> AgentRequestBuilder.registerEndpoints().a2a()
            .fromAgentCard(card).versionRange("[2.0.0]").build());
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> service.agent().discoverAgent(reference(name))).getErrCode());
    }

    private AiService service(AgentTransportMode mode) throws Exception {
        Properties properties = sdkProperties();
        properties.setProperty(AiConstants.AI_TRANSPORT_MODE, mode.getValue());
        return createAiService(properties);
    }

    private String newAgent(String prefix) throws Exception {
        String name = randomServiceName(prefix);
        AgentMaintainerService maintainer = AiMaintainerFactory.createAiMaintainerService(maintainerProperties()).agent();
        addCleanup(() -> maintainer.deleteAgent(Constants.DEFAULT_NAMESPACE_ID, name));
        return name;
    }

    private static AgentReference reference(String name) {
        AgentReference reference = new AgentReference();
        reference.setAgentName(name);
        reference.setVersion("1.0.0");
        return reference;
    }

    private static AgentCard card(String name) {
        AgentCard card = new AgentCard();
        card.setName(name);
        card.setVersion("1.0.0");
        card.setDescription("native description");
        card.setSupportedInterfaces(Arrays.asList(address("http://127.0.0.1:18071/declared", "1.0.0", ""),
            address("http://127.0.0.1:18072/declared", "1.1.0", "definition-tenant")));
        return card;
    }

    private static AgentInterface address(String url, String version, String tenant) {
        AgentInterface result = new AgentInterface();
        result.setUrl(url);
        result.setProtocolBinding("JSONRPC");
        result.setProtocolVersion(version);
        result.setTenant(tenant);
        return result;
    }

    private static List<Endpoint> runtime(AgentDiscoveryResult result) {
        if (result != null) {
            for (AgentCallInterface call : result.getCallInterfaces()) {
                for (EndpointSet set : call.getEndpointSets()) {
                    if (set.getSource() == EndpointSource.RUNTIME) {
                        return set.getEndpoints();
                    }
                }
            }
        }
        return Collections.emptyList();
    }
}
