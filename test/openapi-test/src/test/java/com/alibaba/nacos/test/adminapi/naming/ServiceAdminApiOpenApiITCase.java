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

package com.alibaba.nacos.test.adminapi.naming;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for naming service admin OpenAPI {@code /nacos/v3/admin/ns/service}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: create persists a service, detail returns metadata/default namespace/group/ephemeral
 *     fields, update changes service metadata, list can find the service by {@code serviceNameParam}/{@code
 *     groupNameParam}, selector-types exposes the built-in {@code none} selector, subscribers returns the
 *     expected empty page shape for a service without subscribers, and delete removes the service from the list.</li>
 *     <li>Boundary/validation: omitted namespace and group default to public and DEFAULT_GROUP; service list requires
 *     positive pagination values; {@code serviceName} is required for create/detail/update/delete/subscribers.</li>
 *     <li>Exception/error handling: missing required fields and invalid pagination return HTTP 400 with the v3
 *     {@code Result} error envelope instead of HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ServiceAdminApiOpenApiITCase extends NamingAdminApiBaseITCase {
    
    @Test
    public void testCreateDetailUpdateListAndDeleteService() throws Exception {
        String serviceName = randomServiceName("service");
        createService(serviceName, "", null, "{\"env\":\"it\"}", "0.4");
        addCleanup(() -> deleteServiceQuietly(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE));

        JsonNode selectorTypes = getJsonOk(ADMIN_SERVICE_SELECTOR_TYPES_PATH, Query.newInstance())
                .get("data");
        assertTrue(selectorTypes.isArray(), selectorTypes.toString());
        assertArrayContainsText(selectorTypes, "none");
        
        JsonNode created = getJsonOk(ADMIN_SERVICE_PATH,
                serviceQuery(serviceName, null, null)).get("data");
        assertServiceDetail(created, serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, "env", "it");
        assertEquals(0.4F, created.get("protectThreshold").floatValue(), 0.001F, created.toString());
        
        updateService(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, "{\"env\":\"updated\"}", "0.6");
        JsonNode updated = getJsonOk(ADMIN_SERVICE_PATH,
                serviceQuery(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE)).get("data");
        assertServiceDetail(updated, serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, "env", "updated");
        assertEquals(0.6F, updated.get("protectThreshold").floatValue(), 0.001F, updated.toString());
        
        JsonNode subscribers = getJsonOk(ADMIN_SERVICE_SUBSCRIBERS_PATH,
                subscribersQuery(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, 1, 10, "false"))
                .get("data");
        assertNamingPageShape(subscribers);
        assertEquals(1, subscribers.get("pageNumber").asInt(), subscribers.toString());
        assertEquals(0, subscribers.get("totalCount").asInt(), subscribers.toString());

        JsonNode listed = getJsonOk(ADMIN_SERVICE_LIST_PATH,
                serviceListQuery(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, 1, 10)).get("data");
        assertEquals(1, listed.get("pageNumber").asInt(), listed.toString());
        assertServiceListed(listed, serviceName, DEFAULT_GROUP);
        
        JsonNode deleted = deleteJsonOk(ADMIN_SERVICE_PATH,
                serviceQuery(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE));
        assertEquals("ok", deleted.get("data").asText(), deleted.toString());
        JsonNode afterDelete = getJsonOk(ADMIN_SERVICE_LIST_PATH,
                serviceListQuery(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, 1, 10)).get("data");
        assertEquals(0, afterDelete.get("totalCount").asInt(), afterDelete.toString());
    }
    
    @Test
    public void testServiceValidationReturnsBadRequest() throws Exception {
        assertError(postRaw(ADMIN_SERVICE_PATH, Query.newInstance()), 400,
                ErrorCode.PARAMETER_MISSING, "serviceName");
        assertError(getRaw(ADMIN_SERVICE_PATH, Query.newInstance()), 400,
                ErrorCode.PARAMETER_MISSING, "serviceName");
        assertError(putRaw(ADMIN_SERVICE_PATH, Query.newInstance()), 400,
                ErrorCode.PARAMETER_MISSING, "serviceName");
        assertError(deleteRaw(ADMIN_SERVICE_PATH, Query.newInstance()), 400,
                ErrorCode.PARAMETER_MISSING, "serviceName");
        assertError(getRaw(ADMIN_SERVICE_LIST_PATH,
                serviceListQuery(randomServiceName("service-page"), DEFAULT_GROUP, DEFAULT_NAMESPACE, 0, 10)),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
        assertError(getRaw(ADMIN_SERVICE_SUBSCRIBERS_PATH,
                subscribersQuery(randomServiceName("subscriber-page"), DEFAULT_GROUP, DEFAULT_NAMESPACE, 0, 10,
                        "false")), 400, ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
        assertError(getRaw(ADMIN_SERVICE_SUBSCRIBERS_PATH,
                Query.newInstance().addParam("pageNo", "1").addParam("pageSize", "10")),
                400, ErrorCode.PARAMETER_MISSING, "serviceName");
    }

    private void assertArrayContainsText(JsonNode array, String expected) {
        for (JsonNode item : array) {
            if (expected.equals(item.asText())) {
                return;
            }
        }
        throw new AssertionError("Expected array to contain " + expected + ", actual=" + array);
    }
}
