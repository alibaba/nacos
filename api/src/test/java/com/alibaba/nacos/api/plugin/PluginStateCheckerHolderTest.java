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

package com.alibaba.nacos.api.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginStateCheckerHolderTest {
    
    @Test
    @DisplayName("test getInstance returns empty when no checker set")
    void testGetInstanceReturnsEmptyWhenNoCheckerSet() {
        // Reset to null before test
        PluginStateCheckerHolder.setInstance(null);
        Optional<PluginStateChecker> checker = PluginStateCheckerHolder.getInstance();
        assertFalse(checker.isPresent());
    }
    
    @Test
    @DisplayName("test setInstance and getInstance")
    void testSetInstanceAndGetInstance() {
        PluginStateChecker mockChecker = new MockPluginStateChecker();
        PluginStateCheckerHolder.setInstance(mockChecker);
        Optional<PluginStateChecker> checker = PluginStateCheckerHolder.getInstance();
        assertTrue(checker.isPresent());
        assertNotNull(checker.get());
        // Reset after test
        PluginStateCheckerHolder.setInstance(null);
    }
    
    @Test
    @DisplayName("test isPluginEnabled returns true when no checker set")
    void testIsPluginEnabledReturnsTrueWhenNoCheckerSet() {
        PluginStateCheckerHolder.setInstance(null);
        boolean enabled = PluginStateCheckerHolder.isPluginEnabled("auth", "test-plugin");
        assertTrue(enabled);
    }
    
    @Test
    @DisplayName("test isPluginEnabled with checker set returns checker result")
    void testIsPluginEnabledWithCheckerSetReturnsCheckerResult() {
        MockPluginStateChecker mockChecker = new MockPluginStateChecker();
        PluginStateCheckerHolder.setInstance(mockChecker);
        boolean enabled = PluginStateCheckerHolder.isPluginEnabled("auth", "enabled-plugin");
        assertTrue(enabled);
        boolean disabled = PluginStateCheckerHolder.isPluginEnabled("auth", "disabled-plugin");
        assertFalse(disabled);
        // Reset after test
        PluginStateCheckerHolder.setInstance(null);
    }
    
    /**
     * Mock implementation of PluginStateChecker for testing.
     */
    private static class MockPluginStateChecker implements PluginStateChecker {
        
        @Override
        public boolean isPluginEnabled(String pluginType, String pluginName) {
            return "enabled-plugin".equals(pluginName);
        }
    }
}
