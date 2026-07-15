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

package com.alibaba.nacos.test.adminapi.naming;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for naming operator admin OpenAPI {@code /nacos/v3/admin/ns/ops}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: switches can be queried and updated with debug mode, metrics returns status-only and
 *     full diagnostic shapes, and log-level update returns the standard success envelope.</li>
 *     <li>Boundary/validation: metrics defaults to {@code onlyStatus=true}; {@code entry}/{@code value} are required
 *     for switch updates; debug switch update is restored during cleanup.</li>
 *     <li>Exception/error handling: invalid switch values are converted into a controlled SERVER_ERROR response rather
 *     than an unwrapped exception.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class OperatorAdminApiOpenApiITCase extends NamingAdminApiBaseITCase {

    private static final String SWITCHES_PATH = ADMIN_OPERATOR_PATH + "/switches";

    private static final String METRICS_PATH = ADMIN_OPERATOR_PATH + "/metrics";

    private static final String LOG_PATH = ADMIN_OPERATOR_PATH + "/log";

    @Test
    public void testSwitchesMetricsAndLogLevelOperations() throws Exception {
        JsonNode switches = getJsonOk(SWITCHES_PATH, Query.newInstance()).get("data");
        assertEquals("00-00---000-NACOS_SWITCH_DOMAIN-000---00-00", switches.get("name").asText(),
                switches.toString());
        assertTrue(switches.has("lightBeatEnabled"), switches.toString());
        boolean originalLightBeatEnabled = switches.get("lightBeatEnabled").asBoolean();
        addCleanup(() -> putFormOk(SWITCHES_PATH, switchQuery("lightBeatEnabled",
                String.valueOf(originalLightBeatEnabled), "true")));

        JsonNode switchUpdate = putFormOk(SWITCHES_PATH, switchQuery("lightBeatEnabled",
                String.valueOf(!originalLightBeatEnabled), "true"));
        assertEquals("ok", switchUpdate.get("data").asText(), switchUpdate.toString());
        JsonNode updatedSwitches = getJsonOk(SWITCHES_PATH, Query.newInstance()).get("data");
        assertEquals(!originalLightBeatEnabled, updatedSwitches.get("lightBeatEnabled").asBoolean(),
                updatedSwitches.toString());

        JsonNode defaultMetrics = getJsonOk(METRICS_PATH, Query.newInstance()).get("data");
        assertTrue(defaultMetrics.get("status").asText().length() > 0, defaultMetrics.toString());
        assertFalse(defaultMetrics.has("clientCount"), defaultMetrics.toString());

        JsonNode fullMetrics = getJsonOk(METRICS_PATH,
                Query.newInstance().addParam("onlyStatus", "false")).get("data");
        assertTrue(fullMetrics.get("status").asText().length() > 0, fullMetrics.toString());
        assertTrue(fullMetrics.get("clientCount").asInt() >= 0, fullMetrics.toString());
        assertTrue(fullMetrics.get("serviceCount").asInt() >= 0, fullMetrics.toString());

        JsonNode logUpdate = putFormOk(LOG_PATH, Query.newInstance().addParam("logName", "naming-main")
                .addParam("logLevel", "INFO"));
        assertEquals("ok", logUpdate.get("data").asText(), logUpdate.toString());
    }

    @Test
    public void testOperatorValidationReturnsBadRequestAndControlledServerError() throws Exception {
        assertError(putRaw(SWITCHES_PATH, Query.newInstance().addParam("value", "true")),
                400, ErrorCode.PARAMETER_MISSING, "entry");
        assertError(putRaw(SWITCHES_PATH, Query.newInstance().addParam("entry", "lightBeatEnabled")),
                400, ErrorCode.PARAMETER_MISSING, "value");
        assertError(putRaw(SWITCHES_PATH, switchQuery("distroThreshold", "-1", "true")),
                500, ErrorCode.SERVER_ERROR, "distroThreshold");
    }
}
