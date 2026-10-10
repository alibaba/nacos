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

import com.alibaba.nacos.ai.model.AiResource;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Verifies that Hub business operations delegate to the correct source/governance checks.
 */
class HubSkillAuthorizationServiceTest {
    
    private final HubSkillPermissionGuard guard = mock(HubSkillPermissionGuard.class);
    
    private final HubSkillAuthorizationService service = new HubSkillAuthorizationService(guard);
    
    @Test
    void shareRequiresSourceReadAndWrite() throws Exception {
        AiResource source = mock(AiResource.class);
        service.requireSharePermission(source);
        verify(guard).requireSourceRead(source);
        verify(guard).requireSourceWrite(source);
        verifyNoMoreInteractions(guard);
    }
    
    @Test
    void adjustOnlyRequiresSourceWrite() throws Exception {
        AiResource source = mock(AiResource.class);
        service.requireAdjustPermission(source);
        verify(guard).requireSourceWrite(source);
        verifyNoMoreInteractions(guard);
    }
    
    @Test
    void unshareOnlyRequiresSourceWriteEvenIfSourceIsOffline() throws Exception {
        AiResource source = mock(AiResource.class);
        service.requireUnsharePermission(source);
        verify(guard).requireSourceWrite(source);
        verifyNoMoreInteractions(guard);
    }
    
    @Test
    void administrativeUnshareUsesIndependentGovernancePermission() throws Exception {
        service.requireForceUnsharePermission();
        verify(guard).requireHubManage();
        verifyNoMoreInteractions(guard);
    }
}
