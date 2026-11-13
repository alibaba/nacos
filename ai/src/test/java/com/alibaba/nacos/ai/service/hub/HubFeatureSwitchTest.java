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

import com.alibaba.nacos.ai.config.HubConfig;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.api.ai.model.hub.HubSkillListRequest;
import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillSummary;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Disabled Hub entry points never access data; ordinary source cleanup remains available. */
class HubFeatureSwitchTest {
    
    private ConfigurableEnvironment previous;
    
    private MockEnvironment environment;
    
    @BeforeEach
    void setUp() {
        previous = EnvUtil.getEnvironment();
        environment = new MockEnvironment().withProperty(HubConfig.ENABLED_PROPERTY, "false");
        EnvUtil.setEnvironment(environment);
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(previous);
    }
    
    @Test
    void disabledDirectoryReturnsRequestedEmptyPageWithoutStorageAccess() throws Exception {
        HubEntryStorage storage = mock(HubEntryStorage.class);
        HubSkillReadPermissionGuard permissions = mock(HubSkillReadPermissionGuard.class);
        HubSkillDirectoryService directory = new HubSkillDirectoryService(storage, permissions,
            mock(HubSkillReferenceResolver.class), mock(HubSkillReadAccessService.class),
            mock(NamespaceOperationService.class));
        HubSkillListRequest request = new HubSkillListRequest();
        request.setPageNo(3);
        Page<HubSkillSummary> result = directory.list(request);
        assertEquals(3, result.getPageNumber());
        assertEquals(0, result.getTotalCount());
        assertEquals(0, result.getPagesAvailable());
        assertTrue(result.getPageItems().isEmpty());
        verify(permissions).requireHubRead();
        verifyNoInteractions(storage);
        request.setPageNo(0);
        assertEquals(NacosException.INVALID_PARAM,
            assertThrows(NacosException.class, () -> directory.list(request)).getErrCode());
    }
    
    @Test
    void disabledDirectoryDoesNotHideAuthorizationFailures() throws Exception {
        HubSkillReadPermissionGuard permissions = mock(HubSkillReadPermissionGuard.class);
        doThrow(new NacosException(NacosException.NO_RIGHT, "denied"))
            .when(permissions).requireHubRead();
        HubSkillDirectoryService directory = new HubSkillDirectoryService(
            mock(HubEntryStorage.class), permissions, mock(HubSkillReferenceResolver.class),
            mock(HubSkillReadAccessService.class), mock(NamespaceOperationService.class));
        assertEquals(NacosException.NO_RIGHT, assertThrows(NacosException.class,
            () -> directory.list(new HubSkillListRequest())).getErrCode());
    }
    
    @Test
    void disabledDetailContentAndDownloadNeverResolveOrLoadSource() {
        HubSkillDirectoryService directory = mock(HubSkillDirectoryService.class);
        HubSkillReadAccessService access = mock(HubSkillReadAccessService.class);
        HubSkillSourceContentReader reader = mock(HubSkillSourceContentReader.class);
        HubSkillReadServiceImpl service = new HubSkillReadServiceImpl(directory, access, reader);
        assertDisabled(() -> service.getDetail(1L));
        assertDisabled(() -> service.getContent(1L));
        assertDisabled(() -> service.download(1L));
        verifyNoInteractions(directory, access, reader);
    }
    
    @Test
    void readGateRechecksSwitchAtFinalFence() {
        HubSkillReadPermissionGuard permissions = mock(HubSkillReadPermissionGuard.class);
        HubSkillSourceReadValidator source = mock(HubSkillSourceReadValidator.class);
        HubSkillReferenceResolver resolver = mock(HubSkillReferenceResolver.class);
        HubSkillReadAccessService access =
            new HubSkillReadAccessService(permissions, source, resolver);
        assertDisabled(() -> access.resolveReadable(1L));
        assertDisabled(() -> access.requireReadableSource(null));
        assertDisabled(() -> access.requireReadableVersion(null));
        assertDisabled(() -> access.canReadSourceDetail(null));
        assertDisabled(() -> access.requireStillReadable(null));
        verifyNoInteractions(permissions, source, resolver);
    }
    
    @Test
    void disablingWhileLoadingContentRejectsTheLoadedResponse() throws Exception {
        environment.setProperty(HubConfig.ENABLED_PROPERTY, "true");
        HubSkillReferenceResolver resolver = mock(HubSkillReferenceResolver.class);
        ResolvedHubSkillReference publication = mock(ResolvedHubSkillReference.class);
        when(resolver.resolve(1L)).thenReturn(publication);
        HubSkillReadAccessService access = new HubSkillReadAccessService(
            mock(HubSkillReadPermissionGuard.class), mock(HubSkillSourceReadValidator.class),
            resolver);
        HubSkillSourceContentReader reader = mock(HubSkillSourceContentReader.class);
        when(reader.load(publication)).thenAnswer(invocation -> {
            environment.setProperty(HubConfig.ENABLED_PROPERTY, "false");
            return new HubSkillContent();
        });
        HubSkillReadServiceImpl service = new HubSkillReadServiceImpl(
            mock(HubSkillDirectoryService.class), access, reader);
        assertDisabled(() -> service.getContent(1L));
        verify(reader).load(publication);
    }
    
    @Test
    void disabledMutationsDoNotReachSourceOrRepository() {
        HubShareRepository repository = mock(HubShareRepository.class);
        HubSkillEligibilityValidator eligibility = mock(HubSkillEligibilityValidator.class);
        HubSkillAuthorizationService authorization = mock(HubSkillAuthorizationService.class);
        HubTakedownAuditService audit = mock(HubTakedownAuditService.class);
        HubSkillShareService share =
            new HubSkillShareService(eligibility, authorization, repository);
        HubSkillAdjustService adjust =
            new HubSkillAdjustService(repository, eligibility, authorization);
        HubSkillUnshareService unshare =
            new HubSkillUnshareService(repository, eligibility, authorization, audit);
        assertDisabled(() -> share.shareSkill(null));
        assertDisabled(() -> adjust.adjustShare(null));
        assertDisabled(() -> unshare.unshareSkill(null));
        assertDisabled(() -> unshare.forceUnshare(null));
        verifyNoInteractions(repository, eligibility, authorization, audit);
    }
    
    @Test
    void disabledImportAndShareStateNeverTouchSourcesOrTargets() {
        NamespaceOperationService namespaces = mock(NamespaceOperationService.class);
        HubSkillTransferPermissionGuard permissions = mock(HubSkillTransferPermissionGuard.class);
        HubSkillReadAccessService access = mock(HubSkillReadAccessService.class);
        HubSkillTransferSourceValidator sources = mock(HubSkillTransferSourceValidator.class);
        HubSkillTransferFileService files = mock(HubSkillTransferFileService.class);
        HubSkillDraftTransferWriter writer = mock(HubSkillDraftTransferWriter.class);
        HubSkillTransferService transfer = new HubSkillTransferService(namespaces, permissions,
            access, sources, files, writer);
        assertDisabled(() -> transfer.importFromHub(null));
        // Direct Namespace copy retains its existing validation, not a Hub-disabled error.
        assertEquals(NacosException.INVALID_PARAM,
            assertThrows(NacosException.class, () -> transfer.copyDirect(null)).getErrCode());
        AiResourceManager manager = mock(AiResourceManager.class);
        HubEntryStorage storage = mock(HubEntryStorage.class);
        HubSkillPermissionGuard statePermissions = mock(HubSkillPermissionGuard.class);
        HubSkillShareStateService states =
            new HubSkillShareStateService(manager, storage, statePermissions);
        assertDisabled(() -> states.bySource("public", "skill"));
        assertDisabled(() -> states.byEntryForAdmin(1L));
        verifyNoInteractions(namespaces, permissions, access, sources, files, writer,
            manager, storage, statePermissions);
    }
    
    @Test
    void sourceLifecycleCleanupIsNotDisabled() throws Exception {
        HubEntryStorage storage = mock(HubEntryStorage.class);
        HubSkillPublicationInvalidator invalidator = mock(HubSkillPublicationInvalidator.class);
        AiResource source = new AiResource();
        source.setId(10L);
        source.setNamespaceId("public");
        source.setName("skill");
        source.setType("skill");
        assertFalse(new HubSkillSourceLifecycleRevoker(storage, invalidator).revokeSource(source));
        verify(storage).findByInternalName("skill-10");
    }
    
    private void assertDisabled(Executable operation) {
        NacosApiException failure = assertThrows(NacosApiException.class, operation);
        assertEquals(NacosException.SERVER_NOT_IMPLEMENTED, failure.getErrCode());
        assertEquals(ErrorCode.API_FUNCTION_DISABLED.getCode(), failure.getDetailErrCode());
        assertTrue(failure.getMessage().contains("Hub is disabled"));
    }
}
