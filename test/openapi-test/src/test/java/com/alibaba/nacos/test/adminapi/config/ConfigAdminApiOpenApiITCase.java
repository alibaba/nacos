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

import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for config admin OpenAPI {@code /nacos/v3/admin/cs/config}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: inherited optional source user and app name bind and persist; publish creates a config, republish updates its content, query returns the admin detail
 *     model, metadata update changes description and config tags, and delete removes the config.</li>
 *     <li>Boundary/validation: omitted namespaceId is stored as public, invalid config type is normalized to text,
 *     {@code dataId}, {@code groupName}, and {@code content} are required, embedded dots remain valid in Config names,
 *     directory control segments are rejected, and the legacy {@code group} parameter does not replace
 *     {@code groupName} for the v3 API.</li>
 *     <li>Publish modes: create-only inserts absent configs and rejects duplicates with native HTTP/detail 410,
 *     omitted {@code updateForExist} still updates, and the {@code casMd5} header takes precedence over
 *     create-only, including stale CAS rejection without overwriting content.</li>
 *     <li>Exception/error handling: absent configs return HTTP 404 with the v3 {@code Result} error envelope,
 *     invalid namespace values return HTTP 400 instead of leaking HTTP 500, and invalid
 *     {@code updateForExist} booleans return controlled HTTP 400 without inserting or updating config.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigAdminApiOpenApiITCase extends ConfigAdminApiBaseITCase {

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
        JsonNode result = postFormOk(ADMIN_CONFIG_PATH, form);
        assertTrue(result.get("data").asBoolean(), result.toString());

        JsonNode detail = queryConfig(dataId, groupName, DEFAULT_NAMESPACE).get("data");
        assertConfigDetail(detail, dataId, groupName, DEFAULT_NAMESPACE, content,
                ConfigType.PROPERTIES.getType());
        assertEquals("form-source-user", detail.get("createUser").asText(), detail.toString());
        assertEquals("form-binding-app", detail.get("appName").asText(), detail.toString());
        assertConfigMetadata(detail, "binding description", "binding-tag");
    }

    
    @Test
    public void testPublishQueryUpdateMetadataAndDeleteConfig() throws Exception {
        String dataId = randomDataId("crud") + ".properties";
        String groupName = randomGroupName("crud") + ".v1";
        String initialContent = "{\"name\":\"initial\"}";
        String initialDescription = "initial config description";
        String initialTags = "tag-a,tag-b";
        publishConfig(dataId, groupName, "", initialContent, ConfigType.JSON.getType(), initialDescription,
                initialTags);
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, ""));
        
        JsonNode initialData = queryConfig(dataId, groupName, null).get("data");
        assertConfigDetail(initialData, dataId, groupName, DEFAULT_NAMESPACE, initialContent,
                ConfigType.JSON.getType());
        assertConfigMetadata(initialData, initialDescription, initialTags);
        
        String updatedContent = "plain-content-" + dataId;
        publishConfig(dataId, groupName, "", updatedContent, "not-a-real-type", "", "");
        JsonNode updatedData = queryConfig(dataId, groupName, "").get("data");
        assertConfigDetail(updatedData, dataId, groupName, DEFAULT_NAMESPACE, updatedContent, DEFAULT_TYPE);
        
        String metadataDescription = "metadata-updated-description";
        String metadataTags = "tag-updated-a,tag-updated-b";
        updateConfigMetadata(dataId, groupName, "", metadataDescription, metadataTags);
        JsonNode metadataData = queryConfig(dataId, groupName, "").get("data");
        assertConfigDetail(metadataData, dataId, groupName, DEFAULT_NAMESPACE, updatedContent, DEFAULT_TYPE);
        assertConfigMetadata(metadataData, metadataDescription, metadataTags);
        
        deleteConfig(dataId, groupName, "");
        assertError(getRaw(ADMIN_CONFIG_PATH, configQuery(dataId, groupName, "")), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Config not exist");
    }
    
    @Test
    public void testCreateOnlyPublishCreatesAbsentConfigAndRejectsDuplicate() throws Exception {
        String dataId = randomDataId("create-only");
        String groupName = randomGroupName("create-only");
        String initialContent = "create-only=initial";
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, DEFAULT_NAMESPACE));
        assertError(getRaw(ADMIN_CONFIG_PATH, configQuery(dataId, groupName, DEFAULT_NAMESPACE)),
                404, ErrorCode.RESOURCE_NOT_FOUND, "Config not exist");
        Map<String, String> form = new LinkedHashMap<>(Map.of("dataId", dataId,
                "groupName", groupName, "namespaceId", DEFAULT_NAMESPACE,
                "content", initialContent, "type", ConfigType.PROPERTIES.getType(),
                "desc", "original description", "configTags", "original-tag",
                "updateForExist", "false"));
        JsonNode result = postFormOk(ADMIN_CONFIG_PATH, form);
        assertTrue(result.get("data").asBoolean(), result.toString());
        JsonNode initialData = queryConfig(dataId, groupName, DEFAULT_NAMESPACE).get("data");
        assertConfigDetail(initialData, dataId, groupName, DEFAULT_NAMESPACE, initialContent,
                ConfigType.PROPERTIES.getType());
        assertConfigMetadata(initialData, "original description", "original-tag");

        form.put("content", "{\"rejected\":true}");
        form.put("type", ConfigType.JSON.getType());
        form.put("desc", "rejected description");
        form.put("configTags", "rejected-tag");
        HttpResponse response = postConfigFormRaw(Header.EMPTY, form);
        assertEquals(NacosException.CONFIG_ALREADY_EXISTS, response.code(), response.body());
        JsonNode error = JacksonUtils.toObj(response.body());
        assertEquals(NacosException.CONFIG_ALREADY_EXISTS, error.get("code").asInt(),
                response.body());
        assertEquals("Config already exists", error.get("message").asText(), response.body());
        assertTrue(error.get("data").asText().contains("config already exist"), response.body());
        JsonNode afterDuplicate = queryConfig(dataId, groupName, DEFAULT_NAMESPACE).get("data");
        assertEquals(initialData, afterDuplicate, afterDuplicate.toString());
    }

    @Test
    public void testPublishConfigOmittedUpdateForExistStillUpdates() throws Exception {
        String dataId = randomDataId("default-update");
        String groupName = randomGroupName("default-update");
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, DEFAULT_NAMESPACE));
        publishConfig(dataId, groupName, DEFAULT_NAMESPACE, "default-update=initial");
        String updatedContent = "default-update=updated";
        Map<String, String> form = Map.of("dataId", dataId, "groupName", groupName,
                "namespaceId", DEFAULT_NAMESPACE, "content", updatedContent);
        JsonNode result = postFormOk(ADMIN_CONFIG_PATH, form);
        assertTrue(result.get("data").asBoolean(), result.toString());
        JsonNode updatedData = queryConfig(dataId, groupName, DEFAULT_NAMESPACE).get("data");
        assertConfigDetail(updatedData, dataId, groupName, DEFAULT_NAMESPACE, updatedContent,
                DEFAULT_TYPE);
    }

    @Test
    public void testPublishConfigCasHeaderTakesPrecedenceOverCreateOnly() throws Exception {
        String dataId = randomDataId("cas-create-only");
        String groupName = randomGroupName("cas-create-only");
        String initialContent = "cas-create-only=initial";
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, DEFAULT_NAMESPACE));
        publishConfig(dataId, groupName, DEFAULT_NAMESPACE, initialContent);
        JsonNode initialData = queryConfig(dataId, groupName, DEFAULT_NAMESPACE).get("data");
        assertConfigDetail(initialData, dataId, groupName, DEFAULT_NAMESPACE, initialContent,
                DEFAULT_TYPE);
        Header header = Header.newInstance().addParam("casMd5", initialData.get("md5").asText());
        String updatedContent = "cas-create-only=updated";
        Map<String, String> form = new LinkedHashMap<>(Map.of("dataId", dataId,
                "groupName", groupName, "namespaceId", DEFAULT_NAMESPACE,
                "content", updatedContent, "updateForExist", "false"));
        JsonNode result = postFormOk(ADMIN_CONFIG_PATH, header, form);
        assertTrue(result.get("data").asBoolean(), result.toString());
        JsonNode updatedData = queryConfig(dataId, groupName, DEFAULT_NAMESPACE).get("data");
        assertConfigDetail(updatedData, dataId, groupName, DEFAULT_NAMESPACE, updatedContent,
                DEFAULT_TYPE);

        form.put("content", "cas-create-only=rejected");
        assertError(postConfigFormRaw(header, form), 500, ErrorCode.RESOURCE_CONFLICT,
                "Cas publish");
        JsonNode afterStaleCas = queryConfig(dataId, groupName, DEFAULT_NAMESPACE).get("data");
        assertEquals(updatedData, afterStaleCas, afterStaleCas.toString());
    }

    @Test
    public void testPublishConfigInvalidUpdateForExistReturnsBadRequestWithoutWriting()
            throws Exception {
        String dataId = randomDataId("invalid-update-flag");
        String groupName = randomGroupName("invalid-update-flag");
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, DEFAULT_NAMESPACE));
        Map<String, String> form = Map.of("dataId", dataId, "groupName", groupName,
                "namespaceId", DEFAULT_NAMESPACE, "content", "invalid-update-flag=rejected",
                "updateForExist", "not-a-boolean");
        assertError(postConfigFormRaw(Header.EMPTY, form), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "updateForExist");
        assertError(getRaw(ADMIN_CONFIG_PATH, configQuery(dataId, groupName, DEFAULT_NAMESPACE)),
                404, ErrorCode.RESOURCE_NOT_FOUND, "Config not exist");

        publishConfig(dataId, groupName, DEFAULT_NAMESPACE, "invalid-update-flag=original");
        JsonNode initialData = queryConfig(dataId, groupName, DEFAULT_NAMESPACE).get("data");
        assertError(postConfigFormRaw(Header.EMPTY, form), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "updateForExist");
        JsonNode afterInvalidFlag = queryConfig(dataId, groupName, DEFAULT_NAMESPACE).get("data");
        assertEquals(initialData, afterInvalidFlag, afterInvalidFlag.toString());
    }

    private HttpResponse postConfigFormRaw(Header header, Map<String, String> form)
            throws Exception {
        Header requestHeaders = requestHeader(requestUrl(ADMIN_CONFIG_PATH));
        requestHeaders.addAll(header.getHeader());
        HttpRestResult<String> result = nacosRestTemplate.postForm(requestUrl(ADMIN_CONFIG_PATH),
                requestHeaders, form, String.class);
        String body = null == result.getData() ? result.getMessage() : result.getData();
        return httpResponse(result.getCode(), body);
    }

    @Test
    public void testPublishConfigRequiredParametersReturnBadRequest() throws Exception {
        String dataId = randomDataId("required");
        String groupName = randomGroupName("required");
        assertError(postRaw(ADMIN_CONFIG_PATH, Query.newInstance().addParam("groupName", groupName)
                .addParam("content", "content")), 400, ErrorCode.PARAMETER_MISSING, "dataId");
        assertError(postRaw(ADMIN_CONFIG_PATH, Query.newInstance().addParam("dataId", dataId)
                .addParam("content", "content")), 400, ErrorCode.PARAMETER_MISSING, "groupName");
        assertError(postRaw(ADMIN_CONFIG_PATH, Query.newInstance().addParam("dataId", dataId)
                .addParam("groupName", groupName)), 400, ErrorCode.PARAMETER_MISSING, "content");
        assertError(postRaw(ADMIN_CONFIG_PATH, Query.newInstance().addParam("dataId", dataId)
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
        assertError(postRaw(ADMIN_CONFIG_PATH, query), 400, ErrorCode.PARAMETER_VALIDATE_ERROR, expectedField);
    }
    
    @Test
    public void testQueryConfigNotFoundAndInvalidNamespaceReturnControlledErrors() throws Exception {
        String absentDataId = randomDataId("absent");
        String groupName = randomGroupName("absent");
        assertError(getRaw(ADMIN_CONFIG_PATH, configQuery(absentDataId, groupName, "")), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Config not exist");
        
        Query invalidNamespace = configQuery(absentDataId, groupName, "invalid namespace");
        assertError(getRaw(ADMIN_CONFIG_PATH, invalidNamespace), 400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "namespaceId");
    }
    
    @Test
    public void testDeleteConfigRequiredParametersReturnBadRequest() throws Exception {
        String dataId = randomDataId("delete-required");
        String groupName = randomGroupName("delete-required");
        assertError(deleteRaw(ADMIN_CONFIG_PATH, Query.newInstance().addParam("groupName", groupName)), 400,
                ErrorCode.PARAMETER_MISSING, "dataId");
        assertError(deleteRaw(ADMIN_CONFIG_PATH, Query.newInstance().addParam("dataId", dataId)), 400,
                ErrorCode.PARAMETER_MISSING, "groupName");
    }
    
    @Test
    public void testMetadataUpdateRequiresExistingConfigIdentityFields() throws Exception {
        String dataId = randomDataId("metadata-required");
        String groupName = randomGroupName("metadata-required");
        assertError(putRaw(ADMIN_CONFIG_METADATA_PATH, Query.newInstance().addParam("groupName", groupName)
                .addParam("desc", "desc").addParam("configTags", "tag")), 400,
                ErrorCode.PARAMETER_MISSING, "dataId");
        assertError(putRaw(ADMIN_CONFIG_METADATA_PATH, Query.newInstance().addParam("dataId", dataId)
                .addParam("desc", "desc").addParam("configTags", "tag")), 400,
                ErrorCode.PARAMETER_MISSING, "groupName");
        
        publishConfig(dataId, groupName, "", "metadata-content");
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, ""));
        updateConfigMetadata(dataId, groupName, "", "", "");
        JsonNode data = queryConfig(dataId, groupName, "").get("data");
        assertConfigMetadata(data, "", "");
    }
}
