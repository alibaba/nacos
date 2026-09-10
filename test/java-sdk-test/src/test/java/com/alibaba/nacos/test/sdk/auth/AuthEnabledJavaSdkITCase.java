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

package com.alibaba.nacos.test.sdk.auth;

import com.alibaba.nacos.api.ai.AgentTransportMode;
import com.alibaba.nacos.api.ai.AiService;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.agent.Endpoint;
import com.alibaba.nacos.api.ai.model.agent.AgentEndpointRegistration;
import com.alibaba.nacos.api.ai.model.agent.AgentSearchQuery;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Cross-cutting authentication and authorization tests for the public Java SDK.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Config and Naming use a non-admin read-write identity for normal work, allow a
 *     read-only identity to query existing state, and reject its writes without side effects.</li>
 *     <li>Anonymous, invalid, and authenticated-no-permission clients receive controlled
 *     {@link NacosException#NO_RIGHT} failures or the documented {@code false} Config write
 *     result instead of cache hits, timeouts, or uncontrolled runtime exceptions.</li>
 *     <li>AI propagates read and write identity through explicit HTTP, explicit gRPC, and AUTO;
 *     anonymous reads follow the configured public-AI policy while anonymous writes stay
 *     protected, and invalid credentials never downgrade to anonymous access.</li>
 * </ul>
 *
 * <p>Lock remains an experimental SDK whose server handler does not yet apply its documented
 * {@code SignType.LOCK} authorization contract. Its complete functional lifecycle still runs
 * with the standard authenticated client in {@code LockServiceJavaSdkITCase}; the missing server
 * guard is recorded as a precise coverage gap instead of being represented by a false denial
 * assertion here.
 *
 * @author Nacos
 */
public class AuthEnabledJavaSdkITCase extends JavaSdkBaseITCase {

    private static final String AI_PROTOCOL = "a2a";

    private static final String AI_RUNTIME_VERSION = "1.0.0";

    private static final long AUTH_RESULT_TIMEOUT_MS = 10000L;

    @Test
    void shouldEnforceConfigIdentityAndActionBoundariesWithoutCacheFallback() throws Exception {
        assumeAuthEnabled();

        ConfigService readWrite = createConfigService();
        String dataId = randomDataId("auth-matrix");
        String group = randomGroup("auth-matrix");
        String content = "nacos.java.sdk.auth.enabled=true";
        addCleanup(() -> readWrite.removeConfig(dataId, group));

        assertTrue(readWrite.publishConfig(dataId, group, content));
        waitUntil("authenticated SDK config should become readable",
                () -> content.equals(readWrite.getConfig(dataId, group, DEFAULT_TIMEOUT_MS)));

        ConfigService readOnly = createConfigService(sdkProperties(AuthIdentity.CLIENT_READ_ONLY));
        assertEquals(content, readOnly.getConfig(dataId, group, DEFAULT_TIMEOUT_MS));
        assertFalse(readOnly.publishConfig(dataId, group, "read-only-overwrite"));
        assertEquals(content, readWrite.getConfig(dataId, group, DEFAULT_TIMEOUT_MS));

        verifyConfigDenied(readWrite, createConfigServiceWithoutReadiness(
                sdkProperties(AuthIdentity.ANONYMOUS)), dataId, group, content);
        verifyConfigDenied(readWrite, createConfigServiceWithoutReadiness(
                sdkProperties(AuthIdentity.CLIENT_NO_PERMISSION)), dataId, group, content);
        verifyConfigDenied(readWrite,
                createConfigServiceWithoutReadiness(invalidCredentialProperties()), dataId,
                group, content);
    }

    @Test
    void shouldEnforceNamingIdentityAndActionBoundariesWithoutCacheFallback() throws Exception {
        assumeAuthEnabled();

        NamingService readWrite = createNamingService();
        String serviceName = randomServiceName("auth-matrix");
        String deniedServiceName = randomServiceName("auth-read-only-write");
        String group = randomGroup("auth-matrix");
        Instance instance = instance(randomPort());
        Instance deniedInstance = instance(randomPort());
        addCleanup(() -> readWrite.deregisterInstance(serviceName, group, instance));

        readWrite.registerInstance(serviceName, group, instance);
        waitUntil("authenticated SDK instance should become readable",
                () -> containsPort(readWrite.getAllInstances(serviceName, group, false),
                        instance.getPort()));

        NamingService readOnly = createNamingService(sdkProperties(AuthIdentity.CLIENT_READ_ONLY));
        assertTrue(containsPort(readOnly.getAllInstances(serviceName, group, false),
                instance.getPort()));
        assertNoRight(() -> readOnly.registerInstance(deniedServiceName, group, deniedInstance));
        assertTrue(readWrite.getAllInstances(deniedServiceName, group, false).isEmpty());

        verifyNamingDenied(readWrite, createNamingServiceWithoutReadiness(
                sdkProperties(AuthIdentity.ANONYMOUS)), serviceName, group);
        verifyNamingDenied(readWrite, createNamingServiceWithoutReadiness(
                sdkProperties(AuthIdentity.CLIENT_NO_PERMISSION)), serviceName, group);
        verifyNamingDenied(readWrite,
                createNamingServiceWithoutReadiness(invalidCredentialProperties()), serviceName,
                group);
    }

    @Test
    void shouldPropagateAiAuthorizationInGrpcMode() throws Exception {
        verifyAiAuthorization(AgentTransportMode.GRPC);
    }

    @Test
    void shouldPropagateAiAuthorizationInHttpMode() throws Exception {
        verifyAiAuthorization(AgentTransportMode.HTTP);
    }

    @Test
    void shouldPropagateAiAuthorizationInAutoMode() throws Exception {
        verifyAiAuthorization(AgentTransportMode.AUTO);
    }

    @Disabled("DAUTH-F04: invalid credentials currently downgrade to anonymous AI access; "
            + "see UNEXPECTED_PRODUCT_FINDINGS.md")
    @Test
    void shouldRejectInvalidCredentialsInsteadOfDowngradingToAnonymousAi() throws Exception {
        assumeAuthEnabled();

        AiService readWrite = createAiService(
                aiProperties(AuthIdentity.CLIENT_READ_WRITE, AgentTransportMode.HTTP));
        assertNull(readWrite.loadAgentSpec(randomServiceName("missing-agent-spec")));

        AiService readOnly = createAiService(
                aiProperties(AuthIdentity.CLIENT_READ_ONLY, AgentTransportMode.HTTP));
        assertNull(readOnly.loadAgentSpec(randomServiceName("missing-agent-spec")));

        AiService noPermission = createAiServiceWithoutReadiness(
                aiProperties(AuthIdentity.CLIENT_NO_PERMISSION, AgentTransportMode.HTTP));
        assertNoRight(() -> noPermission.loadAgentSpec(
                randomServiceName("missing-agent-spec")));

        AiService invalid = createAiServiceWithoutReadiness(
                invalidAiProperties(AgentTransportMode.HTTP));
        assertNoRight(() -> invalid.loadAgentSpec(randomServiceName("missing-agent-spec")));

        AiService anonymous = createAiServiceWithoutReadiness(
                aiProperties(AuthIdentity.ANONYMOUS, AgentTransportMode.HTTP));
        if (ANONYMOUS_AI_ENABLED) {
            assertNull(anonymous.loadAgentSpec(randomServiceName("missing-agent-spec")));
        } else {
            assertNoRight(() -> anonymous.loadAgentSpec(
                    randomServiceName("missing-agent-spec")));
        }
    }

    private void verifyConfigDenied(ConfigService readWrite, ConfigService service, String dataId,
            String group, String expectedContent) throws Exception {
        assertNoRight(() -> service.getConfig(dataId, group, DEFAULT_TIMEOUT_MS));
        assertFalse(service.publishConfig(dataId, group, "unauthorized-overwrite"));
        assertEquals(expectedContent, readWrite.getConfig(dataId, group, DEFAULT_TIMEOUT_MS));
    }

    private void verifyNamingDenied(NamingService readWrite, NamingService service,
            String serviceName, String group) throws Exception {
        assertNoRight(() -> service.getAllInstances(serviceName, group, false));
        assertNoRight(() -> service.getAllInstances(serviceName, group, true));
        String deniedServiceName = randomServiceName("auth-denied-write");
        assertNoRight(() -> service.registerInstance(deniedServiceName, group,
                instance(randomPort())));
        assertTrue(readWrite.getAllInstances(deniedServiceName, group, false).isEmpty());
    }

    private void verifyAiAuthorization(AgentTransportMode mode) throws Exception {
        assumeAuthEnabled();

        AiService readWrite = createAiService(aiProperties(AuthIdentity.CLIENT_READ_WRITE, mode));
        assertNotNull(readWrite.agent().searchAgents(searchRequest()));

        AiService readOnly = createAiService(aiProperties(AuthIdentity.CLIENT_READ_ONLY, mode));
        assertNotNull(readOnly.agent().searchAgents(searchRequest()));
        assertNoRight(() -> readOnly.agent().registerAgentEndpoints(endpointBatch(mode, "readonly")));

        AiService noPermission = createAiServiceWithoutReadiness(
                aiProperties(AuthIdentity.CLIENT_NO_PERMISSION, mode));
        assertNoRight(() -> noPermission.agent().searchAgents(searchRequest()));
        assertNoRight(() -> noPermission.agent().registerAgentEndpoints(
                endpointBatch(mode, "no-permission")));

        AiService invalid = createAiServiceWithoutReadiness(invalidAiProperties(mode));
        assertNoRight(() -> invalid.agent().searchAgents(searchRequest()));
        assertNoRight(() -> invalid.agent().registerAgentEndpoints(endpointBatch(mode, "invalid")));

        AiService anonymous = createAiServiceWithoutReadiness(
                aiProperties(AuthIdentity.ANONYMOUS, mode));
        assertNoRight(() -> anonymous.agent().searchAgents(searchRequest()));
        assertNoRight(() -> anonymous.agent().registerAgentEndpoints(endpointBatch(mode, "anonymous")));
    }

    private Properties aiProperties(AuthIdentity identity, AgentTransportMode mode) {
        Properties result = sdkProperties(identity);
        result.setProperty(AiConstants.AI_TRANSPORT_MODE, mode.getValue());
        return result;
    }

    private Properties invalidAiProperties(AgentTransportMode mode) {
        Properties result = invalidCredentialProperties();
        result.setProperty(AiConstants.AI_TRANSPORT_MODE, mode.getValue());
        return result;
    }

    private AgentSearchQuery searchRequest() {
        AgentSearchQuery result = new AgentSearchQuery();
        result.setAgentNameContains(randomServiceName("auth-search"));
        result.setPageNo(1);
        result.setPageSize(1);
        return result;
    }

    private AgentEndpointRegistration endpointBatch(AgentTransportMode mode,
            String identity) {
        Endpoint endpoint = new Endpoint();
        endpoint.setUri("http://127.0.0.1:" + randomPort() + "/auth");
        endpoint.setTransport("HTTP");
        endpoint.setPriority(0);
        endpoint.setWeight(1D);
        AgentEndpointRegistration result = new AgentEndpointRegistration();
        result.setAgentName(randomServiceName("auth-" + mode.getValue() + '-' + identity));
        result.setRuntimeVersion(AI_RUNTIME_VERSION);
        result.setProtocol(AI_PROTOCOL);
        result.setEndpoints(Collections.singletonList(endpoint));
        return result;
    }

    private Instance instance(int port) {
        Instance result = new Instance();
        result.setIp("127.0.0.1");
        result.setPort(port);
        return result;
    }

    private boolean containsPort(List<Instance> instances, int port) {
        return instances.stream().anyMatch(each -> port == each.getPort());
    }

    private void assertNoRight(RemoteCall call) throws Exception {
        long deadline = System.currentTimeMillis() + AUTH_RESULT_TIMEOUT_MS;
        NacosException lastDisconnect = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                call.run();
                fail("Protected SDK operation unexpectedly succeeded");
            } catch (NacosException exception) {
                if (NacosException.NO_RIGHT == exception.getErrCode()) {
                    return;
                }
                if (NacosException.CLIENT_DISCONNECT != exception.getErrCode()) {
                    assertEquals(NacosException.NO_RIGHT, exception.getErrCode(),
                            exception.toString());
                }
                lastDisconnect = exception;
            }
            Thread.sleep(200L);
        }
        fail("Protected SDK operation did not return NO_RIGHT within "
                + AUTH_RESULT_TIMEOUT_MS + " ms", lastDisconnect);
    }

    private void assumeAuthEnabled() {
        assumeTrue(AUTH_ENABLED, "Auth-enabled SDK matrix only runs in the migration job");
    }

    @FunctionalInterface
    private interface RemoteCall {

        void run() throws Exception;
    }
}
