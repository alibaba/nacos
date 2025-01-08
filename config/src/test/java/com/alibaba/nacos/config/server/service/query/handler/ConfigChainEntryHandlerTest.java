package com.alibaba.nacos.config.server.service.query.handler;

import com.alibaba.nacos.config.server.model.CacheItem;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.config.server.utils.GroupKey2;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigChainEntryHandlerTest {
    
    @InjectMocks
    private ConfigChainEntryHandler configChainEntryHandler;
    
    private MockedStatic<GroupKey2> mockedStaticGroupKey2;
    
    private MockedStatic<ConfigCacheService> mockedStaticConfigCacheService;
    
    @Mock
    private ConfigQueryHandler nextHandler;
    
    @Mock
    private CacheItem cacheItem;
    
    @BeforeEach
    public void setUp() {
        mockedStaticGroupKey2 = Mockito.mockStatic(GroupKey2.class);
        mockedStaticConfigCacheService = Mockito.mockStatic(ConfigCacheService.class);
        configChainEntryHandler.setNextHandler(nextHandler);
    }
    
    @AfterEach
    public void tearDown() {
        mockedStaticGroupKey2.close();
        mockedStaticConfigCacheService.close();
    }
    
    @Test
    public void handleLockSuccessAndCacheItemNotNullShouldInvokeNextHandler() throws IOException {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId("dataId");
        request.setGroup("group");
        request.setTenant("tenant");
        
        String groupKey = "groupKey";
        mockedStaticGroupKey2.when(() -> GroupKey2.getKey(anyString(), anyString(), anyString())).thenReturn(groupKey);
        mockedStaticConfigCacheService.when(() -> ConfigCacheService.tryConfigReadLock(groupKey)).thenReturn(1);
        mockedStaticConfigCacheService.when(() -> ConfigCacheService.getContentCache(groupKey)).thenReturn(cacheItem);
        
        ConfigQueryChainResponse nextResponse = new ConfigQueryChainResponse();
        nextResponse.setResultCode(200);
        when(nextHandler.handle(request)).thenReturn(nextResponse);
        ConfigQueryChainResponse response = configChainEntryHandler.handle(request);
        
        assertEquals(200, response.getResultCode());
        verify(nextHandler, times(1)).handle(request);
    }
    
    @Test
    public void handleLockSuccessAndCacheItemNullShouldReturnNotFound() throws IOException {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId("dataId");
        request.setGroup("group");
        request.setTenant("tenant");
        
        String groupKey = "groupKey";
        mockedStaticGroupKey2.when(() -> GroupKey2.getKey(anyString(), anyString(), anyString())).thenReturn(groupKey);
        mockedStaticConfigCacheService.when(() -> ConfigCacheService.tryConfigReadLock(groupKey)).thenReturn(1);
        mockedStaticConfigCacheService.when(() -> ConfigCacheService.getContentCache(groupKey)).thenReturn(null);
        
        ConfigQueryChainResponse response = configChainEntryHandler.handle(request);
        
        assertEquals(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND, response.getStatus());
        verify(nextHandler, never()).handle(any());
    }
    
    @Test
    public void handleLockFailureShouldReturnConflict() throws IOException {
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId("dataId");
        request.setGroup("group");
        request.setTenant("tenant");
        
        String groupKey = "groupKey";
        mockedStaticGroupKey2.when(() -> GroupKey2.getKey(anyString(), anyString(), anyString())).thenReturn(groupKey);
        mockedStaticConfigCacheService.when(() -> ConfigCacheService.tryConfigReadLock(groupKey)).thenReturn(-1);
        mockedStaticConfigCacheService.when(() -> ConfigCacheService.getContentCache(groupKey)).thenReturn(cacheItem);
        
        ConfigQueryChainResponse response = configChainEntryHandler.handle(request);
        
        assertEquals(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_QUERY_CONFLICT, response.getStatus());
        verify(nextHandler, never()).handle(any());
    }
    
    @Test
    public void handleLockSuccessAndNextHandlerNullShouldReturnEmptyResponse() throws IOException {
        configChainEntryHandler.setNextHandler(null);
        
        ConfigQueryChainRequest request = new ConfigQueryChainRequest();
        request.setDataId("dataId");
        request.setGroup("group");
        request.setTenant("tenant");
        
        String groupKey = "groupKey";
        mockedStaticGroupKey2.when(() -> GroupKey2.getKey(anyString(), anyString(), anyString())).thenReturn(groupKey);
        mockedStaticConfigCacheService.when(() -> ConfigCacheService.tryConfigReadLock(groupKey)).thenReturn(1);
        mockedStaticConfigCacheService.when(() -> ConfigCacheService.getContentCache(groupKey)).thenReturn(cacheItem);
        
        ConfigQueryChainResponse response = configChainEntryHandler.handle(request);
        assertNull(response.getStatus());
        verify(nextHandler, never()).handle(any());
    }
    
    @Test
    public void testGetName() {
        assertEquals("configChainEntryHandler", configChainEntryHandler.getName());
    }
    
}