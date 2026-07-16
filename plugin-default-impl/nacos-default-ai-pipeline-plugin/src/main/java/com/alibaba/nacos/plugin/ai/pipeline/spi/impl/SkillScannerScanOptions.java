/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.plugin.ai.pipeline.spi.impl;

import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Map;

/**
 * Immutable skill-scanner CLI options derived from {@link SkillScannerPluginConfig}.
 *
 * <p>Environment variables for the LLM match
 * <a href="https://github.com/cisco-ai-defense/skill-scanner">skill-scanner</a> documentation.</p>
 *
 * @author qiacheng.cxy
 */
final class SkillScannerScanOptions {
    
    private static final String ENV_LLM_API_KEY = "SKILL_SCANNER_LLM_API_KEY";
    
    private static final String ENV_LLM_MODEL = "SKILL_SCANNER_LLM_MODEL";
    
    private final boolean useLlm;
    
    private final String llmApiKey;
    
    private final String llmModel;
    
    private final String llmProvider;
    
    private final boolean enableMeta;
    
    SkillScannerScanOptions(boolean useLlm, String llmApiKey, String llmModel,
        String llmProvider, boolean enableMeta) {
        this.useLlm = useLlm;
        this.llmApiKey = llmApiKey;
        this.llmModel = llmModel;
        this.llmProvider = llmProvider;
        this.enableMeta = enableMeta;
    }
    
    static SkillScannerScanOptions none() {
        return new SkillScannerScanOptions(false, null, null, null, false);
    }
    
    boolean isUseLlm() {
        return useLlm;
    }
    
    boolean isEnableMeta() {
        return enableMeta;
    }
    
    String getLlmProvider() {
        return llmProvider;
    }
    
    /**
     * Applies LLM-related variables to the subprocess environment when configured.
     * Keys match skill-scanner CLI expectations ({@value #ENV_LLM_API_KEY}, {@value #ENV_LLM_MODEL}).
     */
    void applyLlmEnvironment(Map<String, String> env) {
        if (StringUtils.isNotBlank(llmApiKey)) {
            env.put(ENV_LLM_API_KEY, llmApiKey);
        }
        if (StringUtils.isNotBlank(llmModel)) {
            env.put(ENV_LLM_MODEL, llmModel);
        }
    }
}
