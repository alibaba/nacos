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

package com.alibaba.nacos.console.handler.impl.remote.config;

import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryDetailInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.console.handler.impl.remote.AbstractRemoteHandlerTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class HistoryRemoteHandlerTest extends AbstractRemoteHandlerTest {
    
    HistoryRemoteHandler historyRemoteHandler;
    
    @BeforeEach
    void setUp() {
        super.setUpWithConfig();
        historyRemoteHandler = new HistoryRemoteHandler(clientHolder);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void getConfigHistoryInfo() throws NacosException {
        ConfigHistoryDetailInfo mock = new ConfigHistoryDetailInfo();
        when(configMaintainerService.getConfigHistoryInfo("dataId", "group", "namespaceId", 1L)).thenReturn(mock);
        ConfigHistoryDetailInfo result = historyRemoteHandler.getConfigHistoryInfo("dataId", "group", "namespaceId",
                1L);
        assertEquals(mock, result);
    }
    
    @Test
    void listConfigHistory() throws NacosException {
        Page<ConfigHistoryBasicInfo> mockPage = new Page<>();
        when(configMaintainerService.listConfigHistory("dataId", "group", "namespaceId", 1, 10)).thenReturn(mockPage);
        Page<ConfigHistoryBasicInfo> result = historyRemoteHandler.listConfigHistory("dataId", "group", "namespaceId",
                1, 10);
        assertEquals(mockPage, result);
    }
    
    @Test
    void getPreviousConfigHistoryInfo() throws NacosException {
        ConfigHistoryDetailInfo mock = new ConfigHistoryDetailInfo();
        when(configMaintainerService.getPreviousConfigHistoryInfo("dataId", "group", "namespaceId", 1L)).thenReturn(
                mock);
        ConfigHistoryDetailInfo result = historyRemoteHandler.getPreviousConfigHistoryInfo("dataId", "group",
                "namespaceId", 1L);
        assertEquals(mock, result);
    }
    
    @Test
    void getConfigsByTenant() throws NacosException {
        List<ConfigBasicInfo> mockList = List.of(new ConfigBasicInfo());
        when(configMaintainerService.getConfigListByNamespace("namespaceId")).thenReturn(mockList);
        List<ConfigBasicInfo> result = historyRemoteHandler.getConfigsByTenant("namespaceId");
        assertEquals(mockList, result);
    }
}