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
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
class EmbeddedConfigInfoTagPersistServiceImplTest {
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<EmbeddedStorageContextHolder> embeddedStorageContextHolderMockedStatic;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    @Mock
    DatabaseOperate databaseOperate;
    
    private EmbeddedConfigInfoTagPersistServiceImpl embeddedConfigInfoTagPersistService;
    
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
        embeddedConfigInfoTagPersistService = new EmbeddedConfigInfoTagPersistServiceImpl(databaseOperate);
    }
    
    @AfterEach
    void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        embeddedStorageContextHolderMockedStatic.close();
    }
    
    @Test
    void testInsertOrUpdateTagOfAdd() {
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
        String tag = "tag123";
        
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(null).thenReturn(configInfoStateWrapper);
        
        String srcIp = "ip345678";
        String srcUser = "user1234567";
        ConfigOperateResult configOperateResult = embeddedConfigInfoTagPersistService.insertOrUpdateTag(configInfo, tag, srcIp, srcUser);
        
        //mock insert invoked.
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(dataId), eq(group), eq(tenant), eq(tag), eq(appName),
                        eq(content), eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)), eq(srcIp), eq(srcUser), any(Timestamp.class),
                        any(Timestamp.class)), times(1));
        assertEquals(configInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(configInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        
    }
    
    @Test
    void testInsertOrUpdateTagOfUpdate() {
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
        String tag = "tag123";
        
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(new ConfigInfoStateWrapper()).thenReturn(configInfoStateWrapper);
        String srcIp = "ip345678";
        String srcUser = "user1234567";
        ConfigOperateResult configOperateResult = embeddedConfigInfoTagPersistService.insertOrUpdateTag(configInfo, tag, srcIp, srcUser);
        //verify update to be invoked
        embeddedStorageContextHolderMockedStatic.verify(() -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(content),
                eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)), eq(srcIp), eq(srcUser), any(Timestamp.class), eq(appName),
                eq(dataId), eq(group), eq(tenant), eq(tag)), times(1));
        assertEquals(configInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(configInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        
    }
    
    @Test
    void testInsertOrUpdateTagCasOfAdd() {
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
        String tag = "tag123";
        
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(null).thenReturn(configInfoStateWrapper);
        
        String srcIp = "ip345678";
        String srcUser = "user1234567";
        ConfigOperateResult configOperateResult = embeddedConfigInfoTagPersistService.insertOrUpdateTagCas(configInfo, tag, srcIp, srcUser);
        //verify insert to be invoked
        //mock insert invoked.
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(dataId), eq(group), eq(tenant), eq(tag), eq(appName),
                        eq(content), eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)), eq(srcIp), eq(srcUser), any(Timestamp.class),
                        any(Timestamp.class)), times(1));
        assertEquals(configInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(configInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
        
    }
    
    @Test
    void testInsertOrUpdateTagCasOfUpdate() {
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
        String tag = "tag123";
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(new ConfigInfoStateWrapper()).thenReturn(configInfoStateWrapper);
        String srcIp = "ip345678";
        String srcUser = "user1234567";
        
        //mock cas update return 1
        Mockito.when(databaseOperate.blockUpdate()).thenReturn(true);
        ConfigOperateResult configOperateResult = embeddedConfigInfoTagPersistService.insertOrUpdateTagCas(configInfo, tag, srcIp, srcUser);
        //verify update to be invoked
        embeddedStorageContextHolderMockedStatic.verify(() -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(content),
                eq(MD5Utils.md5Hex(content, Constants.PERSIST_ENCODE)), eq(srcIp), eq(srcUser), any(Timestamp.class), eq(appName),
                eq(dataId), eq(group), eq(tenant), eq(tag), eq(configInfo.getMd5())), times(1));
        assertEquals(configInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(configInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
    }
    
    @Test
    void testRemoveConfigInfoTag() {
        String dataId = "dataId1112222";
        String group = "group22";
        String tenant = "tenant2";
        String tag = "tag123345";
        String srcIp = "ip345678";
        String srcUser = "user1234567";
        embeddedConfigInfoTagPersistService.removeConfigInfoTag(dataId, group, tenant, tag, srcIp, srcUser);
        //verify delete sql invoked.
        embeddedStorageContextHolderMockedStatic.verify(
                () -> EmbeddedStorageContextHolder.addSqlContext(anyString(), eq(dataId), eq(group), eq(tenant), eq(tag)), times(1));
    }
    
    @Test
    void testFindConfigInfo4Tag() {
        String dataId = "dataId1112222";
        String group = "group22";
        String tenant = "tenant2";
        String tag = "tag123345";
        
        //mock query tag return obj
        ConfigInfoTagWrapper configInfoTagWrapperMocked = new ConfigInfoTagWrapper();
        configInfoTagWrapperMocked.setLastModified(System.currentTimeMillis());
        
        Mockito.when(databaseOperate.queryOne(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                eq(CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER))).thenReturn(configInfoTagWrapperMocked);
        
        ConfigInfoTagWrapper configInfo4TagReturn = embeddedConfigInfoTagPersistService.findConfigInfo4Tag(dataId, group, tenant, tag);
        assertEquals(configInfoTagWrapperMocked, configInfo4TagReturn);
    }
    
    @Test
    void testConfigInfoTagCount() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        
        //mock count
        Mockito.when(databaseOperate.queryOne(anyString(), eq(Integer.class))).thenReturn(308);
        //execute & verify
        int count = embeddedConfigInfoTagPersistService.configInfoTagCount();
        assertEquals(308, count);
    }
    
    @Test
    void testFindAllConfigInfoTagForDumpAll() {
        
        //mock count
        Mockito.when(databaseOperate.queryOne(anyString(), eq(Integer.class))).thenReturn(308);
        List<ConfigInfoTagWrapper> mockTagList = new ArrayList<>();
        mockTagList.add(new ConfigInfoTagWrapper());
        mockTagList.add(new ConfigInfoTagWrapper());
        mockTagList.add(new ConfigInfoTagWrapper());
        mockTagList.get(0).setLastModified(System.currentTimeMillis());
        mockTagList.get(1).setLastModified(System.currentTimeMillis());
        mockTagList.get(2).setLastModified(System.currentTimeMillis());
        //mock query list
        Mockito.when(databaseOperate.queryMany(anyString(), eq(new Object[] {}), eq(CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER)))
                .thenReturn(mockTagList);
        int pageNo = 3;
        int pageSize = 100;
        //execute & verify
        Page<ConfigInfoTagWrapper> returnTagPage = embeddedConfigInfoTagPersistService.findAllConfigInfoTagForDumpAll(pageNo, pageSize);
        assertEquals(308, returnTagPage.getTotalCount());
        assertEquals(mockTagList, returnTagPage.getPageItems());
    }
    
    @Test
    void testFindConfigInfoTags() {
        String dataId = "dataId1112222";
        String group = "group22";
        String tenant = "tenant2";
        List<String> mockedTags = Arrays.asList("tags1", "tags11", "tags111");
        Mockito.when(databaseOperate.queryMany(anyString(), eq(new Object[] {dataId, group, tenant}), eq(String.class)))
                .thenReturn(mockedTags);
        List<String> configInfoTags = embeddedConfigInfoTagPersistService.findConfigInfoTags(dataId, group, tenant);
        assertEquals(mockedTags, configInfoTags);
    }
}
