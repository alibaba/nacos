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
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.exception.NacosException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regression checks for ordering of Hub publication, source and caller gates. */
class HubSkillReadAccessServiceTest {
    
    private HubSkillReadPermissionGuard permissions;
    
    private HubSkillSourceReadValidator validator;
    
    private HubSkillReferenceResolver resolver;
    
    private HubSkillReadAccessService service;
    
    private AiResource source;
    
    private ResolvedHubSkillReference publication;
    
    @BeforeEach
    void setUp() throws Exception {
        permissions = mock(HubSkillReadPermissionGuard.class);
        validator = mock(HubSkillSourceReadValidator.class);
        resolver = mock(HubSkillReferenceResolver.class);
        service = new HubSkillReadAccessService(permissions, validator, resolver);
        source = new AiResource();
        source.setId(101L);
        when(validator.requireLiveSource(org.mockito.ArgumentMatchers.any()))
            .thenReturn(source);
        HubResourceReference reference = HubResourceReferenceJsonUtil.build(
            "team-a", 101L, "find-skills", 501L, "1.0.0");
        AiResource hub = new AiResource();
        hub.setId(901L);
        hub.setMetaVersion(7L);
        AiResourceVersion version = new AiResourceVersion();
        version.setId(701L);
        publication = new ResolvedHubSkillReference(hub, version, reference);
    }
    
    @Test
    void entryResolutionAuthorizesBeforeLookingUpAnyHubId() throws Exception {
        when(resolver.resolve(901L)).thenReturn(publication);
        assertEquals(publication, service.resolveReadable(901L));
        InOrder order = inOrder(permissions, resolver, validator);
        order.verify(permissions).requireHubRead();
        order.verify(resolver).resolve(901L);
        order.verify(validator).requireLiveSource(publication);
        order.verify(resolver).requireUnchanged(publication);
        order.verifyNoMoreInteractions();
    }
    
    @Test
    void unauthenticatedEntryLookupCannotProbeHubExistence() throws Exception {
        doThrow(new NacosException(NacosException.NO_RIGHT, "not authenticated"))
            .when(permissions).requireHubRead();
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> service.resolveReadable(901L)).getErrCode());
        verify(resolver, never()).resolve(901L);
    }
    
    @Test
    void ordinaryHubReadChecksCallerThenSourceThenHub() throws Exception {
        service.requireReadable(publication);
        InOrder order = inOrder(permissions, validator, resolver);
        order.verify(permissions).requireHubRead();
        order.verify(validator).requireLiveSource(publication);
        order.verify(resolver).requireUnchanged(publication);
        order.verifyNoMoreInteractions();
    }
    
    @Test
    void callerDeniedNeverQueriesSourceOrHub() throws Exception {
        doThrow(new NacosException(NacosException.NO_RIGHT, "not authenticated"))
            .when(permissions).requireHubRead();
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> service.requireReadable(publication)).getErrCode());
        verify(validator, never()).requireLiveSource(publication);
        verify(resolver, never()).requireUnchanged(publication);
    }
    
    @Test
    void withdrawnSourceNeverPassesTheHubFence() throws Exception {
        when(validator.requireLiveSource(publication))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "unavailable"));
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> service.requireReadable(publication)).getErrCode());
        verify(resolver, never()).requireUnchanged(publication);
    }
    
    @Test
    void concurrentlyUnsharedHubIsRejectedAfterSourceCheck() throws Exception {
        doThrow(new NacosException(NacosException.NOT_FOUND, "unshared"))
            .when(resolver).requireUnchanged(publication);
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> service.requireReadable(publication)).getErrCode());
    }
    
    @Test
    void readPermissionRevokedAfterInitialAdmissionFailsAtContentReturn() throws Exception {
        service.requireReadable(publication);
        doThrow(new NacosException(NacosException.NO_RIGHT, "auth revoked"))
            .when(permissions).requireHubRead();
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> service.requireStillReadable(publication)).getErrCode());
    }
    
    @Test
    void finalContentFenceRechecksHubBeforeAndAfterSource() throws Exception {
        service.requireStillReadable(publication);
        InOrder order = inOrder(permissions, resolver, validator);
        order.verify(permissions).requireHubRead();
        order.verify(resolver).requireUnchanged(publication);
        order.verify(validator).requireLiveSource(publication);
        order.verify(resolver).requireUnchanged(publication);
        order.verifyNoMoreInteractions();
    }
    
    @Test
    void finalFenceRejectsUnshareDuringSourceValidation() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        doAnswer(invocation -> {
            if (checks.incrementAndGet() == 2) {
                throw new NacosException(NacosException.NOT_FOUND, "share changed");
            }
            return null;
        }).when(resolver).requireUnchanged(publication);
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> service.requireStillReadable(publication)).getErrCode());
        assertEquals(2, checks.get());
    }
    
    @Test
    void finalFenceRejectsSourceGoingOfflineDuringContentRead() throws Exception {
        when(validator.requireLiveSource(publication))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "version offline"));
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> service.requireStillReadable(publication)).getErrCode());
    }
    
    @Test
    void optionalSourceReadDenialNeverDeniesHubRead() throws Exception {
        when(permissions.canReadSource(source)).thenReturn(false);
        assertFalse(service.canReadSourceDetail(publication));
        verify(resolver, org.mockito.Mockito.times(3)).requireUnchanged(publication);
        verify(validator, org.mockito.Mockito.times(2)).requireLiveSource(publication);
    }
    
    @Test
    void sourceReadGrantOnlyAllowsOptionalNavigation() throws Exception {
        when(permissions.canReadSource(source)).thenReturn(true);
        assertTrue(service.canReadSourceDetail(publication));
        verify(permissions).canReadSource(source);
        verify(resolver, org.mockito.Mockito.times(3)).requireUnchanged(publication);
        verify(validator, org.mockito.Mockito.times(2)).requireLiveSource(publication);
    }
    
    @Test
    void sourceReadCheckDoesNotHappenWhenHubWasWithdrawn() throws Exception {
        doThrow(new NacosException(NacosException.NOT_FOUND, "unshared"))
            .when(resolver).requireUnchanged(publication);
        assertThrows(NacosException.class, () -> service.canReadSourceDetail(publication));
        verify(permissions, never()).canReadSource(source);
    }
    
    @Test
    void nullPublicationAlwaysRejectsWithoutPermissionOrIo() throws Exception {
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> service.requireReadable(null)).getErrCode());
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> service.requireStillReadable(null)).getErrCode());
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> service.canReadSourceDetail(null)).getErrCode());
        verify(permissions, never()).requireHubRead();
    }
    
    @Test
    void sourceRevokedDuringDeepLinkPermissionCheckIsRejected() throws Exception {
        when(permissions.canReadSource(source)).thenAnswer(invocation -> {
            when(validator.requireLiveSource(publication))
                .thenThrow(new NacosException(NacosException.NOT_FOUND, "source revoked"));
            return true;
        });
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> service.canReadSourceDetail(publication)).getErrCode());
    }
    
    @Test
    void callerRevokedDuringDeepLinkPermissionCheckIsRejected() throws Exception {
        when(permissions.canReadSource(source)).thenAnswer(invocation -> {
            doThrow(new NacosException(NacosException.NO_RIGHT, "caller revoked"))
                .when(permissions).requireHubRead();
            return true;
        });
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> service.canReadSourceDetail(publication)).getErrCode());
    }
    
    @Test
    void hubWithdrawnDuringFinalDeepLinkSourceCheckIsRejected() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        doAnswer(invocation -> {
            if (checks.incrementAndGet() == 3) {
                throw new NacosException(NacosException.NOT_FOUND, "share withdrawn");
            }
            return null;
        }).when(resolver).requireUnchanged(publication);
        when(permissions.canReadSource(source)).thenReturn(true);
        assertEquals(NacosException.NOT_FOUND, assertThrows(NacosException.class,
            () -> service.canReadSourceDetail(publication)).getErrCode());
        assertEquals(3, checks.get());
    }
    
    @Test
    void versionProjectionAuthorizesBeforeReadingAndRechecksTheHubPointer() throws Exception {
        HubEligibleSkillVersion snapshot = new HubEligibleSkillVersion(source,
            new AiResourceVersion(), publication.toReference());
        when(validator.requireLiveVersion(publication)).thenReturn(snapshot);
        assertEquals(snapshot, service.requireReadableVersion(publication));
        InOrder order = inOrder(permissions, validator, resolver);
        order.verify(permissions).requireHubRead();
        order.verify(validator).requireLiveVersion(publication);
        order.verify(resolver).requireUnchanged(publication);
    }
    
}
