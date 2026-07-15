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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for core server-loader admin OpenAPI {@code /nacos/v3/admin/core/loader}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: current local connection map and cluster loader metrics return the v3 success envelope
 *     and diagnostic shapes.</li>
 *     <li>Boundary/validation: reload-by-count and reload-single-client require {@code count} and
 *     {@code connectionId}; smart cluster reload requires a numeric {@code loaderFactor}; destructive connection
 *     rebalance success paths are intentionally not executed.</li>
 *     <li>Exception/error handling: missing required parameters return controlled HTTP 400 errors instead of invoking
 *     rebalance operations.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ServerLoaderAdminApiOpenApiITCase extends CoreAdminApiBaseITCase {

    @Test
    public void testCurrentClientsAndClusterMetrics() throws Exception {
        JsonNode current = getJsonOk(ADMIN_CORE_LOADER_PATH + "/current", Query.newInstance()).get("data");
        assertTrue(current.isObject(), current.toString());

        JsonNode metrics = getJsonOk(ADMIN_CORE_LOADER_PATH + "/cluster", Query.newInstance()).get("data");
        assertTrue(metrics.get("memberCount").asInt() >= 1, metrics.toString());
        assertTrue(metrics.get("metricsCount").asInt() >= 0, metrics.toString());
        assertTrue(metrics.get("total").asInt() >= 0, metrics.toString());
        assertNotNull(metrics.get("detail"), metrics.toString());
    }

    @Test
    public void testLoaderValidationReturnsBadRequest() throws Exception {
        assertError(postRaw(ADMIN_CORE_LOADER_PATH + "/reloadCurrent", Query.newInstance()),
                400, ErrorCode.PARAMETER_MISSING, "count");
        assertError(postRaw(ADMIN_CORE_LOADER_PATH + "/reloadClient", Query.newInstance()),
                400, ErrorCode.PARAMETER_MISSING, "connectionId");
        assertError(postRaw(ADMIN_CORE_LOADER_PATH + "/smartReloadCluster",
                Query.newInstance().addParam("loaderFactor", "not-a-number")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "not-a-number");
    }
}
