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

import com.alibaba.nacos.api.ai.model.rad.AgentWatchEventType;
import com.alibaba.nacos.api.ai.remote.request.AgentDiscoveryNotifyRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGrpcWatchTest {
    
    @Test
    void testIdentityAndSubscribeRaceState() {
        AgentProjectionKey projectionKey = AgentProjectionTestFixtures.key("watch");
        AgentWatchOwnerContext owner = new AgentWatchOwnerContext("identity", "open-api");
        AgentGrpcWatch watch = new AgentGrpcWatch("watch-key", "connection", "client-watch",
            projectionKey, owner);
        
        assertEquals("watch-key", watch.getWatchKey());
        assertEquals("connection", watch.getConnectionId());
        assertEquals("client-watch", watch.getClientWatchId());
        assertSame(projectionKey, watch.getProjectionKey());
        assertSame(owner, watch.getOwner());
        assertFalse(watch.markDirty());
        assertTrue(watch.activate("fingerprint-a"));
        assertEquals("fingerprint-a", watch.getLastAcceptedFingerprint());
        assertTrue(watch.beginDelivery());
        assertFalse(watch.beginDelivery());
        assertFalse(watch.shouldInvalidate("fingerprint-a"));
        assertTrue(watch.shouldInvalidate("fingerprint-b"));
    }
    
    @Test
    void testDeliveryCompletionPreservesCurrentFactAndSchedulesDirtySuccessor() {
        AgentGrpcWatch watch = newWatch();
        assertFalse(watch.activate("fingerprint-a"));
        assertTrue(watch.markDirty());
        assertTrue(watch.beginDelivery());
        assertFalse(watch.markDirty());
        
        AgentDiscoveryNotifyRequest invalidation = new AgentDiscoveryNotifyRequest();
        invalidation.setEventType(AgentWatchEventType.INVALIDATE);
        invalidation.setObservedFingerprint("fingerprint-b");
        assertTrue(watch.completeDelivery(invalidation, true));
        assertEquals("fingerprint-b", watch.getLastAcceptedFingerprint());
        assertTrue(watch.beginDelivery());
        
        AgentDiscoveryNotifyRequest revalidate = new AgentDiscoveryNotifyRequest();
        revalidate.setEventType(AgentWatchEventType.REVALIDATE);
        assertFalse(watch.completeDelivery(revalidate, true));
        assertEquals("fingerprint-b", watch.getLastAcceptedFingerprint());
    }
    
    @Test
    void testFailedDeliveryRetriesAndClosedWatchRejectsWork() {
        AgentGrpcWatch watch = newWatch();
        assertFalse(watch.activate(null));
        assertTrue(watch.markDirty());
        assertTrue(watch.beginDelivery());
        assertTrue(watch.completeDelivery(null, false));
        assertTrue(watch.beginDelivery());
        
        watch.close();
        assertTrue(watch.isClosed());
        assertFalse(watch.markDirty());
        assertFalse(watch.activate("late"));
        assertFalse(watch.beginDelivery());
        assertFalse(watch.completeDelivery(null, false));
    }
    
    private AgentGrpcWatch newWatch() {
        return new AgentGrpcWatch("watch-key", "connection", "client-watch",
            AgentProjectionTestFixtures.key("watch"),
            new AgentWatchOwnerContext(null, null));
    }
}
