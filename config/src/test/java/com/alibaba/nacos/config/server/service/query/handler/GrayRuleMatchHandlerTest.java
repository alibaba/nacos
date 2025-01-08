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

import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.model.ConfigCacheGray;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskService;
import com.alibaba.nacos.config.server.service.dump.disk.ConfigDiskServiceFactory;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrayRuleMatchHandlerTest {
    
    @InjectMocks
    private GrayRuleMatchHandler grayRuleMatchHandler;
    
    private MockedStatic<ConfigChainEntryHandler> configChainEntryHandlerMockedStatic;
    
    private MockedStatic<ConfigDiskServiceFactory> configDiskServiceFactoryMockedStatic;
    
    @Mock
    private CacheItem cacheItem;
    
    @Mock
    private ConfigCacheGray configCacheGray;
    
    @Mock
    private ConfigDiskService configDiskService;
    
    @Mock
    private ConfigQueryHandler nextHandler;
    
    @BeforeEach
    public void setUp() {
        configChainEntryHandlerMockedStatic = Mockito.mockStatic(ConfigChainEntryHandler.class);
        configChainEntryHandlerMockedStatic.when(ConfigChainEntryHandler::getThreadLocalCacheItem)
                .thenReturn(cacheItem);
        configDiskServiceFactoryMockedStatic = Mockito.mockStatic(ConfigDiskServiceFactory.class);
        configDiskServiceFactoryMockedStatic.when(ConfigDiskServiceFactory::getInstance).thenReturn(configDiskService);
    }
    
    @AfterEach
    public void tearDown() {
        configChainEntryHandlerMockedStatic.close();
        configDiskServiceFactoryMockedStatic.close();
    }
    
    @Test
    public void handleNoGrayRulesShouldPassToNextHandler() throws IOException {
        when(cacheItem.getSortConfigGrays()).thenReturn(Collections.emptyList());
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        ConfigQueryChainResponse expectedResponse = new ConfigQueryChainResponse();
        expectedResponse.setResultCode(123); // 假设这是下一个处理器的响应
        
        when(nextHandler.handle(request)).thenReturn(expectedResponse);
        grayRuleMatchHandler.setNextHandler(nextHandler);
        
        ConfigQueryChainResponse response = grayRuleMatchHandler.handle(request);
        
        assertEquals(expectedResponse, response);
    }
    
    @Test
    public void handleNoMatchingGrayRuleShouldPassToNextHandler() throws IOException {
        when(cacheItem.getSortConfigGrays()).thenReturn(Collections.singletonList(configCacheGray));
        when(configCacheGray.match(any())).thenReturn(false);
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        ConfigQueryChainResponse expectedResponse = new ConfigQueryChainResponse();
        expectedResponse.setResultCode(123); // 假设这是下一个处理器的响应
        
        when(nextHandler.handle(request)).thenReturn(expectedResponse);
        grayRuleMatchHandler.setNextHandler(nextHandler);
        
        ConfigQueryChainResponse response = grayRuleMatchHandler.handle(request);
        
        assertEquals(expectedResponse, response);
    }
    
    @Test
    public void handleMatchingGrayRuleShouldReturnConfigResponse() throws IOException {
        when(cacheItem.getSortConfigGrays()).thenReturn(Collections.singletonList(configCacheGray));
        when(configCacheGray.match(any())).thenReturn(true);
        when(configCacheGray.getLastModifiedTs()).thenReturn(123456L);
        when(configCacheGray.getMd5()).thenReturn("md5");
        when(configCacheGray.getEncryptedDataKey()).thenReturn("encryptedKey");
        when(configCacheGray.getGrayName()).thenReturn("grayName");
        when(cacheItem.getType()).thenReturn("configType");
        when(configDiskService.getGrayContent(anyString(), anyString(), anyString(), anyString())).thenReturn(
                "content");
        
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId("dataId");
        request.setGroup("group");
        request.setTenant("tenant");
        
        ConfigQueryChainResponse response = grayRuleMatchHandler.handle(request);
        
        assertEquals("content", response.getContent());
        assertEquals("md5", response.getMd5());
        assertEquals(123456L, response.getLastModified());
        assertEquals("encryptedKey", response.getEncryptedDataKey());
        assertEquals("configType", response.getConfigType());
        assertEquals(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_GRAY, response.getStatus());
    }
    
    @Test
    public void testGetName() {
        assertEquals("grayRuleMatchHandler", grayRuleMatchHandler.getName());
    }
}