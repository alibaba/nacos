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
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointDeregistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentEndpointRegistrationBatch;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentSearchRequest;
import com.alibaba.nacos.api.ai.model.rad.EndpointSet;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real standalone-server transport matrix for the five AI resource families exposed by
 * {@link AiService}.
 *
 * <p>The Maintainer SDK is used only to prepare and remove Prompt, Skill, and AgentSpec
 * fixtures. Agent, MCP, and every asserted read/subscription operation use the public Java SDK.
 * The expected unsupported gRPC Skill/AgentSpec polling paths are asserted as controlled
 * {@link NacosException#SERVER_NOT_IMPLEMENTED} results.
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

    private void verifyResourceMatrix(AgentTransportMode mode) throws Exception {
        AiMaintainerService maintainer = createAiMaintainerService();
        AiService service = createAiService(mode);

        verifyAgent(service, maintainer, mode);
        verifyMcp(service, maintainer, mode);
        verifyPrompt(service, maintainer, mode);
        verifySkill(service, maintainer, mode);
        verifyAgentSpec(service, maintainer, mode);
    }

    private void verifyAgent(AiService service, AiMaintainerService maintainer,
            AgentTransportMode mode) throws Exception {
        String agentName = randomServiceName("transport-" + mode.getValue() + "-agent");
        addCleanup(() -> maintainer.agent().deleteAgent(Constants.DEFAULT_NAMESPACE_ID,
                agentName));

        AgentVersionDetail published = service.publishAgent(agentRequest(agentName, mode));
        assertEquals(AiConstants.Agent.VERSION_STATUS_ONLINE, published.getStatus(),
                published.toString());
        waitUntil(mode + " Agent should become searchable", () -> {
            AgentSearchRequest search = new AgentSearchRequest();
            search.setAgentNameContains(agentName);
            return service.searchAgents(search).getPageItems().stream()
                    .anyMatch(each -> agentName.equals(each.getAgentName()));
        });

        AgentReference reference = reference(agentName);
        AgentDiscoveryResult discovered = service.discoverAgent(reference);
        assertEquals(VERSION, discovered.getVersion(), discovered.toString());
        AbstractNacosAgentDiscoveryListener listener = new AbstractNacosAgentDiscoveryListener() {
            @Override
            public void onEvent(NacosAgentDiscoveryEvent event) {
            }
        };
        addCleanup(() -> service.unsubscribeAgent(reference, listener));
        assertEquals(VERSION, service.subscribeAgent(reference, listener).getVersion());

        Endpoint endpoint = endpoint(mode);
        AgentEndpointRegistrationBatch registration = new AgentEndpointRegistrationBatch();
        registration.setAgentName(agentName);
        registration.setRuntimeVersion(VERSION);
        registration.setProtocol(PROTOCOL_A2A);
        registration.setEndpoints(Collections.singletonList(endpoint));
        service.registerAgentEndpoints(registration);
        addCleanup(() -> service.deregisterAgentEndpoints(
                deregistration(agentName, endpoint)));
        waitUntil(mode + " Agent Endpoint should become discoverable",
                () -> containsRuntimeEndpoint(service.discoverAgent(reference),
                        endpoint.getUri()));
    }

    private void verifyMcp(AiService service, AiMaintainerService maintainer,
            AgentTransportMode mode) throws Exception {
        String mcpName = randomServiceName("transport-" + mode.getValue() + "-mcp");
        McpServerBasicInfo server = mcpServer(mcpName);
        String mcpId = service.releaseMcpServer(server, mcpTools(mcpName));
        addCleanup(() -> maintainer.mcp().deleteMcpServer(Constants.DEFAULT_NAMESPACE_ID,
                mcpName, mcpId, VERSION));

        McpServerDetailInfo detail = service.getMcpServer(mcpName, VERSION);
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

        Prompt prompt = service.getPromptByVersion(promptKey, VERSION);
        assertEquals(promptKey, prompt.getPromptKey(), prompt.toString());
        assertEquals(VERSION, prompt.getVersion(), prompt.toString());
        AbstractNacosPromptListener listener = new AbstractNacosPromptListener() {
            @Override
            public void onEvent(NacosPromptEvent event) {
            }
        };
        addCleanup(() -> service.unsubscribePrompt(promptKey, VERSION, null, listener));
        assertEquals(promptKey,
                service.subscribePrompt(promptKey, VERSION, null, listener).getPromptKey());
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

        assertTrue(service.downloadSkillZipByVersion(skillName, VERSION).length > 0);
        AbstractNacosSkillListener listener = new AbstractNacosSkillListener() {
            @Override
            public void onEvent(NacosSkillEvent event) {
            }
        };
        if (mode == AgentTransportMode.HTTP) {
            addCleanup(() -> service.unsubscribeSkill(skillName, VERSION, null, listener));
            assertTrue(service.subscribeSkill(skillName, VERSION, null, listener).length > 0);
        } else {
            assertNotImplemented(
                    () -> service.subscribeSkill(skillName, VERSION, null, listener));
        }
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

        AbstractNacosAgentSpecListener listener = new AbstractNacosAgentSpecListener() {
            @Override
            public void onEvent(NacosAgentSpecEvent event) {
            }
        };
        if (mode == AgentTransportMode.HTTP) {
            AgentSpec spec = service.loadAgentSpec(specName);
            assertEquals(specName, spec.getName(), spec.toString());
            addCleanup(() -> service.unsubscribeAgentSpec(specName, listener));
            assertEquals(specName, service.subscribeAgentSpec(specName, listener).getName());
        } else {
            assertNotImplemented(() -> service.loadAgentSpec(specName));
            assertNotImplemented(() -> service.subscribeAgentSpec(specName, listener));
        }
    }

    private AiService createAiService(AgentTransportMode mode) throws NacosException {
        Properties properties = sdkProperties();
        properties.setProperty(AiConstants.AI_TRANSPORT_MODE, mode.getValue());
        AiService result = AiFactory.createAiService(properties);
        addCleanup(result::shutdown);
        return result;
    }

    private AiMaintainerService createAiMaintainerService() throws NacosException {
        Properties properties = sdkProperties();
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

    private AgentEndpointDeregistrationBatch deregistration(String agentName,
            Endpoint endpoint) {
        Endpoint naturalKey = new Endpoint();
        naturalKey.setUri(endpoint.getUri());
        naturalKey.setTransport(endpoint.getTransport());
        AgentEndpointDeregistrationBatch result = new AgentEndpointDeregistrationBatch();
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

    private void assertNotImplemented(CheckedRunnable runnable) {
        NacosException exception = assertThrows(NacosException.class, runnable::run);
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, exception.getErrCode(),
                exception.toString());
    }

    @FunctionalInterface
    private interface CheckedRunnable {

        void run() throws Exception;
    }
}
