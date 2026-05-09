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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginInfoVOTest {
    
    @Test
    void gettersSettersAndToString() {
        PluginInfoVO vo = new PluginInfoVO();
        vo.setPluginId("auth:nacos");
        vo.setPluginType("auth");
        vo.setPluginName("nacos");
        vo.setEnabled(true);
        vo.setCritical(true);
        vo.setConfigurable(false);
        vo.setExclusive(true);
        vo.setAvailableNodeCount(3);
        vo.setTotalNodeCount(5);
        
        assertEquals("auth:nacos", vo.getPluginId());
        assertEquals("auth", vo.getPluginType());
        assertEquals("nacos", vo.getPluginName());
        assertEquals(true, vo.getEnabled());
        assertEquals(true, vo.getCritical());
        assertEquals(false, vo.getConfigurable());
        assertEquals(true, vo.getExclusive());
        assertEquals(3, vo.getAvailableNodeCount());
        assertEquals(5, vo.getTotalNodeCount());
        
        String s = vo.toString();
        assertNotNull(s);
        assertTrue(s.contains("auth:nacos"));
        assertTrue(s.contains("enabled=true"));
    }
}
