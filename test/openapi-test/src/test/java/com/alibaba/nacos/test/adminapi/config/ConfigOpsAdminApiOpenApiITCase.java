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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for config ops admin OpenAPIs under {@code /nacos/v3/admin/cs/ops}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: local-cache dump trigger returns the v3 success envelope when the standalone server can
 *     dump from store.</li>
 *     <li>Boundary/validation: log level update requires both {@code logName} and {@code logLevel}; Derby query
 *     requires {@code sql}; Derby import is invoked with a multipart file but its success path is intentionally not
 *     exercised because it mutates the embedded database from an uploaded external dump.</li>
 *     <li>Exception/error handling: missing required operation parameters return HTTP 400 with the v3 {@code Result}
 *     error envelope instead of HTTP 500, and disabled or non-embedded Derby import returns a controlled
 *     {@code Result} failure body instead of importing data.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigOpsAdminApiOpenApiITCase extends ConfigAdminApiBaseITCase {
    
    @Test
    public void testLocalCacheDumpReturnsSuccessEnvelope() throws Exception {
        HttpResponse response = postRaw(ADMIN_OPS_PATH + "/localCache", Query.newInstance());
        assertEquals(200, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertSuccess(root);
        assertEquals("Local cache updated from store successfully!", root.get("data").asText(), root.toString());
    }
    
    @Test
    public void testOpsRequiredParametersReturnBadRequest() throws Exception {
        assertError(putRaw(ADMIN_OPS_PATH + "/log", Query.newInstance().addParam("logLevel", "INFO")),
                400, ErrorCode.PARAMETER_MISSING, "logName");
        assertError(putRaw(ADMIN_OPS_PATH + "/log", Query.newInstance().addParam("logName", "config-server")),
                400, ErrorCode.PARAMETER_MISSING, "logLevel");
        assertError(getRaw(ADMIN_OPS_PATH + "/derby"), 400, ErrorCode.PARAMETER_MISSING, "sql");

        HttpResponse importResponse = postMultipartRaw(ADMIN_OPS_PATH + "/derby/import", Query.newInstance(),
                "file", "derby-import.sql", "text/plain", new byte[0]);
        assertEquals(200, importResponse.code(), importResponse.body());
        JsonNode importResult = JacksonUtils.toObj(importResponse.body());
        assertTrue(importResult.get("code").asInt() != ErrorCode.SUCCESS.getCode(), importResult.toString());
        assertTrue(importResult.get("message").asText().contains("Derby ops is disabled")
                || importResult.get("message").asText().contains("embedded storage mode"), importResult.toString());
    }
}
