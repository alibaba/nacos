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

import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests for console health OpenAPIs under {@code /nacos/v3/console/health}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: liveness and readiness expose the console health state through v3 {@code Result}
 *     bodies when the standalone server is healthy.</li>
 *     <li>Boundary/validation: both APIs accept no required parameters; unexpected query parameters are ignored by
 *     liveness and must not change the response envelope.</li>
 *     <li>Exception/error handling: the readiness failure branch is not forced because it requires making the shared
 *     standalone server unhealthy; this class verifies that the healthy deployed contract is HTTP 200 and not a raw
 *     body or HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class HealthConsoleApiOpenApiITCase extends CoreConsoleApiBaseITCase {

    @Test
    public void testLivenessAndReadinessReturnHealthyResult() throws Exception {
        JsonNode liveness = getJsonOk(CONSOLE_HEALTH_PATH + "/liveness",
                Query.newInstance().addParam("unexpected", "ignored"));
        assertEquals("ok", liveness.get("data").asText(), liveness.toString());

        JsonNode readiness = getJsonOk(CONSOLE_HEALTH_PATH + "/readiness", Query.newInstance());
        assertEquals("ok", readiness.get("data").asText(), readiness.toString());
    }
}
