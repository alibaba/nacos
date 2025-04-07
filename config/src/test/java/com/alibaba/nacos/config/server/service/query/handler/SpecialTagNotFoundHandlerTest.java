/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.query.handler;

import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecialTagNotFoundHandlerTest {
    
    @InjectMocks
    private SpecialTagNotFoundHandler specialTagNotFoundHandler;
    
    @Mock
    private ConfigQueryHandler nextHandler;
    
    @Test
    public void handleTagNotEmptyReturnsSpecialTagNotFoundResponse() throws IOException {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setTag("someTag");
        ConfigQueryChainResponse response = specialTagNotFoundHandler.handle(request);
        assertEquals(ConfigQueryChainResponse.ConfigQueryStatus.SPECIAL_TAG_CONFIG_NOT_FOUND, response.getStatus());
    }
    
    @Test
    public void handleTagEmptyDelegatesToNextHandler() throws IOException {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setTag("");
        ConfigQueryChainResponse expectedResponse = new ConfigQueryChainResponse();
        when(nextHandler.handle(request)).thenReturn(expectedResponse);
        ConfigQueryChainResponse response = specialTagNotFoundHandler.handle(request);
        assertEquals(expectedResponse, response);
    }
    
    @Test
    public void getName() {
        assertEquals("specialTagNotFoundHandler", specialTagNotFoundHandler.getName());
    }
    
}