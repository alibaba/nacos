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

package com.alibaba.nacos.api.ai.model.mcp;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncryptObjectTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        EncryptObject encryptObject = new EncryptObject();
        encryptObject.setData("encryptedData");
        
        Map<String, String> encryptInfo = new HashMap<>();
        encryptInfo.put("alg", "AES");
        encryptInfo.put("iv", "initialVector");
        encryptObject.setEncryptInfo(encryptInfo);
        
        String json = mapper.writeValueAsString(encryptObject);
        assertTrue(json.contains("\"data\":\"encryptedData\""));
        assertTrue(json.contains("\"encryptInfo\":{"));
        assertTrue(json.contains("\"alg\":\"AES\""));
        assertTrue(json.contains("\"iv\":\"initialVector\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"data\":\"encryptedData\",\"encryptInfo\":{\"alg\":\"AES\",\"iv\":\"initialVector\"}}";
        
        EncryptObject result = mapper.readValue(json, EncryptObject.class);
        assertNotNull(result);
        assertEquals("encryptedData", result.getData());
        assertNotNull(result.getEncryptInfo());
        assertEquals("AES", result.getEncryptInfo().get("alg"));
        assertEquals("initialVector", result.getEncryptInfo().get("iv"));
    }
}