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

package com.alibaba.nacos.copilot.model;

import com.alibaba.nacos.api.ai.model.skills.Skill;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for SkillOptimizationResponse.
 *
 * @author nacos
 */
class SkillOptimizationResponseTest {
    
    @Test
    void testDefaultConstructor() {
        // When
        SkillOptimizationResponse response = new SkillOptimizationResponse();
        
        // Then
        assertNull(response.getType());
        assertNull(response.getChunk());
        assertNull(response.getOptimizedSkill());
        assertNull(response.getChanges());
        assertNull(response.getQualityScore());
        assertNull(response.getExplanation());
        assertFalse(response.isDone());
    }
    
    @Test
    void testStreamingResponse() {
        // Given
        SkillOptimizationResponse response = new SkillOptimizationResponse();
        
        // When
        response.setType(StreamResponseType.CONTENT);
        response.setChunk("Optimizing skill...");
        response.setDone(false);
        
        // Then
        assertEquals(StreamResponseType.CONTENT, response.getType());
        assertEquals("Optimizing skill...", response.getChunk());
        assertFalse(response.isDone());
        assertNull(response.getOptimizedSkill());
    }
    
    @Test
    void testCompleteResponse() {
        // Given
        SkillOptimizationResponse response = new SkillOptimizationResponse();
        Skill optimizedSkill = new Skill();
        optimizedSkill.setName("optimized-skill");
        
        List<OptimizationChange> changes = new ArrayList<>();
        changes.add(
            new OptimizationChange("instruction", "improved", "Enhanced clarity", "Better UX"));
        changes.add(
            new OptimizationChange("description", "added", "Added description", "More context"));
        
        // When
        response.setType(StreamResponseType.DONE);
        response.setChunk(null);
        response.setOptimizedSkill(optimizedSkill);
        response.setChanges(changes);
        response.setQualityScore(0.95);
        response.setExplanation("The skill was optimized for better clarity and usability");
        response.setDone(true);
        
        // Then
        assertEquals(StreamResponseType.DONE, response.getType());
        assertNull(response.getChunk());
        assertEquals(optimizedSkill, response.getOptimizedSkill());
        assertEquals(2, response.getChanges().size());
        assertEquals(0.95, response.getQualityScore());
        assertEquals("The skill was optimized for better clarity and usability",
            response.getExplanation());
        assertTrue(response.isDone());
    }
    
    @Test
    void testOptimizationChanges() {
        // Given
        SkillOptimizationResponse response = new SkillOptimizationResponse();
        List<OptimizationChange> changes = new ArrayList<>();
        
        OptimizationChange change1 =
            new OptimizationChange("instruction", "improved", "Better steps", "Clarity");
        OptimizationChange change2 =
            new OptimizationChange("description", "added", "New description", "Context");
        OptimizationChange change3 =
            new OptimizationChange("metadata", "removed", "Removed field", "Unused");
        
        changes.add(change1);
        changes.add(change2);
        changes.add(change3);
        
        // When
        response.setChanges(changes);
        
        // Then
        assertEquals(3, response.getChanges().size());
        assertEquals("instruction", response.getChanges().get(0).getField());
        assertEquals("description", response.getChanges().get(1).getField());
        assertEquals("metadata", response.getChanges().get(2).getField());
    }
    
    @Test
    void testQualityScore() {
        // Given
        SkillOptimizationResponse response = new SkillOptimizationResponse();
        
        // When
        response.setQualityScore(0.85);
        
        // Then
        assertEquals(0.85, response.getQualityScore());
        
        // When
        response.setQualityScore(1.0);
        
        // Then
        assertEquals(1.0, response.getQualityScore());
        
        // When
        response.setQualityScore(0.0);
        
        // Then
        assertEquals(0.0, response.getQualityScore());
    }
    
    @Test
    void testOptimizedSkill() {
        // Given
        SkillOptimizationResponse response = new SkillOptimizationResponse();
        Skill skill = new Skill();
        skill.setName("test-skill");
        skill.setDescription("Optimized skill");
        skill.setSkillMd(
            "---\nname: test-skill\ndescription: Optimized skill\n---\n\nImproved instruction");
        
        // When
        response.setOptimizedSkill(skill);
        
        // Then
        assertEquals(skill, response.getOptimizedSkill());
        assertEquals("test-skill", response.getOptimizedSkill().getName());
        assertEquals("Optimized skill", response.getOptimizedSkill().getDescription());
    }
    
    @Test
    void testExplanation() {
        // Given
        SkillOptimizationResponse response = new SkillOptimizationResponse();
        
        // When
        response
            .setExplanation("The skill was optimized to improve clarity and add error handling");
        
        // Then
        assertEquals("The skill was optimized to improve clarity and add error handling",
            response.getExplanation());
    }
    
    @Test
    void testStreamingFlow() {
        // Simulate a streaming optimization response
        // Thinking phase
        SkillOptimizationResponse thinking = new SkillOptimizationResponse();
        thinking.setType(StreamResponseType.THINKING);
        thinking.setChunk("Analyzing the skill structure...");
        thinking.setDone(false);
        assertEquals(StreamResponseType.THINKING, thinking.getType());
        
        // Content phase
        SkillOptimizationResponse content = new SkillOptimizationResponse();
        content.setType(StreamResponseType.CONTENT);
        content.setChunk("Optimizing instruction...");
        content.setDone(false);
        assertEquals(StreamResponseType.CONTENT, content.getType());
        
        // Done phase
        SkillOptimizationResponse done = new SkillOptimizationResponse();
        done.setType(StreamResponseType.DONE);
        done.setDone(true);
        Skill skill = new Skill();
        skill.setName("optimized");
        done.setOptimizedSkill(skill);
        done.setQualityScore(0.9);
        assertTrue(done.isDone());
        assertEquals(skill, done.getOptimizedSkill());
    }
}
