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

package com.alibaba.nacos.test.openapi.auth;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Config HTTP namespace resolution through the deployed auth and MVC pipeline.
 *
 * <p>Scenario coverage: see CONFIG_NAMESPACE_API_TEST_SCENARIOS.md for the operation inventory,
 * defaults, binding boundaries, independent clone permissions, and rejected-request side effects.</p>
 *
 * @author xiweng.yy
 */
public class ConfigNamespaceResolutionOpenApiITCase extends AuthITCase {

    private static final String ADMIN_CONFIG = CONTEXT_PATH + "/v3/admin/cs/config";

    private static final String NAMESPACE = CONTEXT_PATH + "/v3/admin/core/namespace";

    static Stream<Endpoint> managementEndpoints() {
        return Stream.of(
            new Endpoint(false, "/clone", "POST", "namespaceId"),
            new Endpoint(false, "/import", "POST", "namespaceId"),
            new Endpoint(false, "/export", "GET", "namespaceId"),
            new Endpoint(false, "/batch", "DELETE", "namespaceId"),
            new Endpoint(true, "/clone", "POST", "targetNamespaceId"),
            new Endpoint(true, "/import", "POST", "namespaceId"),
            new Endpoint(true, "/export2", "GET", "namespaceId"),
            new Endpoint(true, "/batchDelete", "DELETE", "namespaceId"));
    }

    @ParameterizedTest
    @MethodSource("managementEndpoints")
    void testManagementBindingAndIdentityBoundaries(Endpoint endpoint) throws Exception {
        String explicit = endpoint.field() + "=public";
        assertDenied(call(endpoint, explicit, null));
        assertDenied(call(endpoint, explicit, "invalid-token"));
        assertDenied(call(endpoint, explicit, noPermissionToken()));
        assertEquals(200, call(endpoint, explicit, adminToken()).status());

        assertValidation(call(endpoint, explicit + "&" + endpoint.field() + "=public", adminToken()));
        assertValidation(call(endpoint, explicit + "&" + endpoint.field() + "=other", adminToken()));
        assertValidation(call(endpoint, endpoint.field() + "%5B%5D=other", adminToken()));
        assertEquals(200, call(endpoint,
            explicit + "&" + endpoint.field() + "%5B%5D=other&!" + endpoint.field() + "=other&tenant=ignored",
            adminToken()).status());
        if (endpoint.suffix().startsWith("/export")) {
            assertValidation(call(endpoint, "!namespaceId=other", adminToken()));
        } else if (!"/clone".equals(endpoint.suffix())) {
            assertEquals(200, call(endpoint, "!namespaceId=other", adminToken()).status());
        }
        if ("/clone".equals(endpoint.suffix())) {
            String source = endpoint.console() ? "namespaceId" : "sourceNamespaceId";
            assertValidation(call(endpoint, explicit + "&" + source + "=one&" + source + "=two",
                adminToken()));
            assertValidation(call(endpoint, explicit + "&" + source + "%5B%5D=one", adminToken()));
        }
    }

    @Test
    void testOrdinaryV3IgnoresTenantWithDefaultOrExplicitNamespace() throws Exception {
        String dataId = "namespace-query-" + UUID.randomUUID();
        String group = "namespace_it";
        createConfig(dataId, group, "public", "public-content");
        TestIdentity publicReader = createIdentityWithoutPermission("namespace-public");
        grantReadPermission(publicReader, "public:" + group + ":config/" + dataId);
        String path = CONTEXT_PATH + "/v3/client/cs/config?dataId=" + dataId + "&groupName=" + group;
        for (String query : new String[] {"", "&namespaceId=", "&namespaceId=public"}) {
            Response response = awaitSuccess(() -> get(SERVER_BASE_URL,
                path + query + "&tenant=ignored&tenant=another", publicReader.token()));
            assertEquals("public-content", assertSuccess(response).path("data").path("content").asText());
        }
        TestIdentity otherReader = createIdentityWithoutPermission("namespace-other");
        grantReadPermission(otherReader, "other:" + group + ":config/" + dataId);
        assertDenied(get(SERVER_BASE_URL, path + "&tenant=other", otherReader.token()));
        assertDenied(get(SERVER_BASE_URL, path + "&namespaceId=&tenant=other", otherReader.token()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testCloneChecksTargetWriteAndSourceReadWithEmptyPublicTarget(boolean console) throws Exception {
        String suffix = UUID.randomUUID().toString();
        String sourceNamespace = "clone-source-" + suffix;
        String sourceDataId = "source-" + suffix;
        String targetDataId = "target-" + suffix;
        String group = "namespace_it";
        assertSuccess(postForm(SERVER_BASE_URL, NAMESPACE, adminToken(),
            params("namespaceId", sourceNamespace, "namespaceName", sourceNamespace)));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, NAMESPACE, adminToken(),
            params("namespaceId", sourceNamespace)));
        createConfig(sourceDataId, group, sourceNamespace, "clone-content");
        Map<String, String> target = identity(targetDataId, group, "public");
        addCleanup(() -> deleteForm(SERVER_BASE_URL, ADMIN_CONFIG, adminToken(), target));
        JsonNode source = assertSuccess(get(SERVER_BASE_URL,
            configQuery(sourceDataId, group, sourceNamespace), adminToken())).path("data");
        String body = console
            ? "[{\"cfgId\":\"" + source.path("id").asText() + "\",\"dataId\":\""
                + targetDataId + "\",\"group\":\"" + group + "\"}]"
            : "[{\"configId\":\"" + source.path("id").asText() + "\",\"targetDataId\":\""
                + targetDataId + "\",\"targetGroupName\":\"" + group + "\"}]";
        Endpoint endpoint = new Endpoint(console, "/clone", "POST",
            console ? "targetNamespaceId" : "namespaceId");
        String query = endpoint.field() + "=&" + (console ? "namespaceId" : "sourceNamespaceId")
            + "=" + sourceNamespace + "&tenant=ignored";
        TestIdentity writer = createIdentityWithoutPermission("clone-writer");
        TestIdentity reader = createIdentityWithoutPermission("clone-reader");
        grantPermission(writer, "public:*:config/*", "w");
        grantReadPermission(reader, sourceNamespace + ":*:config/*");
        assertDenied(clone(endpoint, query, body, writer.token()));
        assertDenied(clone(endpoint, query, body, reader.token()));
        assertEquals(404, get(SERVER_BASE_URL, configQuery(targetDataId, group, "public"),
            adminToken()).status());
        grantReadPermission(writer, sourceNamespace + ":*:config/*");
        JsonNode cloned = assertSuccess(awaitSuccess(() -> clone(endpoint, query, body, writer.token())));
        assertEquals(1, cloned.path("data").path("succCount").asInt(), cloned.toString());
        assertEquals("clone-content", assertSuccess(get(SERVER_BASE_URL,
            configQuery(targetDataId, group, "public"), adminToken())).path("data").path("content").asText());
        assertEquals(404, get(SERVER_BASE_URL, configQuery(targetDataId, group, sourceNamespace),
            adminToken()).status());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testRejectedBatchDeleteLeavesConfigUnchanged(boolean console) throws Exception {
        String dataId = "batch-namespace-" + UUID.randomUUID();
        String group = "namespace_it";
        createConfig(dataId, group, "public", "unchanged");
        JsonNode config = assertSuccess(get(SERVER_BASE_URL,
            configQuery(dataId, group, "public"), adminToken())).path("data");
        Endpoint endpoint = new Endpoint(console, console ? "/batchDelete" : "/batch",
            "DELETE", "namespaceId");
        String path = endpoint.path() + "?ids=" + config.path("id").asText();
        assertValidation(requestWithAuthorization(RequestMethod.DELETE, endpoint.base(),
            path + "&namespaceId=public&namespaceId=other", "Bearer " + adminToken()));
        assertDenied(requestWithAuthorization(RequestMethod.DELETE, endpoint.base(),
            path + "&namespaceId=public", "Bearer " + noPermissionToken()));
        assertEquals("unchanged", assertSuccess(get(SERVER_BASE_URL,
            configQuery(dataId, group, "public"), adminToken())).path("data").path("content").asText());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testMultipartNamespaceIsTextAndSingleValued(boolean console) throws Exception {
        Endpoint endpoint = new Endpoint(console, "/import", "POST", "namespaceId");
        for (String name : new String[] {"namespaceId", "namespaceId[]"}) {
            String part = "Content-Disposition: form-data; name=\"" + name
                + "\"; filename=\"namespace.txt\"\r\nContent-Type: text/plain\r\n\r\npublic";
            assertValidation(multipart(endpoint, "", part));
        }
        String textPart = "Content-Disposition: form-data; name=\"namespaceId\"\r\n\r\npublic";
        assertValidation(multipart(endpoint, "namespaceId=public", textPart));
        // Tomcat caches parsed Parts; the normal import ITs verify that file contents remain readable.
        Response normal = multipart(endpoint, "", textPart);
        assertEquals(200, normal.status(), normal.body());
        assertEquals(ErrorCode.DATA_EMPTY.getCode(), JacksonUtils.toObj(normal.body()).path("code").asInt());
    }

    private Response multipart(Endpoint endpoint, String query, String part) throws Exception {
        String boundary = "nacosNamespaceBoundary";
        String body = "--" + boundary + "\r\n" + part + "\r\n--" + boundary + "--\r\n";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint.base() + endpoint.path()
                + "?" + query)).timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer " + adminToken())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        java.net.http.HttpResponse<String> response = httpClient.send(request,
            BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    private Response call(Endpoint endpoint, String query, String token) throws Exception {
        String path = endpoint.path() + "?" + query + "&ids=999999999999";
        if ("/clone".equals(endpoint.suffix())) {
            return postJsonWithAuthorization(endpoint.base(), path,
                token == null ? null : "Bearer " + token, "[]");
        }
        return requestWithAuthorization(RequestMethod.valueOf(endpoint.method()), endpoint.base(),
            path, token == null ? null : "Bearer " + token);
    }

    private Response clone(Endpoint endpoint, String query, String body, String token) throws Exception {
        return postJsonWithAuthorization(endpoint.base(), endpoint.path() + "?" + query,
            "Bearer " + token, body);
    }

    private void assertValidation(Response response) {
        assertEquals(400, response.status(), response.body());
        assertEquals(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(),
            JacksonUtils.toObj(response.body()).path("code").asInt(), response.body());
    }

    private void createConfig(String dataId, String group, String namespace, String content) throws Exception {
        Map<String, String> form = identity(dataId, group, namespace);
        form.put("content", content);
        assertSuccess(postForm(SERVER_BASE_URL, ADMIN_CONFIG, adminToken(), form));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, ADMIN_CONFIG, adminToken(),
            identity(dataId, group, namespace)));
    }

    private Map<String, String> identity(String dataId, String group, String namespace) {
        return params("dataId", dataId, "groupName", group, "namespaceId", namespace);
    }

    private String configQuery(String dataId, String group, String namespace) {
        return ADMIN_CONFIG + "?dataId=" + dataId + "&groupName=" + group + "&namespaceId=" + namespace;
    }

    record Endpoint(boolean console, String suffix, String method, String field) {

        String base() {
            return console ? CONSOLE_BASE_URL : SERVER_BASE_URL;
        }

        String path() {
            return (console ? "/v3/console/cs/config" : ADMIN_CONFIG) + suffix;
        }
    }
}
