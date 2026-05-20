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

package com.alibaba.nacos.config.server.service.query.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConfigQueryChainResponseTest {
    
    @Test
    void testSettersAndGetters() {
        ConfigQueryChainResponse resp = new ConfigQueryChainResponse();
        resp.setContent("content");
        resp.setContentType("json");
        resp.setConfigType("yaml");
        resp.setEncryptedDataKey("key");
        resp.setMd5("md5");
        resp.setLastModified(100L);
        resp.setResultCode(200);
        resp.setMessage("ok");
        resp.setStatus(
            ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        
        assertEquals("content", resp.getContent());
        assertEquals("json", resp.getContentType());
        assertEquals("yaml", resp.getConfigType());
        assertEquals("key", resp.getEncryptedDataKey());
        assertEquals("md5", resp.getMd5());
        assertEquals(100L, resp.getLastModified());
        assertEquals(200, resp.getResultCode());
        assertEquals("ok", resp.getMessage());
        assertEquals(
            ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL,
            resp.getStatus());
        assertNull(resp.getMatchedGray());
    }
    
    @Test
    void testBuildFailResponse() {
        ConfigQueryChainResponse resp =
            ConfigQueryChainResponse.buildFailResponse(500, "error");
        assertNotNull(resp);
        assertEquals("error", resp.getMessage());
    }
    
    @Test
    void testSetErrorInfo() {
        ConfigQueryChainResponse resp = new ConfigQueryChainResponse();
        resp.setErrorInfo(500, "error");
        assertEquals("error", resp.getMessage());
    }
    
    @Test
    void testEquals() {
        ConfigQueryChainResponse a = new ConfigQueryChainResponse();
        a.setContent("c");
        a.setLastModified(1L);
        ConfigQueryChainResponse b = new ConfigQueryChainResponse();
        b.setContent("c");
        b.setLastModified(1L);
        assertEquals(a, b);
    }
    
    @Test
    void testEqualsSelf() {
        ConfigQueryChainResponse a = new ConfigQueryChainResponse();
        assertEquals(a, a);
    }
    
    @Test
    void testNotEqualsNull() {
        ConfigQueryChainResponse a = new ConfigQueryChainResponse();
        assertFalse(a.equals(null));
    }
    
    @Test
    void testNotEqualsDifferentClass() {
        ConfigQueryChainResponse a = new ConfigQueryChainResponse();
        assertFalse(a.equals("str"));
    }
    
    @Test
    void testNotEqualsDifferentContent() {
        ConfigQueryChainResponse a = new ConfigQueryChainResponse();
        a.setContent("a");
        ConfigQueryChainResponse b = new ConfigQueryChainResponse();
        b.setContent("b");
        assertNotEquals(a, b);
    }
    
    @Test
    void testHashCode() {
        ConfigQueryChainResponse a = new ConfigQueryChainResponse();
        a.setContent("c");
        ConfigQueryChainResponse b = new ConfigQueryChainResponse();
        b.setContent("c");
        assertEquals(a.hashCode(), b.hashCode());
    }
    
    @Test
    void testConfigQueryStatusValues() {
        ConfigQueryChainResponse.ConfigQueryStatus[] values =
            ConfigQueryChainResponse.ConfigQueryStatus.values();
        assertEquals(5, values.length);
    }
}
