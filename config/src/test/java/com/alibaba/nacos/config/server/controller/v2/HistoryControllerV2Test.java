/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.controller.v2;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.model.v2.Result;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.config.server.service.HistoryService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HistoryV2ControllerTest.
 *
 * @author dongyafei
 * @date 2022/7/25
 */

@RunWith(MockitoJUnitRunner.class)
public class HistoryControllerV2Test {
    
    HistoryControllerV2 historyControllerV2;
    
    @Mock
    private HistoryService historyService;
    
    private static final String TEST_DATA_ID = "test";
    
    private static final String TEST_GROUP = "test";
    
    private static final String TEST_NAMESPACE_ID = "";
    
    private static final String TEST_NAMESPACE_ID_PUBLIC = "public";
    
    private static final String TEST_CONTENT = "test config";
    
    @Before
    public void setUp() {
        historyControllerV2 = new HistoryControllerV2(historyService);
    }
    
    @Test
    public void testListConfigHistory() throws Exception {
        
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
        
        when(historyService.listConfigHistory(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID, 1, 10)).thenReturn(page);
        
        Result<Page<ConfigHistoryInfo>> pageResult = historyControllerV2.listConfigHistory(TEST_DATA_ID, TEST_GROUP,
                TEST_NAMESPACE_ID, 1, 10);
        
        verify(historyService).listConfigHistory(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID, 1, 10);
        
        List<ConfigHistoryInfo> resultList = pageResult.getData().getPageItems();
        ConfigHistoryInfo resConfigHistoryInfo = resultList.get(0);
        
        assertEquals(ErrorCode.SUCCESS.getCode(), pageResult.getCode());
        assertEquals(configHistoryInfoList.size(), resultList.size());
        assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroup());
        assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
        
    }
    
    @Test
    public void testListConfigHistoryWhenNameSpaceIsPublic() throws Exception {
        
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
        
        when(historyService.listConfigHistory(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID, 1, 10)).thenReturn(page);
        
        Result<Page<ConfigHistoryInfo>> pageResult = historyControllerV2.listConfigHistory(TEST_DATA_ID, TEST_GROUP,
                TEST_NAMESPACE_ID_PUBLIC, 1, 10);
        
        verify(historyService).listConfigHistory(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID, 1, 10);
        
        List<ConfigHistoryInfo> resultList = pageResult.getData().getPageItems();
        ConfigHistoryInfo resConfigHistoryInfo = resultList.get(0);
        
        assertEquals(ErrorCode.SUCCESS.getCode(), pageResult.getCode());
        assertEquals(configHistoryInfoList.size(), resultList.size());
        assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroup());
        assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
        
    }
    
    @Test
    public void testGetConfigHistoryInfoWhenNameSpaceIsPublic() throws Exception {
        
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId(TEST_DATA_ID);
        configHistoryInfo.setGroup(TEST_GROUP);
        configHistoryInfo.setContent(TEST_CONTENT);
        configHistoryInfo.setTenant(TEST_NAMESPACE_ID);
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));
        
        when(historyService.getConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID, 1L)).thenReturn(
                configHistoryInfo);
        
        Result<ConfigHistoryInfo> result = historyControllerV2.getConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP,
                TEST_NAMESPACE_ID_PUBLIC, 1L);
        
        verify(historyService).getConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID, 1L);
        
        ConfigHistoryInfo resConfigHistoryInfo = result.getData();
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroup());
        assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
        
    }
    
    @Test
    public void testGetConfigHistoryInfo() throws Exception {
        
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId(TEST_DATA_ID);
        configHistoryInfo.setGroup(TEST_GROUP);
        configHistoryInfo.setContent(TEST_CONTENT);
        configHistoryInfo.setTenant(TEST_NAMESPACE_ID);
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));
        
        when(historyService.getConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID, 1L)).thenReturn(
                configHistoryInfo);
        
        Result<ConfigHistoryInfo> result = historyControllerV2.getConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP,
                TEST_NAMESPACE_ID, 1L);
        
        verify(historyService).getConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID, 1L);
        
        ConfigHistoryInfo resConfigHistoryInfo = result.getData();
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroup());
        assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
        
    }
    
    @Test
    public void testGetPreviousConfigHistoryInfo() throws Exception {
        
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId(TEST_DATA_ID);
        configHistoryInfo.setGroup(TEST_GROUP);
        configHistoryInfo.setContent(TEST_CONTENT);
        configHistoryInfo.setTenant(TEST_NAMESPACE_ID);
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));
        
        when(historyService.getPreviousConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID, 1L)).thenReturn(
                configHistoryInfo);
        
        Result<ConfigHistoryInfo> result = historyControllerV2.getPreviousConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP,
                TEST_NAMESPACE_ID, 1L);
        
        verify(historyService).getPreviousConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID, 1L);
        
        ConfigHistoryInfo resConfigHistoryInfo = result.getData();
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroup());
        assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
        
    }
    
    @Test
    public void testGetPreviousConfigHistoryInfoWhenNameSpaceIsPublic() throws Exception {
        
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId(TEST_DATA_ID);
        configHistoryInfo.setGroup(TEST_GROUP);
        configHistoryInfo.setContent(TEST_CONTENT);
        configHistoryInfo.setTenant(TEST_NAMESPACE_ID);
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));
        
        when(historyService.getPreviousConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID, 1L)).thenReturn(
                configHistoryInfo);
        
        Result<ConfigHistoryInfo> result = historyControllerV2.getPreviousConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP,
                TEST_NAMESPACE_ID_PUBLIC, 1L);
        
        verify(historyService).getPreviousConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_NAMESPACE_ID, 1L);
        
        ConfigHistoryInfo resConfigHistoryInfo = result.getData();
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroup());
        assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
        
    }
    
    @Test
    public void testGetConfigListByNamespace() throws NacosApiException {
        ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
        configInfoWrapper.setDataId("test");
        configInfoWrapper.setGroup("test");
        configInfoWrapper.setContent("test");
        List<ConfigInfoWrapper> configInfoWrappers = Collections.singletonList(configInfoWrapper);
        
        when(historyService.getConfigListByNamespace("test")).thenReturn(configInfoWrappers);
        Result<List<ConfigInfoWrapper>> result = historyControllerV2.getConfigsByTenant("test");
        verify(historyService).getConfigListByNamespace("test");
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        List<ConfigInfoWrapper> actualList = result.getData();
        assertEquals(configInfoWrappers.size(), actualList.size());
        ConfigInfoWrapper actualConfigInfoWrapper = actualList.get(0);
        assertEquals(configInfoWrapper.getDataId(), actualConfigInfoWrapper.getDataId());
        assertEquals(configInfoWrapper.getGroup(), actualConfigInfoWrapper.getGroup());
        assertEquals(configInfoWrapper.getContent(), actualConfigInfoWrapper.getContent());
    }
    
    @Test
    public void testGetConfigListByNamespaceWhenIsPublic() throws NacosApiException {
        ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
        configInfoWrapper.setDataId("test");
        configInfoWrapper.setGroup("test");
        configInfoWrapper.setContent("test");
        List<ConfigInfoWrapper> configInfoWrappers = Collections.singletonList(configInfoWrapper);
        
        when(historyService.getConfigListByNamespace(TEST_NAMESPACE_ID)).thenReturn(configInfoWrappers);
        Result<List<ConfigInfoWrapper>> result = historyControllerV2.getConfigsByTenant(TEST_NAMESPACE_ID_PUBLIC);
        verify(historyService).getConfigListByNamespace(TEST_NAMESPACE_ID);
        
        assertEquals(ErrorCode.SUCCESS.getCode(), result.getCode());
        List<ConfigInfoWrapper> actualList = result.getData();
        assertEquals(configInfoWrappers.size(), actualList.size());
        ConfigInfoWrapper actualConfigInfoWrapper = actualList.get(0);
        assertEquals(configInfoWrapper.getDataId(), actualConfigInfoWrapper.getDataId());
        assertEquals(configInfoWrapper.getGroup(), actualConfigInfoWrapper.getGroup());
        assertEquals(configInfoWrapper.getContent(), actualConfigInfoWrapper.getContent());
    }
}
