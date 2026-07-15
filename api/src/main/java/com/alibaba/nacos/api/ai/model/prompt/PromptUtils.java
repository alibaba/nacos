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

package com.alibaba.nacos.api.ai.model.prompt;

import com.alibaba.nacos.api.ai.model.NacosAiConfigKeyCodec;
import com.alibaba.nacos.api.utils.StringUtils;

/**
 * Utility class for Prompt storage operations.
 * Mirrors {@link com.alibaba.nacos.api.ai.model.skills.SkillUtils} and
 * {@link com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecUtils} patterns with Prompt-specific constants.
 *
 * @author nacos
 */
public class PromptUtils {
    
    /**
     * Prompt versioned group prefix. Each prompt version is stored in a group named
     * {@code prompt__{enc_promptKey}__{enc_version}}.
     */
    public static final String PROMPT_GROUP_PREFIX = "prompt__";
    
    /**
     * Main content dataId for prompt version storage.
     */
    public static final String PROMPT_MAIN_DATA_ID = "content.json";
    
    private static final String DOUBLE_UNDERSCORE = "__";
    
    private PromptUtils() {
    }
    
    /**
     * Build the Nacos Config group for a specific prompt version.
     *
     * @param promptKey prompt key (name)
     * @param version   version string, e.g. "1.0.0"
     * @return config group string, e.g. "prompt__{enc_promptKey}__{enc_version}"
     * @throws IllegalArgumentException if promptKey or version is blank
     */
    public static String buildPromptVersionGroup(String promptKey, String version) {
        if (StringUtils.isBlank(promptKey)) {
            throw new IllegalArgumentException("Prompt key cannot be blank");
        }
        if (StringUtils.isBlank(version)) {
            throw new IllegalArgumentException("Version cannot be blank");
        }
        return PROMPT_GROUP_PREFIX + NacosAiConfigKeyCodec.encodeVersionedGroupSegment(promptKey)
            + DOUBLE_UNDERSCORE + NacosAiConfigKeyCodec.encodeVersionedGroupSegment(version);
    }
}
