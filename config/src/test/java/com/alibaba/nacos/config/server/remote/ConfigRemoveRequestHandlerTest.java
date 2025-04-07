/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.remote;

import com.alibaba.nacos.api.config.remote.request.ConfigRemoveRequest;
import com.alibaba.nacos.api.config.remote.response.ConfigRemoveResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.service.ConfigOperationService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigRemoveRequestHandlerTest {
    
    private ConfigRemoveRequestHandler configRemoveRequestHandler;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;
    
    @Mock
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    @Mock
    private ConfigOperationService configOperationService;
    
    @BeforeEach
    void setUp() throws Exception {
        configRemoveRequestHandler = new ConfigRemoveRequestHandler(configInfoPersistService,
                configInfoGrayPersistService, configOperationService);
    }
    
    @Test
    void testHandleSuccess() throws Exception {
        ConfigRemoveRequest configRemoveRequest = new ConfigRemoveRequest();
        configRemoveRequest.setRequestId("requestId");
        configRemoveRequest.setGroup("group");
        configRemoveRequest.setDataId("dataId");
        configRemoveRequest.setTenant("tenant");
        RequestMeta meta = new RequestMeta();
        meta.setClientIp("1.1.1.1");
        
        when(configOperationService.deleteConfig(
                anyString(),
                anyString(),
                anyString(),
                isNull(),
                eq("1.1.1.1"),
                isNull(),
                eq(Constants.RPC))).thenReturn(true);

        ConfigRemoveResponse response = configRemoveRequestHandler.handle(configRemoveRequest, meta);

        assertEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        verify(configOperationService, times(1)).deleteConfig(
                anyString(),
                anyString(),
                anyString(),
                isNull(),
                eq("1.1.1.1"),
                isNull(),
                eq(Constants.RPC));
    }
    
    @Test
    void testHandleException() throws Exception {
        ConfigRemoveRequest configRemoveRequest = new ConfigRemoveRequest();
        configRemoveRequest.setRequestId("requestId");
        configRemoveRequest.setGroup("group");
        configRemoveRequest.setDataId("dataId");
        configRemoveRequest.setTenant("tenant");
        RequestMeta meta = new RequestMeta();
        meta.setClientIp("1.1.1.1");
        
        when(configOperationService.deleteConfig(
                anyString(),
                anyString(),
                anyString(),
                isNull(),
                eq("1.1.1.1"),
                isNull(),
                eq(Constants.RPC))).thenThrow(new RuntimeException("test exception"));
        
        ConfigRemoveResponse response = configRemoveRequestHandler.handle(configRemoveRequest, meta);
        
        assertNotEquals(ResponseCode.SUCCESS.getCode(), response.getResultCode());
        assertTrue(response.getMessage().contains("test exception"));
    }
}