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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.config.ConditionalOnAiResourceSearchEnabled;
import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.ai.service.resource.AiResourceFileReader;
import com.alibaba.nacos.api.ai.model.prompt.PromptUtils;
import com.alibaba.nacos.api.ai.model.prompt.PromptVariable;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Loads stored AI resource files as compact input for AI resource indexing and enhancement.
 *
 * @author nacos
 */
@Service
@ConditionalOnAiResourceSearchEnabled
public class AiResourceIndexContentLoaderImpl implements AiResourceIndexContentLoader {
    
    private static final String SKILL_MD_RESOURCE_NAME = "SKILL.md";
    
    static final String KEY_MAX_CONTENT_CHARS =
        "nacos.ai.resource.search.index.enhancement.max-content-chars";
    
    private static final int DEFAULT_MAX_CONTENT_CHARS = 12000;
    
    private final AiResourceFileReader fileReader;
    
    public AiResourceIndexContentLoaderImpl(AiResourceFileReader fileReader) {
        this.fileReader = fileReader;
    }
    
    @Override
    public List<AiResourceIndexEnhancementContent> load(AiResourceSearchDocument entry,
        AiResourceVersion version)
        throws Exception {
        if (entry == null || version == null || StringUtils.isBlank(version.getStorage())) {
            return Collections.emptyList();
        }
        if (AiResourceConstants.RESOURCE_TYPE_SKILL.equals(entry.getResourceType())) {
            return loadSkillContent(entry, version);
        }
        if (AiResourceConstants.RESOURCE_TYPE_PROMPT.equals(entry.getResourceType())) {
            return loadPromptContent(entry, version);
        }
        return Collections.emptyList();
    }
    
    private List<AiResourceIndexEnhancementContent> loadSkillContent(AiResourceSearchDocument entry,
        AiResourceVersion version) throws Exception {
        byte[] bytes = fileReader.read(version, entry.getNamespaceId(), entry.getResourceType(),
            entry.getResourceName(), entry.getResourceVersion(), SKILL_MD_RESOURCE_NAME);
        if (bytes == null || bytes.length == 0) {
            return Collections.emptyList();
        }
        String text = normalize(new String(bytes, StandardCharsets.UTF_8));
        if (StringUtils.isBlank(text)) {
            return Collections.emptyList();
        }
        return Collections
            .singletonList(new AiResourceIndexEnhancementContent(SKILL_MD_RESOURCE_NAME,
                limit(text, maxContentChars())));
    }
    
    private List<AiResourceIndexEnhancementContent> loadPromptContent(
        AiResourceSearchDocument entry,
        AiResourceVersion version) throws Exception {
        byte[] bytes = fileReader.read(version, entry.getNamespaceId(), entry.getResourceType(),
            entry.getResourceName(), entry.getResourceVersion(), PromptUtils.PROMPT_MAIN_DATA_ID);
        if (bytes == null || bytes.length == 0) {
            return Collections.emptyList();
        }
        String text = promptSearchText(new String(bytes, StandardCharsets.UTF_8));
        if (StringUtils.isBlank(text)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(new AiResourceIndexEnhancementContent(
            PromptUtils.PROMPT_MAIN_DATA_ID, limit(text, maxContentChars())));
    }
    
    private String normalize(String text) {
        return text.replace('\u0000', ' ').trim();
    }
    
    private String promptSearchText(String contentJson) {
        try {
            PromptVersionInfo prompt = JacksonUtils.toObj(contentJson, PromptVersionInfo.class);
            if (prompt == null) {
                return normalize(contentJson);
            }
            StringBuilder text = new StringBuilder();
            if (StringUtils.isNotBlank(prompt.getTemplate())) {
                appendLine(text, "# Prompt template");
                appendLine(text, prompt.getTemplate());
            }
            if (prompt.getVariables() != null && !prompt.getVariables().isEmpty()) {
                appendLine(text, "# Prompt variables");
                for (PromptVariable variable : prompt.getVariables()) {
                    appendLine(text, variableText(variable));
                }
            }
            return normalize(text.toString());
        } catch (Exception ignored) {
            return normalize(contentJson);
        }
    }
    
    private String variableText(PromptVariable variable) {
        if (variable == null || StringUtils.isBlank(variable.getName())) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        text.append("- variable ").append(variable.getName());
        if (StringUtils.isNotBlank(variable.getDescription())) {
            text.append(": ").append(variable.getDescription());
        }
        if (StringUtils.isNotBlank(variable.getDefaultValue())) {
            text.append(" default ").append(variable.getDefaultValue());
        }
        return text.toString();
    }
    
    private void appendLine(StringBuilder text, String line) {
        if (StringUtils.isBlank(line)) {
            return;
        }
        if (text.length() > 0) {
            text.append('\n');
        }
        text.append(line);
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
