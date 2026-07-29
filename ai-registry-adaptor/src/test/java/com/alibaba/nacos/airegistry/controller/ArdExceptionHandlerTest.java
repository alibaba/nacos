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

import com.alibaba.nacos.airegistry.model.ard.ArdErrorResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.auth.annotation.ProtocolAuthError;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for {@link ArdExceptionHandler}.
 *
 * @author nacos
 */
class ArdExceptionHandlerTest {
    
    @Test
    void shouldReturnArdErrorWithoutNacosEnvelope() {
        ArdExceptionHandler handler = new ArdExceptionHandler();
        NacosApiException exception = new NacosApiException(NacosException.INVALID_PARAM,
            ErrorCode.PARAMETER_VALIDATE_ERROR, "invalid filter");
        
        ResponseEntity<ArdErrorResponse> response =
            handler.handleNacosApiException(exception);
        
        assertEquals(400, response.getStatusCode().value());
        JsonNode body = JacksonUtils
            .toObj(JacksonUtils.toJson(response.getBody()), JsonNode.class);
        assertEquals(2, body.size());
        assertEquals("INVALID_ARGUMENT", body.get("errorCode").asText());
        assertEquals("invalid filter", body.get("message").asText());
        assertFalse(body.has("code"));
        assertFalse(body.has("data"));
    }
    
    @Test
    void shouldUsePinnedArdErrorCodeAndNonNullMessage() {
        ArdExceptionHandler handler = new ArdExceptionHandler();
        
        ResponseEntity<ArdErrorResponse> response =
            handler.handleNacosException(new NacosException(
                HttpStatus.TOO_MANY_REQUESTS.value(), (String) null));
        
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("RATE_LIMIT_EXCEEDED", response.getBody().getErrorCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
            response.getBody().getMessage());
    }
    
    @Test
    void shouldMapAccessFailureToPinnedUnauthorizedResponse() {
        ArdExceptionHandler handler = new ArdExceptionHandler();
        
        ResponseEntity<ArdErrorResponse> response =
            handler.handleAccessException(new AccessException("invalid token"));
        
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("UNAUTHENTICATED", response.getBody().getErrorCode());
        assertEquals("invalid token", response.getBody().getMessage());
    }
    
    @Test
    void ardControllerShouldExposeProtocolAuthErrorMetadata() {
        ProtocolAuthError error = AnnotatedElementUtils.findMergedAnnotation(
            ArdSearchController.class, ProtocolAuthError.class);
        
        assertNotNull(error);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), error.status());
        assertEquals("UNAUTHENTICATED", error.errorCode());
    }
}
