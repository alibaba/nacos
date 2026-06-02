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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for config admin batch delete OpenAPI {@code DELETE /nacos/v3/admin/cs/config/batch}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: comma-separated config ids delete multiple existing configs and each deleted config
 *     becomes absent from the detail API.</li>
 *     <li>Boundary/validation: non-existing ids are accepted and ignored, while {@code ids} is required.</li>
 *     <li>Exception/error handling: missing {@code ids} returns HTTP 400 with the v3 {@code Result} error envelope
 *     instead of HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigBatchDeleteAdminApiOpenApiITCase extends ConfigAdminApiBaseITCase {
    
    @Test
    public void testBatchDeleteExistingConfigsAndIgnoreMissingId() throws Exception {
        String firstDataId = randomDataId("batch-delete-a");
        String secondDataId = randomDataId("batch-delete-b");
        String groupName = randomGroupName("batch-delete");
        publishConfig(firstDataId, groupName, "", "batch-delete-first");
        addCleanup(() -> deleteConfigQuietly(firstDataId, groupName, ""));
        publishConfig(secondDataId, groupName, "", "batch-delete-second");
        addCleanup(() -> deleteConfigQuietly(secondDataId, groupName, ""));
        
        JsonNode first = queryConfig(firstDataId, groupName, "").get("data");
        JsonNode second = queryConfig(secondDataId, groupName, "").get("data");
        String ids = first.get("id").asText() + "," + second.get("id").asText() + ",999999999999";
        
        JsonNode root = deleteJsonOk(ADMIN_CONFIG_BATCH_PATH, Query.newInstance().addParam("ids", ids));
        assertTrue(root.get("data").asBoolean(), root.toString());
        assertError(getRaw(ADMIN_CONFIG_PATH, configQuery(firstDataId, groupName, "")), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Config not exist");
        assertError(getRaw(ADMIN_CONFIG_PATH, configQuery(secondDataId, groupName, "")), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "Config not exist");
    }
    
    @Test
    public void testBatchDeleteIdsValidationReturnsBadRequest() throws Exception {
        assertError(deleteRaw(ADMIN_CONFIG_BATCH_PATH, Query.newInstance()), 400,
                ErrorCode.PARAMETER_MISSING, "ids");
    }
}
