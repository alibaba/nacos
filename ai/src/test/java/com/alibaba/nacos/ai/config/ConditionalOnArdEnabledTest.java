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
import com.alibaba.nacos.ai.service.ard.AiResourceArdIndexContentLoader;
import com.alibaba.nacos.ai.service.ard.ArdIndexBuildServiceImpl;
import com.alibaba.nacos.ai.service.ard.HashingArdEmbeddingService;
import com.alibaba.nacos.ai.service.ard.JdbcArdIndexRepository;
import com.alibaba.nacos.ai.service.ard.OpenAiCompatibleArdIndexEnhancementService;
import com.alibaba.nacos.ai.service.ard.vector.ArdVectorIndexRouter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ConditionalOnArdEnabled}.
 *
 * @author nacos
 */
class ConditionalOnArdEnabledTest {
    
    private static final Class<?>[] ARD_COMPONENTS = {ArdIndexBackfillTask.class,
        ArdIndexBuildServiceImpl.class, AiResourceArdIndexContentLoader.class,
        HashingArdEmbeddingService.class, JdbcArdIndexRepository.class,
        OpenAiCompatibleArdIndexEnhancementService.class, ArdVectorIndexRouter.class};
    
    @Test
    void shouldBeDisabledByDefault() {
        ConditionalOnProperty condition =
            ConditionalOnArdEnabled.class.getAnnotation(ConditionalOnProperty.class);
        
        assertEquals(Constants.ARD_ENABLED_KEY, condition.value()[0]);
        assertEquals("true", condition.havingValue());
        assertFalse(condition.matchIfMissing());
        assertArdComponentsDisabled(Collections.emptyMap());
    }
    
    @Test
    void shouldBeDisabledWhenExplicitlyConfiguredFalse() {
        assertArdComponentsDisabled(Collections.singletonMap(Constants.ARD_ENABLED_KEY, "false"));
    }
    
    @Test
    void shouldRegisterArdComponentWhenExplicitlyEnabled() {
        try (AnnotationConfigApplicationContext context = newContext(
            Collections.singletonMap(Constants.ARD_ENABLED_KEY, "true"))) {
            context.register(HashingArdEmbeddingService.class);
            context.refresh();
            
            assertNotNull(context.getBean(HashingArdEmbeddingService.class));
        }
    }
    
    private void assertArdComponentsDisabled(Map<String, Object> properties) {
        try (AnnotationConfigApplicationContext context = newContext(properties)) {
            context.register(ARD_COMPONENTS);
            context.refresh();
            
            for (Class<?> component : ARD_COMPONENTS) {
                assertTrue(context.getBeansOfType(component).isEmpty(),
                    () -> component.getSimpleName() + " should not be registered");
            }
        }
    }
    
    private AnnotationConfigApplicationContext newContext(Map<String, Object> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (!properties.isEmpty()) {
            context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("ard-test", properties));
        }
        return context;
    }
}
