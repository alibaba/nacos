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
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentHttpWatchRegistryTest {
    
    @Test
    void testReplacementSoftWatermarkAndCapacityReuse() throws Exception {
        AgentHttpWatchRegistry registry = new AgentHttpWatchRegistry();
        AgentHttpWatchOwnerKey owner = owner("client");
        AgentHttpWatchWaiter below = waiter(owner, 1L, 2, 20);
        assertNull(registry.register(below, 3, 10, 1000L).getReplaced());
        AgentHttpWatchWaiter crosses = waiter(owner, 2L, 4, 40);
        AgentHttpWatchRegistry.Registration replacement =
            registry.register(crosses, 3, 10, 1000L);
        assertSame(below, replacement.getReplaced());
        assertEquals(1, registry.size());
        assertEquals(40L, registry.activeBytes());
        
        NacosApiException rejected = assertThrows(NacosApiException.class,
            () -> registry.register(waiter(owner, 3L, 5, 50), 3, 10, 1000L));
        assertEquals(ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT.getCode(),
            rejected.getDetailErrCode());
        AgentHttpWatchWaiter equal = waiter(owner, 3L, 4, 30);
        assertSame(crosses, registry.register(equal, 3, 10, 1000L).getReplaced());
        assertSame(equal, registry.remove(equal.getWaiterId()));
        assertEquals(0, registry.size());
        assertEquals(0L, registry.activeBytes());
    }
    
    @Test
    void testStaleGenerationDoesNotReplaceCurrentWaiter() throws Exception {
        AgentHttpWatchRegistry registry = new AgentHttpWatchRegistry();
        AgentHttpWatchOwnerKey owner = owner("client");
        AgentHttpWatchWaiter current = waiter(owner, 5L, 1, 10);
        registry.register(current, 3, 10, 100L);
        AgentHttpWatchRegistry.Registration stale =
            registry.register(waiter(owner, 4L, 1, 10), 3, 10, 100L);
        assertTrue(stale.isStale());
        assertNull(stale.getReplaced());
        assertEquals(1, registry.size());
        assertSame(current,
            registry.findByProjection(current.getProjectionKeys().iterator().next()).get(0));
    }
    
    @Test
    void testNodeWaiterAndByteHardLimitsAreAtomic() throws Exception {
        AgentHttpWatchRegistry registry = new AgentHttpWatchRegistry();
        AgentHttpWatchWaiter first = waiter(owner("one"), 1L, 1, 60);
        registry.register(first, 3, 1, 100L);
        assertThrows(NacosApiException.class,
            () -> registry.register(waiter(owner("two"), 1L, 1, 10), 3, 1, 100L));
        assertThrows(NacosApiException.class,
            () -> registry.register(waiter(owner("one"), 2L, 1, 101), 3, 2, 100L));
        assertEquals(1, registry.size());
        assertEquals(60L, registry.activeBytes());
        assertSame(first, registry.clear().get(0));
        assertEquals(0, registry.size());
        assertEquals(0L, registry.activeBytes());
        assertTrue(registry.findByProjection(
            first.getProjectionKeys().iterator().next()).isEmpty());
        assertNull(registry.remove("missing"));
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testDefensiveRemovalToleratesMissingProjectionIndex() throws Exception {
        AgentHttpWatchRegistry registry = new AgentHttpWatchRegistry();
        AgentHttpWatchWaiter waiter = waiter(owner("client"), 1L, 1, 10);
        registry.register(waiter, 3, 10, 100L);
        Field field = AgentHttpWatchRegistry.class.getDeclaredField("projectionIndex");
        field.setAccessible(true);
        ((Map<AgentProjectionKey, ?>) field.get(registry)).clear();
        
        assertSame(waiter, registry.remove(waiter.getWaiterId()));
        assertEquals(0, registry.size());
        assertEquals(0L, registry.activeBytes());
    }
    
    @Test
    void testOwnerKeyValueEquality() {
        AgentHttpWatchOwnerKey owner = owner("client");
        AgentHttpWatchOwnerKey equal = owner("client");
        
        assertEquals(owner, owner);
        assertEquals(owner, equal);
        assertEquals(owner.hashCode(), equal.hashCode());
        assertNotEquals(owner, "client");
        assertNotEquals(owner, owner("other"));
    }
    
    private AgentHttpWatchOwnerKey owner(String clientId) {
        return new AgentHttpWatchOwnerKey(clientId, "alice", "public");
    }
    
    private AgentHttpWatchWaiter waiter(AgentHttpWatchOwnerKey owner, long generation,
        int itemCount, int bytes) {
        List<AgentWatchBatchItem> items = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            AgentReference reference = new AgentReference();
            reference.setAgentName("agent-" + i);
            AgentDiscoveryRequest discoveryRequest = new AgentDiscoveryRequest();
            discoveryRequest.setNamespaceId("public");
            discoveryRequest.setReference(reference);
            AgentWatchBatchItem item = new AgentWatchBatchItem();
            item.setClientWatchId("watch-" + i);
            item.setDiscoveryRequest(discoveryRequest);
            item.setMaterializedFingerprint("fingerprint-" + i);
            items.add(item);
        }
        return new AgentHttpWatchWaiter(owner, generation, 1000L, items, bytes,
            ignored -> {
            });
    }
}
