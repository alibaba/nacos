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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for config beta admin OpenAPI {@code /nacos/v3/admin/cs/config/beta}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: a beta publish made through the config publish API can be queried from the beta API and
 *     can be stopped, after which beta query returns not found.</li>
 *     <li>Boundary/validation: omitted namespace uses public, beta rule is generated from {@code betaIps}, and
 *     {@code dataId}/{@code groupName} are required.</li>
 *     <li>Exception/error handling: absent beta configs return HTTP 404 with the v3 {@code Result} error envelope
 *     instead of HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigBetaAdminApiOpenApiITCase extends ConfigAdminApiBaseITCase {
    
    @Test
    public void testQueryAndStopBetaConfig() throws Exception {
        String dataId = randomDataId("beta");
        String groupName = randomGroupName("beta");
        String content = "beta-content";
        String betaIps = "127.0.0.1";
        publishBetaConfig(dataId, groupName, "", content, betaIps);
        addCleanup(() -> deleteBetaQuietly(dataId, groupName, ""));
        
        JsonNode beta = getJsonOk(ADMIN_CONFIG_BETA_PATH, configQuery(dataId, groupName, null)).get("data");
        assertEquals(dataId, beta.get("dataId").asText(), beta.toString());
        assertEquals(groupName, beta.get("groupName").asText(), beta.toString());
        assertEquals(DEFAULT_NAMESPACE, beta.get("namespaceId").asText(), beta.toString());
        assertEquals(content, beta.get("content").asText(), beta.toString());
        assertEquals("beta", beta.get("grayName").asText(), beta.toString());
        assertTrue(beta.get("grayRule").asText().contains(betaIps), beta.toString());
        
        JsonNode stopped = deleteJsonOk(ADMIN_CONFIG_BETA_PATH, configQuery(dataId, groupName, ""));
        assertTrue(stopped.get("data").asBoolean(), stopped.toString());
        assertError(getRaw(ADMIN_CONFIG_BETA_PATH, configQuery(dataId, groupName, "")), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Config is not in beta");
    }
    
    @Test
    public void testBetaRequiredParametersAndAbsentConfigReturnControlledErrors() throws Exception {
        String dataId = randomDataId("beta-required");
        String groupName = randomGroupName("beta-required");
        assertError(getRaw(ADMIN_CONFIG_BETA_PATH, Query.newInstance().addParam("groupName", groupName)),
                400, ErrorCode.PARAMETER_MISSING, "dataId");
        assertError(getRaw(ADMIN_CONFIG_BETA_PATH, Query.newInstance().addParam("dataId", dataId)),
                400, ErrorCode.PARAMETER_MISSING, "groupName");
        assertError(getRaw(ADMIN_CONFIG_BETA_PATH, configQuery(dataId, groupName, "")), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Config is not in beta");
    }
}
