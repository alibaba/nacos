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

package com.alibaba.nacos.ai.controller;

import com.alibaba.nacos.ai.form.prompt.PromptQueryForm;
import com.alibaba.nacos.ai.service.prompt.PromptClientOperationService;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.v2.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptClientControllerTest {
    
    @Mock
    private PromptClientOperationService promptClientOperationService;
    
    private PromptClientController controller;
    
    @BeforeEach
    void setUp() {
        controller = new PromptClientController(promptClientOperationService);
    }
    
    @Test
    void queryPromptShouldReturn304AndNullWhenNotModified() throws NacosException {
        PromptQueryForm form = new PromptQueryForm();
        form.setPromptKey("p1");
        HttpServletResponse response = new MockHttpServletResponse();
        when(promptClientOperationService.queryPrompt("public", "p1", null, null, null))
            .thenThrow(new NacosException(NacosException.NOT_MODIFIED, "up to date"));
        
        Result<Prompt> result = controller.queryPrompt(form, response);
        
        assertEquals(304, response.getStatus());
        assertNull(result.getData());
    }
    
    @Test
    void queryPromptShouldReturnPromptWhenSuccess() throws NacosException {
        PromptQueryForm form = new PromptQueryForm();
        form.setPromptKey("p1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        PromptVersionInfo versionInfo = new PromptVersionInfo();
        versionInfo.setPromptKey("p1");
        versionInfo.setVersion("1.0.0");
        versionInfo.setTemplate("template");
        versionInfo.setMd5("md5");
        when(promptClientOperationService.queryPrompt("public", "p1", null, null, null))
            .thenReturn(versionInfo);
        
        Result<Prompt> result = controller.queryPrompt(form, response);
        
        assertNotNull(result.getData());
        assertEquals("p1", result.getData().getPromptKey());
        assertEquals("1.0.0", result.getData().getVersion());
        assertEquals("template", result.getData().getTemplate());
    }
    
    @Test
    void queryPromptShouldRethrowWhenNon304Exception() throws NacosException {
        PromptQueryForm form = new PromptQueryForm();
        form.setPromptKey("p1");
        HttpServletResponse response = new MockHttpServletResponse();
        when(promptClientOperationService.queryPrompt("public", "p1", null, null, null))
            .thenThrow(new NacosException(NacosException.NOT_FOUND, "not found"));
        
        assertThrows(NacosException.class, () -> controller.queryPrompt(form, response));
    }
}
