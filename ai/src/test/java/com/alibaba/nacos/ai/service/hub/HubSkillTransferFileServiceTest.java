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
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

/** Copy real bytes through the storage SPI, with safe source descriptors. */
class HubSkillTransferFileServiceTest {
    
    private final HubSkillTransferFileService service = new HubSkillTransferFileService();
    private MockedStatic<AiResourceStorageRouter> routerStatic;
    private final AiResourceStorageRouter router = mock(AiResourceStorageRouter.class);
    private final AiResourceStorage storage = mock(AiResourceStorage.class);
    private final Map<String, byte[]> store = new HashMap<>();
    private HubSkillTransferSourceSnapshot source;
    private AiResourceVersion version;
    
    @BeforeEach
    void setUp() throws Exception {
        routerStatic = mockStatic(AiResourceStorageRouter.class);
        routerStatic.when(AiResourceStorageRouter::getInstance).thenReturn(router);
        when(router.route(any(StorageKey.class))).thenReturn(storage);
        when(storage.get(any(StorageKey.class))).thenAnswer(i -> {
            StorageKey k = i.getArgument(0);
            return store.get(k.getKey());
        });
        AiResource meta = new AiResource();
        meta.setId(111L);
        meta.setNamespaceId("source-ns");
        meta.setName("find-skills");
        version = new AiResourceVersion();
        version.setId(501L);
        version.setVersion("1.0.0");
        version.setNamespaceId("source-ns");
        version.setName("find-skills");
        version.setType("skill");
        version.setStatus("online");
        source = new HubSkillTransferSourceSnapshot(meta, version, 801L);
        store.put("source-ns:find-skills:1.0.0:SKILL.md",
            ("---\nname: find-skills\ndescription: sample\nversion: 1.0.0\n---\n\nBody")
                .getBytes(StandardCharsets.UTF_8));
        store.put("source-ns:find-skills:1.0.0:scripts/helper.sh",
            "echo hello".getBytes(StandardCharsets.UTF_8));
        store.put("source-ns:find-skills:1.0.0:assets/logo.png", new byte[] {0, 1, 3, -1});
        store.put("source-ns:find-skills:1.0.0:_meta.json",
            "{\"version\":\"1.0.0\"}".getBytes(StandardCharsets.UTF_8));
        descriptor(List.of("SKILL.md", "scripts/helper.sh", "assets/logo.png", "_meta.json"));
    }
    
    @AfterEach
    void close() {
        routerStatic.close();
    }
    
    @Test
    void targetFrontmatterNameAndVersionChangeButBinaryBytesRemainExact() throws Exception {
        HubSkillTransferFileService.Files files = service.load(source, version,
            "renamed-skill", "0.0.1");
        String md = new String(files.getContents().get("SKILL.md"), StandardCharsets.UTF_8);
        assertTrue(md.contains("name: renamed-skill"));
        assertTrue(md.contains("version: 0.0.1"));
        assertFalse(md.contains("name: find-skills"));
        assertArrayEquals(new byte[] {0, 1, 3, -1}, files.getContents().get("assets/logo.png"));
        assertFalse(files.getContents().containsKey("_meta.json"));
    }
    
    @Test
    void unsafeSourcePathMustBeRejectedBeforeStorageRead() {
        descriptor(List.of("SKILL.md", "../other-secret"));
        clearInvocations(storage);
        assertThrows(NacosException.class, () -> service.load(source, version,
            "target", "0.0.1"));
        verifyNoInteractions(storage);
    }
    
    @Test
    void missingSkillMdMustNotBeImported() {
        descriptor(List.of("scripts/helper.sh"));
        assertThrows(NacosException.class, () -> service.load(source, version,
            "target", "0.0.1"));
    }
    
    @Test
    void sourceVersionIdMismatchMustNotReadAnyFiles() {
        version.setId(900L);
        assertThrows(NacosException.class, () -> service.load(source, version,
            "target", "0.0.1"));
    }
    
    @Test
    void changedStorageDescriptorMustNotBeRead() {
        version.setStorage("{\"files\":[\"secret\"]}");
        clearInvocations(storage);
        assertThrows(NacosException.class, () -> service.load(source, version, "target", "0.0.1"));
        verifyNoInteractions(storage);
    }
    
    @Test
    void fileBytesAreIndependentOfStorageAndReturnedMaps() throws Exception {
        HubSkillTransferFileService.Files files = service.load(source, version, "target", "0.0.1");
        store.get("source-ns:find-skills:1.0.0:assets/logo.png")[0] = 42;
        files.getContents().get("assets/logo.png")[1] = 42;
        assertArrayEquals(new byte[] {0, 1, 3, -1}, files.getContents().get("assets/logo.png"));
    }
    
    @Test
    void malformedUtf8MustNotBeSilentlyReplaced() {
        store.put("source-ns:find-skills:1.0.0:SKILL.md", new byte[] {(byte) 0xc3, 0x28});
        assertThrows(NacosException.class, () -> service.load(source, version, "target", "0.0.1"));
    }
    
    private void descriptor(List<String> files) {
        Map<String, Object> storageDescriptor = new HashMap<>();
        storageDescriptor.put("provider", "nacos_config");
        storageDescriptor.put("files", files);
        version.setStorage(JacksonUtils.toJson(storageDescriptor));
        AiResource meta = new AiResource();
        meta.setId(111L);
        meta.setNamespaceId("source-ns");
        meta.setName("find-skills");
        source = new HubSkillTransferSourceSnapshot(meta, version, 801L);
    }
}
