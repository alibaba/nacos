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

import java.util.UUID;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Base64;
import org.apache.hc.client5.http.classic.methods.HttpGet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Shared deployed-server scenarios for both Hub mutation audiences.
 *
 * <p>Requires enabled Admin/Console auth and the default visibility plugin.
 * Anonymous read scenarios also require the existing anonymous.ai.enabled setting.
 * Dedicated Hub-role and standalone remote-Console cases require separate
 * deployment fixtures.</p>
 *
 * @author nacos
 */
public abstract class HubSkillApiBaseITCase extends AiAdminApiBaseITCase {

    protected abstract String hubPath();

    @BeforeEach
    void requireAuthBaseline() {
        assumeTrue(AUTH_ENABLED, "Hub lifecycle IT requires the auth-enabled baseline");
    }

    @Test
    void lifecyclePreservesSourceAndStrictMetadataCas() throws Exception {
        String name = "hub-api-it-" + UUID.randomUUID();
        postFormOk(ADMIN_SKILL_PATH + "/draft", skillDraftForm(name, "1.0.0", "Hub source instructions.", null));
        addCleanup(() -> deleteSkillQuietly(name));
        postFormOk(ADMIN_SKILL_PATH + "/force-publish", withUpdateLatestLabel(skillPublishForm(name, "1.0.0")));
        putFormOk(ADMIN_SKILL_PATH + "/scope", skillScopeForm(name, "PUBLIC"));
        Map<String, String> share = Map.of("sourceNamespaceId", "public", "sourceName", name, "sourceVersion", "1.0.0");
        JsonNode[] latest = {postFormOk(hubPath() + "/share", share).get("data")};
        addCleanup(() -> {
            if (latest[0].path("active").asBoolean()) {
                postFormOk(hubPath() + "/force-unshare", Map.of("hubEntryId", latest[0].get("hubEntryId").asText(),
                    "expectedMetaVersion", latest[0].get("metaVersion").asText(), "reason", "Hub API IT cleanup"));
            }
        });
        assertResult(latest[0], name, "CREATED", true);
        assertTrue(latest[0].get("hubEntryId").isTextual());
        assertTrue(latest[0].get("sourceResourceId").isTextual());
        assertTrue(latest[0].get("sourceVersionId").isTextual());
        assertTrue(latest[0].get("metaVersion").isTextual());
        String id = latest[0].get("hubEntryId").asText();
        String firstMeta = latest[0].get("metaVersion").asText();
        JsonNode retry = postFormOk(hubPath() + "/share", share).get("data");
        assertResult(retry, name, "ALREADY_SHARED", true);
        assertEquals(id, retry.get("hubEntryId").asText());
        assertEquals(firstMeta, retry.get("metaVersion").asText());
        postFormOk(ADMIN_SKILL_PATH + "/draft", skillForkForm(name, "2.0.0", "1.0.0"));
        postFormOk(ADMIN_SKILL_PATH + "/force-publish", withUpdateLatestLabel(skillPublishForm(name, "2.0.0")));
        latest[0] = postFormOk(hubPath() + "/adjust", Map.of("hubEntryId", id, "targetVersion", "2.0.0",
            "expectedMetaVersion", firstMeta)).get("data");
        assertResult(latest[0], name, "ADJUSTED", true);
        assertEquals("2.0.0", latest[0].get("sourceVersion").asText());
        assertEquals(Long.parseLong(firstMeta) + 1, latest[0].get("metaVersion").asLong());
        assertError(postRaw(hubPath() + "/adjust", Query.newInstance().addParam("hubEntryId", id)
            .addParam("targetVersion", "1.0.0").addParam("expectedMetaVersion", firstMeta)), 409, ErrorCode.RESOURCE_CONFLICT, "");
        latest[0] = postFormOk(hubPath() + "/unshare", entryParams(latest[0])).get("data");
        assertResult(latest[0], name, "UNSHARED", false);
        retry = postFormOk(hubPath() + "/unshare", entryParams(latest[0])).get("data");
        assertResult(retry, name, "ALREADY_UNSHARED", false);
        latest[0] = postFormOk(hubPath() + "/share", Map.of("sourceNamespaceId", "public", "sourceName", name,
            "sourceVersion", "2.0.0")).get("data");
        assertResult(latest[0], name, "REACTIVATED", true);
        latest[0] = postFormOk(hubPath() + "/force-unshare", Map.of("hubEntryId", id,
            "expectedMetaVersion", latest[0].get("metaVersion").asText(), "reason", "Hub API governance IT")).get("data");
        assertResult(latest[0], name, "FORCE_UNSHARED", false);
        assertEquals("online", findSkillVersionSummary(getJsonOk(ADMIN_SKILL_PATH,
            skillQuery(name)).get("data"), "2.0.0").get("status").asText());
    }

    @Test
    void invalidRequestsReturnControlled400() throws Exception {
        for (String suffix : new String[] {"share", "adjust", "unshare", "force-unshare"}) {
            assertError(postRaw(hubPath() + "/" + suffix, Query.newInstance()), 400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
        }
        assertError(postRaw(hubPath() + "/share", Query.newInstance().addParam("sourceNamespaceId", "public")
            .addParam("sourceName", "unused").addParam("sourceVersion", "latest")), 400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
        assertError(postRaw(hubPath() + "/unshare", Query.newInstance().addParam("hubEntryId", "9223372036854775808")
            .addParam("expectedMetaVersion", "1")), 400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
        assertError(postRaw(hubPath() + "/force-unshare", Query.newInstance().addParam("hubEntryId", "1")
            .addParam("expectedMetaVersion", "1").addParam("reason", "x".repeat(513))), 400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
    }

    @Test
    void hubCannotBeUsedAsSourceAndOrdinarySkillApiCannotMutateHub() throws Exception {
        assertError(postRaw(hubPath() + "/share", Query.newInstance().addParam("sourceNamespaceId", "__nacos_ai_hub__")
            .addParam("sourceName", "unused").addParam("sourceVersion", "1.0.0")), 400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
        assertError(deleteRaw(ADMIN_SKILL_PATH, Query.newInstance().addParam("namespaceId", "__nacos_ai_hub__")
            .addParam("skillName", "unused")), 400, ErrorCode.ILLEGAL_NAMESPACE, "");
    }

    @Test
    void missingHubEntryReturns404() throws Exception {
        assertError(postRaw(hubPath() + "/adjust", Query.newInstance().addParam("hubEntryId", String.valueOf(Long.MAX_VALUE))
            .addParam("targetVersion", "1.0.0").addParam("expectedMetaVersion", "1")), 404, ErrorCode.RESOURCE_NOT_FOUND, "");
    }

    @Test
    void allOperationsRejectAnonymousIdentity() throws Exception {
        for (String suffix : new String[] {"share", "adjust", "unshare", "force-unshare"}) {
            assertError(postRaw(hubPath() + "/" + suffix, Query.newInstance(), AuthIdentity.ANONYMOUS),
                403, ErrorCode.ACCESS_DENIED, "");
        }
    }

    @Test
    void ordinaryAuthenticatedUserCannotForceUnshare() throws Exception {
        assertError(postRaw(hubPath() + "/force-unshare", Query.newInstance().addParam("hubEntryId", String.valueOf(Long.MAX_VALUE))
            .addParam("expectedMetaVersion", "1").addParam("reason", "permission IT"), AuthIdentity.CLIENT_NO_PERMISSION),
            403, ErrorCode.ACCESS_DENIED, "");
    }

    private Map<String, String> entryParams(JsonNode result) {
        return Map.of("hubEntryId", result.get("hubEntryId").asText(), "expectedMetaVersion", result.get("metaVersion").asText());
    }

    private void assertResult(JsonNode result, String sourceName, String outcome, boolean active) {
        assertEquals(sourceName, result.get("displayName").asText());
        assertEquals(sourceName, result.get("sourceName").asText());
        assertEquals("public", result.get("sourceNamespaceId").asText());
        assertEquals(outcome, result.get("outcome").asText());
        assertEquals(active, result.get("active").asBoolean());
        assertFalse(result.has("storage"));
        assertFalse(result.has("ext"));
    }

    @Test
    void readParametersAndMissingEntriesReturnControlledErrors() throws Exception {
        assertError(getRaw(hubPath() + "/list", Query.newInstance().addParam("pageNo", "abc")),
            400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
        assertError(getRaw(hubPath() + "/list", Query.newInstance().addParam("pageSize", "101")),
            400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
        for (String suffix : new String[] {"detail", "content", "download"}) {
            assertError(getRaw(hubPath() + "/" + suffix, Query.newInstance().addParam("hubEntryId", "9223372036854775808")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
            assertError(getRaw(hubPath() + "/" + suffix, Query.newInstance().addParam("hubEntryId", String.valueOf(Long.MAX_VALUE))),
                404, ErrorCode.RESOURCE_NOT_FOUND, "");
        }
    }

    @Test
    void anonymousReadsReachHubValidationWithoutGrantingMutationAccess() throws Exception {
        // These scenarios require the existing anonymous.ai.enabled switch on the deployed auth plugin.
        assertEquals(200, getRaw(hubPath() + "/list", Query.newInstance(), AuthIdentity.ANONYMOUS).code());
        for (String suffix : new String[] {"detail", "content", "download"}) {
            assertError(getRaw(hubPath() + "/" + suffix,
                    Query.newInstance().addParam("hubEntryId", String.valueOf(Long.MAX_VALUE)), AuthIdentity.ANONYMOUS),
                    404, ErrorCode.RESOURCE_NOT_FOUND, "");
        }
    }

    @Test
    void authenticatedHubReadsStayPinnedAndWithdrawImmediately() throws Exception {
        String name = "hub-read-it-" + UUID.randomUUID();
        postFormOk(ADMIN_SKILL_PATH + "/draft", skillDraftForm(name, "1.0.0", "Pinned original instructions.", null));
        addCleanup(() -> deleteSkillQuietly(name));
        postFormOk(ADMIN_SKILL_PATH + "/force-publish", withUpdateLatestLabel(skillPublishForm(name, "1.0.0")));
        putFormOk(ADMIN_SKILL_PATH + "/scope", skillScopeForm(name, "PUBLIC"));
        JsonNode share = postFormOk(hubPath() + "/share", Map.of("sourceNamespaceId", "public", "sourceName", name,
            "sourceVersion", "1.0.0")).get("data");
        JsonNode[] cleanupShare = {share};
        addCleanup(() -> {
            if (cleanupShare[0].get("active").asBoolean()) {
                postFormOk(hubPath() + "/force-unshare", Map.of("hubEntryId", cleanupShare[0].get("hubEntryId").asText(),
                    "expectedMetaVersion", cleanupShare[0].get("metaVersion").asText(), "reason", "read IT cleanup"));
            }
        });
        Query entry = Query.newInstance().addParam("hubEntryId", share.get("hubEntryId").asText());
        JsonNode directory = getJsonOk(hubPath() + "/list", Query.newInstance().addParam("query", name)).get("data");
        assertEquals(1, directory.get("totalCount").asInt());
        assertEquals(name, directory.get("pageItems").get(0).get("displayName").asText());
        assertEquals("public", directory.get("pageItems").get(0).get("sourceDisplayName").asText());
        assertTrue(directory.get("pageItems").get(0).get("downloadCount").isNumber());
        JsonNode detail = getJsonOk(hubPath() + "/detail", entry, AuthIdentity.CLIENT_NO_PERMISSION).get("data");
        assertFalse(detail.get("sourceReadable").asBoolean());
        assertFalse(detail.has("sourceNamespaceId"));
        assertEquals("public", detail.get("sourceDisplayName").asText());
        assertTrue(detail.get("downloadCount").isNumber());
        assertEquals("online", detail.get("versionStatus").asText());
        assertTrue(detail.get("versionDownloadCount").isNumber());
        assertTrue(detail.get("versionLabels").isArray());
        postFormOk(ADMIN_SKILL_PATH + "/draft", skillForkForm(name, "2.0.0", "1.0.0"));
        postFormOk(ADMIN_SKILL_PATH + "/force-publish", withUpdateLatestLabel(skillPublishForm(name, "2.0.0")));
        JsonNode content = getJsonOk(hubPath() + "/content", entry, AuthIdentity.CLIENT_NO_PERMISSION).get("data");
        assertEquals("1.0.0", content.get("sharedVersion").asText());
        assertTrue(content.get("skillMd").asText().contains("Pinned original instructions."));
        detail = getJsonOk(hubPath() + "/detail", entry, AuthIdentity.CLIENT_NO_PERMISSION).get("data");
        assertEquals("1.0.0", detail.get("sharedVersion").asText());
        assertEquals(0, detail.get("versionLabels").size());
        JsonNode anonymousDetail = getJsonOk(hubPath() + "/detail", entry, AuthIdentity.ANONYMOUS).get("data");
        assertFalse(anonymousDetail.get("sourceReadable").asBoolean());
        assertFalse(anonymousDetail.has("sourceNamespaceId"));
        assertEquals("1.0.0", anonymousDetail.get("sharedVersion").asText());
        JsonNode anonymousContent = getJsonOk(hubPath() + "/content", entry, AuthIdentity.ANONYMOUS).get("data");
        assertEquals("1.0.0", anonymousContent.get("sharedVersion").asText());
        assertTrue(anonymousContent.get("skillMd").asText().contains("Pinned original instructions."));
        byte[] archive;
        if (hubPath().contains("/console/")) {
            ByteResponse download = executeRawBytes(new HttpGet(requestUrl(hubPath() + "/download")
                + "?hubEntryId=" + share.get("hubEntryId").asText()), AuthIdentity.ANONYMOUS);
            assertEquals(200, download.code());
            assertTrue(download.contentType().contains("application/zip"));
            archive = download.body();
        } else {
            JsonNode download = getJsonOk(hubPath() + "/download", entry, AuthIdentity.ANONYMOUS).get("data");
            assertEquals("1.0.0", download.get("sharedVersion").asText());
            assertFalse(download.has("sourceNamespaceId"));
            archive = Base64.getDecoder().decode(download.get("zipBytes").asText());
        }
        assertTrue(unzipTextEntries(archive).get(name + "/SKILL.md").contains("Pinned original instructions."));
        cleanupShare[0] = postFormOk(hubPath() + "/unshare", entryParams(share)).get("data");
        assertError(getRaw(hubPath() + "/download", entry), 404, ErrorCode.RESOURCE_NOT_FOUND, "");
        assertError(getRaw(hubPath() + "/content", entry), 404, ErrorCode.RESOURCE_NOT_FOUND, "");
        assertError(getRaw(hubPath() + "/content", entry, AuthIdentity.ANONYMOUS), 404, ErrorCode.RESOURCE_NOT_FOUND, "");
        assertEquals(0, getJsonOk(hubPath() + "/list", Query.newInstance().addParam("query", name)).get("data").get("totalCount").asInt());
    }

    @Test
    void transferBoundariesRejectInvalidAndAnonymousRequests() throws Exception {
        for (String suffix : new String[]{"import", "copy"}) {
            assertError(postRaw(hubPath() + "/" + suffix, Query.newInstance()),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
            assertError(postRaw(hubPath() + "/" + suffix, Query.newInstance(), AuthIdentity.ANONYMOUS),
                403, ErrorCode.ACCESS_DENIED, "");
        }
        assertError(postRaw(hubPath() + "/import", Query.newInstance().addParam("hubEntryId", "abc")
            .addParam("targetNamespaceId", "public").addParam("targetName", "unused")),
            400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
    }

    @Test
    void transferredDraftsRemainIndependentAfterSourceAndPublicationChange() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String source = "hub-transfer-source-" + suffix;
        final String imported = "hub-import-" + suffix;
        final String copied = "hub-copy-" + suffix;
        postFormOk(ADMIN_SKILL_PATH + "/draft", skillDraftForm(source, "1.0.0", "Independent pinned content.", null));
        addCleanup(() -> deleteSkillQuietly(source));
        postFormOk(ADMIN_SKILL_PATH + "/force-publish", withUpdateLatestLabel(skillPublishForm(source, "1.0.0")));
        putFormOk(ADMIN_SKILL_PATH + "/scope", skillScopeForm(source, "PUBLIC"));
        JsonNode share = postFormOk(hubPath() + "/share", Map.of("sourceNamespaceId", "public", "sourceName", source,
            "sourceVersion", "1.0.0")).get("data");
        JsonNode[] publication = {share};
        addCleanup(() -> {
            if (publication[0].get("active").asBoolean()) {
                publication[0] = postFormOk(hubPath() + "/unshare", entryParams(publication[0])).get("data");
            }
        });
        JsonNode result = postFormOk(hubPath() + "/import", Map.of("hubEntryId", share.get("hubEntryId").asText(),
            "targetNamespaceId", "public", "targetName", imported)).get("data");
        addCleanup(() -> deleteSkillQuietly(imported));
        assertEquals("draft", result.get("status").asText());
        assertEquals("0.0.1", result.get("draftVersion").asText());
        assertFalse(result.has("sourceNamespaceId"));
        assertFalse(result.has("ext"));
        assertFalse(result.has("storage"));
        postFormOk(hubPath() + "/copy", Map.of("sourceNamespaceId", "public", "sourceName", source,
            "sourceVersion", "1.0.0", "targetNamespaceId", "public", "targetName", copied));
        addCleanup(() -> deleteSkillQuietly(copied));
        assertError(postRaw(hubPath() + "/import", Query.newInstance().addParam("hubEntryId", share.get("hubEntryId").asText())
            .addParam("targetNamespaceId", "public").addParam("targetName", imported)),
            409, ErrorCode.RESOURCE_CONFLICT, "");
        publication[0] = postFormOk(hubPath() + "/unshare", entryParams(share)).get("data");
        deleteSkillQuietly(source);
        for (String name : new String[]{imported, copied}) {
            JsonNode content = getJsonOk(ADMIN_SKILL_VERSION_PATH, skillVersionQuery(name, "0.0.1")).get("data");
            assertTrue(content.get("skillMd").asText().contains("Independent pinned content."));
            assertTrue(content.get("skillMd").asText().contains("name: " + name));
            JsonNode targetDetail = getJsonOk(ADMIN_SKILL_PATH, skillQuery(name)).get("data");
            assertEquals("PRIVATE", targetDetail.get("scope").asText());
            JsonNode origin = targetDetail.get("origin");
            assertEquals(source, origin.get("sourceName").asText());
            assertEquals("1.0.0", origin.get("sourceVersion").asText());
            assertEquals(name.equals(imported) ? "HUB_IMPORT" : "DIRECT_COPY", origin.get("mode").asText());
            assertFalse(origin.has("sourceNamespaceId"));
            assertFalse(origin.has("sourceVersionId"));
            assertFalse(origin.has("storage"));
        }
    }

    @Test
    void sourceWithdrawalRevokesHubContentAndDirectoryWithoutExplicitUnshare() throws Exception {
        for (String operation : new String[] {"delete", "disable", "private"}) {
            String name = "hub-s4-" + operation + "-" + UUID.randomUUID();
            JsonNode publication = createS4Publication(name);
            Query entry = Query.newInstance().addParam("hubEntryId", publication.get("hubEntryId").asText());
            getJsonOk(hubPath() + "/content", entry, AuthIdentity.CLIENT_NO_PERMISSION);
            if ("delete".equals(operation)) {
                deleteJsonOk(ADMIN_SKILL_PATH, skillQuery(name));
            } else if ("disable".equals(operation)) {
                postFormOk(ADMIN_SKILL_PATH + "/offline",
                    Map.of("namespaceId", "public", "skillName", name, "scope", "skill"));
            } else {
                putFormOk(ADMIN_SKILL_PATH + "/scope", skillScopeForm(name, "PRIVATE"));
            }
            assertError(getRaw(hubPath() + "/content", entry, AuthIdentity.CLIENT_NO_PERMISSION),
                404, ErrorCode.RESOURCE_NOT_FOUND, "");
            assertEquals(0, getJsonOk(hubPath() + "/list", Query.newInstance().addParam("query", name))
                .get("data").get("totalCount").asInt());
            if ("private".equals(operation)) {
                putFormOk(ADMIN_SKILL_PATH + "/scope", skillScopeForm(name, "PUBLIC"));
                assertError(getRaw(hubPath() + "/content", entry), 404, ErrorCode.RESOURCE_NOT_FOUND, "");
            }
        }
    }

    @Test
    void unrelatedVersionOfflinePreservesShareAndPinnedVersionOnlineDoesNotRepublish() throws Exception {
        String name = "hub-s4-version-" + UUID.randomUUID();
        JsonNode publication = createS4Publication(name);
        Query entry = Query.newInstance().addParam("hubEntryId", publication.get("hubEntryId").asText());
        postFormOk(ADMIN_SKILL_PATH + "/draft", skillForkForm(name, "2.0.0", "1.0.0"));
        postFormOk(ADMIN_SKILL_PATH + "/force-publish", withUpdateLatestLabel(skillPublishForm(name, "2.0.0")));
        postFormOk(ADMIN_SKILL_PATH + "/offline",
            Map.of("namespaceId", "public", "skillName", name, "scope", "version", "version", "2.0.0"));
        assertEquals("1.0.0", getJsonOk(hubPath() + "/content", entry).get("data").get("sharedVersion").asText());
        postFormOk(ADMIN_SKILL_PATH + "/offline",
            Map.of("namespaceId", "public", "skillName", name, "scope", "version", "version", "1.0.0"));
        assertError(getRaw(hubPath() + "/content", entry), 404, ErrorCode.RESOURCE_NOT_FOUND, "");
        postFormOk(ADMIN_SKILL_PATH + "/online",
            Map.of("namespaceId", "public", "skillName", name, "scope", "version", "version", "1.0.0"));
        assertError(getRaw(hubPath() + "/content", entry), 404, ErrorCode.RESOURCE_NOT_FOUND, "");
        postFormOk(hubPath() + "/share", Map.of("sourceNamespaceId", "public", "sourceName", name,
            "sourceVersion", "1.0.0"));
        getJsonOk(hubPath() + "/content", entry);
    }

    private JsonNode createS4Publication(String name) throws Exception {
        postFormOk(ADMIN_SKILL_PATH + "/draft", skillDraftForm(name, "1.0.0", "S4 pinned content.", null));
        addCleanup(() -> deleteSkillQuietly(name));
        postFormOk(ADMIN_SKILL_PATH + "/force-publish", withUpdateLatestLabel(skillPublishForm(name, "1.0.0")));
        putFormOk(ADMIN_SKILL_PATH + "/scope", skillScopeForm(name, "PUBLIC"));
        return postFormOk(hubPath() + "/share", Map.of("sourceNamespaceId", "public", "sourceName", name,
            "sourceVersion", "1.0.0")).get("data");
    }

    @Test
    void shareStateEndpointsExposeSafeCasStateAcrossShareAndUnshare() throws Exception {
        String name = "hub-s5-state-" + UUID.randomUUID();
        postFormOk(ADMIN_SKILL_PATH + "/draft", skillDraftForm(name, "1.0.0", "S5 pinned source.", null));
        addCleanup(() -> deleteSkillQuietly(name));
        Query source = Query.newInstance().addParam("sourceNamespaceId", "public").addParam("sourceName", name);
        JsonNode empty = getJsonOk(hubPath() + "/source-share", source).path("data");
        assertTrue(empty.isMissingNode() || empty.isNull());
        postFormOk(ADMIN_SKILL_PATH + "/force-publish", withUpdateLatestLabel(skillPublishForm(name, "1.0.0")));
        putFormOk(ADMIN_SKILL_PATH + "/scope", skillScopeForm(name, "PUBLIC"));
        JsonNode share = postFormOk(hubPath() + "/share", Map.of("sourceNamespaceId", "public",
                "sourceName", name, "sourceVersion", "1.0.0")).get("data");
        JsonNode ownerState = getJsonOk(hubPath() + "/source-share", source).get("data");
        assertEquals(share.get("hubEntryId"), ownerState.get("hubEntryId"));
        assertEquals(share.get("metaVersion"), ownerState.get("metaVersion"));
        assertTrue(ownerState.get("active").asBoolean());
        assertFalse(ownerState.has("ext"));
        assertFalse(ownerState.has("sourceNamespaceId"));
        Query entry = Query.newInstance().addParam("hubEntryId", share.get("hubEntryId").asText());
        assertEquals(ownerState, getJsonOk(hubPath() + "/governance-state", entry).get("data"));
        assertError(getRaw(hubPath() + "/source-share", source, AuthIdentity.CLIENT_NO_PERMISSION),
                403, ErrorCode.ACCESS_DENIED, "");
        JsonNode withdrawn = postFormOk(hubPath() + "/unshare", entryParams(share)).get("data");
        JsonNode inactive = getJsonOk(hubPath() + "/source-share", source).get("data");
        assertFalse(inactive.get("active").asBoolean());
        assertEquals(withdrawn.get("metaVersion"), inactive.get("metaVersion"));
    }

    @Test
    void mutationStateRejectsAnonymousAndOrdinaryGovernanceUsers() throws Exception {
        for (String endpoint : new String[] {"source-share", "governance-state"}) {
            assertError(getRaw(hubPath() + "/" + endpoint, Query.newInstance(), AuthIdentity.ANONYMOUS),
                    403, ErrorCode.ACCESS_DENIED, "");
        }
        assertError(getRaw(hubPath() + "/governance-state",
                Query.newInstance().addParam("hubEntryId", "1"), AuthIdentity.CLIENT_NO_PERMISSION),
                403, ErrorCode.ACCESS_DENIED, "");
        assertError(getRaw(hubPath() + "/governance-state",
                Query.newInstance().addParam("hubEntryId", "9223372036854775808")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "");
    }

}
