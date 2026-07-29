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

package com.alibaba.nacos.ai.plugin;

import com.alibaba.nacos.ai.importer.manager.AiResourceImportPluginManager;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.ai.importer.spi.AiResourceImportServiceBuilder;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiResourceImportPluginProviderTest {
    
    @Test
    void testPluginType() {
        assertEquals(PluginType.AI_RESOURCE_IMPORT,
            new AiResourceImportPluginProvider().getPluginType());
    }
    
    @Test
    void testRejectsUnavailableApplicationContext() {
        try (MockedStatic<ApplicationUtils> applicationUtils =
            Mockito.mockStatic(ApplicationUtils.class)) {
            assertThrows(IllegalStateException.class,
                () -> new AiResourceImportPluginProvider().getAllPlugins());
        }
    }
    
    @Test
    void testReturnsStableManagerPlugins() {
        ApplicationContext context = mock(ApplicationContext.class);
        AiResourceImportPluginManager manager = mock(AiResourceImportPluginManager.class);
        AiResourceImportServiceBuilder builder = mock(AiResourceImportServiceBuilder.class);
        Map<String, AiResourceImportServiceBuilder> plugins =
            Collections.singletonMap("source", builder);
        when(manager.loadPlugins()).thenReturn(plugins);
        try (MockedStatic<ApplicationUtils> applicationUtils =
            Mockito.mockStatic(ApplicationUtils.class)) {
            applicationUtils.when(ApplicationUtils::getApplicationContext).thenReturn(context);
            applicationUtils.when(
                () -> ApplicationUtils.getBean(AiResourceImportPluginManager.class))
                .thenReturn(manager);
            
            assertSame(plugins, new AiResourceImportPluginProvider().getAllPlugins());
        }
    }
}
