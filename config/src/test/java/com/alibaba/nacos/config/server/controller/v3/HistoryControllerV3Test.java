/*
 * Copyright 1999-$toady.year Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.controller.v3;

import com.alibaba.nacos.api.config.model.ConfigBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryBasicInfo;
import com.alibaba.nacos.api.config.model.ConfigHistoryDetailInfo;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.model.form.ConfigFormV3;
import com.alibaba.nacos.config.server.service.HistoryService;
import com.alibaba.nacos.core.model.form.PageForm;
import com.alibaba.nacos.api.model.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryControllerV3Test {
    
    private static final String TEST_DATA_ID = "test";
    
    private static final String TEST_GROUP = "test";
    
    private static final String TEST_NAMESPACE_ID = "";
    
    private static final String TEST_NAMESPACE_ID_PUBLIC = "public";
    
    private static final String TEST_CONTENT = "test config";
    
    HistoryControllerV3 historyControllerV3;
    
    @Mock
    private HistoryService historyService;
    
    @BeforeEach
    void setUp() {
        historyControllerV3 = new HistoryControllerV3(historyService);
    }
    
    @Test
    void testListConfigHistory() throws Exception {
        
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId(TEST_DATA_ID);
        configHistoryInfo.setGroup(TEST_GROUP);
        configHistoryInfo.setContent(TEST_CONTENT);
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));
        List<ConfigHistoryInfo> configHistoryInfoList = new ArrayList<>();
        configHistoryInfoList.add(configHistoryInfo);
        
        Page<ConfigHistoryInfo> page = new Page<>();
        page.setTotalCount(15);
        page.setPageNumber(1);
        page.setPagesAvailable(2);
        page.setPageItems(configHistoryInfoList);
        
        when(historyService.listConfigHistory(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID_PUBLIC, 1, 10)).thenReturn(
                page);
        
        ConfigFormV3 configForm = new ConfigFormV3();
        configForm.setDataId(TEST_DATA_ID);
        configForm.setGroupName(TEST_GROUP);
        configForm.setNamespaceId(TEST_NAMESPACE_ID);
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(1);
        pageForm.setPageSize(10);
        Result<Page<ConfigHistoryBasicInfo>> pageResult = historyControllerV3.listConfigHistory(configForm, pageForm);
        
        verify(historyService).listConfigHistory(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID_PUBLIC, 1, 10);
        
        List<ConfigHistoryBasicInfo> resultList = pageResult.getData().getPageItems();
        ConfigHistoryBasicInfo resConfigHistoryInfo = resultList.get(0);
        
        assertEquals(ErrorCode.SUCCESS.getCode(), pageResult.getCode());
        assertEquals(configHistoryInfoList.size(), resultList.size());
        assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroupName());
        assertEquals(configHistoryInfo.getTenant(), resConfigHistoryInfo.getNamespaceId());
        
    }
    
    @Test
    void testListConfigHistoryWhenNameSpaceIsPublic() throws Exception {
        
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId(TEST_DATA_ID);
        configHistoryInfo.setGroup(TEST_GROUP);
        configHistoryInfo.setContent(TEST_CONTENT);
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));
        List<ConfigHistoryInfo> configHistoryInfoList = new ArrayList<>();
        configHistoryInfoList.add(configHistoryInfo);
        
        Page<ConfigHistoryInfo> page = new Page<>();
        page.setTotalCount(15);
        page.setPageNumber(1);
        page.setPagesAvailable(2);
        page.setPageItems(configHistoryInfoList);
        
        when(historyService.listConfigHistory(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID_PUBLIC, 1, 10)).thenReturn(
                page);
        
        ConfigFormV3 configForm = new ConfigFormV3();
        configForm.setDataId(TEST_DATA_ID);
        configForm.setGroupName(TEST_GROUP);
        configForm.setNamespaceId(TEST_NAMESPACE_ID_PUBLIC);
        PageForm pageForm = new PageForm();
        pageForm.setPageNo(1);
        pageForm.setPageSize(10);
        
        Result<Page<ConfigHistoryBasicInfo>> pageResult = historyControllerV3.listConfigHistory(configForm, pageForm);
        
        verify(historyService).listConfigHistory(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID_PUBLIC, 1, 10);
        
        List<ConfigHistoryBasicInfo> resultList = pageResult.getData().getPageItems();
        ConfigHistoryBasicInfo resConfigHistoryInfo = resultList.get(0);
        
        assertEquals(ErrorCode.SUCCESS.getCode(), pageResult.getCode());
        assertEquals(configHistoryInfoList.size(), resultList.size());
        assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroupName());
        
    }
    
    @Test
    void testGetConfigHistoryInfoWhenNameSpaceIsPublic() throws Exception {
        
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId(TEST_DATA_ID);
        configHistoryInfo.setGroup(TEST_GROUP);
        configHistoryInfo.setContent(TEST_CONTENT);
        configHistoryInfo.setTenant(TEST_NAMESPACE_ID);
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));
        
        when(historyService.getConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID_PUBLIC, 1L)).thenReturn(
                configHistoryInfo);
        
        ConfigFormV3 configForm = new ConfigFormV3();
        configForm.setDataId(TEST_DATA_ID);
        configForm.setGroupName(TEST_GROUP);
        configForm.setNamespaceId(TEST_NAMESPACE_ID_PUBLIC);
        Result<ConfigHistoryDetailInfo> result = historyControllerV3.getConfigHistoryInfo(configForm, 1L);
        
        verify(historyService).getConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID_PUBLIC, 1L);
        
        ConfigHistoryDetailInfo resConfigHistoryInfo = result.getData();
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroupName());
        assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
        
    }
    
    @Test
    void testGetConfigHistoryInfo() throws Exception {
        
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId(TEST_DATA_ID);
        configHistoryInfo.setGroup(TEST_GROUP);
        configHistoryInfo.setContent(TEST_CONTENT);
        configHistoryInfo.setTenant(TEST_NAMESPACE_ID);
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));
        
        when(historyService.getConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID_PUBLIC, 1L)).thenReturn(
                configHistoryInfo);
        
        ConfigFormV3 configForm = new ConfigFormV3();
        configForm.setDataId(TEST_DATA_ID);
        configForm.setGroupName(TEST_GROUP);
        configForm.setNamespaceId(TEST_NAMESPACE_ID);
        Result<ConfigHistoryDetailInfo> result = historyControllerV3.getConfigHistoryInfo(configForm, 1L);
        
        verify(historyService).getConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID_PUBLIC, 1L);
        
        ConfigHistoryDetailInfo resConfigHistoryInfo = result.getData();
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroupName());
        assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
        
    }
    
    @Test
    void testGetPreviousConfigHistoryInfo() throws Exception {
        
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId(TEST_DATA_ID);
        configHistoryInfo.setGroup(TEST_GROUP);
        configHistoryInfo.setContent(TEST_CONTENT);
        configHistoryInfo.setTenant(TEST_NAMESPACE_ID);
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));
        
        when(historyService.getPreviousConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID_PUBLIC,
                1L)).thenReturn(configHistoryInfo);
        
        ConfigFormV3 configForm = new ConfigFormV3();
        configForm.setDataId(TEST_DATA_ID);
        configForm.setGroupName(TEST_GROUP);
        configForm.setNamespaceId(TEST_NAMESPACE_ID);
        Result<ConfigHistoryDetailInfo> result = historyControllerV3.getPreviousConfigHistoryInfo(configForm, 1L);
        
        verify(historyService).getPreviousConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID_PUBLIC, 1L);
        
        ConfigHistoryDetailInfo resConfigHistoryInfo = result.getData();
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroupName());
        assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
        
    }
    
    @Test
    void testGetPreviousConfigHistoryInfoWhenNameSpaceIsPublic() throws Exception {
        
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId(TEST_DATA_ID);
        configHistoryInfo.setGroup(TEST_GROUP);
        configHistoryInfo.setContent(TEST_CONTENT);
        configHistoryInfo.setTenant(TEST_NAMESPACE_ID);
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));
        
        when(historyService.getPreviousConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID_PUBLIC,
                1L)).thenReturn(configHistoryInfo);
        
        ConfigFormV3 configForm = new ConfigFormV3();
        configForm.setDataId(TEST_DATA_ID);
        configForm.setGroupName(TEST_GROUP);
        configForm.setNamespaceId(TEST_NAMESPACE_ID_PUBLIC);
        Result<ConfigHistoryDetailInfo> result = historyControllerV3.getPreviousConfigHistoryInfo(configForm, 1L);
        
        verify(historyService).getPreviousConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID_PUBLIC, 1L);
        
        ConfigHistoryDetailInfo resConfigHistoryInfo = result.getData();
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroupName());
        assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
        
    }
    
    @Test
    void testGetConfigListByNamespace() throws NacosApiException {
        ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
        configInfoWrapper.setDataId("test");
        configInfoWrapper.setGroup("test");
        configInfoWrapper.setContent("test");
        List<ConfigInfoWrapper> configInfoWrappers = Collections.singletonList(configInfoWrapper);
        
        when(historyService.getConfigListByNamespace("test")).thenReturn(configInfoWrappers);
        Result<List<ConfigBasicInfo>> result = historyControllerV3.getConfigsByNamespace("test");
        verify(historyService).getConfigListByNamespace("test");
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        List<ConfigBasicInfo> actualList = result.getData();
        assertEquals(configInfoWrappers.size(), actualList.size());
        ConfigBasicInfo actualConfigInfoWrapper = actualList.get(0);
        assertEquals(configInfoWrapper.getDataId(), actualConfigInfoWrapper.getDataId());
        assertEquals(configInfoWrapper.getGroup(), actualConfigInfoWrapper.getGroupName());
    }
    
    @Test
    void testGetConfigListByNamespaceWhenIsPublic() throws NacosApiException {
        ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
        configInfoWrapper.setDataId("test");
        configInfoWrapper.setGroup("test");
        configInfoWrapper.setContent("test");
        List<ConfigInfoWrapper> configInfoWrappers = Collections.singletonList(configInfoWrapper);
        
        when(historyService.getConfigListByNamespace(TEST_NAMESPACE_ID_PUBLIC)).thenReturn(configInfoWrappers);
        Result<List<ConfigBasicInfo>> result = historyControllerV3.getConfigsByNamespace(TEST_NAMESPACE_ID_PUBLIC);
        verify(historyService).getConfigListByNamespace(TEST_NAMESPACE_ID_PUBLIC);
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        List<ConfigBasicInfo> actualList = result.getData();
        assertEquals(configInfoWrappers.size(), actualList.size());
        ConfigBasicInfo actualConfigInfoWrapper = actualList.get(0);
        assertEquals(configInfoWrapper.getDataId(), actualConfigInfoWrapper.getDataId());
        assertEquals(configInfoWrapper.getGroup(), actualConfigInfoWrapper.getGroupName());
    }
}