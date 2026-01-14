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

package com.alibaba.nacos.api.ai.model.a2a;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSkillTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        AgentSkill agentSkill = new AgentSkill();
        agentSkill.setId("skill-1");
        agentSkill.setName("test skill");
        agentSkill.setDescription("test description");
        
        List<String> tags = Arrays.asList("tag1", "tag2");
        agentSkill.setTags(tags);
        
        List<String> examples = Arrays.asList("example1", "example2");
        agentSkill.setExamples(examples);
        
        List<String> inputModes = Arrays.asList("text", "voice");
        agentSkill.setInputModes(inputModes);
        
        List<String> outputModes = Arrays.asList("text", "image");
        agentSkill.setOutputModes(outputModes);
        
        String json = mapper.writeValueAsString(agentSkill);
        assertNotNull(json);
        assertTrue(json.contains("\"id\":\"skill-1\""));
        assertTrue(json.contains("\"name\":\"test skill\""));
        assertTrue(json.contains("\"description\":\"test description\""));
        assertTrue(json.contains("\"tags\":[\"tag1\",\"tag2\"]"));
        assertTrue(json.contains("\"examples\":[\"example1\",\"example2\"]"));
        assertTrue(json.contains("\"inputModes\":[\"text\",\"voice\"]"));
        assertTrue(json.contains("\"outputModes\":[\"text\",\"image\"]"));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{\"id\":\"skill-1\",\"name\":\"test skill\",\"description\":\"test description\","
                + "\"tags\":[\"tag1\",\"tag2\"],\"examples\":[\"example1\",\"example2\"],"
                + "\"inputModes\":[\"text\",\"voice\"],\"outputModes\":[\"text\",\"image\"]}";
        
        AgentSkill agentSkill = mapper.readValue(json, AgentSkill.class);
        assertNotNull(agentSkill);
        assertEquals("skill-1", agentSkill.getId());
        assertEquals("test skill", agentSkill.getName());
        assertEquals("test description", agentSkill.getDescription());
        assertEquals(2, agentSkill.getTags().size());
        assertEquals("tag1", agentSkill.getTags().get(0));
        assertEquals("tag2", agentSkill.getTags().get(1));
        assertEquals(2, agentSkill.getExamples().size());
        assertEquals("example1", agentSkill.getExamples().get(0));
        assertEquals("example2", agentSkill.getExamples().get(1));
        assertEquals(2, agentSkill.getInputModes().size());
        assertEquals("text", agentSkill.getInputModes().get(0));
        assertEquals("voice", agentSkill.getInputModes().get(1));
        assertEquals(2, agentSkill.getOutputModes().size());
        assertEquals("text", agentSkill.getOutputModes().get(0));
        assertEquals("image", agentSkill.getOutputModes().get(1));
    }
    
    @Test
    void testEqualsAndHashCode() {
        AgentSkill skill1 = new AgentSkill();
        skill1.setId("skill-1");
        skill1.setName("test skill");
        skill1.setDescription("test description");
        skill1.setTags(Arrays.asList("tag1", "tag2"));
        skill1.setExamples(Arrays.asList("example1", "example2"));
        skill1.setInputModes(Arrays.asList("text", "voice"));
        skill1.setOutputModes(Arrays.asList("text", "image"));
        
        AgentSkill skill2 = new AgentSkill();
        skill2.setId("skill-1");
        skill2.setName("test skill");
        skill2.setDescription("test description");
        skill2.setTags(Arrays.asList("tag1", "tag2"));
        skill2.setExamples(Arrays.asList("example1", "example2"));
        skill2.setInputModes(Arrays.asList("text", "voice"));
        skill2.setOutputModes(Arrays.asList("text", "image"));
        
        AgentSkill skill3 = new AgentSkill();
        skill3.setId("skill-2");
        
        assertEquals(skill1, skill2);
        assertEquals(skill1.hashCode(), skill2.hashCode());
        assertNotEquals(skill1, skill3);
        assertNotEquals(skill1.hashCode(), skill3.hashCode());
        assertNotEquals(skill1, null);
        assertNotEquals(skill1, new Object());
    }
}