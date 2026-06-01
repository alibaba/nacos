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
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for config admin OpenAPI {@code /nacos/v3/admin/cs/config}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: publish creates a config, republish updates its content, query returns the admin detail
 *     model, metadata update changes description and config tags, and delete removes the config.</li>
 *     <li>Boundary/validation: omitted namespaceId is stored as public, invalid config type is normalized to text,
 *     {@code dataId}, {@code groupName}, and {@code content} are required, and the legacy {@code group} parameter does
 *     not replace {@code groupName} for the v3 API.</li>
 *     <li>Exception/error handling: absent configs return HTTP 404 with the v3 {@code Result} error envelope, and
 *     invalid namespace values return HTTP 400 instead of leaking HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigAdminApiOpenApiITCase extends ConfigAdminApiBaseITCase {
    
    @Test
    public void testPublishQueryUpdateMetadataAndDeleteConfig() throws Exception {
        String dataId = randomDataId("crud");
        String groupName = randomGroupName("crud");
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
