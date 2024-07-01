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

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.service.sql.ExternalStorageUtils;
import com.alibaba.nacos.config.server.utils.TestCaseUtils;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class ExternalConfigInfoBetaPersistServiceImplTest {
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<ExternalStorageUtils> externalStorageUtilsMockedStatic;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    private ExternalConfigInfoBetaPersistServiceImpl externalConfigInfoBetaPersistService;
    
    @Mock
    private DataSourceService dataSourceService;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    private TransactionTemplate transactionTemplate = TestCaseUtils.createMockTransactionTemplate();
    
    @BeforeEach
    void before() {
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        externalStorageUtilsMockedStatic = Mockito.mockStatic(ExternalStorageUtils.class);
        when(DynamicDataSource.getInstance()).thenReturn(dynamicDataSource);
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        when(dataSourceService.getTransactionTemplate()).thenReturn(transactionTemplate);
        when(dataSourceService.getJdbcTemplate()).thenReturn(jdbcTemplate);
        when(dataSourceService.getDataSourceType()).thenReturn("mysql");
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(anyString(), eq(Boolean.class), eq(false))).thenReturn(false);
        externalConfigInfoBetaPersistService = new ExternalConfigInfoBetaPersistServiceImpl();
    }
    
    @AfterEach
    void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        externalStorageUtilsMockedStatic.close();
    }
    
    @Test
    void testInsertOrUpdateBetaOfUpdate() {
        String dataId = "betaDataId113";
        String group = "group";
        String tenant = "tenant";
        //mock exist beta
        ConfigInfoStateWrapper mockedConfigInfoStateWrapper = new ConfigInfoStateWrapper();
        mockedConfigInfoStateWrapper.setDataId(dataId);
        mockedConfigInfoStateWrapper.setGroup(group);
        mockedConfigInfoStateWrapper.setTenant(tenant);
        mockedConfigInfoStateWrapper.setId(123456L);
        mockedConfigInfoStateWrapper.setLastModified(System.currentTimeMillis());
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(mockedConfigInfoStateWrapper, mockedConfigInfoStateWrapper);
        //execute
        String betaIps = "betaips...";
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        String appName = "appname";
        String content = "content111";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        ConfigOperateResult configOperateResult = externalConfigInfoBetaPersistService.insertOrUpdateBeta(configInfo, betaIps, srcIp,
                srcUser);
        //expect return obj
        assertEquals(mockedConfigInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(mockedConfigInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        //verify update to be invoked
        Mockito.verify(jdbcTemplate, times(1))
                .update(anyString(), eq(configInfo.getContent()), eq(configInfo.getMd5()), eq(betaIps), eq(srcIp), eq(srcUser),
                        eq(configInfo.getAppName()), eq(configInfo.getEncryptedDataKey()), eq(dataId), eq(group), eq(tenant));
    }
    
    @Test
    void testInsertOrUpdateBetaOfAdd() {
        String dataId = "betaDataId113";
        String group = "group113";
        String tenant = "tenant113";
        //mock exist beta
        ConfigInfoStateWrapper mockedConfigInfoStateWrapper = new ConfigInfoStateWrapper();
        mockedConfigInfoStateWrapper.setDataId(dataId);
        mockedConfigInfoStateWrapper.setGroup(group);
        mockedConfigInfoStateWrapper.setTenant(tenant);
        mockedConfigInfoStateWrapper.setId(123456L);
        mockedConfigInfoStateWrapper.setLastModified(System.currentTimeMillis());
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenThrow(new EmptyResultDataAccessException(1))
                .thenReturn(mockedConfigInfoStateWrapper);
        
        String betaIps = "betaips...";
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        String appName = "appname";
        String content = "content111";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        //execute
        ConfigOperateResult configOperateResult = externalConfigInfoBetaPersistService.insertOrUpdateBeta(configInfo, betaIps, srcIp,
                srcUser);
        //expect return obj
        assertEquals(mockedConfigInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(mockedConfigInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        //verify add to be invoked
        Mockito.verify(jdbcTemplate, times(1))
                .update(anyString(), eq(dataId), eq(group), eq(tenant), eq(configInfo.getAppName()), eq(configInfo.getContent()),
                        eq(configInfo.getMd5()), eq(betaIps), eq(srcIp), eq(srcUser), eq(configInfo.getEncryptedDataKey()));
        
    }
    
    @Test
    void testInsertOrUpdateBetaOfException() {
        String dataId = "betaDataId113";
        String group = "group113";
        String tenant = "tenant113";
        //mock exist beta
        ConfigInfoStateWrapper mockedConfigInfoStateWrapper = new ConfigInfoStateWrapper();
        mockedConfigInfoStateWrapper.setDataId(dataId);
        mockedConfigInfoStateWrapper.setGroup(group);
        mockedConfigInfoStateWrapper.setTenant(tenant);
        mockedConfigInfoStateWrapper.setId(123456L);
        mockedConfigInfoStateWrapper.setLastModified(System.currentTimeMillis());
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(mockedConfigInfoStateWrapper);
        
        String betaIps = "betaips...";
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        String appName = "appname";
        String content = "content111";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        
        // mock update throw CannotGetJdbcConnectionException
        when(jdbcTemplate.update(anyString(), eq(configInfo.getContent()), eq(configInfo.getMd5()), eq(betaIps), eq(srcIp), eq(srcUser),
                eq(configInfo.getAppName()), eq(configInfo.getEncryptedDataKey()), eq(dataId), eq(group),
                eq(tenant))).thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        //execute of update& expect.
        try {
            externalConfigInfoBetaPersistService.insertOrUpdateBeta(configInfo, betaIps, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("mock fail", exception.getMessage());
        }

        //mock query return null
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[]{dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(null);
        //mock add throw CannotGetJdbcConnectionException
        when(jdbcTemplate.update(anyString(), eq(dataId), eq(group), eq(tenant), eq(configInfo.getAppName()),
                eq(configInfo.getContent()), eq(configInfo.getMd5()), eq(betaIps), eq(srcIp), eq(srcUser),
                eq(configInfo.getEncryptedDataKey()))).thenThrow(new CannotGetJdbcConnectionException("mock fail add"));
        //execute of add& expect.
        try {
            externalConfigInfoBetaPersistService.insertOrUpdateBeta(configInfo, betaIps, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("mock fail add", exception.getMessage());
        }

        //mock query throw CannotGetJdbcConnectionException
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenThrow(new CannotGetJdbcConnectionException("get c fail"));
        //execute of add& expect.
        try {
            externalConfigInfoBetaPersistService.insertOrUpdateBeta(configInfo, betaIps, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("get c fail", exception.getMessage());
        }
        
    }
    
    @Test
    void testInsertOrUpdateBetaCasOfUpdate() {
        String dataId = "betaDataId113";
        String group = "group";
        String tenant = "tenant";
        //mock exist beta
        ConfigInfoStateWrapper mockedConfigInfoStateWrapper = new ConfigInfoStateWrapper();
        mockedConfigInfoStateWrapper.setDataId(dataId);
        mockedConfigInfoStateWrapper.setGroup(group);
        mockedConfigInfoStateWrapper.setTenant(tenant);
        mockedConfigInfoStateWrapper.setId(123456L);
        mockedConfigInfoStateWrapper.setLastModified(System.currentTimeMillis());
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(mockedConfigInfoStateWrapper, mockedConfigInfoStateWrapper);
        
        //execute
        String betaIps = "betaips...";
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        String appName = "appname";
        String content = "content111";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        configInfo.setMd5("casMd5");
        //mock cas update
        when(jdbcTemplate.update(anyString(), eq(configInfo.getContent()), eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)),
                eq(betaIps), eq(srcIp), eq(srcUser), eq(appName), eq(dataId), eq(group), eq(tenant),
                eq(configInfo.getMd5()))).thenReturn(1);
        
        ConfigOperateResult configOperateResult = externalConfigInfoBetaPersistService.insertOrUpdateBetaCas(configInfo, betaIps, srcIp,
                srcUser);
        //expect return obj
        assertEquals(mockedConfigInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(mockedConfigInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        //verify cas update to be invoked
        Mockito.verify(jdbcTemplate, times(1))
                .update(anyString(), eq(configInfo.getContent()), eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)), eq(betaIps),
                        eq(srcIp), eq(srcUser), eq(appName), eq(dataId), eq(group), eq(tenant), eq(configInfo.getMd5()));
        
    }
    
    @Test
    void testInsertOrUpdateBetaCasOfAdd() {
        String dataId = "betaDataId113";
        String group = "group113";
        String tenant = "tenant113";
        //mock exist beta
        ConfigInfoStateWrapper mockedConfigInfoStateWrapper = new ConfigInfoStateWrapper();
        mockedConfigInfoStateWrapper.setDataId(dataId);
        mockedConfigInfoStateWrapper.setGroup(group);
        mockedConfigInfoStateWrapper.setTenant(tenant);
        mockedConfigInfoStateWrapper.setId(123456L);
        mockedConfigInfoStateWrapper.setLastModified(System.currentTimeMillis());
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenThrow(new EmptyResultDataAccessException(1))
                .thenReturn(mockedConfigInfoStateWrapper);
        
        String betaIps = "betaips...";
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        String appName = "appname";
        String content = "content111";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        //execute
        ConfigOperateResult configOperateResult = externalConfigInfoBetaPersistService.insertOrUpdateBetaCas(configInfo, betaIps, srcIp,
                srcUser);
        //expect return obj
        assertEquals(mockedConfigInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(mockedConfigInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        //verify add to be invoked
        Mockito.verify(jdbcTemplate, times(1))
                .update(anyString(), eq(dataId), eq(group), eq(tenant), eq(configInfo.getAppName()), eq(configInfo.getContent()),
                        eq(configInfo.getMd5()), eq(betaIps), eq(srcIp), eq(srcUser), eq(configInfo.getEncryptedDataKey()));
        
    }
    
    @Test
    void testInsertOrUpdateBetaCasOfException() {
        String dataId = "betaDataId113";
        String group = "group113";
        String tenant = "tenant113";
        //mock exist beta
        ConfigInfoStateWrapper mockedConfigInfoStateWrapper = new ConfigInfoStateWrapper();
        mockedConfigInfoStateWrapper.setDataId(dataId);
        mockedConfigInfoStateWrapper.setGroup(group);
        mockedConfigInfoStateWrapper.setTenant(tenant);
        mockedConfigInfoStateWrapper.setId(123456L);
        mockedConfigInfoStateWrapper.setLastModified(System.currentTimeMillis());
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(mockedConfigInfoStateWrapper);
        
        String betaIps = "betaips...";
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        String appName = "appname";
        String content = "content111";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        configInfo.setMd5("casMd5");
        // mock update throw CannotGetJdbcConnectionException
        when(jdbcTemplate.update(anyString(), eq(configInfo.getContent()), eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)),
                eq(betaIps), eq(srcIp), eq(srcUser), eq(appName), eq(dataId), eq(group), eq(tenant),
                eq(configInfo.getMd5()))).thenThrow(new CannotGetJdbcConnectionException("mock fail"));
        //execute of update& expect.
        try {
            externalConfigInfoBetaPersistService.insertOrUpdateBetaCas(configInfo, betaIps, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("mock fail", exception.getMessage());
        }
        
        //mock query return null
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(null);
        //mock add throw CannotGetJdbcConnectionException
        when(jdbcTemplate.update(anyString(), eq(dataId), eq(group), eq(tenant), eq(configInfo.getAppName()), eq(configInfo.getContent()),
                eq(MD5Utils.md5Hex(configInfo.getContent(), Constants.PERSIST_ENCODE)), eq(betaIps), eq(srcIp),
                eq(srcUser), eq(configInfo.getEncryptedDataKey()))).thenThrow(
                new CannotGetJdbcConnectionException("mock fail add"));
        
        //execute of add& expect.
        try {
            externalConfigInfoBetaPersistService.insertOrUpdateBetaCas(configInfo, betaIps, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("mock fail add", exception.getMessage());
        }
        
        //mock query throw CannotGetJdbcConnectionException
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenThrow(new CannotGetJdbcConnectionException("get c fail"));
        //execute of add& expect.
        try {
            externalConfigInfoBetaPersistService.insertOrUpdateBetaCas(configInfo, betaIps, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("get c fail", exception.getMessage());
        }
        
    }
    
    @Test
    void testRemoveConfigInfo4Beta() {
        String dataId = "dataId456789";
        String group = "group4567";
        String tenant = "tenant56789o0";
        //mock exist beta
        ConfigInfoStateWrapper mockedConfigInfoStateWrapper = new ConfigInfoStateWrapper();
        mockedConfigInfoStateWrapper.setDataId(dataId);
        mockedConfigInfoStateWrapper.setGroup(group);
        mockedConfigInfoStateWrapper.setTenant(tenant);
        mockedConfigInfoStateWrapper.setId(123456L);
        mockedConfigInfoStateWrapper.setLastModified(System.currentTimeMillis());
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(mockedConfigInfoStateWrapper);
        externalConfigInfoBetaPersistService.removeConfigInfo4Beta(dataId, group, tenant);
        
        //verity
        Mockito.verify(jdbcTemplate, times(1)).update(anyString(), eq(dataId), eq(group), eq(tenant));
        
        //mock query throw CannotGetJdbcConnectionException
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenThrow(new CannotGetJdbcConnectionException("mock fail11111"));
        
        try {
            externalConfigInfoBetaPersistService.removeConfigInfo4Beta(dataId, group, tenant);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("mock fail11111", exception.getMessage());
        }
    }
    
    @Test
    void testFindConfigInfo4Beta() {
        String dataId = "dataId456789";
        String group = "group4567";
        String tenant = "tenant56789o0";
        //mock exist beta
        ConfigInfoBetaWrapper mockedConfigInfoStateWrapper = new ConfigInfoBetaWrapper();
        mockedConfigInfoStateWrapper.setDataId(dataId);
        mockedConfigInfoStateWrapper.setGroup(group);
        mockedConfigInfoStateWrapper.setTenant(tenant);
        mockedConfigInfoStateWrapper.setId(123456L);
        mockedConfigInfoStateWrapper.setLastModified(System.currentTimeMillis());
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER))).thenReturn(mockedConfigInfoStateWrapper);
        ConfigInfoBetaWrapper configInfo4BetaReturn = externalConfigInfoBetaPersistService.findConfigInfo4Beta(dataId, group, tenant);
        assertEquals(mockedConfigInfoStateWrapper, configInfo4BetaReturn);
        
        //mock query throw CannotGetJdbcConnectionException
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER))).thenThrow(new CannotGetJdbcConnectionException("mock fail11111"));
        try {
            externalConfigInfoBetaPersistService.findConfigInfo4Beta(dataId, group, tenant);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("mock fail11111", exception.getMessage());
        }
        
        //mock query throw EmptyResultDataAccessException
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant}),
                eq(CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER))).thenThrow(new EmptyResultDataAccessException(1));
        ConfigInfoBetaWrapper configInfo4BetaNull = externalConfigInfoBetaPersistService.findConfigInfo4Beta(dataId, group, tenant);
        assertNull(configInfo4BetaNull);
    }
    
    @Test
    void testConfigInfoBetaCount() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(101);
        int returnCount = externalConfigInfoBetaPersistService.configInfoBetaCount();
        assertEquals(101, returnCount);
    }
    
    @Test
    void testFindAllConfigInfoBetaForDumpAll() {
        //mock count
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(12345);
        
        //mock page list
        List<ConfigInfoBetaWrapper> mockList = new ArrayList<>();
        mockList.add(new ConfigInfoBetaWrapper());
        mockList.add(new ConfigInfoBetaWrapper());
        mockList.add(new ConfigInfoBetaWrapper());
        mockList.get(0).setLastModified(System.currentTimeMillis());
        mockList.get(1).setLastModified(System.currentTimeMillis());
        mockList.get(2).setLastModified(System.currentTimeMillis());
        
        when(jdbcTemplate.query(anyString(), eq(new Object[] {}), eq(CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER))).thenReturn(mockList);
        
        int pageNo = 1;
        int pageSize = 101;
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(101);
        //execute & expect
        Page<ConfigInfoBetaWrapper> pageReturn = externalConfigInfoBetaPersistService.findAllConfigInfoBetaForDumpAll(pageNo, pageSize);
        assertEquals(mockList, pageReturn.getPageItems());
        assertEquals(101, pageReturn.getTotalCount());
        
        //mock count throw CannotGetJdbcConnectionException
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenThrow(new CannotGetJdbcConnectionException("345678909fail"));
        //execute &expect
        try {
            externalConfigInfoBetaPersistService.findAllConfigInfoBetaForDumpAll(pageNo, pageSize);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("345678909fail", exception.getMessage());
        }
    }
    
}

