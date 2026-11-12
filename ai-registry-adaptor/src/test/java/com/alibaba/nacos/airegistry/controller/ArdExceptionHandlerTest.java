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
import com.alibaba.nacos.airegistry.model.ard.ArdListResponse;
import com.alibaba.nacos.airegistry.service.ard.ArdArtifactService;
import com.alibaba.nacos.airegistry.service.ard.ArdSearchService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.auth.annotation.ProtocolAuthError;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Tests for {@link ArdExceptionHandler}.
 *
 * @author nacos
 */
class ArdExceptionHandlerTest {
    
    @ParameterizedTest
    @ValueSource(strings = {"10.5", "true", "abc", "2147483648", "-2147483649"})
    void shouldRejectInvalidPageSizeBeforeCallingService(String pageSize) throws Exception {
        ArdSearchService service = mock(ArdSearchService.class);
        
        MockHttpServletResponse response = mvc(service)
            .perform(get("/v3/ai/ard/agents").param("pageSize", pageSize))
            .andReturn().getResponse();
        
        assertEquals(400, response.getStatus());
        JsonNode body = JacksonUtils.toObj(response.getContentAsString());
        assertEquals(2, body.size());
        assertEquals("INVALID_ARGUMENT", body.path("errorCode").asText());
        assertTrue(body.path("message").asText().contains("pageSize"));
        verifyNoInteractions(service);
    }
    
    @Test
    void shouldKeepValidPageSizeBinding() throws Exception {
        ArdSearchService service = mock(ArdSearchService.class);
        when(service.list(null, null, null, 10, null)).thenReturn(new ArdListResponse());
        
        MockHttpServletResponse response = mvc(service)
            .perform(get("/v3/ai/ard/agents").param("pageSize", "10"))
            .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        verify(service).list(null, null, null, 10, null);
    }
    
    @Test
    void shouldKeepUnexpectedServiceFailureAsInternalError() throws Exception {
        ArdSearchService service = mock(ArdSearchService.class);
        when(service.list(null, null, null, 10, null))
            .thenThrow(new IllegalStateException("internal detail"));
        
        MockHttpServletResponse response = mvc(service)
            .perform(get("/v3/ai/ard/agents").param("pageSize", "10"))
            .andReturn().getResponse();
        
        assertEquals(500, response.getStatus());
        JsonNode body = JacksonUtils.toObj(response.getContentAsString());
        assertEquals(2, body.size());
        assertEquals("INTERNAL_ERROR", body.path("errorCode").asText());
        assertEquals("Internal server error", body.path("message").asText());
    }
    
    private MockMvc mvc(ArdSearchService service) {
        return MockMvcBuilders
            .standaloneSetup(new ArdSearchController(service, mock(ArdArtifactService.class)))
            .setControllerAdvice(new ArdExceptionHandler()).build();
    }
    
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
    
    @ParameterizedTest
    @CsvSource({"403, 403, PERMISSION_DENIED", "404, 404, NOT_FOUND",
        "599, 500, INTERNAL_ERROR"})
    void shouldMapServiceErrorStatus(int serviceStatus, int httpStatus, String errorCode) {
        ArdExceptionHandler handler = new ArdExceptionHandler();
        
        ResponseEntity<ArdErrorResponse> response = handler.handleNacosException(
            new NacosException(serviceStatus, "operation rejected"));
        
        assertEquals(httpStatus, response.getStatusCode().value());
        assertEquals(errorCode, response.getBody().getErrorCode());
        assertEquals("operation rejected", response.getBody().getMessage());
    }
    
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void shouldUseReasonPhraseForMissingValidationMessage(String message) {
        ArdExceptionHandler handler = new ArdExceptionHandler();
        
        ResponseEntity<ArdErrorResponse> response =
            handler.handleInvalidArgument(new IllegalArgumentException(message));
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("INVALID_ARGUMENT", response.getBody().getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST.getReasonPhrase(), response.getBody().getMessage());
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
