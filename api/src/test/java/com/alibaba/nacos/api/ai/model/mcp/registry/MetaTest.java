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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        Meta meta = new Meta();
        
        Map<String, Object> publisherProvided = new HashMap<>();
        publisherProvided.put("key1", "value1");
        meta.setPublisherProvided(publisherProvided);
        
        OfficialMeta officialMeta = new OfficialMeta();
        officialMeta.setPublishedAt("2022-01-01T00:00:00Z");
        officialMeta.setServerId("server1");
        meta.setOfficial(officialMeta);
        
        String json = mapper.writeValueAsString(meta);
        assertNotNull(json);
        assertTrue(json.contains("\"io.modelcontextprotocol.registry/publisher-provided\":"));
        assertTrue(json.contains("\"key1\":\"value1\""));
        assertTrue(json.contains("\"io.modelcontextprotocol.registry/official\":"));
        assertTrue(json.contains("\"publishedAt\":\"2022-01-01T00:00:00Z\""));
        assertTrue(json.contains("\"serverId\":\"server1\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"io.modelcontextprotocol.registry/publisher-provided\":{\"key1\":\"value1\"},"
                + "\"io.modelcontextprotocol.registry/official\":{\"publishedAt\":\"2022-01-01T00:00:00Z\","
                + "\"serverId\":\"server1\"}}";
        
        Meta meta = mapper.readValue(json, Meta.class);
        assertNotNull(meta);
        assertEquals("value1", meta.getPublisherProvided().get("key1"));
        assertEquals("2022-01-01T00:00:00Z", meta.getOfficial().getPublishedAt());
        assertEquals("server1", meta.getOfficial().getServerId());
    }
}