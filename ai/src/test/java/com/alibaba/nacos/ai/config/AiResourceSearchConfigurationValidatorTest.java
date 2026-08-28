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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AiResourceSearchConfigurationValidator}.
 *
 * @author nacos
 */
class AiResourceSearchConfigurationValidatorTest {
    
    @Test
    void shouldAllowIndependentSearchCoreConfigurations() {
        assertDoesNotThrow(() -> refresh(Map.of()));
        assertDoesNotThrow(() -> refresh(Map.of(
            Constants.ARD_ENABLED_KEY, "false",
            Constants.AI_RESOURCE_SEARCH_ENABLED_KEY, "false")));
        assertDoesNotThrow(() -> refresh(Map.of(
            Constants.ARD_ENABLED_KEY, "true",
            Constants.AI_RESOURCE_SEARCH_ENABLED_KEY, "true")));
    }
    
    @Test
    void shouldRejectArdWithoutSearchCore() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(Constants.ARD_ENABLED_KEY, "true");
        properties.put(Constants.AI_RESOURCE_SEARCH_ENABLED_KEY, "false");
        
        BeanCreationException exception = assertThrows(BeanCreationException.class,
            () -> refresh(properties));
        String message = exception.getMostSpecificCause().getMessage();
        assertTrue(message.contains(Constants.ARD_ENABLED_KEY));
        assertTrue(message.contains(Constants.AI_RESOURCE_SEARCH_ENABLED_KEY));
    }
    
    @Test
    void shouldValidateRadSearchMode() {
        assertDoesNotThrow(() -> refresh(Map.of(
            Constants.Agent.RAD_SEARCH_MODE_CONFIG_KEY, "auto")));
        assertDoesNotThrow(() -> refresh(Map.of(
            Constants.Agent.RAD_SEARCH_MODE_CONFIG_KEY, " INDEX ")));
        assertDoesNotThrow(() -> refresh(Map.of(
            Constants.Agent.RAD_SEARCH_MODE_CONFIG_KEY, "SCAN")));
        
        BeanCreationException exception = assertThrows(BeanCreationException.class,
            () -> refresh(Map.of(Constants.Agent.RAD_SEARCH_MODE_CONFIG_KEY, "invalid")));
        assertTrue(exception.getMostSpecificCause().getMessage()
            .contains(Constants.Agent.RAD_SEARCH_MODE_CONFIG_KEY));
    }
    
    private void refresh(Map<String, Object> properties) {
        try (AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("search-configuration-test", properties));
            context.register(AiResourceSearchConfigurationValidator.class);
            context.refresh();
        }
    }
}
