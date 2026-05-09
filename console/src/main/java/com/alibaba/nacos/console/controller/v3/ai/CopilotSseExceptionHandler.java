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

import com.alibaba.nacos.copilot.model.SkillOptimizationResponse;
import com.alibaba.nacos.common.utils.JacksonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Exception handler for Copilot SSE endpoints.
 * This handler has higher priority than NacosApiExceptionHandler to ensure
 * all exceptions are returned as SSE events.
 *
 * @author nacos
 */
@Order(-2)
@ControllerAdvice(assignableTypes = ConsoleCopilotController.class)
public class CopilotSseExceptionHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CopilotSseExceptionHandler.class);
    
    /**
     * Handle all exceptions for SSE endpoints.
     * This ensures exceptions are returned as SSE events instead of Result objects.
     * Only handles SSE endpoints (requests that accept text/event-stream or have SSE path).
     *
     * @param e exception
     * @param request HTTP request
     * @return SSE emitter with error event, or rethrow exception for non-SSE requests
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Object handleException(Exception e, HttpServletRequest request) {
        // Only handle SSE requests - check Accept header or request path
        String acceptHeader = request.getHeader("Accept");
        String requestPath = request.getRequestURI();
        
        // Check if this is an SSE endpoint (optimize or generate endpoint) or accepts SSE
        boolean isSseRequest =
            (acceptHeader != null && acceptHeader.contains(MediaType.TEXT_EVENT_STREAM_VALUE))
                || (requestPath != null && (requestPath.contains("/skill/optimize")
                    || requestPath.contains("/skill/generate")));
        
        if (!isSseRequest) {
            // Not an SSE request, rethrow to let other exception handlers process it
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
        
        LOGGER.error("Exception in Copilot SSE endpoint", e);
        SseEmitter emitter = new SseEmitter(1000L);
        try {
            SkillOptimizationResponse errorResponse = new SkillOptimizationResponse();
            errorResponse.setDone(true);
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.isEmpty()) {
                errorMsg = e.getClass().getSimpleName();
            }
            errorResponse.setExplanation("请求处理失败：" + errorMsg);
            emitter.send(SseEmitter.event()
                .data(JacksonUtils.toJson(errorResponse))
                .name("error"));
            emitter.complete();
        } catch (IOException ioException) {
            LOGGER.error("Failed to send exception SSE event", ioException);
            emitter.complete();
        }
        return emitter;
    }
}
