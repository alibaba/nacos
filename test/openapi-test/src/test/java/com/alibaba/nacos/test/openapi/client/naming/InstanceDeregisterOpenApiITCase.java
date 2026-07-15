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
 * Integration tests for the naming client Open API {@code DELETE /nacos/v3/client/ns/instance}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: deregistering an existing instance returns success and removes that instance from the
 *     client list API.</li>
 *     <li>Boundary/validation: omitted groupName and clusterName use default values; explicit groupName and clusterName
 *     target only the requested instance identity; deregistering an absent instance is idempotent success; serviceName,
 *     ip, and port are required.</li>
 *     <li>Error handling: required-field and invalid clusterName failures return controlled HTTP 400 responses with
 *     wrapped {@code Result} fields instead of HTTP 500.</li>
 * </ul>
 *
 * <p>POST and GET list calls to {@code /nacos/v3/client/ns/instance} are helper calls only; this class keeps its
 * assertions focused on the deregister API contract.
 *
 * @author xiweng.yy
 */
public class InstanceDeregisterOpenApiITCase extends OpenApiBaseITCase {
    
    private static final String INSTANCE_PATH =
            nacosPath(UtilsAndCommons.INSTANCE_V3_CLIENT_API_PATH);
    
    private static final String INSTANCE_LIST_PATH = INSTANCE_PATH + "/list";
    
    private static final String TEST_GROUP = "OPENAPI_IT_DEREGISTER_GROUP";
    
    private static final String TEST_CLUSTER = "openapi-it-deregister-cluster";
    
    @Test
    public void testDeregisterDefaultIdentityRemovesInstance() throws Exception {
        String serviceName = "openapi-it-deregister-default-" + UUID.randomUUID();
        String ip = "10.13.0.1";
        int port = 9201;
        register(serviceName, ip, port, UtilsAndCommons.DEFAULT_CLUSTER_NAME, null);
        assertNotNull(waitUntilInstanceVisible(serviceName, null, UtilsAndCommons.DEFAULT_CLUSTER_NAME, ip, port));

        assertDeregisterOk(Query.newInstance().addParam("serviceName", serviceName).addParam("ip", ip)
                .addParam("port", String.valueOf(port)));

        assertEventuallyAbsent(serviceName, null, UtilsAndCommons.DEFAULT_CLUSTER_NAME, ip, port);
    }
    
    @Test
    public void testDeregisterExplicitGroupAndClusterDoesNotRemoveOtherIdentity() throws Exception {
        String serviceName = "openapi-it-deregister-isolated-" + UUID.randomUUID();
        String targetIp = "10.13.0.2";
        String otherIp = "10.13.0.3";
        register(serviceName, targetIp, 9202, TEST_CLUSTER, TEST_GROUP);
        register(serviceName, otherIp, 9203, TEST_CLUSTER, null);
        assertNotNull(waitUntilInstanceVisible(serviceName, TEST_GROUP, TEST_CLUSTER, targetIp, 9202));
        assertNotNull(waitUntilInstanceVisible(serviceName, null, TEST_CLUSTER, otherIp, 9203));

        assertDeregisterOk(baseInstanceQuery(serviceName, targetIp, 9202).addParam("groupName", TEST_GROUP)
                .addParam("clusterName", TEST_CLUSTER));

        assertEventuallyAbsent(serviceName, TEST_GROUP, TEST_CLUSTER, targetIp, 9202);
        assertNotNull(waitUntilInstanceVisible(serviceName, null, TEST_CLUSTER, otherIp, 9203));
    }
    
    @Test
    public void testDeregisterAbsentInstanceReturnsSuccess() throws Exception {
        String serviceName = "openapi-it-deregister-absent-" + UUID.randomUUID();
        Result<String> actual = deregister(baseInstanceQuery(serviceName, "10.13.0.4", 9204)
                .addParam("clusterName", TEST_CLUSTER));
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), actual.getMessage());
        assertEquals("ok", actual.getData());
    }
    
    @Test
    public void testDeregisterMissingServiceNameReturnsBadRequestResultBody() throws Exception {
        Query query = Query.newInstance().addParam("ip", "10.13.0.5").addParam("port", "9205");
        assertBadRequest(query, ErrorCode.PARAMETER_MISSING, "serviceName");
    }
    
    @Test
    public void testDeregisterMissingIpReturnsBadRequestResultBody() throws Exception {
        Query query = Query.newInstance().addParam("serviceName", "openapi-it-deregister-missing-ip")
                .addParam("port", "9206");
        assertBadRequest(query, ErrorCode.PARAMETER_MISSING, "ip");
    }
    
    @Test
    public void testDeregisterMissingPortReturnsBadRequestResultBody() throws Exception {
        Query query = Query.newInstance().addParam("serviceName", "openapi-it-deregister-missing-port")
                .addParam("ip", "10.13.0.7");
        assertBadRequest(query, ErrorCode.PARAMETER_MISSING, "port");
    }
    
    @Test
    public void testDeregisterInvalidClusterNameReturnsBadRequestResultBody() throws Exception {
        Query query = baseInstanceQuery("openapi-it-deregister-bad-cluster", "10.13.0.8", 9208)
                .addParam("clusterName", "cluster1,cluster2");
        assertBadRequest(query, ErrorCode.PARAMETER_VALIDATE_ERROR, "cluster");
    }
    
    private void register(String serviceName, String ip, int port, String clusterName,
            String groupName) throws Exception {
        Query query = baseInstanceQuery(serviceName, ip, port).addParam("clusterName", clusterName);
        addIfNotBlank(query, "groupName", groupName);
        HttpRestResult<String> restResult = nacosRestTemplate.postForm(url(INSTANCE_PATH),
                Header.EMPTY, query, Collections.emptyMap(), String.class);
        assertTrue(restResult.ok(), "register HTTP status should be 2xx, body=" + restResult.getData());
        JsonNode root = JacksonUtils.toObj(restResult.getData());
        assertEquals(ErrorCode.SUCCESS.getCode(), root.get("code").asInt(), restResult.getData());
        addCleanup(() -> deregisterQuietly(serviceName, ip, port, clusterName, groupName));
    }
    
    private void assertDeregisterOk(Query query) throws Exception {
        Result<String> actual = deregister(query);
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), actual.getMessage());
        assertEquals("ok", actual.getData());
    }
    
    private Result<String> deregister(Query query) throws Exception {
        HttpRestResult<String> restResult = nacosRestTemplate.delete(url(INSTANCE_PATH),
                Header.EMPTY, query, String.class);
        assertTrue(restResult.ok(), "deregister HTTP status should be 2xx, body=" + restResult.getData());
        return JacksonUtils.toObj(restResult.getData(), new TypeReference<>() {
        });
    }
    
    private void deregisterQuietly(String serviceName, String ip, int port, String clusterName,
            String groupName) throws Exception {
        Query query = baseInstanceQuery(serviceName, ip, port).addParam("clusterName", clusterName);
        addIfNotBlank(query, "groupName", groupName);
        HttpRestResult<String> restResult = nacosRestTemplate.delete(url(INSTANCE_PATH),
                Header.EMPTY, query, String.class);
        if (!restResult.ok()) {
            logger().warn("deregister instance non-OK: code={} body={}", restResult.getCode(),
                    restResult.getData());
        }
    }
    
    private void assertBadRequest(Query query, ErrorCode errorCode, String expectedData) throws Exception {
        HttpResponse response = deleteRaw(INSTANCE_PATH, query);
        assertEquals(400, response.code(), response.body());
        JsonNode root = JacksonUtils.toObj(response.body());
        assertNotNull(root);
        assertEquals(errorCode.getCode(), root.get("code").asInt(), response.body());
        assertNotNull(root.get("message").asText(), response.body());
        assertTrue(root.get("data").asText().contains(expectedData), response.body());
    }
    
    private Instance waitUntilInstanceVisible(String serviceName, String groupName,
            String clusterName, String ip, int port) throws Exception {
        Result<List<Instance>> actual = null;
        int retryTime = 20;
        while (retryTime-- > 0) {
            actual = listInstances(serviceName, groupName, clusterName);
            Instance instance = findInstance(actual.getData(), ip, port);
            if (null != instance) {
                return instance;
            }
            // Registration is event-driven and may need a short propagation window.
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Expected registered instance to be visible, last result="
                + JacksonUtils.toJson(actual));
    }
    
    private void assertEventuallyAbsent(String serviceName, String groupName, String clusterName,
            String ip, int port) throws Exception {
        int retryTime = 20;
        while (retryTime-- > 0) {
            Result<List<Instance>> actual = listInstances(serviceName, groupName, clusterName);
            if (null == findInstance(actual.getData(), ip, port)) {
                return;
            }
            // Deregistration is event-driven and may need a short propagation window.
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Expected deregistered instance to be absent: " + ip + ":" + port);
    }
    
    private Result<List<Instance>> listInstances(String serviceName, String groupName,
            String clusterName) throws Exception {
        Query query = Query.newInstance().addParam("serviceName", serviceName)
                .addParam("clusterName", clusterName);
        addIfNotBlank(query, "groupName", groupName);
        HttpRestResult<String> restResult = nacosRestTemplate.get(url(INSTANCE_LIST_PATH),
                Header.EMPTY, query, String.class);
        assertTrue(restResult.ok(), "list HTTP status should be 2xx, body=" + restResult.getData());
        return JacksonUtils.toObj(restResult.getData(), new TypeReference<>() {
        });
    }
    
    private static Query baseInstanceQuery(String serviceName, String ip, int port) {
        return Query.newInstance().addParam("serviceName", serviceName).addParam("ip", ip)
                .addParam("port", String.valueOf(port));
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
