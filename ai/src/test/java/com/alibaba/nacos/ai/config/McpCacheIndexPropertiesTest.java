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

package com.alibaba.nacos.ai.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McpCacheIndexPropertiesTest {
    
    @Test
    public void testToString() {
        McpCacheIndexProperties properties = new McpCacheIndexProperties();
        // Test default value.
        assertEquals(
                "McpCacheIndexProperties{enabled=true, maxSize=10000, expireTimeSeconds=3600, cleanupIntervalSeconds=300, syncIntervalSeconds=300}",
                properties.toString());
        properties.setEnabled(false);
        properties.setCleanupIntervalSeconds(10);
        properties.setSyncIntervalSeconds(10);
        properties.setExpireTimeSeconds(10);
        properties.setMaxSize(100);
        assertEquals(
                "McpCacheIndexProperties{enabled=false, maxSize=100, expireTimeSeconds=10, cleanupIntervalSeconds=10, syncIntervalSeconds=10}",
                properties.toString());
    }
}