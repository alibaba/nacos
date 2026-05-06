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

package com.alibaba.nacos.api.ai.model.mcp.registry;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerVersionDetailTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        ServerVersionDetail serverVersionDetail = new ServerVersionDetail();
        serverVersionDetail.setVersion("1.0.0");
        serverVersionDetail.setRelease_date("2022-01-01T00:00:00Z");
        serverVersionDetail.setIs_latest(true);
        
        String json = mapper.writeValueAsString(serverVersionDetail);
        assertNotNull(json);
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"release_date\":\"2022-01-01T00:00:00Z\""));
        assertTrue(json.contains("\"is_latest\":true"));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"version\":\"1.0.0\",\"release_date\":\"2022-01-01T00:00:00Z\",\"is_latest\":true}";
        
        ServerVersionDetail serverVersionDetail = mapper.readValue(json, ServerVersionDetail.class);
        assertNotNull(serverVersionDetail);
        assertEquals("1.0.0", serverVersionDetail.getVersion());
        assertEquals("2022-01-01T00:00:00Z", serverVersionDetail.getRelease_date());
        assertEquals(true, serverVersionDetail.getIs_latest());
    }
}