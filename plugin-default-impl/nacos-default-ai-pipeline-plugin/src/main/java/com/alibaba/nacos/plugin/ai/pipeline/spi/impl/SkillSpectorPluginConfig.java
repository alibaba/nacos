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
 * Immutable configuration for the built-in skill-spector pipeline.
 *
 * @author Nacos
 */
final class SkillSpectorPluginConfig {
    
    static final String ORDER = "order";
    
    static final int DEFAULT_ORDER = 90;
    
    static final String COMMAND = "command";
    
    static final String DEFAULT_COMMAND = "skill-spector";
    
    static final String COMMAND_ALIAS_EXECUTABLE = "executable";
    
    static final String COMMAND_ALIAS_PATH = "path";
    
    static final String USE_LLM = "use-llm";
    
    static final String USE_LLM_ALIAS = "useLlm";
    
    static final String PROVIDER = "provider";
    
    static final String MODEL = "model";
    
    static final String API_KEY = "api-key";
    
    static final String API_KEY_ALIAS = "apiKey";
    
    static final String BASE_URL = "base-url";
    
    static final String BASE_URL_ALIAS = "baseUrl";
    
    static final String LOG_LEVEL = "log-level";
    
    static final String LOG_LEVEL_ALIAS = "logLevel";
    
    static final String DEFAULT_LOG_LEVEL = "WARNING";
    
    static final String RISK_SCORE_THRESHOLD = "risk-score-threshold";
    
    static final String RISK_SCORE_THRESHOLD_ALIAS = "riskScoreThreshold";
    
    static final int DEFAULT_RISK_SCORE_THRESHOLD = 50;
    
    static final String MAX_FINDINGS = "max-findings";
    
    static final String MAX_FINDINGS_ALIAS = "maxFindings";
    
    static final int DEFAULT_MAX_FINDINGS = 20;
    
    static final int MAX_FINDINGS_LIMIT = 100;
    
    private final String command;
    
    private final int order;
    
    private final boolean useLlm;
    
    private final String provider;
    
    private final String model;
    
    private final String apiKey;
    
    private final String baseUrl;
    
    private final String logLevel;
    
    private final int riskScoreThreshold;
    
    private final int maxFindings;
    
    private SkillSpectorPluginConfig(String command, int order, boolean useLlm, String provider,
        String model, String apiKey, String baseUrl, String logLevel, int riskScoreThreshold,
        int maxFindings) {
        this.command = command;
        this.order = order;
        this.useLlm = useLlm;
        this.provider = provider;
        this.model = model;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.logLevel = logLevel;
        this.riskScoreThreshold = riskScoreThreshold;
        this.maxFindings = maxFindings;
    }
    
    static SkillSpectorPluginConfig fromMap(Map<String, String> config) {
        Map<String, String> source = config == null ? Collections.emptyMap() : config;
        String command = normalizeCommand(read(source, COMMAND, COMMAND_ALIAS_EXECUTABLE,
            COMMAND_ALIAS_PATH));
        int order = parseOrder(read(source, ORDER));
        boolean useLlm = Boolean.parseBoolean(read(source, USE_LLM, USE_LLM_ALIAS));
        String provider = trimToNull(read(source, PROVIDER));
        String model = trimToNull(read(source, MODEL));
        String apiKey = trimToNull(read(source, API_KEY, API_KEY_ALIAS));
        String baseUrl = trimToNull(read(source, BASE_URL, BASE_URL_ALIAS));
        String logLevel = defaultIfBlank(read(source, LOG_LEVEL, LOG_LEVEL_ALIAS),
            DEFAULT_LOG_LEVEL);
        int riskScoreThreshold = parseRiskScoreThreshold(read(source, RISK_SCORE_THRESHOLD,
            RISK_SCORE_THRESHOLD_ALIAS));
        int maxFindings = parseMaxFindings(read(source, MAX_FINDINGS, MAX_FINDINGS_ALIAS));
        return new SkillSpectorPluginConfig(command, order, useLlm, provider, model, apiKey,
            baseUrl, logLevel, riskScoreThreshold, maxFindings);
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
    
    private static String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value.trim();
    }
    
    private static int parseOrder(String value) {
        if (StringUtils.isBlank(value)) {
            return DEFAULT_ORDER;
        }
        return new BigDecimal(value.trim()).intValueExact();
    }
    
    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }
    
    private static int parseRiskScoreThreshold(String value) {
        if (StringUtils.isBlank(value)) {
            return DEFAULT_RISK_SCORE_THRESHOLD;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < 0) {
                return 0;
            }
            return Math.min(parsed, 100);
        } catch (NumberFormatException e) {
            return DEFAULT_RISK_SCORE_THRESHOLD;
        }
    }
    
    private static int parseMaxFindings(String value) {
        if (StringUtils.isBlank(value)) {
            return DEFAULT_MAX_FINDINGS;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed <= 0) {
                return DEFAULT_MAX_FINDINGS;
            }
            return Math.min(parsed, MAX_FINDINGS_LIMIT);
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_FINDINGS;
        }
    }
    
    String getCommand() {
        return command;
    }
    
    int getOrder() {
        return order;
    }
    
    SkillSpectorScanOptions getScanOptions() {
        return new SkillSpectorScanOptions(useLlm, provider, model, apiKey, baseUrl, logLevel,
            riskScoreThreshold, maxFindings);
    }
    
    Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(ORDER, Integer.toString(order));
        result.put(COMMAND, command);
        result.put(USE_LLM, Boolean.toString(useLlm));
        result.put(PROVIDER, valueOrEmpty(provider));
        result.put(MODEL, valueOrEmpty(model));
        result.put(API_KEY, valueOrEmpty(apiKey));
        result.put(BASE_URL, valueOrEmpty(baseUrl));
        result.put(LOG_LEVEL, logLevel);
        result.put(RISK_SCORE_THRESHOLD, Integer.toString(riskScoreThreshold));
        result.put(MAX_FINDINGS, Integer.toString(maxFindings));
        return result;
    }
    
    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
