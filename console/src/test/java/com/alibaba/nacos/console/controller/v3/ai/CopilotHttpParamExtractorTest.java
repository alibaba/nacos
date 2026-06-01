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

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.common.paramcheck.ParamInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CopilotHttpParamExtractorTest {
    
    private CopilotHttpParamExtractor extractor;
    
    @BeforeEach
    void setUp() {
        extractor = new CopilotHttpParamExtractor();
    }
    
    @Test
    void testExtractParamFromPostBodyWithSkill() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContent(
            "{\"skill\":{\"name\":\"my-skill\",\"namespaceId\":\"ns1\"},\"optimizationGoal\":\"improve\"}"
                .getBytes());
        
        List<ParamInfo> result = extractor.extractParam(request);
        
        assertEquals(1, result.size());
        assertEquals("my-skill", result.get(0).getAgentName());
        assertEquals("ns1", result.get(0).getNamespaceId());
    }
    
    @Test
    void testExtractParamFromPostBodyWithoutSkill() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContent("{\"backgroundInfo\":\"some info\"}".getBytes());
        request.setParameter("skillName", "fallback-skill");
        request.setParameter("namespaceId", "ns2");
        
        List<ParamInfo> result = extractor.extractParam(request);
        
        assertEquals(1, result.size());
        assertEquals("fallback-skill", result.get(0).getAgentName());
        assertEquals("ns2", result.get(0).getNamespaceId());
    }
    
    @Test
    void testExtractParamFromGetRequestFallsBackToQueryParams() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("GET");
        request.setParameter("skillName", "query-skill");
        request.setParameter("namespaceId", "ns3");
        
        List<ParamInfo> result = extractor.extractParam(request);
        
        assertEquals(1, result.size());
        assertEquals("query-skill", result.get(0).getAgentName());
        assertEquals("ns3", result.get(0).getNamespaceId());
    }
    
    @Test
    void testExtractParamWithEmptyPostBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContent("".getBytes());
        
        List<ParamInfo> result = extractor.extractParam(request);
        
        assertEquals(1, result.size());
        assertNull(result.get(0).getAgentName());
    }
    
    @Test
    void testExtractParamWithMalformedJson() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContent("{\"skill\": not-valid-json}".getBytes());
        request.setParameter("skillName", "fallback");
        
        List<ParamInfo> result = extractor.extractParam(request);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("fallback", result.get(0).getAgentName());
    }
    
    @Test
    void testExtractParamWithNullSkillName() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContent("{\"skill\":{\"namespaceId\":\"ns1\"}}".getBytes());
        
        List<ParamInfo> result = extractor.extractParam(request);
        
        assertEquals(1, result.size());
        assertNull(result.get(0).getAgentName());
    }
}
