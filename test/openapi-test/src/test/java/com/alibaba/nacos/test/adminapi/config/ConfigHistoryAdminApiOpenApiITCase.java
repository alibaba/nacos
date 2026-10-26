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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for config history admin OpenAPIs under {@code /nacos/v3/admin/cs/history}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Schema: current and historical details preserve version-specific text, absent values may be omitted or null,
 *     explicit empty current schema is preserved, and history extInfo remains available.</li>
 *     <li>Expected capability: publish and republish create history rows, history list is newest first, detail returns
 *     the selected history content, previous returns the current config's latest historical content, and namespace
 *     config listing exposes the current config identity, with storage IDs represented as JSON strings.</li>
 *     <li>Boundary/validation: page size larger than the controller cap is accepted, {@code pageNo}/{@code pageSize},
 *     {@code dataId}, {@code groupName}, {@code nid}, and {@code namespaceId} are validated by their endpoints.</li>
 *     <li>Exception/error handling: missing, absent, and identity-mismatched history queries return controlled v3
 *     {@code Result} errors with HTTP 400 or 403 instead of HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigHistoryAdminApiOpenApiITCase extends ConfigAdminApiBaseITCase {

    @Test
    public void testSchemaInCurrentAndHistoricalDetails() throws Exception {
        String dataId = randomDataId("schema-history");
        String groupName = randomGroupName("schema-history");
        String firstSchema = "{\"type\":\"object\"}";
        String secondSchema = "{\"type\":\"string\"}";
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, ""));
        postFormOk(ADMIN_CONFIG_PATH, configQuery(dataId, groupName, "")
                .addParam("content", "without-schema").addParam("type", DEFAULT_TYPE));
        JsonNode withoutSchema = queryConfig(dataId, groupName, "").get("data");
        assertFalse(withoutSchema.hasNonNull("schema"), withoutSchema.toString());

        postFormOk(ADMIN_CONFIG_PATH, configQuery(dataId, groupName, "")
                .addParam("content", "first-content").addParam("type", DEFAULT_TYPE)
                .addParam("schema", firstSchema));
        postFormOk(ADMIN_CONFIG_PATH, configQuery(dataId, groupName, "")
                .addParam("content", "second-content").addParam("type", DEFAULT_TYPE)
                .addParam("schema", secondSchema));
        JsonNode current = queryConfig(dataId, groupName, "").get("data");
        assertEquals(secondSchema, current.path("schema").asText(), current.toString());

        JsonNode histories = getJsonOk(ADMIN_HISTORY_LIST_PATH,
                historyQuery(dataId, groupName, "", 1, 10)).get("data").get("pageItems");
        JsonNode detail = getJsonOk(ADMIN_HISTORY_PATH, configQuery(dataId, groupName, "")
                .addParam("nid", histories.get(0).get("id").asText())).get("data");
        assertEquals("first-content", detail.path("content").asText(), detail.toString());
        assertEquals(firstSchema, detail.path("schema").asText(), detail.toString());
        JsonNode extInfo = JacksonUtils.toObj(
                detail.get("extInfo").asText());
        assertEquals(firstSchema, extInfo.path("c_schema").asText(), detail.toString());

        JsonNode previous = getJsonOk(ADMIN_HISTORY_PATH + "/previous",
                configQuery(dataId, groupName, "").addParam("id", current.get("id").asText()))
                .get("data");
        assertEquals(firstSchema, previous.path("schema").asText(), previous.toString());
        JsonNode oldDetail = getJsonOk(ADMIN_HISTORY_PATH,
                configQuery(dataId, groupName, "")
                        .addParam("nid", histories.get(1).get("id").asText())).get("data");
        assertEquals("without-schema", oldDetail.path("content").asText(), oldDetail.toString());
        assertFalse(oldDetail.hasNonNull("schema"), oldDetail.toString());

        postFormOk(ADMIN_CONFIG_PATH, configQuery(dataId, groupName, "")
                .addParam("content", "empty-schema").addParam("type", DEFAULT_TYPE)
                .addParam("schema", ""));
        JsonNode emptySchema = queryConfig(dataId, groupName, "").get("data");
        assertTrue(emptySchema.path("schema").isTextual(), emptySchema.toString());
        assertEquals("", emptySchema.path("schema").asText(), emptySchema.toString());
    }
    
    @Test
    public void testHistoryListDetailPreviousAndNamespaceConfigs() throws Exception {
        String dataId = randomDataId("history");
        String groupName = randomGroupName("history");
        String firstContent = "history-first-content";
        String secondContent = "history-second-content";
        publishConfig(dataId, groupName, "", firstContent);
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, ""));
        publishConfig(dataId, groupName, "", secondContent);
        
        JsonNode currentConfig = queryConfig(dataId, groupName, "").get("data");
        assertConfigDetail(currentConfig, dataId, groupName, DEFAULT_NAMESPACE, secondContent, DEFAULT_TYPE);
        assertTrue(currentConfig.get("id").isTextual(), currentConfig.toString());
        
        JsonNode historyPage = getJsonOk(ADMIN_HISTORY_LIST_PATH,
                historyQuery(dataId, groupName, "", 1, 1000)).get("data");
        assertEquals(1, historyPage.get("pageNumber").asInt(), historyPage.toString());
        assertEquals(2, historyPage.get("totalCount").asInt(), historyPage.toString());
        assertEquals(2, historyPage.get("pageItems").size(), historyPage.toString());
        JsonNode newestHistory = historyPage.get("pageItems").get(0);
        JsonNode oldestHistory = historyPage.get("pageItems").get(1);
        assertTrue(newestHistory.get("id").isTextual(), newestHistory.toString());
        assertTrue(oldestHistory.get("id").isTextual(), oldestHistory.toString());
        assertEquals(dataId, newestHistory.get("dataId").asText(), newestHistory.toString());
        assertEquals(groupName, newestHistory.get("groupName").asText(), newestHistory.toString());
        assertTrue(newestHistory.get("opType").asText().trim().startsWith("U"), newestHistory.toString());
        assertTrue(oldestHistory.get("opType").asText().trim().startsWith("I"), oldestHistory.toString());
        
        Query detailQuery = configQuery(dataId, groupName, "")
                .addParam("nid", newestHistory.get("id").asText());
        JsonNode historyDetail = getJsonOk(ADMIN_HISTORY_PATH, detailQuery).get("data");
        assertEquals(firstContent, historyDetail.get("content").asText(), historyDetail.toString());
        assertEquals(md5(firstContent), historyDetail.get("md5").asText(), historyDetail.toString());
        assertEquals("formal", historyDetail.get("publishType").asText(), historyDetail.toString());
        
        Query previousQuery = configQuery(dataId, groupName, "")
                .addParam("id", currentConfig.get("id").asText());
        JsonNode previous = getJsonOk(ADMIN_HISTORY_PATH + "/previous", previousQuery).get("data");
        assertEquals(firstContent, previous.get("content").asText(), previous.toString());
        assertEquals(newestHistory.get("id").asText(), previous.get("id").asText(), previous.toString());
        
        JsonNode namespaceConfigs = getJsonOk(ADMIN_HISTORY_CONFIGS_PATH,
                Query.newInstance().addParam("namespaceId", DEFAULT_NAMESPACE)).get("data");
        JsonNode namespaceConfig = assertArrayContainsConfig(namespaceConfigs, dataId, groupName);
        assertTrue(namespaceConfig.get("id").isTextual(), namespaceConfig.toString());
        assertEquals(DEFAULT_TYPE, namespaceConfig.get("type").asText(), namespaceConfig.toString());
        
        assertError(getRaw(ADMIN_HISTORY_PATH, configQuery("wrong-" + dataId, groupName, "")
                .addParam("nid", newestHistory.get("id").asText())), 403,
                ErrorCode.ACCESS_DENIED, "dataId");
    }
    
    @Test
    public void testHistoryValidationAndAbsentHistoryReturnControlledErrors() throws Exception {
        String dataId = randomDataId("history-validation");
        String groupName = randomGroupName("history-validation");
        assertError(getRaw(ADMIN_HISTORY_LIST_PATH, Query.newInstance().addParam("dataId", dataId)
                .addParam("pageNo", "1").addParam("pageSize", "10")), 400,
                ErrorCode.PARAMETER_MISSING, "groupName");
        assertError(getRaw(ADMIN_HISTORY_LIST_PATH, historyQuery(dataId, groupName, "", 0, 10)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
        assertError(getRaw(ADMIN_HISTORY_LIST_PATH, historyQuery(dataId, groupName, "", 1, 0)), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "pageSize");
        
        assertError(getRaw(ADMIN_HISTORY_PATH, configQuery(dataId, groupName, "")), 400,
                ErrorCode.PARAMETER_MISSING, "nid");
        assertError(getRaw(ADMIN_HISTORY_PATH, configQuery(dataId, groupName, "")
                .addParam("nid", "999999999999")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "Source must not be null");
        
        assertError(getRaw(ADMIN_HISTORY_CONFIGS_PATH), 400, ErrorCode.PARAMETER_MISSING,
                "namespaceId");
        assertError(getRaw(ADMIN_HISTORY_CONFIGS_PATH,
                Query.newInstance().addParam("namespaceId", "invalid namespace")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "namespaceId");
    }
}
