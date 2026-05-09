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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test for OptimizationChange.
 *
 * @author nacos
 */
class OptimizationChangeTest {
    
    @Test
    void testDefaultConstructor() {
        // When
        OptimizationChange change = new OptimizationChange();
        
        // Then
        assertNull(change.getField());
        assertNull(change.getType());
        assertNull(change.getDescription());
        assertNull(change.getReason());
    }
    
    @Test
    void testConstructorWithParameters() {
        // When
        OptimizationChange change = new OptimizationChange(
            "instruction",
            "improved",
            "Added more detailed steps",
            "To make the skill more actionable");
        
        // Then
        assertEquals("instruction", change.getField());
        assertEquals("improved", change.getType());
        assertEquals("Added more detailed steps", change.getDescription());
        assertEquals("To make the skill more actionable", change.getReason());
    }
    
    @Test
    void testGettersAndSetters() {
        // Given
        OptimizationChange change = new OptimizationChange();
        
        // When
        change.setField("description");
        change.setType("added");
        change.setDescription("Added a clear description");
        change.setReason("To improve clarity");
        
        // Then
        assertEquals("description", change.getField());
        assertEquals("added", change.getType());
        assertEquals("Added a clear description", change.getDescription());
        assertEquals("To improve clarity", change.getReason());
    }
    
    @Test
    void testInstructionFieldChange() {
        // Given
        OptimizationChange change = new OptimizationChange();
        
        // When
        change.setField("instruction");
        change.setType("improved");
        change.setDescription("Restructured the instruction for better clarity");
        change.setReason("The original instruction was too verbose");
        
        // Then
        assertEquals("instruction", change.getField());
        assertEquals("improved", change.getType());
        assertEquals("Restructured the instruction for better clarity", change.getDescription());
        assertEquals("The original instruction was too verbose", change.getReason());
    }
    
    @Test
    void testDescriptionFieldChange() {
        // Given
        OptimizationChange change = new OptimizationChange(
            "description",
            "added",
            "Added a comprehensive description",
            "The skill lacked a clear description");
        
        // Then
        assertEquals("description", change.getField());
        assertEquals("added", change.getType());
    }
    
    @Test
    void testRemovedChange() {
        // Given
        OptimizationChange change = new OptimizationChange();
        
        // When
        change.setField("metadata");
        change.setType("removed");
        change.setDescription("Removed unnecessary metadata field");
        change.setReason("The metadata was not being used");
        
        // Then
        assertEquals("metadata", change.getField());
        assertEquals("removed", change.getType());
        assertEquals("Removed unnecessary metadata field", change.getDescription());
        assertEquals("The metadata was not being used", change.getReason());
    }
    
    @Test
    void testMultipleChangeTypes() {
        // Test improved change
        OptimizationChange improved =
            new OptimizationChange("instruction", "improved", "Enhanced clarity", "Better UX");
        assertEquals("improved", improved.getType());
        
        // Test added change
        OptimizationChange added =
            new OptimizationChange("example", "added", "Added example", "Help users");
        assertEquals("added", added.getType());
        
        // Test removed change
        OptimizationChange removed =
            new OptimizationChange("deprecated", "removed", "Removed field", "No longer needed");
        assertEquals("removed", removed.getType());
    }
}
