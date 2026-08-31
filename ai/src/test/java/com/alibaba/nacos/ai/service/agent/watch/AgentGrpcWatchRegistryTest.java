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

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGrpcWatchRegistryTest {
    
    private final AgentWatchOwnerContext owner =
        new AgentWatchOwnerContext("identity", "open-api");
    
    @Test
    void testRegisterIndexesDuplicateAndConflict() throws Exception {
        AgentGrpcWatchRegistry registry = new AgentGrpcWatchRegistry();
        AgentProjectionKey firstKey = AgentProjectionTestFixtures.key("first");
        
        AgentGrpcWatchRegistry.Registration first =
            registry.register("connection", "watch-1", firstKey, owner, 2);
        AgentGrpcWatchRegistry.Registration duplicate =
            registry.register("connection", "watch-1", firstKey, owner, 2);
        
        assertTrue(first.isCreated());
        assertFalse(duplicate.isCreated());
        assertSame(first.getWatch(), duplicate.getWatch());
        assertSame(first.getWatch(), registry.findByClientWatchId("connection", "watch-1"));
        assertSame(first.getWatch(), registry.findOwned("connection",
            first.getWatch().getWatchKey()));
        assertNull(registry.findOwned("other", first.getWatch().getWatchKey()));
        assertSame(first.getWatch(), registry.findByWatchKey(first.getWatch().getWatchKey()));
        assertEquals(1, registry.findByProjection(firstKey).size());
        assertEquals(1, registry.size());
        assertEquals(1, registry.connectionSize("connection"));
        assertEquals(1, AgentWatchMetrics.activeGrpcWatches());
        
        NacosApiException conflict = assertThrows(NacosApiException.class,
            () -> registry.register("connection", "watch-1",
                AgentProjectionTestFixtures.key("different"), owner, 2));
        assertEquals(NacosException.CONFLICT, conflict.getErrCode());
        assertEquals(ErrorCode.RESOURCE_CONFLICT.getCode(), conflict.getDetailErrCode());
        registry.clear();
        assertEquals(0, AgentWatchMetrics.activeGrpcWatches());
    }
    
    @Test
    void testCapacityIsConnectionScopedAndRemovalCleansEveryIndex() throws Exception {
        AgentGrpcWatchRegistry registry = new AgentGrpcWatchRegistry();
        AgentGrpcWatch first = registry.register("connection", "watch-1",
            AgentProjectionTestFixtures.key("first"), owner, 2).getWatch();
        registry.register("connection", "watch-2",
            AgentProjectionTestFixtures.key("second"), owner, 2);
        registry.register("other", "watch-3", AgentProjectionTestFixtures.key("third"), owner,
            2);
        assertEquals(3, AgentWatchMetrics.activeGrpcWatches());
        double rejectedBefore = AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.CAPACITY_REJECTION, AgentWatchMetrics.Result.REJECTED);
        
        NacosApiException overLimit = assertThrows(NacosApiException.class,
            () -> registry.register("connection", "watch-4",
                AgentProjectionTestFixtures.key("fourth"), owner, 2));
        assertEquals(NacosException.OVER_THRESHOLD, overLimit.getErrCode());
        assertEquals(ErrorCode.AGENT_DISCOVERY_SUBSCRIPTION_OVER_LIMIT.getCode(),
            overLimit.getDetailErrCode());
        assertEquals(rejectedBefore + 1D, AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.CAPACITY_REJECTION,
            AgentWatchMetrics.Result.REJECTED));
        
        assertNull(registry.removeOwned("other", first.getWatchKey()));
        assertSame(first, registry.removeOwned("connection", first.getWatchKey()));
        assertTrue(first.isClosed());
        assertNull(registry.findByClientWatchId("connection", "watch-1"));
        assertTrue(registry.findByProjection(first.getProjectionKey()).isEmpty());
        assertNull(registry.remove(first.getWatchKey()));
        
        AgentGrpcWatch second = registry.findByClientWatchId("connection", "watch-2");
        List<AgentGrpcWatch> connectionRemoved = registry.removeConnection("connection");
        assertEquals(1, connectionRemoved.size());
        assertSame(second, connectionRemoved.get(0));
        assertTrue(second.isClosed());
        assertTrue(registry.removeConnection("missing").isEmpty());
        assertEquals(1, registry.size());
        assertEquals(0, registry.connectionSize("connection"));
        assertEquals(1, AgentWatchMetrics.activeGrpcWatches());
        registry.clear();
        assertEquals(0, AgentWatchMetrics.activeGrpcWatches());
    }
    
    @Test
    void testClearClosesWatchesAndEmptyLookupsAreStable() throws Exception {
        AgentGrpcWatchRegistry registry = new AgentGrpcWatchRegistry();
        AgentGrpcWatch first = registry.register("connection-1", "watch-1",
            AgentProjectionTestFixtures.key("shared"), owner, 2).getWatch();
        AgentGrpcWatch second = registry.register("connection-2", "watch-2",
            AgentProjectionTestFixtures.key("shared"), owner, 2).getWatch();
        assertEquals(2, registry.findByProjection(first.getProjectionKey()).size());
        
        List<AgentGrpcWatch> removed = registry.clear();
        
        assertEquals(2, removed.size());
        assertTrue(first.isClosed());
        assertTrue(second.isClosed());
        assertEquals(0, registry.size());
        assertEquals(0, AgentWatchMetrics.activeGrpcWatches());
        assertTrue(registry.findByProjection(first.getProjectionKey()).isEmpty());
        assertNull(registry.findByClientWatchId("connection-1", "watch-1"));
        assertTrue(registry.clear().isEmpty());
    }
}
