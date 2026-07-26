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

package com.alibaba.nacos.ai.param;

import com.alibaba.nacos.api.ai.model.skills.Skill;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillHttpParamExtractorTest {
    
    @Mock
    HttpServletRequest request;
    
    private SkillHttpParamExtractor httpParamExtractor;
    
    @BeforeEach
    void setUp() {
        httpParamExtractor = new SkillHttpParamExtractor();
    }
    
    @Test
    void extractParamWithSkillName() throws NacosException {
        when(request.getParameter("namespaceId")).thenReturn("testNs");
        when(request.getParameter("skillName")).thenReturn("Skill_Name");
        when(request.getParameterMap())
            .thenReturn(Map.of("skillName", new String[] {"Skill_Name"}));
        
        List<ParamInfo> actual = httpParamExtractor.extractParam(request);
        assertEquals(1, actual.size());
        assertEquals("testNs", actual.get(0).getNamespaceId());
        assertEquals("Skill_Name", actual.get(0).getSkillName());
        assertNull(actual.get(0).getAgentName());
    }
    
    @Test
    void extractParamWithClientName() throws NacosException {
        when(request.getParameter("namespaceId")).thenReturn("testNs");
        when(request.getParameter("skillName")).thenReturn(null);
        when(request.getParameter("name")).thenReturn("ClientSkill");
        when(request.getParameterMap()).thenReturn(Map.of("name", new String[] {"ClientSkill"}));
        
        List<ParamInfo> actual = httpParamExtractor.extractParam(request);
        assertEquals(1, actual.size());
        assertEquals("ClientSkill", actual.get(0).getSkillName());
        assertNull(actual.get(0).getAgentName());
    }
    
    @Test
    void extractParamWithSkillCard() throws NacosException {
        Skill skill = new Skill();
        skill.setName("SkillFromCard");
        String skillCardJson = JacksonUtils.toJson(skill);
        
        when(request.getParameter("namespaceId")).thenReturn("testNs");
        when(request.getParameter("skillName")).thenReturn("ignoredSkillName");
        when(request.getParameter("skillCard")).thenReturn(skillCardJson);
        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("skillName", new String[] {"ignoredSkillName"});
        parameterMap.put("skillCard", new String[] {skillCardJson});
        when(request.getParameterMap()).thenReturn(parameterMap);
        
        List<ParamInfo> actual = httpParamExtractor.extractParam(request);
        assertEquals(1, actual.size());
        assertEquals("SkillFromCard", actual.get(0).getSkillName());
        assertNull(actual.get(0).getAgentName());
    }
    
    @Test
    void extractParamWithInvalidSkillCardJson() throws NacosException {
        when(request.getParameter("namespaceId")).thenReturn("testNs");
        when(request.getParameter("skillName")).thenReturn("SkillName");
        when(request.getParameter("skillCard")).thenReturn("{invalidJson");
        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("skillName", new String[] {"SkillName"});
        parameterMap.put("skillCard", new String[] {"{invalidJson"});
        when(request.getParameterMap()).thenReturn(parameterMap);
        
        List<ParamInfo> actual = httpParamExtractor.extractParam(request);
        assertEquals(1, actual.size());
        assertEquals("", actual.get(0).getSkillName());
        assertNull(actual.get(0).getAgentName());
    }
}
