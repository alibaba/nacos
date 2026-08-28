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

package com.alibaba.nacos.ai.service.agent.watch;

import com.alibaba.nacos.api.ai.model.rad.AgentDiscoveryRequest;
import com.alibaba.nacos.api.ai.model.rad.AgentReference;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchItem;
import com.alibaba.nacos.api.ai.model.rad.AgentWatchBatchResponse;
import com.alibaba.nacos.api.model.v2.Result;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentHttpWatchWaiterTest {
    
    @Test
    void testChangedResponseContainsOnlyOpaqueIdsAndCleansOnce() {
        AtomicInteger cleanups = new AtomicInteger();
        AgentWatchBatchItem first = item("first", "agent-a", "fingerprint-a");
        AgentWatchBatchItem second = item("second", "agent-b", "fingerprint-b");
        AgentHttpWatchWaiter waiter = waiter(Arrays.asList(first, second), cleanups);
        Map<AgentProjectionKey, AgentProjectionState> states = new LinkedHashMap<>();
        states.put(AgentProjectionKey.of(first.getDiscoveryRequest()),
            AgentProjectionState.available("fingerprint-a", Collections.emptySet(), 1L));
        states.put(AgentProjectionKey.of(second.getDiscoveryRequest()),
            AgentProjectionState.available("fingerprint-c", Collections.emptySet(), 1L));
        
        assertTrue(waiter.completeIfChanged(states));
        AgentWatchBatchResponse response = result(waiter);
        assertEquals(9L, response.getGeneration());
        assertTrue(response.isChanged());
        assertEquals(Collections.singletonList("second"), response.getChangedClientWatchIds());
        assertEquals(1, cleanups.get());
        assertFalse(waiter.timeout());
        assertFalse(waiter.cancel());
        assertEquals(1, cleanups.get());
    }
    
    @Test
    void testUnchangedWaitsAndTimeoutReturnsNoDetails() {
        AtomicInteger cleanups = new AtomicInteger();
        AgentWatchBatchItem item = item("watch", "agent", "same");
        AgentHttpWatchWaiter waiter = waiter(Collections.singletonList(item), cleanups);
        Map<AgentProjectionKey, AgentProjectionState> states = Collections.singletonMap(
            AgentProjectionKey.of(item.getDiscoveryRequest()),
            AgentProjectionState.available("same", Collections.emptySet(), 1L));
        
        assertFalse(waiter.completeIfChanged(states));
        assertFalse(waiter.getDeferredResult().hasResult());
        assertTrue(waiter.timeout());
        AgentWatchBatchResponse response = result(waiter);
        assertFalse(response.isChanged());
        assertNull(response.getChangedClientWatchIds());
        assertEquals(1, cleanups.get());
    }
    
    @Test
    void testUnavailableAndSharedProjectionInvalidateMatchingIds() {
        AtomicInteger cleanups = new AtomicInteger();
        AgentWatchBatchItem first = item("one", "agent", "same");
        AgentWatchBatchItem second = item("two", "agent", "same");
        AgentHttpWatchWaiter waiter = waiter(Arrays.asList(first, second), cleanups);
        AgentProjectionKey key = AgentProjectionKey.of(first.getDiscoveryRequest());
        AgentProjectionState unavailable = AgentProjectionState.failure(
            AgentProjectionStatus.NOT_FOUND, 404, "hidden", 1L);
        
        assertTrue(waiter.completeIfChanged(key, unavailable));
        assertEquals(Arrays.asList("one", "two"), result(waiter).getChangedClientWatchIds());
        assertEquals(1, waiter.getProjectionKeys().size());
        assertTrue(waiter.isCompleted());
        assertNotNull(waiter.getWaiterId());
        assertEquals(2, waiter.getItemCount());
        assertEquals(27, waiter.getPayloadBytes());
        assertEquals(9L, waiter.getGeneration());
        assertNotNull(waiter.getOwnerKey());
    }
    
    @Test
    void testCancelWithoutResponseIsIdempotent() {
        AtomicInteger cleanups = new AtomicInteger();
        AgentHttpWatchWaiter waiter = waiter(
            Collections.singletonList(item("watch", "agent", "same")), cleanups);
        assertTrue(waiter.cancel());
        assertFalse(waiter.cancel());
        assertFalse(waiter.getDeferredResult().hasResult());
        assertEquals(1, cleanups.get());
    }
    
    @Test
    void testUnrelatedProjectionDoesNotCompleteWaiter() {
        AtomicInteger cleanups = new AtomicInteger();
        AgentHttpWatchWaiter waiter = waiter(
            Collections.singletonList(item("watch", "agent", "same")), cleanups);
        AgentProjectionKey unrelated =
            AgentProjectionKey.of(item("other", "other-agent", "same").getDiscoveryRequest());
        
        assertFalse(waiter.completeIfChanged(unrelated,
            AgentProjectionState.available("new", Collections.emptySet(), 1L)));
        assertFalse(waiter.getDeferredResult().hasResult());
        assertEquals(0, cleanups.get());
    }
    
    @SuppressWarnings("unchecked")
    private AgentWatchBatchResponse result(AgentHttpWatchWaiter waiter) {
        Result<AgentWatchBatchResponse> wrapped =
            (Result<AgentWatchBatchResponse>) waiter.getDeferredResult().getResult();
        return wrapped.getData();
    }
    
    private AgentHttpWatchWaiter waiter(java.util.List<AgentWatchBatchItem> items,
        AtomicInteger cleanups) {
        return new AgentHttpWatchWaiter(new AgentHttpWatchOwnerKey("client", "alice", "public"),
            9L, 1000L, items, 27, ignored -> cleanups.incrementAndGet());
    }
    
    private AgentWatchBatchItem item(String id, String agentName, String fingerprint) {
        AgentReference reference = new AgentReference();
        reference.setAgentName(agentName);
        AgentDiscoveryRequest discoveryRequest = new AgentDiscoveryRequest();
        discoveryRequest.setNamespaceId("public");
        discoveryRequest.setReference(reference);
        AgentWatchBatchItem result = new AgentWatchBatchItem();
        result.setClientWatchId(id);
        result.setDiscoveryRequest(discoveryRequest);
        result.setMaterializedFingerprint(fingerprint);
        return result;
    }
}
