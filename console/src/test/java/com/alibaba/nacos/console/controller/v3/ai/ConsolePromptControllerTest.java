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
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.console.proxy.ai.PromptProxy;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConsolePromptController}.
 *
 * @author nacos
 */
@ExtendWith(MockitoExtension.class)
class ConsolePromptControllerTest {
    
    private static final String NS = "public";
    
    private static final String PROMPT_KEY = "test-prompt";
    
    private static final String VERSION = "0.0.1";
    
    @Mock
    private PromptProxy promptProxy;
    
    private MockMvc mockMvc;
    
    @BeforeEach
    void setUp() {
        EnvUtil.setEnvironment(new StandardEnvironment());
        ConsolePromptController controller = new ConsolePromptController(promptProxy);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }
    
    @Test
    void testDownloadPromptVersionReturnsAttachment() throws Exception {
        PromptVersionInfo info = new PromptVersionInfo();
        info.setPromptKey(PROMPT_KEY);
        info.setVersion(VERSION);
        info.setTemplate("Hello {{name}}");
        when(promptProxy.downloadPromptVersion(eq(NS), eq(PROMPT_KEY), eq(VERSION))).thenReturn(info);
        
        MockHttpServletResponse response = mockMvc.perform(
                        MockMvcRequestBuilders.get(Constants.Prompt.CONSOLE_PATH + "/version/download")
                                .param("namespaceId", NS)
                                .param("promptKey", PROMPT_KEY)
                                .param("version", VERSION))
                .andReturn().getResponse();
        
        assertEquals(200, response.getStatus());
        String contentType = response.getContentType();
        assertNotNull(contentType);
        assertTrue(contentType.toLowerCase().contains("text/markdown"),
                "Content-Type should be text/markdown, but was: " + contentType);
        
        String disposition = response.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);
        assertTrue(disposition.startsWith("attachment;"),
                "Content-Disposition should declare attachment, but was: " + disposition);
        assertTrue(disposition.contains(PROMPT_KEY),
                "Content-Disposition filename should contain prompt key, but was: " + disposition);
        
        String body = response.getContentAsString();
        assertNotNull(body);
        // Markdown body must render the prompt template.
        assertTrue(body.contains("Hello"),
                "Markdown body should contain prompt template content, but was: " + body);
        
        verify(promptProxy).downloadPromptVersion(eq(NS), eq(PROMPT_KEY), eq(VERSION));
    }
}
