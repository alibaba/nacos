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

package com.alibaba.nacos.api.ai.model.rad;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWatchBatchModelsTest {
    
    @Test
    void testWatchBatchModels() {
        AgentDiscoveryRequest discoveryRequest = new AgentDiscoveryRequest();
        AgentWatchBatchItem item = new AgentWatchBatchItem();
        item.setClientWatchId("watch-1");
        item.setDiscoveryRequest(discoveryRequest);
        item.setMaterializedFingerprint("fingerprint");
        assertEquals("watch-1", item.getClientWatchId());
        assertSame(discoveryRequest, item.getDiscoveryRequest());
        assertEquals("fingerprint", item.getMaterializedFingerprint());
        
        AgentWatchBatchRequest request = new AgentWatchBatchRequest();
        request.setGeneration(7L);
        request.setTimeoutMillis(30000L);
        request.setWatches(Collections.singletonList(item));
        assertEquals(7L, request.getGeneration());
        assertEquals(30000L, request.getTimeoutMillis());
        assertSame(item, request.getWatches().get(0));
        
        AgentWatchBatchResponse response = new AgentWatchBatchResponse();
        response.setGeneration(7L);
        response.setChanged(false);
        assertEquals(7L, response.getGeneration());
        assertFalse(response.isChanged());
        response.setChanged(true);
        response.setChangedClientWatchIds(Collections.singletonList("watch-1"));
        assertTrue(response.isChanged());
        assertEquals(Collections.singletonList("watch-1"),
            response.getChangedClientWatchIds());
    }
}
