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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        configDiskServiceFactoryMockedStatic.when(ConfigDiskServiceFactory::getInstance)
            .thenReturn(configDiskService);
    }
    
    @AfterEach
    public void tearDown() {
        configDiskServiceFactoryMockedStatic.close();
        configChainEntryHandlerMockedStatic.close();
    }
    
    @Test
    public void handleContentNullShouldReturnConfigNotFound() throws IOException {
        when(cacheItem.getConfigCache()).thenReturn(configCache);
        when(configCache.getMd5()).thenReturn("mockMd5");
        when(configDiskService.getContent("dataId", "group", "tenant")).thenReturn(null);
        
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId("dataId");
        request.setGroup("group");
        request.setTenant("tenant");
        
        ConfigQueryChainResponse response = formalHandler.handle(request);
        
        assertEquals(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND,
            response.getStatus());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\n\t"})
    void handleBlankContentShouldReturnConfigFoundFormal(String content) throws IOException {
        when(cacheItem.getConfigCache()).thenReturn(configCache);
        when(configCache.getMd5()).thenReturn("mockMd5");
        when(configCache.getLastModifiedTs()).thenReturn(123456789L);
        when(configCache.getEncryptedDataKey()).thenReturn("mockEncryptedDataKey");
        when(cacheItem.getType()).thenReturn("text");
        when(configDiskService.getContent("dataId", "group", "tenant")).thenReturn(content);
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId("dataId");
        request.setGroup("group");
        request.setTenant("tenant");
        
        ConfigQueryChainResponse response = formalHandler.handle(request);
        
        assertEquals(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL,
            response.getStatus());
        assertEquals(content, response.getContent());
        assertEquals("mockMd5", response.getMd5());
        assertEquals(123456789L, response.getLastModified());
        assertEquals("mockEncryptedDataKey", response.getEncryptedDataKey());
        assertEquals("text", response.getConfigType());
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
        assertEquals(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL,
            response.getStatus());
    }
    
    @Test
    public void testGetName() {
        assertEquals("formalHandler", formalHandler.getName());
    }
    
    @Test
    public void handleWithMatchingLocalMd5ShouldSkipContentRead() throws IOException {
        // 304 optimization: when client's localMd5 matches server metadata MD5,
        // FormalHandler must skip disk content read entirely and return CONFIG_NOT_MODIFIED.
        when(cacheItem.getConfigCache()).thenReturn(configCache);
        when(configCache.getMd5()).thenReturn("matching-md5-123");
        when(configCache.getLastModifiedTs()).thenReturn(123456789L);
        when(configCache.getEncryptedDataKey()).thenReturn("enc-key-456");
        when(cacheItem.getType()).thenReturn("yaml");
        
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId("dataId");
        request.setGroup("group");
        request.setTenant("tenant");
        request.setLocalMd5("matching-md5-123");
        
        ConfigQueryChainResponse response = formalHandler.handle(request);
        
        // Verify 304 response
        assertEquals(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_MODIFIED,
            response.getStatus());
        assertEquals("matching-md5-123", response.getMd5());
        assertEquals(123456789L, response.getLastModified());
        assertEquals("enc-key-456", response.getEncryptedDataKey());
        assertEquals("yaml", response.getConfigType());
        // Content must be null (not read from disk)
        assertEquals(null, response.getContent());
        
        // Verify disk content read was NEVER called
        Mockito.verify(configDiskService, Mockito.never())
            .getContent(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }
    
    @Test
    public void handleWithNonMatchingLocalMd5ShouldReadContent() throws IOException {
        // When localMd5 does not match, FormalHandler must read content from disk normally.
        when(cacheItem.getConfigCache()).thenReturn(configCache);
        when(configCache.getMd5()).thenReturn("server-md5-abc");
        when(configCache.getLastModifiedTs()).thenReturn(987654321L);
        when(configCache.getEncryptedDataKey()).thenReturn("server-enc-key");
        when(cacheItem.getType()).thenReturn("properties");
        when(configDiskService.getContent("dataId", "group", "tenant"))
            .thenReturn("actual-config-content");
        
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId("dataId");
        request.setGroup("group");
        request.setTenant("tenant");
        request.setLocalMd5("client-md5-different");
        
        ConfigQueryChainResponse response = formalHandler.handle(request);
        
        // Verify normal response with content
        assertEquals(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL,
            response.getStatus());
        assertEquals("actual-config-content", response.getContent());
        assertEquals("server-md5-abc", response.getMd5());
        
        // Verify disk content read WAS called
        Mockito.verify(configDiskService, Mockito.times(1))
            .getContent("dataId", "group", "tenant");
    }
    
}
