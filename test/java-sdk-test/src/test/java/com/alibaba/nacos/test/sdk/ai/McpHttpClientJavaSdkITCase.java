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
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.listener.AbstractNacosMcpServerListener;
import com.alibaba.nacos.api.ai.listener.NacosMcpServerEvent;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpResourceSpecification;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerDetailInfo;
import com.alibaba.nacos.api.ai.model.mcp.McpServerRemoteServiceConfig;
import com.alibaba.nacos.api.ai.model.mcp.McpServerVersionDetail;
import com.alibaba.nacos.api.ai.model.mcp.McpTool;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerFactory;
import com.alibaba.nacos.maintainer.client.ai.McpMaintainerService;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Standalone integration scenarios for the MCP HTTP Client transport.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: omitted and explicit {@code createDraft=false} keep historical
 *     direct-online behavior, while {@code createDraft=true} creates a non-serving lifecycle
 *     draft once managed cutover is available.</li>
 *     <li>Expected capability: exact/latest query, current-value polling subscription, and REF
 *     Runtime Endpoint register/query/idempotent replacement/deregister use HTTP only.</li>
 *     <li>Boundary/error handling: duplicate release, missing MCP, invalid release content,
 *     invalid Endpoint coordinates, and non-REF Endpoint publication map to controlled SDK
 *     exceptions.</li>
 * </ul>
 *
 * @author Nacos
 */
class McpHttpClientJavaSdkITCase extends JavaSdkBaseITCase {

    private static final String VERSION = "1.0.0";

    private static final String LOCALHOST = "127.0.0.1";

    private static final String STATUS_DRAFT = "draft";

    @Test
    void shouldReleaseQuerySubscribeAndHonorDraftChoiceOverHttp() throws Exception {
        AiService service = createHttpAiService();
        McpMaintainerService maintainer = createMcpMaintainerService();

        String defaultName = randomServiceName("mcp-http-default");
        String defaultId = service.releaseMcpServer(stdioServer(defaultName, VERSION),
                toolSpecification(defaultName), resourceSpecification(defaultName));
        addCleanup(() -> maintainer.deleteMcpServer(Constants.DEFAULT_NAMESPACE_ID, defaultName,
                null, null));
        assertServing(service, defaultName, defaultId, VERSION);

        AtomicReference<McpServerDetailInfo> callback = new AtomicReference<>();
        AbstractNacosMcpServerListener listener = new AbstractNacosMcpServerListener() {
            @Override
            public void onEvent(NacosMcpServerEvent event) {
                callback.set(event.getMcpServerDetailInfo());
            }
        };
        addCleanup(() -> service.unsubscribeMcpServer(defaultName, VERSION, listener));
        McpServerDetailInfo subscribed = service.subscribeMcpServer(defaultName, VERSION,
                listener);
        assertEquals(defaultId, subscribed.getId(), subscribed.toString());
        waitUntil("HTTP MCP subscription should deliver its current value",
                () -> callback.get() != null);
        assertEquals(defaultId, callback.get().getId(), callback.get().toString());

        NacosException duplicate = assertThrows(NacosException.class,
                () -> service.releaseMcpServer(stdioServer(defaultName, VERSION),
                        toolSpecification(defaultName), resourceSpecification(defaultName), null,
                        false));
        assertEquals(NacosException.CONFLICT, duplicate.getErrCode(), duplicate.toString());

        String explicitName = randomServiceName("mcp-http-explicit-false");
        int directPort = randomPort();
        String explicitId = service.releaseMcpServer(remoteServer(explicitName, VERSION),
                toolSpecification(explicitName), resourceSpecification(explicitName),
                directEndpoint(directPort), false);
        addCleanup(() -> maintainer.deleteMcpServer(Constants.DEFAULT_NAMESPACE_ID, explicitName,
                null, null));
        assertServing(service, explicitName, explicitId, VERSION);
        assertTrue(containsEndpoint(service.getMcpServer(explicitName, VERSION), directPort));

        String draftName = randomServiceName("mcp-http-draft");
        try {
            String draftId = service.releaseMcpServer(stdioServer(draftName, VERSION),
                    toolSpecification(draftName), resourceSpecification(draftName), null, true);
            assertNotNull(draftId);
            addCleanup(() -> maintainer.deleteMcpServer(Constants.DEFAULT_NAMESPACE_ID, draftName,
                    null, null));
            McpServerVersionDetail draft = maintainer.getMcpServerVersion(draftName, VERSION);
            assertEquals(STATUS_DRAFT, draft.getStatus(), draft.toString());
            NacosException notServing = assertThrows(NacosException.class,
                    () -> service.getMcpServer(draftName, VERSION));
            assertEquals(NacosException.NOT_FOUND, notServing.getErrCode(),
                    notServing.toString());
        } catch (NacosException exception) {
            assertEquals(NacosException.CONFLICT, exception.getErrCode(), exception.toString());
            assertTrue(exception.getMessage().contains("LIFECYCLE_MANAGED cutover"),
                    exception.toString());
        }
    }

    @Test
    void shouldRegisterAndDeregisterRefEndpointsOverHttp() throws Exception {
        AiService service = createHttpAiService();
        McpMaintainerService maintainer = createMcpMaintainerService();
        String mcpName = randomServiceName("mcp-http-ref");
        int firstPort = randomPort();
        int replacementPort = randomPort();
        String mcpId = service.releaseMcpServer(remoteServer(mcpName, VERSION),
                toolSpecification(mcpName), resourceSpecification(mcpName));
        addCleanup(() -> maintainer.deleteMcpServer(Constants.DEFAULT_NAMESPACE_ID, mcpName,
                null, null));

        service.registerMcpServerEndpoint(mcpName, LOCALHOST, firstPort, VERSION);
        addCleanup(() -> service.deregisterMcpServerEndpoint(mcpName, LOCALHOST, firstPort));
        waitUntil("HTTP MCP Endpoint should be visible",
                () -> containsEndpoint(service.getMcpServer(mcpName, VERSION), firstPort));

        service.registerMcpServerEndpoint(mcpName, LOCALHOST, firstPort, VERSION);
        assertEquals(1, countEndpoint(service.getMcpServer(mcpName, VERSION), firstPort));

        service.deregisterMcpServerEndpoint(mcpName, LOCALHOST, firstPort);
        waitUntil("HTTP MCP Endpoint should disappear after deregistration",
                () -> !containsEndpoint(service.getMcpServer(mcpName, VERSION), firstPort));

        service.registerMcpServerEndpoint(mcpName, LOCALHOST, replacementPort);
        addCleanup(() -> service.deregisterMcpServerEndpoint(mcpName, LOCALHOST,
                replacementPort));
        waitUntil("HTTP MCP latest-Version Endpoint overload should be visible",
                () -> containsEndpoint(service.getMcpServer(mcpName), replacementPort));
        service.deregisterMcpServerEndpoint(mcpName, LOCALHOST, replacementPort);
        waitUntil("HTTP MCP latest-Version Endpoint should disappear after deregistration",
                () -> !containsEndpoint(service.getMcpServer(mcpName), replacementPort));

        NacosException missing = assertThrows(NacosException.class,
                () -> service.registerMcpServerEndpoint(randomServiceName("missing-mcp"),
                        LOCALHOST, randomPort(), VERSION));
        assertEquals(NacosException.NOT_FOUND, missing.getErrCode(), missing.toString());

        String stdioName = randomServiceName("mcp-http-stdio");
        String stdioId = service.releaseMcpServer(stdioServer(stdioName, VERSION),
                toolSpecification(stdioName));
        assertNotNull(stdioId);
        addCleanup(() -> maintainer.deleteMcpServer(Constants.DEFAULT_NAMESPACE_ID, stdioName,
                null, null));
        NacosException noRef = assertThrows(NacosException.class,
                () -> service.registerMcpServerEndpoint(stdioName, LOCALHOST, randomPort(),
                        VERSION));
        assertEquals(NacosException.NOT_FOUND, noRef.getErrCode(), noRef.toString());
    }

    @Test
    void shouldRejectInvalidHttpMcpArguments() throws Exception {
        AiService service = createHttpAiService();
        String mcpName = randomServiceName("mcp-http-invalid");

        assertInvalid(() -> service.releaseMcpServer(null, new McpToolSpecification(), false));
        assertInvalid(() -> service.releaseMcpServer(new McpServerBasicInfo(),
                new McpToolSpecification(), false));
        McpServerBasicInfo missingVersion = new McpServerBasicInfo();
        missingVersion.setName(mcpName);
        assertInvalid(() -> service.releaseMcpServer(missingVersion,
                new McpToolSpecification(), false));
        assertInvalid(() -> service.getMcpServer(""));
        assertInvalid(() -> service.registerMcpServerEndpoint(mcpName, "", randomPort()));
        assertInvalid(() -> service.registerMcpServerEndpoint(mcpName, LOCALHOST, 0));

        NacosException missing = assertThrows(NacosException.class,
                () -> service.getMcpServer(mcpName));
        assertEquals(NacosException.NOT_FOUND, missing.getErrCode(), missing.toString());
    }

    private AiService createHttpAiService() throws Exception {
        Properties properties = sdkProperties();
        properties.setProperty(AiConstants.AI_TRANSPORT_MODE, AiConstants.AI_TRANSPORT_MODE_HTTP);
        return createAiService(properties);
    }

    private McpMaintainerService createMcpMaintainerService() throws NacosException {
        Properties properties = sdkProperties();
        properties.setProperty(PropertyKeyConst.CONTEXT_PATH, "/nacos");
        return AiMaintainerFactory.createAiMaintainerService(properties).mcp();
    }

    private McpServerBasicInfo stdioServer(String mcpName, String version) {
        McpServerBasicInfo result = baseServer(mcpName, version);
        result.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_STDIO);
        return result;
    }

    private McpServerBasicInfo remoteServer(String mcpName, String version) {
        McpServerBasicInfo result = baseServer(mcpName, version);
        result.setProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        result.setFrontProtocol(AiConstants.Mcp.MCP_PROTOCOL_SSE);
        McpServerRemoteServiceConfig remoteConfig = new McpServerRemoteServiceConfig();
        remoteConfig.setExportPath("/mcp");
        remoteConfig.setFrontEndpointConfigList(Collections.emptyList());
        result.setRemoteServerConfig(remoteConfig);
        return result;
    }

    private McpServerBasicInfo baseServer(String mcpName, String version) {
        McpServerBasicInfo result = new McpServerBasicInfo();
        result.setName(mcpName);
        result.setDescription("MCP HTTP Java SDK IT");
        result.setVersion(version);
        ServerVersionDetail versionDetail = new ServerVersionDetail();
        versionDetail.setVersion(version);
        result.setVersionDetail(versionDetail);
        return result;
    }

    private McpToolSpecification toolSpecification(String mcpName) {
        McpTool tool = new McpTool();
        tool.setName("tool_" + mcpName.replace('-', '_'));
        tool.setDescription("MCP HTTP Java SDK IT tool");
        tool.setInputSchema(Collections.singletonMap("type", "object"));
        McpToolSpecification result = new McpToolSpecification();
        result.setTools(Collections.singletonList(tool));
        return result;
    }

    private McpEndpointSpec directEndpoint(int port) {
        McpEndpointSpec result = new McpEndpointSpec();
        result.setType(AiConstants.Mcp.MCP_ENDPOINT_TYPE_DIRECT);
        result.getData().put("address", LOCALHOST);
        result.getData().put("port", String.valueOf(port));
        result.getData().put("transportProtocol", AiConstants.Mcp.MCP_PROTOCOL_SSE);
        return result;
    }

    private McpResourceSpecification resourceSpecification(String mcpName) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("name", "resource_" + mcpName.replace('-', '_'));
        resource.put("uri", "nacos://" + mcpName + "/resource");
        McpResourceSpecification result = new McpResourceSpecification();
        result.setResources(Collections.singletonList(resource));
        return result;
    }

    private void assertServing(AiService service, String mcpName, String mcpId, String version)
            throws NacosException {
        McpServerDetailInfo exact = service.getMcpServer(mcpName, version);
        assertEquals(mcpId, exact.getId(), exact.toString());
        assertEquals(version, exact.getVersionDetail().getVersion(), exact.toString());
        assertNotNull(exact.getToolSpec(), exact.toString());
        assertNotNull(exact.getResourceSpec(), exact.toString());
        McpServerDetailInfo latest = service.getMcpServer(mcpName);
        assertEquals(version, latest.getVersionDetail().getVersion(), latest.toString());
    }

    private int countEndpoint(McpServerDetailInfo detail, int port) {
        if (detail.getBackendEndpoints() == null) {
            return 0;
        }
        int result = 0;
        for (McpEndpointInfo endpoint : detail.getBackendEndpoints()) {
            if (LOCALHOST.equals(endpoint.getAddress()) && port == endpoint.getPort()) {
                result++;
            }
        }
        return result;
    }

    private boolean containsEndpoint(McpServerDetailInfo detail, int port) {
        return countEndpoint(detail, port) > 0;
    }

    private void assertInvalid(CheckedRunnable runnable) {
        NacosException exception = assertThrows(NacosException.class, runnable::run);
        assertEquals(NacosException.INVALID_PARAM, exception.getErrCode(), exception.toString());
    }

    @FunctionalInterface
    private interface CheckedRunnable {

        void run() throws Exception;
    }
}
