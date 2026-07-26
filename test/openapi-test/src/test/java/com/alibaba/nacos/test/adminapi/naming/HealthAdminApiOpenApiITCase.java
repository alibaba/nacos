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
 * Integration tests for naming health admin OpenAPI {@code /nacos/v3/admin/ns/health}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: checker discovery returns built-in checker types, and manual health update can change a
 *     persistent instance when the owning cluster uses the NONE health checker.</li>
 *     <li>Boundary/validation: omitted namespace/group/cluster default to public, DEFAULT_GROUP, and DEFAULT;
 *     {@code healthy}, {@code serviceName}, {@code ip}, and {@code port} are required.</li>
 *     <li>Exception/error handling: manual health update against a service without an eligible NONE checker returns a
 *     controlled failure instead of silently changing ephemeral health state.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class HealthAdminApiOpenApiITCase extends NamingAdminApiBaseITCase {

    private static final String TEST_CLUSTER = "openapi-it-admin-health-cluster";

    @Test
    public void testCheckersReturnBuiltInHealthCheckerTypes() throws Exception {
        JsonNode data = getJsonOk(ADMIN_HEALTH_PATH + "/checkers", Query.newInstance()).get("data");
        assertTrue(data.has("NONE"), data.toString());
        assertTrue(data.has("TCP"), data.toString());
        assertTrue(data.get("NONE").isObject(), data.toString());
        assertTrue(data.get("TCP").isObject(), data.toString());
    }

    @Test
    public void testUpdatePersistentInstanceHealthWhenClusterUsesNoneChecker() throws Exception {
        String serviceName = randomServiceName("health");
        String ip = "10.13.3.1";
        int port = 19601;

        createService(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, "{\"scene\":\"health\"}", "0.1");
        addCleanup(() -> deleteServiceQuietly(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE));
        putFormOk(ADMIN_CLUSTER_PATH, clusterQuery(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, TEST_CLUSTER,
                "0", "true", "{\"type\":\"NONE\"}", "{\"health\":\"manual\"}"));
        registerInstance(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip, port, TEST_CLUSTER,
                "{\"scene\":\"health\"}", "1.0", "true", "true", "false");
        addCleanup(() -> deregisterInstanceQuietly(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip, port,
                TEST_CLUSTER, "false"));

        JsonNode registered = waitUntilInstanceVisible(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE,
                TEST_CLUSTER, ip, port);
        assertInstanceState(registered, 1.0D, true, true, false);

        JsonNode updated = putFormOk(ADMIN_HEALTH_INSTANCE_PATH, healthQuery(serviceName, DEFAULT_GROUP,
                DEFAULT_NAMESPACE, ip, port, TEST_CLUSTER, "false"));
        assertEquals("ok", updated.get("data").asText(), updated.toString());
        JsonNode unhealthy = waitUntilInstanceHealthy(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE,
                TEST_CLUSTER, ip, port, false);
        assertInstanceState(unhealthy, 1.0D, false, true, false);

        putFormOk(ADMIN_HEALTH_INSTANCE_PATH, healthQuery(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip,
                port, TEST_CLUSTER, "true"));
        JsonNode healthy = waitUntilInstanceHealthy(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE,
                TEST_CLUSTER, ip, port, true);
        assertInstanceState(healthy, 1.0D, true, true, false);
    }

    @Test
    public void testUpdateHealthValidationReturnsBadRequest() throws Exception {
        assertError(putRaw(ADMIN_HEALTH_INSTANCE_PATH, instanceQuery(randomServiceName("missing-healthy"),
                DEFAULT_GROUP, DEFAULT_NAMESPACE, "10.13.3.2", 19602)), 400,
                ErrorCode.PARAMETER_MISSING, "healthy");
        assertError(putRaw(ADMIN_HEALTH_INSTANCE_PATH, Query.newInstance().addParam("healthy", "true")
                .addParam("ip", "10.13.3.3").addParam("port", "19603")), 400,
                ErrorCode.PARAMETER_MISSING, "serviceName");
        assertError(putRaw(ADMIN_HEALTH_INSTANCE_PATH, Query.newInstance().addParam("healthy", "true")
                .addParam("serviceName", randomServiceName("missing-ip")).addParam("port", "19604")),
                400, ErrorCode.PARAMETER_MISSING, "ip");
        assertError(putRaw(ADMIN_HEALTH_INSTANCE_PATH, Query.newInstance().addParam("healthy", "true")
                .addParam("serviceName", randomServiceName("missing-port")).addParam("ip", "10.13.3.5")),
                400, ErrorCode.PARAMETER_MISSING, "port");
    }

    @Test
    public void testUpdateHealthWithoutManualCheckerReturnsControlledError() throws Exception {
        String serviceName = randomServiceName("health-error");
        String ip = "10.13.3.6";
        int port = 19606;

        registerInstance(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip, port, DEFAULT_CLUSTER,
                "{\"scene\":\"health-error\"}", "1.0", "true", "true", "true");
        addCleanup(() -> deleteServiceQuietly(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE));
        addCleanup(() -> deregisterInstanceQuietly(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip, port));
        waitUntilInstanceVisible(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, DEFAULT_CLUSTER, ip, port);

        assertError(putRaw(ADMIN_HEALTH_INSTANCE_PATH, healthQuery(serviceName, DEFAULT_GROUP,
                DEFAULT_NAMESPACE, ip, port, DEFAULT_CLUSTER, "false")), 400,
                ErrorCode.SERVER_ERROR, "health check is still working");
    }
}
