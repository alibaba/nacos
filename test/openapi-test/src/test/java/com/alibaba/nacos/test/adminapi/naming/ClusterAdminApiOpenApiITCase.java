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
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Integration tests for naming cluster admin OpenAPI {@code /nacos/v3/admin/ns/cluster}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: update persists cluster health-check port, checker type, instance-port policy, and
 *     metadata, verified through service detail's cluster map after an instance has made the cluster visible in service
 *     storage.</li>
 *     <li>Boundary/validation: omitted namespace/group default to public and DEFAULT_GROUP; serviceName, clusterName,
 *     checkPort, useInstancePort4Check, and healthChecker are required. HTTP checker paths accept queries but reject
 *     scheme, authority, fragment, invalid header, and request-framing header inputs before metadata is written.</li>
 *     <li>Exception/error handling: missing required fields and a non-existent owning service return controlled
 *     failures instead of HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ClusterAdminApiOpenApiITCase extends NamingAdminApiBaseITCase {
    
    private static final String TEST_CLUSTER = "openapi-it-admin-cluster";
    
    @Test
    public void testUpdateClusterMetadataAndDefaultGroup() throws Exception {
        String serviceName = randomServiceName("cluster");
        String ip = "10.13.2.1";
        int port = 19501;
        
        registerInstance(serviceName, null, null, ip, port, TEST_CLUSTER, "{\"scene\":\"cluster\"}",
                "1.0", "true", "true", "true");
        addCleanup(() -> deleteServiceQuietly(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE));
        addCleanup(() -> deregisterInstanceQuietly(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip, port,
                TEST_CLUSTER));
        waitUntilInstanceVisible(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, TEST_CLUSTER, ip, port);
        
        JsonNode root = putFormOk(ADMIN_CLUSTER_PATH, clusterQuery(serviceName, null, null, TEST_CLUSTER,
                "18888", "false", "{\"type\":\"TCP\"}", "{\"zone\":\"hz\",\"owner\":\"it\"}"));
        assertEquals("ok", root.get("data").asText(), root.toString());
        
        JsonNode cluster = waitUntilClusterMetadata(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, TEST_CLUSTER,
                "zone", "hz");
        assertEquals(TEST_CLUSTER, cluster.get("clusterName").asText(), cluster.toString());
        assertEquals(18888, cluster.get("healthyCheckPort").asInt(), cluster.toString());
        assertFalse(cluster.get("useInstancePortForCheck").asBoolean(), cluster.toString());
        assertEquals("TCP", cluster.get("healthChecker").get("type").asText(), cluster.toString());
        assertEquals("it", cluster.get("metadata").get("owner").asText(), cluster.toString());
    }
    
    @Test
    public void testHttpCheckerRequestTargetValidationAndNoMetadataWrite() throws Exception {
        String serviceName = randomServiceName("http-target");
        String ip = "10.13.2.2";
        int port = 19502;
        String validChecker = "{\"type\":\"HTTP\",\"path\":\"/health/ready?group=readiness\","
                + "\"headers\":\"\",\"expectedResponseCode\":200}";
        
        registerInstance(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip, port, TEST_CLUSTER,
                "{\"scene\":\"http-target\"}", "1.0", "true", "true", "true");
        addCleanup(() -> deleteServiceQuietly(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE));
        addCleanup(() -> deregisterInstanceQuietly(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip,
                port, TEST_CLUSTER));
        waitUntilInstanceVisible(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, TEST_CLUSTER, ip, port);
        putFormOk(ADMIN_CLUSTER_PATH, clusterQuery(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE,
                TEST_CLUSTER, "18891", "false", validChecker, "{\"guard\":\"baseline\"}"));
        JsonNode cluster = waitUntilClusterMetadata(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE,
                TEST_CLUSTER, "guard", "baseline");
        assertEquals("/health/ready?group=readiness",
                cluster.path("healthChecker").path("path").asText(), cluster.toString());
        
        String[] invalidTargets = {"http://127.0.0.1:54321/probe", "//127.0.0.1:54321/probe",
                "/health#fragment"};
        for (String invalidTarget : invalidTargets) {
            String invalidChecker = "{\"type\":\"HTTP\",\"path\":\"" + invalidTarget
                    + "\",\"headers\":\"\",\"expectedResponseCode\":200}";
            assertError(putRaw(ADMIN_CLUSTER_PATH, clusterQuery(serviceName, DEFAULT_GROUP,
                    DEFAULT_NAMESPACE, TEST_CLUSTER, "18892", "false", invalidChecker,
                    "{\"guard\":\"rejected\"}")), 400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                    "healthChecker");
        }
        String invalidHeaderChecker = "{\"type\":\"HTTP\",\"path\":\"/health\","
                + "\"headers\":\"X\\r\\nInjected:value\",\"expectedResponseCode\":200}";
        assertError(putRaw(ADMIN_CLUSTER_PATH, clusterQuery(serviceName, DEFAULT_GROUP,
                DEFAULT_NAMESPACE, TEST_CLUSTER, "18892", "false", invalidHeaderChecker,
                "{\"guard\":\"rejected\"}")), 400, ErrorCode.PARAMETER_VALIDATE_ERROR,
                "healthChecker");
        
        cluster = waitUntilClusterMetadata(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE,
                TEST_CLUSTER, "guard", "baseline");
        assertEquals(18891, cluster.path("healthyCheckPort").asInt(), cluster.toString());
        assertEquals("/health/ready?group=readiness",
                cluster.path("healthChecker").path("path").asText(), cluster.toString());
    }
    
    @Test
    public void testClusterValidationReturnsBadRequest() throws Exception {
        assertError(putRaw(ADMIN_CLUSTER_PATH, Query.newInstance().addParam("clusterName", TEST_CLUSTER)
                .addParam("checkPort", "18889").addParam("useInstancePort4Check", "true")
                .addParam("healthChecker", "{\"type\":\"TCP\"}")), 400,
                ErrorCode.PARAMETER_MISSING, "serviceName");
        assertError(putRaw(ADMIN_CLUSTER_PATH, clusterQuery(randomServiceName("missing-cluster"),
                DEFAULT_GROUP, DEFAULT_NAMESPACE, null, "18889", "true", "{\"type\":\"TCP\"}", null)),
                400, ErrorCode.PARAMETER_MISSING, "clusterName");
        assertError(putRaw(ADMIN_CLUSTER_PATH, clusterQuery(randomServiceName("missing-port"),
                DEFAULT_GROUP, DEFAULT_NAMESPACE, TEST_CLUSTER, null, "true", "{\"type\":\"TCP\"}", null)),
                400, ErrorCode.PARAMETER_MISSING, "checkPort");
        assertError(putRaw(ADMIN_CLUSTER_PATH, clusterQuery(randomServiceName("missing-use-port"),
                DEFAULT_GROUP, DEFAULT_NAMESPACE, TEST_CLUSTER, "18889", null, "{\"type\":\"TCP\"}", null)),
                400, ErrorCode.PARAMETER_MISSING, "useInstancePort4Check");
        assertError(putRaw(ADMIN_CLUSTER_PATH, clusterQuery(randomServiceName("missing-checker"),
                DEFAULT_GROUP, DEFAULT_NAMESPACE, TEST_CLUSTER, "18889", "true", null, null)),
                400, ErrorCode.PARAMETER_MISSING, "healthChecker");
    }
    
    @Test
    public void testUpdateClusterForMissingServiceReturnsControlledError() throws Exception {
        assertError(putRaw(ADMIN_CLUSTER_PATH, clusterQuery(randomServiceName("missing-service"),
                DEFAULT_GROUP, DEFAULT_NAMESPACE, TEST_CLUSTER, "18890", "true", "{\"type\":\"TCP\"}",
                "{\"zone\":\"missing\"}")), 400, ErrorCode.SERVER_ERROR, "service not found");
    }
}
