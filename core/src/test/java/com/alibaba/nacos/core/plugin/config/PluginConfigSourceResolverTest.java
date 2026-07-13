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

import com.alibaba.nacos.core.plugin.model.PluginConfigSourceType;
import com.alibaba.nacos.core.plugin.model.PluginInfo;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigSourceResolverTest {
    
    @Test
    void testReadOnlySourceRejectsUpdate() {
        PluginConfigSourceResolver resolver = new PluginConfigSourceResolver() {
            
            @Override
            public Map<String, String> getConfig(PluginInfo pluginInfo) {
                return Collections.emptyMap();
            }
            
            @Override
            public PluginConfigSourceType getSourceType() {
                return PluginConfigSourceType.STATIC;
            }
        };
        
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
            () -> resolver.updateConfig("trace:demo", Collections.emptyMap()));
        
        resolver.initializeConfig(new PluginInfo());
        resolver.refreshConfig(new PluginInfo());
        
        assertFalse(resolver.isUpdatable());
        assertTrue(exception.getMessage().contains("STATIC"));
    }
}
