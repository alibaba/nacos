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

package com.alibaba.nacos.ai.service.ard;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.ai.model.AiResourceVersion;
import com.alibaba.nacos.ai.model.ard.ArdEntry;
import com.alibaba.nacos.ai.storage.NacosConfigAiResourceStorage;
import com.alibaba.nacos.api.ai.model.mcp.McpServerBasicInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link ArdEntryBuilder}.
 *
 * @author nacos
 */
class ArdEntryBuilderTest {
    
    private final ArdEntryBuilder builder = new ArdEntryBuilder();
    
    @Test
    void fromAiResourceShouldExposeSkillClientUrl() {
        ArdEntry entry = builder.fromAiResource(resource(Constants.Skills.RESOURCE_TYPE_SKILL,
            "avatar skill"), version("1.0.0"));
        
        assertEquals("/v3/ai/ard/artifacts?namespaceId=public&resourceType=skill"
            + "&resourceName=avatar+skill&version=1.0.0", entry.getUrl());
    }
    
    @Test
    void fromAiResourceShouldExposePromptClientUrl() {
        ArdEntry entry = builder.fromAiResource(
            resource(NacosConfigAiResourceStorage.RESOURCE_TYPE_PROMPT, "avatar prompt"),
            version("2.0.0"));
        
        assertEquals("/v3/ai/ard/artifacts?namespaceId=public&resourceType=prompt"
            + "&resourceName=avatar+prompt&version=2.0.0", entry.getUrl());
    }
    
    @Test
    void fromMcpServerShouldExposeMcpDetailUrl() {
        McpServerBasicInfo server = new McpServerBasicInfo();
        server.setId("mcp/avatar server");
        server.setName("avatar-server");
        server.setVersion("3.0.0");
        
        ArdEntry entry = builder.fromMcpServer("public", server);
        
        assertEquals("/v3/ai/ard/artifacts?namespaceId=public&resourceType=mcp"
            + "&resourceName=mcp%2Favatar+server&version=3.0.0&mcpName=avatar-server",
            entry.getUrl());
    }
    
    @Test
    void fromMcpServerShouldUseNameWhenIdIsBlank() {
        McpServerBasicInfo server = new McpServerBasicInfo();
        server.setName("avatar server");
        server.setVersion("3.0.0");
        
        ArdEntry entry = builder.fromMcpServer("public", server);
        
        assertEquals("/v3/ai/ard/artifacts?namespaceId=public&resourceType=mcp"
            + "&resourceName=avatar+server&version=3.0.0&mcpName=avatar+server",
            entry.getUrl());
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
