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

package com.alibaba.nacos.ai.config;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.service.McpServerOperationService;
import com.alibaba.nacos.ai.service.search.AiResourceIndexContentLoaderImpl;
import com.alibaba.nacos.ai.service.search.AiResourceIndexMaintenanceServiceImpl;
import com.alibaba.nacos.ai.service.search.AiResourceIndexService;
import com.alibaba.nacos.ai.service.search.AiResourceIndexServiceImpl;
import com.alibaba.nacos.ai.service.search.AiResourceIndexTaskRepository;
import com.alibaba.nacos.ai.service.search.HashingAiResourceEmbeddingService;
import com.alibaba.nacos.ai.service.search.JdbcAiResourceIndexTaskRepository;
import com.alibaba.nacos.ai.service.search.JdbcAiResourceSearchRepository;
import com.alibaba.nacos.ai.service.search.OpenAiCompatibleResourceIndexEnhancementService;
import com.alibaba.nacos.ai.service.search.AiResourceSearchService;
import com.alibaba.nacos.ai.service.search.vector.AiResourceVectorIndexRouter;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link ConditionalOnAiResourceSearchEnabled}.
 *
 * @author nacos
 */
class ConditionalOnAiResourceSearchEnabledTest {
    
    private static final Class<?>[] SEARCH_COMPONENTS = {AiResourceIndexBackfillTask.class,
        AiResourceIndexTaskConsumer.class, AiResourceIndexServiceImpl.class,
        AiResourceIndexMaintenanceServiceImpl.class, AiResourceIndexContentLoaderImpl.class,
        AiResourceSearchService.class, HashingAiResourceEmbeddingService.class,
        JdbcAiResourceSearchRepository.class, JdbcAiResourceIndexTaskRepository.class,
        OpenAiCompatibleResourceIndexEnhancementService.class, AiResourceVectorIndexRouter.class};
    
    @Test
    void shouldBeEnabledByDefaultIndependentlyFromArd() {
        ConditionalOnProperty condition =
            ConditionalOnAiResourceSearchEnabled.class.getAnnotation(ConditionalOnProperty.class);
        
        assertEquals(Constants.AI_RESOURCE_SEARCH_ENABLED_KEY, condition.value()[0]);
        assertEquals("true", condition.havingValue());
        assertTrue(condition.matchIfMissing());
        try (AnnotationConfigApplicationContext context = newContext(
            Collections.singletonMap(Constants.ARD_ENABLED_KEY, "false"))) {
            context.register(HashingAiResourceEmbeddingService.class);
            context.refresh();
            
            assertNotNull(context.getBean(HashingAiResourceEmbeddingService.class));
        }
    }
    
    @Test
    void shouldBeDisabledWhenExplicitlyConfiguredFalse() {
        assertSearchComponentsDisabled(
            Collections.singletonMap(Constants.AI_RESOURCE_SEARCH_ENABLED_KEY, "false"));
    }
    
    @Test
    void shouldRegisterSearchComponentWhenExplicitlyEnabled() {
        try (AnnotationConfigApplicationContext context = newContext(
            Collections.singletonMap(Constants.AI_RESOURCE_SEARCH_ENABLED_KEY, "true"))) {
            context.register(HashingAiResourceEmbeddingService.class);
            context.refresh();
            
            assertNotNull(context.getBean(HashingAiResourceEmbeddingService.class));
        }
    }
    
    @Test
    void shouldConstructTaskConsumerWhenExplicitlyEnabled() {
        try (AnnotationConfigApplicationContext context = newContext(
            Collections.singletonMap(Constants.AI_RESOURCE_SEARCH_ENABLED_KEY, "true"))) {
            context.registerBean(AiResourceIndexTaskRepository.class,
                () -> mock(AiResourceIndexTaskRepository.class));
            context.registerBean(AiResourceIndexService.class,
                () -> mock(AiResourceIndexService.class));
            context.registerBean(McpServerOperationService.class,
                () -> mock(McpServerOperationService.class));
            context.register(AiResourceIndexTaskConsumer.class);
            EnvUtil.setEnvironment(context.getEnvironment());
            context.refresh();
            
            assertNotNull(context.getBean(AiResourceIndexTaskConsumer.class));
        }
    }
    
    private void assertSearchComponentsDisabled(Map<String, Object> properties) {
        try (AnnotationConfigApplicationContext context = newContext(properties)) {
            context.register(SEARCH_COMPONENTS);
            context.refresh();
            
            for (Class<?> component : SEARCH_COMPONENTS) {
                assertTrue(context.getBeansOfType(component).isEmpty(),
                    () -> component.getSimpleName() + " should not be registered");
            }
        }
    }
    
    private AnnotationConfigApplicationContext newContext(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (!properties.isEmpty()) {
            context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("search-test", properties));
        }
        return context;
    }
}
