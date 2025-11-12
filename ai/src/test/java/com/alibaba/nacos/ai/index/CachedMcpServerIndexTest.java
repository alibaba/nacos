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
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
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
        // 设置content为JSON格式的McpServerVersionInfo
        configInfo.setContent("{\"id\":\"" + mcpId + "\"}");
        configList.add(configInfo);
        mockPage.setPageItems(configList);
        mockPage.setTotalCount(1);
        
        // 使用正确的参数匹配，匹配实际的调用参数
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(10), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any())).thenReturn(mockPage);
        
        // 执行搜索
        Page<McpServerIndexData> result = cachedIndex.searchMcpServerByNameWithPage(namespaceId, mcpName,
                Constants.MCP_LIST_SEARCH_ACCURATE, 1, 10);
        
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
    
    // 补充的测试用例
    
    @Test
    void testGetMcpServerByIdWithCacheDisabledAndNotFound() {
        // 创建禁用缓存的实例
        final CachedMcpServerIndex disabledIndex = new CachedMcpServerIndex(configDetailService,
                namespaceOperationService, configQueryChainService, cacheIndex, scheduledExecutor, false, 0);
        
        final String mcpId = "test-id-123";
        
        // 模拟数据库查询结果为null
        ConfigQueryChainResponse mockResponse = mock(ConfigQueryChainResponse.class);
        when(mockResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(mockResponse);
        
        // 模拟命名空间列表
        List<com.alibaba.nacos.api.model.response.Namespace> namespaceList = new ArrayList<>();
        com.alibaba.nacos.api.model.response.Namespace namespace = new com.alibaba.nacos.api.model.response.Namespace();
        namespace.setNamespace("test-namespace");
        namespaceList.add(namespace);
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaceList);
        
        // 执行查询
        McpServerIndexData result = disabledIndex.getMcpServerById(mcpId);
        
        // 验证结果为null
        assertNull(result);
        
        // 验证缓存没有被调用
        verify(cacheIndex, never()).getMcpServerById(anyString());
        verify(cacheIndex, never()).updateIndex(anyString(), anyString(), anyString());
        
        // 验证数据库查询被调用
        verify(configQueryChainService).handle(any(ConfigQueryChainRequest.class));
    }
    
    @Test
    void testGetMcpServerByIdWithCacheMissAndNotFound() {
        final String mcpId = "test-id-123";
        
        // 模拟缓存未命中
        when(cacheIndex.getMcpServerById(mcpId)).thenReturn(null);
        
        // 模拟数据库查询结果为null（未找到）
        ConfigQueryChainResponse mockResponse = mock(ConfigQueryChainResponse.class);
        when(mockResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_NOT_FOUND);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(mockResponse);
        
        // 模拟命名空间列表
        List<com.alibaba.nacos.api.model.response.Namespace> namespaceList = new ArrayList<>();
        com.alibaba.nacos.api.model.response.Namespace namespace = new com.alibaba.nacos.api.model.response.Namespace();
        namespace.setNamespace("test-namespace");
        namespaceList.add(namespace);
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaceList);
        
        // 执行查询
        McpServerIndexData result = cachedIndex.getMcpServerById(mcpId);
        
        // 验证结果为null
        assertNull(result);
        
        // 验证缓存被调用，数据库查询也被调用
        verify(cacheIndex).getMcpServerById(mcpId);
        verify(configQueryChainService).handle(any(ConfigQueryChainRequest.class));
        
        // 验证缓存未被更新（因为未找到）
        verify(cacheIndex, never()).updateIndex(anyString(), anyString(), anyString());
    }
    
    @Test
    void testGetMcpServerByNameWithInvalidParameters() {
        // 设置默认的空 mock 结果
        final Page<ConfigInfo> emptyPage = new Page<>();
        emptyPage.setPageItems(new ArrayList<>());
        emptyPage.setTotalCount(0);
        when(configDetailService.findConfigInfoPage(anyString(), anyInt(), anyInt(), any(), anyString(),
                anyString(), any())).thenReturn(emptyPage);
        
        // 测试null参数 - 当两个都为null时，返回null
        McpServerIndexData result1 = cachedIndex.getMcpServerByName(null, "test-name");
        assertNull(result1);
        
        McpServerIndexData result2 = cachedIndex.getMcpServerByName("test-namespace", null);
        assertNull(result2);
        
        McpServerIndexData result3 = cachedIndex.getMcpServerByName(null, null);
        assertNull(result3);
        
        // 测试空字符串参数
        // 当namespaceId为空时，会调用getFirstMcpServerByName
        List<com.alibaba.nacos.api.model.response.Namespace> emptyNamespaceList = new ArrayList<>();
        when(namespaceOperationService.getNamespaceList()).thenReturn(emptyNamespaceList);
        
        McpServerIndexData result4 = cachedIndex.getMcpServerByName("", "test-name");
        assertNull(result4);
        
        // 恢复正常的命名空间列表
        List<com.alibaba.nacos.api.model.response.Namespace> namespaceList = new ArrayList<>();
        com.alibaba.nacos.api.model.response.Namespace namespace = new com.alibaba.nacos.api.model.response.Namespace();
        namespace.setNamespace("test-namespace");
        namespaceList.add(namespace);
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaceList);
        
        // 当name为空字符串时，实现仍然会调用缓存查询，这是正常行为
        // 确保我们的mock能够处理这种情况
        when(cacheIndex.getMcpServerByName("test-namespace", "")).thenReturn(null);
        
        McpServerIndexData result5 = cachedIndex.getMcpServerByName("test-namespace", "");
        assertNull(result5);
        
        // 验证缓存被调用了空字符串情况
        verify(cacheIndex).getMcpServerByName("test-namespace", "");
    }
    
    @Test
    void testGetMcpServerByNameWithCacheDisabledAndNotFound() {
        // 创建禁用缓存的实例
        final CachedMcpServerIndex disabledIndex = new CachedMcpServerIndex(configDetailService,
                namespaceOperationService, configQueryChainService, cacheIndex, scheduledExecutor, false, 0);
        
        final String namespaceId = "test-namespace";
        final String mcpName = "test-mcp";
        
        // 模拟数据库查询结果为null
        final Page<ConfigInfo> mockPage = new Page<>();
        mockPage.setPageItems(new ArrayList<>());
        mockPage.setTotalCount(0);
        
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(1), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any())).thenReturn(mockPage);
        
        // 执行查询
        McpServerIndexData result = disabledIndex.getMcpServerByName(namespaceId, mcpName);
        
        // 验证结果为null
        assertNull(result);
        
        // 验证缓存没有被调用
        verify(cacheIndex, never()).getMcpServerByName(anyString(), anyString());
        verify(cacheIndex, never()).updateIndex(anyString(), anyString(), anyString());
        
        // 验证数据库查询被调用
        verify(configDetailService).findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(1), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any());
    }
    
    @Test
    void testGetMcpServerByNameWithCacheMissAndNotFound() {
        final String namespaceId = "test-namespace";
        final String mcpName = "test-mcp";
        
        // 模拟缓存未命中
        when(cacheIndex.getMcpServerByName(namespaceId, mcpName)).thenReturn(null);
        
        // 模拟数据库查询结果为null
        final Page<ConfigInfo> mockPage = new Page<>();
        mockPage.setPageItems(new ArrayList<>());
        mockPage.setTotalCount(0);
        
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(1), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any())).thenReturn(mockPage);
        
        // 执行查询
        McpServerIndexData result = cachedIndex.getMcpServerByName(namespaceId, mcpName);
        
        // 验证结果为null
        assertNull(result);
        
        // 验证缓存被调用，数据库查询也被调用
        verify(cacheIndex).getMcpServerByName(namespaceId, mcpName);
        verify(configDetailService).findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(1), eq(1), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any());
        
        // 验证缓存未被更新（因为未找到）
        verify(cacheIndex, never()).updateIndex(anyString(), anyString(), anyString());
    }
    
    @Test
    void testSearchMcpServerByNameWithNullName() {
        final String namespaceId = "test-namespace";
        final String mcpId = "test-id-123";
        
        // 模拟数据库查询结果
        final Page<ConfigInfo> mockPage = new Page<>();
        List<ConfigInfo> configList = new ArrayList<>();
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setDataId(mcpId + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX);
        configInfo.setTenant(namespaceId);
        configInfo.setContent("{\"id\":\"" + mcpId + "\"}");
        configList.add(configInfo);
        mockPage.setPageItems(configList);
        mockPage.setTotalCount(1);
        
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(10), anyString(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any())).thenReturn(mockPage);
        
        // 执行搜索，name为null
        Page<McpServerIndexData> result = cachedIndex.searchMcpServerByNameWithPage(namespaceId, null,
                Constants.MCP_LIST_SEARCH_BLUR, 1, 10);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getPageItems().size());
        
        McpServerIndexData indexData = result.getPageItems().get(0);
        assertEquals(mcpId, indexData.getId());
        assertEquals(namespaceId, indexData.getNamespaceId());
        
        // 验证数据库查询被调用
        verify(configDetailService).findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(10), anyString(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any());
    }
    
    @Test
    void testSearchMcpServerByNameWithEmptyName() {
        final String namespaceId = "test-namespace";
        final String mcpId = "test-id-123";
        
        // 模拟数据库查询结果
        final Page<ConfigInfo> mockPage = new Page<>();
        List<ConfigInfo> configList = new ArrayList<>();
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setDataId(mcpId + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX);
        configInfo.setTenant(namespaceId);
        configInfo.setContent("{\"id\":\"" + mcpId + "\"}");
        configList.add(configInfo);
        mockPage.setPageItems(configList);
        mockPage.setTotalCount(1);
        
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(10), anyString(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any())).thenReturn(mockPage);
        
        // 执行搜索，name为空字符串
        Page<McpServerIndexData> result = cachedIndex.searchMcpServerByNameWithPage(namespaceId, "",
                Constants.MCP_LIST_SEARCH_ACCURATE, 1, 10);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getPageItems().size());
        
        McpServerIndexData indexData = result.getPageItems().get(0);
        assertEquals(mcpId, indexData.getId());
        assertEquals(namespaceId, indexData.getNamespaceId());
        
        // 验证数据库查询被调用
        verify(configDetailService).findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(10), anyString(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any());
    }
    
    @Test
    void testSearchMcpServerByNameWithBlurSearch() {
        final String namespaceId = "test-namespace";
        final String mcpName = "test-mcp";
        final String mcpId = "test-id-123";
        
        // 模拟数据库查询结果
        final Page<ConfigInfo> mockPage = new Page<>();
        List<ConfigInfo> configList = new ArrayList<>();
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setDataId(mcpId + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX);
        configInfo.setTenant(namespaceId);
        configInfo.setContent("{\"id\":\"" + mcpId + "\"}");
        configList.add(configInfo);
        mockPage.setPageItems(configList);
        mockPage.setTotalCount(1);
        
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(10), anyString(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any())).thenReturn(mockPage);
        
        // 执行搜索
        Page<McpServerIndexData> result = cachedIndex.searchMcpServerByNameWithPage(namespaceId, mcpName,
                Constants.MCP_LIST_SEARCH_BLUR, 1, 10);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.getTotalCount());
        assertEquals(1, result.getPageItems().size());
        
        McpServerIndexData indexData = result.getPageItems().get(0);
        assertEquals(mcpId, indexData.getId());
        assertEquals(namespaceId, indexData.getNamespaceId());
        
        // 验证数据库查询被调用
        verify(configDetailService).findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(10), anyString(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any());
    }
    
    @Test
    void testSearchMcpServerByNameWithPagination() {
        final String namespaceId = "test-namespace";
        final String mcpName = "test-mcp";
        final String mcpId = "test-id-123";
        
        // 模拟数据库查询结果
        final Page<ConfigInfo> mockPage = new Page<>();
        List<ConfigInfo> configList = new ArrayList<>();
        ConfigInfo configInfo = new ConfigInfo();
        configInfo.setDataId(mcpId + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX);
        configInfo.setTenant(namespaceId);
        configInfo.setContent("{\"id\":\"" + mcpId + "\"}");
        configList.add(configInfo);
        mockPage.setPageItems(configList);
        mockPage.setTotalCount(15); // 总数15，测试分页
        
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(3), eq(5), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any())).thenReturn(mockPage);
        
        // 执行搜索，pageNo=3, limit=5
        Page<McpServerIndexData> result = cachedIndex.searchMcpServerByNameWithPage(namespaceId, mcpName,
                Constants.MCP_LIST_SEARCH_ACCURATE, 3, 5);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(15, result.getTotalCount());
        assertEquals(1, result.getPageItems().size());
        assertEquals(3, result.getPageNumber());
        assertEquals(3, result.getPagesAvailable()); // ceil(15/5) = 3
        
        McpServerIndexData indexData = result.getPageItems().get(0);
        assertEquals(mcpId, indexData.getId());
        assertEquals(namespaceId, indexData.getNamespaceId());
        
        // 验证数据库查询被调用
        verify(configDetailService).findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_ACCURATE), eq(3), eq(5), isNull(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq(namespaceId), any());
    }
    
    @Test
    void testFetchOrderedNamespaceList() {
        // 模拟命名空间列表（无序）
        final List<com.alibaba.nacos.api.model.response.Namespace> namespaceList = new ArrayList<>();
        com.alibaba.nacos.api.model.response.Namespace namespace1 = new com.alibaba.nacos.api.model.response.Namespace();
        namespace1.setNamespace("b-namespace");
        com.alibaba.nacos.api.model.response.Namespace namespace2 = new com.alibaba.nacos.api.model.response.Namespace();
        namespace2.setNamespace("a-namespace");
        com.alibaba.nacos.api.model.response.Namespace namespace3 = new com.alibaba.nacos.api.model.response.Namespace();
        namespace3.setNamespace("c-namespace");
        namespaceList.add(namespace1);
        namespaceList.add(namespace2);
        namespaceList.add(namespace3);
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaceList);
        
        // 通过调用依赖该方法的函数来间接测试
        final String mcpId = "test-id-123";
        
        // 模拟缓存未命中
        when(cacheIndex.getMcpServerById(mcpId)).thenReturn(null);
        
        // 模拟数据库查询结果
        ConfigQueryChainResponse mockResponse = mock(ConfigQueryChainResponse.class);
        when(mockResponse.getStatus()).thenReturn(ConfigQueryChainResponse.ConfigQueryStatus.CONFIG_FOUND_FORMAL);
        when(configQueryChainService.handle(any(ConfigQueryChainRequest.class))).thenReturn(mockResponse);
        
        // 执行查询
        cachedIndex.getMcpServerById(mcpId);
        
        // 验证命名空间服务被调用
        verify(namespaceOperationService).getNamespaceList();
    }
    
    @Test
    void testMapMcpServerVersionConfigToIndexData() {
        // 通过调用依赖该方法的函数来间接测试
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
        
        // 验证结果，确保mapMcpServerVersionConfigToIndexData方法正确执行
        assertNotNull(result);
        assertEquals(mcpId, result.getId());
        assertEquals(namespaceId, result.getNamespaceId());
    }
    
    @Test
    void testTriggerCacheSyncWhenCacheDisabled() {
        // 创建禁用缓存的实例
        final CachedMcpServerIndex disabledIndex = new CachedMcpServerIndex(configDetailService,
                namespaceOperationService, configQueryChainService, cacheIndex, scheduledExecutor, false, 0);
        
        // 执行手动同步
        disabledIndex.triggerCacheSync();
        
        // 验证数据库查询没有被调用
        verify(configDetailService, never()).findConfigInfoPage(anyString(), anyInt(), anyInt(), anyString(),
                anyString(), anyString(), any());
    }
    
    @Test
    void testStartSyncTask() {
        when(scheduledExecutor.scheduleWithFixedDelay(any(Runnable.class), eq(10L), eq(10L), any(TimeUnit.class))).then(
                (Answer<ScheduledFuture<?>>) invocation -> {
                    invocation.getArgument(0, Runnable.class).run();
                    return null;
                });
        // 创建一个新的实例来测试startSyncTask方法
        new CachedMcpServerIndex(configDetailService, namespaceOperationService,
                configQueryChainService, cacheIndex, scheduledExecutor, true, 10);
        
        // 验证调度任务已启动
        verify(scheduledExecutor).scheduleWithFixedDelay(any(Runnable.class), eq(10L), eq(10L), any(TimeUnit.class));
        verify(namespaceOperationService).getNamespaceList();
    }
    
    @Test
    void testStartSyncTaskWithException() {
        when(scheduledExecutor.scheduleWithFixedDelay(any(Runnable.class), eq(10L), eq(10L), any(TimeUnit.class))).then(
                (Answer<ScheduledFuture<?>>) invocation -> {
                    invocation.getArgument(0, Runnable.class).run();
                    return null;
                });
        when(namespaceOperationService.getNamespaceList()).thenThrow(new RuntimeException("test"));
        // 创建一个新的实例来测试startSyncTask方法
        new CachedMcpServerIndex(configDetailService, namespaceOperationService,
                configQueryChainService, cacheIndex, scheduledExecutor, true, 10);
        
        // 验证调度任务已启动
        verify(scheduledExecutor).scheduleWithFixedDelay(any(Runnable.class), eq(10L), eq(10L), any(TimeUnit.class));
    }
    
    @Test
    void testDestroy() {
        // 模拟一个已经存在的任务
        when(scheduledExecutor.scheduleWithFixedDelay(any(Runnable.class), anyLong(), anyLong(),
                any(TimeUnit.class))).then((Answer<?>) invocation -> mock(ScheduledFuture.class));
        
        // 创建一个新的实例来测试destroy方法
        CachedMcpServerIndex indexToDestroy = new CachedMcpServerIndex(configDetailService, namespaceOperationService,
                configQueryChainService, cacheIndex, scheduledExecutor, true, 300);
        
        // 调用destroy方法
        indexToDestroy.destroy();
        
        // 验证调度任务被取消和线程池被关闭
        verify(scheduledExecutor).shutdown();
    }
    
    @Test
    void testDestroyWithExceptionHandling() {
        // 模拟scheduledExecutor.shutdown()抛出异常
        doThrow(new RuntimeException("Shutdown failed")).when(scheduledExecutor).shutdown();
        
        // 创建一个新的实例来测试destroy方法
        CachedMcpServerIndex indexToDestroy = new CachedMcpServerIndex(configDetailService, namespaceOperationService,
                configQueryChainService, cacheIndex, scheduledExecutor, true, 300);
        
        // 调用destroy方法不应该抛出异常
        indexToDestroy.destroy();
    }
    
    @Test
    void testSyncCacheFromDatabase() {
        // 模拟命名空间列表
        List<com.alibaba.nacos.api.model.response.Namespace> namespaceList = new ArrayList<>();
        com.alibaba.nacos.api.model.response.Namespace namespace1 = new com.alibaba.nacos.api.model.response.Namespace();
        namespace1.setNamespace("namespace-1");
        com.alibaba.nacos.api.model.response.Namespace namespace2 = new com.alibaba.nacos.api.model.response.Namespace();
        namespace2.setNamespace("namespace-2");
        namespaceList.add(namespace1);
        namespaceList.add(namespace2);
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaceList);
        
        // 模拟每个命名空间的搜索结果
        final Page<ConfigInfo> mockPage1 = new Page<>();
        List<ConfigInfo> configList1 = new ArrayList<>();
        ConfigInfo configInfo1 = new ConfigInfo();
        configInfo1.setDataId("server1" + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX);
        configInfo1.setTenant("namespace-1");
        configInfo1.setContent("{\"id\":\"server1\"}");
        configList1.add(configInfo1);
        mockPage1.setPageItems(configList1);
        mockPage1.setTotalCount(1);
        
        final Page<ConfigInfo> mockPage2 = new Page<>();
        List<ConfigInfo> configList2 = new ArrayList<>();
        ConfigInfo configInfo2 = new ConfigInfo();
        configInfo2.setDataId("server2" + Constants.MCP_SERVER_VERSION_DATA_ID_SUFFIX);
        configInfo2.setTenant("namespace-2");
        configInfo2.setContent("{\"id\":\"server2\"}");
        configList2.add(configInfo2);
        mockPage2.setPageItems(configList2);
        mockPage2.setTotalCount(1);
        
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1000), anyString(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespace-1"), any())).thenReturn(mockPage1);
        
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1000), anyString(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespace-2"), any())).thenReturn(mockPage2);
        
        // 调用syncCacheFromDatabase方法（通过triggerCacheSync）
        cachedIndex.triggerCacheSync();
        
        // 验证为每个命名空间调用了搜索
        verify(configDetailService).findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1000), anyString(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespace-1"), any());
        
        verify(configDetailService).findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1000), anyString(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespace-2"), any());
        
        // 验证缓存被更新。当 name=null 时，updateIndex 的第二个参数为 null
        verify(cacheIndex).updateIndex(eq("namespace-1"), isNull(), eq("server1"));
        verify(cacheIndex).updateIndex(eq("namespace-2"), isNull(), eq("server2"));
    }
    
    @Test
    void testSyncCacheFromDatabaseWithSearchException() {
        // 模拟命名空间列表
        List<com.alibaba.nacos.api.model.response.Namespace> namespaceList = new ArrayList<>();
        com.alibaba.nacos.api.model.response.Namespace namespace = new com.alibaba.nacos.api.model.response.Namespace();
        namespace.setNamespace("namespace-1");
        namespaceList.add(namespace);
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaceList);
        
        // 模拟搜索时抛出异常
        when(configDetailService.findConfigInfoPage(anyString(), anyInt(), anyInt(), anyString(), anyString(),
                anyString(), any())).thenThrow(new RuntimeException("Database error"));
        
        // 调用syncCacheFromDatabase方法（通过triggerCacheSync）
        cachedIndex.triggerCacheSync();
        
        // 即使出现异常也应该继续执行而不会中断
        verify(configDetailService).findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1000), anyString(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespace-1"), any());
    }
    
    @Test
    void testSyncCacheFromDatabaseWithEmptyResult() {
        // 模拟命名空间列表
        List<com.alibaba.nacos.api.model.response.Namespace> namespaceList = new ArrayList<>();
        com.alibaba.nacos.api.model.response.Namespace namespace = new com.alibaba.nacos.api.model.response.Namespace();
        namespace.setNamespace("namespace-1");
        namespaceList.add(namespace);
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaceList);
        
        // 模拟空的搜索结果
        Page<ConfigInfo> mockPage = new Page<>();
        mockPage.setPageItems(new ArrayList<>());
        mockPage.setTotalCount(0);
        
        when(configDetailService.findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1000), anyString(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespace-1"), any())).thenReturn(mockPage);
        
        // 调用syncCacheFromDatabase方法（通过triggerCacheSync）
        cachedIndex.triggerCacheSync();
        
        // 验证搜索被调用但缓存未更新
        verify(configDetailService).findConfigInfoPage(eq(Constants.MCP_LIST_SEARCH_BLUR), eq(1), eq(1000), anyString(),
                eq(Constants.MCP_SERVER_VERSIONS_GROUP), eq("namespace-1"), any());
        
        // 没有数据所以不需要更新缓存
        verify(cacheIndex, never()).updateIndex(anyString(), anyString(), anyString());
    }
    
    @Test
    void testSyncCacheFromDatabaseWithException() {
        // 模拟命名空间列表
        List<com.alibaba.nacos.api.model.response.Namespace> namespaceList = new ArrayList<>();
        com.alibaba.nacos.api.model.response.Namespace namespace = new com.alibaba.nacos.api.model.response.Namespace();
        namespace.setNamespace("test-namespace");
        namespaceList.add(namespace);
        when(namespaceOperationService.getNamespaceList()).thenReturn(namespaceList);
        
        // 模拟搜索时抛出异常
        when(configDetailService.findConfigInfoPage(anyString(), anyInt(), anyInt(), anyString(), anyString(),
                anyString(), any())).thenThrow(new RuntimeException("Test exception"));
        
        // 通过调用triggerCacheSync来触发syncCacheFromDatabase
        cachedIndex.triggerCacheSync();
        
        // 验证异常被处理，不会导致程序崩溃
        verify(namespaceOperationService).getNamespaceList();
    }
}