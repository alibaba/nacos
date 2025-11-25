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

package com.alibaba.nacos.console.config;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Nacos console cors configurations.
 *
 * @author zhan7236
 */
public class ConsoleCorsConfig {
    
    private static final String CONSOLE_CORS_PREFIX = "nacos.console.cors.";
    
    private static final String ALLOW_CREDENTIALS_KEY = CONSOLE_CORS_PREFIX + "allow-credentials";
    
    private static final String ALLOWED_HEADERS_KEY = CONSOLE_CORS_PREFIX + "allowed-headers";
    
    private static final String MAX_AGE_KEY = CONSOLE_CORS_PREFIX + "max-age";
    
    private static final String ALLOWED_METHODS_KEY = CONSOLE_CORS_PREFIX + "allowed-methods";
    
    private static final String ALLOWED_ORIGINS_KEY = CONSOLE_CORS_PREFIX + "allowed-origins";
    
    private static final boolean DEFAULT_ALLOW_CREDENTIALS = true;
    
    private static final long DEFAULT_MAX_AGE = 18000L;
    
    private final boolean allowCredentials;
    
    private final List<String> allowedHeaders;
    
    private final long maxAge;
    
    private final List<String> allowedMethods;
    
    private final List<String> allowedOrigins;
    
    public ConsoleCorsConfig() {
        this.allowCredentials = EnvUtil.getProperty(ALLOW_CREDENTIALS_KEY, Boolean.class, DEFAULT_ALLOW_CREDENTIALS);
        this.allowedHeaders = parseListProperty(ALLOWED_HEADERS_KEY);
        this.maxAge = EnvUtil.getProperty(MAX_AGE_KEY, Long.class, DEFAULT_MAX_AGE);
        this.allowedMethods = parseListProperty(ALLOWED_METHODS_KEY);
        this.allowedOrigins = parseListProperty(ALLOWED_ORIGINS_KEY);
    }
    
    private List<String> parseListProperty(String key) {
        String value = EnvUtil.getProperty(key, "");
        if (StringUtils.isNotBlank(value)) {
            return Arrays.asList(value.split(","));
        }
        return Collections.emptyList();
    }
    
    public boolean isAllowCredentials() {
        return allowCredentials;
    }
    
    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }
    
    public long getMaxAge() {
        return maxAge;
    }
    
    public List<String> getAllowedMethods() {
        return allowedMethods;
    }
    
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }
    
    @Override
    public String toString() {
        return "ConsoleCorsConfig{" + "allowCredentials=" + allowCredentials + ", allowedHeaders=" + allowedHeaders
                + ", maxAge=" + maxAge + ", allowedMethods=" + allowedMethods + ", allowedOrigins=" + allowedOrigins
                + '}';
    }
}
