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

package com.alibaba.nacos.test.consoleapi.naming;

import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.test.consoleapi.ConsoleApiBaseITCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared helpers for naming console OpenAPI integration tests.
 *
 * @author xiweng.yy
 */
public abstract class NamingConsoleApiBaseITCase extends ConsoleApiBaseITCase {

    protected static final String CONSOLE_SERVICE_PATH = CONSOLE_BASE_PATH + "/ns/service";

    protected static final String CONSOLE_SERVICE_LIST_PATH = CONSOLE_SERVICE_PATH + "/list";

    protected static final String CONSOLE_SERVICE_SUBSCRIBERS_PATH = CONSOLE_SERVICE_PATH + "/subscribers";

    protected static final String CONSOLE_SERVICE_SELECTOR_TYPES_PATH = CONSOLE_SERVICE_PATH + "/selector/types";

    protected static final String CONSOLE_SERVICE_CLUSTER_PATH = CONSOLE_SERVICE_PATH + "/cluster";

    protected static final String CONSOLE_INSTANCE_PATH = CONSOLE_BASE_PATH + "/ns/instance";

    protected static final String CONSOLE_INSTANCE_LIST_PATH = CONSOLE_INSTANCE_PATH + "/list";

    protected static final String ADMIN_INSTANCE_PATH = nacosPath("/v3/admin/ns/instance");

    protected String randomServiceName(String scenario) {
        return randomConsoleName("service_" + scenario);
    }

    protected String randomGroupName(String scenario) {
        return randomConsoleName("group_" + scenario);
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
            int pageNo, int pageSize) {
        Query query = serviceQuery(serviceName, groupName, namespaceId);
        addIfNotBlank(query, "clusterName", clusterName);
        query.addParam("pageNo", String.valueOf(pageNo));
        query.addParam("pageSize", String.valueOf(pageSize));
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

    protected JsonNode createService(String serviceName, String groupName, String namespaceId,
            String metadata, String protectThreshold) throws Exception {
        JsonNode root = postFormOk(CONSOLE_SERVICE_PATH, serviceQuery(serviceName, groupName, namespaceId)
                .addParam("metadata", metadata).addParam("protectThreshold", protectThreshold)
                .addParam("ephemeral", "false"));
        assertEquals("ok", root.get("data").asText(), root.toString());
        return root;
    }

    protected JsonNode updateService(String serviceName, String groupName, String namespaceId,
            String metadata, String protectThreshold) throws Exception {
        JsonNode root = putFormOk(CONSOLE_SERVICE_PATH, serviceQuery(serviceName, groupName, namespaceId)
                .addParam("metadata", metadata).addParam("protectThreshold", protectThreshold));
        assertEquals("ok", root.get("data").asText(), root.toString());
        return root;
    }

    protected void deleteServiceQuietly(String serviceName, String groupName, String namespaceId) throws Exception {
        deleteQuietly(CONSOLE_SERVICE_PATH, serviceQuery(serviceName, groupName, namespaceId));
    }

    protected JsonNode registerPersistentInstanceForSetup(String serviceName, String groupName, String namespaceId,
            String ip, int port, String clusterName, String metadata, String weight, String healthy,
            String enabled) throws Exception {
        Query query = instanceQuery(serviceName, groupName, namespaceId, ip, port, clusterName);
        addIfNotBlank(query, "metadata", metadata);
        addIfNotBlank(query, "weight", weight);
        addIfNotBlank(query, "healthy", healthy);
        addIfNotBlank(query, "enabled", enabled);
        query.addParam("ephemeral", "false");
        HttpRestResult<String> restResult = nacosRestTemplate.postForm(url(ADMIN_INSTANCE_PATH), Header.EMPTY,
                query, Collections.emptyMap(), String.class);
        assertTrue(restResult.ok(), "HTTP status should be 2xx, code=" + restResult.getCode() + ", body="
                + restResult.getData() + ", message=" + restResult.getMessage());
        JsonNode root = JacksonUtils.toObj(restResult.getData());
        assertSuccess(root);
        assertEquals("ok", root.get("data").asText(), root.toString());
        return root;
    }

    protected void removePersistentInstanceQuietly(String serviceName, String groupName, String namespaceId,
            String ip, int port, String clusterName) throws Exception {
        Query query = instanceQuery(serviceName, groupName, namespaceId, ip, port, clusterName);
        query.addParam("ephemeral", "false");
        deleteQuietly(CONSOLE_INSTANCE_PATH, query);
    }

    protected JsonNode listInstances(String serviceName, String groupName, String namespaceId, String clusterName,
            int pageNo, int pageSize) throws Exception {
        return getJsonOk(CONSOLE_INSTANCE_LIST_PATH, instanceListQuery(serviceName, groupName, namespaceId,
                clusterName, pageNo, pageSize)).get("data");
    }

    protected JsonNode waitUntilInstanceVisible(String serviceName, String groupName, String namespaceId,
            String clusterName, String ip, int port) throws Exception {
        JsonNode page = MissingNode.getInstance();
        int retryTime = 20;
        while (retryTime-- > 0) {
            page = listInstances(serviceName, groupName, namespaceId, null, 1, 10);
            JsonNode instance = findInstance(page, ip, port);
            if (!instance.isMissingNode()) {
                return instance;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Expected instance to be visible, last page=" + page);
    }

    protected void waitUntilInstanceAbsent(String serviceName, String groupName, String namespaceId,
            String clusterName, String ip, int port) throws Exception {
        JsonNode page = MissingNode.getInstance();
        int retryTime = 20;
        while (retryTime-- > 0) {
            page = listInstances(serviceName, groupName, namespaceId, null, 1, 10);
            if (findInstance(page, ip, port).isMissingNode()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Expected instance to be absent, last page=" + page);
    }

    protected JsonNode waitUntilInstanceMetadata(String serviceName, String groupName, String namespaceId,
            String clusterName, String ip, int port, String metadataKey, String expectedValue) throws Exception {
        JsonNode instance = MissingNode.getInstance();
        int retryTime = 20;
        while (retryTime-- > 0) {
            instance = findInstance(listInstances(serviceName, groupName, namespaceId, null, 1, 10),
                    ip, port);
            if (!instance.isMissingNode()
                    && expectedValue.equals(instance.path("metadata").path(metadataKey).asText(null))) {
                return instance;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Expected instance metadata " + metadataKey + "=" + expectedValue
                + ", last instance=" + instance);
    }

    protected JsonNode waitUntilClusterMetadata(String serviceName, String groupName, String namespaceId,
            String clusterName, String metadataKey, String metadataValue) throws Exception {
        JsonNode detail = MissingNode.getInstance();
        int retryTime = 20;
        while (retryTime-- > 0) {
            detail = getJsonOk(CONSOLE_SERVICE_PATH, serviceQuery(serviceName, groupName, namespaceId))
                    .get("data");
            JsonNode cluster = detail.path("clusterMap").path(clusterName);
            if (!cluster.isMissingNode()
                    && metadataValue.equals(cluster.path("metadata").path(metadataKey).asText(null))) {
                return cluster;
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Expected cluster metadata to be visible, last service detail=" + detail);
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

    protected JsonNode findInstance(JsonNode page, String ip, int port) {
        for (JsonNode each : page.get("pageItems")) {
            if (ip.equals(each.get("ip").asText()) && port == each.get("port").asInt()) {
                return each;
            }
        }
        return MissingNode.getInstance();
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
}
