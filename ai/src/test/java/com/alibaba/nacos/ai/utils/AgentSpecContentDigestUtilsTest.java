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

package com.alibaba.nacos.ai.utils;

import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpec;
import com.alibaba.nacos.api.ai.model.agentspecs.AgentSpecResource;
import com.alibaba.nacos.common.utils.MD5Utils;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link AgentSpecContentDigestUtils}.
 *
 * @author nacos
 */
class AgentSpecContentDigestUtilsTest {
    
    @Test
    void testComputeContentMd5RejectsNullAgentSpec() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentSpecContentDigestUtils.computeContentMd5(null));
    }
    
    @Test
    void testComputeContentMd5UsesCanonicalResourceOrder() {
        AgentSpec first = newAgentSpec("desc", "{\"name\":\"worker\"}",
            resources("config/SOUL.md", "soul", "skill/SKILL.md", "skill"));
        AgentSpec second = newAgentSpec("desc", "{\"name\":\"worker\"}",
            resources("skill/SKILL.md", "skill", "config/SOUL.md", "soul"));
        
        assertEquals(AgentSpecContentDigestUtils.computeContentMd5(first),
            AgentSpecContentDigestUtils.computeContentMd5(second));
    }
    
    @Test
    void testComputeContentMd5IncludesSeparatorsAndNullResourceContent() throws Exception {
        Map<String, AgentSpecResource> resources = new LinkedHashMap<>();
        resources.put("z-null", null);
        resources.put("a-blank", new AgentSpecResource());
        AgentSpec agentSpec = newAgentSpec("desc", "content", resources);
        
        String expectedInput = "desc\0content\0a-blank\0\0z-null\0\0";
        String expectedMd5 = MD5Utils.md5Hex(expectedInput.getBytes(StandardCharsets.UTF_8));
        
        assertEquals(expectedMd5, AgentSpecContentDigestUtils.computeContentMd5(agentSpec));
    }
    
    @Test
    void testComputeContentMd5AllowsBlankMainFieldsAndNoResources() {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setDescription(" ");
        agentSpec.setContent(null);
        
        String md5 = AgentSpecContentDigestUtils.computeContentMd5(agentSpec);
        
        assertFalse(md5.isEmpty());
    }
    
    private static AgentSpec newAgentSpec(String description, String content,
        Map<String, AgentSpecResource> resources) {
        AgentSpec agentSpec = new AgentSpec();
        agentSpec.setDescription(description);
        agentSpec.setContent(content);
        agentSpec.setResource(resources);
        return agentSpec;
    }
    
    private static Map<String, AgentSpecResource> resources(String firstName, String firstContent,
        String secondName, String secondContent) {
        Map<String, AgentSpecResource> resources = new LinkedHashMap<>();
        resources.put(firstName, resource(firstName, firstContent));
        resources.put(secondName, resource(secondName, secondContent));
        return resources;
    }
    
    private static AgentSpecResource resource(String name, String content) {
        AgentSpecResource resource = new AgentSpecResource();
        resource.setName(name);
        resource.setContent(content);
        return resource;
    }
}
