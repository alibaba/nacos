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
import com.alibaba.nacos.ai.service.repository.AiResourceVersionPersistService;
import com.alibaba.nacos.ai.service.resource.AiResourceManager;
import com.alibaba.nacos.ai.utils.HubResourceReferenceJsonUtil;
import com.alibaba.nacos.ai.utils.SkillContentDigestUtils;
import com.alibaba.nacos.api.ai.model.hub.HubSkillContent;
import com.alibaba.nacos.api.ai.model.hub.HubSkillDownload;
import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.ai.model.skills.SkillResource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Source storage reader rejects stale identities, missing main content, and poisoned file lists. */
class HubSkillSourceContentReaderTest {
    
    private final AiResourceManager manager = mock(AiResourceManager.class);
    private final HubSkillReadAccessService access = mock(HubSkillReadAccessService.class);
    private final AiResourceVersionPersistService versionPersistence =
        mock(AiResourceVersionPersistService.class);
    private final HubSkillSourceContentReader reader =
        new HubSkillSourceContentReader(manager, access, versionPersistence);
    
    @Test
    void persistedDigestDoesNotLoadContentOrIncrementDownloads() throws Exception {
        ResolvedHubSkillReference publication = publication();
        version("{\"files\":[\"SKILL.md\"],\"contentMd5\":\"published-digest\"}");
        try (MockedStatic<AiResourceStorageRouter> storage =
            mockStatic(AiResourceStorageRouter.class);
            MockedStatic<NotifyCenter> events = mockStatic(NotifyCenter.class)) {
            assertEquals("published-digest", reader.readContentMd5(publication));
            storage.verifyNoInteractions();
            events.verifyNoInteractions();
        }
        verify(access).requireReadable(publication);
        verify(access).requireStillReadable(publication);
        verifyNoInteractions(versionPersistence);
    }
    
    @Test
    void missingOrMalformedPersistedDigestCannotProduceCacheHit() throws Exception {
        for (String storage : new String[] {"{}", "null", "{bad",
            "{\"contentMd5\":3}", "{\"contentMd5\":\" \"}"}) {
            version(storage);
            assertNull(reader.readContentMd5(publication()));
        }
        version("{}").setId(9999L);
        assertThrows(NacosException.class, () -> reader.readContentMd5(publication()));
        verifyNoInteractions(versionPersistence);
    }
    
    @Test
    void legacyBackfillUsesCanonicalTextAndBinaryContentWithoutCountingDownloads()
        throws Exception {
        ResolvedHubSkillReference publication = publication();
        try (MockedStatic<AiResourceStorageRouter> router = legacyContent();
            MockedStatic<NotifyCenter> events = mockStatic(NotifyCenter.class)) {
            String md5 = reader.backfillContentMd5(publication);
            assertEquals(canonicalLegacyMd5(), md5);
            verify(versionPersistence).updateStorageMd5("source-a", "find-skills", "skill",
                "1.0.0", md5);
            events.verifyNoInteractions();
        }
        verify(access, times(3)).requireStillReadable(publication);
    }
    
    @Test
    void persistenceFailureStillReturnsDigestAfterFinalRevocationFence() throws Exception {
        ResolvedHubSkillReference publication = publication();
        when(versionPersistence.updateStorageMd5("source-a", "find-skills", "skill",
            "1.0.0", canonicalLegacyMd5())).thenThrow(new IllegalStateException("db unavailable"));
        try (MockedStatic<AiResourceStorageRouter> router = legacyContent()) {
            assertEquals(canonicalLegacyMd5(), reader.backfillContentMd5(publication));
        }
        verify(access, times(3)).requireStillReadable(publication);
    }
    
    @Test
    void revokedShareCannotBackfillOrReturnDigest() throws Exception {
        ResolvedHubSkillReference publication = publication();
        doNothing().doThrow(new NacosException(NacosException.NO_RIGHT, "revoked"))
            .when(access).requireStillReadable(publication);
        try (MockedStatic<AiResourceStorageRouter> router = legacyContent()) {
            assertEquals(NacosException.NO_RIGHT,
                assertThrows(NacosException.class,
                    () -> reader.backfillContentMd5(publication)).getErrCode());
        }
        verifyNoInteractions(versionPersistence);
    }
    
    @Test
    void revocationAfterPersistenceStillPreventsReturningDigest() throws Exception {
        ResolvedHubSkillReference publication = publication();
        doNothing().doNothing().doThrow(new NacosException(NacosException.NO_RIGHT, "revoked"))
            .when(access).requireStillReadable(publication);
        try (MockedStatic<AiResourceStorageRouter> router = legacyContent()) {
            assertEquals(NacosException.NO_RIGHT,
                assertThrows(NacosException.class,
                    () -> reader.backfillContentMd5(publication)).getErrCode());
        }
        verify(versionPersistence).updateStorageMd5("source-a", "find-skills", "skill",
            "1.0.0", canonicalLegacyMd5());
    }
    
    private MockedStatic<AiResourceStorageRouter> legacyContent() throws Exception {
        version("{\"files\":[\"SKILL.md\",\"scripts/run.sh\",\"assets/icon.png\",\"_meta.json\"]}");
        AiResourceStorageRouter router = mock(AiResourceStorageRouter.class);
        AiResourceStorage storage = mock(AiResourceStorage.class);
        when(router.route(any(StorageKey.class))).thenReturn(storage);
        when(storage.get(any(StorageKey.class))).thenAnswer(invocation -> {
            StorageKey key = invocation.getArgument(0);
            String text = key.getKey().endsWith("SKILL.md") ? "# Original"
                : key.getKey().endsWith("icon.png") ? "AP+A" : "echo works";
            return text.getBytes(StandardCharsets.UTF_8);
        });
        MockedStatic<AiResourceStorageRouter> mocked = mockStatic(AiResourceStorageRouter.class);
        mocked.when(AiResourceStorageRouter::getInstance).thenReturn(router);
        return mocked;
    }
    
    private String canonicalLegacyMd5() {
        Skill skill = new Skill();
        skill.setSkillMd("# Original");
        SkillResource script = new SkillResource();
        script.setName("run.sh");
        script.setType("scripts");
        script.setContent("echo works");
        SkillResource image = new SkillResource();
        image.setName("icon.png");
        image.setType("assets");
        image.setContent("AP+A");
        skill.setResource(Map.of("scripts::run.sh", script, "assets::icon.png", image));
        return SkillContentDigestUtils.computeContentMd5(skill);
    }
    
    @Test
    void loadsOnlyOriginalVersionAndDoesNotExposeMetaFile() throws Exception {
        ResolvedHubSkillReference publication = publication();
        version(
            "{\"provider\":\"nacos_config\",\"files\":[\"SKILL.md\",\"scripts/run.sh\",\"_meta.json\"]}");
        AiResourceStorageRouter router = mock(AiResourceStorageRouter.class);
        AiResourceStorage storage = mock(AiResourceStorage.class);
        when(router.route(any(StorageKey.class))).thenReturn(storage);
        when(storage.get(any(StorageKey.class))).thenAnswer(invocation -> {
            StorageKey key = invocation.getArgument(0);
            if (key.getKey().endsWith("SKILL.md")) {
                return "---\nname: find-skills\n---\nbody".getBytes(StandardCharsets.UTF_8);
            }
            return "echo works".getBytes(StandardCharsets.UTF_8);
        });
        try (MockedStatic<AiResourceStorageRouter> mocked =
            mockStatic(AiResourceStorageRouter.class)) {
            mocked.when(AiResourceStorageRouter::getInstance).thenReturn(router);
            HubSkillContent result = reader.load(publication);
            assertEquals("find-skills", result.getDisplayName());
            assertEquals("1.0.0", result.getSharedVersion());
            assertEquals(1, result.getResource().size());
            assertFalse(result.getResource().containsKey("_meta.json"));
        }
        verify(access).requireReadable(publication);
        verify(access).requireStillReadable(publication);
    }
    
    @Test
    void mismatchedSourceVersionIdCannotBeRead() throws Exception {
        version("{\"files\":[\"SKILL.md\"]}").setId(9999L);
        assertEquals(NacosException.NOT_FOUND,
            assertThrows(NacosException.class, () -> reader.load(publication())).getErrCode());
    }
    
    @Test
    void storagePathTraversalIsRejectedBeforeRouterAccess() throws Exception {
        version("{\"files\":[\"SKILL.md\",\"../secret\"]}");
        assertEquals(NacosException.NOT_FOUND,
            assertThrows(NacosException.class, () -> reader.load(publication())).getErrCode());
    }
    
    @Test
    void missingSkillMdIsRejected() throws Exception {
        version("{\"files\":[\"scripts/run.sh\"]}");
        assertEquals(NacosException.NOT_FOUND,
            assertThrows(NacosException.class, () -> reader.load(publication())).getErrCode());
    }
    
    @Test
    void missingStorageFileRejectsRatherThanServingTruncatedContent() throws Exception {
        ResolvedHubSkillReference publication = publication();
        version("{\"files\":[\"SKILL.md\"]}");
        AiResourceStorageRouter router = mock(AiResourceStorageRouter.class);
        AiResourceStorage storage = mock(AiResourceStorage.class);
        when(router.route(any(StorageKey.class))).thenReturn(storage);
        try (MockedStatic<AiResourceStorageRouter> mocked =
            mockStatic(AiResourceStorageRouter.class)) {
            mocked.when(AiResourceStorageRouter::getInstance).thenReturn(router);
            assertEquals(NacosException.NOT_FOUND,
                assertThrows(NacosException.class, () -> reader.load(publication)).getErrCode());
        }
    }
    
    private AiResourceVersion version(String storage) {
        AiResourceVersion version = new AiResourceVersion();
        version.setId(199L);
        version.setName("find-skills");
        version.setNamespaceId("source-a");
        version.setType("skill");
        version.setStatus("online");
        version.setVersion("1.0.0");
        version.setStorage(storage);
        when(manager.findVersion("source-a", "find-skills", "skill", "1.0.0"))
            .thenReturn(version);
        return version;
    }
    
    private ResolvedHubSkillReference publication() throws Exception {
        AiResource hub = new AiResource();
        hub.setId(5L);
        hub.setMetaVersion(8L);
        hub.setName("find-skills-101");
        AiResourceVersion version = new AiResourceVersion();
        version.setId(99L);
        return new ResolvedHubSkillReference(hub, version,
            HubResourceReferenceJsonUtil.build("source-a", 101L, "find-skills", 199L, "1.0.0"));
    }
    
    @Test
    void databaseFailureDoesNotExposeSourceIdentity() throws Exception {
        when(manager.findVersion("source-a", "find-skills", "skill", "1.0.0"))
            .thenThrow(new IllegalStateException("jdbc-secret-source-a"));
        NacosException failure =
            assertThrows(NacosException.class, () -> reader.load(publication()));
        assertEquals(404, failure.getErrCode());
        assertFalse(failure.getErrMsg().contains("source-a"));
    }
    
    @Test
    void malformedDescriptorsAreRejectedBeforeStorageAccess() throws Exception {
        for (String descriptor : new String[] {"null", "{}", "{bad",
            "{\"provider\":3,\"files\":[\"SKILL.md\"]}",
            "{\"files\":[\"SKILL.md\",\"SKILL.md\"]}", "{\"files\":[\"SKILL.md\",\"./secret\"]}",
            "{\"files\":[\"SKILL.md\",\"assets/\"]}", "{\"files\":[\"SKILL.md\",3]}"}) {
            version(descriptor);
            assertEquals(404,
                assertThrows(NacosException.class, () -> reader.load(publication())).getErrCode());
        }
    }
    
    @Test
    void oversizedFileAndRevocationAfterStorageLoadBothFailClosed() throws Exception {
        ResolvedHubSkillReference publication = publication();
        version("{\"files\":[\"SKILL.md\"]}");
        AiResourceStorageRouter router = mock(AiResourceStorageRouter.class);
        AiResourceStorage storage = mock(AiResourceStorage.class);
        when(router.route(any(StorageKey.class))).thenReturn(storage);
        try (MockedStatic<AiResourceStorageRouter> mocked =
            mockStatic(AiResourceStorageRouter.class)) {
            mocked.when(AiResourceStorageRouter::getInstance).thenReturn(router);
            when(storage.get(any(StorageKey.class))).thenReturn(new byte[2 * 1024 * 1024 + 1]);
            assertEquals(404,
                assertThrows(NacosException.class, () -> reader.load(publication)).getErrCode());
            when(storage.get(any(StorageKey.class)))
                .thenReturn("# pinned".getBytes(StandardCharsets.UTF_8));
            org.mockito.Mockito.doThrow(new NacosException(403, "revoked")).when(access)
                .requireStillReadable(publication);
            assertEquals(403,
                assertThrows(NacosException.class, () -> reader.load(publication)).getErrCode());
        }
    }
    
    @Test
    void zipPreservesBinaryBytesAndOriginalMarkdownWithoutLeakingStorage() throws Exception {
        ResolvedHubSkillReference publication = publication();
        version("{\"files\":[\"SKILL.md\",\"assets/icon.png\",\"_meta.json\"]}");
        byte[] markdown =
            "---\nname: find-skills\n---\n# Original".getBytes(StandardCharsets.UTF_8);
        byte[] binary = new byte[] {0, (byte) 255, (byte) 128, 13, 10};
        AiResourceStorageRouter router = mock(AiResourceStorageRouter.class);
        AiResourceStorage storage = mock(AiResourceStorage.class);
        when(router.route(any(StorageKey.class))).thenReturn(storage);
        when(storage.get(any(StorageKey.class))).thenAnswer(invocation -> {
            StorageKey key = invocation.getArgument(0);
            return key.getKey().endsWith("SKILL.md") ? markdown
                : java.util.Base64.getEncoder().encode(binary);
        });
        try (MockedStatic<AiResourceStorageRouter> mocked =
            mockStatic(AiResourceStorageRouter.class)) {
            mocked.when(AiResourceStorageRouter::getInstance).thenReturn(router);
            HubSkillDownload download = reader.download(publication);
            assertEquals("1.0.0", download.getSharedVersion());
            java.util.Map<String, byte[]> files = new HashMap<>();
            try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(download.getZipBytes()), StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    files.put(entry.getName(), zip.readAllBytes());
                }
            }
            assertEquals(2, files.size());
            org.junit.jupiter.api.Assertions.assertArrayEquals(markdown,
                files.get("find-skills/SKILL.md"));
            org.junit.jupiter.api.Assertions.assertArrayEquals(binary,
                files.get("find-skills/assets/icon.png"));
            assertFalse(files.containsKey("find-skills/_meta.json"));
        }
        verify(access).requireStillReadable(publication);
    }
    
    @Test
    void poisonedDownloadPathsAndRecreatedVersionFailClosed() throws Exception {
        version("{\"files\":[\"SKILL.md\",\"../secret\"]}");
        assertThrows(NacosException.class, () -> reader.download(publication()));
        version("{\"files\":[\"SKILL.md\"]}").setId(200L);
        assertThrows(NacosException.class, () -> reader.download(publication()));
    }
    
}
