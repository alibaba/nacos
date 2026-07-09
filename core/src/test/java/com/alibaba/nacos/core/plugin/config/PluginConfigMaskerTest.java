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

package com.alibaba.nacos.core.plugin.config;

import com.alibaba.nacos.api.plugin.ConfigItemDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginConfigMaskerTest {
    
    @Test
    void testMaskNonSensitiveValue() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setSensitive(false);
        
        assertEquals("secret", PluginConfigMasker.mask(definition, "secret"));
    }
    
    @Test
    void testMaskTinySensitiveValue() {
        ConfigItemDefinition definition = sensitiveDefinition();
        
        assertEquals("******", PluginConfigMasker.mask(definition, "ab"));
    }
    
    @Test
    void testMaskShortSensitiveValue() {
        ConfigItemDefinition definition = sensitiveDefinition();
        
        assertEquals("a******f", PluginConfigMasker.mask(definition, "abcdef"));
    }
    
    @Test
    void testMaskLongSensitiveValue() {
        ConfigItemDefinition definition = sensitiveDefinition();
        
        assertEquals("ab******kl", PluginConfigMasker.mask(definition, "abcdefghijkl"));
    }
    
    private ConfigItemDefinition sensitiveDefinition() {
        ConfigItemDefinition definition = new ConfigItemDefinition();
        definition.setSensitive(true);
        return definition;
    }
}
