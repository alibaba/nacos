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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for core ops admin OpenAPI {@code /nacos/v3/admin/core/ops}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: id-generator diagnostics return a list shape, and runtime log-level update accepts a
 *     valid JSON request.</li>
 *     <li>Boundary/validation: raft maintenance requires {@code command}/{@code value}; log update requires
 *     {@code logName}/{@code logLevel}. Raft success commands are intentionally not executed because they can affect CP
 *     leadership, snapshots, or peers.</li>
 *     <li>Exception/error handling: missing JSON-body fields return HTTP 400 with the v3 {@code Result} envelope.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class CoreOpsAdminApiOpenApiITCase extends CoreAdminApiBaseITCase {

    @Test
    public void testIdsAndLogUpdate() throws Exception {
        JsonNode ids = getJsonOk(ADMIN_CORE_OPS_PATH + "/ids", Query.newInstance()).get("data");
        assertTrue(ids.isArray(), ids.toString());
        for (JsonNode each : ids) {
            assertTrue(each.get("resource").asText().length() > 0, each.toString());
            assertTrue(each.get("info").has("currentId"), each.toString());
            assertTrue(each.get("info").has("workerId"), each.toString());
        }

        JsonNode logUpdate = putJsonOk(ADMIN_CORE_OPS_PATH + "/log", Query.newInstance(),
                "{\"logName\":\"core\",\"logLevel\":\"INFO\"}");
        assertEquals(0, logUpdate.get("code").asInt(), logUpdate.toString());
    }

    @Test
    public void testCoreOpsValidationReturnsBadRequest() throws Exception {
        assertError(postJsonRaw(ADMIN_CORE_OPS_PATH + "/raft", Query.newInstance(), "{}"),
                400, ErrorCode.PARAMETER_MISSING, "Raft command");
        assertError(postJsonRaw(ADMIN_CORE_OPS_PATH + "/raft", Query.newInstance(),
                "{\"command\":\"doSnapshot\"}"), 400, ErrorCode.PARAMETER_MISSING,
                "Raft command value");
        assertError(putJsonRaw(ADMIN_CORE_OPS_PATH + "/log", Query.newInstance(), "{}"),
                400, ErrorCode.PARAMETER_MISSING, "Log name");
        assertError(putJsonRaw(ADMIN_CORE_OPS_PATH + "/log", Query.newInstance(),
                "{\"logName\":\"core\"}"), 400, ErrorCode.PARAMETER_MISSING, "Log level");
    }
}
