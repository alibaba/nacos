/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
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

import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for core state admin OpenAPI {@code /nacos/v3/admin/core/state}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: server state returns the deployed module-state map, liveness returns {@code ok}, and
 *     readiness returns the standard v3 success envelope when the standalone server is ready.</li>
 *     <li>Boundary/validation: the endpoints expose no required request parameters; an unexpected query parameter is
 *     ignored by the state endpoint and must not change the response shape.</li>
 *     <li>Exception/error handling: the readiness failure branch is not forced because it requires making the shared
 *     standalone server unhealthy; this class verifies the healthy contract and Result shape for the deployed server.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class CoreStateAdminApiOpenApiITCase extends CoreAdminApiBaseITCase {

    @Test
    public void testServerStateLivenessAndReadiness() throws Exception {
        JsonNode state = getJsonOk(ADMIN_CORE_STATE_PATH, Query.newInstance()).get("data");
        assertTrue(state.isObject(), state.toString());
        assertTrue(state.size() > 0, state.toString());

        JsonNode stateWithUnexpectedQuery = getJsonOk(ADMIN_CORE_STATE_PATH,
                Query.newInstance().addParam("unexpected", "ignored")).get("data");
        assertTrue(stateWithUnexpectedQuery.isObject(), stateWithUnexpectedQuery.toString());

        JsonNode liveness = getJsonOk(ADMIN_CORE_STATE_PATH + "/liveness", Query.newInstance());
        assertEquals("ok", liveness.get("data").asText(), liveness.toString());

        JsonNode readiness = getJsonOk(ADMIN_CORE_STATE_PATH + "/readiness", Query.newInstance());
        assertEquals("ok", readiness.get("data").asText(), readiness.toString());
    }
}
