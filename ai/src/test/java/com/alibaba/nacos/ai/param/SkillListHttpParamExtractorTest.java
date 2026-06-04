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

import com.alibaba.nacos.ai.controller.SkillAdminController;
import com.alibaba.nacos.ai.form.AiResourceFilterableForm;
import com.alibaba.nacos.ai.form.skills.admin.SkillListForm;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.paramcheck.DefaultParamChecker;
import com.alibaba.nacos.common.paramcheck.ParamCheckResponse;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.code.ControllerMethodsCache;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.core.paramcheck.ParamCheckerFilter;
import com.alibaba.nacos.core.paramcheck.ServerParamCheckConfig;
import com.alibaba.nacos.sys.env.EnvUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillListHttpParamExtractorTest {
    
    private MockHttpServletRequest request;
    
    private SkillListHttpParamExtractor httpParamExtractor;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        httpParamExtractor = new SkillListHttpParamExtractor();
        request = new MockHttpServletRequest();
    }
    
    @Test
    void extractParamShouldNotTreatSkillNameAsCanonicalResourceName() throws NacosException {
        request.addParameter("namespaceId", "public");
        request.addParameter("skillName", "test-");
        
        List<ParamInfo> actual = httpParamExtractor.extractParam(request);
        
        assertEquals(1, actual.size());
        assertEquals("public", actual.get(0).getNamespaceId());
        assertNull(actual.get(0).getSkillName());
        assertEquals("test-", actual.get(0).getSkillSearchName());
    }
    
    @Test
    void fuzzySearchTermWithTrailingHyphenShouldPassGlobalParamCheck() throws NacosException {
        request.addParameter("namespaceId", "public");
        request.addParameter("skillName", "test-");
        
        List<ParamInfo> paramInfos = httpParamExtractor.extractParam(request);
        ParamCheckResponse response = new DefaultParamChecker().checkParamInfoList(paramInfos);
        
        assertTrue(response.isSuccess());
    }
    
    @Test
    void fuzzySearchTermWithIllegalCharactersShouldFailGlobalParamCheck() throws NacosException {
        request.addParameter("namespaceId", "public");
        request.addParameter("skillName", "skill@name");
        
        List<ParamInfo> paramInfos = httpParamExtractor.extractParam(request);
        ParamCheckResponse response = new DefaultParamChecker().checkParamInfoList(paramInfos);
        
        assertFalse(response.isSuccess());
        assertEquals("Skill search name may only contain lowercase letters, numbers, and hyphens",
            response.getMessage());
    }
    
    @Test
    void accurateSearchTermShouldBeTreatedAsCanonicalSkillName() throws NacosException {
        request.addParameter("namespaceId", "public");
        request.addParameter("skillName", "test-");
        request.addParameter("search", "accurate");
        
        List<ParamInfo> actual = httpParamExtractor.extractParam(request);
        
        assertEquals(1, actual.size());
        assertEquals("test-", actual.get(0).getSkillName());
        assertNull(actual.get(0).getSkillSearchName());
    }
    
    @Test
    void accurateSearchTermWithTrailingHyphenShouldFailGlobalParamCheck() throws NacosException {
        request.addParameter("namespaceId", "public");
        request.addParameter("skillName", "test-");
        request.addParameter("search", "accurate");
        
        List<ParamInfo> paramInfos = httpParamExtractor.extractParam(request);
        ParamCheckResponse response = new DefaultParamChecker().checkParamInfoList(paramInfos);
        
        assertFalse(response.isSuccess());
        assertEquals("Skill name may only contain lowercase letters, numbers, and hyphens, "
            + "and must not start or end with a hyphen", response.getMessage());
    }
    
    @Test
    void paramCheckerFilterShouldAllowFuzzySearchTermWithTrailingHyphen() throws Exception {
        request.addParameter("namespaceId", "public");
        request.addParameter("skillName", "test-");
        request.addParameter("search", "blur");
        
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = doFilterThroughSkillListMethod(request, chain);
        
        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }
    
    @Test
    void paramCheckerFilterShouldRejectFuzzySearchTermWithIllegalCharacters() throws Exception {
        request.addParameter("namespaceId", "public");
        request.addParameter("skillName", "skill@name");
        request.addParameter("search", "blur");
        
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = doFilterThroughSkillListMethod(request, chain);
        
        assertEquals(400, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }
    
    @Test
    void paramCheckerFilterShouldRejectAccurateSearchTermWithTrailingHyphen() throws Exception {
        request.addParameter("namespaceId", "public");
        request.addParameter("skillName", "test-");
        request.addParameter("search", "accurate");
        
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = doFilterThroughSkillListMethod(request, chain);
        
        assertEquals(400, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }
    
    private MockHttpServletResponse doFilterThroughSkillListMethod(MockHttpServletRequest request,
        FilterChain chain)
        throws NoSuchMethodException, ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/v3/admin/ai/skills/list");
        ServerParamCheckConfig.getInstance().setParamCheckEnabled(true);
        ServerParamCheckConfig.getInstance().setActiveParamChecker("default");
        ControllerMethodsCache methodsCache = mock(ControllerMethodsCache.class);
        Method method = SkillAdminController.class.getMethod("listSkills", SkillListForm.class,
            AiResourceFilterableForm.class, PageForm.class);
        when(methodsCache.getMethod(request)).thenReturn(method);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new ParamCheckerFilter(methodsCache).doFilter(request, response, chain);
        return response;
    }
}
