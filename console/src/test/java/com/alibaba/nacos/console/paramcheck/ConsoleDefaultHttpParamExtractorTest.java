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

package com.alibaba.nacos.console.paramcheck;

import com.alibaba.nacos.common.paramcheck.ParamInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsoleDefaultHttpParamExtractorTest {
    
    @Mock
    HttpServletRequest mockRequest;
    
    ConsoleDefaultHttpParamExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new ConsoleDefaultHttpParamExtractor();
    }
    
    @Test
    void extractParamWithNamespaceId() {
        when(mockRequest.getParameter("namespaceId")).thenReturn("test");
        List<ParamInfo> actual = extractor.extractParam(mockRequest);
        assertEquals("test", actual.get(0).getNamespaceId());
        assertNull(actual.get(0).getNamespaceShowName());
    }
    
    @Test
    void extractParamWithCustomNamespaceId() {
        when(mockRequest.getParameter("namespaceId")).thenReturn(null);
        when(mockRequest.getParameter("customNamespaceId")).thenReturn("test1");
        List<ParamInfo> actual = extractor.extractParam(mockRequest);
        assertEquals("test1", actual.get(0).getNamespaceId());
        assertNull(actual.get(0).getNamespaceShowName());
    }
    
    @Test
    void extractParamWithNamespaceName() {
        when(mockRequest.getParameter("namespaceId")).thenReturn(null);
        when(mockRequest.getParameter("customNamespaceId")).thenReturn(null);
        when(mockRequest.getParameter("namespaceName")).thenReturn("testName");
        List<ParamInfo> actual = extractor.extractParam(mockRequest);
        assertEquals("testName", actual.get(0).getNamespaceShowName());
        assertNull(actual.get(0).getNamespaceId());
    }
    
    @Test
    void extractParamWithFullNamespace() {
        when(mockRequest.getParameter("namespaceId")).thenReturn("test");
        when(mockRequest.getParameter("namespaceName")).thenReturn("testName");
        List<ParamInfo> actual = extractor.extractParam(mockRequest);
        assertEquals("test", actual.get(0).getNamespaceId());
        assertEquals("testName", actual.get(0).getNamespaceShowName());
    }
}