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

package com.alibaba.nacos.console.handler.impl.remote.ai;

import com.alibaba.nacos.api.ai.model.hub.HubSkillShareState;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.SkillMaintainerService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;

class HubSkillStateRemoteHandlerTest {
    
    @Test
    void forwardsExactSourceAndEntryIdentityOnlyAfterCheckingOriginalCaller() throws Exception {
        NacosMaintainerClientHolder holder = mock(NacosMaintainerClientHolder.class);
        HubRemoteCallerGuard guard = mock(HubRemoteCallerGuard.class);
        AiMaintainerService ai = mock(AiMaintainerService.class);
        SkillMaintainerService skill = mock(SkillMaintainerService.class);
        when(holder.getAiMaintainerService()).thenReturn(ai);
        when(ai.skill()).thenReturn(skill);
        HubSkillShareState expected = new HubSkillShareState();
        when(skill.getHubSkillSourceShare("ns-a", "find-skills")).thenReturn(expected);
        when(skill.getHubSkillGovernanceState(Long.MAX_VALUE)).thenReturn(expected);
        HubSkillStateRemoteHandler handler = new HubSkillStateRemoteHandler(holder, guard);
        assertSame(expected, handler.bySource("ns-a", "find-skills"));
        assertSame(expected, handler.governanceState(Long.MAX_VALUE));
        org.mockito.InOrder order = inOrder(guard, holder);
        order.verify(guard).requireForwardableUser();
        order.verify(holder).getAiMaintainerService();
        order.verify(guard).requireForwardableUser();
        order.verify(holder).getAiMaintainerService();
    }
    
    @Test
    void anonymousOrUnforwardableCredentialsCannotFallBackToServerIdentity() throws Exception {
        NacosMaintainerClientHolder holder = mock(NacosMaintainerClientHolder.class);
        HubRemoteCallerGuard guard = mock(HubRemoteCallerGuard.class);
        doThrow(new NacosException(403, "not forwardable")).when(guard).requireForwardableUser();
        HubSkillStateRemoteHandler handler = new HubSkillStateRemoteHandler(holder, guard);
        assertThrows(NacosException.class, () -> handler.bySource("ns-a", "find-skills"));
        assertThrows(NacosException.class, () -> handler.governanceState(901L));
        verifyNoInteractions(holder);
    }
}
