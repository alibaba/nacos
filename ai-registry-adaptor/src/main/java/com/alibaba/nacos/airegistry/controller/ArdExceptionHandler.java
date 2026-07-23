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

package com.alibaba.nacos.airegistry.controller;

import com.alibaba.nacos.airegistry.annotation.ArdApi;
import com.alibaba.nacos.airegistry.model.ard.ArdErrorResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Exception handler for the external ARD protocol.
 *
 * @author nacos
 */
@Order(-2)
@ControllerAdvice(annotations = ArdApi.class)
@ResponseBody
public class ArdExceptionHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(ArdExceptionHandler.class);
    
    /**
     * Handle an ARD request validation failure.
     */
    @ExceptionHandler(NacosApiException.class)
    public ResponseEntity<ArdErrorResponse> handleNacosApiException(NacosApiException exception) {
        HttpStatus status = resolveStatus(exception.getErrCode());
        LOGGER.warn("ARD request failed: {}", exception.getErrMsg());
        return response(status, exception.getErrMsg());
    }
    
    /**
     * Handle a Nacos service failure exposed through ARD.
     */
    @ExceptionHandler(NacosException.class)
    public ResponseEntity<ArdErrorResponse> handleNacosException(NacosException exception) {
        HttpStatus status = resolveStatus(exception.getErrCode());
        LOGGER.warn("ARD operation failed: {}", exception.getErrMsg());
        return response(status, exception.getErrMsg());
    }
    
    /**
     * Handle malformed ARD request bodies and parameters.
     */
    @ExceptionHandler({
        HttpMessageConversionException.class, MissingServletRequestParameterException.class,
        IllegalArgumentException.class
    })
    public ResponseEntity<ArdErrorResponse> handleInvalidArgument(Exception exception) {
        LOGGER.warn("Invalid ARD request: {}", exception.getMessage());
        return response(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
    
    /**
     * Handle access failures.
     */
    @ExceptionHandler(AccessException.class)
    public ResponseEntity<ArdErrorResponse> handleAccessException(AccessException exception) {
        LOGGER.warn("ARD access denied: {}", exception.getErrMsg());
        return response(HttpStatus.FORBIDDEN, exception.getErrMsg());
    }
    
    /**
     * Handle unexpected ARD failures.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ArdErrorResponse> handleUnexpectedException(Exception exception) {
        LOGGER.error("Unexpected ARD operation failure", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }
    
    private ResponseEntity<ArdErrorResponse> response(HttpStatus status, String message) {
        String responseMessage = message == null || message.isBlank()
            ? status.getReasonPhrase() : message;
        return ResponseEntity.status(status)
            .body(new ArdErrorResponse(errorCode(status), responseMessage));
    }
    
    private HttpStatus resolveStatus(int statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode);
        return status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
    }
    
    private String errorCode(HttpStatus status) {
        switch (status) {
            case BAD_REQUEST:
                return "INVALID_ARGUMENT";
            case UNAUTHORIZED:
                return "UNAUTHENTICATED";
            case FORBIDDEN:
                return "PERMISSION_DENIED";
            case NOT_FOUND:
                return "NOT_FOUND";
            case TOO_MANY_REQUESTS:
                return "RATE_LIMIT_EXCEEDED";
            default:
                return "INTERNAL_ERROR";
        }
    }
}
