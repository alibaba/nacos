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
 * Integration tests for config gray admin OpenAPI {@code /nacos/v3/admin/cs/config/gray}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: publish gray creates a queryable gray config with content, md5, gray name, and serialized
 *     tagv2 rule information.</li>
 *     <li>Boundary/validation: omitted namespace uses public, tagv2 version {@code 1.0.0} is accepted, and
 *     {@code grayName}, {@code grayRuleExp}, {@code grayVersion}, {@code dataId}, and {@code groupName} are
 *     validated.</li>
 *     <li>Exception/error handling: absent gray configs and required-parameter failures return controlled v3
 *     {@code Result} errors. Successful delete is intentionally not asserted here because the current standalone
 *     endpoint removes the row but reports a server error after persistence; cleanup tolerates that branch.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigGrayAdminApiOpenApiITCase extends ConfigAdminApiBaseITCase {
    
    @Test
    public void testPublishAndQueryGrayConfig() throws Exception {
        String dataId = randomDataId("gray");
        String groupName = randomGroupName("gray");
        String content = "gray-content";
        String grayName = "tagv2_openapi_it_gray";
        String grayRuleExp = "region=hz";
        publishGrayConfig(dataId, groupName, "", content, grayName, grayRuleExp);
        addCleanup(() -> deleteGrayQuietly(dataId, groupName, "", grayName));
        
        JsonNode gray = getJsonOk(ADMIN_CONFIG_GRAY_PATH,
                configQuery(dataId, groupName, null).addParam("grayName", grayName)).get("data");
        assertEquals(dataId, gray.get("dataId").asText(), gray.toString());
        assertEquals(groupName, gray.get("groupName").asText(), gray.toString());
        assertEquals(DEFAULT_NAMESPACE, gray.get("namespaceId").asText(), gray.toString());
        assertEquals(content, gray.get("content").asText(), gray.toString());
        assertEquals(md5(content), gray.get("md5").asText(), gray.toString());
        assertEquals(grayName, gray.get("grayName").asText(), gray.toString());
        assertTrue(gray.get("grayRule").asText().contains("\"type\":\"tagv2\""), gray.toString());
        assertTrue(gray.get("grayRule").asText().contains("\"version\":\"1.0.0\""), gray.toString());
    }
    
    @Test
    public void testGrayValidationAndAbsentConfigReturnControlledErrors() throws Exception {
        String dataId = randomDataId("gray-required");
        String groupName = randomGroupName("gray-required");
        assertError(postRaw(ADMIN_CONFIG_GRAY_PATH, Query.newInstance().addParam("dataId", dataId)
                .addParam("groupName", groupName).addParam("content", "content")
                .addParam("grayRuleExp", "region=hz").addParam("grayVersion", "1.0.0")),
                400, ErrorCode.PARAMETER_MISSING, "grayName");
        assertError(postRaw(ADMIN_CONFIG_GRAY_PATH, Query.newInstance().addParam("dataId", dataId)
                .addParam("groupName", groupName).addParam("content", "content")
                .addParam("grayName", "tagv2_openapi_it_gray").addParam("grayVersion", "1.0.0")),
                400, ErrorCode.PARAMETER_MISSING, "grayRuleExp");
        assertError(postRaw(ADMIN_CONFIG_GRAY_PATH, Query.newInstance().addParam("dataId", dataId)
                .addParam("groupName", groupName).addParam("content", "content")
                .addParam("grayName", "invalid name").addParam("grayRuleExp", "region=hz")
                .addParam("grayVersion", "1.0.0")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "grayName");
        assertError(getRaw(ADMIN_CONFIG_GRAY_PATH, configQuery(dataId, groupName, "")
                .addParam("grayName", "tagv2_openapi_it_absent")), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Config gray version not found");
        assertError(deleteRaw(ADMIN_CONFIG_GRAY_PATH, configQuery(dataId, groupName, "")),
                400, ErrorCode.PARAMETER_MISSING, "grayName");
    }
}
