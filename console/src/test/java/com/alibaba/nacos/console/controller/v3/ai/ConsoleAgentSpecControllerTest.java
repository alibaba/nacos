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
import com.alibaba.nacos.ai.form.agentspecs.admin.AgentSpecPublishForm;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.console.proxy.ai.AgentSpecProxy;
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

/**
 * Unit tests for ConsoleAgentSpecController.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
public class ConsoleAgentSpecControllerTest {
    
    @Mock
    private AgentSpecProxy agentSpecProxy;
    
    private MockMvc mockMvc;
    
    private ConsoleAgentSpecController consoleAgentSpecController;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        consoleAgentSpecController = new ConsoleAgentSpecController(agentSpecProxy);
        mockMvc = MockMvcBuilders.standaloneSetup(consoleAgentSpecController).build();
    }
    
    @Test
    void testForcePublishSuccess() throws Exception {
        doNothing().when(agentSpecProxy).forcePublish(any(AgentSpecPublishForm.class));
        
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders
                .post(Constants.AgentSpecs.CONSOLE_PATH + "/force-publish")
                .param("namespaceId", "test-ns")
                .param("agentSpecName", "test-agentspec")
                .param("version", "v1");
        
        MockHttpServletResponse response = mockMvc.perform(builder).andReturn().getResponse();
        String content = response.getContentAsString();
        Result<String> result = JacksonUtils.toObj(content, new TypeReference<>() {
        });
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals("ok", result.getData());
        verify(agentSpecProxy).forcePublish(any(AgentSpecPublishForm.class));
    }
}
