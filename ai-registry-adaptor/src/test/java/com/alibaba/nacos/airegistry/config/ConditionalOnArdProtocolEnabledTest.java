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

package com.alibaba.nacos.airegistry.config;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.airegistry.controller.ArdSearchController;
import com.alibaba.nacos.airegistry.controller.ArdWellKnownController;
import com.alibaba.nacos.airegistry.service.ard.ArdArtifactService;
import com.alibaba.nacos.airegistry.service.ard.ArdSearchServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests ARD protocol components against the global ARD switch.
 *
 * @author nacos
 */
class ConditionalOnArdProtocolEnabledTest {
    
    private static final Class<?>[] ARD_PROTOCOL_COMPONENTS = {ArdSearchController.class,
        ArdWellKnownController.class, ArdArtifactService.class, ArdSearchServiceImpl.class};
    
    @Test
    void shouldBeDisabledByDefault() {
        assertArdProtocolComponentsDisabled(Collections.emptyMap());
    }
    
    @Test
    void shouldBeDisabledWhenExplicitlyConfiguredFalse() {
        assertArdProtocolComponentsDisabled(
            Collections.singletonMap(Constants.ARD_ENABLED_KEY, "false"));
    }
    
    private void assertArdProtocolComponentsDisabled(Map<String, Object> properties) {
        try (AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext()) {
            if (!properties.isEmpty()) {
                context.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("ard-protocol-test", properties));
            }
            context.register(ARD_PROTOCOL_COMPONENTS);
            context.refresh();
            
            for (Class<?> component : ARD_PROTOCOL_COMPONENTS) {
                assertTrue(context.getBeansOfType(component).isEmpty(),
                    () -> component.getSimpleName() + " should not be registered");
            }
        }
    }
}
