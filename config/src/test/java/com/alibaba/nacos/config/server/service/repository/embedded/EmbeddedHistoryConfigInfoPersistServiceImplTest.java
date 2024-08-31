/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.service.repository.embedded;

import com.alibaba.nacos.config.server.model.ConfigHistoryInfo;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.HISTORY_DETAIL_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.HISTORY_LIST_ROW_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * test for embedded config history.
 *
 * @author shiyiyue
 */
@ExtendWith(SpringExtension.class)
class EmbeddedHistoryConfigInfoPersistServiceImplTest {
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<EmbeddedStorageContextHolder> embeddedStorageContextHolderMockedStatic;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    @Mock
    DatabaseOperate databaseOperate;
    
    private EmbeddedHistoryConfigInfoPersistServiceImpl embeddedHistoryConfigInfoPersistService;
    
    @Mock
    private DataSourceService dataSourceService;
    
    @BeforeEach
    void before() {
        embeddedStorageContextHolderMockedStatic = Mockito.mockStatic(EmbeddedStorageContextHolder.class);
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        when(DynamicDataSource.getInstance()).thenReturn(dynamicDataSource);
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        when(dataSourceService.getDataSourceType()).thenReturn("derby");
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(anyString(), eq(Boolean.class), eq(false))).thenReturn(false);
        embeddedHistoryConfigInfoPersistService = new EmbeddedHistoryConfigInfoPersistServiceImpl(databaseOperate);
    }
    
    @AfterEach
    void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        embeddedStorageContextHolderMockedStatic.close();
    }
    
    @Test
    void testInsertConfigHistoryAtomic() {
        String dataId = "dateId243";
        String group = "group243";
        String tenant = "tenant243";
        String content = "content243";
        String appName = "appName243";
        long id = 123456787765432L;
        String srcUser = "user12345";
        String srcIp = "ip1234";
        String ops = "D";
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key23456");
        //expect insert success,verify insert invoked
        embeddedHistoryConfigInfoPersistService.insertConfigHistoryAtomic(id, configInfo, srcIp, srcUser, timestamp, ops);
        
        //verify insert to be invoked
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(id), eq(dataId), eq(group), eq(tenant), eq(appName),
                        eq(content), eq(configInfo.getMd5()), eq(srcIp), eq(srcUser), eq(timestamp), eq(ops),
                        eq(configInfo.getEncryptedDataKey())), times(1));
    }
    
    @Test
    void testRemoveConfigHistory() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        int pageSize = 1233;
        embeddedHistoryConfigInfoPersistService.removeConfigHistory(timestamp, pageSize);
        //verify delete by time and size invoked.
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(timestamp), eq(pageSize)), times(1));
    }
    
    @Test
    void testFindDeletedConfig() {
        
        //mock query list return
        ConfigInfoStateWrapper mockObj1 = new ConfigInfoStateWrapper();
        mockObj1.setDataId("data_id1");
        mockObj1.setGroup("group_id1");
        mockObj1.setTenant("tenant_id1");
        mockObj1.setMd5("md51");
        mockObj1.setLastModified(System.currentTimeMillis());
        
        List<ConfigInfoStateWrapper> list = new ArrayList<>();
        list.add(mockObj1);
        ConfigInfoStateWrapper mockObj2 = new ConfigInfoStateWrapper();
        mockObj2.setDataId("data_id2");
        mockObj2.setGroup("group_id2");
        mockObj2.setTenant("tenant_id2");
        mockObj2.setMd5("md52");
        list.add(mockObj2);
        int pageSize = 1233;
        long startId = 23456;
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        Mockito.when(databaseOperate.queryMany(anyString(), eq(new Object[] {timestamp, startId, pageSize}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(list);
        //execute
        List<ConfigInfoStateWrapper> deletedConfig = embeddedHistoryConfigInfoPersistService.findDeletedConfig(timestamp, startId,
                pageSize);
        //expect verify
        assertEquals("data_id1", deletedConfig.get(0).getDataId());
        assertEquals("group_id1", deletedConfig.get(0).getGroup());
        assertEquals("tenant_id1", deletedConfig.get(0).getTenant());
        assertEquals(mockObj1.getLastModified(), deletedConfig.get(0).getLastModified());
        assertEquals("data_id2", deletedConfig.get(1).getDataId());
        assertEquals("group_id2", deletedConfig.get(1).getGroup());
        assertEquals("tenant_id2", deletedConfig.get(1).getTenant());
        assertEquals(mockObj2.getLastModified(), deletedConfig.get(1).getLastModified());
    }
    
    @Test
    void testFindConfigHistory() {
        String dataId = "dataId34567";
        String group = "group34567";
        String tenant = "tenant34567";
        
        //mock count
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}), eq(Integer.class))).thenReturn(300);
        //mock list
        List<ConfigHistoryInfo> mockList = new ArrayList<>();
        mockList.add(createMockConfigHistoryInfo(0));
        mockList.add(createMockConfigHistoryInfo(1));
        mockList.add(createMockConfigHistoryInfo(2));
        Mockito.when(databaseOperate.queryMany(anyString(), eq(new Object[] {dataId, group, tenant}), eq(HISTORY_LIST_ROW_MAPPER)))
                .thenReturn(mockList);
        int pageSize = 100;
        int pageNo = 2;
        //execute & verify
        Page<ConfigHistoryInfo> historyReturn = embeddedHistoryConfigInfoPersistService.findConfigHistory(dataId, group, tenant, pageNo,
                pageSize);
        assertEquals(mockList, historyReturn.getPageItems());
        assertEquals(300, historyReturn.getTotalCount());
        
    }
    
    @Test
    void testDetailConfigHistory() {
        long nid = 256789;
        
        //mock query
        ConfigHistoryInfo mockConfigHistoryInfo = createMockConfigHistoryInfo(0);
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {nid}), eq(HISTORY_DETAIL_ROW_MAPPER)))
                .thenReturn(mockConfigHistoryInfo);
        //execute & verify
        ConfigHistoryInfo historyReturn = embeddedHistoryConfigInfoPersistService.detailConfigHistory(nid);
        assertEquals(mockConfigHistoryInfo, historyReturn);
    }
    
    @Test
    void testDetailPreviousConfigHistory() {
        long nid = 256789;
        //mock query
        ConfigHistoryInfo mockConfigHistoryInfo = createMockConfigHistoryInfo(0);
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {nid}), eq(HISTORY_DETAIL_ROW_MAPPER)))
                .thenReturn(mockConfigHistoryInfo);
        //execute & verify
        ConfigHistoryInfo historyReturn = embeddedHistoryConfigInfoPersistService.detailPreviousConfigHistory(nid);
        assertEquals(mockConfigHistoryInfo, historyReturn);
    }
    
    @Test
    void testFindConfigHistoryCountByTime() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        
        //mock count
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {timestamp}), eq(Integer.class))).thenReturn(308);
        //execute & verify
        int count = embeddedHistoryConfigInfoPersistService.findConfigHistoryCountByTime(timestamp);
        assertEquals(308, count);
        
    }
    
    private ConfigHistoryInfo createMockConfigHistoryInfo(long mockId) {
        ConfigHistoryInfo configAllInfo = new ConfigHistoryInfo();
        configAllInfo.setDataId("test" + mockId + ".yaml");
        configAllInfo.setGroup("test");
        configAllInfo.setContent("23456789000content");
        configAllInfo.setOpType("D");
        configAllInfo.setEncryptedDataKey("key4567");
        configAllInfo.setSrcIp("ip567");
        configAllInfo.setSrcUser("user1234");
        configAllInfo.setMd5("md52345678");
        return configAllInfo;
    }
}
