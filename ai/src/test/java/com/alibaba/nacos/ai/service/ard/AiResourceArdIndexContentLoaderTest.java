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
import com.alibaba.nacos.api.ai.model.prompt.PromptUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptVariable;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AiResourceArdIndexContentLoader}.
 *
 * @author nacos
 */
class AiResourceArdIndexContentLoaderTest {
    
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
        
        AiResourceArdIndexContentLoader loader = new AiResourceArdIndexContentLoader(
            AiResourceStorageRouter.getInstance());
        List<ArdIndexEnhancementContent> contents = loader.load(entry(), version());
        
        assertEquals(1, contents.size());
        assertEquals("SKILL.md", contents.get(0).getPath());
        assertFalse(contents.stream().anyMatch(content -> "templates/prompt.md".equals(
            content.getPath())));
    }
    
    @Test
    void loadShouldReadPromptContentFromStorage() throws Exception {
        TestStorage storage = new TestStorage("test");
        AiResourceStorageRouter.join(storage);
        PromptVersionInfo prompt = new PromptVersionInfo();
        prompt.setTemplate("生成头像视频脚本，适合数字人介绍产品");
        prompt.setVariables(List.of(new PromptVariable("avatar", null, "头像图片 URL")));
        savePrompt(storage, JacksonUtils.toJson(prompt));
        
        AiResourceArdIndexContentLoader loader = new AiResourceArdIndexContentLoader(
            AiResourceStorageRouter.getInstance());
        List<ArdIndexEnhancementContent> contents = loader.load(promptEntry(), promptVersion());
        
        assertEquals(1, contents.size());
        assertEquals(PromptUtils.PROMPT_MAIN_DATA_ID, contents.get(0).getPath());
        assertFalse(contents.get(0).getText().contains("{\"template\""));
        assertFalse(contents.get(0).getText().contains("\"variables\""));
        assertTrue(contents.get(0).getText().contains("头像图片 URL"));
    }
    
    private void save(TestStorage storage, String filePath, String content) throws Exception {
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(storage.type(), "public",
            "ai-video-avatar", "1.0.0", filePath);
        storage.save(key, content.getBytes(StandardCharsets.UTF_8));
    }
    
    private void savePrompt(TestStorage storage, String content) throws Exception {
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(storage.type(), "public",
            NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, "avatar-prompt", "1.0.0",
            PromptUtils.PROMPT_MAIN_DATA_ID);
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
    
    private ArdEntry promptEntry() {
        ArdEntry entry = new ArdEntry();
        entry.setNamespaceId("public");
        entry.setResourceType(NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT);
        entry.setResourceName("avatar-prompt");
        entry.setResourceVersion("1.0.0");
        return entry;
    }
    
    private AiResourceVersion version() {
        AiResourceVersion version = new AiResourceVersion();
        version.setStorage(JacksonUtils.toJson(Map.of("provider", "test", "files",
            List.of("SKILL.md", "templates/prompt.md", "assets/logo.png"))));
        return version;
    }
    
    private AiResourceVersion promptVersion() {
        AiResourceVersion version = new AiResourceVersion();
        version.setStorage(JacksonUtils.toJson(Map.of("provider", "test", "files",
            List.of(PromptUtils.PROMPT_MAIN_DATA_ID))));
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
