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

package com.alibaba.nacos.test.openapi.hub;

import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.openapi.client.ai.McpClientOpenApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Deployed Direct/REF Hub imports, private draft lifecycle and Naming ownership. */
public class HubMcpAutomaticTransferOpenApiITCase extends McpClientOpenApiBaseITCase {

    @Test
    void directImportCreatesIndependentTargetServiceAndPreservesFrontendDefinitions() throws Exception {
        assumeTrue(AUTH_ENABLED, "Requires the auth-enabled, Hub-enabled LIFECYCLE_MANAGED baseline");
        String source = randomAiName("hub-fixed-source");
        String target = randomAiName("hub-fixed-copy");
        String namespace = "hub-fixed-" + UUID.randomUUID();
        String namespacePath = nacosPath("/v3/admin/core/namespace");
        postFormOk(namespacePath, Map.of("namespaceId", namespace, "namespaceName", namespace));
        addCleanup(() -> {
            deleteQuietly(ADMIN_MCP_PATH, Query.newInstance().addParam("namespaceId", namespace).addParam("mcpName", target));
            deleteMcpServerQuietly(source, null);
            deleteQuietly(namespacePath, Query.newInstance().addParam("namespaceId", namespace));
        });
        Map<String, Object> server = Map.of("name", source, "protocol", "mcp-streamable", "frontProtocol", "mcp-streamable",
            "versionDetail", Map.of("version", "1.0.0"), "remoteServerConfig", Map.of("exportPath", "/", "frontEndpointConfigList", List.of(
                Map.of("endpointType", "BACKEND", "endpointData", "one.example.test:443", "protocol", "https", "type", "streamable-http", "path", "/"),
                Map.of("endpointType", "BACKEND", "endpointData", "two.example.test:443", "protocol", "https", "type", "streamable-http", "path", "/mcp"))));
        Map<String, Object> endpoint = Map.of("type", "DIRECT", "data", Map.of("address", "one.example.test", "port", "443", "transportProtocol", "https"));
        postFormOk(ADMIN_MCP_PATH + "/draft", Map.of("namespaceId", DEFAULT_NAMESPACE, "mcpName", source, "version", "1.0.0",
            "serverSpecification", JacksonUtils.toJson(server), "endpointSpecification", JacksonUtils.toJson(endpoint)));
        postFormOk(ADMIN_MCP_PATH + "/force-publish", Map.of("namespaceId", DEFAULT_NAMESPACE, "mcpName", source, "version", "1.0.0"));
        putFormOk(ADMIN_MCP_PATH + "/scope", Map.of("namespaceId", DEFAULT_NAMESPACE, "mcpName", source, "scope", "PUBLIC"));
        String management = nacosPath("/v3/admin/ai/hub/mcp");
        JsonNode shared = postFormOk(management + "/share", Map.of("sourceNamespaceId", DEFAULT_NAMESPACE,
            "sourceName", source, "sourceVersion", "1.0.0")).path("data");
        String entryId = shared.path("hubEntryId").asText();
        assertFalse(entryId.isBlank());
        JsonNode imported = postFormOk(management + "/import", Map.of("hubEntryId", entryId, "targetNamespaceId", namespace,
            "targetName", target, "targetVersion", "0.0.1")).path("data");
        assertEquals("draft", imported.path("status").asText());
        Query query = Query.newInstance().addParam("namespaceId", namespace).addParam("mcpName", target).addParam("version", "0.0.1");
        JsonNode draft = getJsonOk(ADMIN_MCP_PATH + "/version", query).path("data");
        assertEquals("draft", draft.path("status").asText());
        JsonNode remote = draft.path("serverSpecification").path("remoteServerConfig");
        assertTrue(remote.hasNonNull("serviceRef"));
        assertEquals(2, remote.path("frontEndpointConfigList").size());
        assertEquals("BACKEND", remote.path("frontEndpointConfigList").get(0).path("endpointType").asText());
        assertEquals("/mcp", remote.path("frontEndpointConfigList").get(1).path("path").asText());
        JsonNode original = getJsonOk(ADMIN_MCP_PATH + "/version", mcpLifecycleVersionQuery(source, "1.0.0")).path("data");
        assertTrue(original.path("serverSpecification").path("remoteServerConfig").hasNonNull("serviceRef"));
        assertEquals(namespace, remote.path("serviceRef").path("namespaceId").asText());
        assertEquals("mcp-endpoints", remote.path("serviceRef").path("groupName").asText());
        assertEquals(target + "::0.0.1", remote.path("serviceRef").path("serviceName").asText());
        ObjectNode restoredSource = remote.deepCopy();
        restoredSource.set("serviceRef", original.path("serverSpecification").path("remoteServerConfig").path("serviceRef"));
        assertEquals(original.path("serverSpecification").path("remoteServerConfig"), restoredSource);
        assertEquals("PRIVATE", draft.path("scope").asText());
        assertError(getMcp(null, Query.newInstance().addParam("namespaceId", namespace).addParam("mcpName", target)),
            404, ErrorCode.MCP_SERVER_NOT_FOUND, "not found");
        JsonNode hub = getJsonOk(ADMIN_MCP_PATH, Query.newInstance().addParam("namespaceId", "__nacos_ai_hub__")
            .addParam("mcpName", entryId)).path("data");
        assertEquals(original.path("serverSpecification").path("remoteServerConfig").path("serviceRef"),
            hub.path("remoteServerConfig").path("serviceRef"));
        assertEquals("BACKEND", original.path("serverSpecification").path("remoteServerConfig").path("frontEndpointConfigList").get(0).path("endpointType").asText());
        // Publishing is a separate explicit test action; import itself leaves the resource a draft.
        postFormOk(ADMIN_MCP_PATH + "/force-publish", Map.of("namespaceId", namespace, "mcpName", target, "version", "0.0.1"));
        JsonNode serving = getJsonOk(MCP_CLIENT_PATH, query).path("data");
        JsonNode sourceServing = getJsonOk(MCP_CLIENT_PATH,
            mcpLifecycleVersionQuery(source, "1.0.0")).path("data");
        assertEquals(sourceServing.path("backendEndpoints"), serving.path("backendEndpoints"));
        assertEquals(sourceServing.path("frontendEndpoints"), serving.path("frontendEndpoints"));
        assertEquals(2, serving.path("frontendEndpoints").size());
        assertEquals("one.example.test", serving.path("frontendEndpoints").get(0).path("address").asText());
        assertEquals("/mcp", serving.path("frontendEndpoints").get(1).path("path").asText());
        JsonNode services = getJsonOk(nacosPath("/v3/admin/ns/service/list"), Query.newInstance().addParam("namespaceId", namespace)
            .addParam("pageNo", "1").addParam("pageSize", "10")).path("data");
        assertEquals(1, services.path("totalCount").asInt(-1), "Direct import owns one target Naming service");
        postFormOk(management + "/unshare", Map.of("hubEntryId", entryId,
            "expectedMetaVersion", shared.path("metaVersion").asText()));
        assertEquals(1, getJsonOk(MCP_CLIENT_PATH, query).path("data").path("backendEndpoints").size());
        postFormOk(ADMIN_MCP_PATH + "/offline", Map.of("namespaceId", namespace, "mcpName", target, "version", "0.0.1"));
        assertEquals(1, targetServiceCount(namespace));
        deleteJsonOk(ADMIN_MCP_PATH, Query.newInstance().addParam("namespaceId", namespace).addParam("mcpName", target));
        assertEquals(0, targetServiceCount(namespace));
        assertEquals(1, getJsonOk(MCP_CLIENT_PATH, mcpLifecycleVersionQuery(source, "1.0.0"))
            .path("data").path("backendEndpoints").size());
    }
    @Test
    void refImportAndDeletionPreserveUserOwnedService() throws Exception {
        assumeTrue(AUTH_ENABLED, "Requires auth and Hub enabled managed lifecycle");
        String source = randomAiName("hub-ref-source");
        String target = randomAiName("hub-ref-target");
        String namespace = "hub-ref-" + UUID.randomUUID();
        String service = randomAiName("hub-user-service");
        String namespacePath = nacosPath("/v3/admin/core/namespace");
        String namingPath = nacosPath("/v3/admin/ns/service");
        postFormOk(namespacePath, Map.of("namespaceId", namespace, "namespaceName", namespace));
        postFormOk(namingPath, Map.of("namespaceId", DEFAULT_NAMESPACE, "groupName", "user-group", "serviceName", service, "ephemeral", "false"));
        addCleanup(() -> {
            deleteQuietly(ADMIN_MCP_PATH, Query.newInstance().addParam("namespaceId", namespace).addParam("mcpName", target));
            deleteMcpServerQuietly(source, null);
            deleteQuietly(namingPath, Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE).addParam("groupName", "user-group").addParam("serviceName", service));
            deleteQuietly(namespacePath, Query.newInstance().addParam("namespaceId", namespace));
        });
        Map<String, Object> server = Map.of("name", source, "protocol", "mcp-streamable", "versionDetail", Map.of("version", "1.0.0"));
        Map<String, Object> endpoint = Map.of("type", "REF", "data", Map.of("namespaceId", DEFAULT_NAMESPACE, "groupName", "user-group", "serviceName", service));
        postFormOk(ADMIN_MCP_PATH + "/draft", Map.of("namespaceId", DEFAULT_NAMESPACE, "mcpName", source, "version", "1.0.0",
            "serverSpecification", JacksonUtils.toJson(server), "endpointSpecification", JacksonUtils.toJson(endpoint)));
        postFormOk(ADMIN_MCP_PATH + "/force-publish", Map.of("namespaceId", DEFAULT_NAMESPACE, "mcpName", source, "version", "1.0.0"));
        putFormOk(ADMIN_MCP_PATH + "/scope", Map.of("namespaceId", DEFAULT_NAMESPACE, "mcpName", source, "scope", "PUBLIC"));
        String management = nacosPath("/v3/admin/ai/hub/mcp");
        String entry = postFormOk(management + "/share", Map.of("sourceNamespaceId", DEFAULT_NAMESPACE,
            "sourceName", source, "sourceVersion", "1.0.0")).path("data").path("hubEntryId").asText();
        postFormOk(management + "/import", Map.of("hubEntryId", entry, "targetNamespaceId", namespace,
            "targetName", target, "targetVersion", "0.0.1"));
        JsonNode imported = getJsonOk(ADMIN_MCP_PATH + "/version", Query.newInstance().addParam("namespaceId", namespace)
            .addParam("mcpName", target).addParam("version", "0.0.1")).path("data");
        JsonNode ref = imported.path("serverSpecification").path("remoteServerConfig").path("serviceRef");
        assertEquals("draft", imported.path("status").asText());
        assertEquals(DEFAULT_NAMESPACE, ref.path("namespaceId").asText());
        assertEquals(service, ref.path("serviceName").asText());
        assertEquals(0, targetServiceCount(namespace));
        deleteJsonOk(ADMIN_MCP_PATH, Query.newInstance().addParam("namespaceId", namespace).addParam("mcpName", target));
        getJsonOk(namingPath, Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)
            .addParam("groupName", "user-group").addParam("serviceName", service));
    }

    private int targetServiceCount(String namespace) throws Exception {
        return getJsonOk(nacosPath("/v3/admin/ns/service/list"), Query.newInstance().addParam("namespaceId", namespace)
            .addParam("pageNo", "1").addParam("pageSize", "10")).path("data").path("totalCount").asInt(-1);
    }

}
