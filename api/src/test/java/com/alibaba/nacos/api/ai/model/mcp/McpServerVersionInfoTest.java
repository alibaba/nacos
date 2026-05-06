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

import com.alibaba.nacos.api.ai.model.mcp.registry.ServerVersionDetail;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerVersionInfoTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        McpServerVersionInfo mcpServerVersionInfo = new McpServerVersionInfo();
        mcpServerVersionInfo.setId(UUID.randomUUID().toString());
        mcpServerVersionInfo.setName("testVersionInfo");
        mcpServerVersionInfo.setLatestPublishedVersion("1.0.0");
        mcpServerVersionInfo.setVersionDetail(new ServerVersionDetail());
        mcpServerVersionInfo.getVersionDetail().setVersion("1.0.0");
        mcpServerVersionInfo.getVersionDetail().setRelease_date("2023-07-01T00:00:00Z");
        mcpServerVersionInfo.getVersionDetail().setIs_latest(true);
        mcpServerVersionInfo.setVersions(Collections.singletonList(mcpServerVersionInfo.getVersionDetail()));
        
        String json = mapper.writeValueAsString(mcpServerVersionInfo);
        assertNotNull(json);
        assertTrue(json.contains(String.format("\"id\":\"%s\"", mcpServerVersionInfo.getId())));
        assertTrue(json.contains("\"name\":\"testVersionInfo\""));
        assertTrue(json.contains("\"versionDetail\":{"));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"release_date\":\"2023-07-01T00:00:00Z\""));
        assertTrue(json.contains("\"is_latest\":true"));
        assertTrue(json.contains("\"latestPublishedVersion\":\"1.0.0\""));
        assertTrue(json.contains("\"versionDetails\":[{"));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"id\":\"b646506e-901b-41a1-8790-a4378d11055e\",\"name\":\"testVersionInfo\",\"versionDetail\":"
                + "{\"version\":\"1.0.0\",\"release_date\":\"2023-07-01T00:00:00Z\",\"is_latest\":true},\"enabled\":true,"
                + "\"latestPublishedVersion\":\"1.0.0\",\"versionDetails\":[{\"version\":\"1.0.0\",\"release_date\":"
                + "\"2023-07-01T00:00:00Z\",\"is_latest\":true}]}";
        McpServerVersionInfo mcpServerVersionInfo = mapper.readValue(json, McpServerVersionInfo.class);
        assertNotNull(mcpServerVersionInfo);
        assertEquals("b646506e-901b-41a1-8790-a4378d11055e", mcpServerVersionInfo.getId());
        assertEquals("testVersionInfo", mcpServerVersionInfo.getName());
        assertNotNull(mcpServerVersionInfo.getVersionDetail());
        assertEquals("1.0.0", mcpServerVersionInfo.getVersionDetail().getVersion());
        assertEquals("2023-07-01T00:00:00Z", mcpServerVersionInfo.getVersionDetail().getRelease_date());
        assertTrue(mcpServerVersionInfo.getVersionDetail().getIs_latest());
        assertTrue(mcpServerVersionInfo.isEnabled());
        assertEquals("1.0.0", mcpServerVersionInfo.getLatestPublishedVersion());
        assertNotNull(mcpServerVersionInfo.getVersionDetails());
        assertEquals(1, mcpServerVersionInfo.getVersionDetails().size());
        ServerVersionDetail versionDetail = mcpServerVersionInfo.getVersionDetails().get(0);
        assertEquals("1.0.0", versionDetail.getVersion());
        assertEquals("2023-07-01T00:00:00Z", versionDetail.getRelease_date());
        assertTrue(versionDetail.getIs_latest());
    }
}