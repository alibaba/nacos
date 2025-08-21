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

package com.alibaba.nacos.console.proxy.config;

import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.config.HistoryHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HistoryProxyTest {
    
    @Mock
    private HistoryHandler historyHandler;
    
    private HistoryProxy historyProxy;
    
    private static final String DATA_ID = "dataId";
    
    private static final String GROUP = "group";
    
    private static final String NAMESPACE_ID = "namespaceId";
    
    private static final int PAGE_NO = 1;
    
    private static final int PAGE_SIZE = 10;
    
    @BeforeEach
    public void setUp() {
        historyProxy = new HistoryProxy(historyHandler);
    }
    
    @Test
    public void getConfigHistoryInfo() throws NacosException {
        ConfigHistoryDetailInfo expectedInfo = new ConfigHistoryDetailInfo();
        expectedInfo.setDataId("testDataId");
        expectedInfo.setGroupName("testGroup");
        expectedInfo.setNamespaceId("testNamespace");
        expectedInfo.setId(1L);
        
        when(historyHandler.getConfigHistoryInfo(anyString(), anyString(), anyString(), anyLong())).thenReturn(
                expectedInfo);
        
        ConfigHistoryDetailInfo actualInfo = historyProxy.getConfigHistoryInfo("testDataId", "testGroup",
                "testNamespace", 1L);
        
        assertEquals(expectedInfo.getDataId(), actualInfo.getDataId());
        assertEquals(expectedInfo.getGroupName(), actualInfo.getGroupName());
        assertEquals(expectedInfo.getNamespaceId(), actualInfo.getNamespaceId());
        assertEquals(expectedInfo.getId(), actualInfo.getId());
    }
    
    @Test
    public void getPreviousConfigHistoryInfo() throws NacosException {
        // 准备
        String dataId = "testDataId";
        String group = "testGroup";
        String namespaceId = "testNamespaceId";
        Long id = 1L;
        ConfigHistoryDetailInfo expectedInfo = new ConfigHistoryDetailInfo();
        expectedInfo.setDataId(dataId);
        expectedInfo.setGroupName(group);
        expectedInfo.setNamespaceId(namespaceId);
        expectedInfo.setId(id);
        
        when(historyHandler.getPreviousConfigHistoryInfo(dataId, group, namespaceId, id)).thenReturn(expectedInfo);
        
        // 测试
        ConfigHistoryDetailInfo actualInfo = historyProxy.getPreviousConfigHistoryInfo(dataId, group, namespaceId, id);
        
        // 验证
        assertEquals(expectedInfo, actualInfo);
    }
    
    @Test
    public void listConfigHistory() throws NacosException {
        Page<ConfigHistoryBasicInfo> expectedPage = new Page<>();
        List<ConfigHistoryBasicInfo> pageItems = new ArrayList<>();
        ConfigHistoryBasicInfo configHistoryBasicInfo = new ConfigHistoryBasicInfo();
        pageItems.add(configHistoryBasicInfo);
        expectedPage.setPageItems(pageItems);
        expectedPage.setPageNumber(PAGE_NO);
        expectedPage.setPagesAvailable(1);
        expectedPage.setTotalCount(1);
        
        when(historyHandler.listConfigHistory(DATA_ID, GROUP, NAMESPACE_ID, PAGE_NO, PAGE_SIZE)).thenReturn(
                expectedPage);
        
        Page<ConfigHistoryBasicInfo> result = historyProxy.listConfigHistory(DATA_ID, GROUP, NAMESPACE_ID, PAGE_NO,
                PAGE_SIZE);
        
        assertEquals(expectedPage, result);
        verify(historyHandler, times(1)).listConfigHistory(DATA_ID, GROUP, NAMESPACE_ID, PAGE_NO, PAGE_SIZE);
    }
    
    @Test
    public void getConfigsByTenant() throws NacosException {
        // 准备
        String namespaceId = "testNamespace";
        ConfigBasicInfo config1 = new ConfigBasicInfo();
        config1.setDataId("dataId1");
        ConfigBasicInfo config2 = new ConfigBasicInfo();
        config2.setDataId("dataId2");
        List<ConfigBasicInfo> expectedConfigs = Arrays.asList(config1, config2);
        
        when(historyHandler.getConfigsByTenant(namespaceId)).thenReturn(expectedConfigs);
        
        // 执行
        List<ConfigBasicInfo> actualConfigs = historyProxy.getConfigsByTenant(namespaceId);
        
        // 断言
        assertEquals(expectedConfigs, actualConfigs);
    }
    
}
