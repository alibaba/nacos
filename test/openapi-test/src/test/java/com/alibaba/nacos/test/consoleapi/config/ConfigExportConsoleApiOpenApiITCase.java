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
import com.alibaba.nacos.config.server.constant.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for console config export OpenAPI {@code GET /nacos/v3/console/cs/config/export2}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: export by config id returns a downloadable zip containing the config content entry and
 *     metadata yaml entry.</li>
 *     <li>Boundary/validation: exported config ids are serialized as query parameters, omitted namespace uses public,
 *     and invalid namespace values are rejected when ids are present.</li>
 *     <li>Exception/error handling: namespace validation returns HTTP 400 with a v3 {@code Result} body instead of
 *     HTTP 500. Export without {@code ids} is not asserted because the current endpoint dereferences a null id list
 *     before validation.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigExportConsoleApiOpenApiITCase extends ConfigConsoleApiBaseITCase {

    @Test
    public void testExportConfigByIdReturnsZipWithContentAndMetadata() throws Exception {
        String dataId = randomDataId("export");
        String groupName = randomGroupName("export");
        String content = "console-export-content";
        publishConfig(dataId, groupName, "", content, ConfigType.TEXT.getType(), "console export desc", "");
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, ""));
        JsonNode config = queryConfig(dataId, groupName, "").get("data");

        ByteResponse response = getRawBytes(CONSOLE_CONFIG_EXPORT_PATH,
                Query.newInstance().addParam("ids", config.get("id").asText()));
        assertEquals(200, response.code(), response.contentDisposition());
        assertTrue(response.contentDisposition().startsWith("attachment;filename=nacos_config_export_"),
                response.contentDisposition());

        Map<String, String> entries = unzip(response.body());
        String configEntry = groupName + Constants.CONFIG_EXPORT_ITEM_FILE_SEPARATOR + dataId;
        assertEquals(content, entries.get(configEntry), entries.toString());
        String metadata = entries.get(Constants.CONFIG_EXPORT_METADATA_NEW);
        assertTrue(metadata.contains("dataId: " + dataId), metadata);
        assertTrue(metadata.contains("group: " + groupName), metadata);
        assertTrue(metadata.contains("type: text"), metadata);
    }

    @Test
    public void testExportInvalidNamespaceReturnsBadRequest() throws Exception {
        assertError(getRaw(CONSOLE_CONFIG_EXPORT_PATH, Query.newInstance().addParam("ids", "1")
                .addParam("namespaceId", "invalid namespace")), 400,
                ErrorCode.PARAMETER_VALIDATE_ERROR, "namespaceId");
    }

    private Map<String, String> unzip(byte[] body) throws Exception {
        Map<String, String> result = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(body))) {
            ZipEntry entry;
            while (null != (entry = zipInputStream.getNextEntry())) {
                result.put(entry.getName(), new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return result;
    }
}
