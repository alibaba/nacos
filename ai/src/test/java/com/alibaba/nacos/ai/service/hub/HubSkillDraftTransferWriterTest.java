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

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.utils.AiResourceImportUtils;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.storage.AiResourceStorageUtils;
import com.alibaba.nacos.api.ai.model.hub.HubSkillTransferResult;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Failure injection: never leave an enabled partial draft or delete a rival's entry. */
class HubSkillDraftTransferWriterTest {
    
    private final AiResourcePersistService resources = mock(AiResourcePersistService.class);
    private final AiResourceVersionPersistService versions =
        mock(AiResourceVersionPersistService.class);
    private final HubSkillDraftCommitStorage commit = mock(HubSkillDraftCommitStorage.class);
    private final HubSkillTransferFileService files = mock(HubSkillTransferFileService.class);
    private final HubSkillDraftTransferWriter writer =
        new HubSkillDraftTransferWriter(resources, versions, commit, files);
    
    private final AtomicReference<AiResource> meta = new AtomicReference<>();
    private final AtomicReference<AiResourceVersion> version = new AtomicReference<>();
    private final AtomicInteger cleanedFiles = new AtomicInteger();
    private MockedStatic<VisibilityHelper> visibility;
    private MockedStatic<AiResourceStorageUtils> storageConfig;
    private HubSkillTransferFileService.Files contents;
    private HubSkillTransferSourceSnapshot source;
    
    @BeforeEach
    void setUp() throws Exception {
        visibility = mockStatic(VisibilityHelper.class);
        visibility.when(VisibilityHelper::resolveCurrentIdentity).thenReturn("alice");
        storageConfig = mockStatic(AiResourceStorageUtils.class);
        storageConfig.when(() -> AiResourceStorageUtils.resolveProvider(
            com.alibaba.nacos.ai.constant.Constants.Skills.SKILL_STORAGE_PROVIDER_CONFIG_KEY,
            "nacos_config")).thenReturn("nacos_config");
        contents = mock(HubSkillTransferFileService.Files.class);
        when(contents.getDescription()).thenReturn("a new skill");
        when(files.recovery(eq("target-ns"), eq("target"), eq("0.0.1"), any()))
            .thenReturn(Map.of("storage", "skill descriptor", "files",
                java.util.List.of(Map.of("provider", "nacos_config", "key", "SKILL.md"))));
        when(contents.forProvider("nacos_config")).thenReturn(contents);
        when(resources.find("target-ns", "target", "skill")).thenAnswer(i -> meta.get());
        when(versions.find("target-ns", "target", "skill", "0.0.1"))
            .thenAnswer(i -> version.get());
        when(resources.insert(any(AiResource.class))).thenAnswer(i -> {
            AiResource inserted = i.getArgument(0);
            assertTrue(AiResourceImportUtils.isPending(inserted));
            Map<?, ?> pendingExt = JacksonUtils.toObj(inserted.getExt(), Map.class);
            assertNotNull(pendingExt.get("importRecovery"));
            assertNotNull(pendingExt.get("origin"));
            inserted.setId(900L);
            meta.set(inserted);
            return 900L;
        });
        when(versions.insert(any(AiResourceVersion.class))).thenAnswer(i -> {
            AiResourceVersion inserted = i.getArgument(0);
            inserted.setId(2001L);
            version.set(inserted);
            return 2001L;
        });
        when(commit.deleteDraftVersion(eq(900L), eq("target-ns"), eq("target"), anyLong(),
            eq(2001L), eq("0.0.1"))).thenAnswer(i -> {
                assertEquals(1, cleanedFiles.get());
                version.set(null);
                return true;
            });
        when(commit.publish(eq(900L), eq("target-ns"), eq("target"), eq(1L), anyString()))
            .thenAnswer(i -> {
                meta.get().setStatus(AiResourceConstants.META_STATUS_ENABLE);
                meta.get().setMetaVersion(2L);
                meta.get().setExt(i.getArgument(4));
                return true;
            });
        when(commit.deleteDisabled(eq(900L), eq("target-ns"), eq("target"), anyLong()))
            .thenAnswer(i -> {
                meta.set(null);
                return true;
            });
        doAnswer(i -> {
            i.<java.util.List<String>>getArgument(4).add("SKILL.md");
            return null;
        }).when(files).save(eq("target-ns"), eq("target"), eq("0.0.1"),
            eq(contents), any());
        doAnswer(i -> {
            assertNotNull(meta.get());
            assertEquals("disable", meta.get().getStatus());
            assertTrue(AiResourceImportUtils.isPending(meta.get()));
            assertEquals(409, assertThrows(NacosException.class,
                () -> AiResourceImportUtils.checkNotPending(meta.get())).getErrCode());
            cleanedFiles.incrementAndGet();
            return null;
        }).when(files).delete(eq("target-ns"), eq("target"), eq("0.0.1"),
            eq("nacos_config"), any());
        AiResource original = new AiResource();
        original.setId(101L);
        original.setNamespaceId("source-a");
        original.setName("find-skills");
        AiResourceVersion selected = new AiResourceVersion();
        selected.setId(501L);
        selected.setVersion("1.0.0");
        source = new HubSkillTransferSourceSnapshot(original, selected, 701L);
    }
    
    @AfterEach
    void tearDown() {
        storageConfig.close();
        visibility.close();
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void happyPathCreatesIndependentDraftWithOriginInFirstMetaInsert(boolean directClone)
        throws Exception {
        if (directClone) {
            AiResource original = new AiResource();
            original.setId(101L);
            original.setNamespaceId("source-a");
            original.setName("find-skills");
            AiResourceVersion originalVersion = new AiResourceVersion();
            originalVersion.setId(501L);
            originalVersion.setVersion("1.0.0");
            source = new HubSkillTransferSourceSnapshot(original, originalVersion, null);
        }
        AtomicInteger checks = new AtomicInteger();
        HubSkillTransferResult result = run(() -> checks.incrementAndGet());
        assertEquals("0.0.1", result.getDraftVersion());
        assertEquals("draft", result.getStatus());
        assertEquals(directClone ? "DIRECT_COPY" : "HUB", result.getOrigin());
        assertEquals(1, checks.get());
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, meta.get().getStatus());
        assertFalse(AiResourceImportUtils.isPending(meta.get()));
        assertFalse(meta.get().getExt().contains("importRecovery"));
        assertEquals(AiResourceConstants.VERSION_STATUS_DRAFT, version.get().getStatus());
        Map<?, ?> ext = JacksonUtils.toObj(meta.get().getExt(), Map.class);
        Map<?, ?> origin = (Map<?, ?>) ext.get("origin");
        assertEquals("source-a", origin.get("sourceNamespaceId"));
        assertEquals("1.0.0", origin.get("sourceVersion"));
        assertEquals(0, cleanedFiles.get());
    }
    
    @Test
    void targetNameConflictIs409BeforeAnyWrites() {
        AiResource existing = new AiResource();
        existing.setId(11L);
        meta.set(existing);
        assertEquals(NacosException.CONFLICT,
            assertThrows(NacosException.class, () -> run(() -> {
            })).getErrCode());
        verify(resources, never()).insert(any());
    }
    
    @Test
    void partialFileSaveFailureCleansTouchedFileAndMetadata() throws Exception {
        doAnswer(i -> {
            i.<java.util.List<String>>getArgument(4).add("SKILL.md");
            throw new NacosException(NacosException.SERVER_ERROR, "failed after write");
        }).when(files).save(eq("target-ns"), eq("target"), eq("0.0.1"),
            eq(contents), any());
        assertThrows(NacosException.class, () -> run(() -> {
        }));
        assertNull(meta.get());
        assertNull(version.get());
        assertEquals(1, cleanedFiles.get());
    }
    
    @Test
    void failedVersionInsertRemovesFilesAndReservation() throws Exception {
        doAnswer(i -> {
            throw new IllegalStateException("SQL unavailable");
        }).when(versions).insert(any());
        assertEquals(500, assertThrows(NacosException.class, () -> run(() -> {
        })).getErrCode());
        assertNull(meta.get());
        assertEquals(1, cleanedFiles.get());
    }
    
    @Test
    void sourceOfflineBeforeActivationNeverEnablesTarget() throws Exception {
        assertThrows(NacosException.class, () -> run(() -> {
            throw new NacosException(NacosException.NOT_FOUND, "source offline");
        }));
        assertNull(meta.get());
        assertNull(version.get());
        assertEquals(1, cleanedFiles.get());
    }
    
    @Test
    void completionCasOpensDraftAndNeverRechecksOrRollsBackUserEdits() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        doAnswer(i -> {
            assertTrue(AiResourceImportUtils.isPending(meta.get()));
            assertEquals(409, assertThrows(NacosException.class,
                () -> AiResourceImportUtils.checkNotPending(meta.get())).getErrCode());
            meta.get().setExt(i.getArgument(4));
            meta.get().setStatus("enable");
            meta.get().setMetaVersion(2L);
            AiResourceImportUtils.checkNotPending(meta.get());
            version.get().setStorage("user saved content");
            return true;
        }).when(commit).publish(eq(900L), eq("target-ns"), eq("target"), eq(1L), anyString());
        run(() -> checks.incrementAndGet());
        assertEquals(1, checks.get());
        assertEquals("user saved content", version.get().getStorage());
        assertEquals(0, cleanedFiles.get());
    }
    
    @Test
    void lostCompletionResponseNeverDeletesAnAlreadyOpenedDraft() throws Exception {
        doAnswer(i -> {
            meta.get().setExt(i.getArgument(4));
            meta.get().setStatus("enable");
            meta.get().setMetaVersion(2L);
            version.get().setStorage("user saved content");
            throw new IllegalStateException("commit response lost after database success");
        }).when(commit).publish(eq(900L), eq("target-ns"), eq("target"), eq(1L), anyString());
        assertThrows(NacosException.class, () -> run(() -> {
        }));
        assertFalse(AiResourceImportUtils.isPending(meta.get()));
        assertEquals("user saved content", version.get().getStorage());
        assertEquals(0, cleanedFiles.get());
    }
    
    @Test
    void failedCompletionCasCleansOnlyPendingReservation() throws Exception {
        doReturn(false).when(commit).publish(eq(900L), eq("target-ns"), eq("target"), eq(1L),
            anyString());
        assertThrows(NacosException.class, () -> run(() -> {
            assertTrue(AiResourceImportUtils.isPending(meta.get()));
        }));
        assertNull(meta.get());
        assertNull(version.get());
        assertEquals(1, cleanedFiles.get());
    }
    
    @Test
    void fileCleanupFailureKeepsDisabledNameReservation() throws Exception {
        doThrow(new NacosException(500, "storage unavailable")).when(files).delete(
            eq("target-ns"), eq("target"), eq("0.0.1"), eq("nacos_config"), any());
        assertThrows(NacosException.class, () -> run(() -> {
            throw new NacosException(404, "withdrawn");
        }));
        assertNotNull(meta.get());
        assertEquals("disable", meta.get().getStatus());
        assertNotNull(version.get());
        Map<?, ?> descriptor = JacksonUtils.toObj(version.get().getStorage(), Map.class);
        assertEquals("nacos_config", descriptor.get("provider"));
        assertEquals("target-ns:target:0.0.1", descriptor.get("scope"));
        assertEquals(java.util.List.of("SKILL.md"), descriptor.get("files"));
        verify(commit, never()).deleteDraftVersion(eq(900L), eq("target-ns"), eq("target"),
            anyLong(), eq(2001L), eq("0.0.1"));
        verify(commit, never()).deleteDisabled(eq(900L), eq("target-ns"), eq("target"), anyLong());
    }
    
    @Test
    void replacedDraftVersionMustNotBeDeletedDuringCompensation() {
        AtomicInteger checks = new AtomicInteger();
        assertThrows(NacosException.class, () -> run(() -> {
            if (checks.incrementAndGet() == 1) {
                version.get().setId(9999L);
                throw new NacosException(404, "withdrawn");
            }
        }));
        assertNotNull(meta.get());
        assertEquals(9999L, version.get().getId());
        assertEquals(0, cleanedFiles.get());
        assertTrue(AiResourceImportUtils.isPending(meta.get()));
    }
    
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void earlyFailureAndFailedCleanupRetainRecoveryWithoutVersion(boolean failVersionInsert)
        throws Exception {
        doThrow(new NacosException(500, "cleanup failed")).when(files).delete(
            eq("target-ns"), eq("target"), eq("0.0.1"), eq("nacos_config"), any());
        if (failVersionInsert) {
            when(versions.insert(any())).thenThrow(new IllegalStateException("SQL unavailable"));
        } else {
            doThrow(new NacosException(500, "partial write")).when(files).save(
                eq("target-ns"), eq("target"), eq("0.0.1"), eq(contents), any());
        }
        assertThrows(NacosException.class, () -> run(() -> {
        }));
        assertNull(version.get());
        assertTrue(AiResourceImportUtils.isPending(meta.get()));
        Map<?, ?> ext = JacksonUtils.toObj(meta.get().getExt(), Map.class);
        Map<?, ?> recovery = (Map<?, ?>) ext.get("importRecovery");
        assertNotNull(recovery.get("storage"));
        assertEquals(1, ((java.util.List<?>) recovery.get("files")).size());
        assertEquals(409, assertThrows(NacosException.class,
            () -> AiResourceImportUtils.checkNotPending(meta.get())).getErrCode());
    }
    
    private HubSkillTransferResult run(HubSkillDraftTransferWriter.RunnableWithNacosException check)
        throws NacosException {
        Map<String, Object> origin = new HashMap<>();
        origin.put("sourceNamespaceId", "source-a");
        origin.put("sourceVersion", "1.0.0");
        return writer.create(source, "target-ns", "target", "0.0.1", contents, origin, check);
    }
}
