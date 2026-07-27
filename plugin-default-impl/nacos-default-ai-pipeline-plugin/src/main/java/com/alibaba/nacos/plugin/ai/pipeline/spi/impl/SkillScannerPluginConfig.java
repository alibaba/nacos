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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable configuration for the built-in skill-scanner pipeline.
 *
 * @author Nacos
 */
final class SkillScannerPluginConfig {
    
    static final String ORDER = "order";
    
    static final int DEFAULT_ORDER = 100;
    
    static final String COMMAND = "command";
    
    static final String DEFAULT_COMMAND = "skill-scanner";
    
    static final String COMMAND_ALIAS_EXECUTABLE = "executable";
    
    static final String COMMAND_ALIAS_PATH = "path";
    
    static final String USE_LLM = "use-llm";
    
    static final String USE_LLM_ALIAS = "useLlm";
    
    static final String LLM_API_KEY = "llm-api-key";
    
    static final String LLM_API_KEY_ALIAS = "llmApiKey";
    
    static final String LLM_MODEL = "llm-model";
    
    static final String LLM_MODEL_ALIAS = "llmModel";
    
    static final String LLM_PROVIDER = "llm-provider";
    
    static final String LLM_PROVIDER_ALIAS = "llmProvider";
    
    static final String ENABLE_META = "enable-meta";
    
    static final String ENABLE_META_ALIAS = "enableMeta";
    
    private final String command;
    
    private final int order;
    
    private final boolean useLlm;
    
    private final String llmApiKey;
    
    private final String llmModel;
    
    private final String llmProvider;
    
    private final boolean enableMeta;
    
    private SkillScannerPluginConfig(String command, int order, boolean useLlm, String llmApiKey,
        String llmModel, String llmProvider, boolean enableMeta) {
        this.command = command;
        this.order = order;
        this.useLlm = useLlm;
        this.llmApiKey = llmApiKey;
        this.llmModel = llmModel;
        this.llmProvider = llmProvider;
        this.enableMeta = enableMeta;
    }
    
    static SkillScannerPluginConfig fromMap(Map<String, String> config) {
        Map<String, String> source = config == null ? Collections.emptyMap() : config;
        String command = normalizeCommand(read(source, COMMAND, COMMAND_ALIAS_EXECUTABLE,
            COMMAND_ALIAS_PATH));
        int order = parseOrder(read(source, ORDER));
        boolean useLlm = Boolean.parseBoolean(read(source, USE_LLM, USE_LLM_ALIAS));
        String llmApiKey = trimToNull(read(source, LLM_API_KEY, LLM_API_KEY_ALIAS));
        String llmModel = trimToNull(read(source, LLM_MODEL, LLM_MODEL_ALIAS));
        String llmProvider = trimToNull(read(source, LLM_PROVIDER, LLM_PROVIDER_ALIAS));
        boolean enableMeta = Boolean.parseBoolean(read(source, ENABLE_META, ENABLE_META_ALIAS));
        return new SkillScannerPluginConfig(command, order, useLlm, llmApiKey, llmModel,
            llmProvider, enableMeta);
    }
    
    private static String read(Map<String, String> properties, String key, String... aliases) {
        if (properties.containsKey(key)) {
            return properties.get(key);
        }
        for (String alias : aliases) {
            if (properties.containsKey(alias)) {
                return properties.get(alias);
            }
        }
        return null;
    }
    
    private static String normalizeCommand(String value) {
        return StringUtils.isBlank(value) ? DEFAULT_COMMAND : value.trim();
    }
    
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }
    
    private static int parseOrder(String value) {
        if (StringUtils.isBlank(value)) {
            return DEFAULT_ORDER;
        }
        return new BigDecimal(value.trim()).intValueExact();
    }
    
    String getCommand() {
        return command;
    }
    
    int getOrder() {
        return order;
    }
    
    SkillScannerScanOptions getScanOptions() {
        return new SkillScannerScanOptions(useLlm, llmApiKey, llmModel, llmProvider,
            enableMeta);
    }
    
    Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(ORDER, Integer.toString(order));
        result.put(COMMAND, command);
        result.put(USE_LLM, Boolean.toString(useLlm));
        result.put(LLM_API_KEY, valueOrEmpty(llmApiKey));
        result.put(LLM_MODEL, valueOrEmpty(llmModel));
        result.put(LLM_PROVIDER, valueOrEmpty(llmProvider));
        result.put(ENABLE_META, Boolean.toString(enableMeta));
        return result;
    }
    
    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
