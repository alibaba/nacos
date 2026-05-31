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

package com.alibaba.nacos.config.server.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigPersistContextTest {
    
    @AfterEach
    void tearDown() {
        ConfigPersistContext.clear();
    }
    
    @Test
    void testDefaultIsNotSkipHistory() {
        assertFalse(ConfigPersistContext.isSkipHistory());
    }
    
    @Test
    void testSetSkipHistoryTrue() {
        ConfigPersistContext.setSkipHistory(true);
        assertTrue(ConfigPersistContext.isSkipHistory());
    }
    
    @Test
    void testSetSkipHistoryFalseClears() {
        ConfigPersistContext.setSkipHistory(true);
        assertTrue(ConfigPersistContext.isSkipHistory());
        ConfigPersistContext.setSkipHistory(false);
        assertFalse(ConfigPersistContext.isSkipHistory());
    }
    
    @Test
    void testClear() {
        ConfigPersistContext.setSkipHistory(true);
        ConfigPersistContext.clear();
        assertFalse(ConfigPersistContext.isSkipHistory());
    }
    
    @Test
    void testWithSkipHistoryGuard() {
        assertFalse(ConfigPersistContext.isSkipHistory());
        try (ConfigPersistContext.Guard ignored =
            ConfigPersistContext.withSkipHistory()) {
            assertTrue(ConfigPersistContext.isSkipHistory());
        }
        assertFalse(ConfigPersistContext.isSkipHistory());
    }
    
    @Test
    void testGuardRestoresPreviousTrue() {
        ConfigPersistContext.setSkipHistory(true);
        try (ConfigPersistContext.Guard ignored =
            ConfigPersistContext.withSkipHistory()) {
            assertTrue(ConfigPersistContext.isSkipHistory());
        }
        assertTrue(ConfigPersistContext.isSkipHistory());
    }
}
