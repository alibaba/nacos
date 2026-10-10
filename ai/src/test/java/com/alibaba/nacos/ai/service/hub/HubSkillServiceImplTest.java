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

package com.alibaba.nacos.ai.service.hub;

import com.alibaba.nacos.api.ai.model.hub.HubAdjustShareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubForceUnshareRequest;
import com.alibaba.nacos.api.ai.model.hub.HubShareResult;
import com.alibaba.nacos.api.ai.model.hub.HubShareSkillRequest;
import com.alibaba.nacos.api.ai.model.hub.HubUnshareRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises the unified S1 service's delegation contract. */
class HubSkillServiceImplTest {
    
    @Test
    void delegatesAllFourOperations() throws Exception {
        HubSkillShareService share = mock(HubSkillShareService.class);
        HubSkillAdjustService adjust = mock(HubSkillAdjustService.class);
        HubSkillUnshareService unshare = mock(HubSkillUnshareService.class);
        HubSkillServiceImpl service = new HubSkillServiceImpl(share, adjust, unshare);
        HubShareSkillRequest shareRequest = new HubShareSkillRequest();
        HubAdjustShareRequest adjustRequest = new HubAdjustShareRequest();
        HubUnshareRequest unshareRequest = new HubUnshareRequest();
        HubForceUnshareRequest forceRequest = new HubForceUnshareRequest();
        HubShareResult response = new HubShareResult();
        when(share.shareSkill(shareRequest)).thenReturn(response);
        when(adjust.adjustShare(adjustRequest)).thenReturn(response);
        when(unshare.unshareSkill(unshareRequest)).thenReturn(response);
        when(unshare.forceUnshare(forceRequest)).thenReturn(response);
        assertSame(response, service.shareSkill(shareRequest));
        assertSame(response, service.adjustShare(adjustRequest));
        assertSame(response, service.unshareSkill(unshareRequest));
        assertSame(response, service.forceUnshare(forceRequest));
        verify(share).shareSkill(shareRequest);
        verify(adjust).adjustShare(adjustRequest);
        verify(unshare).unshareSkill(unshareRequest);
        verify(unshare).forceUnshare(forceRequest);
    }
}
