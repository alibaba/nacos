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

import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.test.openapi.OpenApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared helpers for naming admin OpenAPI integration tests.
 *
 * @author xiweng.yy
 */
public abstract class NamingAdminApiBaseITCase extends OpenApiBaseITCase {
    
    protected static final String ADMIN_SERVICE_PATH = nacosPath(UtilsAndCommons.SERVICE_CONTROLLER_V3_ADMIN_PATH);
    
    protected static final String ADMIN_INSTANCE_PATH = nacosPath(UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH);
    
    protected static final String ADMIN_CLUSTER_PATH = nacosPath(UtilsAndCommons.CLUSTER_CONTROLLER_V3_ADMIN_PATH);
    
    protected static final String DEFAULT_NAMESPACE = "public";
    
    protected static final String DEFAULT_GROUP = "DEFAULT_GROUP";
    
    protected static final String DEFAULT_CLUSTER = "DEFAULT";
    
    protected String randomServiceName(String scenario) {
        return "openapi_it_admin_" + scenario + "_" + UUID.randomUUID();
    }
    
    protected String randomGroupName(String scenario) {
        return "openapi_it_group_" + scenario + "_" + UUID.randomUUID();
    }
    
    protected Query serviceQuery(String serviceName, String groupName, String namespaceId) {
        Query query = Query.newInstance();
        addIfNotBlank(query, "serviceName", serviceName);
        addIfNotBlank(query, "groupName", groupName);
        addIfNotBlank(query, "namespaceId", namespaceId);
        return query;
    }
    
    protected Query serviceListQuery(String serviceName, String groupName, String namespaceId,
            int pageNo, int pageSize) {
        Query query = Query.newInstance();
        addIfNotBlank(query, "serviceNameParam", serviceName);
        addIfNotBlank(query, "groupNameParam", groupName);
        addIfNotBlank(query, "namespaceId", namespaceId);
        query.addParam("pageNo", String.valueOf(pageNo));
        query.addParam("pageSize", String.valueOf(pageSize));
        return query;
    }
    
    protected JsonNode createService(String serviceName, String groupName, String namespaceId,
            String metadata, String protectThreshold) throws Exception {
        JsonNode root = postFormOk(ADMIN_SERVICE_PATH, serviceQuery(serviceName, groupName, namespaceId)
                .addParam("metadata", metadata).addParam("protectThreshold", protectThreshold)
                .addParam("ephemeral", "false"));
        assertEquals("ok", root.get("data").asText(), root.toString());
        return root;
    }
    
    protected JsonNode updateService(String serviceName, String groupName, String namespaceId,
            String metadata, String protectThreshold) throws Exception {
        JsonNode root = putFormOk(ADMIN_SERVICE_PATH, serviceQuery(serviceName, groupName, namespaceId)
                .addParam("metadata", metadata).addParam("protectThreshold", protectThreshold));
        assertEquals("ok", root.get("data").asText(), root.toString());
        return root;
    }
    
    protected void deleteServiceQuietly(String serviceName, String groupName, String namespaceId) throws Exception {
        deleteQuietly(ADMIN_SERVICE_PATH, serviceQuery(serviceName, groupName, namespaceId));
    }
    
    protected void assertServiceDetail(JsonNode data, String serviceName, String groupName, String namespaceId,
            String metadataKey, String metadataValue) {
        assertEquals(serviceName, data.get("serviceName").asText(), data.toString());
        assertEquals(groupName, data.get("groupName").asText(), data.toString());
        assertEquals(namespaceId, data.get("namespaceId").asText(), data.toString());
        assertEquals(metadataValue, data.get("metadata").get(metadataKey).asText(), data.toString());
        assertFalse(data.get("ephemeral").asBoolean(), data.toString());
    }
    
    protected JsonNode findService(JsonNode page, String serviceName, String groupName) {
        for (JsonNode item : page.get("pageItems")) {
            if (serviceName.equals(item.get("name").asText())
                    && groupName.equals(item.get("groupName").asText())) {
                return item;
            }
        }
        return MissingNode.getInstance();
    }
    
    protected void assertServiceListed(JsonNode page, String serviceName, String groupName) {
        JsonNode service = findService(page, serviceName, groupName);
        assertFalse(service.isMissingNode(), page.toString());
        assertTrue(service.get("clusterCount").asInt() >= 0, service.toString());
    }
}
