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

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for naming client admin OpenAPI {@code /nacos/v3/admin/ns/client}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: an HTTP registered instance creates an ip:port client visible in client list/detail,
 *     client publish list, service publisher list, empty subscribe lists, and distro responsible-server query.</li>
 *     <li>Boundary/validation: service publisher/subscriber queries respect namespace/group isolation, omitted
 *     namespace/group use public and DEFAULT_GROUP, optional ip/port filters narrow publisher results, and
 *     {@code serviceName} is required for service-scoped queries.</li>
 *     <li>Exception/error handling: missing clients return RESOURCE_NOT_FOUND with HTTP 404, and required-field
 *     failures return HTTP 400 in the v3 {@code Result} envelope.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ClientAdminApiOpenApiITCase extends NamingAdminApiBaseITCase {

    private static final String TEST_CLUSTER = "openapi-it-admin-client-cluster";

    @Test
    public void testClientDiagnosticsForRegisteredHttpInstance() throws Exception {
        String serviceName = randomServiceName("client");
        String groupName = randomGroupName("client");
        String ip = "10.13.4.1";
        int port = 19701;
        String clientId = ip + ":" + port + "#true";

        registerInstance(serviceName, groupName, DEFAULT_NAMESPACE, ip, port, TEST_CLUSTER,
                "{\"scene\":\"client\"}", "1.0", "true", "true", "true");
        addCleanup(() -> deleteServiceQuietly(serviceName, groupName, DEFAULT_NAMESPACE));
        addCleanup(() -> deregisterInstanceQuietly(serviceName, groupName, DEFAULT_NAMESPACE, ip, port,
                TEST_CLUSTER));

        waitUntilInstanceVisible(serviceName, groupName, DEFAULT_NAMESPACE, TEST_CLUSTER, ip, port);
        waitUntilClientVisible(clientId);

        JsonNode detail = getJsonOk(ADMIN_CLIENT_PATH, Query.newInstance().addParam("clientId", clientId))
                .get("data");
        assertEquals(clientId, detail.get("clientId").asText(), detail.toString());
        assertEquals("ipPort", detail.get("clientType").asText(), detail.toString());
        assertTrue(detail.get("ephemeral").asBoolean(), detail.toString());

        JsonNode publishedServices = getJsonOk(ADMIN_CLIENT_PATH + "/publish/list",
                Query.newInstance().addParam("clientId", clientId)).get("data");
        JsonNode publishedService = findClientService(publishedServices, serviceName, groupName);
        assertFalse(publishedService.isMissingNode(), publishedServices.toString());
        assertEquals(ip, publishedService.get("publisherInfo").get("ip").asText(), publishedService.toString());
        assertEquals(port, publishedService.get("publisherInfo").get("port").asInt(), publishedService.toString());
        assertEquals(TEST_CLUSTER, publishedService.get("publisherInfo").get("clusterName").asText(),
                publishedService.toString());

        JsonNode publishedClients = getJsonOk(ADMIN_CLIENT_PATH + "/service/publisher/list",
                clientServiceQuery(serviceName, groupName, DEFAULT_NAMESPACE, ip, port)).get("data");
        assertEquals(1, publishedClients.size(), publishedClients.toString());
        assertEquals(clientId, publishedClients.get(0).get("clientId").asText(), publishedClients.toString());

        JsonNode filteredOut = getJsonOk(ADMIN_CLIENT_PATH + "/service/publisher/list",
                clientServiceQuery(serviceName, groupName, DEFAULT_NAMESPACE, ip, port + 1)).get("data");
        assertEquals(0, filteredOut.size(), filteredOut.toString());
        JsonNode defaultGroupPublishers = getJsonOk(ADMIN_CLIENT_PATH + "/service/publisher/list",
                clientServiceQuery(serviceName, null, null, null, null)).get("data");
        assertEquals(0, defaultGroupPublishers.size(), defaultGroupPublishers.toString());

        JsonNode clientSubscribeList = getJsonOk(ADMIN_CLIENT_PATH + "/subscribe/list",
                Query.newInstance().addParam("clientId", clientId)).get("data");
        assertEquals(0, clientSubscribeList.size(), clientSubscribeList.toString());
        JsonNode serviceSubscriberList = getJsonOk(ADMIN_CLIENT_PATH + "/service/subscriber/list",
                clientServiceQuery(serviceName, groupName, DEFAULT_NAMESPACE, null, null)).get("data");
        assertEquals(0, serviceSubscriberList.size(), serviceSubscriberList.toString());

        JsonNode distro = getJsonOk(ADMIN_CLIENT_PATH + "/distro",
                Query.newInstance().addParam("ip", ip).addParam("port", String.valueOf(port))).get("data");
        assertTrue(distro.get("responsibleServer").asText().length() > 0, distro.toString());
    }

    @Test
    public void testClientValidationAndNotFoundReturnControlledErrors() throws Exception {
        assertError(getRaw(ADMIN_CLIENT_PATH, Query.newInstance().addParam("clientId", "missing-client")),
                404, ErrorCode.RESOURCE_NOT_FOUND, "missing-client");
        assertError(getRaw(ADMIN_CLIENT_PATH + "/publish/list",
                Query.newInstance().addParam("clientId", "missing-client")), 404,
                ErrorCode.RESOURCE_NOT_FOUND, "missing-client");
        assertError(getRaw(ADMIN_CLIENT_PATH + "/service/publisher/list", Query.newInstance()),
                400, ErrorCode.PARAMETER_MISSING, "serviceName");
        assertError(getRaw(ADMIN_CLIENT_PATH + "/service/subscriber/list", Query.newInstance()),
                400, ErrorCode.PARAMETER_MISSING, "serviceName");
    }

    private void waitUntilClientVisible(String clientId) throws Exception {
        JsonNode clients = null;
        int retryTime = 20;
        while (retryTime-- > 0) {
            clients = getJsonOk(ADMIN_CLIENT_PATH + "/list", Query.newInstance()).get("data");
            for (JsonNode each : clients) {
                if (clientId.equals(each.asText())) {
                    return;
                }
            }
            TimeUnit.MILLISECONDS.sleep(100);
        }
        throw new AssertionError("Expected client to be visible, clientId=" + clientId + ", last clients="
                + clients);
    }

    private JsonNode findClientService(JsonNode services, String serviceName, String groupName) {
        for (JsonNode each : services) {
            if (serviceName.equals(each.get("serviceName").asText())
                    && groupName.equals(each.get("groupName").asText())) {
                return each;
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }
}
