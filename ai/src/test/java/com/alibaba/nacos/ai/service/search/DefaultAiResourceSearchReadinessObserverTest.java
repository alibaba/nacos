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

package com.alibaba.nacos.ai.service.search;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAiResourceSearchReadinessObserverTest {
    
    @Test
    void shouldCacheReadinessAndRateLimitIncompleteSnapshotWarnings() {
        AiResourceSearchTypeHandlerRegistry registry =
            mock(AiResourceSearchTypeHandlerRegistry.class);
        AiResourceSearchTypeHandler handler = mock(AiResourceSearchTypeHandler.class);
        AiResourceSearchReadinessService readiness =
            mock(AiResourceSearchReadinessService.class);
        Clock clock = mock(Clock.class);
        Logger logger = mock(Logger.class);
        when(registry.resourceTypes()).thenReturn(List.of("skill", "prompt"));
        when(registry.get("skill")).thenReturn(handler);
        when(registry.get("prompt")).thenReturn(handler);
        when(handler.projectionVersion()).thenReturn(1);
        when(readiness.isReady("skill", 1)).thenReturn(false, false, false, true);
        when(readiness.isReady("prompt", 1)).thenReturn(true);
        when(clock.millis()).thenReturn(1000L, 2000L, 7000L, 61000L, 67000L, 70000L);
        DefaultAiResourceSearchReadinessObserver observer =
            new DefaultAiResourceSearchReadinessObserver(registry, readiness, clock, logger);
        
        observer.observe(Collections.emptyList());
        observer.observe(Collections.emptyList());
        observer.observe(Collections.emptyList());
        observer.observe(Collections.emptyList());
        observer.observe(Collections.emptyList());
        observer.observe(Collections.emptyList());
        
        verify(readiness, times(4)).isReady("skill", 1);
        verify(readiness).isReady("prompt", 1);
        verify(logger, times(2)).warn(anyString(), any(Object.class));
    }
    
    @Test
    void shouldIgnoreUnsupportedTypesAndTreatReadinessFailureAsNotReady() {
        AiResourceSearchTypeHandlerRegistry registry =
            mock(AiResourceSearchTypeHandlerRegistry.class);
        AiResourceSearchTypeHandler searchable = mock(AiResourceSearchTypeHandler.class);
        AiResourceSearchTypeHandler disabled = mock(AiResourceSearchTypeHandler.class);
        AiResourceSearchReadinessService readiness =
            mock(AiResourceSearchReadinessService.class);
        Clock clock = mock(Clock.class);
        Logger logger = mock(Logger.class);
        when(registry.get("skill")).thenReturn(searchable);
        when(registry.get("disabled")).thenReturn(disabled);
        when(searchable.projectionVersion()).thenReturn(1);
        when(disabled.projectionVersion()).thenReturn(0);
        when(readiness.isReady("skill", 1)).thenThrow(new IllegalStateException("unavailable"));
        when(clock.millis()).thenReturn(1000L);
        DefaultAiResourceSearchReadinessObserver observer =
            new DefaultAiResourceSearchReadinessObserver(registry, readiness, clock, logger);
        
        observer.observe(Arrays.asList(null, "", "skill", "skill", "disabled", "unknown"));
        
        verify(readiness).isReady("skill", 1);
        verify(readiness, never()).isReady("disabled", 0);
        verify(logger).warn(anyString(), any(Object.class));
    }
    
    @Test
    void shouldSupportProductionConstructorAndNoopObserver() {
        AiResourceSearchTypeHandlerRegistry registry =
            mock(AiResourceSearchTypeHandlerRegistry.class);
        when(registry.resourceTypes()).thenReturn(Collections.emptyList());
        new DefaultAiResourceSearchReadinessObserver(registry,
            AiResourceSearchReadinessService.NOOP).observe(null);
        AiResourceSearchReadinessObserver.NOOP.observe(null);
    }
    
    @Test
    void shouldCreateObserverThroughSpringConstructorInjection() {
        AiResourceSearchTypeHandlerRegistry registry =
            mock(AiResourceSearchTypeHandlerRegistry.class);
        AiResourceSearchReadinessService readiness =
            mock(AiResourceSearchReadinessService.class);
        try (AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext()) {
            context.registerBean(AiResourceSearchTypeHandlerRegistry.class, () -> registry);
            context.registerBean(AiResourceSearchReadinessService.class, () -> readiness);
            context.register(DefaultAiResourceSearchReadinessObserver.class);
            context.refresh();
            
            assertNotNull(context.getBean(DefaultAiResourceSearchReadinessObserver.class));
        }
    }
}
