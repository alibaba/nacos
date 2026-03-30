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

package com.alibaba.nacos.core.model.request;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogUpdateRequestTest {
    
    @Test
    void testGettersAndSetters() {
        LogUpdateRequest request = new LogUpdateRequest();
        request.setLogName("test");
        request.setLogLevel("info");
        
        assertEquals("test", request.getLogName());
        assertEquals("info", request.getLogLevel());
    }
    
    @Test
    void validateShouldThrowWhenLogNameMissing() {
        LogUpdateRequest request = new LogUpdateRequest();
        request.setLogLevel("info");
        NacosApiException exception = assertThrows(NacosApiException.class, request::validate);
        assertTrue(exception.getErrMsg().contains("Log name is required"));
    }
    
    @Test
    void validateShouldThrowWhenLogLevelMissing() {
        LogUpdateRequest request = new LogUpdateRequest();
        request.setLogName("test");
        NacosApiException exception = assertThrows(NacosApiException.class, request::validate);
        assertTrue(exception.getErrMsg().contains("Log level is required"));
    }
    
    @Test
    void validateShouldThrowWhenBothMissing() {
        LogUpdateRequest request = new LogUpdateRequest();
        NacosApiException exception = assertThrows(NacosApiException.class, request::validate);
        assertTrue(exception.getErrMsg().contains("Log name is required"));
    }
    
    @Test
    void validateShouldNotThrowWhenBothPresent() throws NacosApiException {
        LogUpdateRequest request = new LogUpdateRequest();
        request.setLogName("test");
        request.setLogLevel("info");
        assertDoesNotThrow(request::validate);
    }
    
    @Test
    void validateShouldThrowWhenLogNameBlank() {
        LogUpdateRequest request = new LogUpdateRequest();
        request.setLogName("");
        request.setLogLevel("info");
        NacosApiException exception = assertThrows(NacosApiException.class, request::validate);
        assertTrue(exception.getErrMsg().contains("Log name is required"));
    }
    
    @Test
    void validateShouldThrowWhenLogLevelBlank() {
        LogUpdateRequest request = new LogUpdateRequest();
        request.setLogName("test");
        request.setLogLevel("");
        NacosApiException exception = assertThrows(NacosApiException.class, request::validate);
        assertTrue(exception.getErrMsg().contains("Log level is required"));
    }
}
