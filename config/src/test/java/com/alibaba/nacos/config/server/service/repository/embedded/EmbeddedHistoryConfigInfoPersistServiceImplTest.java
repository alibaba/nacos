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
import com.alibaba.nacos.config.server.model.ConfigInfoWrapper;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.repository.embedded.EmbeddedStorageContextHolder;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.HISTORY_DETAIL_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.HISTORY_LIST_ROW_MAPPER;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * test for embedded config history.
 *
 * @author shiyiyue
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class EmbeddedHistoryConfigInfoPersistServiceImplTest {
    
    private EmbeddedHistoryConfigInfoPersistServiceImpl embeddedHistoryConfigInfoPersistService;
    
    @Mock
    private DataSourceService dataSourceService;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<EmbeddedStorageContextHolder> embeddedStorageContextHolderMockedStatic;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    @Mock
    DatabaseOperate databaseOperate;
    
    @Before
    public void before() {
        embeddedStorageContextHolderMockedStatic = Mockito.mockStatic(EmbeddedStorageContextHolder.class);
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        when(DynamicDataSource.getInstance()).thenReturn(dynamicDataSource);
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        when(dataSourceService.getDataSourceType()).thenReturn("derby");
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(anyString(), eq(Boolean.class), eq(false)))
                .thenReturn(false);
        embeddedHistoryConfigInfoPersistService = new EmbeddedHistoryConfigInfoPersistServiceImpl(databaseOperate);
    }
    
    @After
    public void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        embeddedStorageContextHolderMockedStatic.close();
    }
    
    @Test
    public void testInsertConfigHistoryAtomic() {
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
        embeddedHistoryConfigInfoPersistService.insertConfigHistoryAtomic(id, configInfo, srcIp, srcUser, timestamp,
                ops);
        
        //verify insert to be invoked
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(id), eq(dataId), eq(group), eq(tenant),
                        eq(appName), eq(content), eq(configInfo.getMd5()), eq(srcIp), eq(srcUser), eq(timestamp),
                        eq(ops), eq(configInfo.getEncryptedDataKey())), times(1));
    }
    
    @Test
    public void testRemoveConfigHistory() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        int pageSize = 1233;
        embeddedHistoryConfigInfoPersistService.removeConfigHistory(timestamp, pageSize);
        //verify delete by time and size invoked.
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(timestamp), eq(pageSize)), times(1));
    }
    
    @Test
    public void testFindDeletedConfig() {
        
        //mock query list return
        Map<String, Object> mockObj1 = new HashMap<>();
        mockObj1.put("nid", new BigInteger("1234"));
        mockObj1.put("data_id", "data_id1");
        mockObj1.put("group_id", "group_id1");
        mockObj1.put("tenant_id", "tenant_id1");
        LocalDateTime now = LocalDateTime.of(LocalDate.now(), LocalTime.now());
        mockObj1.put("gmt_modified", now);
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(mockObj1);
        Map<String, Object> mockObj2 = new HashMap<>();
        mockObj2.put("nid", new BigInteger("12345"));
        mockObj2.put("data_id", "data_id2");
        mockObj2.put("group_id", "group_id2");
        mockObj2.put("tenant_id", "tenant_id2");
        LocalDateTime now2 = LocalDateTime.of(LocalDate.now(), LocalTime.now());
        mockObj2.put("gmt_modified", now2);
        list.add(mockObj2);
        int pageSize = 1233;
        long startId = 23456;
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        Mockito.when(databaseOperate.queryMany(anyString(), eq(new Object[] {timestamp, startId, pageSize})))
                .thenReturn(list);
        //execute
        List<ConfigInfoWrapper> deletedConfig = embeddedHistoryConfigInfoPersistService.findDeletedConfig(timestamp,
                startId, pageSize);
        //expect verify
        Assert.assertEquals("data_id1", deletedConfig.get(0).getDataId());
        Assert.assertEquals("group_id1", deletedConfig.get(0).getGroup());
        Assert.assertEquals("tenant_id1", deletedConfig.get(0).getTenant());
        Assert.assertEquals(now.toInstant(ZoneOffset.ofHours(8)).toEpochMilli(),
                deletedConfig.get(0).getLastModified());
        Assert.assertEquals("data_id2", deletedConfig.get(1).getDataId());
        Assert.assertEquals("group_id2", deletedConfig.get(1).getGroup());
        Assert.assertEquals("tenant_id2", deletedConfig.get(1).getTenant());
        Assert.assertEquals(now2.toInstant(ZoneOffset.ofHours(8)).toEpochMilli(),
                deletedConfig.get(1).getLastModified());
    }
    
    @Test
    public void testFindConfigHistory() {
        String dataId = "dataId34567";
        String group = "group34567";
        String tenant = "tenant34567";
        
        //mock count
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}), eq(Integer.class)))
                .thenReturn(300);
        //mock list
        List<ConfigHistoryInfo> mockList = new ArrayList<>();
        mockList.add(createMockConfigHistoryInfo(0));
        mockList.add(createMockConfigHistoryInfo(1));
        mockList.add(createMockConfigHistoryInfo(2));
        Mockito.when(databaseOperate.queryMany(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(HISTORY_LIST_ROW_MAPPER))).thenReturn(mockList);
        int pageSize = 100;
        int pageNo = 2;
        //execute & verify
        Page<ConfigHistoryInfo> historyReturn = embeddedHistoryConfigInfoPersistService.findConfigHistory(dataId, group,
                tenant, pageNo, pageSize);
        Assert.assertEquals(mockList, historyReturn.getPageItems());
        Assert.assertEquals(300, historyReturn.getTotalCount());
        
    }
    
    @Test
    public void testDetailConfigHistory() {
        long nid = 256789;
        
        //mock query
        ConfigHistoryInfo mockConfigHistoryInfo = createMockConfigHistoryInfo(0);
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {nid}), eq(HISTORY_DETAIL_ROW_MAPPER)))
                .thenReturn(mockConfigHistoryInfo);
        //execute & verify
        ConfigHistoryInfo historyReturn = embeddedHistoryConfigInfoPersistService.detailConfigHistory(nid);
        Assert.assertEquals(mockConfigHistoryInfo, historyReturn);
    }
    
    @Test
    public void testDetailPreviousConfigHistory() {
        long nid = 256789;
        //mock query
        ConfigHistoryInfo mockConfigHistoryInfo = createMockConfigHistoryInfo(0);
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {nid}), eq(HISTORY_DETAIL_ROW_MAPPER)))
                .thenReturn(mockConfigHistoryInfo);
        //execute & verify
        ConfigHistoryInfo historyReturn = embeddedHistoryConfigInfoPersistService.detailPreviousConfigHistory(nid);
        Assert.assertEquals(mockConfigHistoryInfo, historyReturn);
    }
    
    @Test
    public void testFindConfigHistoryCountByTime() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        
        //mock count
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {timestamp}), eq(Integer.class)))
                .thenReturn(308);
        //execute & verify
        int count = embeddedHistoryConfigInfoPersistService.findConfigHistoryCountByTime(timestamp);
        Assert.assertEquals(308, count);
        
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
