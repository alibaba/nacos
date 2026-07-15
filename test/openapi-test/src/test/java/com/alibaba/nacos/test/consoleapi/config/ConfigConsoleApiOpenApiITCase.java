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

package com.alibaba.nacos.test.consoleapi.config;

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for console config OpenAPI {@code /nacos/v3/console/cs/config}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: publish creates a config, republish updates content, query returns the console detail
 *     model, and delete removes the config.</li>
 *     <li>Boundary/validation: omitted namespaceId is stored as public, invalid config type is normalized to text,
 *     {@code dataId}, {@code groupName}, and {@code content} are required, and legacy {@code group} does not replace
 *     {@code groupName} for the v3 console API.</li>
 *     <li>Exception/error handling: absent console config queries return HTTP 200 with success and {@code data=null},
 *     while invalid namespace values return HTTP 400 rather than HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigConsoleApiOpenApiITCase extends ConfigConsoleApiBaseITCase {

    @Test
    public void testPublishQueryRepublishAndDeleteConfig() throws Exception {
        String dataId = randomDataId("crud");
        String groupName = randomGroupName("crud");
        String initialContent = "{\"name\":\"initial\"}";
        String description = "console config description";
        String tags = "console-tag-a,console-tag-b";
        publishConfig(dataId, groupName, "", initialContent, ConfigType.JSON.getType(), description, tags);
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, ""));

        JsonNode initialData = queryConfig(dataId, groupName, null).get("data");
        assertConfigDetail(initialData, dataId, groupName, DEFAULT_NAMESPACE, initialContent,
                ConfigType.JSON.getType());
        assertConfigMetadata(initialData, description, tags);

        String updatedContent = "plain-console-content-" + dataId;
        publishConfig(dataId, groupName, "", updatedContent, "not-a-real-type", "", "");
        JsonNode updatedData = queryConfig(dataId, groupName, "").get("data");
        assertConfigDetail(updatedData, dataId, groupName, DEFAULT_NAMESPACE, updatedContent, DEFAULT_TYPE);

        deleteConfig(dataId, groupName, "");
        assertSuccessDataNull(getRaw(CONSOLE_CONFIG_PATH, configQuery(dataId, groupName, "")));
    }

    @Test
    public void testPublishConfigRequiredParametersReturnBadRequest() throws Exception {
        String dataId = randomDataId("required");
        String groupName = randomGroupName("required");
        assertError(postRaw(CONSOLE_CONFIG_PATH, Query.newInstance().addParam("groupName", groupName)
                .addParam("content", "content")), 400, ErrorCode.PARAMETER_MISSING, "dataId");
        assertError(postRaw(CONSOLE_CONFIG_PATH, Query.newInstance().addParam("dataId", dataId)
                .addParam("content", "content")), 400, ErrorCode.PARAMETER_MISSING, "groupName");
        assertError(postRaw(CONSOLE_CONFIG_PATH, Query.newInstance().addParam("dataId", dataId)
                .addParam("groupName", groupName)), 400, ErrorCode.PARAMETER_MISSING, "content");
        assertError(postRaw(CONSOLE_CONFIG_PATH, Query.newInstance().addParam("dataId", dataId)
                .addParam("group", groupName).addParam("content", "content")), 400,
                ErrorCode.PARAMETER_MISSING, "groupName");
    }

    @Test
    public void testQueryDeleteNotFoundAndInvalidNamespaceReturnControlledErrors() throws Exception {
        String dataId = randomDataId("absent");
        String groupName = randomGroupName("absent");
        assertSuccessDataNull(getRaw(CONSOLE_CONFIG_PATH, configQuery(dataId, groupName, "")));
        assertError(getRaw(CONSOLE_CONFIG_PATH, configQuery(dataId, groupName, "invalid namespace")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "namespaceId");
        assertError(deleteRaw(CONSOLE_CONFIG_PATH, Query.newInstance().addParam("groupName", groupName)),
                400, ErrorCode.PARAMETER_MISSING, "dataId");
        assertError(deleteRaw(CONSOLE_CONFIG_PATH, Query.newInstance().addParam("dataId", dataId)),
                400, ErrorCode.PARAMETER_MISSING, "groupName");
    }
}
