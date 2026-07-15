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

package com.alibaba.nacos.test.adminapi.auth;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.test.adminapi.ai.AiAdminApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for auth AI visibility grant API {@code /nacos/v3/auth/ai/visibility}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: grant, list, and revoke explicit AI visibility access for an existing skill
 *     resource through the auth plugin owned API.</li>
 *     <li>Boundary/validation: write grant requests normalize to stored action {@code rw}; unsupported actions
 *     return a wrapped HTTP 400 validation error.</li>
 *     <li>Exception/error handling: grant requests for a missing AI resource return wrapped HTTP 404
 *     {@code RESOURCE_NOT_FOUND} instead of an internal error.</li>
 * </ul>
 *
 * <p>The default standalone IT profile runs without auth bootstrap, so this class validates the public API
 * contract and resource lookup integration, but not the auth-enabled owner/global-admin management rule.</p>
 *
 * @author Zhengcy05
 */
public class AiVisibilityGrantAuthApiITCase extends AiAdminApiBaseITCase {

    private static final String AUTH_AI_VISIBILITY_PATH = nacosPath("/v3/auth/ai/visibility");

    private static final String AUTH_AI_VISIBILITY_LIST_PATH = AUTH_AI_VISIBILITY_PATH + "/list";

    @Test
    public void testGrantListAndRevokeVisibilityGrant() throws Exception {
        String skillName = randomAiName("visibility-auth");
        postFormOk(ADMIN_SKILL_PATH + "/draft",
                skillDraftForm(skillName, "1.0.0", "visibility body", "visibility guide"));
        addCleanup(() -> deleteSkillQuietly(skillName));

        JsonNode grant = postFormOk(AUTH_AI_VISIBILITY_PATH,
                visibilityGrantQuery(skillName, "readerA", "w"));
        assertEquals("grant ai visibility permission ok!", grant.get("data").asText(), grant.toString());

        JsonNode list = getJsonOk(AUTH_AI_VISIBILITY_LIST_PATH, visibilityResourceQuery(skillName)).get("data");
        assertEquals(1, list.size(), list.toString());
        JsonNode item = list.get(0);
        assertEquals(DEFAULT_NAMESPACE, item.get("namespaceId").asText(), item.toString());
        assertEquals("skill", item.get("resourceType").asText(), item.toString());
        assertEquals(skillName, item.get("resourceName").asText(), item.toString());
        assertEquals("readerA", item.get("username").asText(), item.toString());
        assertEquals("rw", item.get("action").asText(), item.toString());

        JsonNode revoke = deleteJsonOk(AUTH_AI_VISIBILITY_PATH, visibilityGrantQuery(skillName, "readerA", "w"));
        assertEquals("revoke ai visibility permission ok!", revoke.get("data").asText(), revoke.toString());

        JsonNode afterRevoke =
                getJsonOk(AUTH_AI_VISIBILITY_LIST_PATH, visibilityResourceQuery(skillName)).get("data");
        assertTrue(afterRevoke.isArray(), afterRevoke.toString());
        assertEquals(0, afterRevoke.size(), afterRevoke.toString());
    }

    @Test
    public void testGrantValidationAndNotFoundErrors() throws Exception {
        assertError(postRaw(AUTH_AI_VISIBILITY_PATH,
                visibilityGrantQuery(randomAiName("missing-visibility"), "readerA", "r")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "AI resource not found");

        String skillName = randomAiName("visibility-invalid-action");
        postFormOk(ADMIN_SKILL_PATH + "/draft",
                skillDraftForm(skillName, "1.0.0", "invalid action body", "invalid action guide"));
        addCleanup(() -> deleteSkillQuietly(skillName));

        assertError(postRaw(AUTH_AI_VISIBILITY_PATH, visibilityGrantQuery(skillName, "readerA", "x")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "unsupported action");
    }

    private Query visibilityResourceQuery(String resourceName) {
        return Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE).addParam("resourceType", "skill")
                .addParam("resourceName", resourceName);
    }

    private Query visibilityGrantQuery(String resourceName, String username, String action) {
        return visibilityResourceQuery(resourceName).addParam("username", username).addParam("action", action);
    }
}
