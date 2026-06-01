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

package com.alibaba.nacos.test.openapi.client.naming;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.test.openapi.OpenApiBaseITCase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the naming client Open API
 * {@code GET /nacos/v3/client/ns/instance/list}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: registered enabled instances can be listed with
 *     their ip, port, cluster, service, health, enabled, weight, and metadata.</li>
 *     <li>Boundary/validation: omitted namespace, group, and clusterName use
 *     defaults; explicit groupName isolates resources; clusterName filters
 *     results; healthyOnly is accepted but not used by this Open API; missing
 *     serviceName returns HTTP 400.</li>
 *     <li>Error handling: unknown services return a successful empty data array,
 *     and validation errors return a controlled {@code code/message/data}
 *     response instead of HTTP 500.</li>
 * </ul>
 *
 * <p>POST and DELETE calls to {@code /nacos/v3/client/ns/instance} are helper
 * calls only; this class keeps its assertions focused on the list API contract.
 *
 * @author xiweng.yy
 */
public class InstanceListOpenApiITCase extends OpenApiBaseITCase {
    
    private static final String INSTANCE_PATH =
            nacosPath(UtilsAndCommons.INSTANCE_V3_CLIENT_API_PATH);
    
    private static final String INSTANCE_LIST_PATH = INSTANCE_PATH + "/list";
    
    private static final String TEST_CLUSTER = "openapi-it-cluster";
    
    private static final String CUSTOM_GROUP = "OPENAPI_IT_GROUP";
    
    @Test
    public void testListDefaultNamespaceAndGroupFiltersDisabledInstances() throws Exception {
        String serviceName = "openapi-it-list-" + UUID.randomUUID();
        String enabledIp = "10.11.0.1";
        String disabledIp = "10.11.0.2";
        String otherClusterIp = "10.11.0.3";
        assertRegisterOk(serviceName, enabledIp, 9001, TEST_CLUSTER, true);
        assertRegisterOk(serviceName, disabledIp, 9002, TEST_CLUSTER, false);
        assertRegisterOk(serviceName, otherClusterIp, 9003, "other-" + TEST_CLUSTER, true);

        Result<List<Instance>> actual = waitUntilInstanceVisible(serviceName, TEST_CLUSTER, enabledIp, 9001);
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), actual.getMessage());
        assertNotNull(actual.getData());
        assertTrue(containsInstance(actual.getData(), enabledIp, 9001));
        assertFalse(containsInstance(actual.getData(), disabledIp, 9002),
                "client list API should filter enabled=false instances");
        assertFalse(containsInstance(actual.getData(), otherClusterIp, 9003),
                "client list API should filter by clusterName");

        Instance enabled = findInstance(actual.getData(), enabledIp, 9001);
        assertEquals(TEST_CLUSTER, enabled.getClusterName());
        assertEquals(Constants.DEFAULT_GROUP + "@@" + serviceName, enabled.getServiceName());
        assertTrue(enabled.isHealthy());
        assertTrue(enabled.isEnabled());
        assertEquals(1.0, enabled.getWeight(), 0.0001);
        assertEquals("openapi-it", enabled.getMetadata().get("source"));
    }
    
    @Test
    public void testListWithoutClusterNameReturnsAllEnabledInstances() throws Exception {
        String serviceName = "openapi-it-list-all-" + UUID.randomUUID();
        String defaultClusterIp = "10.11.1.1";
        String customClusterIp = "10.11.1.2";
        String disabledIp = "10.11.1.3";
        assertRegisterOk(serviceName, defaultClusterIp, 9011, UtilsAndCommons.DEFAULT_CLUSTER_NAME, true);
        assertRegisterOk(serviceName, customClusterIp, 9012, TEST_CLUSTER, true);
        assertRegisterOk(serviceName, disabledIp, 9013, TEST_CLUSTER, false);

        Result<List<Instance>> actual = waitUntilInstanceVisible(serviceName, null, defaultClusterIp, 9011);
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), actual.getMessage());
        assertTrue(containsInstance(actual.getData(), defaultClusterIp, 9011));
        assertTrue(containsInstance(actual.getData(), customClusterIp, 9012));
        assertFalse(containsInstance(actual.getData(), disabledIp, 9013),
                "client list API should filter enabled=false instances without clusterName");
    }
    
    @Test
    public void testListWithExplicitGroupNameIsolatesDefaultGroup() throws Exception {
        String serviceName = "openapi-it-list-group-" + UUID.randomUUID();
        String defaultGroupIp = "10.11.2.1";
        String customGroupIp = "10.11.2.2";
        assertRegisterOk(serviceName, defaultGroupIp, 9021, TEST_CLUSTER, true);
        assertRegisterOk(serviceName, customGroupIp, 9022, TEST_CLUSTER, true, CUSTOM_GROUP);

        Result<List<Instance>> defaultGroup = waitUntilInstanceVisible(serviceName, TEST_CLUSTER, defaultGroupIp, 9021);
        assertTrue(containsInstance(defaultGroup.getData(), defaultGroupIp, 9021));
        assertFalse(containsInstance(defaultGroup.getData(), customGroupIp, 9022));

        Result<List<Instance>> customGroup = waitUntilInstanceVisible(serviceName, CUSTOM_GROUP, TEST_CLUSTER,
                customGroupIp, 9022, null);
        assertEquals(ErrorCode.SUCCESS.getCode(), customGroup.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), customGroup.getMessage());
        assertTrue(containsInstance(customGroup.getData(), customGroupIp, 9022));
        assertFalse(containsInstance(customGroup.getData(), defaultGroupIp, 9021));
        assertEquals(CUSTOM_GROUP + "@@" + serviceName,
                findInstance(customGroup.getData(), customGroupIp, 9022).getServiceName());
    }
    
    @Test
    public void testListHealthyOnlyParameterDoesNotFilterOpenApiResult() throws Exception {
        String serviceName = "openapi-it-list-healthy-" + UUID.randomUUID();
        String healthyIp = "10.11.3.1";
        String unhealthyIp = "10.11.3.2";
        assertRegisterOk(serviceName, healthyIp, 9031, TEST_CLUSTER, true, null, true);
        assertRegisterOk(serviceName, unhealthyIp, 9032, TEST_CLUSTER, true, null, false);

        Result<List<Instance>> actual = waitUntilInstanceVisible(serviceName, null, TEST_CLUSTER, unhealthyIp, 9032,
                "true");
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), actual.getMessage());
        assertTrue(containsInstance(actual.getData(), healthyIp, 9031));
        assertTrue(containsInstance(actual.getData(), unhealthyIp, 9032),
                "client list API passes healthOnly=false even when healthyOnly=true");
        assertFalse(findInstance(actual.getData(), unhealthyIp, 9032).isHealthy());
    }
    
    @Test
    public void testListUnknownServiceReturnsEmptySuccessResult() throws Exception {
        Result<List<Instance>> actual = listInstances("openapi-it-absent-" + UUID.randomUUID(),
                null, TEST_CLUSTER, null);
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), actual.getMessage());
        assertNotNull(actual.getData());
        assertTrue(actual.getData().isEmpty());
    }
    
    @Test
    public void testListMissingServiceNameReturnsBadRequestResultBody() throws Exception {
        HttpResponse response = getRaw(INSTANCE_LIST_PATH + "?clusterName=" + TEST_CLUSTER);
        assertEquals(400, response.code());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertNotNull(root);
        assertEquals(ErrorCode.PARAMETER_MISSING.getCode(), root.get("code").asInt(),
                response.body());
        assertTrue(root.get("message").asText().contains("parameter"));
        assertTrue(root.get("data").asText().contains("serviceName"));
    }
    
    private void assertRegisterOk(String serviceName, String ip, int port, String clusterName,
            boolean enabled) throws Exception {
        assertRegisterOk(serviceName, ip, port, clusterName, enabled, null);
    }
    
    private void assertRegisterOk(String serviceName, String ip, int port, String clusterName,
            boolean enabled, String groupName) throws Exception {
        assertRegisterOk(serviceName, ip, port, clusterName, enabled, groupName, true);
    }
    
    private void assertRegisterOk(String serviceName, String ip, int port, String clusterName,
            boolean enabled, String groupName, boolean healthy) throws Exception {
        Query query = buildInstanceQuery(serviceName, ip, port, clusterName, enabled, groupName,
                healthy);
        HttpRestResult<String> restResult = nacosRestTemplate.postForm(url(INSTANCE_PATH),
                Header.EMPTY, query, Collections.emptyMap(), String.class);
        assertTrue(restResult.ok(),
                "register HTTP status should be 2xx, body=" + restResult.getData());
        JsonNode root = JacksonUtils.toObj(restResult.getData());
        assertNotNull(root);
        assertEquals(ErrorCode.SUCCESS.getCode(), root.get("code").asInt(), restResult.getData());
        assertEquals("ok", root.get("data").asText());
        addCleanup(() -> deregister(serviceName, ip, port, clusterName, groupName));
    }
    
    private void deregister(String serviceName, String ip, int port, String clusterName,
            String groupName) throws Exception {
        Query query = Query.newInstance().addParam("serviceName", serviceName).addParam("ip", ip)
                .addParam("port", String.valueOf(port)).addParam("clusterName", clusterName);
        addIfNotBlank(query, "groupName", groupName);
        HttpRestResult<String> restResult = nacosRestTemplate.delete(url(INSTANCE_PATH),
                Header.EMPTY, query, String.class);
        if (!restResult.ok()) {
            logger().warn("deregister instance non-OK: code={} body={}", restResult.getCode(),
                    restResult.getData());
        }
    }
    
    private Result<List<Instance>> waitUntilInstanceVisible(String serviceName, String clusterName,
            String ip, int port) throws Exception {
        return waitUntilInstanceVisible(serviceName, null, clusterName, ip, port, null);
    }
    
    private Result<List<Instance>> waitUntilInstanceVisible(String serviceName, String groupName,
            String clusterName, String ip, int port, String healthyOnly) throws Exception {
        Result<List<Instance>> actual = null;
        int retryTime = 20;
        while (retryTime-- > 0) {
            actual = listInstances(serviceName, groupName, clusterName, healthyOnly);
            if (ErrorCode.SUCCESS.getCode().equals(actual.getCode())
                    && containsInstance(actual.getData(), ip, port)) {
                return actual;
            }
            // Registration is event-driven and may need a short propagation window.
            TimeUnit.MILLISECONDS.sleep(100);
        }
        return actual;
    }
    
    private Result<List<Instance>> listInstances(String serviceName, String groupName,
            String clusterName, String healthyOnly) throws Exception {
        Query query = Query.newInstance().addParam("serviceName", serviceName);
        addIfNotBlank(query, "groupName", groupName);
        addIfNotBlank(query, "clusterName", clusterName);
        addIfNotBlank(query, "healthyOnly", healthyOnly);
        HttpRestResult<String> restResult = nacosRestTemplate.get(url(INSTANCE_LIST_PATH),
                Header.EMPTY, query, String.class);
        assertTrue(restResult.ok(), "list HTTP status should be 2xx, body=" + restResult.getData());
        return JacksonUtils.toObj(restResult.getData(), new TypeReference<>() {
        });
    }
    
    private static Query buildInstanceQuery(String serviceName, String ip, int port,
            String clusterName, boolean enabled, String groupName, boolean healthy) {
        Query query = Query.newInstance().addParam("serviceName", serviceName).addParam("ip", ip)
                .addParam("port", String.valueOf(port)).addParam("clusterName", clusterName)
                .addParam("enabled", String.valueOf(enabled))
                .addParam("healthy", String.valueOf(healthy))
                .addParam("metadata", "source=openapi-it");
        addIfNotBlank(query, "groupName", groupName);
        return query;
    }
    
    private static boolean containsInstance(List<Instance> instances, String ip, int port) {
        return null != findInstance(instances, ip, port);
    }
    
    private static Instance findInstance(List<Instance> instances, String ip, int port) {
        if (null == instances) {
            return null;
        }
        for (Instance each : instances) {
            if (ip.equals(each.getIp()) && port == each.getPort()) {
                return each;
            }
        }
        return null;
    }
}
