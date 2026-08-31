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

package com.alibaba.nacos.client.ai.watch;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentWatchClientMetricsTest {
    
    @Test
    void gaugesAndClosedEventLabelsAreProcessWide() throws Exception {
        Constructor<AgentWatchClientMetrics> constructor = AgentWatchClientMetrics.class
            .getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
        double intents = AgentWatchClientMetrics.intentCount();
        double pending = AgentWatchClientMetrics.pendingCount();
        double dirty = AgentWatchClientMetrics.dirtyCount();
        
        AgentWatchClientMetrics.intentAdded();
        AgentWatchClientMetrics.pendingAdded();
        AgentWatchClientMetrics.dirtyAdded();
        assertEquals(intents + 1D, AgentWatchClientMetrics.intentCount());
        assertEquals(pending + 1D, AgentWatchClientMetrics.pendingCount());
        assertEquals(dirty + 1D, AgentWatchClientMetrics.dirtyCount());
        AgentWatchClientMetrics.dirtyRemoved();
        AgentWatchClientMetrics.pendingRemoved();
        AgentWatchClientMetrics.intentRemoved();
        assertEquals(intents, AgentWatchClientMetrics.intentCount());
        assertEquals(pending, AgentWatchClientMetrics.pendingCount());
        assertEquals(dirty, AgentWatchClientMetrics.dirtyCount());
        
        double before = AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.LISTENER_CALLBACK,
            AgentWatchClientMetrics.Result.SUCCESS);
        AgentWatchClientMetrics.record(AgentWatchClientMetrics.Event.LISTENER_CALLBACK,
            AgentWatchClientMetrics.Result.SUCCESS);
        assertEquals(before + 1D, AgentWatchClientMetrics.eventCount(
            AgentWatchClientMetrics.Event.LISTENER_CALLBACK,
            AgentWatchClientMetrics.Result.SUCCESS));
        assertEquals(5, AgentWatchClientMetrics.Event.values().length);
        assertEquals(6, AgentWatchClientMetrics.Result.values().length);
    }
}
