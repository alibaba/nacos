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

package com.alibaba.nacos.console.controller.v3.ai;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.form.skills.admin.SkillPublishForm;
import com.alibaba.nacos.api.ai.model.skills.SkillSummary;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.proxy.ai.SkillProxy;
import com.alibaba.nacos.sys.env.EnvUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ConsoleSkillController.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
public class ConsoleSkillControllerTest {
    
    @Mock
    private SkillProxy skillProxy;
    
    private MockMvc mockMvc;
    
    private ConsoleSkillController consoleSkillController;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        consoleSkillController = new ConsoleSkillController(skillProxy);
        mockMvc = MockMvcBuilders.standaloneSetup(consoleSkillController).build();
    }
    
    @Test
    void testForcePublishSuccess() throws Exception {
        doNothing().when(skillProxy).forcePublish(any(SkillPublishForm.class));
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(
            Constants.Skills.CONSOLE_PATH + "/force-publish").param("namespaceId", "test-ns")
            .param("skillName", "test-skill").param("version", "v1");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String content = response.getContentAsString();
        Result<String> result = JacksonUtils.toObj(content, new TypeReference<>() {
        });
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(skillProxy).forcePublish(any(SkillPublishForm.class));
    }
    
    @Test
    void testListSkillsSuccess() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setTotalCount(1);
        page.setPagesAvailable(1);
        SkillSummary item = new SkillSummary();
        item.setName("test-skill");
        page.setPageItems(java.util.List.of(item));
        when(skillProxy.listSkills(any(), any(), any())).thenReturn(page);
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.Skills.CONSOLE_PATH + "/list")
                .param("pageNo", "1").param("pageSize", "10");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        Result<Page<SkillSummary>> result =
            JacksonUtils.toObj(response.getContentAsString(), new TypeReference<>() {
            });
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(1, result.getData().getTotalCount());
    }
    
    @Test
    void testListSkillsWithOwnerFilter() throws Exception {
        Page<SkillSummary> page = new Page<>();
        page.setTotalCount(0);
        page.setPageItems(java.util.Collections.emptyList());
        when(skillProxy.listSkills(any(), any(), any())).thenReturn(page);
        
        MockHttpServletRequestBuilder builder =
            MockMvcRequestBuilders.get(Constants.Skills.CONSOLE_PATH + "/list")
                .param("owner", "alice").param("pageNo", "1").param("pageSize", "10");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        verify(skillProxy).listSkills(any(), any(), any());
    }
}
