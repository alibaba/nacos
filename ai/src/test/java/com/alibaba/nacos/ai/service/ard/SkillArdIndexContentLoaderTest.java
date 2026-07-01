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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.ard.ArdEntry;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.plugin.ai.storage.spi.AiResourceStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests for {@link SkillArdIndexContentLoader}.
 *
 * @author nacos
 */
class SkillArdIndexContentLoaderTest {
    
    @AfterEach
    void tearDown() {
        AiResourceStorageRouter.reset();
    }
    
    @Test
    void loadShouldOnlyReadSkillMdFromStorage() throws Exception {
        TestStorage storage = new TestStorage("test");
        AiResourceStorageRouter.join(storage);
        save(storage, "SKILL.md", "Create AI avatar and talking head videos.");
        save(storage, "templates/prompt.md", "Avatar video prompt template.");
        save(storage, "assets/logo.png", "binary");
        
        SkillArdIndexContentLoader loader = new SkillArdIndexContentLoader(
            AiResourceStorageRouter.getInstance());
        List<ArdIndexEnhancementContent> contents = loader.load(entry(), version());
        
        assertEquals(1, contents.size());
        assertEquals("SKILL.md", contents.get(0).getPath());
        assertFalse(contents.stream().anyMatch(content -> "templates/prompt.md".equals(
            content.getPath())));
    }
    
    private void save(TestStorage storage, String filePath, String content) throws Exception {
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(storage.type(), "public",
            "ai-video-avatar", "1.0.0", filePath);
        storage.save(key, content.getBytes(StandardCharsets.UTF_8));
    }
    
    private ArdEntry entry() {
        ArdEntry entry = new ArdEntry();
        entry.setNamespaceId("public");
        entry.setResourceType(Constants.Skills.RESOURCE_TYPE_SKILL);
        entry.setResourceName("ai-video-avatar");
        entry.setResourceVersion("1.0.0");
        return entry;
    }
    
    private AiResourceVersion version() {
        AiResourceVersion version = new AiResourceVersion();
        version.setStorage(JacksonUtils.toJson(Map.of("provider", "test", "files",
            List.of("SKILL.md", "templates/prompt.md", "assets/logo.png"))));
        return version;
    }
    
    private static class TestStorage implements AiResourceStorage {
        
        private final String type;
        
        private final Map<String, byte[]> values = new HashMap<>();
        
        private TestStorage(String type) {
            this.type = type;
        }
        
        @Override
        public String type() {
            return type;
        }
        
        @Override
        public void save(StorageKey storageKey, byte[] content) {
            values.put(storageKey.getKey(), content);
        }
        
        @Override
        public byte[] get(StorageKey storageKey) {
            return values.get(storageKey.getKey());
        }
        
        @Override
        public void delete(StorageKey storageKey) {
            values.remove(storageKey.getKey());
        }
    }
}
