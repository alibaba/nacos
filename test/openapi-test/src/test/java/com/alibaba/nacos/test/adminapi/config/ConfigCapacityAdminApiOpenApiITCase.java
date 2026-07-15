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
 * Integration tests for config capacity admin OpenAPI {@code /nacos/v3/admin/cs/capacity}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: updating group capacity persists quota and size limits that can be queried through the
 *     capacity detail API.</li>
 *     <li>Boundary/validation: at least one of {@code groupName}/{@code namespaceId} is required, and at least one
 *     capacity value is required for update. The success case uses an isolated random group; there is no public delete
 *     endpoint for capacity rows.</li>
 *     <li>Exception/error handling: missing identity or capacity values return HTTP 400 with the v3 {@code Result}
 *     error envelope instead of HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigCapacityAdminApiOpenApiITCase extends ConfigAdminApiBaseITCase {
    
    @Test
    public void testUpdateAndQueryGroupCapacity() throws Exception {
        String groupName = randomGroupName("capacity");
        JsonNode updated = postFormOk(ADMIN_CAPACITY_PATH, Query.newInstance().addParam("groupName", groupName)
                .addParam("quota", "101").addParam("maxSize", "1024")
                .addParam("maxAggrCount", "8").addParam("maxAggrSize", "512"));
        assertTrue(updated.get("data").asBoolean(), updated.toString());
        
        JsonNode capacity = getJsonOk(ADMIN_CAPACITY_PATH,
                Query.newInstance().addParam("groupName", groupName)).get("data");
        assertEquals(101, capacity.get("quota").asInt(), capacity.toString());
        assertEquals(1024, capacity.get("maxSize").asInt(), capacity.toString());
        assertEquals(8, capacity.get("maxAggrCount").asInt(), capacity.toString());
        assertEquals(512, capacity.get("maxAggrSize").asInt(), capacity.toString());
    }
    
    @Test
    public void testCapacityValidationReturnsBadRequest() throws Exception {
        assertError(getRaw(ADMIN_CAPACITY_PATH), 400, ErrorCode.PARAMETER_MISSING,
                "At least one of the parameters");
        assertError(postRaw(ADMIN_CAPACITY_PATH, Query.newInstance()), 400,
                ErrorCode.PARAMETER_MISSING, "At least one of the parameters");
        assertError(postRaw(ADMIN_CAPACITY_PATH, Query.newInstance().addParam("groupName",
                randomGroupName("capacity-required"))), 400,
                ErrorCode.PARAMETER_MISSING, "quota, maxSize, maxAggrCount, maxAggrSize");
    }
}
