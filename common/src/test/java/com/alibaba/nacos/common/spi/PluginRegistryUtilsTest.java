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

package com.alibaba.nacos.common.spi;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PluginRegistryUtilsTest {
    
    @Test
    void testRegisterFirst() {
        Logger logger = mock(Logger.class);
        Map<String, Object> plugins = new LinkedHashMap<>();
        Object first = new Object();
        Object duplicate = new Object();
        
        assertFalse(PluginRegistryUtils.registerFirst(plugins, "test", " ", first, logger));
        assertFalse(PluginRegistryUtils.registerFirst(plugins, "test", "null", null, logger));
        assertTrue(PluginRegistryUtils.registerFirst(plugins, "test", "same", first, logger));
        assertFalse(
            PluginRegistryUtils.registerFirst(plugins, "test", "same", duplicate, logger));
        
        assertSame(first, plugins.get("same"));
    }
}
