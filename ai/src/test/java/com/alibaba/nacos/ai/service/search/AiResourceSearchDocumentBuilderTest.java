/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.search;

import com.alibaba.nacos.ai.constant.AiResourceConstants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.search.AiResourceSearchDocument;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link AiResourceSearchDocumentBuilder}.
 *
 * @author nacos
 */
class AiResourceSearchDocumentBuilderTest {
    
    private final AiResourceSearchDocumentBuilder builder = new AiResourceSearchDocumentBuilder();
    
    @Test
    void fromAiResourceShouldBuildSkillSearchEntry() {
        AiResourceSearchDocument entry =
            builder.fromAiResource(resource(AiResourceConstants.RESOURCE_TYPE_SKILL,
                "avatar skill"), version("1.0.0"));
        
        assertEquals(AiResourceConstants.RESOURCE_TYPE_SKILL, entry.getResourceType());
        assertEquals("avatar skill", entry.getResourceName());
        assertEquals("1.0.0", entry.getResourceVersion());
        Map<?, ?> metadata = JacksonUtils.toObj(entry.getMetadata(), Map.class);
        assertEquals("SKILL.md", metadata.get("entrypoint"));
    }
    
    @Test
    void fromAiResourceShouldBuildPromptSearchEntry() {
        AiResourceSearchDocument entry = builder.fromAiResource(
            resource(AiResourceConstants.RESOURCE_TYPE_PROMPT, "avatar prompt"),
            version("2.0.0"));
        
        assertEquals(AiResourceConstants.RESOURCE_TYPE_PROMPT, entry.getResourceType());
        assertEquals("avatar prompt", entry.getResourceName());
        assertEquals("2.0.0", entry.getResourceVersion());
    }
    
    @Test
    void fromMcpServerShouldRetainArtifactLookupMetadata() {
        McpServerBasicInfo server = new McpServerBasicInfo();
        server.setId("mcp/avatar server");
        server.setName("avatar-server");
        server.setVersion("3.0.0");
        
        AiResourceSearchDocument entry = builder.fromMcpServer("public", server);
        
        assertEquals(AiResourceConstants.RESOURCE_TYPE_MCP, entry.getResourceType());
        assertEquals("mcp/avatar server", entry.getResourceName());
        Map<?, ?> metadata = JacksonUtils.toObj(entry.getMetadata(), Map.class);
        assertEquals("avatar-server", metadata.get("mcpName"));
    }
    
    @Test
    void fromMcpServerShouldUseNameWhenIdIsBlank() {
        McpServerBasicInfo server = new McpServerBasicInfo();
        server.setName("avatar server");
        server.setVersion("3.0.0");
        
        AiResourceSearchDocument entry = builder.fromMcpServer("public", server);
        
        assertEquals("avatar server", entry.getResourceName());
    }
    
    private AiResource resource(String type, String name) {
        AiResource resource = new AiResource();
        resource.setNamespaceId("public");
        resource.setType(type);
        resource.setName(name);
        resource.setDesc("demo");
        return resource;
    }
    
    private AiResourceVersion version(String version) {
        AiResourceVersion resourceVersion = new AiResourceVersion();
        resourceVersion.setVersion(version);
        resourceVersion.setDesc("demo version");
        return resourceVersion;
    }
}
