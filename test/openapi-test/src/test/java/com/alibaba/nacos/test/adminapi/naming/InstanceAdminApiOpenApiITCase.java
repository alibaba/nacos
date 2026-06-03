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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for naming instance admin OpenAPI {@code /nacos/v3/admin/ns/instance}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: register makes an instance visible through detail/list, full update and partial update
 *     change operational metadata/weight/enabled fields, and delete removes the instance from the admin list.</li>
 *     <li>Boundary/validation: omitted namespace/group/cluster default to public, DEFAULT_GROUP, and DEFAULT;
 *     explicit group/cluster isolate list results; {@code healthyOnly} filters unhealthy instances; an absent cluster
 *     filter returns a controlled 404; required {@code serviceName}, {@code ip}, and {@code port}, invalid weight, and
 *     invalid cluster names are rejected.</li>
 *     <li>Exception/error handling: querying a missing instance returns a controlled RESOURCE_NOT_FOUND envelope, and
 *     registering an ephemeral instance into a persistent service is rejected instead of changing service type; the
 *     current deployed contract wraps this runtime branch as SERVER_ERROR data with HTTP 400.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class InstanceAdminApiOpenApiITCase extends NamingAdminApiBaseITCase {
    
    private static final String TEST_CLUSTER = "openapi-it-admin-instance-cluster";
    
    @Test
    public void testRegisterWithDefaultsDetailUpdatePartialUpdateAndDelete() throws Exception {
        String serviceName = randomServiceName("instance");
        String ip = "10.13.0.1";
        int port = 19301;
        
        registerInstance(serviceName, null, null, ip, port, null, "{\"source\":\"admin-it\"}",
                "2.5", "true", "true", "true");
        addCleanup(() -> deleteServiceQuietly(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE));
        addCleanup(() -> deregisterInstanceQuietly(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip, port));
        
        JsonNode registered = waitUntilInstanceVisible(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE,
                DEFAULT_CLUSTER, ip, port);
        assertInstance(registered, serviceName, DEFAULT_GROUP, ip, port, DEFAULT_CLUSTER, "source", "admin-it");
        assertInstanceState(registered, 2.5D, true, true, true);
        
        JsonNode detail = getInstanceDetail(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip, port,
                DEFAULT_CLUSTER);
        assertInstance(detail, serviceName, DEFAULT_GROUP, ip, port, DEFAULT_CLUSTER, "source", "admin-it");
        
        updateInstance(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip, port, DEFAULT_CLUSTER,
                "{\"source\":\"updated\",\"version\":\"full\"}", "3.5", null, "false", "true");
        JsonNode updated = waitUntilInstanceMetadata(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE,
                DEFAULT_CLUSTER, ip, port, "source", "updated");
        assertInstance(updated, serviceName, DEFAULT_GROUP, ip, port, DEFAULT_CLUSTER, "source", "updated");
        assertEquals("full", updated.get("metadata").get("version").asText(), updated.toString());
        assertInstanceState(updated, 3.5D, true, false, true);
        
        partialUpdateInstance(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip, port, DEFAULT_CLUSTER,
                "{\"source\":\"partial\"}", "4.5", "true");
        JsonNode partialUpdated = waitUntilInstanceMetadata(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE,
                DEFAULT_CLUSTER, ip, port, "source", "partial");
        assertInstance(partialUpdated, serviceName, DEFAULT_GROUP, ip, port, DEFAULT_CLUSTER, "source",
                "partial");
        assertInstanceState(partialUpdated, 4.5D, true, true, true);
        partialUpdated = waitUntilInstanceMetadataMissing(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE,
                DEFAULT_CLUSTER, ip, port, "version");
        assertInstanceState(partialUpdated, 4.5D, true, true, true);
        
        JsonNode deleted = deleteJsonOk(ADMIN_INSTANCE_PATH,
                instanceQuery(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip, port, DEFAULT_CLUSTER));
        assertEquals("ok", deleted.get("data").asText(), deleted.toString());
        waitUntilInstanceAbsent(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, DEFAULT_CLUSTER, ip, port);
    }
    
    @Test
    public void testListFiltersByClusterAndHealthyOnly() throws Exception {
        String serviceName = randomServiceName("instance-list");
        String groupName = randomGroupName("instance-list");
        String healthyIp = "10.13.0.2";
        String unhealthyIp = "10.13.0.3";
        int healthyPort = 19302;
        int unhealthyPort = 19303;
        
        registerInstance(serviceName, groupName, DEFAULT_NAMESPACE, healthyIp, healthyPort, TEST_CLUSTER,
                "{\"role\":\"healthy\"}", "1.0", "true", "true", "true");
        registerInstance(serviceName, groupName, DEFAULT_NAMESPACE, unhealthyIp, unhealthyPort, TEST_CLUSTER,
                "{\"role\":\"unhealthy\"}", "1.0", "false", "true", "true");
        addCleanup(() -> deleteServiceQuietly(serviceName, groupName, DEFAULT_NAMESPACE));
        addCleanup(() -> deregisterInstanceQuietly(serviceName, groupName, DEFAULT_NAMESPACE, healthyIp,
                healthyPort, TEST_CLUSTER));
        addCleanup(() -> deregisterInstanceQuietly(serviceName, groupName, DEFAULT_NAMESPACE, unhealthyIp,
                unhealthyPort, TEST_CLUSTER));
        
        waitUntilInstanceVisible(serviceName, groupName, DEFAULT_NAMESPACE, TEST_CLUSTER, healthyIp, healthyPort);
        waitUntilInstanceVisible(serviceName, groupName, DEFAULT_NAMESPACE, TEST_CLUSTER, unhealthyIp,
                unhealthyPort);
        
        JsonNode all = listInstances(serviceName, groupName, DEFAULT_NAMESPACE, TEST_CLUSTER, "false");
        assertFalse(findInstance(all, healthyIp, healthyPort).isMissingNode(), all.toString());
        assertFalse(findInstance(all, unhealthyIp, unhealthyPort).isMissingNode(), all.toString());
        
        JsonNode healthyOnly = listInstances(serviceName, groupName, DEFAULT_NAMESPACE, TEST_CLUSTER, "true");
        assertFalse(findInstance(healthyOnly, healthyIp, healthyPort).isMissingNode(), healthyOnly.toString());
        assertTrue(findInstance(healthyOnly, unhealthyIp, unhealthyPort).isMissingNode(), healthyOnly.toString());
        
        assertError(getRaw(ADMIN_INSTANCE_LIST_PATH, instanceListQuery(serviceName, groupName,
                DEFAULT_NAMESPACE, "other-cluster", "false")), 404, ErrorCode.SERVER_ERROR,
                "cluster other-cluster is not found");
    }
    
    @Test
    public void testInstanceValidationReturnsBadRequest() throws Exception {
        assertError(postRaw(ADMIN_INSTANCE_PATH, Query.newInstance().addParam("ip", "10.13.0.4")
                .addParam("port", "19304")), 400, ErrorCode.PARAMETER_MISSING, "serviceName");
        assertError(postRaw(ADMIN_INSTANCE_PATH, Query.newInstance().addParam("serviceName",
                randomServiceName("missing-ip")).addParam("port", "19305")), 400,
                ErrorCode.PARAMETER_MISSING, "ip");
        assertError(postRaw(ADMIN_INSTANCE_PATH, Query.newInstance().addParam("serviceName",
                randomServiceName("missing-port")).addParam("ip", "10.13.0.6")), 400,
                ErrorCode.PARAMETER_MISSING, "port");
        assertError(postRaw(ADMIN_INSTANCE_PATH, instanceQuery(randomServiceName("bad-weight"),
                DEFAULT_GROUP, DEFAULT_NAMESPACE, "10.13.0.7", 19307).addParam("weight", "10001")),
                400, ErrorCode.WEIGHT_ERROR, "weights range");
        assertError(postRaw(ADMIN_INSTANCE_PATH, instanceQuery(randomServiceName("bad-cluster"),
                DEFAULT_GROUP, DEFAULT_NAMESPACE, "10.13.0.8", 19308, "cluster1,cluster2")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "cluster");
        assertError(getRaw(ADMIN_INSTANCE_LIST_PATH, Query.newInstance()), 400,
                ErrorCode.PARAMETER_MISSING, "serviceName");
    }
    
    @Test
    public void testMissingAndMismatchedInstanceReturnControlledErrors() throws Exception {
        String missingServiceName = randomServiceName("missing-instance");
        assertError(getRaw(ADMIN_INSTANCE_PATH, instanceQuery(missingServiceName, DEFAULT_GROUP,
                DEFAULT_NAMESPACE, "10.13.0.9", 19309)), 404, ErrorCode.RESOURCE_NOT_FOUND,
                "no ips found");
        
        String persistentServiceName = randomServiceName("persistent-service");
        createService(persistentServiceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, "{\"type\":\"persistent\"}",
                "0.1");
        addCleanup(() -> deleteServiceQuietly(persistentServiceName, DEFAULT_GROUP, DEFAULT_NAMESPACE));
        assertError(postRaw(ADMIN_INSTANCE_PATH, instanceQuery(persistentServiceName, DEFAULT_GROUP,
                DEFAULT_NAMESPACE, "10.13.0.10", 19310).addParam("ephemeral", "true")),
                400, ErrorCode.SERVER_ERROR, "persistent service");
    }
}
