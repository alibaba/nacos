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

package com.alibaba.nacos.plugin.auth.impl.ldap;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable effective configuration for the LDAP auth plugin.
 *
 * @author Nacos
 */
public final class LdapAuthPluginConfig {
    
    public static final String URL = "url";
    
    public static final String BASE_DN = "base-dn";
    
    public static final String TIMEOUT = "timeout";
    
    public static final String USER_DN = "user-dn";
    
    public static final String PASSWORD = "password";
    
    public static final String FILTER_PREFIX = "filter-prefix";
    
    public static final String CASE_SENSITIVE = "case-sensitive";
    
    public static final String IGNORE_PARTIAL_RESULT_EXCEPTION =
        "ignore-partial-result-exception";
    
    public static final String DEFAULT_URL = "ldap://localhost:389";
    
    public static final String DEFAULT_BASE_DN = "dc=example,dc=org";
    
    public static final long DEFAULT_TIMEOUT = 3000L;
    
    public static final String DEFAULT_USER_DN = "cn=admin,dc=example,dc=org";
    
    public static final String DEFAULT_PASSWORD = "password";
    
    public static final String DEFAULT_FILTER_PREFIX = "uid";
    
    public static final boolean DEFAULT_CASE_SENSITIVE = true;
    
    public static final boolean DEFAULT_IGNORE_PARTIAL_RESULT_EXCEPTION = false;
    
    private final String url;
    
    private final String baseDn;
    
    private final long timeout;
    
    private final String userDn;
    
    private final String password;
    
    private final String filterPrefix;
    
    private final boolean caseSensitive;
    
    private final boolean ignorePartialResultException;
    
    private LdapAuthPluginConfig(String url, String baseDn, long timeout, String userDn,
        String password, String filterPrefix, boolean caseSensitive,
        boolean ignorePartialResultException) {
        this.url = url;
        this.baseDn = baseDn;
        this.timeout = timeout;
        this.userDn = userDn;
        this.password = password;
        this.filterPrefix = filterPrefix;
        this.caseSensitive = caseSensitive;
        this.ignorePartialResultException = ignorePartialResultException;
    }
    
    /**
     * Create the default configuration before unified plugin configuration is applied.
     *
     * @return default configuration
     */
    public static LdapAuthPluginConfig defaults() {
        return new LdapAuthPluginConfig(DEFAULT_URL, DEFAULT_BASE_DN, DEFAULT_TIMEOUT,
            DEFAULT_USER_DN, DEFAULT_PASSWORD, DEFAULT_FILTER_PREFIX, DEFAULT_CASE_SENSITIVE,
            DEFAULT_IGNORE_PARTIAL_RESULT_EXCEPTION);
    }
    
    /**
     * Parse and validate one effective plugin configuration map.
     *
     * @param config effective configuration
     * @return parsed immutable configuration
     */
    public static LdapAuthPluginConfig from(Map<String, String> config) {
        String url = value(config, URL, DEFAULT_URL);
        String baseDn = value(config, BASE_DN, DEFAULT_BASE_DN);
        long timeout = parsePositiveLong(value(config, TIMEOUT,
            Long.toString(DEFAULT_TIMEOUT)), TIMEOUT);
        String userDn = value(config, USER_DN, DEFAULT_USER_DN);
        String password = value(config, PASSWORD, DEFAULT_PASSWORD);
        String filterPrefix = value(config, FILTER_PREFIX, DEFAULT_FILTER_PREFIX);
        boolean caseSensitive = parseBoolean(value(config, CASE_SENSITIVE,
            Boolean.toString(DEFAULT_CASE_SENSITIVE)), CASE_SENSITIVE);
        boolean ignorePartialResultException = parseBoolean(value(config,
            IGNORE_PARTIAL_RESULT_EXCEPTION,
            Boolean.toString(DEFAULT_IGNORE_PARTIAL_RESULT_EXCEPTION)),
            IGNORE_PARTIAL_RESULT_EXCEPTION);
        return new LdapAuthPluginConfig(url, baseDn, timeout, userDn, password, filterPrefix,
            caseSensitive, ignorePartialResultException);
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
    
    public String getUrl() {
        return url;
    }
    
    public String getBaseDn() {
        return baseDn;
    }
    
    public long getTimeout() {
        return timeout;
    }
    
    public String getUserDn() {
        return userDn;
    }
    
    public String getPassword() {
        return password;
    }
    
    public String getFilterPrefix() {
        return filterPrefix;
    }
    
    public boolean isCaseSensitive() {
        return caseSensitive;
    }
    
    public boolean isIgnorePartialResultException() {
        return ignorePartialResultException;
    }
    
    /**
     * Convert this configuration to the item-key map used by {@code PluginConfigSpec}.
     *
     * @return configuration map
     */
    public Map<String, String> toMap() {
        Map<String, String> result = new LinkedHashMap<>(8);
        result.put(URL, url);
        result.put(BASE_DN, baseDn);
        result.put(TIMEOUT, Long.toString(timeout));
        result.put(USER_DN, userDn);
        result.put(PASSWORD, password);
        result.put(FILTER_PREFIX, filterPrefix);
        result.put(CASE_SENSITIVE, Boolean.toString(caseSensitive));
        result.put(IGNORE_PARTIAL_RESULT_EXCEPTION,
            Boolean.toString(ignorePartialResultException));
        return result;
    }
}
