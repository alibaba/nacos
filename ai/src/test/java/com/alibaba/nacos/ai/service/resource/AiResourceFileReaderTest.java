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

package com.alibaba.nacos.ai.service.resource;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.model.prompt.PromptUtils;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link AiResourceFileReader}.
 *
 * @author nacos
 */
class AiResourceFileReaderTest {
    
    @AfterEach
    void tearDown() {
        AiResourceStorageRouter.reset();
    }
    
    @Test
    void readShouldSupportSkillAndTypedResourceKeys() throws Exception {
        TestStorage storage = new TestStorage();
        AiResourceStorageRouter.join(storage);
        byte[] skillContent = "# Skill".getBytes(StandardCharsets.UTF_8);
        byte[] promptContent = "{\"template\":\"hello\"}".getBytes(StandardCharsets.UTF_8);
        storage.save(NacosConfigAiResourceStorage.buildStorageKey(storage.type(), "public",
            "demo-skill", "1.0.0", "SKILL.md"), skillContent);
        storage.save(NacosConfigAiResourceStorage.buildStorageKey(storage.type(), "public",
            AiResourceConstants.RESOURCE_TYPE_PROMPT, "demo-prompt", "1.0.0",
            PromptUtils.PROMPT_MAIN_DATA_ID), promptContent);
        AiResourceFileReader reader = new AiResourceFileReader(
            AiResourceStorageRouter.getInstance());
        
        assertArrayEquals(skillContent, reader.read(version("SKILL.md"), "public",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "demo-skill", "1.0.0", "SKILL.md"));
        assertArrayEquals(promptContent, reader.read(version(PromptUtils.PROMPT_MAIN_DATA_ID),
            "public", AiResourceConstants.RESOURCE_TYPE_PROMPT, "demo-prompt", "1.0.0",
            PromptUtils.PROMPT_MAIN_DATA_ID));
    }
    
    @Test
    void readShouldIgnoreFilesNotDeclaredByVersion() throws Exception {
        TestStorage storage = new TestStorage();
        AiResourceStorageRouter.join(storage);
        AiResourceFileReader reader = new AiResourceFileReader(
            AiResourceStorageRouter.getInstance());
        
        assertNull(reader.read(version("README.md"), "public",
            AiResourceConstants.RESOURCE_TYPE_SKILL, "demo-skill", "1.0.0", "SKILL.md"));
    }
    
    private AiResourceVersion version(String filePath) {
        AiResourceVersion version = new AiResourceVersion();
        version.setStorage(JacksonUtils.toJson(Map.of("provider", "test", "files",
            List.of(filePath))));
        return version;
    }
    
    private static class TestStorage implements AiResourceStorage {
        
        private final Map<String, byte[]> values = new HashMap<>();
        
        @Override
        public String type() {
            return "test";
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
