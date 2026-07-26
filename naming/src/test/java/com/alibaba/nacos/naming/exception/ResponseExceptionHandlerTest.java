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

package com.alibaba.nacos.naming.exception;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseExceptionHandlerTest {
    
    private final ResponseExceptionHandler handler = new ResponseExceptionHandler();
    
    @Test
    void testHandleNacosException() {
        ResponseEntity<String> response =
            handler.handleNacosException(new NacosException(403, "no permission"));
        
        assertEquals(403, response.getStatusCode().value());
        assertEquals("no permission", response.getBody());
    }
    
    @Test
    void testHandleNacosRuntimeException() {
        ResponseEntity<String> response =
            handler.handleNacosRuntimeException(new NacosRuntimeException(500, "failed"));
        
        assertEquals(500, response.getStatusCode().value());
        assertTrue(response.getBody().contains("failed"));
    }
    
    @Test
    void testHandleParameterError() {
        ResponseEntity<String> response =
            handler.handleParameterError(new IllegalArgumentException("bad argument"));
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("bad argument", response.getBody());
    }
    
    @Test
    void testHandleMissingParams() {
        MissingServletRequestParameterException exception =
            new MissingServletRequestParameterException("namespaceId", "String");
        
        ResponseEntity<String> response = handler.handleMissingParams(exception);
        
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Parameter 'namespaceId' is missing", response.getBody());
    }
    
    @Test
    void testHandleException() {
        ResponseEntity<String> response = handler.handleException(new Exception("unexpected"));
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(response.getBody().contains("unexpected"));
    }
}
