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

package com.alibaba.nacos.ai.param;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpHttpParamExtractorTest {
    
    @Mock
    HttpServletRequest request;
    
    McpHttpParamExtractor httpParamExtractor;
    
    @BeforeEach
    void setUp() {
        httpParamExtractor = new McpHttpParamExtractor();
    }
    
    @Test
    void extractParam() throws NacosException {
        String id = UUID.randomUUID().toString();
        when(request.getParameter("namespaceId")).thenReturn("testNs");
        when(request.getParameter("mcpName")).thenReturn("testMcp");
        when(request.getParameter("mcpId")).thenReturn(id);
        List<ParamInfo> actual = httpParamExtractor.extractParam(request);
        assertEquals(1, actual.size());
        assertEquals(id, actual.get(0).getMcpId());
        assertEquals("testNs", actual.get(0).getNamespaceId());
        assertEquals("testMcp", actual.get(0).getMcpName());
    }
}