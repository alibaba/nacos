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

class HubResourceDiscoveryInfoTest {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Test
    void sourceCoordinateSerializationIsGatedByTheIndependentReadResult() throws Exception {
        HubResourceDiscoveryInfo info = new HubResourceDiscoveryInfo();
        info.setSourceNamespaceId("team-a");
        info.setSourceName("source-resource");
        JsonNode denied = mapper.readTree(mapper.writeValueAsString(info));
        assertFalse(denied.get("sourceReadable").asBoolean());
        assertFalse(denied.has("sourceNamespaceId"));
        assertFalse(denied.has("sourceName"));
        info.setSourceReadable(true);
        JsonNode allowed = mapper.readTree(mapper.writeValueAsString(info));
        assertEquals("team-a", allowed.get("sourceNamespaceId").asText());
        assertEquals("source-resource", allowed.get("sourceName").asText());
        info.setSourceReadable(false);
        assertFalse(mapper.readTree(mapper.writeValueAsString(info)).has("sourceNamespaceId"));
    }
    
    @Test
    void timestampIsNumericWhileOpaqueIdentityKeepsItsStringContract() throws Exception {
        HubResourceDiscoveryInfo info = new HubResourceDiscoveryInfo();
        info.setHubEntryId(Long.MAX_VALUE);
        info.setUpdateTime(1700000000000L);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(info));
        assertEquals(Long.toString(Long.MAX_VALUE), json.get("hubEntryId").asText());
        assertTrue(json.get("hubEntryId").isTextual());
        assertTrue(json.get("updateTime").isIntegralNumber());
        assertEquals(1700000000000L, json.get("updateTime").asLong());
        HubResourceDiscoveryInfo decoded = mapper.readValue(mapper.writeValueAsString(info),
            HubResourceDiscoveryInfo.class);
        assertEquals(info.getUpdateTime(), decoded.getUpdateTime());
    }
    
    @Test
    void absentTimestampRemainsOptionalForOldOrIncompleteMetadata() throws Exception {
        JsonNode json = mapper.readTree(mapper.writeValueAsString(new HubResourceDiscoveryInfo()));
        assertFalse(json.has("updateTime"));
    }
}
