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

package com.alibaba.nacos.test.consoleapi.core;

import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for console cluster OpenAPI {@code GET /nacos/v3/console/core/cluster/nodes}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: node list exposes current Nacos members and their address/state metadata.</li>
 *     <li>Boundary/validation: omitted keyword returns the full member view; keyword filtering accepts address
 *     fragments and unknown keywords return a successful empty collection.</li>
 *     <li>Exception/error handling: this read-only endpoint has no controller-level required parameters or mutation
 *     branch; the test verifies that filtering returns a controlled success envelope rather than HTTP 500.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ClusterConsoleApiOpenApiITCase extends CoreConsoleApiBaseITCase {

    @Test
    public void testNodeListAndKeywordFiltering() throws Exception {
        JsonNode nodes = getJsonOk(CONSOLE_CLUSTER_NODES_PATH, Query.newInstance()).get("data");
        assertTrue(nodes.isArray(), nodes.toString());
        assertTrue(nodes.size() >= 1, nodes.toString());

        JsonNode firstNode = nodes.get(0);
        String address = firstNode.get("address").asText();
        assertTrue(address.contains(":"), firstNode.toString());
        assertTrue(firstNode.get("state").asText().length() > 0, firstNode.toString());

        JsonNode filtered = getJsonOk(CONSOLE_CLUSTER_NODES_PATH,
                Query.newInstance().addParam("keyword", address.substring(0, address.indexOf(':'))))
                .get("data");
        assertFalse(findByTextField(filtered, "address", address).isMissingNode(), filtered.toString());

        JsonNode noMatch = getJsonOk(CONSOLE_CLUSTER_NODES_PATH,
                Query.newInstance().addParam("keyword", "no-such-node-" + randomConsoleName("cluster")))
                .get("data");
        assertEquals(0, noMatch.size(), noMatch.toString());
    }
}
