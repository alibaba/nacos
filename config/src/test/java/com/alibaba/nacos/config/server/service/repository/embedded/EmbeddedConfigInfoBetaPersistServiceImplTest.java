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
import com.alibaba.nacos.config.server.model.ConfigInfoBetaWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigOperateResult;
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
import java.util.List;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * test for embedded config beta.
 *
 * @author shiyiyue
 */
@ExtendWith(SpringExtension.class)
class EmbeddedConfigInfoBetaPersistServiceImplTest {
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<EmbeddedStorageContextHolder> embeddedStorageContextHolderMockedStatic;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    @Mock
    DatabaseOperate databaseOperate;
    
    private EmbeddedConfigInfoBetaPersistServiceImpl embeddedConfigInfoBetaPersistService;
    
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
        embeddedConfigInfoBetaPersistService = new EmbeddedConfigInfoBetaPersistServiceImpl(databaseOperate);
    }
    
    @AfterEach
    void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        embeddedStorageContextHolderMockedStatic.close();
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
        Mockito.when(
                        databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(mockedConfigInfoStateWrapper, mockedConfigInfoStateWrapper);
        //execute
        String betaIps = "betaips...";
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        String appName = "appname";
        String content = "content111";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        ConfigOperateResult configOperateResult = embeddedConfigInfoBetaPersistService.insertOrUpdateBeta(configInfo, betaIps, srcIp,
                srcUser);
        //expect return obj
        assertEquals(mockedConfigInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(mockedConfigInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        //verify update to be invoked
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(configInfo.getContent()),
                        eq(configInfo.getMd5()), eq(betaIps), eq(srcIp), eq(srcUser), eq(configInfo.getAppName()),
                        eq(configInfo.getEncryptedDataKey()), eq(dataId), eq(group), eq(tenant)), times(1));
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
        Mockito.when(
                        databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(null).thenReturn(mockedConfigInfoStateWrapper);
        
        String betaIps = "betaips...";
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        String appName = "appname";
        String content = "content111";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        //execute
        ConfigOperateResult configOperateResult = embeddedConfigInfoBetaPersistService.insertOrUpdateBeta(configInfo, betaIps, srcIp,
                srcUser);
        //expect return obj
        assertEquals(mockedConfigInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(mockedConfigInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        //verify add to be invoked
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(dataId), eq(group), eq(tenant),
                        eq(configInfo.getAppName()), eq(configInfo.getContent()), eq(configInfo.getMd5()),
                        eq(betaIps), eq(srcIp), eq(srcUser), eq(configInfo.getEncryptedDataKey())), times(1));
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
        Mockito.when(
                        databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(mockedConfigInfoStateWrapper, mockedConfigInfoStateWrapper);
        
        //execute
        
        String appName = "appname";
        String content = "content111";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        configInfo.setMd5("casMd5");
        //mock cas update
        Mockito.when(databaseOperate.blockUpdate()).thenReturn(true);
        String betaIps = "betaips...";
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        ConfigOperateResult configOperateResult = embeddedConfigInfoBetaPersistService.insertOrUpdateBetaCas(configInfo, betaIps, srcIp,
                srcUser);
        //expect return obj
        assertEquals(mockedConfigInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(mockedConfigInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        //verify cas update to be invoked
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(configInfo.getContent()),
                        eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)), eq(betaIps), eq(srcIp), eq(srcUser),
                        eq(appName), eq(dataId), eq(group), eq(tenant), eq(configInfo.getMd5())), times(1));
        
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
        Mockito.when(
                        databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(null).thenReturn(mockedConfigInfoStateWrapper);
        
        String betaIps = "betaips...";
        String srcIp = "srcUp...";
        String srcUser = "srcUser...";
        String appName = "appname";
        String content = "content111";
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key34567");
        //execute
        ConfigOperateResult configOperateResult = embeddedConfigInfoBetaPersistService.insertOrUpdateBetaCas(configInfo, betaIps, srcIp,
                srcUser);
        //expect return obj
        assertEquals(mockedConfigInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(mockedConfigInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        //verify add to be invoked
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(dataId), eq(group), eq(tenant),
                        eq(configInfo.getAppName()), eq(configInfo.getContent()), eq(configInfo.getMd5()),
                        eq(betaIps), eq(srcIp), eq(srcUser), eq(configInfo.getEncryptedDataKey())), times(1));
        
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
        Mockito.when(
                        databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}), eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER)))
                .thenReturn(mockedConfigInfoStateWrapper);
        //mock remove ok
        Mockito.when(databaseOperate.update(any(List.class))).thenReturn(true);
        
        embeddedConfigInfoBetaPersistService.removeConfigInfo4Beta(dataId, group, tenant);
        //verity
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(dataId), eq(group), eq(tenant)), times(1));
        
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
        Mockito.when(
                        databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant}), eq(CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER)))
                .thenReturn(mockedConfigInfoStateWrapper);
        ConfigInfoBetaWrapper configInfo4BetaReturn = embeddedConfigInfoBetaPersistService.findConfigInfo4Beta(dataId, group, tenant);
        assertEquals(mockedConfigInfoStateWrapper, configInfo4BetaReturn);
        
    }
    
    @Test
    void testConfigInfoBetaCount() {
        Mockito.when(databaseOperate.queryOne(anyString(), eq(Integer.class))).thenReturn(101);
        int returnCount = embeddedConfigInfoBetaPersistService.configInfoBetaCount();
        assertEquals(101, returnCount);
    }
    
    @Test
    void testFindAllConfigInfoBetaForDumpAll() {
        //mock count
        Mockito.when(databaseOperate.queryOne(anyString(), eq(Integer.class))).thenReturn(12345);
        
        //mock page list
        List<ConfigInfoBetaWrapper> mockList = new ArrayList<>();
        mockList.add(new ConfigInfoBetaWrapper());
        mockList.add(new ConfigInfoBetaWrapper());
        mockList.add(new ConfigInfoBetaWrapper());
        mockList.get(0).setLastModified(System.currentTimeMillis());
        mockList.get(1).setLastModified(System.currentTimeMillis());
        mockList.get(2).setLastModified(System.currentTimeMillis());
        
        Mockito.when(databaseOperate.queryMany(anyString(), eq(new Object[] {}), eq(CONFIG_INFO_BETA_WRAPPER_ROW_MAPPER)))
                .thenReturn(mockList);
        
        int pageNo = 1;
        int pageSize = 101;
        Mockito.when(databaseOperate.queryOne(anyString(), eq(Integer.class))).thenReturn(101);
        //execute & expect
        Page<ConfigInfoBetaWrapper> pageReturn = embeddedConfigInfoBetaPersistService.findAllConfigInfoBetaForDumpAll(pageNo, pageSize);
        assertEquals(mockList, pageReturn.getPageItems());
        assertEquals(101, pageReturn.getTotalCount());
        
    }
    
}
