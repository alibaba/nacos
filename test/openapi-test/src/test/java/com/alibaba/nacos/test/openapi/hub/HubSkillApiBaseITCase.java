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

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Shared deployed-server scenarios for both Hub mutation audiences.
 *
 * <p>Requires enabled Admin/Console auth and the default visibility plugin.
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
        String name = "hub-api-it-" + java.util.UUID.randomUUID();
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
}
