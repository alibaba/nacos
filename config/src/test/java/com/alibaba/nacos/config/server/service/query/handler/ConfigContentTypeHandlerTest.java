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

import com.alibaba.nacos.config.server.enums.FileTypeEnum;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigContentTypeHandlerTest {
    
    private ConfigContentTypeHandler configContentTypeHandler = new ConfigContentTypeHandler();
    
    @Mock
    private ConfigQueryHandler nextHandler;
    
    @BeforeEach
    public void setUp() {
        configContentTypeHandler.setNextHandler(nextHandler);
    }
    
    @Test
    public void handleConfigNotFoundReturnsSameResponse() throws IOException {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        
        when(nextHandler.handle(request)).thenReturn(response);
        
        ConfigQueryChainResponse actualResponse = configContentTypeHandler.handle(request);
        
        assertEquals(response, actualResponse);
    }
    
    @Test
    public void handleSpecialTagConfigNotFoundReturnsSameResponse() throws IOException {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.SPECIAL_TAG_CONFIG_NOT_FOUND);
        
        when(nextHandler.handle(request)).thenReturn(response);
        
        ConfigQueryChainResponse actualResponse = configContentTypeHandler.handle(request);
        
        assertEquals(response, actualResponse);
    }
    
    @Test
    public void handleContentTypeIsNullDefaultsToText() throws IOException {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        
        when(nextHandler.handle(request)).thenReturn(response);
        
        ConfigQueryChainResponse actualResponse = configContentTypeHandler.handle(request);
        
        assertEquals(FileTypeEnum.TEXT.getContentType(), actualResponse.getContentType());
    }
    
    @Test
    public void handleContentTypeIsNotNullSetsCorrectContentType() throws IOException {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        ConfigQueryChainResponse response = new ConfigQueryChainResponse();
        response.setStatus(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        response.setContentType(FileTypeEnum.JSON.name());
        
        when(nextHandler.handle(request)).thenReturn(response);
        
        ConfigQueryChainResponse actualResponse = configContentTypeHandler.handle(request);
        
        assertEquals(FileTypeEnum.JSON.getContentType(), actualResponse.getContentType());
    }
}