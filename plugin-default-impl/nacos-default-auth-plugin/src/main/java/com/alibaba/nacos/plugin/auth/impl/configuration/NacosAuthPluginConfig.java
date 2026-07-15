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

package com.alibaba.nacos.plugin.auth.impl.configuration;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import com.alibaba.nacos.plugin.auth.impl.jwt.NacosJwtParser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable effective configuration for the built-in Nacos auth plugin.
 *
 * @author Nacos
 */
public final class NacosAuthPluginConfig {
    
    private static final String INVALID_SECRET_MESSAGE = "the length of secret key must great than "
        + "or equal 32 bytes; And the secret key must be encoded by base64. Please see "
        + "https://nacos.io/docs/latest/manual/admin/auth/";
    
    public static final String TOKEN_SECRET_KEY = "token.secret.key";
    
    public static final String TOKEN_EXPIRE_SECONDS = "token.expire.seconds";
    
    public static final String TOKEN_CACHE_ENABLE = "token.cache.enable";
    
    public static final String CACHING_ENABLED = "caching.enabled";
    
    public static final String ANONYMOUS_AI_ENABLED = "anonymous.ai.enabled";
    
    private static final boolean DEFAULT_TOKEN_CACHE_ENABLE = false;
    
    private static final boolean DEFAULT_CACHING_ENABLED = true;
    
    private static final boolean DEFAULT_ANONYMOUS_AI_ENABLED = false;
    
    private final String tokenSecretKey;
    
    private final long tokenExpireSeconds;
    
    private final boolean tokenCacheEnabled;
    
    private final boolean cachingEnabled;
    
    private final boolean anonymousAiEnabled;
    
    private NacosAuthPluginConfig(String tokenSecretKey, long tokenExpireSeconds,
        boolean tokenCacheEnabled, boolean cachingEnabled, boolean anonymousAiEnabled) {
        this.tokenSecretKey = tokenSecretKey;
        this.tokenExpireSeconds = tokenExpireSeconds;
        this.tokenCacheEnabled = tokenCacheEnabled;
        this.cachingEnabled = cachingEnabled;
        this.anonymousAiEnabled = anonymousAiEnabled;
    }
    
    /**
     * Create the default configuration before the unified plugin configuration is applied.
     *
     * @return default configuration
     */
    public static NacosAuthPluginConfig defaults() {
        return new NacosAuthPluginConfig(AuthConstants.DEFAULT_TOKEN_SECRET_KEY,
            AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS, DEFAULT_TOKEN_CACHE_ENABLE,
            DEFAULT_CACHING_ENABLED, DEFAULT_ANONYMOUS_AI_ENABLED);
    }
    
    /**
     * Parse and validate one effective plugin configuration map.
     *
     * @param config effective configuration
     * @param tokenSecretRequired whether current module configuration requires token support
     * @return parsed immutable configuration
     */
    public static NacosAuthPluginConfig from(Map<String, String> config,
        boolean tokenSecretRequired) {
        String tokenSecretKey = value(config, TOKEN_SECRET_KEY,
            AuthConstants.DEFAULT_TOKEN_SECRET_KEY);
        if (tokenSecretRequired && StringUtils.isBlank(tokenSecretKey)) {
            throw new IllegalArgumentException("Required config missing: " + TOKEN_SECRET_KEY);
        }
        validateTokenSecret(tokenSecretKey);
        long tokenExpireSeconds = parsePositiveLong(value(config, TOKEN_EXPIRE_SECONDS,
            AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS.toString()), TOKEN_EXPIRE_SECONDS);
        boolean tokenCacheEnabled = parseBoolean(value(config, TOKEN_CACHE_ENABLE,
            Boolean.toString(DEFAULT_TOKEN_CACHE_ENABLE)), TOKEN_CACHE_ENABLE);
        boolean cachingEnabled = parseBoolean(value(config, CACHING_ENABLED,
            Boolean.toString(DEFAULT_CACHING_ENABLED)), CACHING_ENABLED);
        boolean anonymousAiEnabled = parseBoolean(value(config, ANONYMOUS_AI_ENABLED,
            Boolean.toString(DEFAULT_ANONYMOUS_AI_ENABLED)), ANONYMOUS_AI_ENABLED);
        return new NacosAuthPluginConfig(tokenSecretKey, tokenExpireSeconds, tokenCacheEnabled,
            cachingEnabled, anonymousAiEnabled);
    }
    
    private static String value(Map<String, String> config, String key, String defaultValue) {
        if (config == null || !config.containsKey(key)) {
            return defaultValue;
        }
        String result = config.get(key);
        if (result == null) {
            throw new IllegalArgumentException("Plugin config value cannot be null: " + key);
        }
        return result;
    }
    
    private static long parsePositiveLong(String value, String key) {
        try {
            long result = Long.parseLong(value);
            if (result <= 0) {
                throw new IllegalArgumentException("Plugin config value must be positive: " + key);
            }
            return result;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Plugin config value is not a number: " + key, e);
        }
    }
    
    private static boolean parseBoolean(String value, String key) {
        if (!Boolean.TRUE.toString().equalsIgnoreCase(value)
            && !Boolean.FALSE.toString().equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Plugin config value is not a boolean: " + key);
        }
        return Boolean.parseBoolean(value);
    }
    
    private static void validateTokenSecret(String tokenSecretKey) {
        if (StringUtils.isBlank(tokenSecretKey)) {
            return;
        }
        try {
            new NacosJwtParser(tokenSecretKey);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(INVALID_SECRET_MESSAGE, e);
        }
    }
    
    public String getTokenSecretKey() {
        return tokenSecretKey;
    }
    
    public long getTokenExpireSeconds() {
        return tokenExpireSeconds;
    }
    
    public boolean isTokenCacheEnabled() {
        return tokenCacheEnabled;
    }
    
    public boolean isCachingEnabled() {
        return cachingEnabled;
    }
    
    public boolean isAnonymousAiEnabled() {
        return anonymousAiEnabled;
    }
    
    /**
     * Convert this configuration to the item-key map used by {@code PluginConfigSpec}.
     *
     * @return configuration map
     */
    public Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>(5);
        result.put(TOKEN_SECRET_KEY, tokenSecretKey);
        result.put(TOKEN_EXPIRE_SECONDS, Long.toString(tokenExpireSeconds));
        result.put(TOKEN_CACHE_ENABLE, Boolean.toString(tokenCacheEnabled));
        result.put(CACHING_ENABLED, Boolean.toString(cachingEnabled));
        result.put(ANONYMOUS_AI_ENABLED, Boolean.toString(anonymousAiEnabled));
        return result;
    }
}
