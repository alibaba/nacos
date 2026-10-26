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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for console config OpenAPI {@code /nacos/v3/console/cs/config}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: inherited optional source user and app name bind and persist; publish creates a config, republish updates content, query returns the console detail
 *     model, and delete removes the config.</li>
 *     <li>Boundary/validation: omitted namespaceId is stored as public, invalid config type is normalized to text,
 *     {@code dataId}, {@code groupName}, and {@code content} are required, embedded dots remain valid in Config names,
 *     directory control segments are rejected, and legacy {@code group} does not replace {@code groupName} for the v3
 *     console API.</li>
 *     <li>Exception/error handling: absent console config queries return HTTP 200 with success and {@code data=null},
 *     while invalid namespace values return HTTP 400 rather than HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigConsoleApiOpenApiITCase extends ConfigConsoleApiBaseITCase {

    @Test
    public void testPublishConfigWithInheritedOptionalFields() throws Exception {
        String dataId = randomDataId("binding");
        String groupName = randomGroupName("binding");
        String content = "binding=optional-fields";
        Map<String, String> form = Map.of("dataId", dataId, "groupName", groupName,
                "namespaceId", DEFAULT_NAMESPACE, "content", content,
                "type", ConfigType.PROPERTIES.getType(), "desc", "binding description",
                "configTags", "binding-tag", "srcUser", "form-source-user",
                "appName", "form-binding-app");
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, DEFAULT_NAMESPACE));
        JsonNode result = postFormOk(CONSOLE_CONFIG_PATH, form);
        assertTrue(result.get("data").asBoolean(), result.toString());

        JsonNode detail = queryConfig(dataId, groupName, DEFAULT_NAMESPACE).get("data");
        assertConfigDetail(detail, dataId, groupName, DEFAULT_NAMESPACE, content,
                ConfigType.PROPERTIES.getType());
        assertEquals("form-source-user", detail.get("createUser").asText(), detail.toString());
        assertEquals("form-binding-app", detail.get("appName").asText(), detail.toString());
        assertConfigMetadata(detail, "binding description", "binding-tag");
    }


    @Test
    public void testPublishQueryRepublishAndDeleteConfig() throws Exception {
        String dataId = randomDataId("crud") + ".properties";
        String groupName = randomGroupName("crud") + ".v1";
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
    public void testPublishConfigRejectsDirectoryControlIdentitySegments() throws Exception {
        String dataId = randomDataId("path-segment");
        String groupName = randomGroupName("path-segment");
        assertInvalidPublishIdentity(".", groupName, "", "dataId");
        assertInvalidPublishIdentity("..", groupName, "", "dataId");
        assertInvalidPublishIdentity(dataId, ".", "", "group");
        assertInvalidPublishIdentity(dataId, "..", "", "group");
        assertInvalidPublishIdentity(dataId, groupName, ".", "namespaceId");
        assertInvalidPublishIdentity(dataId, groupName, "..", "namespaceId");
    }

    private void assertInvalidPublishIdentity(String dataId, String groupName, String namespaceId,
            String expectedField) throws Exception {
        Query query = Query.newInstance().addParam("dataId", dataId).addParam("groupName", groupName)
                .addParam("namespaceId", namespaceId).addParam("content", "content");
        assertError(postRaw(CONSOLE_CONFIG_PATH, query), 400, ErrorCode.PARAMETER_VALIDATE_ERROR, expectedField);
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
