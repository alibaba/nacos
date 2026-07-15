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

package com.alibaba.nacos.test.adminapi.config;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for config metrics admin OpenAPIs under {@code /nacos/v3/admin/cs/metrics}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: local ip metrics returns a map and cluster metrics returns a map with
 *     {@code complete} status for a dataId/groupName identity.</li>
 *     <li>Boundary/validation: namespace is defaulted for valid calls, dataId/groupName are required by the runtime
 *     parameter validator, and invalid namespace values are rejected.</li>
 *     <li>Exception/error handling: missing required {@code ip}, missing config identity, and invalid namespace values
 *     return HTTP 400 with the v3 {@code Result} error envelope instead of HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigMetricsAdminApiOpenApiITCase extends ConfigAdminApiBaseITCase {
    
    @Test
    public void testLocalAndClusterMetricsReturnResultMaps() throws Exception {
        Query metricQuery = Query.newInstance().addParam("ip", "127.0.0.1")
                .addParam("dataId", randomDataId("metrics")).addParam("groupName", randomGroupName("metrics"));
        JsonNode local = getJsonOk(ADMIN_METRICS_PATH + "/ip",
                metricQuery).get("data");
        assertTrue(local.isObject(), local.toString());
        
        JsonNode cluster = getJsonOk(ADMIN_METRICS_PATH + "/cluster",
                metricQuery).get("data");
        assertTrue(cluster.isObject(), cluster.toString());
        assertTrue(cluster.has("complete"), cluster.toString());
    }
    
    @Test
    public void testMetricsValidationReturnsBadRequest() throws Exception {
        assertError(getRaw(ADMIN_METRICS_PATH + "/ip"), 400, ErrorCode.PARAMETER_MISSING, "ip");
        assertError(getRaw(ADMIN_METRICS_PATH + "/cluster"), 400, ErrorCode.PARAMETER_MISSING, "ip");
        assertError(getRaw(ADMIN_METRICS_PATH + "/ip", Query.newInstance().addParam("ip", "127.0.0.1")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "dataId");
        assertError(getRaw(ADMIN_METRICS_PATH + "/ip",
                Query.newInstance().addParam("ip", "127.0.0.1").addParam("dataId", randomDataId("metrics-invalid"))
                        .addParam("groupName", randomGroupName("metrics-invalid"))
                        .addParam("namespaceId", "invalid namespace")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "namespaceId");
    }
}
