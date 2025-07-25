/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.index;

import com.alibaba.nacos.ai.constant.Constants;
import com.alibaba.nacos.ai.model.mcp.McpServerIndexData;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.service.ConfigDetailService;
import com.alibaba.nacos.config.server.service.query.ConfigQueryChainService;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainRequest;
import com.alibaba.nacos.config.server.service.query.model.ConfigQueryChainResponse;
import com.alibaba.nacos.core.service.NamespaceOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CachedMcpServerIndex.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CachedMcpServerIndexTest {
    
    @Mock
    private ConfigDetailService configDetailService;
    
    @Mock
    private NamespaceOperationService namespaceOperationService;
    
    @Mock
    private ConfigQueryChainService configQueryChainService;
    
    @Mock
    private McpCacheIndex cacheIndex;
    
    @Mock
    private ScheduledExecutorService scheduledExecutor;
    
    private CachedMcpServerIndex cachedIndex;
    
    @BeforeEach
    void setUp() {
        // Set system properties to enable cache
        System.setProperty("nacos.mcp.cache.enabled", "true");
        System.setProperty("nacos.mcp.cache.sync.interval", "300");
        
        cachedIndex = new CachedMcpServerIndex(configDetailService, namespaceOperationService, configQueryChainService,
                cacheIndex, scheduledExecutor, true, 300);
    }
    
    @Test
    void testGetMcpServerByIdWithCacheHit() {
        final String mcpId = "test-id-123";
        final String namespaceId = "test-namespace";
        
        // 模拟缓存命中
        McpServerIndexData cachedData = new McpServerIndexData();
        cachedData.setId(mcpId);
        cachedData.setNamespaceId(namespaceId);
        when(cacheIndex.getMcpServerById(mcpId)).thenReturn(cachedData);
        
        // 执行查询
        McpServerIndexData result = cachedIndex.getMcpServerById(mcpId);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(mcpId, result.getId());
        assertEquals(namespaceId, result.getNamespaceId());
        
        // 验证缓存被调用，数据库查询没有被调用
        verify(cacheIndex).getMcpServerById(mcpId);
        verify(configQueryChainService, never()).handle(any());
    }
    
    @Test
    void testGetMcpServerByIdWithCacheMiss() {
        final String mcpId = "test-id-123";
        final String namespaceId = "test-namespace";
        
        // 模拟缓存未命中
        when(cacheIndex.getMcpServerById(mcpId)).thenReturn(null);
        
        // 模拟数据库查询结果
        ConfigQueryChainResponse mockResponse = mock(ConfigQueryChainResponse.class);
        when(mockResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(mockResponse);
        
        // 模拟命名空间列表
        List<com.alibaba.nacos.api.model.response.Namespace> namespaceList = new ArrayList<>();
        com.alibaba.nacos.api.model.response.Namespace namespace = new com.alibaba.nacos.api.model.response.Namespace();
        namespace.setNamespace(namespaceId);
        namespaceList.add(namespace);
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaceList);
        
        // 执行查询
        McpServerIndexData result = cachedIndex.getMcpServerById(mcpId);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(mcpId, result.getId());
        assertEquals(namespaceId, result.getNamespaceId());
        
        // 验证缓存被调用，数据库查询也被调用
        verify(cacheIndex).getMcpServerById(mcpId);
        verify(configQueryChainService).handle(any(ConfigQueryChainRequest.class));
        
        // 验证缓存被更新
        verify(cacheIndex).updateIndex(eq(namespaceId), eq(mcpId), eq(mcpId));
    }
    
    @Test
    void testGetMcpServerByNameWithCacheHit() {
        final String namespaceId = "test-namespace";
        final String mcpName = "test-mcp";
        final String mcpId = "test-id-123";
        
        // 模拟缓存命中
        McpServerIndexData cachedData = new McpServerIndexData();
        cachedData.setId(mcpId);
        cachedData.setNamespaceId(namespaceId);
        when(cacheIndex.getMcpServerByName(namespaceId, mcpName)).thenReturn(cachedData);
        
        // 执行查询
        McpServerIndexData result = cachedIndex.getMcpServerByName(namespaceId, mcpName);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(mcpId, result.getId());
        assertEquals(namespaceId, result.getNamespaceId());
        
        // 验证缓存被调用
        verify(cacheIndex).getMcpServerByName(namespaceId, mcpName);
    }
    
    @Test
    void testGetMcpServerByNameWithCacheMiss() {
        final String namespaceId = "test-namespace";
        final String mcpName = "test-mcp";
        final String mcpId = "test-id-123";
        
        // 模拟缓存未命中
        when(cacheIndex.getMcpServerByName(namespaceId, mcpName)).thenReturn(null);
        
        // 模拟数据库查询结果
        final Page<ConfigInfo> mockPage = new Page<>();
        List<ConfigInfo> configList = new ArrayList<>();
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setDataId(mcpId + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX);
        configInfo.setTenant(namespaceId);
        configList.add(configInfo);
        mockPage.setPageItems(configList);
        mockPage.setTotalCount(1);
        
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(1), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any())).thenReturn(mockPage);
        
        // 执行查询
        McpServerIndexData result = cachedIndex.getMcpServerByName(namespaceId, mcpName);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(mcpId, result.getId());
        assertEquals(namespaceId, result.getNamespaceId());
        
        // 验证缓存被调用，数据库查询也被调用
        verify(cacheIndex).getMcpServerByName(namespaceId, mcpName);
        verify(configDetailService).findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(1), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any());
        
        // 验证缓存被更新
        verify(cacheIndex).updateIndex(eq(namespaceId), eq(mcpName), eq(mcpId));
    }
    
    @Test
    void testSearchMcpServerByName() {
        final String namespaceId = "test-namespace";
        final String mcpName = "test-mcp";
        final String mcpId = "test-id-123";
        
        // 模拟数据库查询结果
        final Page<ConfigInfo> mockPage = new Page<>();
        List<ConfigInfo> configList = new ArrayList<>();
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setDataId(mcpId + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX);
        configInfo.setTenant(namespaceId);
        configList.add(configInfo);
        mockPage.setPageItems(configList);
        mockPage.setTotalCount(1);
        
        // 使用正确的参数匹配，匹配实际的调用参数
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(10), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any())).thenReturn(mockPage);
        
        // 执行搜索
        Page<McpServerIndexData> result = cachedIndex.searchMcpServerByName(namespaceId, mcpName,
                Constants.MCP_LIST_SEARCH_ACCURATE, 0, 10);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getPageItems().size());
        
        McpServerIndexData indexData = result.getPageItems().get(0);
        assertEquals(mcpId, indexData.getId());
        assertEquals(namespaceId, indexData.getNamespaceId());
        
        // 验证数据库查询被调用
        verify(configDetailService).findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(10), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any());
        
        // 验证缓存被更新
        verify(cacheIndex).updateIndex(eq(namespaceId), eq(mcpName), eq(mcpId));
    }
    
    @Test
    void testCacheDisabled() {
        // 设置系统属性以禁用缓存
        System.setProperty("nacos.mcp.cache.enabled", "false");
        
        // 重新创建实例
        final CachedMcpServerIndex disabledIndex = new CachedMcpServerIndex(configDetailService,
                namespaceOperationService, configQueryChainService, cacheIndex, scheduledExecutor, false, 0);
        
        final String mcpId = "test-id-123";
        final String namespaceId = "test-namespace";
        
        // 模拟数据库查询结果
        ConfigQueryChainResponse mockResponse = mock(ConfigQueryChainResponse.class);
        when(mockResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(mockResponse);
        
        // 模拟命名空间列表
        List<com.alibaba.nacos.api.model.response.Namespace> namespaceList = new ArrayList<>();
        com.alibaba.nacos.api.model.response.Namespace namespace = new com.alibaba.nacos.api.model.response.Namespace();
        namespace.setNamespace(namespaceId);
        namespaceList.add(namespace);
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaceList);
        
        // 执行查询
        McpServerIndexData result = disabledIndex.getMcpServerById(mcpId);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(mcpId, result.getId());
        assertEquals(namespaceId, result.getNamespaceId());
        
        // 验证缓存没有被调用
        verify(cacheIndex, never()).getMcpServerById(anyString());
        verify(cacheIndex, never()).updateIndex(anyString(), anyString(), anyString());
        
        // 验证数据库查询被调用
        verify(configQueryChainService).handle(any(ConfigQueryChainRequest.class));
    }
    
    @Test
    void testGetCacheStats() {
        // 模拟缓存统计
        McpCacheIndex.CacheStats mockStats = new McpCacheIndex.CacheStats(10, 5, 2, 100);
        when(cacheIndex.getStats()).thenReturn(mockStats);
        
        // 获取统计信息
        McpCacheIndex.CacheStats result = cachedIndex.getCacheStats();
        
        // 验证结果
        assertNotNull(result);
        assertEquals(10, result.getHitCount());
        assertEquals(5, result.getMissCount());
        assertEquals(2, result.getEvictionCount());
        assertEquals(100, result.getSize());
        assertEquals(2.0 / 3.0, result.getHitRate(), 0.001);
    }
    
    @Test
    void testClearCache() {
        // 执行清空缓存
        cachedIndex.clearCache();
        
        // 验证缓存被清空
        verify(cacheIndex).clear();
    }
    
    @Test
    void testTriggerCacheSync() {
        // 模拟命名空间列表
        List<com.alibaba.nacos.api.model.response.Namespace> namespaceList = new ArrayList<>();
        com.alibaba.nacos.api.model.response.Namespace namespace = new com.alibaba.nacos.api.model.response.Namespace();
        namespace.setNamespace("test-namespace");
        namespaceList.add(namespace);
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaceList);
        
        // 模拟搜索结果
        final Page<ConfigInfo> mockPage = new Page<>();
        mockPage.setPageItems(new ArrayList<>());
        mockPage.setTotalCount(0);
        
        when(configDetailService.findConfigInfoPage(anyString(), anyInt(), anyInt(), anyString(), anyString(),
                anyString(), any())).thenReturn(mockPage);
        
        // 执行手动同步
        cachedIndex.triggerCacheSync();
        
        // 验证数据库查询被调用
        verify(configDetailService).findConfigInfoPage(anyString(), anyInt(), anyInt(), anyString(), anyString(),
                anyString(), any());
    }
    
    // 新增缓存删除功能测试
    
    @Test
    void testRemoveMcpServerByNameWhenCacheEnabled() {
        final String namespaceId = "test-namespace";
        final String mcpName = "test-mcp-name";
        
        // 执行缓存删除
        cachedIndex.removeMcpServerByName(namespaceId, mcpName);
        
        // 验证缓存删除方法被调用
        verify(cacheIndex).removeIndex(namespaceId, mcpName);
    }
    
    @Test
    void testRemoveMcpServerByIdWhenCacheEnabled() {
        final String mcpId = "test-mcp-id-123";
        
        // 执行缓存删除
        cachedIndex.removeMcpServerById(mcpId);
        
        // 验证缓存删除方法被调用
        verify(cacheIndex).removeIndex(mcpId);
    }
    
    @Test
    void testRemoveMcpServerByNameWhenCacheDisabled() {
        // 创建禁用缓存的实例
        final CachedMcpServerIndex disabledIndex = new CachedMcpServerIndex(configDetailService,
                namespaceOperationService, configQueryChainService, cacheIndex, scheduledExecutor, false, 0);
        
        final String namespaceId = "test-namespace";
        final String mcpName = "test-mcp-name";
        
        // 执行缓存删除
        disabledIndex.removeMcpServerByName(namespaceId, mcpName);
        
        // 验证缓存删除方法没有被调用（因为缓存被禁用）
        verify(cacheIndex, never()).removeIndex(namespaceId, mcpName);
    }
    
    @Test
    void testRemoveMcpServerByIdWhenCacheDisabled() {
        // 创建禁用缓存的实例
        final CachedMcpServerIndex disabledIndex = new CachedMcpServerIndex(configDetailService,
                namespaceOperationService, configQueryChainService, cacheIndex, scheduledExecutor, false, 0);
        
        final String mcpId = "test-mcp-id-123";
        
        // 执行缓存删除
        disabledIndex.removeMcpServerById(mcpId);
        
        // 验证缓存删除方法没有被调用（因为缓存被禁用）
        verify(cacheIndex, never()).removeIndex(mcpId);
    }
    
    @Test
    void testRemoveMcpServerByNameWithNullParameters() {
        // 测试 null 参数
        cachedIndex.removeMcpServerByName(null, null);
        cachedIndex.removeMcpServerByName("namespace", null);
        cachedIndex.removeMcpServerByName(null, "mcpName");
        
        // 验证缓存删除方法没有被调用（因为参数为 null 或空）
        verify(cacheIndex, never()).removeIndex(anyString(), anyString());
    }
    
    @Test
    void testRemoveMcpServerByIdWithNullParameter() {
        // 测试 null 参数
        cachedIndex.removeMcpServerById(null);
        
        // 验证缓存删除方法没有被调用（因为参数为 null）
        verify(cacheIndex, never()).removeIndex(anyString());
    }
    
    @Test
    void testRemoveMcpServerByNameWithEmptyParameters() {
        // 测试空字符串参数
        cachedIndex.removeMcpServerByName("", "");
        cachedIndex.removeMcpServerByName("namespace", "");
        cachedIndex.removeMcpServerByName("", "mcpName");
        
        // 空字符串应该仍然调用缓存删除方法
        verify(cacheIndex).removeIndex("", "");
        verify(cacheIndex).removeIndex("namespace", "");
        verify(cacheIndex).removeIndex("", "mcpName");
    }
    
    @Test
    void testRemoveMcpServerByIdWithEmptyParameter() {
        // 测试空字符串参数
        cachedIndex.removeMcpServerById("");
        
        // 空字符串应该仍然调用缓存删除方法
        verify(cacheIndex).removeIndex("");
    }
} 