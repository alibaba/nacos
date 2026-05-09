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

package com.alibaba.nacos.ai.remote.handler;

import com.alibaba.nacos.ai.service.prompt.PromptClientOperationService;
import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.ai.model.prompt.PromptVersionInfo;
import com.alibaba.nacos.api.ai.remote.request.QueryPromptRequest;
import com.alibaba.nacos.api.ai.remote.response.QueryPromptResponse;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryPromptRequestHandlerTest {
    
    @Mock
    private PromptClientOperationService promptClientOperationService;
    
    private QueryPromptRequestHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new QueryPromptRequestHandler(promptClientOperationService);
    }
    
    @Test
    void handleShouldReturnInvalidParamWhenPromptKeyBlank() {
        QueryPromptRequest request = new QueryPromptRequest();
        QueryPromptResponse response = handler.handle(request, null);
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(NacosException.INVALID_PARAM, response.getErrorCode());
    }
    
    @Test
    void handleShouldReturnNotModifiedWhenServiceThrows304() throws NacosException {
        QueryPromptRequest request = new QueryPromptRequest();
        request.setPromptKey("p1");
        when(promptClientOperationService.queryPrompt("public", "p1", null, null, null))
            .thenThrow(
                new NacosException(NacosException.NOT_MODIFIED, "prompt data is up to date"));
        
        QueryPromptResponse response = handler.handle(request, null);
        
        assertEquals(ResponseCode.FAIL.getCode(), response.getResultCode());
        assertEquals(NacosException.NOT_MODIFIED, response.getErrorCode());
    }
    
    @Test
    void handleShouldMapPromptFieldsWhenSuccess() throws NacosException {
        QueryPromptRequest request = new QueryPromptRequest();
        request.setPromptKey("p1");
        PromptVersionInfo versionInfo = new PromptVersionInfo();
        versionInfo.setPromptKey("p1");
        versionInfo.setVersion("1.0.0");
        versionInfo.setTemplate("hello");
        versionInfo.setMd5("m1");
        when(promptClientOperationService.queryPrompt("public", "p1", null, null, null))
            .thenReturn(versionInfo);
        
        QueryPromptResponse response = handler.handle(request, null);
        Prompt prompt = response.getPromptInfo();
        
        assertNotNull(prompt);
        assertEquals("p1", prompt.getPromptKey());
        assertEquals("1.0.0", prompt.getVersion());
        assertEquals("hello", prompt.getTemplate());
        assertEquals("m1", prompt.getMd5());
    }
    
    @Test
    void handleShouldProcessNamespaceBeforeQuery() throws NacosException {
        QueryPromptRequest request = new QueryPromptRequest();
        request.setPromptKey("p1");
        request.setNamespaceId("");
        when(promptClientOperationService.queryPrompt("public", "p1", null, null, null))
            .thenReturn(new PromptVersionInfo());
        
        handler.handle(request, null);
        
        verify(promptClientOperationService).queryPrompt("public", "p1", null, null, null);
    }
}
