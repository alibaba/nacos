/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.exception;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {
    
    private GlobalExceptionHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }
    
    @Test
    void testHandleIllegalArgumentException() throws Exception {
        IllegalArgumentException ex = new IllegalArgumentException("bad arg");
        ResponseEntity<String> response = handler.handleIllegalArgumentException(ex);
        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().contains("bad arg"));
    }
    
    @Test
    void testHandleNacosRuntimeException() throws Exception {
        NacosRuntimeException ex = new NacosRuntimeException(503, "service down");
        ResponseEntity<String> response = handler.handleNacosRunTimeException(ex);
        assertEquals(503, response.getStatusCode().value());
        assertTrue(response.getBody().contains("service down"));
    }
    
    @Test
    void testHandleNacosException() throws Exception {
        NacosException ex = new NacosException(500, "internal error");
        ResponseEntity<String> response = handler.handleNacosException(ex);
        assertEquals(500, response.getStatusCode().value());
        assertTrue(response.getBody().contains("internal error"));
    }
    
    @Test
    void testHandleDataAccessException() {
        DataAccessResourceFailureException ex =
            new DataAccessResourceFailureException("db error");
        ResponseEntity<String> response = handler.handleDataAccessException(ex);
        assertEquals(500, response.getStatusCode().value());
        assertTrue(response.getBody().contains("db error"));
    }
}
