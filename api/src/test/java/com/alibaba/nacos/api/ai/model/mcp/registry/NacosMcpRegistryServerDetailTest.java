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

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.api.ai.model.mcp.McpEndpointSpec;
import com.alibaba.nacos.api.ai.model.mcp.McpToolSpecification;
import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NacosMcpRegistryServerDetailTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        // Repository是空对象
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        NacosMcpRegistryServerDetail mcpRegistryServerDetail = new NacosMcpRegistryServerDetail();
        mcpRegistryServerDetail.setId(UUID.randomUUID().toString());
        mcpRegistryServerDetail.setName("testRegistryServer");
        mcpRegistryServerDetail.setDescription("test mcp registry server object");
        mcpRegistryServerDetail.setRepository(new Repository());
        mcpRegistryServerDetail.setVersion_detail(new ServerVersionDetail());
        mcpRegistryServerDetail.getVersion_detail().setVersion("1.0.0");
        mcpRegistryServerDetail.getVersion_detail().setRelease_date("2022-01-01");
        mcpRegistryServerDetail.getVersion_detail().setIs_latest(true);
        mcpRegistryServerDetail.setRemotes(Collections.singletonList(new Remote()));
        mcpRegistryServerDetail.getRemotes().get(0).setUrl("127.0.0.1:8848/sse");
        mcpRegistryServerDetail.getRemotes().get(0).setTransportType("https");
        mcpRegistryServerDetail.setNacosMcpEndpointSpec(new McpEndpointSpec());
        mcpRegistryServerDetail.setMcpToolSpecification(new McpToolSpecification());
        mcpRegistryServerDetail.setNacosNamespaceId(AiConstants.Mcp.MCP_DEFAULT_NAMESPACE);
        String json = mapper.writeValueAsString(mcpRegistryServerDetail);
        assertNotNull(json);
        assertTrue(json.contains(String.format("\"id\":\"%s\"", mcpRegistryServerDetail.getId())));
        assertTrue(json.contains("\"name\":\"testRegistryServer\""));
        assertTrue(json.contains("\"description\":\"test mcp registry server object\""));
        assertTrue(json.contains("\"repository\":{}"));
        assertTrue(json.contains("\"version_detail\":{"));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"release_date\":\"2022-01-01\""));
        assertTrue(json.contains("\"is_latest\":true"));
        assertTrue(json.contains("\"remotes\":[{"));
        assertTrue(json.contains("\"url\":\"127.0.0.1:8848/sse\""));
        assertTrue(json.contains("\"transport_type\":\"https\""));
        assertTrue(json.contains("\"nacosMcpEndpointSpec\":{\"data\":{}}"));
        assertTrue(json.contains("\"mcpToolSpecification\":{\"tools\":[],\"toolsMeta\":{},\"securitySchemes\":[]}"));
        assertTrue(json.contains("\"nacosNamespaceId\":\"public\""));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"id\":\"ada27489-8572-4746-80a2-11baaf8c2f84\",\"name\":\"testRegistryServer\",\"description\":"
                + "\"test mcp registry server object\",\"repository\":{},\"version_detail\":{\"version\":\"1.0.0\","
                + "\"release_date\":\"2022-01-01\",\"is_latest\":true},\"remotes\":[{\"transport_type\":\"https\","
                + "\"url\":\"127.0.0.1:8848/sse\"}],\"nacosMcpEndpointSpec\":{\"data\":{}},\"mcpToolSpecification\":"
                + "{\"tools\":[],\"toolsMeta\":{}},\"nacosNamespaceId\":\"public\"}";
        NacosMcpRegistryServerDetail mcpRegistryServerDetail = mapper.readValue(json, NacosMcpRegistryServerDetail.class);
        assertNotNull(mcpRegistryServerDetail);
        assertEquals("ada27489-8572-4746-80a2-11baaf8c2f84", mcpRegistryServerDetail.getId());
        assertEquals("testRegistryServer", mcpRegistryServerDetail.getName());
        assertEquals("test mcp registry server object", mcpRegistryServerDetail.getDescription());
        assertNotNull(mcpRegistryServerDetail.getRepository());
        assertNotNull(mcpRegistryServerDetail.getVersion_detail());
        assertEquals("1.0.0", mcpRegistryServerDetail.getVersion_detail().getVersion());
        assertEquals("2022-01-01", mcpRegistryServerDetail.getVersion_detail().getRelease_date());
        assertTrue(mcpRegistryServerDetail.getVersion_detail().getIs_latest());
        assertNotNull(mcpRegistryServerDetail.getRemotes());
        assertEquals(1, mcpRegistryServerDetail.getRemotes().size());
        assertEquals("https", mcpRegistryServerDetail.getRemotes().get(0).getTransportType());
        assertEquals("127.0.0.1:8848/sse", mcpRegistryServerDetail.getRemotes().get(0).getUrl());
        assertNotNull(mcpRegistryServerDetail.getNacosMcpEndpointSpec());
        assertNotNull(mcpRegistryServerDetail.getMcpToolSpecification());
        assertEquals("public", mcpRegistryServerDetail.getNacosNamespaceId());
    }
}
