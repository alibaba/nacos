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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.ai.AgentTransportMode;
import com.alibaba.nacos.api.ai.AiFactory;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentDiscoveryListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentSpecListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosPromptListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosSkillListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentDiscoveryEvent;
import com.alibaba.nacos.api.ai.listener.NacosAgentSpecEvent;
import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.api.ai.listener.NacosPromptEvent;
import com.alibaba.nacos.api.ai.listener.NacosSkillEvent;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentCallInterface;
import com.alibaba.nacos.api.ai.model.agent.AgentProvider;
import com.alibaba.nacos.api.ai.model.agent.AgentPublishRequest;
import com.alibaba.nacos.api.ai.model.agent.AgentVersionDetail;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.EndpointSource;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryCallInterface;
import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryResult;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointDeregistration;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistration;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.agent.AgentSearchQuery;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.listener.EventListener;
import com.alibaba.nacos.api.naming.listener.NamingEvent;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Real standalone-server transport matrix for the five AI resource families exposed by
 * {@link AiService} and an ordinary Naming lifecycle isolation control.
 *
 * <p>The Maintainer SDK is used only to prepare and remove Prompt, Skill, and AgentSpec
 * fixtures. Agent, MCP, and every asserted read/subscription operation use the public Java SDK.
 * Skill/AgentSpec use their existing HTTP binding for every requested mode. Mixed resource
 * overrides and legacy/new entry points share the same client and lifecycle.
 *
 * @author Nacos
 */
class AiTransportResourceMatrixJavaSdkITCase extends JavaSdkBaseITCase {

    private static final String VERSION = "1.0.0";

    private static final String PROTOCOL_A2A = "a2a";

    @Test
    void shouldKeepFiveAiResourcesCorrectInGrpcMode() throws Exception {
        verifyResourceMatrix(AgentTransportMode.GRPC);
    }

    @Test
    void shouldKeepFiveAiResourcesCorrectInHttpMode() throws Exception {
        verifyResourceMatrix(AgentTransportMode.HTTP);
    }

    @Test
    void shouldKeepFiveAiResourcesCorrectInAutoMode() throws Exception {
        verifyResourceMatrix(AgentTransportMode.AUTO);
    }

    @Test
    void shouldMixHttpAgentWithGrpcMcpAndHttpPrompt() throws Exception {
        verifyMixedResources(AgentTransportMode.GRPC, AgentTransportMode.HTTP);
    }

    @Test
    void shouldMixGrpcAgentWithHttpMcpAndGrpcPrompt() throws Exception {
        verifyMixedResources(AgentTransportMode.HTTP, AgentTransportMode.GRPC);
    }

    @Test
    void shouldKeepNativeHttpResourcesUsableWhenGrpcIsUnreachable() throws Exception {
        Properties properties = sdkProperties();
        properties.setProperty(AiConstants.AI_TRANSPORT_MODE, "http");
        // The standalone harness leaves this alternate gRPC port closed.
        properties.setProperty("nacos.server.grpc.port.offset", "5000");
        properties.setProperty(AiConstants.AI_SKILL_TRANSPORT_MODE, "grpc");
        properties.setProperty(AiConstants.AI_AGENT_SPEC_TRANSPORT_MODE, "grpc");
        AiService service = createAiServiceWithoutReadiness(properties);
        AiMaintainerService maintainer = createAiMaintainerService();
        verifyAgent(service, maintainer, AgentTransportMode.HTTP);
        verifyMcp(service, maintainer, AgentTransportMode.HTTP);
        verifyPrompt(service, maintainer, AgentTransportMode.HTTP);
        verifySkill(service, maintainer, AgentTransportMode.HTTP);
        verifyAgentSpec(service, maintainer, AgentTransportMode.HTTP);
        String absent = randomServiceName("http-only-a2a");
        NacosRuntimeException legacy = assertThrows(NacosRuntimeException.class, () -> service.getAgentCard(absent));
        assertEquals(NacosException.SERVER_ERROR, legacy.getErrCode(), legacy.toString());
        NacosRuntimeException child = assertThrows(NacosRuntimeException.class, () -> service.agent().getAgentCard(absent));
        assertEquals(legacy.getErrCode(), child.getErrCode());
        assertNotNull(service.agent().searchAgents(new AgentSearchQuery()));
    }

    @Test
    void shouldRejectInvalidRequestedModesThroughPublicFactory() {
        String[] keys = {AiConstants.AI_TRANSPORT_MODE, AiConstants.AI_MCP_TRANSPORT_MODE,
            AiConstants.AI_AGENT_TRANSPORT_MODE, AiConstants.AI_PROMPT_TRANSPORT_MODE,
            AiConstants.AI_SKILL_TRANSPORT_MODE, AiConstants.AI_AGENT_SPEC_TRANSPORT_MODE};
        for (String key : keys) {
            Properties properties = sdkProperties();
            for (String validKey : keys) {
                properties.setProperty(validKey, "http");
            }
            properties.setProperty(key, "unsupported");
            NacosException error = assertThrows(NacosException.class, () -> AiFactory.createAiService(properties));
            assertEquals(NacosException.CLIENT_INVALID_PARAM, error.getErrCode(), key);
            Throwable cause = error;
            boolean propertyIdentified = false;
            while (cause != null) {
                propertyIdentified |= cause.toString().contains(key);
                cause = cause.getCause();
            }
            assertTrue(propertyIdentified, key);
        }
    }

    @ParameterizedTest
    @EnumSource(AgentTransportMode.class)
    void shouldSharePollingRecoveryAndCancellationAcrossEntrypoints(AgentTransportMode mode) throws Exception {
        Properties properties = sdkProperties();
        properties.setProperty(AiConstants.AI_TRANSPORT_MODE, mode.getValue());
        properties.setProperty(AiConstants.AI_PROMPT_CACHE_UPDATE_INTERVAL, "100");
        properties.setProperty(AiConstants.AI_SKILL_CACHE_UPDATE_INTERVAL, "100");
        properties.setProperty(AiConstants.AI_AGENTSPEC_CACHE_UPDATE_INTERVAL, "100");
        AiService service = createAiService(properties);
        AiMaintainerService maintainer = createAiMaintainerService();
        String prompt = randomServiceName("compat-prompt");
        String skill = randomServiceName("compat-skill");
        String spec = randomServiceName("compat-spec");
        AtomicInteger callbacks = new AtomicInteger();
        AtomicReference<NacosPromptEvent> promptEvent = new AtomicReference<>();
        AtomicReference<NacosSkillEvent> skillEvent = new AtomicReference<>();
        AtomicReference<NacosAgentSpecEvent> specEvent = new AtomicReference<>();
        AbstractNacosPromptListener promptListener = new AbstractNacosPromptListener() {
            @Override
            public void onEvent(NacosPromptEvent event) {
                callbacks.incrementAndGet();
                promptEvent.set(event);
            }
        };
        AbstractNacosSkillListener skillListener = new AbstractNacosSkillListener() {
            @Override
            public void onEvent(NacosSkillEvent event) {
                callbacks.incrementAndGet();
                skillEvent.set(event);
            }
        };
        AbstractNacosAgentSpecListener specListener = new AbstractNacosAgentSpecListener() {
            @Override
            public void onEvent(NacosAgentSpecEvent event) {
                callbacks.incrementAndGet();
                specEvent.set(event);
            }
        };
        addCleanup(() -> service.prompt().unsubscribePrompt(prompt, null, null, promptListener));
        addCleanup(() -> service.skill().unsubscribeSkill(skill, null, null, skillListener));
        addCleanup(() -> service.agentSpec().unsubscribeAgentSpec(spec, specListener));
        assertNull(service.subscribePrompt(prompt, null, null, promptListener));
        assertNull(service.subscribeSkill(skill, null, null, skillListener));
        assertNull(service.subscribeAgentSpec(spec, specListener));
        addCleanup(() -> maintainer.prompt().deletePrompt(Constants.DEFAULT_NAMESPACE_ID, prompt));
        addCleanup(() -> maintainer.skill().deleteSkill(Constants.DEFAULT_NAMESPACE_ID, skill));
        addCleanup(() -> maintainer.agentSpec().deleteAgentSpec(Constants.DEFAULT_NAMESPACE_ID, spec));
        publishPollingFixtures(maintainer, prompt, skill, spec, VERSION, mode);
        grantClientReadVisibility(Constants.DEFAULT_NAMESPACE_ID, "prompt", prompt);
        grantClientReadVisibility(Constants.DEFAULT_NAMESPACE_ID, "skill", skill);
        grantClientReadVisibility(Constants.DEFAULT_NAMESPACE_ID, "agentspec", spec);
        waitUntil("missing polling resources should recover through the new entrypoints", () ->
            promptEvent.get() != null && promptEvent.get().getPrompt() != null
                && skillEvent.get() != null && skillEvent.get().getZipBytes() != null
                && specEvent.get() != null && specEvent.get().getAgentSpec() != null);
        assertEquals(prompt, promptEvent.get().getPrompt().getPromptKey());
        assertEquals(spec, specEvent.get().getAgentSpec().getName());
        assertNotNull(skillEvent.get().getMd5());
        assertTrue(skillEvent.get().getZipBytes().length > 0);
        assertTrue(skillMarkdown(service.skill().downloadSkillZip(skill)).contains(skill));
        assertTrue(skillMarkdown(skillEvent.get().getZipBytes()).contains(skill));
        int unchanged = callbacks.get();
        TimeUnit.MILLISECONDS.sleep(600);
        assertEquals(unchanged, callbacks.get(), "unchanged MD5 polling must not repeat callbacks");

        service.prompt().unsubscribePrompt(prompt, null, null, promptListener);
        service.skill().unsubscribeSkill(skill, null, null, skillListener);
        service.agentSpec().unsubscribeAgentSpec(spec, specListener);
        publishPollingFixtures(maintainer, prompt, skill, spec, "2.0.0", mode);
        AiService observer = createAiService(properties);
        assertEquals("2.0.0", observer.prompt().getPrompt(prompt).getVersion());
        assertTrue(observer.skill().downloadSkillZip(skill).length > 0);
        assertEquals(spec, observer.agentSpec().loadAgentSpec(spec).getName());
        TimeUnit.MILLISECONDS.sleep(600);
        assertEquals(unchanged, callbacks.get(), "cross-entry cancellation must stop callbacks");

        assertNotNull(service.prompt().subscribePrompt(prompt, null, null, promptListener));
        assertNotNull(service.skill().subscribeSkill(skill, null, null, skillListener));
        assertNotNull(service.agentSpec().subscribeAgentSpec(spec, specListener));
        waitUntil("resubscribed caches must observe the new publication", () ->
            "2.0.0".equals(promptEvent.get().getPrompt().getVersion())
                && skillMarkdown(skillEvent.get().getZipBytes()).contains("2.0.0")
                && specEvent.get().getAgentSpec().getDescription().contains("2.0.0"));
        service.shutdown();
        service.shutdown();
        TimeUnit.MILLISECONDS.sleep(200);
        int closed = callbacks.get();
        publishPollingFixtures(maintainer, prompt, skill, spec, "3.0.0", mode);
        assertEquals("3.0.0", observer.prompt().getPrompt(prompt).getVersion());
        TimeUnit.MILLISECONDS.sleep(600);
        assertEquals(closed, callbacks.get(), "shutdown must stop callbacks across both entrypoints");
    }

    private String skillMarkdown(byte[] archive) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("SKILL.md") || entry.getName().endsWith("/SKILL.md")) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("Skill archive must contain SKILL.md");
    }

    private void publishPollingFixtures(AiMaintainerService maintainer, String prompt, String skill,
            String spec, String version, AgentTransportMode mode) throws Exception {
        maintainer.prompt().createDraft(Constants.DEFAULT_NAMESPACE_ID, prompt, null, version,
                "Prompt " + version, null, "polling compatibility", null, null);
        maintainer.prompt().forcePublish(Constants.DEFAULT_NAMESPACE_ID, prompt, version, true);
        String skillDocument = skillCard(skill, mode).replace("Skill transport matrix", "Skill polling " + version);
        maintainer.skill().createDraft(Constants.DEFAULT_NAMESPACE_ID, skill, VERSION.equals(version) ? null : VERSION, version,
                VERSION.equals(version) ? skillDocument : null, "compatibility");
        if (!VERSION.equals(version)) {
            maintainer.skill().updateDraft(Constants.DEFAULT_NAMESPACE_ID, skillDocument, true);
        }
        maintainer.skill().forcePublish(Constants.DEFAULT_NAMESPACE_ID, skill, version, true);
        maintainer.agentSpec().createDraft(Constants.DEFAULT_NAMESPACE_ID, spec, null, version);
        maintainer.agentSpec().updateDraft(Constants.DEFAULT_NAMESPACE_ID,
                agentSpecCard(spec, mode).replace("AgentSpec transport matrix", "AgentSpec polling " + version), true);
        maintainer.agentSpec().forcePublish(Constants.DEFAULT_NAMESPACE_ID, spec, version, true);
    }

    private void verifyMixedResources(AgentTransportMode global, AgentTransportMode override) throws Exception {
        Properties properties = sdkProperties();
        properties.setProperty(AiConstants.AI_TRANSPORT_MODE, global.getValue());
        properties.setProperty(AiConstants.AI_AGENT_TRANSPORT_MODE, override.getValue());
        properties.setProperty(AiConstants.AI_PROMPT_TRANSPORT_MODE, override.getValue());
        properties.setProperty(AiConstants.AI_SKILL_TRANSPORT_MODE, "grpc");
        properties.setProperty(AiConstants.AI_AGENT_SPEC_TRANSPORT_MODE, "auto");
        AiService service = createAiService(properties);
        AiMaintainerService maintainer = createAiMaintainerService();
        verifyAgent(service, maintainer, override);
        verifyMcp(service, maintainer, global);
        verifyPrompt(service, maintainer, override);
        verifySkill(service, maintainer, override);
        verifyAgentSpec(service, maintainer, override);
    }

    private void verifyResourceMatrix(AgentTransportMode mode) throws Exception {
        AiMaintainerService maintainer = createAiMaintainerService();
        AiService service = createAiService(mode);

        verifyAgent(service, maintainer, mode);
        verifyMcp(service, maintainer, mode);
        verifyPrompt(service, maintainer, mode);
        verifySkill(service, maintainer, mode);
        verifyAgentSpec(service, maintainer, mode);
        verifyOrdinaryNaming(mode);
    }

    private void verifyAgent(AiService service, AiMaintainerService maintainer,
            AgentTransportMode mode) throws Exception {
        String agentName = randomServiceName("transport-" + mode.getValue() + "-agent");
        addCleanup(() -> maintainer.agent().deleteAgent(Constants.DEFAULT_NAMESPACE_ID,
                agentName));

        AgentVersionDetail published = service.agent().publishAgent(agentRequest(agentName, mode));
        assertEquals(AiConstants.Agent.VERSION_STATUS_ONLINE, published.getStatus(),
                published.toString());
        waitUntil(mode + " Agent should become searchable", () -> {
            AgentSearchQuery search = new AgentSearchQuery();
            search.setAgentNameContains(agentName);
            return service.agent().searchAgents(search).getPageItems().stream()
                    .anyMatch(each -> agentName.equals(each.getAgentName()));
        });

        AgentReference reference = reference(agentName);
        AgentDiscoveryResult discovered = service.agent().discoverAgent(reference);
        assertEquals(VERSION, discovered.getVersion(), discovered.toString());
        AbstractNacosAgentDiscoveryListener listener = new AbstractNacosAgentDiscoveryListener() {
            @Override
            public void onEvent(NacosAgentDiscoveryEvent event) {
            }
        };
        addCleanup(() -> service.agent().unsubscribeAgent(reference, listener));
        assertEquals(VERSION, service.agent().subscribeAgent(reference, listener).getVersion());

        Endpoint endpoint = endpoint(mode);
        AgentEndpointRegistration registration = new AgentEndpointRegistration();
        registration.setAgentName(agentName);
        registration.setRuntimeVersion(VERSION);
        registration.setProtocol(PROTOCOL_A2A);
        registration.setEndpoints(Collections.singletonList(endpoint));
        service.agent().registerAgentEndpoints(registration);
        addCleanup(() -> service.agent().deregisterAgentEndpoints(
                deregistration(agentName, endpoint)));
        waitUntil(mode + " Agent Endpoint should become discoverable",
                () -> containsRuntimeEndpoint(service.agent().discoverAgent(reference),
                        endpoint.getUri()));
    }

    private void verifyMcp(AiService service, AiMaintainerService maintainer,
            AgentTransportMode mode) throws Exception {
        String mcpName = randomServiceName("transport-" + mode.getValue() + "-mcp");
        McpServerBasicInfo server = mcpServer(mcpName);
        String mcpId = service.mcp().releaseMcpServer(server, mcpTools(mcpName));
        addCleanup(() -> maintainer.mcp().deleteMcpServer(Constants.DEFAULT_NAMESPACE_ID,
                mcpName, mcpId, VERSION));

        McpServerDetailInfo detail = service.mcp().getMcpServer(mcpName, VERSION);
        assertEquals(mcpId, detail.getId(), detail.toString());
        assertEquals(VERSION, detail.getVersionDetail().getVersion(), detail.toString());
        AbstractNacosMcpServerListener listener = new AbstractNacosMcpServerListener() {
            @Override
            public void onEvent(NacosMcpServerEvent event) {
            }
        };
        addCleanup(() -> service.unsubscribeMcpServer(mcpName, VERSION, listener));
        assertEquals(mcpId, service.subscribeMcpServer(mcpName, VERSION, listener).getId());
    }

    private void verifyPrompt(AiService service, AiMaintainerService maintainer,
            AgentTransportMode mode) throws Exception {
        String promptKey = randomServiceName("transport-" + mode.getValue() + "-prompt");
        maintainer.prompt().createDraft(Constants.DEFAULT_NAMESPACE_ID, promptKey, null,
                VERSION, "Prompt for " + mode, null, "transport matrix", null, null);
        maintainer.prompt().forcePublish(Constants.DEFAULT_NAMESPACE_ID, promptKey, VERSION,
                true);
        addCleanup(() -> maintainer.prompt().deletePrompt(Constants.DEFAULT_NAMESPACE_ID,
                promptKey));
        grantClientReadVisibility(Constants.DEFAULT_NAMESPACE_ID, "prompt", promptKey);

        Prompt prompt = service.prompt().getPromptByVersion(promptKey, VERSION);
        assertEquals(promptKey, prompt.getPromptKey(), prompt.toString());
        assertEquals(VERSION, prompt.getVersion(), prompt.toString());
        AbstractNacosPromptListener listener = new AbstractNacosPromptListener() {
            @Override
            public void onEvent(NacosPromptEvent event) {
            }
        };
        addCleanup(() -> service.unsubscribePrompt(promptKey, VERSION, null, listener));
        assertEquals(promptKey,
                service.prompt().subscribePrompt(promptKey, VERSION, null, listener).getPromptKey());
    }

    private void verifySkill(AiService service, AiMaintainerService maintainer,
            AgentTransportMode mode) throws Exception {
        String skillName = randomServiceName("transport-" + mode.getValue() + "-skill");
        String skillCard = skillCard(skillName, mode);
        maintainer.skill().createDraft(Constants.DEFAULT_NAMESPACE_ID, skillName, null,
                VERSION, skillCard, "transport matrix");
        maintainer.skill().forcePublish(Constants.DEFAULT_NAMESPACE_ID, skillName, VERSION,
                true);
        addCleanup(() -> maintainer.skill().deleteSkill(Constants.DEFAULT_NAMESPACE_ID,
                skillName));
        grantClientReadVisibility(Constants.DEFAULT_NAMESPACE_ID, "skill", skillName);

        assertTrue(service.skill().downloadSkillZipByVersion(skillName, VERSION).length > 0);
        AbstractNacosSkillListener listener = new AbstractNacosSkillListener() {
            @Override
            public void onEvent(NacosSkillEvent event) {
            }
        };
        addCleanup(() -> service.unsubscribeSkill(skillName, VERSION, null, listener));
        assertTrue(service.skill().subscribeSkill(skillName, VERSION, null, listener).length > 0);
        assertTrue(service.downloadSkillZipByVersion(skillName, VERSION).length > 0);
    }

    private void verifyAgentSpec(AiService service, AiMaintainerService maintainer,
            AgentTransportMode mode) throws Exception {
        String specName = randomServiceName("transport-" + mode.getValue() + "-agentspec");
        maintainer.agentSpec().createDraft(Constants.DEFAULT_NAMESPACE_ID, specName, null,
                VERSION);
        maintainer.agentSpec().updateDraft(Constants.DEFAULT_NAMESPACE_ID,
                agentSpecCard(specName, mode), true);
        maintainer.agentSpec().forcePublish(Constants.DEFAULT_NAMESPACE_ID, specName, VERSION,
                true);
        addCleanup(() -> maintainer.agentSpec().deleteAgentSpec(
                Constants.DEFAULT_NAMESPACE_ID, specName));
        grantClientReadVisibility(Constants.DEFAULT_NAMESPACE_ID, "agentspec", specName);

        AbstractNacosAgentSpecListener listener = new AbstractNacosAgentSpecListener() {
            @Override
            public void onEvent(NacosAgentSpecEvent event) {
            }
        };
        AgentSpec spec = service.agentSpec().loadAgentSpec(specName);
        assertEquals(specName, spec.getName(), spec.toString());
        addCleanup(() -> service.unsubscribeAgentSpec(specName, listener));
        assertEquals(specName, service.agentSpec().subscribeAgentSpec(specName, listener).getName());
        assertEquals(specName, service.loadAgentSpec(specName).getName());
    }

    private void verifyOrdinaryNaming(AgentTransportMode mode) throws Exception {
        NamingService namingService = createNamingService();
        String serviceName = randomServiceName("transport-" + mode.getValue() + "-naming");
        String groupName = "AI_TRANSPORT_MATRIX";
        int port = randomPort();
        Instance instance = new Instance();
        instance.setIp("127.0.0.1");
        instance.setPort(port);
        instance.setHealthy(true);
        instance.setEnabled(true);
        instance.addMetadata("source", "ai-transport-matrix");
        CountDownLatch pushed = new CountDownLatch(1);
        EventListener listener = event -> {
            if (event instanceof NamingEvent && ((NamingEvent) event).getInstances().stream()
                    .anyMatch(each -> port == each.getPort())) {
                pushed.countDown();
            }
        };
        addCleanup(() -> namingService.unsubscribe(serviceName, groupName, listener));
        addCleanup(() -> namingService.deregisterInstance(serviceName, groupName, instance));

        namingService.subscribe(serviceName, groupName, listener);
        namingService.registerInstance(serviceName, groupName, instance);
        assertTrue(pushed.await(10, TimeUnit.SECONDS),
                mode + " ordinary Naming listener should receive the instance");
        waitUntil(mode + " ordinary Naming query should return the instance",
                () -> namingService.getAllInstances(serviceName, groupName).stream()
                        .anyMatch(each -> port == each.getPort()
                                && "ai-transport-matrix".equals(
                                each.getMetadata().get("source"))));
        namingService.deregisterInstance(serviceName, groupName, instance);
        waitUntil(mode + " ordinary Naming instance should be removed",
                () -> namingService.getAllInstances(serviceName, groupName).isEmpty());
    }

    private AiService createAiService(AgentTransportMode mode) throws Exception {
        Properties properties = sdkProperties();
        properties.setProperty(AiConstants.AI_TRANSPORT_MODE, mode.getValue());
        return createAiService(properties);
    }

    private AiMaintainerService createAiMaintainerService() throws NacosException {
        Properties properties = maintainerProperties();
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, "/nacos");
        return AiMaintainerFactory.createAiMaintainerService(properties);
    }

    private AgentPublishRequest agentRequest(String agentName, AgentTransportMode mode) {
        AgentInterface descriptorEndpoint = new AgentInterface();
        descriptorEndpoint.setUrl("https://example.com/" + agentName);
        descriptorEndpoint.setProtocolBinding("HTTP+JSON");
        descriptorEndpoint.setProtocolVersion("1.0");
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(Boolean.TRUE);
        AgentCard card = new AgentCard();
        card.setName(agentName);
        card.setVersion(VERSION);
        card.setDescription("Agent transport matrix " + mode);
        card.setSupportedInterfaces(Collections.singletonList(descriptorEndpoint));
        card.setCapabilities(capabilities);

        Endpoint declared = new Endpoint();
        declared.setUri(descriptorEndpoint.getUrl());
        declared.setTransport(descriptorEndpoint.getProtocolBinding());
        AgentCallInterface callInterface = new AgentCallInterface();
        callInterface.setProtocol(PROTOCOL_A2A);
        callInterface.setProtocolVersion("1.0");
        callInterface.setDescriptorMediaType("application/json");
        callInterface.setNativeDescriptor(
                JacksonUtils.toObj(JacksonUtils.toJson(card), Map.class));
        callInterface.setEndpointSourceOrder(Arrays.asList(EndpointSource.DECLARED,
                EndpointSource.RUNTIME));
        callInterface.setDeclaredEndpoints(Collections.singletonList(declared));

        AgentProvider provider = new AgentProvider();
        provider.setName("Nacos Java SDK IT");
        provider.setUrl("https://nacos.io");
        AgentPublishRequest result = new AgentPublishRequest();
        result.setAgentName(agentName);
        result.setDisplayName("Display " + agentName);
        result.setDescription("Agent transport matrix " + mode);
        result.setProvider(provider);
        result.setTags(Arrays.asList("java-sdk-it", "transport-" + mode.getValue()));
        result.setVersion(VERSION);
        result.setCallInterfaces(Collections.singletonList(callInterface));
        result.setAuthor("java-sdk-it");
        result.setChangeDescription("verify " + mode + " transport");
        result.setAutoSubmit(true);
        return result;
    }

    private AgentReference reference(String agentName) {
        AgentReference result = new AgentReference();
        result.setAgentName(agentName);
        return result;
    }

    private Endpoint endpoint(AgentTransportMode mode) {
        Endpoint result = new Endpoint();
        result.setUri("http://127.0.0.1:" + randomPort() + "/" + mode.getValue());
        result.setTransport("HTTP");
        result.setPriority(0);
        result.setWeight(1D);
        result.setMetadata(Collections.singletonMap("transport", mode.getValue()));
        return result;
    }

    private AgentEndpointDeregistration deregistration(String agentName,
            Endpoint endpoint) {
        Endpoint naturalKey = new Endpoint();
        naturalKey.setUri(endpoint.getUri());
        naturalKey.setTransport(endpoint.getTransport());
        AgentEndpointDeregistration result = new AgentEndpointDeregistration();
        result.setAgentName(agentName);
        result.setProtocol(PROTOCOL_A2A);
        result.setEndpoints(Collections.singletonList(naturalKey));
        return result;
    }

    private boolean containsRuntimeEndpoint(AgentDiscoveryResult result, String uri) {
        if (result == null || result.getCallInterfaces() == null) {
            return false;
        }
        for (AgentDiscoveryCallInterface callInterface : result.getCallInterfaces()) {
            if (!PROTOCOL_A2A.equals(callInterface.getProtocol())
                    || callInterface.getEndpointSets() == null) {
                continue;
            }
            for (EndpointSet endpointSet : callInterface.getEndpointSets()) {
                if (endpointSet.getSource() == EndpointSource.RUNTIME
                        && endpointSet.getEndpoints() != null
                        && endpointSet.getEndpoints().stream()
                        .anyMatch(each -> uri.equals(each.getUri()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private McpServerBasicInfo mcpServer(String mcpName) {
        McpServerBasicInfo result = new McpServerBasicInfo();
        result.setName(mcpName);
        result.setDescription("MCP transport matrix");
        result.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        result.setVersion(VERSION);
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion(VERSION);
        result.setVersionDetail(versionDetail);
        return result;
    }

    private McpToolSpecification mcpTools(String mcpName) {
        McpTool tool = new McpTool();
        tool.setName("tool_" + mcpName.replace('-', '_'));
        tool.setDescription("Transport matrix tool");
        tool.setInputSchema(Collections.singletonMap("type", "object"));
        McpToolSpecification result = new McpToolSpecification();
        result.setTools(Collections.singletonList(tool));
        return result;
    }

    private String skillCard(String skillName, AgentTransportMode mode) {
        String description = "Skill transport matrix " + mode;
        Skill skill = new Skill();
        skill.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
        skill.setName(skillName);
        skill.setDescription(description);
        skill.setSkillMd("---\nname: " + skillName + "\ndescription: " + description
                + "\n---\n\n" + description + '\n');
        return JacksonUtils.toJson(skill);
    }

    private String agentSpecCard(String specName, AgentTransportMode mode) {
        String description = "AgentSpec transport matrix " + mode;
        Map<String, Object> worker = new HashMap<>();
        worker.put("suggested_name", specName);
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("version", "1.0");
        manifest.put("description", description);
        manifest.put("worker", worker);
        AgentSpec spec = new AgentSpec();
        spec.setNamespaceId(Constants.DEFAULT_NAMESPACE_ID);
        spec.setName(specName);
        spec.setDescription(description);
        spec.setContent(JacksonUtils.toJson(manifest));
        return JacksonUtils.toJson(spec);
    }
}
