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

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for console naming instance OpenAPI {@code /nacos/v3/console/ns/instance}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: list returns persistent instances in the console page model, update changes metadata,
 *     weight and enabled state, accepts the healthy parameter while leaving persistent health server-controlled, and
 *     delete removes the persistent instance. The instance setup uses admin register API because console exposes no
 *     register endpoint.</li>
 *     <li>Boundary/validation: omitted namespace/group default through setup, pageNo/pageSize must be positive,
 *     required {@code serviceName}/{@code ip}/{@code port} are enforced, invalid weight is rejected, and console
 *     delete rejects {@code ephemeral=true}. The custom cluster filter is intentionally not used before cluster
 *     metadata exists because the console list API reports such filters as a controlled 404.</li>
 *     <li>Exception/error handling: invalid filters and delete/update validation return HTTP 400 with the v3
 *     {@code Result} envelope instead of HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class InstanceConsoleApiOpenApiITCase extends NamingConsoleApiBaseITCase {

    private static final String TEST_CLUSTER = "openapi-it-console-instance-cluster";

    @Test
    public void testListUpdateAndDeletePersistentInstance() throws Exception {
        String serviceName = randomServiceName("instance");
        String ip = "10.23.0.1";
        int port = 20301;

        registerPersistentInstanceForSetup(serviceName, null, null, ip, port, TEST_CLUSTER,
                "{\"source\":\"console-it\"}", "2.5", "true", "true");
        addCleanup(() -> deleteServiceQuietly(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE));
        addCleanup(() -> removePersistentInstanceQuietly(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip, port,
                TEST_CLUSTER));

        JsonNode listed = waitUntilInstanceVisible(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, TEST_CLUSTER,
                ip, port);
        assertInstance(listed, serviceName, DEFAULT_GROUP, ip, port, TEST_CLUSTER, "source", "console-it");
        assertInstanceState(listed, 2.5D, true, true, false);

        JsonNode page = listInstances(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, null, 1, 1);
        assertPageShape(page);
        assertEquals(1, page.get("pageItems").size(), page.toString());
        assertFalse(findInstance(page, ip, port).isMissingNode(), page.toString());

        JsonNode update = putFormOk(CONSOLE_INSTANCE_PATH,
                instanceQuery(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip, port, TEST_CLUSTER)
                        .addParam("metadata", "{\"source\":\"updated\",\"version\":\"console\"}")
                        .addParam("weight", "3.5").addParam("healthy", "false")
                        .addParam("enabled", "false").addParam("ephemeral", "false"));
        assertEquals("ok", update.get("data").asText(), update.toString());

        JsonNode updated = waitUntilInstanceMetadata(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, TEST_CLUSTER,
                ip, port, "source", "updated");
        assertInstance(updated, serviceName, DEFAULT_GROUP, ip, port, TEST_CLUSTER, "source", "updated");
        assertEquals("console", updated.get("metadata").get("version").asText(), updated.toString());
        assertInstanceState(updated, 3.5D, true, false, false);

        JsonNode deleted = deleteJsonOk(CONSOLE_INSTANCE_PATH,
                instanceQuery(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, ip, port, TEST_CLUSTER));
        assertEquals("ok", deleted.get("data").asText(), deleted.toString());
        waitUntilInstanceAbsent(serviceName, DEFAULT_GROUP, DEFAULT_NAMESPACE, TEST_CLUSTER, ip, port);
    }

    @Test
    public void testInstanceValidationReturnsBadRequest() throws Exception {
        assertError(getRaw(CONSOLE_INSTANCE_LIST_PATH, Query.newInstance()), 400,
                ErrorCode.PARAMETER_MISSING, "serviceName");
        assertError(getRaw(CONSOLE_INSTANCE_LIST_PATH,
                instanceListQuery(randomServiceName("page"), DEFAULT_GROUP, DEFAULT_NAMESPACE,
                        TEST_CLUSTER, 0, 10)), 400, ErrorCode.PARAMETER_VALIDATE_ERROR, "pageNo");
        assertError(putRaw(CONSOLE_INSTANCE_PATH, Query.newInstance().addParam("ip", "10.23.0.2")
                .addParam("port", "20302")), 400, ErrorCode.PARAMETER_MISSING, "serviceName");
        assertError(putRaw(CONSOLE_INSTANCE_PATH, Query.newInstance().addParam("serviceName",
                randomServiceName("missing-ip")).addParam("port", "20303")), 400,
                ErrorCode.PARAMETER_MISSING, "ip");
        assertError(putRaw(CONSOLE_INSTANCE_PATH, Query.newInstance().addParam("serviceName",
                randomServiceName("missing-port")).addParam("ip", "10.23.0.4")), 400,
                ErrorCode.PARAMETER_MISSING, "port");
        assertError(putRaw(CONSOLE_INSTANCE_PATH, instanceQuery(randomServiceName("bad-weight"),
                DEFAULT_GROUP, DEFAULT_NAMESPACE, "10.23.0.5", 20305).addParam("weight", "10001")),
                400, ErrorCode.WEIGHT_ERROR, "weights range");
        assertError(deleteRaw(CONSOLE_INSTANCE_PATH, instanceQuery(randomServiceName("delete-ephemeral"),
                DEFAULT_GROUP, DEFAULT_NAMESPACE, "10.23.0.6", 20306).addParam("ephemeral", "true")),
                400, ErrorCode.PARAMETER_VALIDATE_ERROR, "persistent instances");
        assertError(deleteRaw(CONSOLE_INSTANCE_PATH, Query.newInstance().addParam("serviceName",
                randomServiceName("delete-missing-ip")).addParam("port", "20307")),
                400, ErrorCode.PARAMETER_MISSING, "ip");
    }
}
