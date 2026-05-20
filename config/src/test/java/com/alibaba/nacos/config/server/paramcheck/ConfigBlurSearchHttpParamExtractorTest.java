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

package com.alibaba.nacos.config.server.paramcheck;

import com.alibaba.nacos.common.paramcheck.ParamInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigBlurSearchHttpParamExtractorTest {
    
    @Mock
    private HttpServletRequest request;
    
    private ConfigBlurSearchHttpParamExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new ConfigBlurSearchHttpParamExtractor();
    }
    
    @Test
    void testExtractParamBlurMode() {
        when(request.getParameter("search")).thenReturn("blur");
        List<ParamInfo> result = extractor.extractParam(request);
        assertTrue(result.isEmpty());
    }
    
    @Test
    void testExtractParamNonBlurMode() {
        when(request.getParameter("search")).thenReturn("accurate");
        when(request.getParameter("tenant")).thenReturn("t1");
        when(request.getParameter("dataId")).thenReturn("d1");
        when(request.getParameter("group")).thenReturn("g1");
        
        List<ParamInfo> result = extractor.extractParam(request);
        assertEquals(1, result.size());
        assertEquals("t1", result.get(0).getNamespaceId());
        assertEquals("d1", result.get(0).getDataId());
        assertEquals("g1", result.get(0).getGroup());
    }
    
    @Test
    void testExtractParamNullSearchMode() {
        when(request.getParameter("search")).thenReturn(null);
        when(request.getParameter("tenant")).thenReturn(null);
        when(request.getParameter("dataId")).thenReturn(null);
        when(request.getParameter("group")).thenReturn(null);
        
        List<ParamInfo> result = extractor.extractParam(request);
        assertEquals(1, result.size());
    }
}
