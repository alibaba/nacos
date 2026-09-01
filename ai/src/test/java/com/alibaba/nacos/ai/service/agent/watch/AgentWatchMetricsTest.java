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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWatchMetricsTest {
    
    @AfterEach
    void tearDown() {
        AgentWatchMetrics.resetGaugesForTest();
    }
    
    @Test
    void gaugesAndClosedEventLabelsRemainLowCardinality() throws Exception {
        Constructor<AgentWatchMetrics> constructor = AgentWatchMetrics.class
            .getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
        
        AgentWatchMetrics.setActiveProjections(3);
        AgentWatchMetrics.setActiveGrpcWatches(5);
        AgentWatchMetrics.setActiveHttpWaiters(7, 1024L);
        AgentWatchMetrics.setPendingProjectionTasks(2);
        AgentWatchMetrics.setReconciliationLagMillis(31L);
        assertEquals(3, AgentWatchMetrics.activeProjections());
        assertEquals(5, AgentWatchMetrics.activeGrpcWatches());
        assertEquals(7, AgentWatchMetrics.activeHttpWaiters());
        assertEquals(1024L, AgentWatchMetrics.httpActiveBytes());
        assertEquals(2, AgentWatchMetrics.pendingProjectionTasks());
        assertEquals(31L, AgentWatchMetrics.reconciliationLagMillis());
        
        double before = AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.PROJECTION_RECOMPUTE,
            AgentWatchMetrics.Result.SUCCESS);
        AgentWatchMetrics.record(AgentWatchMetrics.Event.PROJECTION_RECOMPUTE,
            AgentWatchMetrics.Result.SUCCESS);
        AgentWatchMetrics.record(AgentWatchMetrics.Event.PROJECTION_RECOMPUTE,
            AgentWatchMetrics.Result.SUCCESS, 2D);
        AgentWatchMetrics.record(AgentWatchMetrics.Event.PROJECTION_RECOMPUTE,
            AgentWatchMetrics.Result.SUCCESS, 0D);
        assertEquals(before + 3D, AgentWatchMetrics.eventCount(
            AgentWatchMetrics.Event.PROJECTION_RECOMPUTE,
            AgentWatchMetrics.Result.SUCCESS));
        double grpcBytes = AgentWatchMetrics.byteCount(AgentWatchMetrics.Transport.GRPC);
        AgentWatchMetrics.recordBytes(AgentWatchMetrics.Transport.GRPC, 128D);
        AgentWatchMetrics.recordBytes(AgentWatchMetrics.Transport.GRPC, 0D);
        assertEquals(grpcBytes + 128D,
            AgentWatchMetrics.byteCount(AgentWatchMetrics.Transport.GRPC));
        double httpBytes = AgentWatchMetrics.byteCount(AgentWatchMetrics.Transport.HTTP);
        AgentWatchMetrics.recordJsonBytes(AgentWatchMetrics.Transport.HTTP,
            Collections.singletonMap("event", "invalidate"));
        assertTrue(AgentWatchMetrics.byteCount(AgentWatchMetrics.Transport.HTTP) > httpBytes);
        assertDoesNotThrow(() -> AgentWatchMetrics.recordJsonBytes(
            AgentWatchMetrics.Transport.HTTP, new FailingPayload()));
        assertEquals(9, AgentWatchMetrics.Event.values().length);
        assertEquals(10, AgentWatchMetrics.Result.values().length);
        assertEquals(2, AgentWatchMetrics.Transport.values().length);
    }
    
    private static class FailingPayload {
        
        public String getValue() {
            throw new IllegalStateException("serialization failure");
        }
    }
}
