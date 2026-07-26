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
 * Integration tests for the naming client Open API {@code POST /nacos/v3/client/ns/instance}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: registering an instance returns success and the instance becomes visible through the
 *     client list API with ip, port, serviceName, clusterName, healthy, enabled, weight, metadata, and ephemeral
 *     state.</li>
 *     <li>Boundary/validation: omitted namespaceId, groupName, clusterName, healthy, weight, and enabled use defaults;
 *     explicit groupName, clusterName, healthy, weight, metadata, and ephemeral values are persisted; heartBeat=true is
 *     accepted for existing instances but returns INSTANCE_NOT_FOUND for absent instances; serviceName, ip, and port are
 *     required.</li>
 *     <li>Error handling: invalid weight and clusterName return controlled HTTP 400 responses with wrapped
 *     {@code Result} fields instead of HTTP 500.</li>
 * </ul>
 *
 * <p>GET list and DELETE calls to {@code /nacos/v3/client/ns/instance} are helper calls only; this class keeps its
 * assertions focused on the register API contract.
 *
 * @author xiweng.yy
 */
public class InstanceRegisterOpenApiITCase extends OpenApiBaseITCase {
    
    private static final String INSTANCE_PATH =
            nacosPath(UtilsAndCommons.INSTANCE_V3_CLIENT_API_PATH);
    
    private static final String INSTANCE_LIST_PATH = INSTANCE_PATH + "/list";
    
    private static final String TEST_GROUP = "OPENAPI_IT_REGISTER_GROUP";
    
    private static final String TEST_CLUSTER = "openapi-it-register-cluster";
    
    @Test
    public void testRegisterWithDefaultValuesMakesInstanceDiscoverable() throws Exception {
        String serviceName = "openapi-it-register-default-" + UUID.randomUUID();
        String ip = "10.12.0.1";
        int port = 9101;
        assertRegisterOk(Query.newInstance().addParam("serviceName", serviceName).addParam("ip", ip)
                .addParam("port", String.valueOf(port)).addParam("metadata", "source=openapi-it"));
        addCleanup(() -> deregister(serviceName, ip, port, UtilsAndCommons.DEFAULT_CLUSTER_NAME, null));

        Instance actual = waitUntilInstanceVisible(serviceName, null, UtilsAndCommons.DEFAULT_CLUSTER_NAME, ip, port);
        assertEquals(Constants.DEFAULT_GROUP + "@@" + serviceName, actual.getServiceName());
        assertEquals(UtilsAndCommons.DEFAULT_CLUSTER_NAME, actual.getClusterName());
        assertTrue(actual.isHealthy());
        assertTrue(actual.isEnabled());
        assertTrue(actual.isEphemeral());
        assertEquals(1.0, actual.getWeight(), 0.0001);
        assertEquals("openapi-it", actual.getMetadata().get("source"));
    }
    
    @Test
    public void testRegisterWithExplicitFieldsPersistsClientVisibleState() throws Exception {
        String serviceName = "openapi-it-register-explicit-" + UUID.randomUUID();
        String ip = "10.12.0.2";
        int port = 9102;
        assertRegisterOk(baseInstanceQuery(serviceName, ip, port).addParam("groupName", TEST_GROUP)
                .addParam("clusterName", TEST_CLUSTER).addParam("healthy", "false").addParam("enabled", "true")
                .addParam("weight", "2.5").addParam("metadata", "source=openapi-it,case=explicit"));
        addCleanup(() -> deregister(serviceName, ip, port, TEST_CLUSTER, TEST_GROUP));

        Instance actual = waitUntilInstanceVisible(serviceName, TEST_GROUP, TEST_CLUSTER, ip, port);
        assertEquals(TEST_GROUP + "@@" + serviceName, actual.getServiceName());
        assertEquals(TEST_CLUSTER, actual.getClusterName());
        assertFalse(actual.isHealthy());
        assertTrue(actual.isEnabled());
        assertEquals(2.5, actual.getWeight(), 0.0001);
        assertEquals("explicit", actual.getMetadata().get("case"));
    }
    
    @Test
    public void testRegisterPersistentInstanceWhenEphemeralFalse() throws Exception {
        String serviceName = "openapi-it-register-persistent-" + UUID.randomUUID();
        String ip = "10.12.0.3";
        int port = 9103;
        assertRegisterOk(baseInstanceQuery(serviceName, ip, port).addParam("clusterName", TEST_CLUSTER)
                .addParam("ephemeral", "false"));
        addCleanup(() -> deregister(serviceName, ip, port, TEST_CLUSTER, null));

        Instance actual = waitUntilInstanceVisible(serviceName, null, TEST_CLUSTER, ip, port);
        assertFalse(actual.isEphemeral());
    }
    
    @Test
    public void testHeartbeatExistingInstanceReturnsSuccess() throws Exception {
        String serviceName = "openapi-it-register-heartbeat-" + UUID.randomUUID();
        String ip = "10.12.0.4";
        int port = 9104;
        assertRegisterOk(baseInstanceQuery(serviceName, ip, port).addParam("clusterName", TEST_CLUSTER));
        addCleanup(() -> deregister(serviceName, ip, port, TEST_CLUSTER, null));
        assertRegisterOk(baseInstanceQuery(serviceName, ip, port).addParam("clusterName", TEST_CLUSTER)
                .addParam("heartBeat", "true"));

        Instance actual = waitUntilInstanceVisible(serviceName, null, TEST_CLUSTER, ip, port);
        assertEquals(ip, actual.getIp());
        assertEquals(port, actual.getPort());
    }
    
    @Test
    public void testHeartbeatAbsentInstanceReturnsInstanceNotFoundResult() throws Exception {
        String serviceName = "openapi-it-register-missing-heartbeat-" + UUID.randomUUID();
        Query query = baseInstanceQuery(serviceName, "10.12.0.5", 9105).addParam("heartBeat", "true");
        Result<String> actual = postInstance(query);
        assertEquals(ErrorCode.INSTANCE_NOT_FOUND.getCode(), actual.getCode());
        assertNotNull(actual.getMessage());
    }
    
    @Test
    public void testRegisterMissingServiceNameReturnsBadRequestResultBody() throws Exception {
        Query query = Query.newInstance().addParam("ip", "10.12.0.6").addParam("port", "9106");
        assertBadRequest(query, ErrorCode.PARAMETER_MISSING, "serviceName");
    }
    
    @Test
    public void testRegisterMissingIpReturnsBadRequestResultBody() throws Exception {
        Query query = Query.newInstance().addParam("serviceName", "openapi-it-register-missing-ip")
                .addParam("port", "9107");
        assertBadRequest(query, ErrorCode.PARAMETER_MISSING, "ip");
    }
    
    @Test
    public void testRegisterMissingPortReturnsBadRequestResultBody() throws Exception {
        Query query = Query.newInstance().addParam("serviceName", "openapi-it-register-missing-port")
                .addParam("ip", "10.12.0.8");
        assertBadRequest(query, ErrorCode.PARAMETER_MISSING, "port");
    }
    
    @Test
    public void testRegisterInvalidWeightReturnsBadRequestResultBody() throws Exception {
        Query query = baseInstanceQuery("openapi-it-register-bad-weight", "10.12.0.9", 9109)
                .addParam("weight", "10001");
        assertBadRequest(query, ErrorCode.WEIGHT_ERROR, "weights range");
    }
    
    @Test
    public void testRegisterInvalidClusterNameReturnsBadRequestResultBody() throws Exception {
        Query query = baseInstanceQuery("openapi-it-register-bad-cluster", "10.12.0.10", 9110)
                .addParam("clusterName", "cluster1,cluster2");
        assertBadRequest(query, ErrorCode.PARAMETER_VALIDATE_ERROR, "cluster");
    }
    
    private void assertRegisterOk(Query query) throws Exception {
        Result<String> actual = postInstance(query);
        assertEquals(ErrorCode.SUCCESS.getCode(), actual.getCode());
        assertEquals(ErrorCode.SUCCESS.getMsg(), actual.getMessage());
        assertEquals("ok", actual.getData());
    }
    
    private Result<String> postInstance(Query query) throws Exception {
        HttpRestResult<String> restResult = nacosRestTemplate.postForm(url(INSTANCE_PATH),
                Header.EMPTY, query, Collections.emptyMap(), String.class);
        assertTrue(restResult.ok(), "register HTTP status should be 2xx, body=" + restResult.getData());
        return JacksonUtils.toObj(restResult.getData(), new TypeReference<>() {
        });
    }
    
    private void assertBadRequest(Query query, ErrorCode errorCode, String expectedData) throws Exception {
        HttpResponse response = postRaw(INSTANCE_PATH, query);
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
