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

package com.alibaba.nacos.console.handler.ai;

import com.alibaba.nacos.ai.config.AiEnabledFilter;
import com.alibaba.nacos.console.handler.impl.ConditionFunctionEnabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EnabledAiHandler} unit tests.
 *
 * @author lengleng
 */
class EnabledAiHandlerTest {
    
    /**
     * The AI handler should only be enabled when both AI function mode and AI switch are enabled.
     */
    @Test
    void shouldContainAiFunctionAndPropertyConditions() {
        Conditional conditional = EnabledAiHandler.class.getAnnotation(Conditional.class);
        assertArrayEquals(new Class[] {ConditionFunctionEnabled.ConditionAiEnabled.class},
            conditional.value());
        
        ConditionalOnProperty conditionalOnProperty =
            EnabledAiHandler.class.getAnnotation(ConditionalOnProperty.class);
        assertArrayEquals(new String[] {AiEnabledFilter.AI_ENABLED_KEY},
            conditionalOnProperty.value());
        assertEquals("true", conditionalOnProperty.havingValue());
        assertTrue(conditionalOnProperty.matchIfMissing());
    }
}
