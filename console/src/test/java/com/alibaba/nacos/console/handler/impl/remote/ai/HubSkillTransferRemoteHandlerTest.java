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

import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.SkillMaintainerService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Remote operation must fail before SDK if caller has no forwardable identity. */
class HubSkillTransferRemoteHandlerTest {
    
    @Test
    void forwardsBothCallsAsOriginalCaller() throws Exception {
        NacosMaintainerClientHolder holder = mock(NacosMaintainerClientHolder.class);
        HubRemoteCallerGuard caller = mock(HubRemoteCallerGuard.class);
        AiMaintainerService maintainer = mock(AiMaintainerService.class);
        SkillMaintainerService skill = mock(SkillMaintainerService.class);
        when(holder.getAiMaintainerService()).thenReturn(maintainer);
        when(maintainer.skill()).thenReturn(skill);
        HubSkillTransferRemoteHandler handler = new HubSkillTransferRemoteHandler(holder, caller);
        HubSkillTransferRequest request = new HubSkillTransferRequest();
        HubSkillTransferResult result = new HubSkillTransferResult();
        when(skill.importHubSkill(request)).thenReturn(result);
        when(skill.copyHubSkill(request)).thenReturn(result);
        assertSame(result, handler.importFromHub(request));
        assertSame(result, handler.copyDirect(request));
        verify(caller, org.mockito.Mockito.times(2)).requireForwardableUser();
        verify(skill).importHubSkill(request);
        verify(skill).copyHubSkill(request);
    }
    
    @Test
    void missingForwardableCredentialNeverCallsMaintainer() throws Exception {
        NacosMaintainerClientHolder holder = mock(NacosMaintainerClientHolder.class);
        HubRemoteCallerGuard caller = mock(HubRemoteCallerGuard.class);
        doThrow(new NacosException(NacosException.NO_RIGHT, "denied"))
            .when(caller).requireForwardableUser();
        HubSkillTransferRemoteHandler handler = new HubSkillTransferRemoteHandler(holder, caller);
        assertThrows(NacosException.class,
            () -> handler.importFromHub(new HubSkillTransferRequest()));
        verifyNoInteractions(holder);
    }
}
