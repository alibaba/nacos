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
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.ai.storage.AiResourceStorageRouter;
import com.alibaba.nacos.plugin.ai.storage.model.StorageKey;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Loads stored Skill files as compact input for ARD LLM enhancement.
 *
 * @author nacos
 */
@Service
public class SkillArdIndexContentLoader implements ArdIndexContentLoader {
    
    private static final String SKILL_MD_RESOURCE_NAME = "SKILL.md";
    
    static final String KEY_MAX_CONTENT_CHARS =
        "nacos.ai.ard.index.enhancement.max-content-chars";
    
    private static final int DEFAULT_MAX_CONTENT_CHARS = 12000;
    
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<Map<String, Object>>() {
        };
    
    private final AiResourceStorageRouter storageRouter;
    
    public SkillArdIndexContentLoader() {
        this(AiResourceStorageRouter.getInstance());
    }
    
    SkillArdIndexContentLoader(AiResourceStorageRouter storageRouter) {
        this.storageRouter = storageRouter;
    }
    
    @Override
    public List<ArdIndexEnhancementContent> load(ArdEntry entry, AiResourceVersion version)
        throws Exception {
        if (entry == null || version == null || !Constants.Skills.RESOURCE_TYPE_SKILL
            .equals(entry.getResourceType()) || StringUtils.isBlank(version.getStorage())) {
            return Collections.emptyList();
        }
        Map<String, Object> storage = parseStorage(version.getStorage());
        List<String> files = parseFiles(storage.get("files"));
        if (files.isEmpty()) {
            return Collections.emptyList();
        }
        String provider = provider(storage.get("provider"));
        if (!files.contains(SKILL_MD_RESOURCE_NAME)) {
            return Collections.emptyList();
        }
        StorageKey key = NacosConfigAiResourceStorage.buildStorageKey(provider,
            entry.getNamespaceId(), entry.getResourceName(), entry.getResourceVersion(),
            SKILL_MD_RESOURCE_NAME);
        byte[] bytes = storageRouter.route(key).get(key);
        if (bytes == null || bytes.length == 0) {
            return Collections.emptyList();
        }
        String text = normalize(new String(bytes, StandardCharsets.UTF_8));
        if (StringUtils.isBlank(text)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new ArdIndexEnhancementContent(SKILL_MD_RESOURCE_NAME,
            limit(text, maxContentChars())));
    }
    
    private Map<String, Object> parseStorage(String storageJson) {
        try {
            Map<String, Object> parsed = JacksonUtils.toObj(storageJson, MAP_TYPE);
            return parsed == null ? Collections.emptyMap() : parsed;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
    
    private List<String> parseFiles(Object files) {
        if (!(files instanceof Collection)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object file : (Collection<?>) files) {
            if (file != null && StringUtils.isNotBlank(String.valueOf(file))) {
                result.add(String.valueOf(file));
            }
        }
        return result;
    }
    
    private String provider(Object provider) {
        String value = provider == null ? null : String.valueOf(provider);
        return StringUtils.isBlank(value) ? NacosConfigAiResourceStorage.TYPE : value;
    }
    
    private String normalize(String text) {
        return text.replace('\u0000', ' ').trim();
    }
    
    private String limit(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
    
    private int maxContentChars() {
        return positiveInt(KEY_MAX_CONTENT_CHARS, DEFAULT_MAX_CONTENT_CHARS);
    }
    
    private int positiveInt(String key, int defaultValue) {
        String value = property(key, String.valueOf(defaultValue));
        try {
            return Integer.max(1, Integer.parseInt(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
    
    private String property(String key, String defaultValue) {
        try {
            return EnvUtil.getProperty(key, defaultValue);
        } catch (Exception ignored) {
            String value = System.getProperty(key);
            return StringUtils.isBlank(value) ? defaultValue : value;
        }
    }
}
