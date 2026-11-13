/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.api.ai.model.hub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exact ID serialization and safe transfer response contract. */
class HubSkillTransferContractTest {
    
    @Test
    void resultPreservesLargeIdsAndOmitsSourceInternals() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HubSkillTransferResult result = new HubSkillTransferResult();
        result.setHubEntryId(Long.MAX_VALUE);
        result.setTargetResourceId(9007199254740993L);
        result.setTargetName("new-skill");
        JsonNode json = mapper.readTree(mapper.writeValueAsString(result));
        assertTrue(json.get("hubEntryId").isTextual());
        assertEquals("9223372036854775807", json.get("hubEntryId").asText());
        assertEquals("9007199254740993", json.get("targetResourceId").asText());
        for (String key : new String[] {"sourceNamespaceId", "sourceResourceId", "sourceVersionId",
            "storage", "ext"}) {
            assertFalse(json.has(key));
        }
        assertEquals(Long.MAX_VALUE,
            mapper.treeToValue(json, HubSkillTransferResult.class).getHubEntryId());
    }
    
    @Test
    void requestSerializesEntryIdAsStringLikeExistingHubRequests() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HubSkillTransferRequest request = new HubSkillTransferRequest();
        request.setHubEntryId(Long.MAX_VALUE);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(request));
        assertTrue(json.get("hubEntryId").isTextual());
        assertEquals("9223372036854775807", json.get("hubEntryId").asText());
        assertEquals(Long.MAX_VALUE,
            mapper.treeToValue(json, HubSkillTransferRequest.class).getHubEntryId());
    }
    
}
