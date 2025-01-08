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
import com.alibaba.nacos.config.server.model.ConfigCache;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FormalHandlerTest {
    
    @InjectMocks
    private FormalHandler formalHandler;
    
    private MockedStatic<ConfigDiskServiceFactory> configDiskServiceFactoryMockedStatic;
    
    private MockedStatic<ConfigChainEntryHandler> configChainEntryHandlerMockedStatic;
    
    @Mock
    private ConfigDiskService configDiskService;
    
    @Mock
    private CacheItem cacheItem;
    
    @Mock
    private ConfigCache configCache;
    
    @BeforeEach
    public void setUp() throws IOException {
        configDiskServiceFactoryMockedStatic = Mockito.mockStatic(ConfigDiskServiceFactory.class);
        configChainEntryHandlerMockedStatic = Mockito.mockStatic(ConfigChainEntryHandler.class);
        configChainEntryHandlerMockedStatic.when(ConfigChainEntryHandler::getThreadLocalCacheItem)
                .thenReturn(cacheItem);
        configDiskServiceFactoryMockedStatic.when(ConfigDiskServiceFactory::getInstance).thenReturn(configDiskService);
    }
    
    @AfterEach
    public void tearDown() {
        configDiskServiceFactoryMockedStatic.close();
        configChainEntryHandlerMockedStatic.close();
    }
    
    @Test
    public void handleContentEmptyShouldReturnConfigNotFound() throws IOException {
        when(cacheItem.getConfigCache()).thenReturn(configCache);
        when(configCache.getMd5()).thenReturn("mockMd5");
        when(configDiskService.getContent("dataId", "group", "tenant")).thenReturn("");
        
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId("dataId");
        request.setGroup("group");
        request.setTenant("tenant");
        
        ConfigQueryChainResponse response = formalHandler.handle(request);
        
        assertEquals(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND, response.getStatus());
    }
    
    @Test
    public void handleContentNotEmptyShouldReturnConfigFoundFormal() throws IOException {
        when(cacheItem.getConfigCache()).thenReturn(configCache);
        when(configCache.getMd5()).thenReturn("mockMd5");
        when(configCache.getLastModifiedTs()).thenReturn(123456789L);
        when(configCache.getEncryptedDataKey()).thenReturn("mockEncryptedDataKey");
        when(cacheItem.getType()).thenReturn("mockType");
        when(configDiskService.getContent("dataId", "group", "tenant")).thenReturn("mockContent");
        
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId("dataId");
        request.setGroup("group");
        request.setTenant("tenant");
        
        ConfigQueryChainResponse response = formalHandler.handle(request);
        
        assertEquals("mockContent", response.getContent());
        assertEquals("mockMd5", response.getMd5());
        assertEquals(123456789L, response.getLastModified());
        assertEquals("mockEncryptedDataKey", response.getEncryptedDataKey());
        assertEquals("mockType", response.getConfigType());
        assertEquals(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL, response.getStatus());
    }
    
    @Test
    public void testGetName() {
        assertEquals("formalHandler", formalHandler.getName());
    }
    
}