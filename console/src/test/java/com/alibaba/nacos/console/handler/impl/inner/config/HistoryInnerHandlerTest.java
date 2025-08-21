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

package com.alibaba.nacos.console.handler.impl.inner.config;

import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryBasicInfo;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.HistoryService;
import com.alibaba.nacos.plugin.auth.exception.AccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryInnerHandlerTest {
    
    @Mock
    HistoryService historyService;
    
    HistoryInnerHandler historyInnerHandler;
    
    @BeforeEach
    void setUp() {
        historyInnerHandler = new HistoryInnerHandler(historyService);
    }
    
    @AfterEach
    void tearDown() {
    }
    
    @Test
    void getConfigHistoryInfo() throws NacosException {
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        injectMockDataToHistoryInfo(configHistoryInfo);
        when(historyService.getConfigHistoryInfo("dataId", "group", "tenant", 1L)).thenReturn(configHistoryInfo);
        assertNotNull(historyInnerHandler.getConfigHistoryInfo("dataId", "group", "tenant", 1L));
    }
    
    @Test
    void getConfigHistoryInfoNotFound() throws NacosException {
        when(historyService.getConfigHistoryInfo("dataId", "group", "tenant", 1L)).thenThrow(
                new EmptyResultDataAccessException(1));
        assertThrows(NacosApiException.class,
                () -> historyInnerHandler.getConfigHistoryInfo("dataId", "group", "tenant", 1L),
                "certain config history for nid = 1 not exist");
    }
    
    @Test
    void listConfigHistory() throws NacosException {
        Page<ConfigHistoryInfo> mockPage = new Page<>();
        mockPage.setPageNumber(1);
        mockPage.setPagesAvailable(1);
        mockPage.setTotalCount(1);
        ConfigHistoryInfo mockConfigHistoryInfo = new ConfigHistoryInfo();
        injectMockDataToHistoryInfo(mockConfigHistoryInfo);
        mockPage.setPageItems(Collections.singletonList(mockConfigHistoryInfo));
        when(historyService.listConfigHistory("dataId", "group", "tenant", 1, 1)).thenReturn(mockPage);
        Page<ConfigHistoryBasicInfo> actual = historyInnerHandler.listConfigHistory("dataId", "group", "tenant", 1, 1);
        assertNotNull(actual);
        assertEquals(1, actual.getPageNumber());
        assertEquals(1, actual.getPagesAvailable());
        assertEquals(1, actual.getTotalCount());
        assertEquals(1, actual.getPageItems().size());
        assertEquals(mockConfigHistoryInfo.getId(), actual.getPageItems().get(0).getId());
        assertEquals(mockConfigHistoryInfo.getDataId(), actual.getPageItems().get(0).getDataId());
        assertEquals(mockConfigHistoryInfo.getGroup(), actual.getPageItems().get(0).getGroupName());
        assertEquals(mockConfigHistoryInfo.getTenant(), actual.getPageItems().get(0).getNamespaceId());
    }
    
    @Test
    void getPreviousConfigHistoryInfo() throws NacosException {
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        injectMockDataToHistoryInfo(configHistoryInfo);
        when(historyService.getPreviousConfigHistoryInfo("dataId", "group", "tenant", 1L)).thenReturn(
                configHistoryInfo);
        assertNotNull(historyInnerHandler.getPreviousConfigHistoryInfo("dataId", "group", "tenant", 1L));
    }
    
    @Test
    void getPreviousConfigHistoryInfoNotFound() throws AccessException {
        when(historyService.getPreviousConfigHistoryInfo("dataId", "group", "tenant", 1L)).thenThrow(
                new EmptyResultDataAccessException(1));
        assertThrows(NacosApiException.class,
                () -> historyInnerHandler.getPreviousConfigHistoryInfo("dataId", "group", "tenant", 1L),
                "previous config history for id = 1 not exist");
    }
    
    @Test
    void getConfigsByTenant() {
        ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
        configInfoWrapper.setId(1L);
        configInfoWrapper.setDataId("dataId");
        configInfoWrapper.setGroup("group");
        configInfoWrapper.setTenant("tenant");
        when(historyService.getConfigListByNamespace("tenant")).thenReturn(
                Collections.singletonList(configInfoWrapper));
        List<ConfigBasicInfo> actual = historyInnerHandler.getConfigsByTenant("tenant");
        assertEquals(1, actual.size());
        assertEquals(configInfoWrapper.getId(), actual.get(0).getId());
        assertEquals(configInfoWrapper.getDataId(), actual.get(0).getDataId());
        assertEquals(configInfoWrapper.getGroup(), actual.get(0).getGroupName());
        assertEquals(configInfoWrapper.getTenant(), actual.get(0).getNamespaceId());
    }
    
    private void injectMockDataToHistoryInfo(ConfigHistoryInfo configHistoryInfo) {
        configHistoryInfo.setId(1L);
        configHistoryInfo.setDataId("dataId");
        configHistoryInfo.setGroup("group");
        configHistoryInfo.setTenant("tenant");
        configHistoryInfo.setContent("content");
        configHistoryInfo.setSrcIp("srcIp");
        configHistoryInfo.setSrcUser("srcUser");
        configHistoryInfo.setOpType("opType");
        configHistoryInfo.setPublishType("publishType");
        configHistoryInfo.setGrayName("grayName");
        configHistoryInfo.setExtInfo("extInfo");
        configHistoryInfo.setCreatedTime(new java.sql.Timestamp(System.currentTimeMillis()));
        configHistoryInfo.setLastModifiedTime(new java.sql.Timestamp(System.currentTimeMillis()));
    }
}