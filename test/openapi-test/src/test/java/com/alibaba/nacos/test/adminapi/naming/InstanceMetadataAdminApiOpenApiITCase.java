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
 * Integration tests for naming instance metadata batch admin OpenAPI
 * {@code /nacos/v3/admin/ns/instance/metadata/batch}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: batch update overlays operational metadata on all matching instances, and batch delete
 *     removes selected metadata keys from selected instances while leaving other instances untouched.</li>
 *     <li>Boundary/validation: omitted {@code instances} means all instances of the service; an instance selector
 *     without {@code clusterName} defaults to DEFAULT; {@code serviceName} and {@code metadata} are required. The
 *     controller's malformed-JSON no-op branch is not covered here because the deployed HTTP parameter extractor
 *     rejects malformed {@code instances} before the controller is invoked.</li>
 *     <li>Exception/error handling: required-field failures return HTTP 400 with the v3 {@code Result} envelope, and
 *     required-field failures return HTTP 400 with the v3 {@code Result} envelope.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class InstanceMetadataAdminApiOpenApiITCase extends NamingAdminApiBaseITCase {
    
    @Test
    public void testBatchUpdateAndDeleteInstanceMetadata() throws Exception {
        String serviceName = randomServiceName("instance-metadata");
        String groupName = randomGroupName("instance-metadata");
        String firstIp = "10.13.1.1";
        String secondIp = "10.13.1.2";
        int firstPort = 19401;
        int secondPort = 19402;
        
        registerInstance(serviceName, groupName, DEFAULT_NAMESPACE, firstIp, firstPort,
                "{\"role\":\"first\"}", "1.0");
        registerInstance(serviceName, groupName, DEFAULT_NAMESPACE, secondIp, secondPort,
                "{\"role\":\"second\"}", "1.0");
        addCleanup(() -> deleteServiceQuietly(serviceName, groupName, DEFAULT_NAMESPACE));
        addCleanup(() -> deregisterInstanceQuietly(serviceName, groupName, DEFAULT_NAMESPACE, firstIp,
                firstPort));
        addCleanup(() -> deregisterInstanceQuietly(serviceName, groupName, DEFAULT_NAMESPACE, secondIp,
                secondPort));
        
        waitUntilInstanceVisible(serviceName, groupName, DEFAULT_NAMESPACE, DEFAULT_CLUSTER, firstIp,
                firstPort);
        waitUntilInstanceVisible(serviceName, groupName, DEFAULT_NAMESPACE, DEFAULT_CLUSTER, secondIp,
                secondPort);
        
        JsonNode updated = putFormOk(ADMIN_INSTANCE_METADATA_BATCH_PATH,
                metadataBatchQuery(serviceName, groupName, DEFAULT_NAMESPACE, "{\"batch\":\"added\"}",
                        null, "ephemeral"));
        assertEquals(2, updated.get("data").get("updated").size(), updated.toString());
        assertTrue(updated.get("data").get("updated").toString().contains(firstIp), updated.toString());
        assertTrue(updated.get("data").get("updated").toString().contains(secondIp), updated.toString());
        
        JsonNode firstWithBatch = waitUntilInstanceMetadata(serviceName, groupName, DEFAULT_NAMESPACE,
                DEFAULT_CLUSTER, firstIp, firstPort, "batch", "added");
        JsonNode secondWithBatch = waitUntilInstanceMetadata(serviceName, groupName, DEFAULT_NAMESPACE,
                DEFAULT_CLUSTER, secondIp, secondPort, "batch", "added");
        assertEquals("added", firstWithBatch.get("metadata").get("batch").asText(), firstWithBatch.toString());
        assertEquals("added", secondWithBatch.get("metadata").get("batch").asText(), secondWithBatch.toString());
        
        String selectedInstance = "[{\"ip\":\"" + firstIp + "\",\"port\":" + firstPort + "}]";
        JsonNode deleted = deleteJsonOk(ADMIN_INSTANCE_METADATA_BATCH_PATH,
                metadataBatchQuery(serviceName, groupName, DEFAULT_NAMESPACE, "{\"batch\":\"\"}",
                        selectedInstance, "ephemeral"));
        assertEquals(1, deleted.get("data").get("updated").size(), deleted.toString());
        assertTrue(deleted.get("data").get("updated").toString().contains(firstIp), deleted.toString());
        
        JsonNode firstAfterDelete = waitUntilInstanceMetadataMissing(serviceName, groupName, DEFAULT_NAMESPACE,
                DEFAULT_CLUSTER, firstIp, firstPort, "batch");
        JsonNode secondAfterDelete = waitUntilInstanceMetadata(serviceName, groupName, DEFAULT_NAMESPACE,
                DEFAULT_CLUSTER, secondIp, secondPort, "batch", "added");
        assertFalse(firstAfterDelete.get("metadata").has("batch"), firstAfterDelete.toString());
        assertEquals("added", secondAfterDelete.get("metadata").get("batch").asText(),
                secondAfterDelete.toString());
    }
    
    @Test
    public void testBatchMetadataValidationReturnsBadRequest() throws Exception {
        assertError(putRaw(ADMIN_INSTANCE_METADATA_BATCH_PATH,
                Query.newInstance().addParam("metadata", "{\"batch\":\"missing-service\"}")),
                400, ErrorCode.PARAMETER_MISSING, "serviceName");
        assertError(putRaw(ADMIN_INSTANCE_METADATA_BATCH_PATH,
                Query.newInstance().addParam("serviceName", randomServiceName("missing-metadata"))),
                400, ErrorCode.PARAMETER_MISSING, "metadata");
        assertError(deleteRaw(ADMIN_INSTANCE_METADATA_BATCH_PATH,
                Query.newInstance().addParam("metadata", "{\"batch\":\"missing-service\"}")),
                400, ErrorCode.PARAMETER_MISSING, "serviceName");
        assertError(deleteRaw(ADMIN_INSTANCE_METADATA_BATCH_PATH,
                Query.newInstance().addParam("serviceName", randomServiceName("missing-metadata"))),
                400, ErrorCode.PARAMETER_MISSING, "metadata");
    }
}
