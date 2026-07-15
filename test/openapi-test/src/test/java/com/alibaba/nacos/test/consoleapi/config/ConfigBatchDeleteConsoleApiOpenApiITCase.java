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

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for console config batch delete OpenAPI
 * {@code DELETE /nacos/v3/console/cs/config/batchDelete}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: config ids delete multiple existing configs and each deleted config becomes absent from
 *     the detail API.</li>
 *     <li>Boundary/validation: non-existing ids are accepted and ignored, ids outside the requested namespace are
 *     skipped, while {@code ids} is required.</li>
 *     <li>Exception/error handling: missing {@code ids} returns HTTP 400 with the v3 {@code Result} envelope instead
 *     of HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigBatchDeleteConsoleApiOpenApiITCase extends ConfigConsoleApiBaseITCase {

    @Test
    public void testBatchDeleteExistingConfigsAndIgnoreMissingId() throws Exception {
        String firstDataId = randomDataId("batch-delete-a");
        String secondDataId = randomDataId("batch-delete-b");
        String groupName = randomGroupName("batch-delete");
        publishConfig(firstDataId, groupName, "", "console-batch-delete-first");
        addCleanup(() -> deleteConfigQuietly(firstDataId, groupName, ""));
        publishConfig(secondDataId, groupName, "", "console-batch-delete-second");
        addCleanup(() -> deleteConfigQuietly(secondDataId, groupName, ""));

        JsonNode first = queryConfig(firstDataId, groupName, "").get("data");
        JsonNode second = queryConfig(secondDataId, groupName, "").get("data");
        String ids = first.get("id").asText() + "," + second.get("id").asText() + ",999999999999";

        JsonNode root = deleteJsonOk(CONSOLE_CONFIG_BATCH_DELETE_PATH, Query.newInstance().addParam("ids", ids));
        assertTrue(root.get("data").asBoolean(), root.toString());
        assertSuccessDataNull(getRaw(CONSOLE_CONFIG_PATH, configQuery(firstDataId, groupName, "")));
        assertSuccessDataNull(getRaw(CONSOLE_CONFIG_PATH, configQuery(secondDataId, groupName, "")));
    }

    @Test
    public void testBatchDeleteSkipsIdsOutsideNamespace() throws Exception {
        String namespaceId = randomNamespaceId("batch-delete-isolation");
        createNamespace(namespaceId);
        addCleanup(() -> deleteNamespaceQuietly(namespaceId));
        String dataId = randomDataId("batch-delete-isolation");
        String groupName = randomGroupName("batch-delete-isolation");
        String content = "console-batch-delete-isolation-content";
        publishConfig(dataId, groupName, namespaceId, content);
        addCleanup(() -> deleteConfigQuietly(dataId, groupName, namespaceId));
        JsonNode config = queryConfig(dataId, groupName, namespaceId).get("data");

        JsonNode root = deleteJsonOk(CONSOLE_CONFIG_BATCH_DELETE_PATH, Query.newInstance()
                .addParam("ids", config.get("id").asText()).addParam("namespaceId", DEFAULT_NAMESPACE));

        assertTrue(root.get("data").asBoolean(), root.toString());
        JsonNode remain = queryConfig(dataId, groupName, namespaceId).get("data");
        assertConfigDetail(remain, dataId, groupName, namespaceId, content, DEFAULT_TYPE);
    }

    @Test
    public void testBatchDeleteIdsValidationReturnsBadRequest() throws Exception {
        assertError(deleteRaw(CONSOLE_CONFIG_BATCH_DELETE_PATH, Query.newInstance()), 400,
                ErrorCode.PARAMETER_MISSING, "ids");
    }
}
