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

package com.alibaba.nacos.api.config;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigQueryResultTest extends BasicRequestTest {
    
    @Test
    @DisplayName("test default constructor")
    void testDefaultConstructor() {
        ConfigQueryResult result = new ConfigQueryResult();
        assertNull(result.getContent());
        assertNull(result.getMd5());
        assertNull(result.getConfigType());
        assertNull(result.getEncryptedDataKey());
    }
    
    @Test
    @DisplayName("test constructor with content and md5")
    void testConstructorWithContentAndMd5() {
        ConfigQueryResult result = new ConfigQueryResult("test content", "abc123");
        assertEquals("test content", result.getContent());
        assertEquals("abc123", result.getMd5());
    }
    
    @Test
    @DisplayName("test getter and setter for content")
    void testGetterAndSetterForContent() {
        ConfigQueryResult result = new ConfigQueryResult();
        result.setContent("configuration content");
        assertEquals("configuration content", result.getContent());
    }
    
    @Test
    @DisplayName("test getter and setter for md5")
    void testGetterAndSetterForMd5() {
        ConfigQueryResult result = new ConfigQueryResult();
        result.setMd5("def456");
        assertEquals("def456", result.getMd5());
    }
    
    @Test
    @DisplayName("test getter and setter for configType")
    void testGetterAndSetterForConfigType() {
        ConfigQueryResult result = new ConfigQueryResult();
        result.setConfigType("yaml");
        assertEquals("yaml", result.getConfigType());
    }
    
    @Test
    @DisplayName("test getter and setter for encryptedDataKey")
    void testGetterAndSetterForEncryptedDataKey() {
        ConfigQueryResult result = new ConfigQueryResult();
        result.setEncryptedDataKey("encrypted-key-123");
        assertEquals("encrypted-key-123", result.getEncryptedDataKey());
    }
    
    @Test
    @DisplayName("test toString with null content")
    void testToStringWithNullContent() {
        ConfigQueryResult result = new ConfigQueryResult();
        result.setMd5("abc123");
        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("ConfigQueryResult"));
        assertTrue(str.contains("null"));
        assertTrue(str.contains("abc123"));
    }
    
    @Test
    @DisplayName("test toString with short content")
    void testToStringWithShortContent() {
        ConfigQueryResult result = new ConfigQueryResult("short", "md5");
        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("ConfigQueryResult"));
        assertTrue(str.contains("short"));
    }
    
    @Test
    @DisplayName("test toString with long content truncates")
    void testToStringWithLongContentTruncates() {
        ConfigQueryResult result = new ConfigQueryResult();
        result.setContent(
            "This is a very long content string that should be truncated in toString output because it exceeds 50 characters limit");
        result.setMd5("hash123");
        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("ConfigQueryResult"));
        assertTrue(str.contains("..."));
        assertTrue(str.length() < 150);
    }
    
    @Test
    @DisplayName("test toString with configType")
    void testToStringWithConfigType() {
        ConfigQueryResult result = new ConfigQueryResult("test", "md5");
        result.setConfigType("json");
        String str = result.toString();
        assertNotNull(str);
        assertTrue(str.contains("json"));
    }
    
    @Test
    @DisplayName("test serialize to json")
    void testSerializeToJson() throws JsonProcessingException {
        ConfigQueryResult result = new ConfigQueryResult("test content", "abc123");
        result.setConfigType("yaml");
        result.setEncryptedDataKey("key");
        
        String json = mapper.writeValueAsString(result);
        assertNotNull(json);
        assertTrue(json.contains("\"content\":\"test content\""));
        assertTrue(json.contains("\"md5\":\"abc123\""));
        assertTrue(json.contains("\"configType\":\"yaml\""));
        assertTrue(json.contains("\"encryptedDataKey\":\"key\""));
    }
    
    @Test
    @DisplayName("test deserialize from json")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json =
            "{\"content\":\"test\",\"md5\":\"abc123\",\"configType\":\"yaml\",\"encryptedDataKey\":\"key\"}";
        
        ConfigQueryResult result = mapper.readValue(json, ConfigQueryResult.class);
        assertNotNull(result);
        assertEquals("test", result.getContent());
        assertEquals("abc123", result.getMd5());
        assertEquals("yaml", result.getConfigType());
        assertEquals("key", result.getEncryptedDataKey());
    }
}
