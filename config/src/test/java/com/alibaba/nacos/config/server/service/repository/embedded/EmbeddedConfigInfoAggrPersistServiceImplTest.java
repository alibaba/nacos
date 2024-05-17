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

import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_AGGR_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_CHANGED_ROW_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * test for embedded config aggr.
 *
 * @author shiyiyue
 */
@ExtendWith(SpringExtension.class)
class EmbeddedConfigInfoAggrPersistServiceImplTest {
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<EmbeddedStorageContextHolder> embeddedStorageContextHolderMockedStatic;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    @Mock
    DatabaseOperate databaseOperate;
    
    private EmbeddedConfigInfoAggrPersistServiceImpl embededConfigInfoAggrPersistService;
    
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
        embededConfigInfoAggrPersistService = new EmbeddedConfigInfoAggrPersistServiceImpl(databaseOperate);
    }
    
    @AfterEach
    void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        embeddedStorageContextHolderMockedStatic.close();
    }
    
    @Test
    void testAddAggrConfigInfoOfEqualContent() {
        String dataId = "dataId111";
        String group = "group";
        String tenant = "tenant";
        String datumId = "datumId";
        String appName = "appname1234";
        String content = "content1234";
        
        //mock query datumId and equal with current content param.
        String existContent = "content1234";
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, datumId}), eq(String.class)))
                .thenReturn(existContent);
        //mock insert success
        Mockito.when(databaseOperate.update(any(List.class))).thenReturn(true);
        
        boolean result = embededConfigInfoAggrPersistService.addAggrConfigInfo(dataId, group, tenant, datumId, appName, content);
        assertTrue(result);
    }
    
    @Test
    void testAddAggrConfigInfoOfAddNewContent() {
        String dataId = "dataId111";
        String group = "group";
        String tenant = "tenant";
        String datumId = "datumId";
        String appName = "appname1234";
        String content = "content1234";
        
        //mock query datumId and return null.
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, datumId}), eq(String.class)))
                .thenReturn(null);
        //mock insert success
        Mockito.when(databaseOperate.update(any(List.class))).thenReturn(true);
        
        //execute
        boolean result = embededConfigInfoAggrPersistService.addAggrConfigInfo(dataId, group, tenant, datumId, appName, content);
        assertTrue(result);
    }
    
    @Test
    void testAddAggrConfigInfoOfUpdateNotEqualContent() {
        String dataId = "dataId111";
        String group = "group";
        String tenant = "tenant";
        String datumId = "datumId";
        String appName = "appname1234";
        String content = "content1234";
        
        //mock query datumId
        String existContent = "existContent111";
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, datumId}), eq(String.class)))
                .thenReturn(existContent);
        //mock update success,return 1
        Mockito.when(databaseOperate.update(any(List.class))).thenReturn(true);
        
        //mock update content
        boolean result = embededConfigInfoAggrPersistService.addAggrConfigInfo(dataId, group, tenant, datumId, appName, content);
        assertTrue(result);
        
    }
    
    @Test
    void testBatchPublishAggrSuccess() {
        
        String dataId = "dataId111";
        String group = "group";
        String tenant = "tenant";
        //mock query datumId and equal with current content param.
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, "d1"}), eq(String.class)))
                .thenReturn("c1");
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, "d2"}), eq(String.class)))
                .thenReturn("c2");
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, "d3"}), eq(String.class)))
                .thenReturn("c3");
        Mockito.when(databaseOperate.update(any(List.class))).thenReturn(true);
        
        Map<String, String> datumMap = new HashMap<>();
        datumMap.put("d1", "c1");
        datumMap.put("d2", "c2");
        datumMap.put("d3", "c3");
        String appName = "appname1234";
        boolean result = embededConfigInfoAggrPersistService.batchPublishAggr(dataId, group, tenant, datumMap, appName);
        assertTrue(result);
    }
    
    @Test
    void testAggrConfigInfoCount() {
        String dataId = "dataId11122";
        String group = "group";
        String tenant = "tenant";
        
        //mock select count of aggr.
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}), eq(Integer.class)))
                .thenReturn(new Integer(101));
        int result = embededConfigInfoAggrPersistService.aggrConfigInfoCount(dataId, group, tenant);
        assertEquals(101, result);
        
    }
    
    @Test
    void testFindConfigInfoAggrByPage() {
        String dataId = "dataId111";
        String group = "group";
        String tenant = "tenant";
        
        //mock query count.
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}), eq(Integer.class))).thenReturn(101);
        //mock query page list
        List<ConfigInfoAggr> configInfoAggrs = new ArrayList<>();
        configInfoAggrs.add(new ConfigInfoAggr());
        configInfoAggrs.add(new ConfigInfoAggr());
        configInfoAggrs.add(new ConfigInfoAggr());
        
        Mockito.when(databaseOperate.queryMany(anyString(), eq(new Object[] {dataId, group, tenant}), eq(CONFIG_INFO_AGGR_ROW_MAPPER)))
                .thenReturn(configInfoAggrs);
        int pageNo = 1;
        int pageSize = 120;
        Page<ConfigInfoAggr> configInfoAggrByPage = embededConfigInfoAggrPersistService.findConfigInfoAggrByPage(dataId, group, tenant,
                pageNo, pageSize);
        assertEquals(101, configInfoAggrByPage.getTotalCount());
        assertEquals(configInfoAggrs, configInfoAggrByPage.getPageItems());
        
    }
    
    @Test
    void testFindAllAggrGroup() {
        List<ConfigInfoChanged> configList = new ArrayList<>();
        configList.add(create("dataId", 0));
        configList.add(create("dataId", 1));
        //mock return list
        Mockito.when(databaseOperate.queryMany(anyString(), eq(new Object[] {}), eq(CONFIG_INFO_CHANGED_ROW_MAPPER)))
                .thenReturn(configList);
        
        List<ConfigInfoChanged> allAggrGroup = embededConfigInfoAggrPersistService.findAllAggrGroup();
        assertEquals(configList, allAggrGroup);
        
    }
    
    private ConfigInfoChanged create(String dataID, int i) {
        ConfigInfoChanged hasDatum = new ConfigInfoChanged();
        hasDatum.setDataId(dataID + i);
        hasDatum.setTenant("tenant1");
        hasDatum.setGroup("group1");
        return hasDatum;
    }
    
}
