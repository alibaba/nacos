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

package com.alibaba.nacos.plugin.ai.pipeline.spi.impl;

import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Locale;
import java.util.Map;

/**
 * SkillSpector runtime options derived from the accepted plugin configuration.
 *
 * @author nacos
 */
final class SkillSpectorScanOptions {
    
    private static final String ENV_SKILLSPECTOR_PROVIDER = "SKILLSPECTOR_PROVIDER";
    
    private static final String ENV_SKILLSPECTOR_MODEL = "SKILLSPECTOR_MODEL";
    
    private static final String ENV_SKILLSPECTOR_LOG_LEVEL = "SKILLSPECTOR_LOG_LEVEL";
    
    private static final String ENV_SKILLSPECTOR_API_KEY = "SKILLSPECTOR_API_KEY";
    
    private static final String ENV_OPENAI_API_KEY = "OPENAI_API_KEY";
    
    private static final String ENV_OPENAI_BASE_URL = "OPENAI_BASE_URL";
    
    private static final String ENV_ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY";
    
    private static final String ENV_NVIDIA_INFERENCE_KEY = "NVIDIA_INFERENCE_KEY";
    
    private final boolean useLlm;
    
    private final String provider;
    
    private final String model;
    
    private final String apiKey;
    
    private final String baseUrl;
    
    private final String logLevel;
    
    private final int riskScoreThreshold;
    
    private final int maxFindings;
    
    SkillSpectorScanOptions(boolean useLlm, String provider, String model, String apiKey,
        String baseUrl, String logLevel, int riskScoreThreshold, int maxFindings) {
        this.useLlm = useLlm;
        this.provider = provider;
        this.model = model;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.logLevel = logLevel;
        this.riskScoreThreshold = riskScoreThreshold;
        this.maxFindings = maxFindings;
    }
    
    static SkillSpectorScanOptions none() {
        return new SkillSpectorScanOptions(false, null, null, null, null,
            SkillSpectorPluginConfig.DEFAULT_LOG_LEVEL,
            SkillSpectorPluginConfig.DEFAULT_RISK_SCORE_THRESHOLD,
            SkillSpectorPluginConfig.DEFAULT_MAX_FINDINGS);
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
    
    int getRiskScoreThreshold() {
        return riskScoreThreshold;
    }
    
    int getMaxFindings() {
        return maxFindings;
    }
    
    void applyLlmEnvironment(Map<String, String> env) {
        putIfConfigured(env, ENV_SKILLSPECTOR_LOG_LEVEL, logLevel);
        if (!useLlm) {
            return;
        }
        putIfConfigured(env, ENV_SKILLSPECTOR_PROVIDER, provider);
        putIfConfigured(env, ENV_SKILLSPECTOR_MODEL, model);
        putProviderCredentials(env);
    }
    
    private void putProviderCredentials(Map<String, String> env) {
        String effectiveProvider = getEffectiveProvider(env);
        if ("openai".equals(effectiveProvider)) {
            putSecretIfAbsent(env, ENV_OPENAI_API_KEY, apiKey);
            putIfConfigured(env, ENV_OPENAI_BASE_URL, baseUrl);
            return;
        }
        if ("anthropic".equals(effectiveProvider)) {
            putSecretIfAbsent(env, ENV_ANTHROPIC_API_KEY, apiKey);
            return;
        }
        if ("nv_inference".equals(effectiveProvider) || "nv_build".equals(effectiveProvider)) {
            putSecretIfAbsent(env, ENV_NVIDIA_INFERENCE_KEY, apiKey);
        }
    }
    
    private String getEffectiveProvider(Map<String, String> env) {
        String envProvider = trimToNull(env.get(ENV_SKILLSPECTOR_PROVIDER));
        String value = envProvider != null ? envProvider : provider;
        return value == null ? "nv_inference" : value.toLowerCase(Locale.ROOT);
    }
    
    private void putSecretIfAbsent(Map<String, String> env, String key, String configuredValue) {
        if (StringUtils.isNotBlank(env.get(key))) {
            return;
        }
        String genericValue = trimToNull(env.get(ENV_SKILLSPECTOR_API_KEY));
        if (genericValue != null) {
            env.put(key, genericValue);
            return;
        }
        putIfConfigured(env, key, configuredValue);
    }
    
    private void putIfConfigured(Map<String, String> env, String key, String value) {
        if (StringUtils.isBlank(value) || StringUtils.isNotBlank(env.get(key))) {
            return;
        }
        env.put(key, value);
    }
}
