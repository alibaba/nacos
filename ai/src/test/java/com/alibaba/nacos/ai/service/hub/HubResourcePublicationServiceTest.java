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
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.hub.HubResourceReference;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.ai.service.mcp.McpCompatibilityMode;
import com.alibaba.nacos.ai.service.mcp.McpLifecycleManagementStateService;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.service.repository.HubEntryStorage;
import com.alibaba.nacos.core.exception.NacosApiExceptionHandler;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.hub.HubShareOutcome;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Typed publication and content fences, separate from type-specific payload and HTTP tests. */
class HubResourcePublicationServiceTest {
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = "SKILL", mode = EnumSource.Mode.EXCLUDE)
    void shareAdjustUnshareAndReactivateKeepOneStableEntry(HubResourceType type) throws Exception {
        Fixture f = new Fixture(type);
        assertEquals(HubShareOutcome.CREATED,
            f.service.share(type, "source", "same-name", "1.0.0", 101L).getOutcome());
        assertEquals(HubShareOutcome.ALREADY_SHARED,
            f.service.share(type, "source", "same-name", "1.0.0", 101L).getOutcome());
        f.sourceVersion.setId(202L);
        f.sourceVersion.setVersion("2.0.0");
        assertThrows(NacosException.class,
            () -> f.service.share(type, "source", "same-name", "2.0.0", 101L));
        assertEquals(HubShareOutcome.ADJUSTED,
            f.service.adjust(type, 501, 2, "2.0.0").getOutcome());
        assertEquals("2.0.0", f.service.readPinned(type, 501, null,
            (source, reference) -> reference.getSourceVersion()));
        assertThrows(NacosException.class,
            () -> f.service.readPinned(type, 501, "1.0.0", (source, reference) -> "wrong"));
        assertEquals(HubShareOutcome.UNSHARED, f.service.unshare(type, 501, 3).getOutcome());
        assertEquals(HubShareOutcome.ALREADY_UNSHARED,
            f.service.unshare(type, 501, 4).getOutcome());
        assertThrows(NacosException.class,
            () -> f.service.readPinned(type, 501, null, (source, reference) -> "wrong"));
        assertEquals(HubShareOutcome.REACTIVATED,
            f.service.share(type, "source", "same-name", "2.0.0", 101L).getOutcome());
        assertEquals(501L, f.entry.getId());
        assertEquals(5L, f.entry.getMetaVersion());
    }
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = "SKILL", mode = EnumSource.Mode.EXCLUDE)
    void sharedReadNeedsAdmissionButDoesNotRequestSourceRead(HubResourceType type)
        throws Exception {
        Fixture f = new Fixture(type);
        f.activate();
        assertEquals("contents", f.service.readPinned(type, 501, "1.0.0",
            (source, reference) -> "contents"));
        verify(f.permissions, never()).requireSourceRead(any(), any());
        verify(f.permissions, never()).requireSourceWrite(any(), any());
        assertThrows(NacosException.class, () -> f.service.readPinned(type, 501, null,
            (source, reference) -> {
                f.source.setStatus("disable");
                return "must not escape after revocation";
            }));
    }
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = "SKILL", mode = EnumSource.Mode.EXCLUDE)
    void adjustmentOrUnshareDuringContentIoRejectsResult(HubResourceType type) throws Exception {
        Fixture f = new Fixture(type);
        f.activate();
        assertThrows(NacosException.class, () -> f.service.readPinned(type, 501, null,
            (source, reference) -> {
                f.entry.setMetaVersion(3L);
                f.entry.setStatus("disable");
                return "stale";
            }));
    }
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = "SKILL", mode = EnumSource.Mode.EXCLUDE)
    void deniedSourceWriteCannotReserveOrPrepareEntry(HubResourceType type) throws Exception {
        Fixture f = new Fixture(type);
        doThrow(new NacosException(NacosException.NO_RIGHT, "denied"))
            .when(f.permissions).requireSourceWrite(type, f.source);
        assertThrows(NacosException.class,
            () -> f.service.share(type, "source", "same-name", "1.0.0", 101L));
        verifyNoInteractions(f.repository);
    }
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = "SKILL", mode = EnumSource.Mode.EXCLUDE)
    void disabledPrivateSourceCanUnshareButCannotShareAgain(HubResourceType type) throws Exception {
        Fixture f = new Fixture(type);
        f.activate();
        f.source.setScope("PRIVATE");
        f.source.setStatus("disable");
        assertEquals(HubShareOutcome.UNSHARED, f.service.unshare(type, 501, 2).getOutcome());
        assertThrows(NacosException.class,
            () -> f.service.share(type, "source", "same-name", "1.0.0", 101L));
        verify(f.permissions, atLeastOnce()).requireSourceWrite(type, f.source);
    }
    
    @Test
    void promptSharesAndReadsWithoutChangingItsGenericPrivateScope() throws Exception {
        Fixture f = new Fixture(HubResourceType.PROMPT);
        f.source.setScope("PRIVATE");
        assertEquals(HubShareOutcome.CREATED, f.service.share(HubResourceType.PROMPT,
            "source", "same-name", "1.0.0", 101L).getOutcome());
        verify(f.permissions, atLeastOnce()).requireSourceWrite(HubResourceType.PROMPT, f.source);
        verify(f.permissions, atLeastOnce()).requireSourceRead(HubResourceType.PROMPT, f.source);
        assertEquals("PRIVATE", f.source.getScope());
        assertEquals("contents", f.service.readPinned(HubResourceType.PROMPT, 501, "1.0.0",
            (source, reference) -> "contents"));
        assertEquals("PRIVATE", f.source.getScope());
        f.source.setStatus("disable");
        assertThrows(NacosException.class, () -> f.service.readPinned(HubResourceType.PROMPT,
            501, "1.0.0", (source, reference) -> "must not read disabled Prompt"));
    }
    
    @Test
    void mcpSyncingAndDisabledHubNeverPublishOrRead() throws Exception {
        Fixture f = new Fixture(HubResourceType.MCP);
        when(f.mcpState.resolveMode()).thenReturn(McpCompatibilityMode.SYNCING);
        assertThrows(NacosException.class,
            () -> f.service.share(HubResourceType.MCP, "source", "same-name", "1.0.0", null));
        verifyNoInteractions(f.repository);
        when(f.mcpState.resolveMode()).thenReturn(McpCompatibilityMode.LIFECYCLE_MANAGED);
        try (MockedStatic<HubConfig> config = mockStatic(HubConfig.class)) {
            config.when(HubConfig::requireEnabled)
                .thenThrow(new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED,
                    ErrorCode.API_FUNCTION_DISABLED, "disabled"));
            assertThrows(NacosException.class,
                () -> f.service.share(HubResourceType.MCP, "source", "same-name", "1.0.0", null));
            assertThrows(NacosException.class, () -> f.service.readPinned(HubResourceType.MCP,
                501, null, (source, reference) -> "must not read"));
        }
        verifyNoInteractions(f.repository);
    }
    
    @Test
    void governanceUsesIndependentGrantAndActualResourceType() throws Exception {
        Fixture f = new Fixture(HubResourceType.PROMPT);
        f.activate();
        assertEquals(HubShareOutcome.FORCE_UNSHARED,
            f.service.forceUnshare(HubResourceType.PROMPT, 501, 2, "policy").getOutcome());
        verify(f.permissions).requireAdministrator();
        verify(f.permissions, never()).requireSourceWrite(any(), any());
        verify(f.audit).recordUnshare(eq(HubResourceType.PROMPT), eq(501L), any(), eq(3L),
            eq(true), eq("policy"));
        assertFalse(f.service.forceUnshare(HubResourceType.PROMPT, 501, 3, "policy").isActive());
    }
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = {"MCP", "AGENT", "PROMPT", "AGENTSPEC"})
    void disabledHubRejectsCancellationBeforeReadingOrChangingEntries(HubResourceType type)
        throws Exception {
        Fixture f = new Fixture(type);
        try (MockedStatic<HubConfig> config = mockStatic(HubConfig.class)) {
            config.when(HubConfig::requireEnabled).thenThrow(new NacosApiException(501,
                ErrorCode.API_FUNCTION_DISABLED, "disabled"));
            assertEquals(ErrorCode.API_FUNCTION_DISABLED.getCode(), assertThrows(
                NacosApiException.class, () -> f.service.unshare(type, 501, 3)).getDetailErrCode());
            assertEquals(501, assertThrows(NacosApiException.class,
                () -> f.service.forceUnshare(type, 501, 3, "policy")).getErrCode());
        }
        verifyNoInteractions(f.repository, f.permissions, f.manager);
    }
    
    @ParameterizedTest
    @EnumSource(value = HubResourceType.class, names = {"MCP", "AGENT", "PROMPT", "AGENTSPEC"})
    void realSourceQueryFailuresFailWholeDirectoryWithSanitizedServerError(HubResourceType type)
        throws Exception {
        for (boolean metadataFailure : new boolean[] {true, false}) {
            for (boolean checkedFailure : new boolean[] {true, false}) {
                Fixture f = new Fixture(type);
                f.activate();
                Exception backend = checkedFailure ? new NacosException(503, "jdbc-secret-source")
                    : new IllegalStateException("jdbc-secret-source");
                if (metadataFailure) {
                    when(f.manager.findMeta("source", "same-name", type.resourceType()))
                        .thenAnswer(call -> {
                            throw backend;
                        });
                } else {
                    when(f.manager.findVersion("source", "same-name", type.resourceType(), "1.0.0"))
                        .thenAnswer(call -> {
                            throw backend;
                        });
                }
                HubEntryStorage storage = mock(HubEntryStorage.class);
                when(storage.maxEntryId(type)).thenReturn(501L);
                when(storage.nextEntryId(type, 0, 501)).thenReturn(501L);
                when(storage.entriesInIdRange(type, 501, 501))
                    .thenReturn(java.util.Collections.singletonList(f.entry));
                HubResourceDirectoryService directory =
                    new HubResourceDirectoryService(storage, f.service, f.permissions);
                NacosApiException failure = assertThrows(NacosApiException.class,
                    () -> directory.list(type, null, null, null, null, 1, 20));
                assertEquals(500, failure.getErrCode());
                assertSame(backend, failure.getCause());
                org.springframework.http.ResponseEntity<?> response =
                    new NacosApiExceptionHandler().handleNacosApiException(failure);
                assertEquals(500, response.getStatusCode().value());
                assertFalse(JacksonUtils.toJson(response.getBody()).contains("jdbc-secret"));
                assertEquals("enable", f.entry.getStatus());
                verify(f.repository, never()).disable(anyLong(), anyLong());
            }
        }
    }
    
    private static final class Fixture {
        
        private final HubShareRepositoryImpl repositories = mock(HubShareRepositoryImpl.class);
        
        private final HubShareRepository repository = mock(HubShareRepository.class);
        
        private final AiResourceManager manager = mock(AiResourceManager.class);
        
        private final HubResourcePermissionGuard permissions =
            mock(HubResourcePermissionGuard.class);
        
        private final McpLifecycleManagementStateService mcpState =
            mock(McpLifecycleManagementStateService.class);
        
        private final HubTakedownAuditService audit = mock(HubTakedownAuditService.class);
        
        private final AiResource source = new AiResource();
        
        private final AiResourceVersion sourceVersion = new AiResourceVersion();
        
        private final AiResource entry = new AiResource();
        
        private final HubResourcePublicationService service;
        
        private final HubResourceReference reference;
        
        private Fixture(HubResourceType type) throws Exception {
            source.setId(101L);
            source.setNamespaceId("source");
            source.setName("same-name");
            source.setType(type.resourceType());
            source.setStatus("enable");
            source.setScope("PUBLIC");
            sourceVersion.setId(201L);
            sourceVersion.setNamespaceId("source");
            sourceVersion.setName("same-name");
            sourceVersion.setType(type.resourceType());
            sourceVersion.setStatus("online");
            sourceVersion.setVersion("1.0.0");
            when(manager.findMeta("source", "same-name", type.resourceType())).thenReturn(source);
            when(manager.findVersion(eq("source"), eq("same-name"), eq(type.resourceType()),
                anyString()))
                .thenAnswer(call -> call.getArgument(3).equals(sourceVersion.getVersion())
                    ? sourceVersion : null);
            reference = HubResourceReferenceJsonUtil.build("source", 101L, "same-name", 201L,
                "1.0.0");
            entry.setId(501L);
            entry.setNamespaceId(AiConstants.Hub.NAMESPACE_ID);
            entry.setName("same-name-101");
            entry.setType(type.resourceType());
            entry.setFrom("hub");
            entry.setScope("PUBLIC");
            entry.setOwner("nacos-ai-hub");
            entry.setStatus("disable");
            entry.setMetaVersion(1L);
            entry.setExt(HubResourceReferenceJsonUtil.toJson(reference));
            entry.setVersionInfo("{\"onlineCnt\":0,\"labels\":{}}");
            when(repositories.forResourceType(type)).thenReturn(repository);
            when(repository.reserveEntry(any(), any())).thenAnswer(call -> snapshot());
            when(repository.findByEntryId(501L)).thenAnswer(call -> snapshot());
            when(repository.publish(eq(501L), anyLong(), any())).thenAnswer(call -> {
                long expected = call.getArgument(1);
                if (entry.getMetaVersion() != expected) {
                    return false;
                }
                HubResourceReference ref = call.getArgument(2);
                entry.setMetaVersion(expected + 1);
                entry.setExt(HubResourceReferenceJsonUtil.toJson(ref));
                entry.setVersionInfo("{\"onlineCnt\":1,\"labels\":{\"latest\":\""
                    + ref.getSourceVersion() + "\"}}");
                entry.setStatus("enable");
                return true;
            });
            when(repository.disable(eq(501L), anyLong())).thenAnswer(call -> {
                long expected = call.getArgument(1);
                if (entry.getMetaVersion() != expected) {
                    return false;
                }
                entry.setMetaVersion(expected + 1);
                entry.setStatus("disable");
                return true;
            });
            when(mcpState.resolveMode()).thenReturn(McpCompatibilityMode.LIFECYCLE_MANAGED);
            service = new HubResourcePublicationService(repositories,
                new HubResourceEligibilityValidator(manager), permissions, mcpState, audit);
        }
        
        private AiResource snapshot() {
            return JacksonUtils.toObj(JacksonUtils.toJson(entry), AiResource.class);
        }
        
        private void activate() throws NacosException {
            repository.publish(501, 1, reference);
        }
    }
}
