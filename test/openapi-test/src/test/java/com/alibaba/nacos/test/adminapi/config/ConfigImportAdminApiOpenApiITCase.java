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
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for config import admin OpenAPI {@code POST /nacos/v3/admin/cs/config/import}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: a valid metadata zip imports and publishes a config that can be queried with content,
 *     type, and description.</li>
 *     <li>Boundary/validation: omitted namespace uses public, {@code ABORT} policy is accepted, missing file returns
 *     {@code DATA_EMPTY}, and malformed metadata returns {@code METADATA_ILLEGAL} with an object data field.</li>
 *     <li>Exception/error handling: business failures are returned in the v3 {@code Result} envelope instead of HTTP
 *     500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigImportAdminApiOpenApiITCase extends ConfigAdminApiBaseITCase {
    
    @Test
    public void testImportConfigZipPublishesConfig() throws Exception {
        String dataId = randomDataId("import");
        String groupName = randomGroupName("import");
        String content = "import-content";
        String description = "import desc";
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, ""));
        
        HttpResponse response = postMultipartRaw(ADMIN_CONFIG_IMPORT_PATH,
                Query.newInstance().addParam("policy", "ABORT"), "file", "nacos-import.zip",
                "application/zip", buildImportZip(dataId, groupName, content, description));
        assertEquals(200, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertSuccess(root);
        assertEquals(1, root.get("data").get("succCount").asInt(), root.toString());
        
        JsonNode imported = queryConfig(dataId, groupName, "").get("data");
        assertConfigDetail(imported, dataId, groupName, DEFAULT_NAMESPACE, content, ConfigType.TEXT.getType());
        assertEquals(description, imported.get("desc").asText(), imported.toString());
    }
    
    @Test
    public void testImportMissingFileAndBadMetadataReturnFailureResult() throws Exception {
        HttpResponse missingFile = postRaw(ADMIN_CONFIG_IMPORT_PATH, Query.newInstance().addParam("policy", "ABORT"));
        assertEquals(200, missingFile.code(), missingFile.body());
        assertFailureResult(JacksonUtils.toObj(missingFile.body()), ErrorCode.DATA_EMPTY);
        
        HttpResponse badMetadata = postMultipartRaw(ADMIN_CONFIG_IMPORT_PATH,
                Query.newInstance().addParam("policy", "ABORT"), "file", "bad-import.zip",
                "application/zip", buildBadMetadataZip());
        assertEquals(200, badMetadata.code(), badMetadata.body());
        assertFailureResult(JacksonUtils.toObj(badMetadata.body()), ErrorCode.METADATA_ILLEGAL);
    }
    
    private void assertFailureResult(JsonNode root, ErrorCode errorCode) {
        assertEquals(errorCode.getCode(), root.get("code").asInt(), root.toString());
        assertEquals(errorCode.getMsg(), root.get("message").asText(), root.toString());
        assertTrue(root.get("data").isObject(), root.toString());
    }
    
    private byte[] buildImportZip(String dataId, String groupName, String content, String description)
            throws Exception {
        String metadata = "metadata:\n"
                + "- dataId: " + dataId + "\n"
                + "  group: " + groupName + "\n"
                + "  type: text\n"
                + "  appName: ''\n"
                + "  desc: " + description + "\n";
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            writeEntry(zipOutputStream, Constants.CONFIG_EXPORT_METADATA_NEW, metadata);
            writeEntry(zipOutputStream, groupName + Constants.CONFIG_EXPORT_ITEM_FILE_SEPARATOR + dataId, content);
        }
        return outputStream.toByteArray();
    }
    
    private byte[] buildBadMetadataZip() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            writeEntry(zipOutputStream, Constants.CONFIG_EXPORT_METADATA_NEW, "metadata: []\n");
        }
        return outputStream.toByteArray();
    }
    
    private void writeEntry(ZipOutputStream zipOutputStream, String name, String content) throws Exception {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }
}
