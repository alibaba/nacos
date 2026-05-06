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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;

/**
 * Utility class for converting prompt related models.
 *
 * @author nacos
 */
public class PromptConvertUtils {
    
    private PromptConvertUtils() {
    }
    
    /**
     * Convert {@link PromptVersionInfo} to client-facing {@link Prompt}.
     *
     * @param versionInfo prompt version info from service layer, must not be {@code null}
     * @return client-facing prompt
     */
    public static Prompt toClientPrompt(PromptVersionInfo versionInfo) {
        Prompt prompt = new Prompt();
        prompt.setPromptKey(versionInfo.getPromptKey());
        prompt.setVersion(versionInfo.getVersion());
        prompt.setTemplate(versionInfo.getTemplate());
        prompt.setMd5(versionInfo.getMd5());
        prompt.setVariables(versionInfo.getVariables());
        return prompt;
    }
}
