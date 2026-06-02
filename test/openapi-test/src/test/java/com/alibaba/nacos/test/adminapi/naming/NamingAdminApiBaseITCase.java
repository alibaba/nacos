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

import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared helpers for naming admin OpenAPI integration tests.
 *
 * @author xiweng.yy
 */
public abstract class NamingAdminApiBaseITCase extends OpenApiBaseITCase {

    protected static final String ADMIN_SERVICE_PATH = nacosPath(UtilsAndCommons.SERVICE_CONTROLLER_V3_ADMIN_PATH);

    protected static final String ADMIN_SERVICE_LIST_PATH = ADMIN_SERVICE_PATH + "/list";

    protected static final String ADMIN_SERVICE_SUBSCRIBERS_PATH = ADMIN_SERVICE_PATH + "/subscribers";

    protected static final String ADMIN_SERVICE_SELECTOR_TYPES_PATH = ADMIN_SERVICE_PATH + "/selector/types";

    protected static final String ADMIN_INSTANCE_PATH = nacosPath(UtilsAndCommons.INSTANCE_CONTROLLER_V3_ADMIN_PATH);

    protected static final String ADMIN_INSTANCE_LIST_PATH = ADMIN_INSTANCE_PATH + "/list";

    protected static final String ADMIN_INSTANCE_PARTIAL_PATH = ADMIN_INSTANCE_PATH + "/partial";

    protected static final String ADMIN_INSTANCE_METADATA_BATCH_PATH = ADMIN_INSTANCE_PATH + "/metadata/batch";

    protected static final String ADMIN_CLUSTER_PATH = nacosPath(UtilsAndCommons.CLUSTER_CONTROLLER_V3_ADMIN_PATH);

    protected static final String ADMIN_HEALTH_PATH = nacosPath(UtilsAndCommons.HEALTH_CONTROLLER_V3_ADMIN_PATH);

    protected static final String ADMIN_HEALTH_INSTANCE_PATH = ADMIN_HEALTH_PATH + "/instance";

    protected static final String ADMIN_CLIENT_PATH = nacosPath(UtilsAndCommons.CLIENT_CONTROLLER_V3_ADMIN_PATH);

    protected static final String ADMIN_OPERATOR_PATH = nacosPath(UtilsAndCommons.OPERATOR_CONTROLLER_V3_ADMIN_PATH);

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

    protected Query subscribersQuery(String serviceName, String groupName, String namespaceId,
            int pageNo, int pageSize, String aggregation) {
        Query query = serviceQuery(serviceName, groupName, namespaceId);
        query.addParam("pageNo", String.valueOf(pageNo));
        query.addParam("pageSize", String.valueOf(pageSize));
        addIfNotBlank(query, "aggregation", aggregation);
        return query;
    }

    protected Query instanceQuery(String serviceName, String groupName, String namespaceId, String ip, int port) {
        return instanceQuery(serviceName, groupName, namespaceId, ip, port, null);
    }

    protected Query instanceQuery(String serviceName, String groupName, String namespaceId, String ip, int port,
            String clusterName) {
        Query query = serviceQuery(serviceName, groupName, namespaceId);
        addIfNotBlank(query, "ip", ip);
        query.addParam("port", String.valueOf(port));
        addIfNotBlank(query, "clusterName", clusterName);
        return query;
    }

    protected Query instanceListQuery(String serviceName, String groupName, String namespaceId, String clusterName,
            String healthyOnly) {
        Query query = serviceQuery(serviceName, groupName, namespaceId);
        addIfNotBlank(query, "clusterName", clusterName);
        addIfNotBlank(query, "healthyOnly", healthyOnly);
        return query;
    }

    protected Query metadataBatchQuery(String serviceName, String groupName, String namespaceId, String metadata,
            String instances, String consistencyType) {
        Query query = serviceQuery(serviceName, groupName, namespaceId);
        addIfNotBlank(query, "metadata", metadata);
        addIfNotBlank(query, "instances", instances);
        addIfNotBlank(query, "consistencyType", consistencyType);
        return query;
    }

    protected Query clusterQuery(String serviceName, String groupName, String namespaceId, String clusterName,
            String checkPort, String useInstancePort4Check, String healthChecker, String metadata) {
        Query query = serviceQuery(serviceName, groupName, namespaceId);
        addIfNotBlank(query, "clusterName", clusterName);
        addIfNotBlank(query, "checkPort", checkPort);
        addIfNotBlank(query, "useInstancePort4Check", useInstancePort4Check);
        addIfNotBlank(query, "healthChecker", healthChecker);
        addIfNotBlank(query, "metadata", metadata);
        return query;
    }

    protected Query healthQuery(String serviceName, String groupName, String namespaceId, String ip, int port,
            String clusterName, String healthy) {
        Query query = instanceQuery(serviceName, groupName, namespaceId, ip, port, clusterName);
        addIfNotBlank(query, "healthy", healthy);
        return query;
    }

    protected Query clientServiceQuery(String serviceName, String groupName, String namespaceId, String ip,
            Integer port) {
        Query query = serviceQuery(serviceName, groupName, namespaceId);
        addIfNotBlank(query, "ip", ip);
        if (null != port) {
            query.addParam("port", String.valueOf(port));
        }
        return query;
    }

    protected Query switchQuery(String entry, String value, String debug) {
        Query query = Query.newInstance();
        addIfNotBlank(query, "entry", entry);
        addIfNotBlank(query, "value", value);
        addIfNotBlank(query, "debug", debug);
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

    protected JsonNode registerInstance(String serviceName, String groupName, String namespaceId, String ip,
            int port, String metadata, String weight) throws Exception {
        return registerInstance(serviceName, groupName, namespaceId, ip, port, DEFAULT_CLUSTER, metadata,
                weight, null, null, "true");
    }

    protected JsonNode registerInstance(String serviceName, String groupName, String namespaceId, String ip,
            int port, String clusterName, String metadata, String weight, String healthy, String enabled,
            String ephemeral) throws Exception {
        Query query = instanceQuery(serviceName, groupName, namespaceId, ip, port, clusterName);
        addIfNotBlank(query, "metadata", metadata);
        addIfNotBlank(query, "weight", weight);
        addIfNotBlank(query, "healthy", healthy);
        addIfNotBlank(query, "enabled", enabled);
        addIfNotBlank(query, "ephemeral", ephemeral);
        JsonNode root = postFormOk(ADMIN_INSTANCE_PATH, query);
        assertEquals("ok", root.get("data").asText(), root.toString());
        return root;
    }

    protected JsonNode updateInstance(String serviceName, String groupName, String namespaceId, String ip,
            int port, String clusterName, String metadata, String weight, String healthy, String enabled,
            String ephemeral) throws Exception {
        Query query = instanceQuery(serviceName, groupName, namespaceId, ip, port, clusterName);
        addIfNotBlank(query, "metadata", metadata);
        addIfNotBlank(query, "weight", weight);
        addIfNotBlank(query, "healthy", healthy);
        addIfNotBlank(query, "enabled", enabled);
        addIfNotBlank(query, "ephemeral", ephemeral);
        JsonNode root = putFormOk(ADMIN_INSTANCE_PATH, query);
        assertEquals("ok", root.get("data").asText(), root.toString());
        return root;
    }

    protected JsonNode partialUpdateInstance(String serviceName, String groupName, String namespaceId, String ip,
            int port, String clusterName, String metadata, String weight, String enabled) throws Exception {
        Query query = instanceQuery(serviceName, groupName, namespaceId, ip, port, clusterName);
        addIfNotBlank(query, "metadata", metadata);
        addIfNotBlank(query, "weight", weight);
        addIfNotBlank(query, "enabled", enabled);
        JsonNode root = putFormOk(ADMIN_INSTANCE_PARTIAL_PATH, query);
        assertEquals("ok", root.get("data").asText(), root.toString());
        return root;
    }

    protected void deregisterInstanceQuietly(String serviceName, String groupName, String namespaceId, String ip,
            int port) throws Exception {
        deregisterInstanceQuietly(serviceName, groupName, namespaceId, ip, port, DEFAULT_CLUSTER);
    }

    protected void deregisterInstanceQuietly(String serviceName, String groupName, String namespaceId, String ip,
            int port, String clusterName) throws Exception {
        deregisterInstanceQuietly(serviceName, groupName, namespaceId, ip, port, clusterName, null);
    }

    protected void deregisterInstanceQuietly(String serviceName, String groupName, String namespaceId, String ip,
            int port, String clusterName, String ephemeral) throws Exception {
        Query query = instanceQuery(serviceName, groupName, namespaceId, ip, port, clusterName);
        addIfNotBlank(query, "ephemeral", ephemeral);
        deleteQuietly(ADMIN_INSTANCE_PATH, query);
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

    protected void assertNamingPageShape(JsonNode page) {
        assertNotNull(page.get("pageNumber"), page.toString());
        assertNotNull(page.get("pagesAvailable"), page.toString());
        assertNotNull(page.get("totalCount"), page.toString());
        assertTrue(page.get("pageNumber").asInt() >= 1, page.toString());
        assertTrue(page.get("pagesAvailable").asInt() >= 0, page.toString());
        assertTrue(page.get("totalCount").asInt() >= 0, page.toString());
        assertTrue(page.get("pageItems").isArray(), page.toString());
    }

    protected JsonNode getInstanceDetail(String serviceName, String groupName, String namespaceId, String ip,
            int port, String clusterName) throws Exception {
        return getJsonOk(ADMIN_INSTANCE_PATH, instanceQuery(serviceName, groupName, namespaceId, ip, port,
                clusterName)).get("data");
    }

    protected JsonNode listInstances(String serviceName, String groupName, String namespaceId, String clusterName,
            String healthyOnly) throws Exception {
        return getJsonOk(ADMIN_INSTANCE_LIST_PATH, instanceListQuery(serviceName, groupName, namespaceId,
                clusterName, healthyOnly)).get("data");
    }

    protected JsonNode findInstance(JsonNode instances, String ip, int port) {
        for (JsonNode each : instances) {
            if (ip.equals(each.get("ip").asText()) && port == each.get("port").asInt()) {
                return each;
            }
        }
        return MissingNode.getInstance();
    }

    protected JsonNode waitUntilInstanceVisible(String serviceName, String groupName, String namespaceId,
            String clusterName, String ip, int port) throws Exception {
        JsonNode listed = MissingNode.getInstance();
        int retryTime = 20;
        while (retryTime-- > 0) {
            listed = listInstances(serviceName, groupName, namespaceId, null, null);
            JsonNode instance = findInstance(listed, ip, port);
            if (!instance.isMissingNode()) {
                return instance;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Expected registered instance to be visible, last result=" + listed);
    }

    protected void waitUntilInstanceAbsent(String serviceName, String groupName, String namespaceId,
            String clusterName, String ip, int port) throws Exception {
        JsonNode listed = MissingNode.getInstance();
        int retryTime = 20;
        while (retryTime-- > 0) {
            listed = listInstances(serviceName, groupName, namespaceId, null, null);
            if (findInstance(listed, ip, port).isMissingNode()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Expected deregistered instance to be absent, last result=" + listed);
    }

    protected JsonNode waitUntilInstanceMetadata(String serviceName, String groupName, String namespaceId,
            String clusterName, String ip, int port, String metadataKey, String expectedValue) throws Exception {
        JsonNode instance = MissingNode.getInstance();
        int retryTime = 20;
        while (retryTime-- > 0) {
            instance = getInstanceDetail(serviceName, groupName, namespaceId, ip, port, clusterName);
            JsonNode metadata = instance.path("metadata");
            if (expectedValue.equals(metadata.path(metadataKey).asText(null))) {
                return instance;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Expected instance metadata " + metadataKey + "=" + expectedValue
                + ", last instance=" + instance);
    }

    protected JsonNode waitUntilInstanceMetadataMissing(String serviceName, String groupName, String namespaceId,
            String clusterName, String ip, int port, String metadataKey) throws Exception {
        JsonNode instance = MissingNode.getInstance();
        int retryTime = 20;
        while (retryTime-- > 0) {
            instance = getInstanceDetail(serviceName, groupName, namespaceId, ip, port, clusterName);
            if (!instance.path("metadata").has(metadataKey)) {
                return instance;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Expected instance metadata " + metadataKey + " to be missing, last instance="
                + instance);
    }

    protected JsonNode waitUntilInstanceHealthy(String serviceName, String groupName, String namespaceId,
            String clusterName, String ip, int port, boolean healthy) throws Exception {
        JsonNode instance = MissingNode.getInstance();
        int retryTime = 20;
        while (retryTime-- > 0) {
            instance = getInstanceDetail(serviceName, groupName, namespaceId, ip, port, clusterName);
            if (healthy == instance.path("healthy").asBoolean(!healthy)) {
                return instance;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Expected instance healthy=" + healthy + ", last instance=" + instance);
    }

    protected void assertInstance(JsonNode instance, String serviceName, String groupName, String ip, int port,
            String clusterName, String metadataKey, String metadataValue) {
        assertEquals(groupName + "@@" + serviceName, instance.get("serviceName").asText(), instance.toString());
        assertEquals(ip, instance.get("ip").asText(), instance.toString());
        assertEquals(port, instance.get("port").asInt(), instance.toString());
        assertEquals(clusterName, instance.get("clusterName").asText(), instance.toString());
        if (null != metadataKey) {
            assertEquals(metadataValue, instance.get("metadata").get(metadataKey).asText(), instance.toString());
        }
    }

    protected void assertInstanceState(JsonNode instance, double weight, boolean healthy, boolean enabled,
            boolean ephemeral) {
        assertEquals(weight, instance.get("weight").asDouble(), 0.0001D, instance.toString());
        assertEquals(healthy, instance.get("healthy").asBoolean(), instance.toString());
        assertEquals(enabled, instance.get("enabled").asBoolean(), instance.toString());
        assertEquals(ephemeral, instance.get("ephemeral").asBoolean(), instance.toString());
    }

    protected JsonNode waitUntilClusterMetadata(String serviceName, String groupName, String namespaceId,
            String clusterName, String metadataKey, String metadataValue) throws Exception {
        JsonNode detail = MissingNode.getInstance();
        int retryTime = 20;
        while (retryTime-- > 0) {
            detail = getJsonOk(ADMIN_SERVICE_PATH, serviceQuery(serviceName, groupName, namespaceId)).get("data");
            JsonNode cluster = detail.path("clusterMap").path(clusterName);
            if (!cluster.isMissingNode()
                    && metadataValue.equals(cluster.path("metadata").path(metadataKey).asText(null))) {
                return cluster;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Expected cluster metadata to be visible, last service detail=" + detail);
    }
}
