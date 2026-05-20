/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigCacheGrayTest {
    
    @Test
    void testConstructorWithName() {
        ConfigCacheGray gray = new ConfigCacheGray("beta");
        assertEquals("beta", gray.getGrayName());
    }
    
    @Test
    void testResetGrayRule() {
        ConfigCacheGray gray = new ConfigCacheGray("tag1");
        String rule = "{\"type\":\"tag\",\"version\":\"1.0.0\","
            + "\"expr\":\"tagVal\",\"priority\":1}";
        gray.resetGrayRule(rule);
        assertNotNull(gray.getGrayRule());
        assertEquals(1, gray.getPriority());
        assertTrue(gray.isValid());
        assertEquals("tagVal", gray.getRawGrayRule());
    }
    
    @Test
    void testResetGrayRuleInvalid() {
        ConfigCacheGray gray = new ConfigCacheGray("test");
        String rule = "{\"type\":\"unknown\",\"version\":\"9.9.9\","
            + "\"expr\":\"x\",\"priority\":1}";
        assertThrows(RuntimeException.class, () -> gray.resetGrayRule(rule));
    }
    
    @Test
    void testMatch() {
        ConfigCacheGray gray = new ConfigCacheGray("tag1");
        String rule = "{\"type\":\"tag\",\"version\":\"1.0.0\","
            + "\"expr\":\"myTag\",\"priority\":1}";
        gray.resetGrayRule(rule);
        
        Map<String, String> tags = new HashMap<>();
        tags.put("Vipserver-Tag", "myTag");
        assertTrue(gray.match(tags));
        
        Map<String, String> noMatch = new HashMap<>();
        noMatch.put("Vipserver-Tag", "other");
        assertFalse(gray.match(noMatch));
    }
    
    @Test
    void testClear() {
        ConfigCacheGray gray = new ConfigCacheGray("g");
        gray.setMd5("md5");
        gray.setLastModifiedTs(100L);
        gray.clear();
        assertEquals("", gray.getMd5());
        assertEquals(-1L, gray.getLastModifiedTs());
    }
    
    @Test
    void testSetGrayName() {
        ConfigCacheGray gray = new ConfigCacheGray();
        gray.setGrayName("newName");
        assertEquals("newName", gray.getGrayName());
    }
}
