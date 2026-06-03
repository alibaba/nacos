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

import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentCardListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosAgentSpecListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosPromptListener;
import com.alibaba.nacos.api.ai.listener.AbstractNacosSkillListener;
import com.alibaba.nacos.api.ai.listener.NacosAgentCardEvent;
import com.alibaba.nacos.api.ai.listener.NacosAgentSpecEvent;
import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.api.ai.listener.NacosPromptEvent;
import com.alibaba.nacos.api.ai.listener.NacosSkillEvent;
import com.alibaba.nacos.api.ai.model.NacosAiConfigKeyCodec;
import com.alibaba.nacos.api.ai.model.a2a.AgentCapabilities;
import com.alibaba.nacos.api.ai.model.a2a.AgentCard;
import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.api.ai.model.a2a.AgentEndpoint;
import com.alibaba.nacos.api.ai.model.a2a.AgentInterface;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Java SDK {@link AiService} and inherited A2A APIs.
 *
 * <p>The full scenario matrix and remaining gaps are recorded in
 * {@code test/java-sdk-test/JAVA_SDK_IT_SCENARIOS.md}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: release/query MCP server and A2A agent card through the
 *     public Java SDK factory, including current-value subscription callbacks, latest-version
 *     lookup, duplicate release behavior, A2A endpoint registration APIs, and MCP endpoint
 *     registration error handling for stdio servers.</li>
 *     <li>Boundary/validation: missing MCP, A2A, Prompt, Skill, AgentSpec, endpoint, and
 *     listener parameters throw controlled {@link NacosException} values; missing subscribed
 *     resources return nullable SDK shapes where the SDK defines them.</li>
 *     <li>Error handling: unsupported registration data, mismatched endpoint versions, missing
 *     labels, and missing downloadable resources are rejected by SDK validation or controlled
 *     SDK exceptions before becoming uncontrolled remote failures.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class AiServiceJavaSdkITCase extends JavaSdkBaseITCase {
    
    private static final String A2A_AGENT_GROUP = "agent";
    
    private static final String A2A_AGENT_VERSION_GROUP = "agent-version";
    
    private static final String MCP_SERVER_GROUP = "mcp-server";
    
    private static final String MCP_SERVER_VERSIONS_GROUP = "mcp-server-versions";
    
    private static final String MCP_SERVER_TOOL_GROUP = "mcp-tools";
    
    private static final String MCP_SERVER_RESOURCE_GROUP = "mcp-resources";
    
    private static final String LOCALHOST = "127.0.0.1";
    
    @Test
    public void testReleaseQueryAndSubscribeMcpServer() throws Exception {
        AiService aiService = createAiService();
        ConfigService configService = createConfigService();
        String mcpName = randomServiceName("mcp");
        String version = "1.0.0";
        
        String mcpId = aiService.releaseMcpServer(buildMcpServer(mcpName, version),
                buildMcpToolSpecification(mcpName), buildMcpResourceSpecification(mcpName));
        addCleanup(() -> cleanupMcpServer(configService, mcpId, version));
        
        McpServerDetailInfo detail = aiService.getMcpServer(mcpName, version);
        assertEquals(mcpId, detail.getId(), detail.toString());
        assertEquals(mcpName, detail.getName(), detail.toString());
        assertEquals(version, detail.getVersionDetail().getVersion(), detail.toString());
        assertNotNull(detail.getToolSpec(), detail.toString());
        assertNotNull(detail.getResourceSpec(), detail.toString());
        
        AtomicReference<McpServerDetailInfo> callback = new AtomicReference<>();
        AbstractNacosMcpServerListener listener = new AbstractNacosMcpServerListener() {
            @Override
            public void onEvent(NacosMcpServerEvent event) {
                callback.set(event.getMcpServerDetailInfo());
            }
        };
        addCleanup(() -> aiService.unsubscribeMcpServer(mcpName, version, listener));
        McpServerDetailInfo subscribed = aiService.subscribeMcpServer(mcpName, version, listener);
        assertEquals(mcpId, subscribed.getId(), subscribed.toString());
        assertNotNull(callback.get(), "subscribe should invoke listener with current MCP detail");
        assertEquals(mcpId, callback.get().getId(), callback.get().toString());
    }
    
    @Test
    public void testMcpServerLatestDuplicateAndEndpointScenarios() throws Exception {
        AiService aiService = createAiService();
        ConfigService configService = createConfigService();
        String mcpName = randomServiceName("mcp-version");
        String firstVersion = "1.0.0";
        String secondVersion = "2.0.0";
        int endpointPort = randomPort();
        
        String mcpId = aiService.releaseMcpServer(buildMcpServer(mcpName, firstVersion),
                buildMcpToolSpecification(mcpName), buildMcpResourceSpecification(mcpName));
        addCleanup(() -> cleanupMcpServer(configService, mcpId, firstVersion));
        NacosException duplicate = assertThrows(NacosException.class,
                () -> aiService.releaseMcpServer(buildMcpServer(mcpName, firstVersion),
                        buildMcpToolSpecification(mcpName),
                        buildMcpResourceSpecification(mcpName)));
        assertNotNull(duplicate.getMessage(), duplicate.toString());
        
        String secondReleaseId = aiService.releaseMcpServer(buildMcpServer(mcpName, secondVersion),
                buildMcpToolSpecification(mcpName), buildMcpResourceSpecification(mcpName));
        addCleanup(() -> cleanupMcpServer(configService, mcpId, secondVersion));
        assertEquals(mcpId, secondReleaseId);
        
        McpServerDetailInfo latest = aiService.getMcpServer(mcpName);
        assertEquals(secondVersion, latest.getVersionDetail().getVersion(), latest.toString());
        assertTrue(containsMcpVersion(latest, firstVersion), latest.toString());
        assertTrue(containsMcpVersion(latest, secondVersion), latest.toString());
        
        NacosException unsupportedEndpoint = assertThrows(NacosException.class,
                () -> aiService.registerMcpServerEndpoint(mcpName, LOCALHOST, endpointPort,
                        secondVersion));
        assertNotNull(unsupportedEndpoint.getMessage(), unsupportedEndpoint.toString());
    }
    
    @Test
    public void testMissingMcpSubscriptionReturnsNullableShape() throws Exception {
        AiService aiService = createAiService();
        String missingName = randomServiceName("missing-mcp");
        AbstractNacosMcpServerListener listener = new AbstractNacosMcpServerListener() {
            @Override
            public void onEvent(NacosMcpServerEvent event) {
            }
        };
        addCleanup(() -> aiService.unsubscribeMcpServer(missingName, listener));
        
        assertNull(aiService.subscribeMcpServer(missingName, listener));
        NacosException missing = assertThrows(NacosException.class,
                () -> aiService.getMcpServer(missingName));
        assertEquals(NacosException.NOT_FOUND, missing.getErrCode(), missing.toString());
    }
    
    @Test
    public void testReleaseQueryAndSubscribeAgentCard() throws Exception {
        AiService aiService = createAiService();
        ConfigService configService = createConfigService();
        String agentName = randomServiceName("agent");
        String version = "1.0.0";
        
        aiService.releaseAgentCard(buildAgentCard(agentName, version),
                AiConstants.A2a.A2A_ENDPOINT_TYPE_URL, true);
        addCleanup(() -> cleanupAgentCard(configService, agentName, version));
        
        AgentCardDetailInfo detail = aiService.getAgentCard(agentName, version,
                AiConstants.A2a.A2A_ENDPOINT_TYPE_URL);
        assertEquals(agentName, detail.getName(), detail.toString());
        assertEquals(version, detail.getVersion(), detail.toString());
        assertEquals(AiConstants.A2a.A2A_ENDPOINT_TYPE_URL, detail.getRegistrationType(),
                detail.toString());
        assertTrue(detail.isLatestVersion(), detail.toString());
        
        AtomicReference<AgentCardDetailInfo> callback = new AtomicReference<>();
        AbstractNacosAgentCardListener listener = new AbstractNacosAgentCardListener() {
            @Override
            public void onEvent(NacosAgentCardEvent event) {
                callback.set(event.getAgentCard());
            }
        };
        addCleanup(() -> aiService.unsubscribeAgentCard(agentName, version, listener));
        AgentCardDetailInfo subscribed = aiService.subscribeAgentCard(agentName, version, listener);
        assertEquals(agentName, subscribed.getName(), subscribed.toString());
        assertNotNull(callback.get(), "subscribe should invoke listener with current agent card");
        assertEquals(agentName, callback.get().getName(), callback.get().toString());
    }
    
    @Test
    public void testAgentCardLatestVersionAndEndpointScenarios() throws Exception {
        AiService aiService = createAiService();
        ConfigService configService = createConfigService();
        String agentName = randomServiceName("agent-version");
        String firstVersion = "1.0.0";
        String secondVersion = "2.0.0";
        String thirdVersion = "3.0.0";
        
        aiService.releaseAgentCard(buildAgentCard(agentName, firstVersion),
                AiConstants.A2a.A2A_ENDPOINT_TYPE_URL, true);
        addCleanup(() -> cleanupAgentCard(configService, agentName, firstVersion));
        aiService.releaseAgentCard(buildAgentCard(agentName, secondVersion),
                AiConstants.A2a.A2A_ENDPOINT_TYPE_URL, false);
        addCleanup(() -> cleanupAgentCard(configService, agentName, secondVersion));
        assertEquals(firstVersion, aiService.getAgentCard(agentName).getVersion());
        assertEquals(secondVersion, aiService.getAgentCard(agentName, secondVersion).getVersion());
        
        aiService.releaseAgentCard(buildAgentCard(agentName, thirdVersion),
                AiConstants.A2a.A2A_ENDPOINT_TYPE_URL, true);
        addCleanup(() -> cleanupAgentCard(configService, agentName, thirdVersion));
        assertEquals(thirdVersion, aiService.getAgentCard(agentName).getVersion());
        
        AgentEndpoint endpoint = buildAgentEndpoint(thirdVersion);
        addCleanup(() -> aiService.deregisterAgentEndpoint(agentName, endpoint));
        aiService.registerAgentEndpoint(agentName, endpoint);
        AgentCardDetailInfo serviceDetail = aiService.getAgentCard(agentName, thirdVersion,
                AiConstants.A2a.A2A_ENDPOINT_TYPE_SERVICE);
        assertEquals(agentName, serviceDetail.getName(), serviceDetail.toString());
        assertEquals(AiConstants.A2a.A2A_ENDPOINT_TYPE_URL, serviceDetail.getRegistrationType(),
                serviceDetail.toString());
        aiService.deregisterAgentEndpoint(agentName, endpoint);
    }
    
    @Test
    public void testMissingAiSubscriptionResourcesReturnNullableShapes() throws Exception {
        AiService aiService = createAiService();
        String agentName = randomServiceName("missing-agent");
        String promptKey = randomServiceName("missing-prompt");
        String skillName = randomServiceName("missing-skill");
        String agentSpecName = randomServiceName("missing-agentspec");
        AbstractNacosAgentCardListener agentCardListener = new AbstractNacosAgentCardListener() {
            @Override
            public void onEvent(NacosAgentCardEvent event) {
            }
        };
        AbstractNacosPromptListener promptListener = new AbstractNacosPromptListener() {
            @Override
            public void onEvent(NacosPromptEvent event) {
            }
        };
        AbstractNacosSkillListener skillListener = new AbstractNacosSkillListener() {
            @Override
            public void onEvent(NacosSkillEvent event) {
            }
        };
        AbstractNacosAgentSpecListener agentSpecListener = new AbstractNacosAgentSpecListener() {
            @Override
            public void onEvent(NacosAgentSpecEvent event) {
            }
        };
        addCleanup(() -> aiService.unsubscribeAgentCard(agentName, agentCardListener));
        addCleanup(() -> aiService.unsubscribePrompt(promptKey, null, null, promptListener));
        addCleanup(() -> aiService.unsubscribeSkill(skillName, null, null, skillListener));
        addCleanup(() -> aiService.unsubscribeAgentSpec(agentSpecName, agentSpecListener));
        
        assertNull(aiService.subscribeAgentCard(agentName, agentCardListener));
        assertNull(aiService.subscribePrompt(promptKey, null, null, promptListener));
        assertServerNotImplemented(
                () -> aiService.subscribeSkill(skillName, null, null, skillListener));
        assertServerNotImplemented(() -> aiService.loadAgentSpec(agentSpecName));
        assertServerNotImplemented(() -> aiService.subscribeAgentSpec(agentSpecName,
                agentSpecListener));
        assertThrows(NacosException.class, () -> aiService.downloadSkillZip(skillName));
    }
    
    @Test
    public void testInvalidAiParametersThrowNacosException() throws Exception {
        AiService aiService = createAiService();
        String name = randomServiceName("invalid-ai");
        
        assertInvalidParam(() -> aiService.getMcpServer(""));
        assertInvalidParam(() -> aiService.releaseMcpServer(null, new McpToolSpecification()));
        assertInvalidParam(() -> aiService.releaseMcpServer(new McpServerBasicInfo(),
                new McpToolSpecification()));
        assertInvalidParam(() -> aiService.subscribeMcpServer(name, null));
        
        assertInvalidParam(() -> aiService.getAgentCard(""));
        assertInvalidParam(() -> aiService.releaseAgentCard(null));
        assertInvalidParam(() -> aiService.releaseAgentCard(buildInvalidAgentCard(name)));
        assertInvalidParam(() -> aiService.registerAgentEndpoint("", buildAgentEndpoint()));
        assertInvalidParam(() -> aiService.registerAgentEndpoint(name, new AgentEndpoint()));
        assertInvalidParam(() -> aiService.subscribeAgentCard(name, null));
        assertInvalidParam(() -> aiService.registerAgentEndpoint(name, Collections.emptyList()));
        assertInvalidParam(() -> aiService.registerAgentEndpoint(name,
                Arrays.asList(buildAgentEndpoint("1.0.0"), buildAgentEndpoint("2.0.0"))));
        assertInvalidParam(() -> aiService.registerMcpServerEndpoint(name, "", randomPort()));
        assertInvalidParam(() -> aiService.registerMcpServerEndpoint(name, LOCALHOST, -1));
        
        assertInvalidParam(() -> aiService.getPrompt(""));
        assertInvalidParam(() -> aiService.getPromptByLabel(name, ""));
        assertInvalidParam(() -> aiService.subscribePrompt(name, null, null, null));
        
        assertInvalidParam(() -> aiService.downloadSkillZip(""));
        assertInvalidParam(() -> aiService.subscribeSkill(name, null, null, null));
        
        assertInvalidParam(() -> aiService.loadAgentSpec(""));
        assertInvalidParam(() -> aiService.subscribeAgentSpec(name, null));
    }
    
    private void assertInvalidParam(CheckedRunnable runnable) {
        NacosException exception = assertThrows(NacosException.class, runnable::run);
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode(), exception.toString());
    }

    private void assertServerNotImplemented(CheckedRunnable runnable) {
        NacosException exception = assertThrows(NacosException.class, runnable::run);
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, exception.getErrCode(),
                exception.toString());
    }

    private McpServerBasicInfo buildMcpServer(String mcpName, String version) {
        McpServerBasicInfo result = new McpServerBasicInfo();
        result.setName(mcpName);
        result.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        result.setDescription("Java SDK IT MCP server");
        result.setVersion(version);
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion(version);
        result.setVersionDetail(versionDetail);
        return result;
    }
    
    private McpToolSpecification buildMcpToolSpecification(String mcpName) {
        McpTool tool = new McpTool();
        tool.setName("tool_" + mcpName.replace('-', '_'));
        tool.setDescription("Echo text for Java SDK IT");
        tool.setInputSchema(Collections.singletonMap("type", "object"));
        McpToolSpecification result = new McpToolSpecification();
        result.setTools(Collections.singletonList(tool));
        return result;
    }
    
    private McpResourceSpecification buildMcpResourceSpecification(String mcpName) {
        LinkedHashMap<String, Object> resource = new LinkedHashMap<>();
        resource.put("name", "resource_" + mcpName.replace('-', '_'));
        resource.put("uri", "file:///tmp/" + mcpName + ".txt");
        resource.put("description", "Java SDK IT resource");
        resource.put("mimeType", "text/plain");
        McpResourceSpecification result = new McpResourceSpecification();
        result.setResources(Collections.singletonList(resource));
        return result;
    }
    
    private AgentCard buildAgentCard(String agentName, String version) {
        AgentInterface jsonRpc = new AgentInterface();
        jsonRpc.setUrl("https://example.com/" + agentName + "/jsonrpc");
        jsonRpc.setProtocolBinding(AiConstants.A2a.A2A_ENDPOINT_DEFAULT_TRANSPORT);
        jsonRpc.setProtocolVersion("1.0");
        
        AgentCapabilities capabilities = new AgentCapabilities();
        capabilities.setStreaming(true);
        capabilities.setExtendedAgentCard(true);
        
        AgentCard result = new AgentCard();
        result.setName(agentName);
        result.setVersion(version);
        result.setDescription("Java SDK IT agent");
        result.setSupportedInterfaces(Collections.singletonList(jsonRpc));
        result.setCapabilities(capabilities);
        return result;
    }
    
    private AgentCard buildInvalidAgentCard(String agentName) {
        AgentCard result = new AgentCard();
        result.setName(agentName);
        result.setVersion("1.0.0");
        return result;
    }
    
    private AgentEndpoint buildAgentEndpoint() {
        return buildAgentEndpoint("1.0.0");
    }
    
    private AgentEndpoint buildAgentEndpoint(String version) {
        AgentEndpoint result = new AgentEndpoint();
        result.setVersion(version);
        result.setAddress(LOCALHOST);
        result.setPort(randomPort());
        result.setTransport(AiConstants.A2a.A2A_ENDPOINT_DEFAULT_TRANSPORT);
        result.setPath("/a2a");
        return result;
    }
    
    private boolean containsMcpVersion(McpServerDetailInfo detail, String version) {
        List<ServerVersionDetail> versions = detail.getAllVersions();
        return null != versions && versions.stream()
                .anyMatch(each -> version.equals(each.getVersion()));
    }
    
    private void cleanupMcpServer(ConfigService configService, String mcpId, String version)
            throws NacosException {
        configService.removeConfig(mcpId + "-" + version + "-mcp-server.json", MCP_SERVER_GROUP);
        configService.removeConfig(mcpId + "-mcp-versions.json", MCP_SERVER_VERSIONS_GROUP);
        configService.removeConfig(mcpId + "-" + version + "-mcp-tools.json",
                MCP_SERVER_TOOL_GROUP);
        configService.removeConfig(mcpId + "-" + version + "-mcp-resources.json",
                MCP_SERVER_RESOURCE_GROUP);
    }
    
    private void cleanupAgentCard(ConfigService configService, String agentName, String version)
            throws NacosException {
        String encodedName = NacosAiConfigKeyCodec.encodeSegment(agentName);
        configService.removeConfig(encodedName + "-" + version, A2A_AGENT_VERSION_GROUP);
        configService.removeConfig(encodedName, A2A_AGENT_GROUP);
    }
    
    @FunctionalInterface
    private interface CheckedRunnable {
        
        void run() throws Exception;
    }
}
