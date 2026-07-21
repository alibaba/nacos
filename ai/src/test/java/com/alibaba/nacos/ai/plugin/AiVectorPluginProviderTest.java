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

import com.alibaba.nacos.ai.service.ard.vector.ArdVectorIndexRouter;
import com.alibaba.nacos.api.plugin.PluginType;
import com.alibaba.nacos.plugin.ai.ard.vector.spi.AiResourceVectorIndex;
import com.alibaba.nacos.sys.utils.ApplicationUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AiVectorPluginProvider}.
 *
 * @author nacos
 */
class AiVectorPluginProviderTest {
    
    @Test
    void shouldExposeAllLoadedVectorIndexes() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        ArdVectorIndexRouter router = mock(ArdVectorIndexRouter.class);
        AiResourceVectorIndex index = mock(AiResourceVectorIndex.class);
        when(router.allIndexes()).thenReturn(Map.of("postgresql", index));
        try (MockedStatic<ApplicationUtils> applicationUtils =
            Mockito.mockStatic(ApplicationUtils.class)) {
            applicationUtils.when(ApplicationUtils::getApplicationContext)
                .thenReturn(applicationContext);
            applicationUtils.when(() -> ApplicationUtils.getBean(ArdVectorIndexRouter.class))
                .thenReturn(router);
            
            AiVectorPluginProvider provider = new AiVectorPluginProvider();
            Map<String, AiResourceVectorIndex> plugins = provider.getAllPlugins();
            
            assertEquals(PluginType.AI_VECTOR, provider.getPluginType());
            assertSame(index, plugins.get("postgresql"));
        }
    }
    
    @Test
    void shouldReturnEmptyWhenApplicationContextIsUnavailable() {
        try (MockedStatic<ApplicationUtils> applicationUtils =
            Mockito.mockStatic(ApplicationUtils.class)) {
            assertTrue(new AiVectorPluginProvider().getAllPlugins().isEmpty());
        }
    }
}
