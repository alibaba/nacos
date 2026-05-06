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

package com.alibaba.nacos.copilot.form;

import com.alibaba.nacos.copilot.model.ConversationHistory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test for SkillGenerationForm.
 *
 * @author nacos
 */
class SkillGenerationFormTest {
    
    @Test
    void testValidateWithValidBackgroundInfo() {
        // Given
        SkillGenerationForm form = new SkillGenerationForm();
        form.setBackgroundInfo("Test background information");
        
        // When & Then
        // Should not throw exception
        form.validate();
    }
    
    @Test
    void testValidateWithNullBackgroundInfo() {
        // Given
        SkillGenerationForm form = new SkillGenerationForm();
        form.setBackgroundInfo(null);
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> form.validate());
    }
    
    @Test
    void testValidateWithEmptyBackgroundInfo() {
        // Given
        SkillGenerationForm form = new SkillGenerationForm();
        form.setBackgroundInfo("");
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> form.validate());
    }
    
    @Test
    void testValidateWithBlankBackgroundInfo() {
        // Given
        SkillGenerationForm form = new SkillGenerationForm();
        form.setBackgroundInfo("   ");
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> form.validate());
    }
    
    @Test
    void testGettersAndSetters() {
        // Given
        SkillGenerationForm form = new SkillGenerationForm();
        String backgroundInfo = "Test background";
        List<Map<String, Object>> tools = new ArrayList<>();
        Map<String, Object> tool = new HashMap<>();
        tool.put("name", "test-tool");
        tools.add(tool);
        ConversationHistory history = new ConversationHistory();
        
        // When
        form.setBackgroundInfo(backgroundInfo);
        form.setSelectedMcpTools(tools);
        form.setConversationHistory(history);
        
        // Then
        assertEquals(backgroundInfo, form.getBackgroundInfo());
        assertEquals(tools, form.getSelectedMcpTools());
        assertEquals(history, form.getConversationHistory());
    }
    
    @Test
    void testDefaultValues() {
        // When
        SkillGenerationForm form = new SkillGenerationForm();
        
        // Then
        assertNull(form.getBackgroundInfo());
        assertNull(form.getSelectedMcpTools());
        assertNull(form.getConversationHistory());
    }
}
