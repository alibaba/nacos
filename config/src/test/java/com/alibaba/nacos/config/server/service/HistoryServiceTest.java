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

package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.enums.OperationType;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigHistoryInfoDetail;
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoGrayPersistService;
import com.alibaba.nacos.config.server.service.repository.ConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.persistence.model.Page;
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

/**
 * HistoryServiceTest.
 *
 * @author dongyafei
 * @date 2022/8/11
 */

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {
    
    private static final String TEST_DATA_ID = "test";
    
    private static final String TEST_GROUP = "test";
    
    private static final String TEST_TENANT = "";
    
    private static final String TEST_CONTENT = "test config";

    private static final String TEST_UPDATED_CONTENT = "test config updated";

    private static final String TEST_OP_TYPE = OperationType.UPDATE.getValue();

    private static final String TEST_MD5 = "77963b7a931377ad4ab5ad6a9cd718aa";

    private static final String TEST_UPDATED_MD5 = "3ba1e44fa18519221f6c70afc0e8ae84";
    
    private HistoryService historyService;
    
    @Mock
    private HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    @Mock
    private ConfigInfoPersistService configInfoPersistService;

    @Mock
    private ConfigInfoGrayPersistService configInfoGrayPersistService;
    
    @BeforeEach
    void setUp() throws Exception {
        this.historyService = new HistoryService(historyConfigInfoPersistService, configInfoPersistService, configInfoGrayPersistService);
    }
    
    @Test
    void testListConfigHistory() {
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
        
        when(historyConfigInfoPersistService.findConfigHistory(TEST_DATA_ID, TEST_GROUP, TEST_TENANT, 1, 10)).thenReturn(page);
        
        Page<ConfigHistoryInfo> pageResult = historyService.listConfigHistory(TEST_DATA_ID, TEST_GROUP, TEST_TENANT, 1, 10);
        
        verify(historyConfigInfoPersistService).findConfigHistory(TEST_DATA_ID, TEST_GROUP, TEST_TENANT, 1, 10);
        
        List<ConfigHistoryInfo> resultList = pageResult.getPageItems();
        ConfigHistoryInfo resConfigHistoryInfo = resultList.get(0);
        
        assertEquals(configHistoryInfoList.size(), resultList.size());
        assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroup());
        assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
    }
    
    @Test
    void testGetConfigHistoryInfo() throws Exception {
        
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId(TEST_DATA_ID);
        configHistoryInfo.setGroup(TEST_GROUP);
        configHistoryInfo.setContent(TEST_CONTENT);
        configHistoryInfo.setTenant(TEST_TENANT);
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));
        
        when(historyConfigInfoPersistService.detailConfigHistory(1L)).thenReturn(configHistoryInfo);
        
        ConfigHistoryInfo resConfigHistoryInfo = historyService.getConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_TENANT, 1L);
        
        verify(historyConfigInfoPersistService).detailConfigHistory(1L);
        
        assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroup());
        assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
        
    }
    
    @Test
    void testGetPreviousConfigHistoryInfo() throws Exception {
        
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId(TEST_DATA_ID);
        configHistoryInfo.setGroup(TEST_GROUP);
        configHistoryInfo.setContent(TEST_CONTENT);
        configHistoryInfo.setTenant(TEST_TENANT);
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));
        
        when(historyConfigInfoPersistService.detailPreviousConfigHistory(1L)).thenReturn(configHistoryInfo);
        
        ConfigHistoryInfo resConfigHistoryInfo = historyService.getPreviousConfigHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_TENANT, 1L);
        
        verify(historyConfigInfoPersistService).detailPreviousConfigHistory(1L);
        
        assertEquals(configHistoryInfo.getDataId(), resConfigHistoryInfo.getDataId());
        assertEquals(configHistoryInfo.getGroup(), resConfigHistoryInfo.getGroup());
        assertEquals(configHistoryInfo.getContent(), resConfigHistoryInfo.getContent());
        
    }
    
    @Test
    void testGetConfigListByNamespace() {
        ConfigInfoWrapper configInfoWrapper = new ConfigInfoWrapper();
        configInfoWrapper.setDataId("test");
        configInfoWrapper.setGroup("test");
        configInfoWrapper.setContent("test");
        List<ConfigInfoWrapper> configInfoWrappers = Collections.singletonList(configInfoWrapper);
        
        when(configInfoPersistService.queryConfigInfoByNamespace("test")).thenReturn(configInfoWrappers);
        
        List<ConfigInfoWrapper> actualList = historyService.getConfigListByNamespace("test");
        
        verify(configInfoPersistService).queryConfigInfoByNamespace("test");
        
        assertEquals(configInfoWrappers.size(), actualList.size());
        ConfigInfoWrapper actualConfigInfoWrapper = actualList.get(0);
        assertEquals(configInfoWrapper.getDataId(), actualConfigInfoWrapper.getDataId());
        assertEquals(configInfoWrapper.getGroup(), actualConfigInfoWrapper.getGroup());
        assertEquals(configInfoWrapper.getContent(), actualConfigInfoWrapper.getContent());
    }

    @Test
    void testGetConfigHistoryInfoPair() throws Exception {
        ConfigHistoryInfo configHistoryInfo = new ConfigHistoryInfo();
        configHistoryInfo.setDataId(TEST_DATA_ID);
        configHistoryInfo.setGroup(TEST_GROUP);
        configHistoryInfo.setContent(TEST_CONTENT);
        configHistoryInfo.setTenant(TEST_TENANT);
        configHistoryInfo.setOpType(TEST_OP_TYPE);
        configHistoryInfo.setMd5(TEST_MD5);
        configHistoryInfo.setPublishType(Constants.FORMAL);
        configHistoryInfo.setGrayName(StringUtils.EMPTY);
        configHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        configHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));

        when(historyConfigInfoPersistService.detailConfigHistory(1L)).thenReturn(configHistoryInfo);

        ConfigHistoryInfo nextHistoryInfo = new ConfigHistoryInfo();
        nextHistoryInfo.setDataId(TEST_DATA_ID);
        nextHistoryInfo.setGroup(TEST_GROUP);
        nextHistoryInfo.setTenant(TEST_TENANT);
        nextHistoryInfo.setOpType(TEST_OP_TYPE);
        nextHistoryInfo.setMd5(TEST_UPDATED_MD5);
        nextHistoryInfo.setContent(TEST_UPDATED_CONTENT);
        nextHistoryInfo.setPublishType(Constants.FORMAL);
        nextHistoryInfo.setGrayName(StringUtils.EMPTY);
        nextHistoryInfo.setCreatedTime(new Timestamp(new Date().getTime()));
        nextHistoryInfo.setLastModifiedTime(new Timestamp(new Date().getTime()));

        when(historyConfigInfoPersistService.getNextHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_TENANT, Constants.FORMAL,
                StringUtils.EMPTY, 1L)).thenReturn(nextHistoryInfo);

        ConfigHistoryInfoDetail resConfigHistoryInfoDetail = historyService.getConfigHistoryInfoDetail(TEST_DATA_ID, TEST_GROUP,
                TEST_TENANT, 1L);

        verify(historyConfigInfoPersistService).getNextHistoryInfo(TEST_DATA_ID, TEST_GROUP, TEST_TENANT, Constants.FORMAL,
                StringUtils.EMPTY, 1L);

        assertEquals(nextHistoryInfo.getDataId(), resConfigHistoryInfoDetail.getDataId());
        assertEquals(nextHistoryInfo.getGroup(), resConfigHistoryInfoDetail.getGroup());
        assertEquals(nextHistoryInfo.getMd5(), resConfigHistoryInfoDetail.getUpdatedMd5());
        assertEquals(nextHistoryInfo.getContent(), resConfigHistoryInfoDetail.getUpdatedContent());
    }
}
