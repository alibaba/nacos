/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.console.controller.v3.ai;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ControllerAdvice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * {@link CopilotSseExceptionHandler} unit test.
 */
class CopilotSseExceptionHandlerTest {
    
    @Test
    void testControllerAdviceShouldOnlyApplyToCopilotController() {
        ControllerAdvice advice = CopilotSseExceptionHandler.class.getAnnotation(ControllerAdvice.class);
        assertArrayEquals(new Class[] {ConsoleCopilotController.class}, advice.assignableTypes());
    }
}
