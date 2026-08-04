/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.test.openapi.client.ai;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for code-first Agent publication under
 * {@code POST /nacos/v3/client/ai/agents}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: draft-only publication, equivalent retry, auto-submit resume,
 *     direct and inherited Version evolution, and HTTP/Admin/Console/RAD/legacy A2A projection
 *     all converge on one canonical definition.</li>
 *     <li>Boundary/validation: default and custom namespaces remain isolated; Form JSON fields,
 *     direct-versus-inherited content, and first-Version inheritance are validated; publishing a
 *     definition never creates a Runtime Endpoint.</li>
 *     <li>Exception/error handling: conflicting content or initial metadata, draft-only retry of
 *     an advanced Version, retry of an offline Version, malformed JSON, and missing identity are
 *     returned as controlled 4xx envelopes.</li>
 * </ul>
 *
 * @author Nacos
 */
public class AgentPublishClientOpenApiITCase extends AgentClientOpenApiBaseITCase {

    private static final String VERSION_ONE = "1.0.0";

    private static final String VERSION_TWO = "2.0.0";

    private static final String VERSION_THREE = "3.0.0";

    @Test
    public void testDraftResumeRetryAndCrossSurfaceProjection() throws Exception {
        String agentName = randomAiName("agent-client-publish");
        Map<String, Object> request = legacyCompatibleRequest(null, agentName, VERSION_ONE,
                "initial");
        addCleanup(() -> deleteAgentDefinitionQuietly(DEFAULT_NAMESPACE, agentName));

        JsonNode draft = publish(request, false);
        assertEquals("draft", draft.get("status").asText(), draft.toString());
        assertEquals(DEFAULT_NAMESPACE, draft.get("namespaceId").asText(), draft.toString());
        String digest = draft.get("contentDigest").asText();
        assertTrue(digest.startsWith("sha256:"), draft.toString());
        assertEquals(digest, publish(request, false).get("contentDigest").asText());
        assertError(getRaw(AGENT_CLIENT_PATH,
                Query.newInstance().addParam("agentName", agentName)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "not discoverable");

        JsonNode online = publish(request, true);
        assertEquals("online", online.get("status").asText(), online.toString());
        assertEquals(digest, online.get("contentDigest").asText(), online.toString());
        assertEquals(digest, publish(request, true).get("contentDigest").asText());

        JsonNode adminVersion = getJsonOk(ADMIN_AGENT_VERSION_PATH,
                agentVersionIdentityQuery(null, agentName, VERSION_ONE)).get("data");
        assertEquals("online", adminVersion.get("status").asText(), adminVersion.toString());
        assertEquals("Nacos OpenAPI IT",
                getJsonOk(ADMIN_AGENT_PATH, agentIdentityQuery(null, agentName)).get("data")
                        .get("agent").get("provider").get("name").asText());
        JsonNode consoleVersion = getConsoleJsonOk(CONSOLE_AGENT_PATH + "/version",
                agentVersionIdentityQuery(null, agentName, VERSION_ONE)).get("data");
        assertEquals(digest, consoleVersion.get("contentDigest").asText(),
                consoleVersion.toString());

        JsonNode discovered = getJsonOk(AGENT_CLIENT_PATH,
                Query.newInstance().addParam("agentName", agentName)).get("data");
        assertEquals(VERSION_ONE, discovered.get("version").asText(), discovered.toString());
        assertEquals(digest, discovered.get("contentDigest").asText(), discovered.toString());
        assertTrue(runtimeEndpoints(discovered).isEmpty(), discovered.toString());

        JsonNode legacy = getJsonOk(ADMIN_A2A_PATH,
                Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
                        .addParam("agentName", agentName).addParam("version", VERSION_ONE)
                        .addParam("registrationType",
                                AiConstants.A2a.A2A_ENDPOINT_TYPE_URL)).get("data");
        assertEquals(agentName, legacy.get("name").asText(), legacy.toString());
        assertEquals(VERSION_ONE, legacy.get("version").asText(), legacy.toString());
        assertEquals(2, legacy.get("supportedInterfaces").size(), legacy.toString());

        assertError(postFormRaw(AGENT_CLIENT_PATH, publishForm(request, false)), 400,
                ErrorCode.ILLEGAL_STATE, "status must be draft");

        Map<String, Object> conflictingContent = legacyCompatibleRequest(null, agentName,
                VERSION_ONE, "different-content");
        assertError(postFormRaw(AGENT_CLIENT_PATH,
                publishForm(conflictingContent, true)), 409,
                ErrorCode.RESOURCE_CONFLICT, "different content");

        Map<String, Object> conflictingMetadata = new LinkedHashMap<>(request);
        conflictingMetadata.put("description", "different initial metadata");
        assertError(postFormRaw(AGENT_CLIENT_PATH,
                publishForm(conflictingMetadata, true)), 409,
                ErrorCode.RESOURCE_CONFLICT, "initial metadata");
    }

    @Test
    public void testVersionEvolutionCustomNamespaceAndIllegalState() throws Exception {
        String namespaceId = randomAiName("agent-publish-namespace");
        String agentName = randomAiName("agent-publish-version");
        addCleanup(() -> deleteAgentDefinitionQuietly(namespaceId, agentName));

        Map<String, Object> initial = agentInitialDraftRequest(namespaceId, agentName,
                VERSION_ONE);
        JsonNode first = publish(initial, true);
        assertEquals("online", first.get("status").asText(), first.toString());

        Map<String, Object> direct = agentDraftCreateRequest(namespaceId, agentName,
                VERSION_TWO, null);
        JsonNode second = publish(direct, true);
        assertEquals("online", second.get("status").asText(), second.toString());
        assertFalse(first.get("contentDigest").asText()
                .equals(second.get("contentDigest").asText()), second.toString());

        Map<String, Object> inherited = agentDraftCreateRequest(namespaceId, agentName,
                VERSION_THREE, VERSION_TWO);
        JsonNode third = publish(inherited, true);
        assertEquals("online", third.get("status").asText(), third.toString());
        assertEquals(second.get("contentDigest").asText(),
                third.get("contentDigest").asText(), third.toString());

        assertError(getRaw(AGENT_CLIENT_PATH,
                Query.newInstance().addParam("agentName", agentName)), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "agent not found");
        JsonNode discovered = getJsonOk(AGENT_CLIENT_PATH,
                Query.newInstance().addParam("namespaceId", namespaceId)
                        .addParam("agentName", agentName)).get("data");
        assertEquals(VERSION_THREE, discovered.get("version").asText(),
                discovered.toString());
        assertTrue(runtimeEndpoints(discovered).isEmpty(), discovered.toString());

        Map<String, Object> bothSources = new LinkedHashMap<>(inherited);
        bothSources.put("version", "4.0.0");
        bothSources.put("callInterfaces", direct.get("callInterfaces"));
        assertError(postFormRaw(AGENT_CLIENT_PATH, publishForm(bothSources, false)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "either callInterfaces or basedOnVersion");

        Map<String, Object> neitherSource = agentVersionCommand(namespaceId, agentName,
                "4.0.0");
        assertError(postFormRaw(AGENT_CLIENT_PATH, publishForm(neitherSource, false)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "either callInterfaces or basedOnVersion");

        String inheritanceOnlyAgent = randomAiName("agent-publish-inherit-first");
        addCleanup(() -> deleteAgentDefinitionQuietly(namespaceId, inheritanceOnlyAgent));
        Map<String, Object> firstInheritance = agentDraftCreateRequest(namespaceId,
                inheritanceOnlyAgent, VERSION_ONE, VERSION_TWO);
        assertError(postFormRaw(AGENT_CLIENT_PATH,
                publishForm(firstInheritance, false)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "basedOnVersion");

        postFormOk(ADMIN_AGENT_PATH + "/offline",
                agentForm(agentVersionCommand(namespaceId, agentName, VERSION_TWO)));
        assertError(postFormRaw(AGENT_CLIENT_PATH, publishForm(direct, true)), 400,
                ErrorCode.ILLEGAL_STATE, "submitted state");
    }

    @Test
    public void testPublicationFormValidation() throws Exception {
        Map<String, Object> missingName = agentVersionCommand(null, "", VERSION_ONE);
        missingName.put("callInterfaces", Collections.emptyList());
        assertError(postFormRaw(AGENT_CLIENT_PATH, publishForm(missingName, false)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "agentName");

        Map<String, String> malformed = agentForm(agentVersionCommand(null,
                randomAiName("agent-publish-invalid-json"), VERSION_ONE));
        malformed.put("callInterfaces", "{");
        assertError(postFormRaw(AGENT_CLIENT_PATH, malformed), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "callInterfaces");

        Map<String, String> invalidBoolean = agentForm(agentInitialDraftRequest(null,
                randomAiName("agent-publish-invalid-boolean"), VERSION_ONE));
        invalidBoolean.put("autoSubmit", "maybe");
        assertError(postFormRaw(AGENT_CLIENT_PATH, invalidBoolean), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "autoSubmit");
    }

    private JsonNode publish(Map<String, Object> request, boolean autoSubmit) throws Exception {
        return postFormOk(AGENT_CLIENT_PATH, publishForm(request, autoSubmit)).get("data");
    }

    private Map<String, String> publishForm(Map<String, Object> request, boolean autoSubmit) {
        Map<String, String> result = agentForm(request);
        result.put("autoSubmit", String.valueOf(autoSubmit));
        return result;
    }

    private Map<String, Object> legacyCompatibleRequest(String namespaceId, String agentName,
            String version, String marker) {
        Map<String, Object> result = agentInitialDraftRequest(namespaceId, agentName, version);
        Map<String, Object> descriptor = JacksonUtils.toObj(
                buildV1AgentCard(agentName, version, "1.0"), Map.class);
        descriptor.put("description", marker);
        Map<String, Object> callInterface = new LinkedHashMap<>();
        callInterface.put("protocol", "a2a");
        callInterface.put("protocolVersion", "1.0");
        callInterface.put("descriptorMediaType", "application/json");
        callInterface.put("nativeDescriptor", descriptor);
        callInterface.put("endpointSourceOrder",
                Arrays.asList("DECLARED", "RUNTIME"));
        callInterface.put("declaredEndpoints", Arrays.asList(
                declaredEndpoint("https://example.com/" + agentName + "/jsonrpc", "JSONRPC"),
                declaredEndpoint("https://example.com/" + agentName + "/grpc", "GRPC")));
        result.put("callInterfaces", Collections.singletonList(callInterface));
        return result;
    }

    private Map<String, Object> declaredEndpoint(String uri, String transport) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uri", uri);
        result.put("transport", transport);
        return result;
    }

    private JsonNode runtimeEndpoints(JsonNode discovery) {
        for (JsonNode endpointSet : discovery.get("callInterfaces").get(0)
                .get("endpointSets")) {
            if ("RUNTIME".equals(endpointSet.get("source").asText())) {
                return endpointSet.get("endpoints");
            }
        }
        throw new AssertionError("Runtime Endpoint set is missing: " + discovery);
    }
}
