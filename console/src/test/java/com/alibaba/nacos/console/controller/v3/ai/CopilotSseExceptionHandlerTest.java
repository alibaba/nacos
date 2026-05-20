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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.console.controller.v3.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CopilotSseExceptionHandlerTest {
    
    private CopilotSseExceptionHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new CopilotSseExceptionHandler();
    }
    
    @Test
    void testControllerAdviceShouldOnlyApplyToCopilotController() {
        ControllerAdvice advice =
            CopilotSseExceptionHandler.class.getAnnotation(ControllerAdvice.class);
        assertArrayEquals(new Class[] {ConsoleCopilotController.class}, advice.assignableTypes());
    }
    
    @Test
    void testHandleExceptionReturnsSseEmitterForSseAcceptHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);
        request.setRequestURI("/v3/console/ai/copilot/skill/optimize");
        
        Object result = handler.handleException(new RuntimeException("test error"), request);
        
        assertInstanceOf(SseEmitter.class, result);
    }
    
    @Test
    void testHandleExceptionReturnsSseEmitterForSkillOptimizePath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v3/console/ai/copilot/skill/optimize");
        
        Object result = handler.handleException(new RuntimeException("optimize error"), request);
        
        assertInstanceOf(SseEmitter.class, result);
    }
    
    @Test
    void testHandleExceptionReturnsSseEmitterForSkillGeneratePath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v3/console/ai/copilot/skill/generate");
        
        Object result = handler.handleException(new RuntimeException("generate error"), request);
        
        assertInstanceOf(SseEmitter.class, result);
    }
    
    @Test
    void testHandleExceptionRethrowsRuntimeExceptionForNonSseRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v3/console/ai/copilot/config");
        
        RuntimeException ex = new RuntimeException("not sse");
        assertThrows(RuntimeException.class, () -> handler.handleException(ex, request));
    }
    
    @Test
    void testHandleExceptionWrapsCheckedExceptionForNonSseRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v3/console/ai/copilot/config");
        
        Exception ex = new Exception("checked");
        RuntimeException thrown =
            assertThrows(RuntimeException.class, () -> handler.handleException(ex, request));
        assertInstanceOf(Exception.class, thrown.getCause());
    }
    
    @Test
    void testHandleExceptionWithNullMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);
        request.setRequestURI("/v3/console/ai/copilot/skill/optimize");
        
        Object result = handler.handleException(new RuntimeException((String) null), request);
        
        assertInstanceOf(SseEmitter.class, result);
    }
    
    @Test
    void testHandleExceptionWithEmptyMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", MediaType.TEXT_EVENT_STREAM_VALUE);
        request.setRequestURI("/v3/console/ai/copilot/skill/optimize");
        
        Object result = handler.handleException(new RuntimeException(""), request);
        
        assertInstanceOf(SseEmitter.class, result);
    }
}
