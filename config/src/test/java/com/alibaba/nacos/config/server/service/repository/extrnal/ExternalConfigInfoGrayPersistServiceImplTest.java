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
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.config.server.service.sql.ExternalStorageUtils;
import com.alibaba.nacos.config.server.utils.TestCaseUtils;
import com.alibaba.nacos.persistence.datasource.DataSourceService;
import com.alibaba.nacos.persistence.datasource.DynamicDataSource;
import com.alibaba.nacos.persistence.model.Page;
import com.alibaba.nacos.persistence.repository.embedded.operate.DatabaseOperate;
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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import static com.alibaba.nacos.config.server.constant.Constants.ENCODE;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class ExternalConfigInfoGrayPersistServiceImplTest {
    
    private ExternalConfigInfoGrayPersistServiceImpl externalConfigInfoGrayPersistService;
    
    @Mock
    private DataSourceService dataSourceService;
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @Mock
    private HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    @Mock
    DatabaseOperate databaseOperate;
    
    private TransactionTemplate transactionTemplate = TestCaseUtils.createMockTransactionTemplate();
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<ExternalStorageUtils> externalStorageUtilsMockedStatic;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    /**
     * before each tet case.
     */
    @BeforeEach
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
        externalConfigInfoGrayPersistService = new ExternalConfigInfoGrayPersistServiceImpl(historyConfigInfoPersistService);
    }
    
    /**
     * after each test case.
     */
    @AfterEach
    public void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        externalStorageUtilsMockedStatic.close();
    }
    
    @Test
    public void testInsertOrUpdateGrayOfUpdate() {
        String dataId = "grayDataId113";
        String group = "group";
        String tenant = "tenant";
        
        //mock exist gray
        ConfigInfoStateWrapper mockedConfigInfoStateWrapper = new ConfigInfoStateWrapper();
        mockedConfigInfoStateWrapper.setDataId(dataId);
        mockedConfigInfoStateWrapper.setGroup(group);
        mockedConfigInfoStateWrapper.setTenant(tenant);
        mockedConfigInfoStateWrapper.setId(123456L);
        mockedConfigInfoStateWrapper.setLastModified(System.currentTimeMillis());
        
        //mock exist config info
        ConfigInfoGrayWrapper configAllInfo4Gray = new ConfigInfoGrayWrapper();
        configAllInfo4Gray.setDataId(dataId);
        configAllInfo4Gray.setGroup(group);
        configAllInfo4Gray.setTenant(tenant);
        configAllInfo4Gray.setMd5("old_md5");
        String grayName = "grayName...";
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(mockedConfigInfoStateWrapper);
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER))).thenReturn(configAllInfo4Gray);
        
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        String appName = "appName";
        String content = "content111";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        String grayRule = "grayRule...";
        ConfigOperateResult configOperateResult = externalConfigInfoGrayPersistService.insertOrUpdateGray(configInfo,
                grayName, grayRule, srcIp, srcUser);
        //expect return obj
        assertEquals(mockedConfigInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(mockedConfigInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        //verify update to be invoked
        Mockito.verify(jdbcTemplate, times(1))
                .update(anyString(), eq(configInfo.getContent()), eq(configInfo.getEncryptedDataKey()),
                        eq(configInfo.getMd5()), eq(srcIp), eq(srcUser), eq(configInfo.getAppName()), eq(grayRule),
                        eq(dataId), eq(group), eq(tenant), eq(grayName));
        
    }
    
    @Test
    public void testInsertOrUpdateGrayOfAdd() {
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
        String grayName = "grayName...";
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenThrow(new EmptyResultDataAccessException(1))
                .thenReturn(mockedConfigInfoStateWrapper);
        
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        String appName = "appname";
        String content = "content111";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        String grayRule = "grayRule...";
        
        //execute
        ConfigOperateResult configOperateResult = externalConfigInfoGrayPersistService.insertOrUpdateGray(configInfo,
                grayName, grayRule, srcIp, srcUser);
        //expect return obj
        assertEquals(mockedConfigInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(mockedConfigInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        //verify add to be invoked
        Mockito.verify(jdbcTemplate, times(1))
                .update(anyString(), eq(dataId), eq(group), eq(tenant), eq(grayName), eq(grayRule),
                        eq(configInfo.getAppName()), eq(configInfo.getContent()), eq(configInfo.getEncryptedDataKey()),
                        eq(configInfo.getMd5()), eq(srcIp), eq(srcUser));
    }
    
    @Test
    public void testInsertOrUpdateGrayOfException() {
        String dataId = "grapDataId113";
        String group = "group113";
        String tenant = "tenant113";
        
        //mock exist gray
        ConfigInfoStateWrapper mockedConfigInfoStateWrapper = new ConfigInfoStateWrapper();
        mockedConfigInfoStateWrapper.setDataId(dataId);
        mockedConfigInfoStateWrapper.setGroup(group);
        mockedConfigInfoStateWrapper.setTenant(tenant);
        mockedConfigInfoStateWrapper.setId(123456L);
        mockedConfigInfoStateWrapper.setLastModified(System.currentTimeMillis());
        String grayName = "grayName...";
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(mockedConfigInfoStateWrapper);
        
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        String appName = "appname";
        String content = "content111";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        configInfo.setMd5("casMd5");
        String grayRule = "grayRule...";
        // mock update throw CannotGetJdbcConnectionException
        when(jdbcTemplate.update(anyString(), eq(configInfo.getContent()), eq(configInfo.getEncryptedDataKey()),
                    eq(MD5Utils.md5Hex(content, ENCODE)), eq(srcIp), eq(srcUser), eq(configInfo.getAppName()), eq(grayRule),
                    eq(dataId), eq(group), eq(tenant), eq(grayName))).thenThrow(
                        new CannotGetJdbcConnectionException("mock fail"));
        //execute of update& expect.
        try {
            externalConfigInfoGrayPersistService.insertOrUpdateGray(configInfo, grayName, grayRule, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("mock fail", exception.getMessage());
        }
        
        //mock query return null
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(null);
        //mock add throw CannotGetJdbcConnectionException
        when(jdbcTemplate.update(anyString(), eq(dataId), eq(group), eq(tenant), eq(grayName), eq(grayRule),
                eq(configInfo.getAppName()), eq(configInfo.getContent()), eq(configInfo.getEncryptedDataKey()),
                eq(MD5Utils.md5Hex(content, ENCODE)), eq(srcIp), eq(srcUser))).thenThrow(
                        new CannotGetJdbcConnectionException("mock fail add"));
        
        //execute of add& expect.
        try {
            externalConfigInfoGrayPersistService.insertOrUpdateGray(configInfo, grayName, grayRule, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("mock fail add", exception.getMessage());
        }
        
        //mock query throw CannotGetJdbcConnectionException
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenThrow(
                    new CannotGetJdbcConnectionException("get c fail"));
        //execute of add& expect.
        try {
            externalConfigInfoGrayPersistService.insertOrUpdateGray(configInfo, grayName, grayRule, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("get c fail", exception.getMessage());
        }
        
    }
    
    @Test
    public void testInsertOrUpdateGrayCasOfUpdate() {
        String dataId = "grayDataId113";
        String group = "group";
        String tenant = "tenant";
        
        //mock exist gray
        ConfigInfoStateWrapper mockedConfigInfoStateWrapper = new ConfigInfoStateWrapper();
        mockedConfigInfoStateWrapper.setDataId(dataId);
        mockedConfigInfoStateWrapper.setGroup(group);
        mockedConfigInfoStateWrapper.setTenant(tenant);
        mockedConfigInfoStateWrapper.setId(123456L);
        mockedConfigInfoStateWrapper.setLastModified(System.currentTimeMillis());
        String grayName = "grayName...";
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(mockedConfigInfoStateWrapper,
                mockedConfigInfoStateWrapper);
        
        //execute
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        String appName = "appname";
        String content = "content111";
        
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        configInfo.setMd5("casMd5");
        String grayRule = "grayRule...";
        //mock cas update
        when(jdbcTemplate.update(anyString(), eq(configInfo.getContent()), eq(MD5Utils.md5Hex(content, ENCODE)),
                eq(srcIp), eq(srcUser), eq(configInfo.getAppName()), eq(grayRule), eq(dataId), eq(group), eq(tenant),
                eq(grayName), eq(configInfo.getMd5()))).thenReturn(1);
        
        //mock exist config info
        ConfigInfoGrayWrapper configAllInfo4Gray = new ConfigInfoGrayWrapper();
        configAllInfo4Gray.setDataId(dataId);
        configAllInfo4Gray.setGroup(group);
        configAllInfo4Gray.setTenant(tenant);
        configAllInfo4Gray.setMd5("old_md5");
        String grayName1 = "grayName1...";
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER))).thenReturn(configAllInfo4Gray);
        
        ConfigOperateResult configOperateResult = externalConfigInfoGrayPersistService.insertOrUpdateGrayCas(configInfo,
                grayName, grayRule, srcIp, srcUser);
        //expect return obj
        assertEquals(mockedConfigInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(mockedConfigInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        //verify cas update to be invoked
        Mockito.verify(jdbcTemplate, times(1))
                .update(anyString(), eq(configInfo.getContent()), eq(MD5Utils.md5Hex(content, ENCODE)), eq(srcIp),
                        eq(srcUser), eq(configInfo.getAppName()), eq(grayRule), eq(dataId), eq(group), eq(tenant),
                        eq(grayName), eq(configInfo.getMd5()));
        
    }
    
    @Test
    public void testInsertOrUpdateGrayCasOfAdd() {
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
        String grayName = "grayName...";
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenThrow(new EmptyResultDataAccessException(1))
                .thenReturn(mockedConfigInfoStateWrapper);
        
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        String appName = "appname";
        String content = "content111";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        configInfo.setMd5("csMd5");
        String grayRule = "grayRule...";
        //execute
        ConfigOperateResult configOperateResult = externalConfigInfoGrayPersistService.insertOrUpdateGrayCas(configInfo,
                grayName, grayRule, srcIp, srcUser);
        //expect return obj
        assertEquals(mockedConfigInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(mockedConfigInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        //verify add to be invoked
        Mockito.verify(jdbcTemplate, times(1))
                .update(anyString(), eq(dataId), eq(group), eq(tenant), eq(grayName), eq(grayRule),
                        eq(configInfo.getAppName()), eq(configInfo.getContent()), eq(configInfo.getEncryptedDataKey()),
                        eq(MD5Utils.md5Hex(content, ENCODE)), eq(srcIp), eq(srcUser));
        
    }
    
    @Test
    public void testInsertOrUpdateGrayCasOfException() {
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
        String grayName = "grayName...";
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(mockedConfigInfoStateWrapper);
        
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        String appName = "appname";
        String content = "content111";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        configInfo.setMd5("casMd5");
        String grayRule = "grayRule...";
        // mock update throw CannotGetJdbcConnectionException
        when(jdbcTemplate.update(anyString(), eq(configInfo.getContent()), eq(MD5Utils.md5Hex(content, ENCODE)),
                eq(srcIp), eq(srcUser), eq(configInfo.getAppName()), eq(grayRule), eq(dataId), eq(group), eq(tenant),
                eq(grayName), eq(configInfo.getMd5()))).thenThrow(
                    new CannotGetJdbcConnectionException("updat mock fail"));
        
        //execute of update& expect.
        try {
            externalConfigInfoGrayPersistService.insertOrUpdateGrayCas(configInfo, grayName, grayRule, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("updat mock fail", exception.getMessage());
        }
        
        //mock query return null
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(null);
        //mock add throw CannotGetJdbcConnectionException
        when(jdbcTemplate.update(anyString(), eq(dataId), eq(group), eq(tenant), eq(grayName), eq(grayRule),
                eq(configInfo.getAppName()), eq(configInfo.getContent()), eq(configInfo.getEncryptedDataKey()),
                eq(MD5Utils.md5Hex(content, ENCODE)), eq(srcIp), eq(srcUser))).thenThrow(
                    new CannotGetJdbcConnectionException("mock fail add"));
        
        //execute of add& expect.
        try {
            externalConfigInfoGrayPersistService.insertOrUpdateGrayCas(configInfo, grayName, grayRule, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("mock fail add", exception.getMessage());
        }
        
        //mock query throw CannotGetJdbcConnectionException
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenThrow(
                    new CannotGetJdbcConnectionException("get c fail"));
        //execute of add& expect.
        try {
            externalConfigInfoGrayPersistService.insertOrUpdateGrayCas(configInfo, grayName, grayRule, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("get c fail", exception.getMessage());
        }
        
    }
    
    @Test
    void testRemoveConfigInfo() {
        String dataId = "dataId4567";
        String group = "group3456789";
        String tenant = "tenant4567890";
        final String grayName = "grayName1";
        
        //mock exist config info
        ConfigInfoGrayWrapper configAllInfo4Gray = new ConfigInfoGrayWrapper();
        configAllInfo4Gray.setDataId(dataId);
        configAllInfo4Gray.setGroup(group);
        configAllInfo4Gray.setTenant(tenant);
        configAllInfo4Gray.setMd5("old_md5");
        
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                        eq(CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER)))
                .thenReturn(configAllInfo4Gray);
        Mockito.when(databaseOperate.update(any())).thenReturn(true);
        
        String srcIp = "srcIp1234";
        String srcUser = "srcUser";
        externalConfigInfoGrayPersistService.removeConfigInfoGray(dataId, group, tenant, grayName, srcIp, srcUser);
        
        Mockito.verify(jdbcTemplate, times(1)).update(anyString(), eq(dataId), eq(group), eq(tenant), eq(grayName));
        Mockito.verify(historyConfigInfoPersistService, times(1)).insertConfigHistoryAtomic(
                eq(configAllInfo4Gray.getId()), eq(configAllInfo4Gray), eq(srcIp), eq(srcUser), any(Timestamp.class), eq("D"),
                eq("gray"), anyString());
        
        // Test the exception handling for CannotGetJdbcConnectionException
        when(jdbcTemplate.update(anyString(), eq(dataId), eq(group), eq(tenant), eq(grayName)))
                .thenThrow(new CannotGetJdbcConnectionException("mock fail11111"));
        
    }
    
    @Test
    public void testFindChangeConfigInfo4Gray() {
        List<ConfigInfoGrayWrapper> mockList = new ArrayList<>();
        mockList.add(new ConfigInfoGrayWrapper());
        mockList.add(new ConfigInfoGrayWrapper());
        mockList.add(new ConfigInfoGrayWrapper());
        mockList.get(0).setLastModified(System.currentTimeMillis());
        mockList.get(1).setLastModified(System.currentTimeMillis());
        mockList.get(2).setLastModified(System.currentTimeMillis());
        
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        
        long lastMaxId = 123;
        when(jdbcTemplate.query(anyString(), eq(new Object[] {timestamp, lastMaxId, 100}),
                eq(CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER))).thenReturn(mockList)
                .thenThrow(new CannotGetJdbcConnectionException("mock exception22"));
        
        List<ConfigInfoGrayWrapper> changeConfig = externalConfigInfoGrayPersistService.findChangeConfig(timestamp,
                lastMaxId, 100);
        assertTrue(changeConfig.get(0).getLastModified() == mockList.get(0).getLastModified());
        assertTrue(changeConfig.get(1).getLastModified() == mockList.get(1).getLastModified());
        assertTrue(changeConfig.get(2).getLastModified() == mockList.get(2).getLastModified());
        try {
            externalConfigInfoGrayPersistService.findChangeConfig(timestamp, lastMaxId, 100);
            assertTrue(false);
        } catch (CannotGetJdbcConnectionException exception) {
            assertEquals("mock exception22", exception.getMessage());
        }
        
    }
    
    @Test
    public void testFindConfigInfo4Gray() {
        String dataId = "dataId456789";
        String group = "group4567";
        String tenant = "tenant56789o0";
        String grayName = "gray12";
        //mock exist gray
        ConfigInfoGrayWrapper mockedConfigInfoStateWrapper = new ConfigInfoGrayWrapper();
        mockedConfigInfoStateWrapper.setDataId(dataId);
        mockedConfigInfoStateWrapper.setGroup(group);
        mockedConfigInfoStateWrapper.setGrayName(grayName);
        mockedConfigInfoStateWrapper.setTenant(tenant);
        mockedConfigInfoStateWrapper.setId(123456L);
        mockedConfigInfoStateWrapper.setLastModified(System.currentTimeMillis());
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER))).thenReturn(mockedConfigInfoStateWrapper);
        ConfigInfoGrayWrapper configInfo4GrayReturn = externalConfigInfoGrayPersistService.findConfigInfo4Gray(dataId,
                group, tenant, grayName);
        assertEquals(mockedConfigInfoStateWrapper, configInfo4GrayReturn);
        
        //mock query throw CannotGetJdbcConnectionException
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER))).thenThrow(
                    new CannotGetJdbcConnectionException("mock fail11111"));
        try {
            externalConfigInfoGrayPersistService.findConfigInfo4Gray(dataId, group, tenant, grayName);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("mock fail11111", exception.getMessage());
        }
        
        //mock query throw EmptyResultDataAccessException
        when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER))).thenThrow(new EmptyResultDataAccessException(1));
        ConfigInfoGrayWrapper configInfo4GrayNull = externalConfigInfoGrayPersistService.findConfigInfo4Gray(dataId,
                group, tenant, grayName);
        assertNull(configInfo4GrayNull);
    }
    
    @Test
    public void testConfigInfoGrayCount() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(101);
        int returnCount = externalConfigInfoGrayPersistService.configInfoGrayCount();
        assertEquals(101, returnCount);
    }
    
    @Test
    public void testFindAllConfigInfoGrayForDumpAll() {
        //mock count
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(12345);
        
        //mock page list
        List<ConfigInfoGrayWrapper> mockList = new ArrayList<>();
        mockList.add(new ConfigInfoGrayWrapper());
        mockList.add(new ConfigInfoGrayWrapper());
        mockList.add(new ConfigInfoGrayWrapper());
        mockList.get(0).setLastModified(System.currentTimeMillis());
        mockList.get(1).setLastModified(System.currentTimeMillis());
        mockList.get(2).setLastModified(System.currentTimeMillis());
        
        when(jdbcTemplate.query(anyString(), eq(new Object[] {}), eq(CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER))).thenReturn(
                mockList);
        
        int pageNo = 1;
        int pageSize = 101;
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(101);
        //execute & expect
        Page<ConfigInfoGrayWrapper> pageReturn = externalConfigInfoGrayPersistService.findAllConfigInfoGrayForDumpAll(
                pageNo, pageSize);
        assertEquals(mockList, pageReturn.getPageItems());
        assertEquals(101, pageReturn.getTotalCount());
        
        //mock count throw CannotGetJdbcConnectionException
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenThrow(
                new CannotGetJdbcConnectionException("345678909fail"));
        //execute &expect
        try {
            externalConfigInfoGrayPersistService.findAllConfigInfoGrayForDumpAll(pageNo, pageSize);
            assertTrue(false);
        } catch (Exception exception) {
            assertEquals("345678909fail", exception.getMessage());
        }
    }
    
}

