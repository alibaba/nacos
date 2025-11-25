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

import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ConsoleCorsConfig test.
 */
class ConsoleCorsConfigTest {
    
    private ConfigurableEnvironment cachedEnvironment;
    
    @BeforeEach
    void setUp() {
        cachedEnvironment = EnvUtil.getEnvironment();
    }
    
    @AfterEach
    void tearDown() {
        EnvUtil.setEnvironment(cachedEnvironment);
    }
    
    @Test
    void testDefaultConfiguration() {
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        
        ConsoleCorsConfig config = new ConsoleCorsConfig();
        
        assertTrue(config.isAllowCredentials());
        assertEquals(Collections.emptyList(), config.getAllowedHeaders());
        assertEquals(18000L, config.getMaxAge());
        assertEquals(Collections.emptyList(), config.getAllowedMethods());
        assertEquals(Collections.emptyList(), config.getAllowedOrigins());
    }
    
    @Test
    void testCustomConfiguration() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("nacos.console.cors.allow-credentials", "false");
        environment.setProperty("nacos.console.cors.allowed-headers", "Content-Type,Authorization");
        environment.setProperty("nacos.console.cors.max-age", "3600");
        environment.setProperty("nacos.console.cors.allowed-methods", "GET,POST,PUT");
        environment.setProperty("nacos.console.cors.allowed-origins", "http://localhost:8080,https://example.com");
        EnvUtil.setEnvironment(environment);
        
        ConsoleCorsConfig config = new ConsoleCorsConfig();
        
        assertFalse(config.isAllowCredentials());
        assertEquals(Arrays.asList("Content-Type", "Authorization"), config.getAllowedHeaders());
        assertEquals(3600L, config.getMaxAge());
        assertEquals(Arrays.asList("GET", "POST", "PUT"), config.getAllowedMethods());
        assertEquals(Arrays.asList("http://localhost:8080", "https://example.com"), config.getAllowedOrigins());
    }
    
    @Test
    void testToString() {
        MockEnvironment environment = new MockEnvironment();
        EnvUtil.setEnvironment(environment);
        
        ConsoleCorsConfig config = new ConsoleCorsConfig();
        String result = config.toString();
        
        assertTrue(result.contains("ConsoleCorsConfig"));
        assertTrue(result.contains("allowCredentials=true"));
        assertTrue(result.contains("maxAge=18000"));
    }
}
