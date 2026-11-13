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

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import com.alibaba.nacos.maintainer.client.ai.AiMaintainerService;
import com.alibaba.nacos.maintainer.client.ai.SkillMaintainerService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * All remote mutations authenticate the original user before touching the client.
 *
 * @author nacos
 */
class HubSkillRemoteHandlerTest {
    
    @ParameterizedTest
    @ValueSource(strings = {"share", "adjust", "unshare", "forceUnshare"})
    void authenticatedUserIsCheckedBeforeEveryOperation(String action) throws Exception {
        NacosMaintainerClientHolder holder = mock(NacosMaintainerClientHolder.class);
        HubRemoteCallerGuard guard = mock(HubRemoteCallerGuard.class);
        AiMaintainerService maintainer = mock(AiMaintainerService.class);
        SkillMaintainerService skill = mock(SkillMaintainerService.class);
        when(holder.getAiMaintainerService()).thenReturn(maintainer);
        when(maintainer.skill()).thenReturn(skill);
        HubSkillRemoteHandler handler = new HubSkillRemoteHandler(holder, guard);
        Object request = invoke(action, handler);
        InOrder sequence = inOrder(guard, skill);
        sequence.verify(guard).requireForwardableUser();
        switch (action) {
            case "share":
                sequence.verify(skill).shareHubSkill((HubShareSkillRequest) request);
                break;
            case "adjust":
                sequence.verify(skill).adjustHubShare((HubAdjustShareRequest) request);
                break;
            case "unshare":
                sequence.verify(skill).unshareHubSkill((HubUnshareRequest) request);
                break;
            default:
                sequence.verify(skill).forceUnshareHubSkill((HubForceUnshareRequest) request);
                break;
        }
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"share", "adjust", "unshare", "forceUnshare"})
    void deniedUserNeverObtainsPrivilegedMaintainerClient(String action) throws Exception {
        NacosMaintainerClientHolder holder = mock(NacosMaintainerClientHolder.class);
        HubRemoteCallerGuard guard = mock(HubRemoteCallerGuard.class);
        doThrow(new NacosException(403, "denied")).when(guard).requireForwardableUser();
        HubSkillRemoteHandler handler = new HubSkillRemoteHandler(holder, guard);
        assertThrows(NacosException.class, () -> invoke(action, handler));
        verifyNoInteractions(holder);
    }
    
    private Object invoke(String action, HubSkillRemoteHandler handler) throws NacosException {
        switch (action) {
            case "share":
                HubShareSkillRequest share = new HubShareSkillRequest();
                handler.share(share);
                return share;
            case "adjust":
                HubAdjustShareRequest adjust = new HubAdjustShareRequest();
                handler.adjust(adjust);
                return adjust;
            case "unshare":
                HubUnshareRequest unshare = new HubUnshareRequest();
                handler.unshare(unshare);
                return unshare;
            default:
                HubForceUnshareRequest force = new HubForceUnshareRequest();
                handler.forceUnshare(force);
                return force;
        }
    }
}
