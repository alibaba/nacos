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

import com.alibaba.nacos.plugin.auth.impl.constant.AuthConstants;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosAuthPluginConfigTest {
    
    @Test
    void testDefaults() {
        NacosAuthPluginConfig config = NacosAuthPluginConfig.defaults();
        assertEquals(AuthConstants.DEFAULT_TOKEN_SECRET_KEY, config.getTokenSecretKey());
        assertEquals(AuthConstants.DEFAULT_TOKEN_EXPIRE_SECONDS.longValue(),
            config.getTokenExpireSeconds());
        assertFalse(config.isTokenCacheEnabled());
        assertTrue(config.isCachingEnabled());
        assertFalse(config.isAnonymousAiEnabled());
    }
    
    @Test
    void testParseAndConvertToMap() {
        String tokenSecret = Base64.getEncoder().encodeToString(
            "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
        Map<String, String> input = new LinkedHashMap<>();
        input.put(NacosAuthPluginConfig.TOKEN_SECRET_KEY, tokenSecret);
        input.put(NacosAuthPluginConfig.TOKEN_EXPIRE_SECONDS, "3600");
        input.put(NacosAuthPluginConfig.TOKEN_CACHE_ENABLE, "TRUE");
        input.put(NacosAuthPluginConfig.CACHING_ENABLED, "false");
        input.put(NacosAuthPluginConfig.ANONYMOUS_AI_ENABLED, "true");
        NacosAuthPluginConfig config = NacosAuthPluginConfig.from(input, true);
        assertEquals(tokenSecret, config.getTokenSecretKey());
        assertEquals(3600L, config.getTokenExpireSeconds());
        assertTrue(config.isTokenCacheEnabled());
        assertFalse(config.isCachingEnabled());
        assertTrue(config.isAnonymousAiEnabled());
        assertEquals(tokenSecret,
            config.toMap().get(NacosAuthPluginConfig.TOKEN_SECRET_KEY));
        assertEquals("3600",
            config.toMap().get(NacosAuthPluginConfig.TOKEN_EXPIRE_SECONDS));
        assertEquals("true",
            config.toMap().get(NacosAuthPluginConfig.TOKEN_CACHE_ENABLE));
        assertEquals("false", config.toMap().get(NacosAuthPluginConfig.CACHING_ENABLED));
        assertEquals("true",
            config.toMap().get(NacosAuthPluginConfig.ANONYMOUS_AI_ENABLED));
    }
    
    @Test
    void testMissingValuesUseDefaults() {
        NacosAuthPluginConfig config = NacosAuthPluginConfig.from(null, false);
        assertEquals(NacosAuthPluginConfig.defaults().toMap(), config.toMap());
    }
    
    @Test
    void testRejectMissingRequiredSecret() {
        assertThrows(IllegalArgumentException.class,
            () -> NacosAuthPluginConfig.from(new LinkedHashMap<>(), true));
    }
    
    @Test
    void testRejectInvalidSecret() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put(NacosAuthPluginConfig.TOKEN_SECRET_KEY,
            Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class,
            () -> NacosAuthPluginConfig.from(input, false));
    }
    
    @Test
    void testRejectNullValue() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put(NacosAuthPluginConfig.TOKEN_EXPIRE_SECONDS, null);
        assertThrows(IllegalArgumentException.class,
            () -> NacosAuthPluginConfig.from(input, false));
    }
    
    @Test
    void testRejectNonPositiveExpiration() {
        assertInvalidValue(NacosAuthPluginConfig.TOKEN_EXPIRE_SECONDS, "0");
    }
    
    @Test
    void testRejectNonNumericExpiration() {
        assertInvalidValue(NacosAuthPluginConfig.TOKEN_EXPIRE_SECONDS, "invalid");
    }
    
    @Test
    void testRejectInvalidBoolean() {
        assertInvalidValue(NacosAuthPluginConfig.TOKEN_CACHE_ENABLE, "yes");
        assertInvalidValue(NacosAuthPluginConfig.CACHING_ENABLED, "yes");
        assertInvalidValue(NacosAuthPluginConfig.ANONYMOUS_AI_ENABLED, "yes");
    }
    
    private void assertInvalidValue(String key, String value) {
        Map<String, String> input = new LinkedHashMap<>();
        input.put(key, value);
        assertThrows(IllegalArgumentException.class,
            () -> NacosAuthPluginConfig.from(input, false));
    }
}
