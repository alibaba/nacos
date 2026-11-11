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

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Account/scope matrix against the default auth and visibility plugins.
 *
 * <p>Scenario coverage: owner, administrator, unrelated user, explicit r/rw grantees and
 * anonymous requests exercise detail, list/count/page, search, metadata writes and deletion.
 * Two separate server configurations cover anonymous disabled/enabled. Grant downgrade,
 * revocation, scope changes, credential and management boundaries remain independent checks.
 * Enable explicitly with nacos.test.visibility.matrix.enabled=true. See
 * AI_VISIBILITY_ACCOUNT_MATRIX.md for prerequisites and known fixture limitations.</p>
 */
@EnabledIfSystemProperty(named = "nacos.test.visibility.matrix.enabled", matches = "true")
public class AiVisibilityAccountMatrixOpenApiITCase extends AuthITCase {
    
    private static final String GRANT_PATH = CONTEXT_PATH + "/v3/auth/visibility";
    
    private static final String ARD_URL = "http://" + NACOS_HOST + ':'
        + System.getProperty("nacos.ai.registry.port", "9080");
    
    private static final boolean ANONYMOUS_ENABLED = Boolean.parseBoolean(
        System.getProperty("nacos.test.auth.anonymous-ai.enabled", "false"));
    
    private static final String VERSION = "1.0.0";

    private static final String ONLY_CASE = System.getProperty("nacos.test.visibility.matrix.only", "");
    
    private final Forms forms = new Forms();
    
    private final List<Executable> failures = new ArrayList<>();
    
    @ParameterizedTest(name = "{0}-{1}")
    @CsvSource({"agent,PRIVATE", "agent,PUBLIC", "mcp,PRIVATE", "mcp,PUBLIC",
        "prompt,PRIVATE", "prompt,PUBLIC", "skill,PRIVATE", "skill,PUBLIC",
        "agentspec,PRIVATE", "agentspec,PUBLIC"})
    void accountScopeMatrix(String type, String scope) throws Exception {
        assumeTrue(ONLY_CASE.isBlank() || ONLY_CASE.equals(type + '/' + scope),
            "A different matrix case was selected");
        String group = (ANONYMOUS_ENABLED ? "anonymous-on/" : "anonymous-off/")
            + type + '/' + scope;
        if ("prompt".equals(type) && "PUBLIC".equals(scope)) {
            record("group", group, "BLOCKED", "No public Prompt scope creation/update API");
        }
        assumeFalse("prompt".equals(type) && "PUBLIC".equals(scope),
            "No public API can prepare a PUBLIC Prompt with the default visibility plugin");
        TestIdentity owner = identity("vm-owner");
        Fixture fixture = createFixture(type, scope, owner);
        Actor anonymous = new Actor("anonymous", null, null, null);
        if (!ANONYMOUS_ENABLED) {
            exerciseActor(group, fixture, owner, anonymous, false, false);
            assertAll(group, failures);
            return;
        }
        TestIdentity other = identity("vm-other");
        TestIdentity reader = identity("vm-reader");
        TestIdentity writer = identity("vm-writer");
        grant(fixture, reader.username(), "r", owner.token());
        grant(fixture, writer.username(), "rw", owner.token());
        assertReadableEventually(fixture, reader.token());
        assertReadableEventually(fixture, writer.token());
        exerciseActor(group, fixture, owner,
            new Actor("owner", owner.username(), owner.token(), null), true, true);
        exerciseActor(group, fixture, owner,
            new Actor("admin", "nacos-admin", adminToken(), null), true, true);
        exerciseActor(group, fixture, owner,
            new Actor("other", other.username(), other.token(), null),
            "PUBLIC".equals(scope), false);
        exerciseActor(group, fixture, owner,
            new Actor("reader", reader.username(), reader.token(), "r"), true, false);
        exerciseActor(group, fixture, owner,
            new Actor("writer", writer.username(), writer.token(), "rw"), true, true);
        exerciseActor(group, fixture, owner, anonymous, "PUBLIC".equals(scope), false);
        boundaries(group, fixture, owner, other, reader, writer);
        assertAll(group, failures);
    }
    
    private TestIdentity identity(String prefix) throws Exception {
        TestIdentity identity = createIdentityWithoutPermission(prefix);
        grantPermission(identity, "*:*:ai/*", "rw");
        Response probe = awaitAuthorized(() -> get(SERVER_BASE_URL,
            CONTEXT_PATH + "/v3/admin/ai/agents?namespaceId=public&agentName=missing-matrix",
            identity.token()));
        assertEquals(404, probe.status(), probe.body());
        return identity;
    }

    @ParameterizedTest(name = "isolation-and-pages-{0}")
    @ValueSource(strings = {"agent", "mcp", "prompt", "skill", "agentspec"})
    void isolationAndMixedPages(String type) throws Exception {
        assumeTrue(ONLY_CASE.isBlank() || ONLY_CASE.equals("boundary/" + type),
            "A different matrix case was selected");
        assumeTrue(ANONYMOUS_ENABLED, "Shared boundaries run in the anonymous-enabled phase");
        String group = "boundary/" + type;
        String prefix = "vm-mix-" + UUID.randomUUID().toString().substring(0, 8);
        TestIdentity caller = identity("vm-pages");
        TestIdentity foreignOwner = identity("vm-foreign");
        Fixture own = createFixture(type, "PRIVATE", caller, prefix + "-own", "public");
        Fixture shared = createFixture(type, "PRIVATE", foreignOwner, prefix + "-shared", "public");
        Fixture hidden = createFixture(type, "PRIVATE", foreignOwner, prefix + "-hidden", "public");
        Set<String> visible = new HashSet<>(Set.of(own.name(), shared.name()));
        if (!"prompt".equals(type)) {
            visible.add(createFixture(type, "PUBLIC", foreignOwner, prefix + "-public", "public").name());
        }
        grant(shared, caller.username(), "rw", foreignOwner.token());
        assertReadableEventually(shared, caller.token());
        check(group + "/B10/mixed-pages", () -> verifyMixedPages(own, prefix, caller.token(),
            visible, ""));
        check(group + "/B10/name-filter", () -> verifyMixedPages(own, hidden.name(), caller.token(),
            Set.of(), ""));
        if (!"prompt".equals(type) && !"mcp".equals(type)) {
            check(group + "/B10/owner-scope-filter", () -> verifyMixedPages(own, prefix,
                caller.token(), Set.of(shared.name()), "&scope=PRIVATE&owner=" + foreignOwner.username()));
        }
        String otherType = "skill".equals(type) ? "agentspec" : "skill";
        Fixture crossType = createFixture(otherType, "PRIVATE", foreignOwner, shared.name(), "public");
        check(group + "/B07/cross-type", () -> {
            assertEquals(404, get(SERVER_BASE_URL, crossType.detail(), caller.token()).status());
            verifyWrite(crossType, caller.token(), false, "crosstype");
        });
        String namespace = "vm-ns-" + UUID.randomUUID().toString().substring(0, 8);
        String namespacePath = CONTEXT_PATH + "/v3/admin/core/namespace";
        assertSuccess(postForm(SERVER_BASE_URL, namespacePath, adminToken(),
            params("namespaceId", namespace, "namespaceName", namespace)));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, namespacePath, adminToken(),
            params("namespaceId", namespace)));
        Fixture crossNamespace = createFixture(type, "PRIVATE", foreignOwner, shared.name(), namespace);
        check(group + "/B07/cross-namespace", () -> {
            assertEquals(404, get(SERVER_BASE_URL, crossNamespace.detail(), caller.token()).status());
            verifyWrite(crossNamespace, caller.token(), false, "crossnamespace");
        });
        assertAll(group, failures);
    }

    private void verifyMixedPages(Fixture fixture, String prefix, String token, Set<String> expected,
        String filters) throws Exception {
        Set<String> found = new HashSet<>();
        for (int page = 1; page <= expected.size() + 1; page++) {
            String path = fixture.path() + "/list?" + query(params("namespaceId", fixture.namespace(),
                fixture.key(), prefix)) + "&search=blur&pageSize=1&pageNo=" + page + filters;
            JsonNode data = assertSuccess(get(SERVER_BASE_URL, path, token)).path("data");
            assertEquals(expected.size(), data.path("totalCount").asInt(-1), data.toString());
            JsonNode items = data.path("pageItems");
            assertEquals(page <= expected.size() ? 1 : 0, items.size(), data.toString());
            for (JsonNode item : items) {
                String name = item.path("name").asText(item.path(fixture.key()).asText());
                assertTrue(expected.contains(name), item.toString());
                assertTrue(found.add(name), "Duplicate item across pages: " + name);
            }
        }
        assertEquals(expected, found);
    }
    
    private Fixture createFixture(String type, String scope, TestIdentity owner) throws Exception {
        return createFixture(type, scope, owner, "vm-" + type + '-'
            + UUID.randomUUID().toString().substring(0, 8), "public");
    }

    private Fixture createFixture(String type, String scope, TestIdentity owner, String name,
        String namespace) throws Exception {
        Fixture fixture = new Fixture(type, name, scope, namespace);
        addCleanup(() -> deleteForm(SERVER_BASE_URL, fixture.path(), adminToken(),
            fixture.identity()));
        Map<String, String> draft = forms.draft(type, fixture.name());
        draft.put("namespaceId", namespace);
        assertSuccess(postForm(SERVER_BASE_URL, fixture.path() + "/draft", owner.token(), draft));
        if ("agentspec".equals(type)) {
            Map<String, String> update = forms.agentSpec(fixture.name());
            update.put("namespaceId", namespace);
            assertSuccess(putForm(SERVER_BASE_URL, fixture.path() + "/draft", owner.token(),
                update));
        }
        Map<String, String> version = fixture.identity();
        version.put("version", VERSION);
        version.put("updateLatestLabel", "true");
        assertSuccess(postForm(SERVER_BASE_URL, fixture.path() + "/force-publish", adminToken(),
            version));
        if (!"prompt".equals(type)) {
            setScope(fixture, scope, owner.token());
        }
        JsonNode metadata = metadata(fixture);
        if (!"prompt".equals(type)) {
            assertEquals(owner.username(), metadata.findValue("owner").asText(), metadata.toString());
            assertEquals(scope, metadata.findValue("scope").asText(), metadata.toString());
        }
        return fixture;
    }
    
    private void exerciseActor(String group, Fixture fixture, TestIdentity owner, Actor actor,
        boolean read, boolean write) throws Exception {
        String id = group + '/' + actor.name();
        int before = failures.size();
        check(id + "/detail", () -> verifyRead(fixture, actor, read));
        check(id + "/list-count-pages", () -> verifyList(fixture, actor, read));
        check(id + "/search", () -> verifySearch(fixture, actor, read));
        check(id + "/labels-write", () -> verifyWrite(fixture, actor.token(), write,
            "matrix" + actor.name()));
        check(id + "/delete", () -> verifyDelete(fixture, owner, actor, write));
        if (actor.token() == null) {
            check(id + "/non-anonymous-entry", () -> assertDenied(
                get(SERVER_BASE_URL, fixture.detail(), null)));
        }
        record("row", id, failures.size() == before ? "PASS" : "FAIL",
            "expectedRead=" + read + ", expectedWrite=" + write);
    }
    
    private void verifyRead(Fixture fixture, Actor actor, boolean readable) throws Exception {
        boolean agentSpecDownload = actor.token() == null && "agentspec".equals(fixture.type());
        String url = actor.token() == null && !agentSpecDownload ? ARD_URL : SERVER_BASE_URL;
        String path = actor.token() == null ? agentSpecDownload ? fixture.clientDetail()
            : artifact(fixture) : fixture.detail();
        Response response = get(url, path, actor.token());
        if (readable) {
            assertEquals(200, response.status(), response.body());
            assertTrue(response.body().contains(fixture.name()), response.body());
            if (actor.token() != null) {
                assertSuccess(response);
            }
        } else if (actor.token() == null && !ANONYMOUS_ENABLED) {
            assertEquals(agentSpecDownload ? 403 : 401, response.status(), response.body());
            assertFalse(response.body().contains(fixture.name()), response.body());
        } else {
            assertEquals(404, response.status(), response.body());
            assertFalse(response.body().contains("matrix body"), response.body());
        }
        if (actor.token() == null && "skill".equals(fixture.type())) {
            Response download = get(SERVER_BASE_URL, fixture.clientDetail(), null);
            assertEquals(readable ? 200 : ANONYMOUS_ENABLED ? 404 : 403,
                download.status(), "download: " + download.body());
        }
    }
    
    private void verifyList(Fixture fixture, Actor actor, boolean readable) throws Exception {
        if (actor.token() == null && !"agentspec".equals(fixture.type())) {
            Response response = awaitDiscovery(() -> get(ARD_URL, fixture.ardList(), null),
                fixture, readable);
            if (!ANONYMOUS_ENABLED) {
                assertEquals(401, response.status(), response.body());
            } else {
                assertEquals(200, response.status(), response.body());
                assertEquals(readable, response.body().contains(fixture.name()), response.body());
            }
            return;
        }
        if (actor.token() == null && !ANONYMOUS_ENABLED) {
            assertDenied(get(SERVER_BASE_URL, fixture.list(1), null));
            return;
        }
        for (int page : new int[] {1, 2}) {
            JsonNode data = assertSuccess(get(SERVER_BASE_URL, fixture.list(page), actor.token()))
                .path("data");
            assertEquals(readable ? 1 : 0, data.path("totalCount").asInt(-1), data.toString());
            assertEquals(readable && page == 1 ? 1 : 0, data.path("pageItems").size(),
                data.toString());
            assertEquals(readable && page == 1, data.path("pageItems").toString()
                .contains(fixture.name()), data.toString());
        }
    }
    
    private void verifySearch(Fixture fixture, Actor actor, boolean readable) throws Exception {
        if (actor.token() == null) {
            if ("agentspec".equals(fixture.type())) {
                // AgentSpec search has no anonymous opt-in; detail and list do.
                assertDenied(get(SERVER_BASE_URL, fixture.search(), null));
                return;
            }
            String body = JacksonUtils.toJson(Map.of("query", Map.of("text", fixture.name()),
                "pageSize", 20));
            Response response = awaitDiscovery(() -> postJsonWithAuthorization(ARD_URL,
                "/v3/ai/ard/search?namespaceId=public", null, body), fixture, readable);
            if (!ANONYMOUS_ENABLED) {
                assertEquals(401, response.status(), response.body());
            } else {
                assertEquals(200, response.status(), response.body());
                assertEquals(readable, response.body().contains(fixture.name()), response.body());
            }
            return;
        }
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        JsonNode data;
        do {
            data = assertSuccess(get(SERVER_BASE_URL, fixture.search(), actor.token())).path("data");
            if (data.path("totalCount").asInt(-1) == (readable ? 1 : 0)) {
                break;
            }
            Thread.sleep(250L);
        } while (System.nanoTime() < deadline);
        assertEquals(readable ? 1 : 0, data.path("totalCount").asInt(-1), data.toString());
        assertEquals(readable, data.path("pageItems").toString().contains(fixture.name()),
            data.toString());
    }

    private Response awaitDiscovery(ResponseRequest request, Fixture fixture, boolean readable)
        throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        Response response;
        do {
            response = request.execute();
            if (!readable || response.status() != 200 || response.body().contains(fixture.name())) {
                return response;
            }
            Thread.sleep(250L);
        } while (System.nanoTime() < deadline);
        return response;
    }

    private String artifact(Fixture fixture) throws Exception {
        if ("agent".equals(fixture.type())) {
            JsonNode version = assertSuccess(get(SERVER_BASE_URL, fixture.path() + "/version?"
                + query(fixture.identity()) + "&version=" + VERSION, adminToken())).path("data");
            String digest = version.path("contentDigest").asText();
            assertFalse(digest.isBlank(), version.toString());
            return fixture.artifact() + "&representation=nacos-agent&contentDigest="
                + URLEncoder.encode(digest, StandardCharsets.UTF_8);
        }
        if ("mcp".equals(fixture.type())) {
            JsonNode compatibility = assertSuccess(get(SERVER_BASE_URL, fixture.path() + '?'
                + query(fixture.identity()) + "&version=" + VERSION, adminToken())).path("data");
            String mcpId = compatibility.path("id").asText();
            assertFalse(mcpId.isBlank(), compatibility.toString());
            return fixture.artifact().replace("resourceName=" + fixture.name(),
                "resourceName=" + mcpId) + "&mcpName=" + fixture.name();
        }
        return fixture.artifact();
    }
    
    private void verifyWrite(Fixture fixture, String token, boolean writable, String label)
        throws Exception {
        JsonNode before = metadata(fixture).findValue("labels").deepCopy();
        Map<String, String> update = fixture.identity();
        update.put("labels", JacksonUtils.toJson(Map.of(label, VERSION)));
        Response response = putForm(SERVER_BASE_URL, fixture.path() + "/labels", token, update);
        if (writable) {
            assertSuccess(response);
            assertEquals(VERSION, metadata(fixture).findValue("labels").path(label).asText());
        } else {
            assertDenied(response);
            assertEquals(before, metadata(fixture).findValue("labels"));
        }
    }
    
    private void verifyDelete(Fixture source, TestIdentity owner, Actor actor, boolean writable)
        throws Exception {
        Fixture copy = createFixture(source.type(), source.scope(), owner);
        if (actor.grant() != null) {
            grant(copy, actor.username(), actor.grant(), owner.token());
            assertReadableEventually(copy, actor.token());
        }
        Response response = deleteForm(SERVER_BASE_URL, copy.path(), actor.token(), copy.identity());
        if (writable) {
            assertSuccess(response);
            Response absent = awaitStatus(() -> get(SERVER_BASE_URL, copy.detail(), adminToken()), 404);
            assertEquals(404, absent.status(), absent.body());
        } else {
            assertDenied(response);
            assertTrue(metadata(copy).toString().contains(copy.name()));
        }
    }
    
    private void boundaries(String group, Fixture fixture, TestIdentity owner, TestIdentity other,
        TestIdentity reader, TestIdentity writer) throws Exception {
        check(group + "/B08/grant-management", () -> {
            assertDenied(postForm(SERVER_BASE_URL, GRANT_PATH, writer.token(),
                grantForm(fixture, other.username(), "rw")));
            assertDenied(deleteForm(SERVER_BASE_URL, GRANT_PATH, writer.token(),
                grantForm(fixture, reader.username(), "r")));
        });
        check(group + "/B03/invalid-credentials", () -> {
            for (String credential : new String[] {"", "Bearer invalid-token"}) {
                Response response = getWithAuthorization(ARD_URL, fixture.artifact(), credential);
                assertEquals(401, response.status(), response.body());
            }
        });
        check(group + "/B07/adjacent-resource", () -> {
            Fixture adjacent = createFixture(fixture.type(), "PRIVATE", owner);
            assertEquals(404, get(SERVER_BASE_URL, adjacent.detail(), reader.token()).status());
            verifyWrite(adjacent, writer.token(), false, "notgranted");
        });
        check(group + "/B09/base-permission", () -> {
            TestIdentity noPermission = createIdentityWithoutPermission("vm-no-api");
            grant(fixture, noPermission.username(), "rw", owner.token());
            assertDenied(get(SERVER_BASE_URL, fixture.detail(), noPermission.token()));
            verifyWrite(fixture, noPermission.token(), false, "nobase");
        });
        check(group + "/B05/downgrade", () -> {
            revoke(fixture, writer.username(), "rw", owner.token());
            grant(fixture, writer.username(), "r", owner.token());
            awaitWriteDenied(fixture, writer.token());
            assertReadableEventually(fixture, writer.token());
            verifyWrite(fixture, writer.token(), false, "downgraded");
        });
        check(group + "/B04/revoke", () -> {
            revoke(fixture, writer.username(), "r", owner.token());
            int status = "PUBLIC".equals(fixture.scope()) ? 200 : 404;
            Response response = awaitStatus(() -> get(SERVER_BASE_URL, fixture.detail(),
                writer.token()), status);
            assertEquals(status, response.status(), response.body());
            verifyWrite(fixture, writer.token(), false, "revoked");
        });
        if (!"prompt".equals(fixture.type())) {
            check(group + "/B06/scope-transition", () -> {
                try {
                    for (String scope : new String[] {"PUBLIC", "PRIVATE", "PUBLIC"}) {
                        setScope(fixture, scope, owner.token());
                        assertEquals(scope, metadata(fixture).findValue("scope").asText());
                        int status = "PUBLIC".equals(scope) ? 200 : 404;
                        Response response = awaitStatus(() -> get(SERVER_BASE_URL, fixture.detail(),
                            other.token()), status);
                        assertEquals(status, response.status(), response.body());
                        assertReadableEventually(fixture, owner.token());
                    }
                } finally {
                    setScope(fixture, fixture.scope(), adminToken());
                }
            });
        }
    }
    
    private void awaitWriteDenied(Fixture fixture, String token) throws Exception {
        Map<String, String> update = fixture.identity();
        update.put("labels", "{\"cacheprobe\":\"1.0.0\"}");
        Response response = awaitStatus(() -> putForm(SERVER_BASE_URL,
            fixture.path() + "/labels", token, update), 403);
        assertDenied(response);
    }
    
    private void setScope(Fixture fixture, String scope, String token) throws Exception {
        Map<String, String> update = fixture.identity();
        update.put("scope", scope);
        assertSuccess(putForm(SERVER_BASE_URL, fixture.path() + "/scope", token, update));
    }
    
    private JsonNode metadata(Fixture fixture) throws Exception {
        return assertSuccess(get(SERVER_BASE_URL, fixture.detail(), adminToken())).path("data");
    }
    
    private void assertReadableEventually(Fixture fixture, String token) throws Exception {
        assertSuccess(awaitSuccess(() -> get(SERVER_BASE_URL, fixture.detail(), token)));
    }
    
    private void grant(Fixture fixture, String username, String action, String token)
        throws Exception {
        Map<String, String> form = grantForm(fixture, username, action);
        assertSuccess(postForm(SERVER_BASE_URL, GRANT_PATH, token, form));
        addCleanup(() -> deleteForm(SERVER_BASE_URL, GRANT_PATH, adminToken(), form));
    }
    
    private void revoke(Fixture fixture, String username, String action, String token)
        throws Exception {
        assertSuccess(deleteForm(SERVER_BASE_URL, GRANT_PATH, token,
            grantForm(fixture, username, action)));
    }
    
    private Map<String, String> grantForm(Fixture fixture, String username, String action) {
        return params("namespaceId", fixture.namespace(), "resourceType", fixture.type(), "resourceName",
            fixture.name(), "username", username, "action", action);
    }
    
    private void check(String id, Executable executable) throws Exception {
        try {
            executable.execute();
            record("check", id, "PASS", "");
        } catch (Throwable failure) {
            if (!(failure instanceof Exception) && !(failure instanceof AssertionError)) {
                throw new IllegalStateException("Fatal matrix failure", failure);
            }
            failures.add(() -> { throw new AssertionError(id + ": " + failure.getMessage(), failure); });
            record("check", id, "FAIL", String.valueOf(failure.getMessage()));
        }
    }
    
    private void record(String kind, String id, String status, String detail) throws Exception {
        String output = System.getProperty("nacos.test.visibility.matrix.report", "");
        if (!output.isBlank()) {
            Path path = Path.of(output);
            Files.createDirectories(path.toAbsolutePath().getParent());
            Files.writeString(path, JacksonUtils.toJson(Map.of("kind", kind, "id", id,
                "status", status, "detail", detail)) + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }
    
    private static String query(Map<String, String> parameters) {
        List<String> parts = new ArrayList<>();
        parameters.forEach((key, value) -> parts.add(URLEncoder.encode(key, StandardCharsets.UTF_8)
            + '=' + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return String.join("&", parts);
    }
    
    private record Actor(String name, String username, String token, String grant) {
    }
    
    private record Fixture(String type, String name, String scope, String namespace) {
        
        String segment() {
            return switch (type) {
                case "agent" -> "agents";
                case "skill" -> "skills";
                case "agentspec" -> "agentspecs";
                default -> type;
            };
        }
        
        String key() {
            return switch (type) {
                case "agent" -> "agentName";
                case "mcp" -> "mcpName";
                case "prompt" -> "promptKey";
                case "skill" -> "skillName";
                case "agentspec" -> "agentSpecName";
                default -> throw new IllegalArgumentException(type);
            };
        }
        
        Map<String, String> identity() {
            return params("namespaceId", namespace, key(), name);
        }
        
        String path() {
            return CONTEXT_PATH + "/v3/admin/ai/" + segment();
        }
        
        String detail() {
            String suffix = "mcp".equals(type) ? "/version" : "prompt".equals(type)
                ? "/governance" : "";
            return path() + suffix + '?' + query(identity()) + "&version=" + VERSION;
        }
        
        String list(int page) {
            return path() + "/list?" + query(identity())
                + "&search=accurate&pageSize=1&pageNo=" + page;
        }
        
        String clientDetail() {
            return CONTEXT_PATH + "/v3/client/ai/" + segment()
                + "?namespaceId=public&name=" + name + "&version=" + VERSION;
        }
        
        String search() {
            return CONTEXT_PATH + "/v3/client/ai/" + segment()
                + "/search?namespaceId=public&" + ("agent".equals(type) ? "agentNameContains" : "query") + '=' + name
                + "&pageNo=1&pageSize=10";
        }
        
        String artifact() {
            return "/v3/ai/ard/artifacts?namespaceId=public&resourceType=" + type
                + "&resourceName=" + name + "&version=" + VERSION;
        }
        
        String ardList() {
            return "/v3/ai/ard/agents?namespaceId=public&pageSize=100&filter="
                + URLEncoder.encode("metadata.resourceName = '" + name + "'", StandardCharsets.UTF_8);
        }
    }
    
    private static class Forms extends AiAdminApiBaseITCase {
        
        Map<String, String> draft(String type, String name) {
            return switch (type) {
                case "agent" -> {
                    Map<String, Object> request = agentInitialDraftRequest("public", name, VERSION);
                    request.put("callInterfaces", List.of(Map.of("protocol", "a2a",
                        "protocolVersion", "1.0", "descriptorMediaType", "application/json",
                        "nativeDescriptor", JacksonUtils.toObj(buildV1AgentCard(name, VERSION,
                            "1.0"), Map.class), "endpointSourceOrder", List.of("DECLARED", "RUNTIME"),
                        "endpointSets", List.of(Map.of("source", "DECLARED", "endpoints",
                            List.of(Map.of("uri", "https://example.com/" + name + "/jsonrpc",
                                "transport", "JSONRPC")))))));
                    yield agentForm(request);
                }
                case "mcp" -> {
                    Map<String, String> form = mcpServerForm(name, VERSION, "matrix body",
                        "echo", "resource");
                    form.put("version", VERSION);
                    yield form;
                }
                case "prompt" -> promptDraftForm(name, VERSION, "matrix body {{name}}",
                    "matrix body", "matrix");
                case "skill" -> skillDraftForm(name, VERSION, "matrix body", "matrix guide");
                case "agentspec" -> agentSpecDraftForm(name, VERSION);
                default -> throw new IllegalArgumentException(type);
            };
        }
        
        Map<String, String> agentSpec(String name) {
            Map<String, String> form = agentSpecUpdateForm(name, VERSION, "matrix body",
                "matrix", "matrix soul");
            form.put("namespaceId", "public");
            return form;
        }
    }
}
