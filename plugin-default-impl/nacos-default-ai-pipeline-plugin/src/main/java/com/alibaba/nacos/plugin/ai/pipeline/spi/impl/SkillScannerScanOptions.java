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
import java.util.Properties;

/**
 * Skill-scanner CLI options derived from pipeline node {@link Properties}.
 *
 * <p>Configure via {@code nacos.plugin.ai-pipeline.type=skill-scanner}
 * and matching {@code nacos.plugin.ai-pipeline.skill-scanner.&lt;key&gt;} entries
 * (see {@link com.alibaba.nacos.ai.pipeline.config.FilePipelineConfigProvider}).</p>
 *
 * <p>Environment variables for the LLM match
 * <a href="https://github.com/cisco-ai-defense/skill-scanner">skill-scanner</a> documentation.</p>
 *
 * @author qiacheng.cxy
 */
final class SkillScannerScanOptions {
    
    static final String PROP_USE_LLM = "useLlm";
    
    static final String PROP_LLM_API_KEY = "llmApiKey";
    
    static final String PROP_LLM_MODEL = "llmModel";
    
    static final String PROP_LLM_PROVIDER = "llmProvider";
    
    static final String PROP_ENABLE_META = "enableMeta";
    
    private static final String ENV_LLM_API_KEY = "SKILL_SCANNER_LLM_API_KEY";
    
    private static final String ENV_LLM_MODEL = "SKILL_SCANNER_LLM_MODEL";
    
    private final boolean useLlm;
    
    private final String llmApiKey;
    
    private final String llmModel;
    
    private final String llmProvider;
    
    private final boolean enableMeta;
    
    private SkillScannerScanOptions(boolean useLlm, String llmApiKey, String llmModel,
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
    
    static SkillScannerScanOptions fromProperties(Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return none();
        }
        boolean useLlm = Boolean.parseBoolean(properties.getProperty(PROP_USE_LLM, "false"));
        String llmApiKey = trimToNull(properties.getProperty(PROP_LLM_API_KEY));
        String llmModel = trimToNull(properties.getProperty(PROP_LLM_MODEL));
        String llmProvider = trimToNull(properties.getProperty(PROP_LLM_PROVIDER));
        boolean enableMeta =
            Boolean.parseBoolean(properties.getProperty(PROP_ENABLE_META, "false"));
        return new SkillScannerScanOptions(useLlm, llmApiKey, llmModel, llmProvider, enableMeta);
    }
    
    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
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
