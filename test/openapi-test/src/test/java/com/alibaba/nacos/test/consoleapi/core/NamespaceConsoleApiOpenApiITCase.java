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

package com.alibaba.nacos.test.consoleapi.core;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for console namespace OpenAPIs under {@code /nacos/v3/console/core/namespace}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: create persists namespace metadata, detail/list/exist expose it, update changes
 *     display metadata, and delete removes it.</li>
 *     <li>Boundary/validation: {@code customNamespaceId} is accepted for explicit creation, blank
 *     {@code customNamespaceId} in exist returns false, namespace id/name are required where the form requires them,
 *     and namespace name/id domain validation is enforced.</li>
 *     <li>Exception/error handling: required-field and illegal namespace input return HTTP 400 with the v3
 *     {@code Result} error envelope rather than HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class NamespaceConsoleApiOpenApiITCase extends CoreConsoleApiBaseITCase {

    @Test
    public void testCreateDetailUpdateListExistAndDeleteNamespace() throws Exception {
        String namespaceId = randomNamespaceId("crud");
        JsonNode created = postFormOk(CONSOLE_NAMESPACE_PATH,
                namespaceCreateQuery(namespaceId, "console namespace", "created by console it"));
        assertTrue(created.get("data").asBoolean(), created.toString());
        addCleanup(() -> deleteNamespaceQuietly(namespaceId));

        JsonNode detail = getJsonOk(CONSOLE_NAMESPACE_PATH,
                Query.newInstance().addParam("namespaceId", namespaceId)).get("data");
        assertEquals(namespaceId, detail.get("namespace").asText(), detail.toString());
        assertEquals("console namespace", detail.get("namespaceShowName").asText(), detail.toString());
        assertEquals("created by console it", detail.get("namespaceDesc").asText(), detail.toString());

        JsonNode list = getJsonOk(CONSOLE_NAMESPACE_LIST_PATH, Query.newInstance()).get("data");
        assertNamespaceListed(list, namespaceId);

        JsonNode exists = getJsonOk(CONSOLE_NAMESPACE_EXIST_PATH,
                Query.newInstance().addParam("customNamespaceId", namespaceId)).get("data");
        assertTrue(exists.asBoolean(), exists.toString());

        JsonNode updated = putFormOk(CONSOLE_NAMESPACE_PATH,
                namespaceUpdateQuery(namespaceId, "console namespace updated", "updated by console it"));
        assertTrue(updated.get("data").asBoolean(), updated.toString());

        JsonNode updatedDetail = getJsonOk(CONSOLE_NAMESPACE_PATH,
                Query.newInstance().addParam("namespaceId", namespaceId)).get("data");
        assertEquals("console namespace updated", updatedDetail.get("namespaceShowName").asText(),
                updatedDetail.toString());
        assertEquals("updated by console it", updatedDetail.get("namespaceDesc").asText(),
                updatedDetail.toString());

        JsonNode blankExists = getJsonOk(CONSOLE_NAMESPACE_EXIST_PATH,
                Query.newInstance().addParam("customNamespaceId", "")).get("data");
        assertFalse(blankExists.asBoolean(), blankExists.toString());

        JsonNode deleted = deleteJsonOk(CONSOLE_NAMESPACE_PATH,
                Query.newInstance().addParam("namespaceId", namespaceId));
        assertTrue(deleted.get("data").asBoolean(), deleted.toString());

        JsonNode existsAfterDelete = getJsonOk(CONSOLE_NAMESPACE_EXIST_PATH,
                Query.newInstance().addParam("customNamespaceId", namespaceId)).get("data");
        assertFalse(existsAfterDelete.asBoolean(), existsAfterDelete.toString());
    }

    @Test
    public void testNamespaceValidationReturnsBadRequest() throws Exception {
        assertError(postRaw(CONSOLE_NAMESPACE_PATH,
                Query.newInstance().addParam("customNamespaceId", randomNamespaceId("missing-name"))),
                400, ErrorCode.PARAMETER_MISSING, "namespaceName");
        assertError(putRaw(CONSOLE_NAMESPACE_PATH,
                namespaceUpdateQuery(randomNamespaceId("missing-name"), null, "desc")),
                400, ErrorCode.PARAMETER_MISSING, "namespaceName");
        assertError(putRaw(CONSOLE_NAMESPACE_PATH,
                namespaceUpdateQuery(null, "missing id", "desc")), 400,
                ErrorCode.PARAMETER_MISSING, "namespaceId");
        assertError(postRaw(CONSOLE_NAMESPACE_PATH,
                namespaceCreateQuery(randomNamespaceId("bad-name"), "bad@name", "bad")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "namespaceShowName");
        assertError(postRaw(CONSOLE_NAMESPACE_PATH,
                namespaceCreateQuery("n".repeat(129), "too long", "bad")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "length should not exceed 64");
        assertError(getRaw(CONSOLE_NAMESPACE_PATH), 400, ErrorCode.PARAMETER_MISSING, "namespaceId");
        assertError(getRaw(CONSOLE_NAMESPACE_EXIST_PATH), 400, ErrorCode.PARAMETER_MISSING,
                "customNamespaceId");
    }
}
