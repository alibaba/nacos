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

import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDetail;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
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

/** Remote Console must admit the original user before calling the Admin SDK. */
class HubSkillReadRemoteHandlerTest {
    
    @Test
    void forwardsAuthenticatedUserToAdminReadPath() throws Exception {
        NacosMaintainerClientHolder holder = mock(NacosMaintainerClientHolder.class);
        HubRemoteCallerGuard guard = mock(HubRemoteCallerGuard.class);
        AiMaintainerService ai = mock(AiMaintainerService.class);
        SkillMaintainerService skill = mock(SkillMaintainerService.class);
        when(holder.getAiMaintainerService()).thenReturn(ai);
        when(ai.skill()).thenReturn(skill);
        HubSkillReadRemoteHandler handler = new HubSkillReadRemoteHandler(holder, guard);
        HubSkillListRequest request = new HubSkillListRequest();
        handler.list(request);
        HubSkillDetail detail = new HubSkillDetail();
        when(skill.getHubSkillDetail(91L)).thenReturn(detail);
        assertSame(detail, handler.detail(91L));
        HubSkillContent content = new HubSkillContent();
        when(skill.getHubSkillContent(91L)).thenReturn(content);
        assertSame(content, handler.content(91L));
        HubSkillDownload download = new HubSkillDownload();
        when(skill.getHubSkillDownload(91L)).thenReturn(download);
        assertSame(download, handler.download(91L));
        verify(guard, org.mockito.Mockito.times(4)).requireForwardableUser();
        verify(skill).listHubSkills(request);
        verify(skill).getHubSkillDetail(91L);
        verify(skill).getHubSkillContent(91L);
    }
    
    @Test
    void denyUnforwardableIdentityWithoutContactingRemoteAdmin() throws Exception {
        NacosMaintainerClientHolder holder = mock(NacosMaintainerClientHolder.class);
        HubRemoteCallerGuard guard = mock(HubRemoteCallerGuard.class);
        doThrow(new NacosException(NacosException.NO_RIGHT, "not forwardable"))
            .when(guard).requireForwardableUser();
        HubSkillReadRemoteHandler handler = new HubSkillReadRemoteHandler(holder, guard);
        assertThrows(NacosException.class, () -> handler.detail(91L));
        assertThrows(NacosException.class, () -> handler.download(91L));
        verifyNoInteractions(holder);
    }
}
