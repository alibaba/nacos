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

package com.alibaba.nacos.client.ai.event;

import com.alibaba.nacos.api.ai.model.a2a.AgentCardDetailInfo;
import com.alibaba.nacos.client.ai.utils.CacheKeyUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AgentCardChangedEventTest {
    
    @Test
    void testGettersWithLatestVersion() {
        AgentCardDetailInfo info = new AgentCardDetailInfo();
        info.setName("agentX");
        info.setLatestVersion(true);
        info.setVersion("1.0");
        AgentCardChangedEvent event = new AgentCardChangedEvent(info);
        assertEquals("agentX", event.getAgentName());
        assertEquals(CacheKeyUtils.LATEST_VERSION, event.getVersion());
        assertSame(info, event.getAgentCard());
    }
    
    @Test
    void testGettersWithSpecifiedVersion() {
        AgentCardDetailInfo info = new AgentCardDetailInfo();
        info.setName("agentX");
        info.setLatestVersion(false);
        info.setVersion("v2");
        AgentCardChangedEvent event = new AgentCardChangedEvent(info);
        assertEquals("agentX", event.getAgentName());
        assertEquals("v2", event.getVersion());
        assertSame(info, event.getAgentCard());
    }
    
    @Test
    void testGettersWithNullLatestVersion() {
        AgentCardDetailInfo info = new AgentCardDetailInfo();
        info.setName("agentX");
        info.setLatestVersion(null);
        info.setVersion("v3");
        AgentCardChangedEvent event = new AgentCardChangedEvent(info);
        assertEquals("agentX", event.getAgentName());
        assertEquals(CacheKeyUtils.LATEST_VERSION, event.getVersion());
    }
}
