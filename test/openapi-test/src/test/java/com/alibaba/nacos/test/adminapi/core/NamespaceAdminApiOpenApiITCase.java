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

package com.alibaba.nacos.test.adminapi.core;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Integration tests for core namespace admin OpenAPI {@code /nacos/v3/admin/core/namespace}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: create persists namespace metadata, detail/list/check expose the namespace, update
 *     changes display metadata, and delete removes it.</li>
 *     <li>Boundary/validation: explicit namespace ids are trimmed and limited to 128 characters; namespace names reject
 *     reserved characters; {@code namespaceId} and {@code namespaceName} are required by the deployed form.</li>
 *     <li>Exception/error handling: required-field and illegal namespace input return HTTP 400 with the v3
 *     {@code Result} error envelope.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class NamespaceAdminApiOpenApiITCase extends CoreAdminApiBaseITCase {

    @Test
    public void testCreateDetailUpdateListCheckAndDeleteNamespace() throws Exception {
        String namespaceId = randomNamespaceId("namespace");
        postFormOk(ADMIN_CORE_NAMESPACE_PATH,
                namespaceQuery(namespaceId, "openapi namespace", "created by openapi it"));
        addCleanup(() -> deleteNamespaceQuietly(namespaceId));

        JsonNode detail = getJsonOk(ADMIN_CORE_NAMESPACE_PATH,
                Query.newInstance().addParam("namespaceId", namespaceId)).get("data");
        assertEquals(namespaceId, detail.get("namespace").asText(), detail.toString());
        assertEquals("openapi namespace", detail.get("namespaceShowName").asText(), detail.toString());
        assertEquals("created by openapi it", detail.get("namespaceDesc").asText(), detail.toString());

        JsonNode list = getJsonOk(ADMIN_CORE_NAMESPACE_PATH + "/list", Query.newInstance()).get("data");
        assertFalse(findNamespace(list, namespaceId).isMissingNode(), list.toString());
        JsonNode check = getJsonOk(ADMIN_CORE_NAMESPACE_PATH + "/check",
                Query.newInstance().addParam("namespaceId", namespaceId)).get("data");
        assertEquals(1, check.asInt(), check.toString());

        JsonNode updated = putFormOk(ADMIN_CORE_NAMESPACE_PATH,
                namespaceQuery(namespaceId, "openapi namespace updated", "updated by openapi it"));
        assertEquals(true, updated.get("data").asBoolean(), updated.toString());
        JsonNode updatedDetail = getJsonOk(ADMIN_CORE_NAMESPACE_PATH,
                Query.newInstance().addParam("namespaceId", namespaceId)).get("data");
        assertEquals("openapi namespace updated", updatedDetail.get("namespaceShowName").asText(),
                updatedDetail.toString());
        assertEquals("updated by openapi it", updatedDetail.get("namespaceDesc").asText(),
                updatedDetail.toString());

        JsonNode deleted = deleteJsonOk(ADMIN_CORE_NAMESPACE_PATH,
                Query.newInstance().addParam("namespaceId", namespaceId));
        assertEquals(true, deleted.get("data").asBoolean(), deleted.toString());
        JsonNode checkAfterDelete = getJsonOk(ADMIN_CORE_NAMESPACE_PATH + "/check",
                Query.newInstance().addParam("namespaceId", namespaceId)).get("data");
        assertEquals(0, checkAfterDelete.asInt(), checkAfterDelete.toString());
    }

    @Test
    public void testNamespaceValidationReturnsBadRequest() throws Exception {
        assertError(postRaw(ADMIN_CORE_NAMESPACE_PATH,
                Query.newInstance().addParam("namespaceName", "missing id")), 400,
                ErrorCode.PARAMETER_MISSING, "namespaceId");
        assertError(postRaw(ADMIN_CORE_NAMESPACE_PATH,
                Query.newInstance().addParam("namespaceId", randomNamespaceId("missing-name"))),
                400, ErrorCode.PARAMETER_MISSING, "namespaceName");
        assertError(postRaw(ADMIN_CORE_NAMESPACE_PATH,
                namespaceQuery(randomNamespaceId("bad-name"), "bad@name", "bad")), 400,
                ErrorCode.ILLEGAL_NAMESPACE, "namespaceName");
        assertError(postRaw(ADMIN_CORE_NAMESPACE_PATH,
                namespaceQuery("n".repeat(129), "too long", "bad")), 400,
                ErrorCode.ILLEGAL_NAMESPACE, "too long namespaceId");
    }

    private JsonNode findNamespace(JsonNode namespaces, String namespaceId) {
        for (JsonNode each : namespaces) {
            if (namespaceId.equals(each.get("namespace").asText())) {
                return each;
            }
        }
        return MissingNode.getInstance();
    }
}
