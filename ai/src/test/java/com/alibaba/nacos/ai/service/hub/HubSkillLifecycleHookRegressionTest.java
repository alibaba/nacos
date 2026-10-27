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
import com.alibaba.nacos.ai.pipeline.PublishPipelineExecutor;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.resource.ResourceVersionInfo;
import com.alibaba.nacos.ai.service.skills.SkillIndexManifestService;
import com.alibaba.nacos.ai.service.skills.SkillOperationServiceImpl;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

/** Verify that the actual ordinary Skill service wires Hub fences in correct order. */
class HubSkillLifecycleHookRegressionTest {
    
    private final AiResourceManager manager = mock(AiResourceManager.class);
    private final SkillIndexManifestService manifests = mock(SkillIndexManifestService.class);
    private final HubSkillSourceLifecycleRevoker revoker =
        mock(HubSkillSourceLifecycleRevoker.class);
    private final SkillOperationServiceImpl skills = new SkillOperationServiceImpl(
        mock(AiResourcePersistService.class), mock(AiResourceVersionPersistService.class),
        mock(PublishPipelineExecutor.class), manifests, manager);
    
    private final AiResource source = new AiResource();
    
    @BeforeEach
    void setUp() {
        skills.setHubSkillSourceLifecycleRevoker(revoker);
        source.setId(101L);
        source.setNamespaceId("source-a");
        source.setName("find-skills");
        source.setType("skill");
        ResourceVersionInfo info = new ResourceVersionInfo();
        source.setVersionInfo(JacksonUtils.toJson(info));
    }
    
    @Test
    void missingRequiredRevokerStopsDeletionBeforeManifestOrFiles() throws Exception {
        skills.setHubSkillSourceLifecycleRevoker(null);
        when(manager.findMeta("source-a", "find-skills", "skill")).thenReturn(source);
        try (MockedStatic<VisibilityHelper> visibility = mockStatic(VisibilityHelper.class)) {
            org.junit.jupiter.api.Assertions.assertEquals(500, assertThrows(NacosException.class,
                () -> skills.deleteSkill("source-a", "find-skills")).getErrCode());
        }
        verifyNoInteractions(manifests);
        verify(manager, org.mockito.Mockito.never()).deleteResourceWithVersions(any(), any(), any(),
            any());
    }
    
    @Test
    void preFenceConflictPreventsSourceDeletion() throws Exception {
        when(manager.findMeta("source-a", "find-skills", "skill")).thenReturn(source);
        doThrow(new NacosException(409, "concurrent publication"))
            .when(revoker).revokeSource(source);
        try (MockedStatic<VisibilityHelper> visibility = mockStatic(VisibilityHelper.class)) {
            org.junit.jupiter.api.Assertions.assertEquals(409, assertThrows(NacosException.class,
                () -> skills.deleteSkill("source-a", "find-skills")).getErrCode());
        }
        verifyNoInteractions(manifests);
        verify(manager, org.mockito.Mockito.never()).deleteResourceWithVersions(any(), any(), any(),
            any());
    }
    
    @Test
    void postFenceFailureDoesNotMaskTheSourceFailure() throws Exception {
        when(manager.findMeta("source-a", "find-skills", "skill")).thenReturn(source);
        NacosException original = new NacosException(500, "manifest failed");
        NacosException secondary = new NacosException(409, "post fence failed");
        doThrow(original).when(manifests).delete("source-a", "find-skills");
        when(revoker.revokeSource(source)).thenReturn(true).thenThrow(secondary);
        try (MockedStatic<VisibilityHelper> visibility = mockStatic(VisibilityHelper.class)) {
            NacosException failure = assertThrows(NacosException.class,
                () -> skills.deleteSkill("source-a", "find-skills"));
            org.junit.jupiter.api.Assertions.assertSame(original, failure);
            org.junit.jupiter.api.Assertions.assertArrayEquals(new Throwable[] {secondary},
                failure.getSuppressed());
        }
    }
    
    @Test
    void versionOfflineUsesOnlyTheVersionFence() throws Exception {
        when(manager.requireMeta("source-a", "find-skills", "skill")).thenReturn(source);
        try (MockedStatic<VisibilityHelper> visibility = mockStatic(VisibilityHelper.class)) {
            skills.changeOnlineStatus("source-a", "find-skills", "version", "1.0.0", false);
        }
        verify(revoker, times(2)).revokeVersion(source, "1.0.0");
        verify(revoker, org.mockito.Mockito.never()).revokeSource(any());
    }
    
    @Test
    void deniedPrivateScopeCannotRevokeOrMutateTheSource() throws Exception {
        when(manager.requireMeta("source-a", "find-skills", "skill")).thenReturn(source);
        try (MockedStatic<VisibilityHelper> visibility = mockStatic(VisibilityHelper.class)) {
            visibility.when(() -> VisibilityHelper.checkWritableResource(source))
                .thenThrow(new NacosException(403, "denied"));
            assertThrows(NacosException.class,
                () -> skills.updateScope("source-a", "find-skills", "PRIVATE"));
        }
        verifyNoInteractions(revoker);
        verify(manager, org.mockito.Mockito.never()).doUpdateScope(any(), any(), any(), any());
    }
    
    @Test
    void deleteRevokesBeforeIndexOrStorageAndAgainAfterDelete() throws Exception {
        when(manager.findMeta("source-a", "find-skills", "skill")).thenReturn(source);
        try (MockedStatic<VisibilityHelper> visibility = mockStatic(VisibilityHelper.class)) {
            skills.deleteSkill("source-a", "find-skills");
        }
        InOrder order = inOrder(revoker, manifests, manager);
        order.verify(revoker).revokeSource(source);
        order.verify(manifests).delete("source-a", "find-skills");
        order.verify(manager).deleteResourceWithVersions(eq("source-a"), eq("find-skills"),
            eq("skill"), any());
        order.verify(revoker).revokeSource(source);
    }
    
    @Test
    void midDeleteFailureStillKeepsSecondFence() throws Exception {
        when(manager.findMeta("source-a", "find-skills", "skill")).thenReturn(source);
        doThrow(new NacosException(NacosException.SERVER_ERROR, "manifest storage unavailable"))
            .when(manifests).delete("source-a", "find-skills");
        try (MockedStatic<VisibilityHelper> visibility = mockStatic(VisibilityHelper.class)) {
            assertThrows(NacosException.class, () -> skills.deleteSkill("source-a", "find-skills"));
        }
        verify(revoker, times(2)).revokeSource(source);
    }
    
    @Test
    void fullSkillDisableFencesSourceTwice() throws Exception {
        when(manager.requireMeta("source-a", "find-skills", "skill")).thenReturn(source);
        try (MockedStatic<VisibilityHelper> visibility = mockStatic(VisibilityHelper.class)) {
            skills.changeOnlineStatus("source-a", "find-skills", "skill", null, false);
        }
        InOrder order = inOrder(revoker, manager, manifests);
        order.verify(revoker).revokeSource(source);
        order.verify(manager).metaEnableDisable("source-a", source, false);
        order.verify(manifests).delete("source-a", "find-skills");
        order.verify(revoker).revokeSource(source);
    }
    
    @Test
    void privateScopeWithdrawsButPublicDoesNot() throws Exception {
        when(manager.requireMeta("source-a", "find-skills", "skill")).thenReturn(source);
        try (MockedStatic<VisibilityHelper> visibility = mockStatic(VisibilityHelper.class)) {
            skills.updateScope("source-a", "find-skills", "PRIVATE");
            skills.updateScope("source-a", "find-skills", "PUBLIC");
        }
        verify(revoker, times(2)).revokeSource(source);
        verify(manager).doUpdateScope("source-a", "find-skills", "skill", "PRIVATE");
        verify(manager).doUpdateScope("source-a", "find-skills", "skill", "PUBLIC");
    }
    
    @Test
    void deniedDeleteMustNotAttemptRevocation() throws Exception {
        when(manager.findMeta("source-a", "find-skills", "skill")).thenReturn(source);
        try (MockedStatic<VisibilityHelper> visibility = mockStatic(VisibilityHelper.class)) {
            visibility.when(() -> VisibilityHelper.checkWritableResource(source))
                .thenThrow(new NacosException(NacosException.NO_RIGHT, "denied"));
            assertThrows(NacosException.class, () -> skills.deleteSkill("source-a", "find-skills"));
        }
        verifyNoInteractions(revoker);
    }
}
