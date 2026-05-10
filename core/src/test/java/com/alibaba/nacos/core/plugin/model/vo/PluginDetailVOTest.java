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

package com.alibaba.nacos.core.plugin.model.vo;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginDetailVOTest {
    
    @Test
    void gettersSettersAndToString() {
        PluginDetailVO vo = new PluginDetailVO();
        vo.setPluginId("trace:otel");
        vo.setPluginType("trace");
        vo.setPluginName("otel");
        vo.setEnabled(false);
        vo.setCritical(false);
        vo.setConfigurable(true);
        Map<String, String> config = new HashMap<>();
        config.put("endpoint", "http://localhost");
        vo.setConfig(config);
        ConfigItemDefinition def = new ConfigItemDefinition();
        def.setKey("endpoint");
        vo.setConfigDefinitions(Collections.singletonList(def));
        
        assertEquals("trace:otel", vo.getPluginId());
        assertEquals("trace", vo.getPluginType());
        assertEquals("otel", vo.getPluginName());
        assertEquals(false, vo.getEnabled());
        assertEquals(false, vo.getCritical());
        assertEquals(true, vo.getConfigurable());
        assertEquals("http://localhost", vo.getConfig().get("endpoint"));
        assertNotNull(vo.getConfigDefinitions());
        assertEquals(1, vo.getConfigDefinitions().size());
        
        String s = vo.toString();
        assertNotNull(s);
        assertTrue(s.contains("trace:otel"));
    }
}
