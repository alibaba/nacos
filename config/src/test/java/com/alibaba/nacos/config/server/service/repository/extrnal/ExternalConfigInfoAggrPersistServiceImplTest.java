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

package com.alibaba.nacos.config.server.service.repository.extrnal;

import com.alibaba.nacos.config.server.model.ConfigInfoAggr;
import com.alibaba.nacos.config.server.model.ConfigInfoChanged;
import com.alibaba.nacos.config.server.service.sql.ExternalStorageUtils;
import com.alibaba.nacos.config.server.utils.TestCaseUtils;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_AGGR_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_CHANGED_ROW_MAPPER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
public class ExternalConfigInfoAggrPersistServiceImplTest {
    
    private ExternalConfigInfoAggrPersistServiceImpl externalConfigInfoAggrPersistService;
    
    @Mock
    private DataSourceService dataSourceService;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    private TransactionTemplate transactionTemplate = TestCaseUtils.createMockTransactionTemplate();
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<ExternalStorageUtils> externalStorageUtilsMockedStatic;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    @Before
    public void before() {
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        externalStorageUtilsMockedStatic = Mockito.mockStatic(ExternalStorageUtils.class);
        when(DynamicDataSource.getInstance()).thenReturn(dynamicDataSource);
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        when(dataSourceService.getTransactionTemplate()).thenReturn(transactionTemplate);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(dataSourceService.getDataSourceType()).thenReturn("mysql");
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(anyString(), eq(Boolean.class), eq(false)))
                .thenReturn(false);
        externalConfigInfoAggrPersistService = new ExternalConfigInfoAggrPersistServiceImpl();
        
    }
    
    @After
    public void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        externalStorageUtilsMockedStatic.close();
    }
    
    @Test
    public void testAddAggrConfigInfoOfEqualContent() {
        String dataId = "dataId111";
        String group = "group";
        String tenant = "tenant";
        String datumId = "datumId";
        String appName = "appname1234";
        String content = "content1234";
        
        //mock query datumId and equal with current content param.
        String existContent = "content1234";
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, datumId}),
                eq(String.class))).thenReturn(existContent);
        
        boolean result = externalConfigInfoAggrPersistService.addAggrConfigInfo(dataId, group, tenant, datumId, appName,
                content);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testAddAggrConfigInfoOfAddNewContent() {
        String dataId = "dataId111";
        String group = "group";
        String tenant = "tenant";
        String datumId = "datumId";
        String appName = "appname1234";
        String content = "content1234";
        
        //mock query datumId and throw EmptyResultDataAccessException.
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, datumId}),
                eq(String.class))).thenThrow(new EmptyResultDataAccessException(1));
        //mock insert success
        when(jdbcTemplate.update(anyString(), eq(dataId), eq(group), eq(tenant), eq(datumId), eq(appName), eq(content),
                any(Timestamp.class))).thenReturn(1);
        
        //execute
        boolean result = externalConfigInfoAggrPersistService.addAggrConfigInfo(dataId, group, tenant, datumId, appName,
                content);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testAddAggrConfigInfoOfUpdateNotEqualContent() {
        String dataId = "dataId111";
        String group = "group";
        String tenant = "tenant";
        String datumId = "datumId";
        String appName = "appname1234";
        String content = "content1234";
        
        //mock query datumId
        String existContent = "existContent111";
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, datumId}),
                eq(String.class))).thenReturn(existContent);
        //mock update success,return 1
        when(jdbcTemplate.update(anyString(), eq(content), any(Timestamp.class), eq(dataId), eq(group), eq(tenant),
                eq(datumId))).thenReturn(1);
        //mock update content
        boolean result = externalConfigInfoAggrPersistService.addAggrConfigInfo(dataId, group, tenant, datumId, appName,
                content);
        Assert.assertTrue(result);
        
    }
    
    @Test
    public void testAddAggrConfigInfoOfException() {
        String dataId = "dataId111";
        String group = "group";
        String tenant = "tenant";
        String datumId = "datumId";
        String appName = "appname1234";
        String content = "content1234";
        
        //mock query datumId and throw EmptyResultDataAccessException.
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, datumId}),
                eq(String.class))).thenThrow(new CannotGetJdbcConnectionException("mock exp"));
        try {
            externalConfigInfoAggrPersistService.addAggrConfigInfo(dataId, group, tenant, datumId, appName, content);
            Assert.assertTrue(false);
        } catch (Exception exp) {
            Assert.assertEquals("mock exp", exp.getMessage());
        }
    }
    
    @Test
    public void testBatchPublishAggrSuccess() {
        
        String dataId = "dataId111";
        String group = "group";
        String tenant = "tenant";
        //mock query datumId and equal with current content param.
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, "d1"}),
                eq(String.class))).thenReturn("c1");
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, "d2"}),
                eq(String.class))).thenReturn("c2");
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, "d3"}),
                eq(String.class))).thenReturn("c3");
        Map<String, String> datumMap = new HashMap<>();
        datumMap.put("d1", "c1");
        datumMap.put("d2", "c2");
        datumMap.put("d3", "c3");
        String appName = "appname1234";
        boolean result = externalConfigInfoAggrPersistService.batchPublishAggr(dataId, group, tenant, datumMap,
                appName);
        Assert.assertTrue(result);
    }
    
    @Test
    public void testBatchPublishAggrException() {
        
        String dataId = "dataId111";
        String group = "group";
        String tenant = "tenant";
        //mock query datumId and equal with current content param.
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, "d1"}),
                eq(String.class))).thenThrow(new TransactionSystemException("c1t fail"));
        Map<String, String> datumMap = new HashMap<>();
        datumMap.put("d1", "c1");
        datumMap.put("d2", "c2");
        datumMap.put("d3", "c3");
        String appName = "appname1234";
        boolean result = externalConfigInfoAggrPersistService.batchPublishAggr(dataId, group, tenant, datumMap,
                appName);
        Assert.assertFalse(result);
    }
    
    @Test
    public void testAggrConfigInfoCount() {
        String dataId = "dataId11122";
        String group = "group";
        String tenant = "tenant";
        
        //mock select count of aggr.
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(dataId), eq(group), eq(tenant))).thenReturn(
                new Integer(101));
        int result = externalConfigInfoAggrPersistService.aggrConfigInfoCount(dataId, group, tenant);
        Assert.assertEquals(101, result);
        
    }
    
    @Test
    public void testFindConfigInfoAggrByPage() {
        String dataId = "dataId111";
        String group = "group";
        String tenant = "tenant";
        
        //mock query count.
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(Integer.class))).thenReturn(101);
        //mock query page list
        List<ConfigInfoAggr> configInfoAggrs = new ArrayList<>();
        configInfoAggrs.add(new ConfigInfoAggr());
        configInfoAggrs.add(new ConfigInfoAggr());
        configInfoAggrs.add(new ConfigInfoAggr());
        
        when(jdbcTemplate.query(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_AGGR_ROW_MAPPER))).thenReturn(configInfoAggrs);
        int pageNo = 1;
        int pageSize = 120;
        Page<ConfigInfoAggr> configInfoAggrByPage = externalConfigInfoAggrPersistService.findConfigInfoAggrByPage(
                dataId, group, tenant, pageNo, pageSize);
        Assert.assertEquals(101, configInfoAggrByPage.getTotalCount());
        Assert.assertEquals(configInfoAggrs, configInfoAggrByPage.getPageItems());
        
    }
    
    @Test
    public void testFindConfigInfoAggrByPageOfException() {
        String dataId = "dataId111";
        String group = "group";
        String tenant = "tenant";
        
        //mock query count exception.
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(Integer.class))).thenThrow(new CannotGetJdbcConnectionException("mock fail222"));
        
        try {
            int pageNo = 1;
            int pageSize = 120;
            externalConfigInfoAggrPersistService.findConfigInfoAggrByPage(dataId, group, tenant, pageNo, pageSize);
            Assert.assertTrue(false);
        } catch (Throwable throwable) {
            Assert.assertEquals("mock fail222", throwable.getMessage());
        }
    }
    
    @Test
    public void testFindAllAggrGroup() {
        List<ConfigInfoChanged> configList = new ArrayList<>();
        configList.add(create("dataId", 0));
        configList.add(create("dataId", 1));
        //mock return list
        when(jdbcTemplate.query(anyString(), eq(new Object[] {}), eq(CONFIG_INFO_CHANGED_ROW_MAPPER))).thenReturn(
                configList);
        
        List<ConfigInfoChanged> allAggrGroup = externalConfigInfoAggrPersistService.findAllAggrGroup();
        Assert.assertEquals(configList, allAggrGroup);
        
    }
    
    @Test
    public void testFindAllAggrGroupException() {
        
        //mock throw CannotGetJdbcConnectionException
        when(jdbcTemplate.query(anyString(), eq(new Object[] {}), eq(CONFIG_INFO_CHANGED_ROW_MAPPER))).thenThrow(
                new CannotGetJdbcConnectionException("mock fail"));
        try {
            externalConfigInfoAggrPersistService.findAllAggrGroup();
            Assert.assertTrue(false);
        } catch (Throwable throwable) {
            Assert.assertEquals("mock fail", throwable.getMessage());
        }
        
        //mock throw EmptyResultDataAccessException
        when(jdbcTemplate.query(anyString(), eq(new Object[] {}), eq(CONFIG_INFO_CHANGED_ROW_MAPPER))).thenThrow(
                new EmptyResultDataAccessException(1));
        List<ConfigInfoChanged> allAggrGroup = externalConfigInfoAggrPersistService.findAllAggrGroup();
        Assert.assertEquals(null, allAggrGroup);
        
        //mock Exception
        when(jdbcTemplate.query(anyString(), eq(new Object[] {}), eq(CONFIG_INFO_CHANGED_ROW_MAPPER))).thenThrow(
                new RuntimeException("789"));
        try {
            externalConfigInfoAggrPersistService.findAllAggrGroup();
            Assert.assertTrue(false);
        } catch (Throwable throwable) {
            Assert.assertEquals("789", throwable.getCause().getMessage());
        }
        
    }
    
    private ConfigInfoChanged create(String dataID, int i) {
        ConfigInfoChanged hasDatum = new ConfigInfoChanged();
        hasDatum.setDataId(dataID + i);
        hasDatum.setTenant("tenant1");
        hasDatum.setGroup("group1");
        return hasDatum;
    }
    
}
