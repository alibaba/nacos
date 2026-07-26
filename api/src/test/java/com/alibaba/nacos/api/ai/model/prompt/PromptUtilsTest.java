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

package com.alibaba.nacos.api.ai.model.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PromptUtils}.
 *
 * @author nacos
 */
class PromptUtilsTest {
    
    @Test
    void testBuildPromptVersionGroupBasic() {
        String group = PromptUtils.buildPromptVersionGroup("greeting", "1.0.0");
        assertTrue(group.startsWith(PromptUtils.PROMPT_GROUP_PREFIX));
        // Should contain encoded promptKey and version separated by double underscore
        assertTrue(group.contains("__"));
    }
    
    @Test
    void testBuildPromptVersionGroupDeterministicOutput() {
        String group1 = PromptUtils.buildPromptVersionGroup("greeting", "1.0.0");
        String group2 = PromptUtils.buildPromptVersionGroup("greeting", "1.0.0");
        assertEquals(group1, group2);
    }
    
    @Test
    void testBuildPromptVersionGroupDifferentVersionsProduceDifferentGroups() {
        String group1 = PromptUtils.buildPromptVersionGroup("greeting", "1.0.0");
        String group2 = PromptUtils.buildPromptVersionGroup("greeting", "2.0.0");
        assertTrue(!group1.equals(group2));
    }
    
    @Test
    void testBuildPromptVersionGroupDifferentKeysProduceDifferentGroups() {
        String group1 = PromptUtils.buildPromptVersionGroup("greeting", "1.0.0");
        String group2 = PromptUtils.buildPromptVersionGroup("farewell", "1.0.0");
        assertTrue(!group1.equals(group2));
    }
    
    @Test
    void testBuildPromptVersionGroupShouldThrowOnBlankKey() {
        assertThrows(IllegalArgumentException.class,
            () -> PromptUtils.buildPromptVersionGroup("", "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> PromptUtils.buildPromptVersionGroup(null, "1.0.0"));
        assertThrows(IllegalArgumentException.class,
            () -> PromptUtils.buildPromptVersionGroup("  ", "1.0.0"));
    }
    
    @Test
    void testBuildPromptVersionGroupShouldThrowOnBlankVersion() {
        assertThrows(IllegalArgumentException.class,
            () -> PromptUtils.buildPromptVersionGroup("greeting", ""));
        assertThrows(IllegalArgumentException.class,
            () -> PromptUtils.buildPromptVersionGroup("greeting", null));
        assertThrows(IllegalArgumentException.class,
            () -> PromptUtils.buildPromptVersionGroup("greeting", "  "));
    }
    
    @Test
    void testPromptMainDataIdConstant() {
        assertEquals("content.json", PromptUtils.PROMPT_MAIN_DATA_ID);
    }
    
    @Test
    void testPromptGroupPrefixConstant() {
        assertEquals("prompt__", PromptUtils.PROMPT_GROUP_PREFIX);
    }
}
