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
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.HubResourceNameUtils;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubSkillShareState;
import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HubSkillShareStateServiceTest {
    
    @Mock
    private AiResourceManager sources;
    
    @Mock
    private HubEntryStorage storage;
    
    @Mock
    private HubSkillPermissionGuard permissions;
    
    private HubSkillShareStateService service;
    
    @BeforeEach
    void setUp() {
        service = new HubSkillShareStateService(sources, storage, permissions);
    }
    
    @Test
    void neverSharedOwnerGetsNull() throws Exception {
        AiResource source = source();
        when(sources.findMeta("ns-a", "find-skills", "skill")).thenReturn(source);
        assertNull(service.bySource("ns-a", "find-skills"));
        verify(permissions, times(2)).requireSourceWrite(source);
    }
    
    @Test
    void sharesAreResolvedByImmutableSourceIdNotDisplayName() throws Exception {
        AiResource source = source();
        AiResource entry = entry(source, "enable");
        when(sources.findMeta("ns-a", "find-skills", "skill")).thenReturn(source);
        when(
            storage.findByInternalName(HubResourceNameUtils.buildInternalName("find-skills", 101L)))
            .thenReturn(entry);
        HubSkillShareState state = service.bySource("ns-a", "find-skills");
        assertTrue(state.isActive());
        assertEquals(901L, state.getHubEntryId());
        assertEquals(3L, state.getMetaVersion());
        assertEquals("1.0.0", state.getSharedVersion());
    }
    
    @Test
    void crossNamespaceNameCollisionIsRejected() throws Exception {
        AiResource source = source();
        AiResource wrong = entry(source, "enable");
        wrong.setExt(HubResourceReferenceJsonUtil.toJson(HubResourceReferenceJsonUtil.build(
            "ns-b", 101L, "find-skills", 501L, "1.0.0")));
        when(sources.findMeta("ns-a", "find-skills", "skill")).thenReturn(source);
        when(
            storage.findByInternalName(HubResourceNameUtils.buildInternalName("find-skills", 101L)))
            .thenReturn(wrong);
        assertThrows(NacosException.class, () -> service.bySource("ns-a", "find-skills"));
    }
    
    @Test
    void sourceWriteIsMandatory() throws Exception {
        AiResource source = source();
        when(sources.findMeta("ns-a", "find-skills", "skill")).thenReturn(source);
        doThrow(new NacosException(NacosException.NO_RIGHT, "denied"))
            .when(permissions).requireSourceWrite(source);
        assertThrows(NacosException.class, () -> service.bySource("ns-a", "find-skills"));
    }
    
    @Test
    void governanceIsAuthorizedBeforeAnyEntryLookup() throws Exception {
        doThrow(new NacosException(NacosException.NO_RIGHT, "denied"))
            .when(permissions).requireHubManage();
        assertThrows(NacosException.class, () -> service.byEntryForAdmin(901L));
    }
    
    @Test
    void adminCanInspectDisabledShareWithoutSourceRead() throws Exception {
        AiResource entry = entry(source(), "disable");
        when(storage.findByEntryId(901L)).thenReturn(entry);
        HubSkillShareState state = service.byEntryForAdmin(901L);
        assertFalse(state.isActive());
        assertEquals(3L, state.getMetaVersion());
    }
    
    @Test
    void replacementWithTheSameNameCannotInspectTheOldShare() throws Exception {
        AiResource original = source();
        AiResource replacement = source();
        replacement.setId(102L);
        when(sources.findMeta("ns-a", "find-skills", "skill")).thenReturn(original, replacement);
        when(
            storage.findByInternalName(HubResourceNameUtils.buildInternalName("find-skills", 101L)))
            .thenReturn(entry(original, "enable"));
        assertEquals(404, assertThrows(NacosException.class,
            () -> service.bySource("ns-a", "find-skills")).getErrCode());
    }
    
    @Test
    void ownershipChangeDuringLookupUsesFreshAuthorization() throws Exception {
        AiResource original = source();
        AiResource current = source();
        current.setOwner("new-owner");
        when(sources.findMeta("ns-a", "find-skills", "skill")).thenReturn(original, current);
        doAnswer(invocation -> {
            if (invocation.getArgument(0) == current) {
                throw new NacosException(403, "denied");
            }
            return null;
        }).when(permissions).requireSourceWrite(org.mockito.ArgumentMatchers.any(AiResource.class));
        assertEquals(403, assertThrows(NacosException.class,
            () -> service.bySource("ns-a", "find-skills")).getErrCode());
    }
    
    @Test
    void sourceRemovedDuringLookupDoesNotReturnAnAuthorizedStaleState() throws Exception {
        when(sources.findMeta("ns-a", "find-skills", "skill")).thenReturn(source(), null);
        assertEquals(404, assertThrows(NacosException.class,
            () -> service.bySource("ns-a", "find-skills")).getErrCode());
    }
    
    @Test
    void reservedNamespaceCannotBeQueriedAsAnOrdinarySource() {
        assertEquals(400, assertThrows(NacosException.class,
            () -> service.bySource(AiConstants.Hub.NAMESPACE_ID, "find-skills")).getErrCode());
        verifyNoInteractions(sources, storage, permissions);
    }
    
    @Test
    void invalidReferenceReturnsControlledNotFoundWithoutDescriptor() throws Exception {
        AiResource corrupted = entry(source(), "enable");
        corrupted.setExt("{secret-storage-descriptor}");
        when(storage.findByEntryId(901L)).thenReturn(corrupted);
        NacosException failure =
            assertThrows(NacosException.class, () -> service.byEntryForAdmin(901L));
        assertEquals(404, failure.getErrCode());
        assertFalse(failure.getMessage().contains("secret-storage"));
    }
    
    @Test
    void governanceCannotReturnADifferentEntryThanRequested() throws Exception {
        AiResource wrong = entry(source(), "enable");
        wrong.setId(902L);
        when(storage.findByEntryId(901L)).thenReturn(wrong);
        assertEquals(404,
            assertThrows(NacosException.class, () -> service.byEntryForAdmin(901L)).getErrCode());
    }
    
    @Test
    void governanceRejectsNonCanonicalNamesAndUnknownStatus() throws Exception {
        AiResource corrupted = entry(source(), "enable");
        corrupted.setName("find-skills");
        when(storage.findByEntryId(901L)).thenReturn(corrupted);
        assertEquals(404,
            assertThrows(NacosException.class, () -> service.byEntryForAdmin(901L)).getErrCode());
        corrupted.setName(HubResourceNameUtils.buildInternalName("find-skills", 101L));
        corrupted.setStatus("unknown");
        assertEquals(404,
            assertThrows(NacosException.class, () -> service.byEntryForAdmin(901L)).getErrCode());
    }
    
    @Test
    void storageOutageReturnsControlledUnavailableWithoutBackendDetails() throws Exception {
        when(storage.findByEntryId(901L))
            .thenThrow(new IllegalStateException("secret SQL credentials"));
        NacosException failure =
            assertThrows(NacosException.class, () -> service.byEntryForAdmin(901L));
        assertEquals(500, failure.getErrCode());
        assertFalse(failure.getMessage().contains("SQL"));
    }
    
    @Test
    void deniedOwnerAndAnonymousGovernanceDoNotReadHubStorage() throws Exception {
        when(sources.findMeta("ns-a", "find-skills", "skill")).thenReturn(source());
        doThrow(new NacosException(403, "denied")).when(permissions)
            .requireSourceWrite(org.mockito.ArgumentMatchers.any(AiResource.class));
        doThrow(new NacosException(403, "denied")).when(permissions).requireHubManage();
        assertThrows(NacosException.class, () -> service.bySource("ns-a", "find-skills"));
        assertThrows(NacosException.class, () -> service.byEntryForAdmin(901L));
        verifyNoInteractions(storage);
    }
    
    private AiResource source() {
        AiResource source = new AiResource();
        source.setId(101L);
        source.setNamespaceId("ns-a");
        source.setType("skill");
        source.setName("find-skills");
        return source;
    }
    
    private AiResource entry(AiResource source, String status) throws NacosException {
        AiResource entry = new AiResource();
        entry.setId(901L);
        entry.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
        entry.setType("skill");
        entry.setFrom("hub");
        entry.setStatus(status);
        entry.setMetaVersion(3L);
        entry.setName(HubResourceNameUtils.buildInternalName(source.getName(), source.getId()));
        entry.setExt(HubResourceReferenceJsonUtil.toJson(HubResourceReferenceJsonUtil.build(
            source.getNamespaceId(), source.getId(), source.getName(), 501L, "1.0.0")));
        return entry;
    }
}
