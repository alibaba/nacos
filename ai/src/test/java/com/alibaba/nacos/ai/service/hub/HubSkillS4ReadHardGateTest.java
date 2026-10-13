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

import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A source becoming ineligible after content retrieval must not return its bytes. */
class HubSkillS4ReadHardGateTest {
    
    private final HubSkillDirectoryService directory = mock(HubSkillDirectoryService.class);
    private final HubSkillReadAccessService access = mock(HubSkillReadAccessService.class);
    private final HubSkillSourceContentReader reader = mock(HubSkillSourceContentReader.class);
    private final HubSkillReadServiceImpl endpoint =
        new HubSkillReadServiceImpl(directory, access, reader);
    
    @Test
    void contentCannotReturnAfterSourceOfflineAtFinalFence() throws Exception {
        ResolvedHubSkillReference pinned = mock(ResolvedHubSkillReference.class);
        HubSkillContent sourceBytes = new HubSkillContent();
        sourceBytes.setSkillMd("confidential payload");
        when(access.resolveReadable(901L)).thenReturn(pinned);
        when(reader.load(pinned)).thenReturn(sourceBytes);
        doThrow(new NacosException(NacosException.NOT_FOUND, "source version offline"))
            .when(access).requireStillReadable(pinned);
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> endpoint.getContent(901L)).getErrCode());
        verify(reader).load(pinned);
        verify(access).requireStillReadable(pinned);
    }
    
    @Test
    void contentCannotReturnAfterAdminUnsharesAtFinalFence() throws Exception {
        ResolvedHubSkillReference pinned = mock(ResolvedHubSkillReference.class);
        when(access.resolveReadable(901L)).thenReturn(pinned);
        when(reader.load(pinned)).thenReturn(new HubSkillContent());
        doThrow(new NacosException(NacosException.NOT_FOUND, "Hub disabled"))
            .when(access).requireStillReadable(pinned);
        assertThrows(NacosException.class, () -> endpoint.getContent(901L));
    }
}
