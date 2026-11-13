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

package com.alibaba.nacos.test.openapi.client.ai;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Deployed-server scenarios for the existing Skill Client routes with the reserved namespace.
 *
 * <p>Requires an auth-enabled standalone server with Hub enabled and the default visibility
 * plugin. Fixtures are published through Admin APIs. No source visibility grant is given to
 * the no-permission Client identity. Download counters converge through the normal ten-second
 * flusher; polling is bounded to thirty seconds. No server is started inside these tests.</p>
 *
 * @author nacos
 */
public class SharedSkillClientOpenApiITCase extends AiAdminApiBaseITCase {

    private static final String CLIENT_SKILLS = nacosPath("/v3/client/ai/skills");

    private static final String HUB_SKILLS = nacosPath("/v3/admin/ai/hub/skills");

    private static final String HUB_NAMESPACE = AiConstants.Hub.NAMESPACE_ID;

    @BeforeEach
    void requireAuthenticatedFixture() {
        assumeTrue(AUTH_ENABLED, "Shared Client IT requires the auth-enabled standalone baseline");
    }

    @Test
    void sharedSearchReturnsOpaqueProvenanceAndPinnedZipWithoutSourcePermission() throws Exception {
        String name = uniquePrefix() + "-pinned";
        createOnlineSkill(name);
        JsonNode[] publication = share(name);
        String id = publication[0].get("hubEntryId").asText();
        JsonNode page = sharedSearch(name, 1, 10);
        assertEquals(1, page.get("totalCount").asInt());
        JsonNode item = page.get("pageItems").get(0);
        assertEquals(id, item.get("name").asText());
        assertEquals(id, item.get("hubEntryId").asText());
        assertEquals("HUB", item.get("discoverySource").asText());
        assertEquals(HUB_NAMESPACE, item.get("namespaceId").asText());
        assertEquals(name, item.get("displayName").asText());
        assertEquals("public", item.get("sourceDisplayName").asText());
        assertEquals("1.0.0", item.get("version").asText());
        assertFalse(item.has("sourceNamespaceId"));
        assertFalse(item.has("sourceResourceId"));
        assertFalse(item.has("storage"));
        assertFalse(item.has("ext"));
        JsonNode detail = getJsonOk(ADMIN_SKILL_PATH, entry(id),
            AuthIdentity.CLIENT_NO_PERMISSION).get("data");
        assertFalse(detail.get("sourceReadable").asBoolean());
        assertFalse(detail.has("sourceNamespaceId"));

        postFormOk(ADMIN_SKILL_PATH + "/draft", skillForkForm(name, "2.0.0", "1.0.0"));
        postFormOk(ADMIN_SKILL_PATH + "/force-publish",
            withUpdateLatestLabel(skillPublishForm(name, "2.0.0")));
        Download download = download(sharedQuery(id), AuthIdentity.CLIENT_NO_PERMISSION);
        assertEquals(200, download.status());
        assertTrue(download.contentType().contains("application/zip"));
        assertTrue(download.disposition().contains(name + ".zip"));
        assertEquals("1.0.0", download.resolvedVersion());
        assertEquals(id, download.hubEntryId());
        assertTrue(unzipTextEntries(download.body()).get(name + "/SKILL.md")
            .contains("Pinned shared instructions."));
        assertNotNull(download.md5());
        Download unchanged = download(sharedQuery(id).addParam("md5", download.md5()),
            AuthIdentity.CLIENT_NO_PERMISSION);
        assertEquals(304, unchanged.status());
        assertEquals(0, unchanged.body().length);
        assertEquals(download.md5(), unchanged.md5());
        if (EXPECTED_ANONYMOUS_AI_ENABLED) {
            assertEquals(200, download(sharedQuery(id), AuthIdentity.ANONYMOUS).status());
        }

        // Omitting namespace retains the public resource lookup, not shared discovery.
        assertError(getRaw(CLIENT_SKILLS, Query.newInstance().addParam("name", id),
            AuthIdentity.ADMIN), 404, ErrorCode.RESOURCE_NOT_FOUND, "");
        publication[0] = postFormOk(HUB_SKILLS + "/unshare", publicationParams(publication[0]))
            .get("data");
        assertEquals(0, sharedSearch(name, 1, 10).get("totalCount").asInt());
        assertError(getRaw(CLIENT_SKILLS, sharedQuery(id), AuthIdentity.CLIENT_NO_PERMISSION),
            404, ErrorCode.RESOURCE_NOT_FOUND, "");
    }

    @Test
    void sharedHandlesSelectorsAndPaginationReturnControlledErrors() throws Exception {
        String name = uniquePrefix() + "-validation";
        createOnlineSkill(name);
        JsonNode[] publication = share(name);
        String id = publication[0].get("hubEntryId").asText();
        for (String malformed : new String[] {"0", "-1", "01", name, "9223372036854775808"}) {
            assertError(getRaw(CLIENT_SKILLS, sharedQuery(malformed),
                AuthIdentity.CLIENT_NO_PERMISSION), 400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
        }
        assertError(getRaw(CLIENT_SKILLS, sharedQuery(Long.toString(Long.MAX_VALUE)),
            AuthIdentity.CLIENT_NO_PERMISSION), 404, ErrorCode.RESOURCE_NOT_FOUND, "");
        assertError(getRaw(CLIENT_SKILLS, sharedQuery(id).addParam("version", "9.9.9"),
            AuthIdentity.CLIENT_NO_PERMISSION), 404, ErrorCode.RESOURCE_NOT_FOUND, "");
        assertError(getRaw(CLIENT_SKILLS, sharedQuery(id).addParam("label", "stable"),
            AuthIdentity.CLIENT_NO_PERMISSION), 404, ErrorCode.RESOURCE_NOT_FOUND, "");
        assertError(getRaw(CLIENT_SKILLS + "/search", Query.newInstance()
            .addParam("namespaceId", HUB_NAMESPACE).addParam("pageSize", "101"),
            AuthIdentity.CLIENT_NO_PERMISSION), 400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
        assertEquals(200, download(sharedQuery(id).addParam("version", "1.0.0")
            .addParam("label", "stable"), AuthIdentity.CLIENT_NO_PERMISSION).status());
    }

    @Test
    void sharedPopularityUsesIndependentCountersBeforePagination() throws Exception {
        String prefix = uniquePrefix();
        String firstName = prefix + "-first";
        String secondName = prefix + "-second";
        createOnlineSkill(firstName);
        createOnlineSkill(secondName);
        assertEquals(200, download(sourceQuery(firstName), AuthIdentity.ADMIN).status());
        awaitCounts(() -> sourceCount(firstName) == 1L);
        String firstId = share(firstName)[0].get("hubEntryId").asText();
        String secondId = share(secondName)[0].get("hubEntryId").asText();
        assertEquals(0L, hubCount(firstId));
        assertEquals(0L, hubCount(secondId));
        assertTrue(Long.parseLong(firstId) < Long.parseLong(secondId));
        JsonNode initialItem = sharedSearch(prefix, 1, 1).get("pageItems").get(0);
        assertEquals(firstId, initialItem.get("name").asText());
        long sharedAt = initialItem.get("updateTime").asLong();

        Download first = download(sharedQuery(firstId), AuthIdentity.CLIENT_NO_PERMISSION);
        assertEquals(200, first.status());
        assertEquals(200, download(sharedQuery(secondId), AuthIdentity.CLIENT_NO_PERMISSION).status());
        awaitCounts(() -> hubCount(firstId) == 1L && hubCount(secondId) == 1L);
        assertEquals(firstId, sharedSearch(prefix, 1, 1).get("pageItems").get(0).get("name").asText());
        assertEquals(secondId, sharedSearch(prefix, 2, 1).get("pageItems").get(0).get("name").asText());
        assertEquals(1L, sourceCount(firstName));
        assertEquals(0L, sourceCount(secondName));
        assertEquals(1L, getJsonOk(ADMIN_SKILL_PATH, entry(firstId)).get("data")
            .get("versions").get(0).get("downloadCount").asLong());
        assertEquals(1L, findSkillVersionSummary(getJsonOk(ADMIN_SKILL_PATH,
            skillQuery(firstName)).get("data"), "1.0.0").get("downloadCount").asLong());

        assertEquals(200, download(sharedQuery(secondId), AuthIdentity.CLIENT_NO_PERMISSION).status());
        awaitCounts(() -> hubCount(secondId) == 2L);
        JsonNode page = sharedSearch(prefix, 1, 1);
        assertEquals(2, page.get("totalCount").asInt());
        assertEquals(2, page.get("pagesAvailable").asInt());
        assertEquals(secondId, page.get("pageItems").get(0).get("name").asText());
        assertEquals(firstId, sharedSearch(prefix, 2, 1).get("pageItems").get(0).get("name").asText());
        assertEquals(0, sharedSearch(prefix, 3, 1).get("pageItems").size());
        assertEquals(304, download(sharedQuery(firstId).addParam("md5", first.md5()),
            AuthIdentity.CLIENT_NO_PERMISSION).status());
        assertEquals(200, download(sourceQuery(firstName), AuthIdentity.ADMIN).status());
        awaitCounts(() -> sourceCount(firstName) == 2L);
        assertEquals(1L, hubCount(firstId));
        assertEquals(2L, hubCount(secondId));
        assertEquals(0L, sourceCount(secondName));
        assertEquals(sharedAt, sharedSearch(prefix, 2, 1).get("pageItems").get(0)
            .get("updateTime").asLong());
    }

    private void createOnlineSkill(String name) throws Exception {
        postFormOk(ADMIN_SKILL_PATH + "/draft",
            skillDraftForm(name, "1.0.0", "Pinned shared instructions.", null));
        addCleanup(() -> deleteSkillQuietly(name));
        postFormOk(ADMIN_SKILL_PATH + "/force-publish",
            withUpdateLatestLabel(skillPublishForm(name, "1.0.0")));
        putFormOk(ADMIN_SKILL_PATH + "/scope", skillScopeForm(name, "PUBLIC"));
    }

    private JsonNode[] share(String name) throws Exception {
        JsonNode[] publication = {postFormOk(HUB_SKILLS + "/share", Map.of(
            "sourceNamespaceId", "public", "sourceName", name, "sourceVersion", "1.0.0")).get("data")};
        addCleanup(() -> {
            if (publication[0].get("active").asBoolean()) {
                publication[0] = postFormOk(HUB_SKILLS + "/unshare",
                    publicationParams(publication[0])).get("data");
            }
        });
        return publication;
    }

    private Map<String, String> publicationParams(JsonNode publication) {
        return Map.of("hubEntryId", publication.get("hubEntryId").asText(),
            "expectedMetaVersion", publication.get("metaVersion").asText());
    }

    private JsonNode sharedSearch(String query, int pageNo, int pageSize) throws Exception {
        return getJsonOk(CLIENT_SKILLS + "/search", Query.newInstance()
            .addParam("namespaceId", HUB_NAMESPACE).addParam("query", query)
            .addParam("pageNo", pageNo).addParam("pageSize", pageSize),
            AuthIdentity.CLIENT_NO_PERMISSION).get("data");
    }

    private long hubCount(String id) throws Exception {
        return getJsonOk(ADMIN_SKILL_PATH, entry(id)).get("data").get("downloadCount").asLong();
    }

    private long sourceCount(String name) throws Exception {
        return getJsonOk(ADMIN_SKILL_PATH, skillQuery(name)).get("data").get("downloadCount").asLong();
    }

    private Query entry(String id) {
        return Query.newInstance().addParam("namespaceId", HUB_NAMESPACE).addParam("skillName", id);
    }

    private Query sharedQuery(String id) {
        return Query.newInstance().addParam("namespaceId", HUB_NAMESPACE).addParam("name", id);
    }

    private Query sourceQuery(String name) {
        return Query.newInstance().addParam("namespaceId", "public").addParam("name", name);
    }

    private Download download(Query query, AuthIdentity identity) throws Exception {
        HttpGet request = new HttpGet(requestUrl(CLIENT_SKILLS) + "?" + query.toQueryUrl());
        authHeader(identity).getHeader().forEach(request::setHeader);
        return httpClient.execute(request, response -> new Download(response.getCode(),
            response.getEntity() == null ? new byte[0] : EntityUtils.toByteArray(response.getEntity()),
            response.getFirstHeader("Content-Type") == null ? null
                : response.getFirstHeader("Content-Type").getValue(),
            response.getFirstHeader("Content-Disposition") == null ? null
                : response.getFirstHeader("Content-Disposition").getValue(),
            response.getFirstHeader("X-Nacos-Skill-Md5") == null ? null
                : response.getFirstHeader("X-Nacos-Skill-Md5").getValue(),
            response.getFirstHeader("X-Nacos-Skill-Resolved-Version") == null ? null
                : response.getFirstHeader("X-Nacos-Skill-Resolved-Version").getValue(),
            response.getFirstHeader("X-Nacos-Hub-Entry-Id") == null ? null
                : response.getFirstHeader("X-Nacos-Hub-Entry-Id").getValue()));
    }

    private void awaitCounts(CountCondition condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30L);
        while (!condition.matches()) {
            assertTrue(System.nanoTime() < deadline, "Download counters did not converge within 30 seconds");
            Thread.sleep(250L);
        }
    }

    private String uniquePrefix() {
        return "shared-client-it-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @FunctionalInterface
    private interface CountCondition {
        boolean matches() throws Exception;
    }

    private record Download(int status, byte[] body, String contentType, String disposition,
                            String md5, String resolvedVersion, String hubEntryId) {
    }
}
