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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JSON contract tests for Hub sharing DTOs.
 *
 * @author nacos
 */
class HubShareContractTest {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Test
    void largeIdsAreStringsInJson() throws Exception {
        HubShareResult result = new HubShareResult();
        result.setHubEntryId(Long.MAX_VALUE);
        result.setSourceResourceId(101L);
        result.setSourceVersionId(501L);
        result.setMetaVersion(2L);
        result.setSourceName("find-skills");
        result.setOutcome(HubShareOutcome.CREATED);
        result.setActive(true);
        
        String json = mapper.writeValueAsString(result);
        assertTrue(json.contains("\"hubEntryId\":\"9223372036854775807\""));
        assertTrue(json.contains("\"sourceResourceId\":\"101\""));
        assertTrue(json.contains("\"sourceVersionId\":\"501\""));
        assertTrue(json.contains("\"metaVersion\":\"2\""));
        
        HubShareResult roundTrip = mapper.readValue(json, HubShareResult.class);
        assertEquals(Long.MAX_VALUE, roundTrip.getHubEntryId());
        assertEquals("find-skills", roundTrip.getSourceName());
        assertEquals(HubShareOutcome.CREATED, roundTrip.getOutcome());
        assertTrue(roundTrip.isActive());
    }
    
    @Test
    void requestsRoundTripWithIdStrings() throws Exception {
        HubAdjustShareRequest request = mapper.readValue(
            "{\"hubEntryId\":\"305\",\"expectedMetaVersion\":\"7\","
                + "\"targetVersion\":\"2.0.0\"}",
            HubAdjustShareRequest.class);
        assertEquals(305L, request.getHubEntryId());
        assertEquals(7L, request.getExpectedMetaVersion());
        assertEquals("2.0.0", request.getTargetVersion());
    }
}
