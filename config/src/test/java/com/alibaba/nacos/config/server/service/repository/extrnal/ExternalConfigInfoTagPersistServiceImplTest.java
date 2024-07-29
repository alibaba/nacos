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
import com.alibaba.nacos.config.server.model.ConfigInfoStateWrapper;
import com.alibaba.nacos.config.server.model.ConfigInfoTagWrapper;
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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER;
import static com.alibaba.nacos.config.server.service.repository.ConfigRowMapperInjector.CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class ExternalConfigInfoTagPersistServiceImplTest {
    
    MockedStatic<EnvUtil> envUtilMockedStatic;
    
    MockedStatic<ExternalStorageUtils> externalStorageUtilsMockedStatic;
    
    MockedStatic<DynamicDataSource> dynamicDataSourceMockedStatic;
    
    @Mock
    DynamicDataSource dynamicDataSource;
    
    private ExternalConfigInfoTagPersistServiceImpl externalConfigInfoTagPersistService;
    
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
        externalConfigInfoTagPersistService = new ExternalConfigInfoTagPersistServiceImpl();
    }
    
    @AfterEach
    void after() {
        dynamicDataSourceMockedStatic.close();
        envUtilMockedStatic.close();
        externalStorageUtilsMockedStatic.close();
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
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                        eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenThrow(new EmptyResultDataAccessException(1))
                .thenReturn(configInfoStateWrapper);
        String srcIp = "ip345678";
        String srcUser = "user1234567";
        ConfigOperateResult configOperateResult = externalConfigInfoTagPersistService.insertOrUpdateTag(configInfo, tag, srcIp, srcUser);
        //verify insert to be invoked
        Mockito.verify(jdbcTemplate, times(1))
                .update(anyString(), eq(dataId), eq(group), eq(tenant), eq(tag), eq(appName), eq(configInfo.getContent()),
                        eq(configInfo.getMd5()), eq(srcIp), eq(srcUser), any(Timestamp.class), any(Timestamp.class));
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
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(new ConfigInfoStateWrapper()).thenReturn(configInfoStateWrapper);
        String srcIp = "ip345678";
        String srcUser = "user1234567";
        ConfigOperateResult configOperateResult = externalConfigInfoTagPersistService.insertOrUpdateTag(configInfo, tag, srcIp, srcUser);
        //verify update to be invoked
        Mockito.verify(jdbcTemplate, times(1))
                .update(anyString(), eq(configInfo.getContent()), eq(configInfo.getMd5()), eq(srcIp), eq(srcUser), any(Timestamp.class),
                        eq(appName), eq(dataId), eq(group), eq(tenant), eq(tag));
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
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                        eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenThrow(new EmptyResultDataAccessException(1))
                .thenReturn(configInfoStateWrapper);
        String srcIp = "ip345678";
        String srcUser = "user1234567";
        ConfigOperateResult configOperateResult = externalConfigInfoTagPersistService.insertOrUpdateTagCas(configInfo, tag, srcIp, srcUser);
        //verify insert to be invoked
        Mockito.verify(jdbcTemplate, times(1))
                .update(anyString(), eq(dataId), eq(group), eq(tenant), eq(tag), eq(appName), eq(configInfo.getContent()),
                        eq(MD5Utils.md5Hex(configInfo.getContent(), Constants.PERSIST_ENCODE)), eq(srcIp), eq(srcUser),
                        any(Timestamp.class), any(Timestamp.class));
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
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(new ConfigInfoStateWrapper()).thenReturn(configInfoStateWrapper);
        String srcIp = "ip345678";
        String srcUser = "user1234567";
        
        //mock cas update return 1
        Mockito.when(jdbcTemplate.update(anyString(), eq(configInfo.getContent()),
                eq(MD5Utils.md5Hex(configInfo.getContent(), Constants.PERSIST_ENCODE)), eq(srcIp), eq(srcUser), any(Timestamp.class),
                eq(appName), eq(dataId), eq(group), eq(tenant), eq(tag), eq(configInfo.getMd5()))).thenReturn(1);
        ConfigOperateResult configOperateResult = externalConfigInfoTagPersistService.insertOrUpdateTagCas(configInfo, tag, srcIp, srcUser);
        //verify update to be invoked
        Mockito.verify(jdbcTemplate, times(1))
                .update(anyString(), eq(configInfo.getContent()), eq(MD5Utils.md5Hex(configInfo.getContent(), Constants.PERSIST_ENCODE)),
                        eq(srcIp), eq(srcUser), any(Timestamp.class), eq(appName), eq(dataId), eq(group), eq(tenant), eq(tag),
                        eq(configInfo.getMd5()));
        assertEquals(configInfoStateWrapper.getId(), configOperateResult.getId());
        assertEquals(configInfoStateWrapper.getLastModified(), configOperateResult.getLastModified());
    }
    
    @Test
    void testInsertOrUpdateTagCasOfException() {
        String dataId = "dataId111222";
        String group = "group";
        String tenant = "tenant";
        String appName = "appname1234";
        String content = "c12345";
        
        ConfigInfo configInfo = new ConfigInfo(dataId, group, tenant, appName, content);
        configInfo.setEncryptedDataKey("key23456");
        configInfo.setMd5("casMd5");
        //mock query config state CannotGetJdbcConnectionException
        ConfigInfoStateWrapper configInfoStateWrapper = new ConfigInfoStateWrapper();
        configInfoStateWrapper.setLastModified(System.currentTimeMillis());
        configInfoStateWrapper.setId(234567890L);
        String tag = "tag123";
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenThrow(new CannotGetJdbcConnectionException("state query throw exception"));
        String srcIp = "ip345678";
        String srcUser = "user1234567";
        try {
            externalConfigInfoTagPersistService.insertOrUpdateTagCas(configInfo, tag, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("state query throw exception", e.getMessage());
        }
        //mock get state return null,and execute add throw CannotGetJdbcConnectionException
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(null);
        Mockito.when(jdbcTemplate.update(anyString(), eq(dataId), eq(group), eq(tenant), eq(tag), eq(appName), eq(configInfo.getContent()),
                eq(MD5Utils.md5Hex(configInfo.getContent(), Constants.PERSIST_ENCODE)), eq(srcIp), eq(srcUser), any(Timestamp.class),
                any(Timestamp.class))).thenThrow(new CannotGetJdbcConnectionException("throw exception add config tag"));
        try {
            externalConfigInfoTagPersistService.insertOrUpdateTagCas(configInfo, tag, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("throw exception add config tag", e.getMessage());
        }
        
        //mock get state return obj,and execute update throw CannotGetJdbcConnectionException
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                eq(CONFIG_INFO_STATE_WRAPPER_ROW_MAPPER))).thenReturn(configInfoStateWrapper);
        Mockito.when(jdbcTemplate.update(anyString(), eq(configInfo.getContent()),
                        eq(MD5Utils.md5Hex(configInfo.getContent(), Constants.PERSIST_ENCODE)), eq(srcIp), eq(srcUser), any(Timestamp.class),
                        eq(appName), eq(dataId), eq(group), eq(tenant), eq(tag), eq(configInfo.getMd5())))
                .thenThrow(new CannotGetJdbcConnectionException("throw exception update config tag"));
        try {
            externalConfigInfoTagPersistService.insertOrUpdateTagCas(configInfo, tag, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("throw exception update config tag", e.getMessage());
        }
    }
    
    @Test
    void testRemoveConfigInfoTag() {
        String dataId = "dataId1112222";
        String group = "group22";
        String tenant = "tenant2";
        String tag = "tag123345";
        String srcIp = "ip345678";
        String srcUser = "user1234567";
        //Mockito.when(jdbcTemplate.update(anyString(),eq(dataId),eq(group),eq(tenant),eq(tag))).thenReturn()
        //verify delete sql invoked.
        externalConfigInfoTagPersistService.removeConfigInfoTag(dataId, group, tenant, tag, srcIp, srcUser);
        Mockito.verify(jdbcTemplate, times(1)).update(anyString(), eq(dataId), eq(group), eq(tenant), eq(tag));
        
        //mock delete throw CannotGetJdbcConnectionException
        Mockito.when(jdbcTemplate.update(anyString(), eq(dataId), eq(group), eq(tenant), eq(tag)))
                .thenThrow(new CannotGetJdbcConnectionException("delete fail"));
        try {
            externalConfigInfoTagPersistService.removeConfigInfoTag(dataId, group, tenant, tag, srcIp, srcUser);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("delete fail", e.getMessage());
        }
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
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                eq(CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER))).thenReturn(configInfoTagWrapperMocked);
        
        ConfigInfoTagWrapper configInfo4TagReturn = externalConfigInfoTagPersistService.findConfigInfo4Tag(dataId, group, tenant, tag);
        assertEquals(configInfoTagWrapperMocked, configInfo4TagReturn);
        //mock query tag throw EmptyResultDataAccessException
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                eq(CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER))).thenThrow(new EmptyResultDataAccessException(1));
        ConfigInfoTagWrapper configInfo4Tag = externalConfigInfoTagPersistService.findConfigInfo4Tag(dataId, group, tenant, tag);
        assertNull(configInfo4Tag);
        
        //mock query tag throw CannotGetJdbcConnectionException
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(new Object[] {dataId, group, tenant, tag}),
                eq(CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER))).thenThrow(new CannotGetJdbcConnectionException("con error"));
        try {
            externalConfigInfoTagPersistService.findConfigInfo4Tag(dataId, group, tenant, tag);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("con error", e.getMessage());
        }
    }
    
    @Test
    void testConfigInfoTagCount() {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        
        //mock count
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(308);
        //execute & verify
        int count = externalConfigInfoTagPersistService.configInfoTagCount();
        assertEquals(308, count);
        
        //mock count is null
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(null);
        //execute & verify
        try {
            externalConfigInfoTagPersistService.configInfoTagCount();
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("configInfoTagCount error", e.getMessage());
        }
    }
    
    @Test
    void testFindAllConfigInfoTagForDumpAll() {
        
        //mock count
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(308);
        List<ConfigInfoTagWrapper> mockTagList = new ArrayList<>();
        mockTagList.add(new ConfigInfoTagWrapper());
        mockTagList.add(new ConfigInfoTagWrapper());
        mockTagList.add(new ConfigInfoTagWrapper());
        mockTagList.get(0).setLastModified(System.currentTimeMillis());
        mockTagList.get(1).setLastModified(System.currentTimeMillis());
        mockTagList.get(2).setLastModified(System.currentTimeMillis());
        //mock query list
        Mockito.when(jdbcTemplate.query(anyString(), eq(new Object[] {}), eq(CONFIG_INFO_TAG_WRAPPER_ROW_MAPPER))).thenReturn(mockTagList);
        int pageNo = 3;
        int pageSize = 100;
        //execute & verify
        Page<ConfigInfoTagWrapper> returnTagPage = externalConfigInfoTagPersistService.findAllConfigInfoTagForDumpAll(pageNo, pageSize);
        assertEquals(308, returnTagPage.getTotalCount());
        assertEquals(mockTagList, returnTagPage.getPageItems());
        
        //mock count CannotGetJdbcConnectionException
        Mockito.when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new CannotGetJdbcConnectionException("conn error111"));
        //execute & verify
        try {
            externalConfigInfoTagPersistService.findAllConfigInfoTagForDumpAll(pageNo, pageSize);
            assertTrue(false);
        } catch (Exception e) {
            assertEquals("conn error111", e.getMessage());
        }
    }
    
    @Test
    void testFindConfigInfoTags() {
        String dataId = "dataId1112222";
        String group = "group22";
        String tenant = "tenant2";
        List<String> mockedTags = Arrays.asList("tags1", "tags11", "tags111");
        Mockito.when(jdbcTemplate.queryForList(anyString(), eq(new Object[] {dataId, group, tenant}), eq(String.class)))
                .thenReturn(mockedTags);
        
        List<String> configInfoTags = externalConfigInfoTagPersistService.findConfigInfoTags(dataId, group, tenant);
        assertEquals(mockedTags, configInfoTags);
        
    }
    
}
