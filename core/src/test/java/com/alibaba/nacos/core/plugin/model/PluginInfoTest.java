/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.alibaba.nacos.core.plugin.model;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import com.alibaba.nacos.api.plugin.PluginType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginInfoTest {
    
    @Test
    void gettersSetters() {
        PluginInfo info = new PluginInfo();
        info.setPluginId("trace:test");
        info.setPluginName("test");
        info.setPluginType(PluginType.TRACE);
        info.setClassName("com.example.TracePlugin");
        info.setDescription("trace plugin");
        info.setEnabled(true);
        info.setCritical(false);
        info.setConfigurable(true);
        info.setLoadTimestamp(12345L);
        Map<String, String> config = new HashMap<>();
        config.put("key", "value");
        info.setConfig(config);
        ConfigItemDefinition def = new ConfigItemDefinition();
        def.setKey("k");
        info.setConfigDefinitions(Collections.singletonList(def));
        
        assertEquals("trace:test", info.getPluginId());
        assertEquals("test", info.getPluginName());
        assertEquals(PluginType.TRACE, info.getPluginType());
        assertEquals("com.example.TracePlugin", info.getClassName());
        assertEquals("trace plugin", info.getDescription());
        assertTrue(info.isEnabled());
        assertFalse(info.isCritical());
        assertTrue(info.isConfigurable());
        assertEquals(12345L, info.getLoadTimestamp());
        assertEquals("value", info.getConfig().get("key"));
        assertNotNull(info.getConfigDefinitions());
        assertEquals(1, info.getConfigDefinitions().size());
    }
}
