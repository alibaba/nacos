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

package com.alibaba.nacos.test.adminapi.core;

import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.http.param.Query;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for core cluster admin OpenAPI {@code /nacos/v3/admin/core/cluster}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: self and node list expose current member state, and address/state filters preserve the
 *     member view contract.</li>
 *     <li>Boundary/validation: state filter is case-insensitive for legal values; illegal states, empty node-update
 *     bodies, and missing lookup type are rejected.</li>
 *     <li>Exception/error handling: topology mutation success paths are intentionally not executed because they alter
 *     cluster membership; validation failures are verified to return controlled v3 error envelopes.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class CoreClusterAdminApiOpenApiITCase extends CoreAdminApiBaseITCase {

    @Test
    public void testSelfAndNodeListExposeCurrentMemberState() throws Exception {
        JsonNode self = getJsonOk(ADMIN_CORE_CLUSTER_PATH + "/node/self", Query.newInstance()).get("data");
        assertTrue(self.get("address").asText().contains(":"), self.toString());
        assertTrue(self.get("state").asText().length() > 0, self.toString());

        JsonNode allNodes = getJsonOk(ADMIN_CORE_CLUSTER_PATH + "/node/list", Query.newInstance()).get("data");
        assertTrue(allNodes.size() >= 1, allNodes.toString());
        assertFalse(findMember(allNodes, self.get("address").asText()).isMissingNode(), allNodes.toString());

        JsonNode filteredByAddress = getJsonOk(ADMIN_CORE_CLUSTER_PATH + "/node/list",
                Query.newInstance().addParam("address", self.get("address").asText())).get("data");
        assertFalse(findMember(filteredByAddress, self.get("address").asText()).isMissingNode(),
                filteredByAddress.toString());

        JsonNode filteredByState = getJsonOk(ADMIN_CORE_CLUSTER_PATH + "/node/list",
                Query.newInstance().addParam("state", self.get("state").asText().toLowerCase())).get("data");
        assertFalse(findMember(filteredByState, self.get("address").asText()).isMissingNode(),
                filteredByState.toString());
    }

    @Test
    public void testClusterValidationReturnsBadRequest() throws Exception {
        assertError(getRaw(ADMIN_CORE_CLUSTER_PATH + "/node/list",
                Query.newInstance().addParam("state", "not-a-state")), 400,
                ErrorCode.ILLEGAL_STATE, "not-a-state");
        assertError(putJsonRaw(ADMIN_CORE_CLUSTER_PATH + "/node/list", Query.newInstance(), "[]"),
                400, ErrorCode.PARAMETER_MISSING, "nodes");
        assertError(putRaw(ADMIN_CORE_CLUSTER_PATH + "/lookup", Query.newInstance()),
                400, ErrorCode.PARAMETER_MISSING, "type");
    }

    private JsonNode findMember(JsonNode members, String address) {
        for (JsonNode each : members) {
            if (address.equals(each.get("address").asText())) {
                return each;
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }
}
