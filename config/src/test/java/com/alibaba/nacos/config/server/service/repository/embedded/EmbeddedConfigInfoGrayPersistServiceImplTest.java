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

import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.ConfigInfo;
import com.alibaba.nacos.config.server.model.ConfigInfoGrayWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
import com.alibaba.nacos.config.server.service.repository.HistoryConfigInfoPersistService;
import com.alibaba.nacos.core.distributed.id.IdGeneratorManager;
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
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * test for embedded config tag.
 *
 * @author shiyiyue
 */
@ExtendWith(SpringExtension.class)
public class EmbeddedConfigInfoGrayPersistServiceImplTest {
    
    private EmbeddedConfigInfoGrayPersistServiceImpl embeddedConfigInfoGrayPersistService;
    
    @Mock
    private DataSourceService dataSourceService;
    
    @Mock
    private IdGeneratorManager idGeneratorManager;
    
    @Mock
    private HistoryConfigInfoPersistService historyConfigInfoPersistService;
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<EmbeddedStorageContextHolder> embeddedStorageContextHolderMockedStatic;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    @Mock
    DatabaseOperate databaseOperate;
    
    /**
     * before test.
     */
    @BeforeEach
    public void before() {
        embeddedStorageContextHolderMockedStatic = Mockito.mockStatic(EmbeddedStorageContextHolder.class);
        dynamicDataSourceMockedStatic = Mockito.mockStatic(DynamicDataSource.class);
        envUtilMockedStatic = Mockito.mockStatic(EnvUtil.class);
        when(DynamicDataSource.getInstance()).thenReturn(dynamicDataSource);
        when(dynamicDataSource.getDataSource()).thenReturn(dataSourceService);
        when(dataSourceService.getDataSourceType()).thenReturn("derby");
        envUtilMockedStatic.when(() -> EnvUtil.getProperty(anyString(), eq(Boolean.class), eq(false)))
                .thenReturn(false);
        embeddedConfigInfoGrayPersistService = new EmbeddedConfigInfoGrayPersistServiceImpl(databaseOperate,
                idGeneratorManager, historyConfigInfoPersistService);
    }
    
    /**
     * after each case.
     */
    @AfterEach
    public void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        embeddedStorageContextHolderMockedStatic.close();
    }
    
    @Test
    public void testInsertOrUpdateGrayOfAdd() {
        String dataId = "dataId111222";
        String group = "group";
        String tenant = "tenant";
        String appName = "appname1234";
        String content = "c12345";
        
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key23456");
        //mock query config state empty and return obj after insert
        ConfigInfoStateWrapper configInfoStateWrapper = new ConfigInfoStateWrapper();
        configInfoStateWrapper.setLastModified(System.currentTimeMillis());
        configInfoStateWrapper.setId(234567890L);
        String grayName = "tag123grayName";
        String grayRule = "";
        
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(null).thenReturn(configInfoStateWrapper);
        
        String srcIp = "ip345678";
        String srcUser = "user1234567";
        ConfigOperateResult configOperateResult = embeddedConfigInfoGrayPersistService.insertOrUpdateGray(configInfo,
                grayName, grayRule, srcIp, srcUser);
        
        //mock insert invoked.
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), any(), eq(dataId), eq(group), eq(tenant),
                        eq(grayName), eq(grayRule), eq(appName), eq(content),
                        eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)), eq(srcIp), eq(srcUser),
                        any(Timestamp.class), any(Timestamp.class)), times(1));
        
        Mockito.verify(historyConfigInfoPersistService, times(1)).insertConfigHistoryAtomic(
                eq(configInfo.getId()), eq(configInfo), eq(srcIp), eq(srcUser), any(Timestamp.class), eq("I"),
                eq("gray"), anyString());
        assertEquals(configInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(configInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        
    }
    
    @Test
    public void testInsertOrUpdateGrayOfUpdate() {
        String dataId = "dataId111222";
        String group = "group";
        String tenant = "tenant";
        String appName = "appname1234";
        String content = "c12345";
        
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key23456");
        //mock query config state and return obj after update
        ConfigInfoStateWrapper configInfoStateWrapper = new ConfigInfoStateWrapper();
        configInfoStateWrapper.setLastModified(System.currentTimeMillis());
        configInfoStateWrapper.setId(234567890L);
        String grayName = "tag123grayName";
        final String grayRule = "tag123grayrule";
        
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                        eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(new ConfigInfoStateWrapper())
                .thenReturn(configInfoStateWrapper);
        
        //mock exist config info
        ConfigInfoGrayWrapper configAllInfo4Gray = new ConfigInfoGrayWrapper();
        configAllInfo4Gray.setDataId(dataId);
        configAllInfo4Gray.setGroup(group);
        configAllInfo4Gray.setTenant(tenant);
        configAllInfo4Gray.setMd5("old_md5");
        configAllInfo4Gray.setSrcUser("user");
        when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER))).thenReturn(configAllInfo4Gray);
        
        String srcIp = "ip345678";
        String srcUser = "user1234567";
        ConfigOperateResult configOperateResult = embeddedConfigInfoGrayPersistService.insertOrUpdateGray(configInfo,
                grayName, grayRule, srcIp, srcUser);
        //verify update to be invoked
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(content),
                        eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)), eq(srcIp), eq(srcUser),
                        any(Timestamp.class), eq(appName), eq(grayRule), eq(dataId), eq(group), eq(tenant),
                        eq(grayName)), times(1));
        Mockito.verify(historyConfigInfoPersistService, times(1)).insertConfigHistoryAtomic(
                eq(configAllInfo4Gray.getId()), eq(configAllInfo4Gray), eq(srcIp), eq(srcUser), any(Timestamp.class), eq("U"),
                eq("gray"), anyString());
        assertEquals(configInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(configInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        
    }
    
    @Test
    public void testInsertOrUpdateGrayCasOfAdd() {
        String dataId = "dataId111222";
        String group = "group";
        String tenant = "tenant";
        String appName = "appname1234";
        String content = "c12345";
        
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key23456");
        configInfo.setMd5("casMd5");
        //mock query config state empty and return obj after insert
        ConfigInfoStateWrapper configInfoStateWrapper = new ConfigInfoStateWrapper();
        configInfoStateWrapper.setLastModified(System.currentTimeMillis());
        configInfoStateWrapper.setId(234567890L);
        String grayName = "tag123grayName";
        String grayRule = "";
        
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(null).thenReturn(configInfoStateWrapper);
        
        String srcIp = "ip345678";
        String srcUser = "user1234567";
        ConfigOperateResult configOperateResult = embeddedConfigInfoGrayPersistService.insertOrUpdateGrayCas(configInfo,
                grayName, grayRule, srcIp, srcUser);
        //verify insert to be invoked
        //mock insert invoked.
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), any(), eq(dataId), eq(group), eq(tenant),
                        eq(grayName), eq(grayRule), eq(appName), eq(content),
                        eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)), eq(srcIp), eq(srcUser),
                        any(Timestamp.class), any(Timestamp.class)), times(1));
        Mockito.verify(historyConfigInfoPersistService, times(1)).insertConfigHistoryAtomic(
                eq(configInfo.getId()), eq(configInfo), eq(srcIp), eq(srcUser), any(Timestamp.class), eq("I"),
                eq("gray"), anyString());
        assertEquals(configInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(configInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        
    }
    
    @Test
    public void testInsertOrUpdateGrayCasOfUpdate() {
        String dataId = "dataId111222";
        String group = "group";
        String tenant = "tenant";
        String appName = "appname1234";
        String content = "c12345";
        
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key23456");
        configInfo.setMd5("casMd5");
        //mock query config state and return obj after update
        ConfigInfoStateWrapper configInfoStateWrapper = new ConfigInfoStateWrapper();
        configInfoStateWrapper.setLastModified(System.currentTimeMillis());
        configInfoStateWrapper.setId(234567890L);
        String grayName = "tag123grayName";
        final String grayRule = "";
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                        eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(new ConfigInfoStateWrapper())
                .thenReturn(configInfoStateWrapper);
        
        //mock exist config info
        ConfigInfoGrayWrapper configAllInfo4Gray = new ConfigInfoGrayWrapper();
        configAllInfo4Gray.setDataId(dataId);
        configAllInfo4Gray.setGroup(group);
        configAllInfo4Gray.setTenant(tenant);
        configAllInfo4Gray.setMd5("old_md5");
        configAllInfo4Gray.setSrcUser("user");
        when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER))).thenReturn(configAllInfo4Gray);
        
        String srcIp = "ip345678";
        String srcUser = "user1234567";
        
        //mock cas update return 1
        Mockito.when(databaseOperate.blockUpdate()).thenReturn(true);
        ConfigOperateResult configOperateResult = embeddedConfigInfoGrayPersistService.insertOrUpdateGrayCas(configInfo,
                grayName, grayRule, srcIp, srcUser);
        //verify update to be invoked
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(content),
                        eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)), eq(srcIp), eq(srcUser), eq(appName),
                        eq(grayRule), eq(dataId), eq(group), eq(tenant), eq(grayName), eq(configInfo.getMd5())),
                times(1));
        Mockito.verify(historyConfigInfoPersistService, times(1)).insertConfigHistoryAtomic(
                eq(configAllInfo4Gray.getId()), eq(configAllInfo4Gray), eq(srcIp), eq(srcUser), any(Timestamp.class), eq("U"),
                eq("gray"), anyString());
        assertEquals(configInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(configInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
    }
    
    @Test
    public void testRemoveConfigInfoGrayName() {
        String dataId = "dataId1112222";
        String group = "group22";
        String tenant = "tenant2";
        final String srcIp = "ip345678";
        final String srcUser = "user1234567";
        final String grayName = "grayName...";
        
        //mock exist config info
        ConfigInfoGrayWrapper configAllInfo4Gray = new ConfigInfoGrayWrapper();
        configAllInfo4Gray.setDataId(dataId);
        configAllInfo4Gray.setGroup(group);
        configAllInfo4Gray.setTenant(tenant);
        configAllInfo4Gray.setMd5("old_md5");
        
        when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER))).thenReturn(configAllInfo4Gray);
        
        embeddedConfigInfoGrayPersistService.removeConfigInfoGray(dataId, group, tenant, grayName, srcIp, srcUser);
        
        //verify delete sql invoked.
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(dataId), eq(group), eq(tenant),
                        eq(grayName)), times(1));
        Mockito.verify(historyConfigInfoPersistService, times(1)).insertConfigHistoryAtomic(
                eq(configAllInfo4Gray.getId()), eq(configAllInfo4Gray), eq(srcIp), eq(srcUser), any(Timestamp.class), eq("D"),
                eq("gray"), anyString());
    }
    
    @Test
    public void testFindConfigInfo4Gray() {
        String dataId = "dataId1112222";
        String group = "group22";
        String tenant = "tenant2";
        String grayName = "tag123345";
        
        //mock query tag return obj
        ConfigInfoGrayWrapper configInfoGrayWrapperMocked = new ConfigInfoGrayWrapper();
        configInfoGrayWrapperMocked.setLastModified(System.currentTimeMillis());
        
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, grayName}),
                eq(CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER))).thenReturn(configInfoGrayWrapperMocked);
        
        ConfigInfoGrayWrapper configInfo4GrayReturn = embeddedConfigInfoGrayPersistService.findConfigInfo4Gray(dataId,
                group, tenant, grayName);
        assertEquals(configInfoGrayWrapperMocked, configInfo4GrayReturn);
    }
    
    @Test
    public void testConfigInfoGrayCount() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        
        //mock count
        Mockito.when(databaseOperate.queryOne(anyString(), eq(Integer.class))).thenReturn(308);
        //execute & verify
        int count = embeddedConfigInfoGrayPersistService.configInfoGrayCount();
        assertEquals(308, count);
    }
    
    @Test
    public void testFindAllConfigInfoGrayForDumpAll() {
        
        //mock count
        Mockito.when(databaseOperate.queryOne(anyString(), eq(Integer.class))).thenReturn(308);
        List<ConfigInfoGrayWrapper> mockGrayList = new ArrayList<>();
        mockGrayList.add(new ConfigInfoGrayWrapper());
        mockGrayList.add(new ConfigInfoGrayWrapper());
        mockGrayList.add(new ConfigInfoGrayWrapper());
        mockGrayList.get(0).setLastModified(System.currentTimeMillis());
        mockGrayList.get(1).setLastModified(System.currentTimeMillis());
        mockGrayList.get(2).setLastModified(System.currentTimeMillis());
        //mock query list
        Mockito.when(
                        databaseOperate.queryMany(anyString(), eq(new Object[] {}), eq(CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER)))
                .thenReturn(mockGrayList);
        int pageNo = 3;
        int pageSize = 100;
        //execute & verify
        Page<ConfigInfoGrayWrapper> returnGrayPage = embeddedConfigInfoGrayPersistService.findAllConfigInfoGrayForDumpAll(
                pageNo, pageSize);
        assertEquals(308, returnGrayPage.getTotalCount());
        assertEquals(mockGrayList, returnGrayPage.getPageItems());
    }
    
    @Test
    public void testFindConfigInfoGrays() {
        String dataId = "dataId1112222";
        String group = "group22";
        String tenant = "tenant2";
        List<String> mockedGrays = Arrays.asList("tags1", "tags11", "tags111");
        Mockito.when(databaseOperate.queryMany(anyString(), eq(new Object[] {dataId, group, tenant}), eq(String.class)))
                .thenReturn(mockedGrays);
        List<String> configInfoGrays = embeddedConfigInfoGrayPersistService.findConfigInfoGrays(dataId, group, tenant);
        assertEquals(mockedGrays, configInfoGrays);
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
        long lastMaxId = 123;
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        when(databaseOperate.queryMany(anyString(), eq(new Object[] {timestamp, lastMaxId, 100}),
                eq(CONFIG_INFO_GRAY_WRAPPER_ROW_MAPPER))).thenReturn(mockList)
                .thenThrow(new CannotGetJdbcConnectionException("mock exception22"));
        
        List<ConfigInfoGrayWrapper> changeConfig = embeddedConfigInfoGrayPersistService.findChangeConfig(timestamp,
                lastMaxId, 100);
        assertTrue(changeConfig.get(0).getLastModified() == mockList.get(0).getLastModified());
        assertTrue(changeConfig.get(1).getLastModified() == mockList.get(1).getLastModified());
        assertTrue(changeConfig.get(2).getLastModified() == mockList.get(2).getLastModified());
        try {
            embeddedConfigInfoGrayPersistService.findChangeConfig(timestamp, lastMaxId, 100);
            assertTrue(false);
        } catch (CannotGetJdbcConnectionException exception) {
            assertEquals("mock exception22", exception.getMessage());
        }
        
    }
    
}
