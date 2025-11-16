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

class OfficialMetaTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        OfficialMeta officialMeta = new OfficialMeta();
        officialMeta.setPublishedAt("2022-01-01T00:00:00Z");
        officialMeta.setUpdatedAt("2022-01-02T00:00:00Z");
        officialMeta.setIsLatest(true);
        
        String json = mapper.writeValueAsString(officialMeta);
        assertNotNull(json);
        assertTrue(json.contains("\"publishedAt\":\"2022-01-01T00:00:00Z\""));
        assertTrue(json.contains("\"updatedAt\":\"2022-01-02T00:00:00Z\""));
        assertTrue(json.contains("\"isLatest\":true"));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"serverId\":\"server1\",\"versionId\":\"version1\","
                + "\"publishedAt\":\"2022-01-01T00:00:00Z\",\"updatedAt\":\"2022-01-02T00:00:00Z\","
                + "\"isLatest\":true}";
        
        OfficialMeta officialMeta = mapper.readValue(json, OfficialMeta.class);
        assertNotNull(officialMeta);
        assertEquals("2022-01-01T00:00:00Z", officialMeta.getPublishedAt());
        assertEquals("2022-01-02T00:00:00Z", officialMeta.getUpdatedAt());
        assertEquals(true, officialMeta.getIsLatest());
    }
}