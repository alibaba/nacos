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
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.service.VisibilityHelper;
import com.alibaba.nacos.ai.service.repository.AiResourcePersistService;
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.model.hub.HubResourceType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
class HubResourceDraftTransferWriterTest {
    
    private final AiResourcePersistService resources = mock(AiResourcePersistService.class);
    private final AiResourceVersionPersistService versions =
        mock(AiResourceVersionPersistService.class);
    private final HubResourceDraftCommitStorage commit = mock(HubResourceDraftCommitStorage.class);
    private final HubResourceDraftTransferWriter.PreparedContent contents =
        mock(HubResourceDraftTransferWriter.PreparedContent.class);
    private final HubResourceDraftTransferWriter writer =
        new HubResourceDraftTransferWriter(resources, versions, commit);
    
    private final AtomicReference<AiResource> meta = new AtomicReference<>();
    private final AtomicReference<AiResourceVersion> version = new AtomicReference<>();
    private final AtomicInteger cleanedFiles = new AtomicInteger();
    private MockedStatic<VisibilityHelper> visibility;
    
    @BeforeEach
    void setUp() throws Exception {
        visibility = mockStatic(VisibilityHelper.class);
        visibility.when(VisibilityHelper::resolveCurrentIdentity).thenReturn("alice");
        when(contents.write()).thenReturn("independent target descriptor");
        when(resources.find("target-ns", "target", "prompt")).thenAnswer(i -> meta.get());
        when(versions.find("target-ns", "target", "prompt", "0.0.1"))
            .thenAnswer(i -> version.get());
        when(resources.insert(any(AiResource.class))).thenAnswer(i -> {
            AiResource inserted = i.getArgument(0);
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
        when(commit.deleteDraftVersion(eq(HubResourceType.PROMPT), eq(900L), eq("target-ns"),
            eq("target"), anyLong(),
            eq(2001L), eq("0.0.1"))).thenAnswer(i -> {
                version.set(null);
                return true;
            });
        when(commit.publish(HubResourceType.PROMPT, 900L, "target-ns", "target", 1L))
            .thenAnswer(i -> {
                meta.get().setStatus(AiResourceConstants.META_STATUS_ENABLE);
                meta.get().setMetaVersion(2L);
                return true;
            });
        when(commit.disable(HubResourceType.PROMPT, 900L, "target-ns", "target", 2L))
            .thenAnswer(i -> {
                meta.get().setStatus(AiResourceConstants.META_STATUS_DISABLE);
                meta.get().setMetaVersion(3L);
                return true;
            });
        when(commit.deleteDisabled(eq(HubResourceType.PROMPT), eq(900L), eq("target-ns"),
            eq("target"), anyLong()))
            .thenAnswer(i -> {
                meta.set(null);
                return true;
            });
        doAnswer(i -> {
            assertNotNull(meta.get());
            assertEquals("disable", meta.get().getStatus());
            cleanedFiles.incrementAndGet();
            return null;
        }).when(contents).cleanup();
    }
    
    @AfterEach
    void tearDown() {
        visibility.close();
    }
    
    @Test
    void happyPathCreatesIndependentDraftWithOriginInFirstMetaInsert() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        HubResourceDraftTransferWriter.CreatedDraft result = run(() -> checks.incrementAndGet());
        assertEquals("0.0.1", result.version());
        assertEquals(900L, result.resourceId());
        assertEquals(2, checks.get());
        assertEquals(AiResourceConstants.META_STATUS_ENABLE, meta.get().getStatus());
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
        when(contents.write()).thenThrow(new NacosException(500, "failed after write"));
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
    void sourceOfflineJustAfterActivationCompensatesVisibleDraft() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(NacosException.class, () -> run(() -> {
            if (calls.incrementAndGet() == 2) {
                throw new NacosException(NacosException.NOT_FOUND, "source withdrawn");
            }
        }));
        assertEquals(2, calls.get());
        assertNull(meta.get());
        assertNull(version.get());
        assertEquals(1, cleanedFiles.get());
        verify(commit).disable(HubResourceType.PROMPT, 900L, "target-ns", "target", 2L);
    }
    
    @Test
    void concurrentTargetEditAfterActivationPreventsUnsafeRollback() throws Exception {
        doReturn(false).when(commit).disable(HubResourceType.PROMPT, 900L, "target-ns", "target",
            2L);
        AtomicInteger calls = new AtomicInteger();
        assertThrows(NacosException.class, () -> run(() -> {
            if (calls.incrementAndGet() == 2) {
                throw new NacosException(NacosException.NOT_FOUND, "source offline");
            }
        }));
        assertNotNull(meta.get());
        assertNotNull(version.get());
        assertEquals(0, cleanedFiles.get());
        verify(commit, never()).deleteDisabled(eq(HubResourceType.PROMPT), eq(900L),
            eq("target-ns"),
            eq("target"), anyLong());
    }
    
    @Test
    void fileCleanupFailureKeepsDisabledNameReservation() throws Exception {
        doThrow(new NacosException(500, "storage unavailable")).when(contents).cleanup();
        assertThrows(NacosException.class, () -> run(() -> {
            throw new NacosException(404, "withdrawn");
        }));
        assertNotNull(meta.get());
        assertEquals("disable", meta.get().getStatus());
        assertNotNull(version.get());
        assertEquals(2001L, version.get().getId());
        assertEquals("independent target descriptor", version.get().getStorage());
        verify(commit, never()).deleteDraftVersion(eq(HubResourceType.PROMPT), eq(900L),
            eq("target-ns"), eq("target"), anyLong(), anyLong(), eq("0.0.1"));
        verify(commit, never()).deleteDisabled(eq(HubResourceType.PROMPT), eq(900L),
            eq("target-ns"), eq("target"), anyLong());
    }
    
    @Test
    void replacedDraftVersionMustNotBeDeletedDuringCompensation() {
        AtomicInteger checks = new AtomicInteger();
        assertThrows(NacosException.class, () -> run(() -> {
            if (checks.incrementAndGet() == 2) {
                version.get().setId(9999L);
                throw new NacosException(404, "withdrawn");
            }
        }));
        assertNotNull(meta.get());
        assertEquals(9999L, version.get().getId());
        assertEquals(0, cleanedFiles.get());
        verify(commit, never()).disable(HubResourceType.PROMPT, 900L, "target-ns", "target", 2L);
    }
    
    private HubResourceDraftTransferWriter.CreatedDraft run(
        HubResourceDraftTransferWriter.RunnableWithNacosException check)
        throws NacosException {
        Map<String, Object> origin = new HashMap<>();
        origin.put("schemaVersion", 1);
        origin.put("sourceNamespaceId", "source-a");
        origin.put("sourceResourceId", 101L);
        origin.put("sourceName", "same-name");
        origin.put("sourceVersionId", 201L);
        origin.put("sourceVersion", "1.0.0");
        origin.put("importedAt", "2026-10-23T01:00:00Z");
        return writer.create(HubResourceType.PROMPT, "target-ns", "target", "0.0.1",
            JacksonUtils.toJson(java.util.Collections.singletonMap("origin", origin)),
            "Independent target", null, contents, check);
    }
}
