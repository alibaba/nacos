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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigDefaultHttpParamExtractorTest {
    
    @Mock
    private HttpServletRequest request;
    
    private ConfigDefaultHttpParamExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new ConfigDefaultHttpParamExtractor();
    }
    
    @Test
    void testExtractParamWithNamespaceId() {
        when(request.getParameter("namespaceId")).thenReturn("ns1");
        when(request.getParameter("dataId")).thenReturn("d1");
        when(request.getParameter("groupName")).thenReturn("g1");
        when(request.getParameter("ip")).thenReturn("1.1.1.1");
        
        List<ParamInfo> result = extractor.extractParam(request);
        assertEquals(1, result.size());
        assertEquals("ns1", result.get(0).getNamespaceId());
        assertEquals("d1", result.get(0).getDataId());
        assertEquals("g1", result.get(0).getGroup());
        assertEquals("1.1.1.1", result.get(0).getIp());
    }
    
    @Test
    void testExtractParamFallbackToTenant() {
        when(request.getParameter("namespaceId")).thenReturn(null);
        when(request.getParameter("tenant")).thenReturn("t1");
        when(request.getParameter("dataId")).thenReturn("d1");
        when(request.getParameter("groupName")).thenReturn(null);
        when(request.getParameter("group")).thenReturn("g2");
        when(request.getParameter("ip")).thenReturn(null);
        
        List<ParamInfo> result = extractor.extractParam(request);
        assertEquals(1, result.size());
        assertEquals("t1", result.get(0).getNamespaceId());
        assertEquals("g2", result.get(0).getGroup());
        assertNull(result.get(0).getIp());
    }
    
    @Test
    void testExtractParamFallbackToNamespace() {
        when(request.getParameter("namespaceId")).thenReturn("");
        when(request.getParameter("tenant")).thenReturn("");
        when(request.getParameter("namespace")).thenReturn("ns3");
        when(request.getParameter("dataId")).thenReturn(null);
        when(request.getParameter("groupName")).thenReturn("");
        when(request.getParameter("group")).thenReturn(null);
        when(request.getParameter("ip")).thenReturn(null);
        
        List<ParamInfo> result = extractor.extractParam(request);
        assertEquals(1, result.size());
        assertEquals("ns3", result.get(0).getNamespaceId());
        assertNull(result.get(0).getDataId());
        assertNull(result.get(0).getGroup());
    }
    
    @Test
    void testExtractParamAllNull() {
        when(request.getParameter("namespaceId")).thenReturn(null);
        when(request.getParameter("tenant")).thenReturn(null);
        when(request.getParameter("namespace")).thenReturn(null);
        when(request.getParameter("dataId")).thenReturn(null);
        when(request.getParameter("groupName")).thenReturn(null);
        when(request.getParameter("group")).thenReturn(null);
        when(request.getParameter("ip")).thenReturn(null);
        
        List<ParamInfo> result = extractor.extractParam(request);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertNull(result.get(0).getNamespaceId());
    }
}
